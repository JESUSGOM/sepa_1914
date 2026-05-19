package com.sepa1914.adminservice.service;

import com.sepa1914.adminservice.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.text.Normalizer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class SepaService {

    private static final Logger log = LoggerFactory.getLogger(SepaService.class);

    // 🔴 NORMATIVA CUADERNO 19-14: Longitud fija de 600 caracteres según especificación técnica
    private static final int LONGITUD_REGISTRO = 600;

    private static final String CODIGO_NORMA_1915 = "19154";
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME_STAMP = DateTimeFormatter.ofPattern("HHmmss");

    private static final Pattern DIACRITICS = Pattern.compile("[\\p{InCombiningDiacriticalMarks}]");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^A-Z0-9 ]");

    private final ContabilidadService contabilidadService;
    private final PdfService pdfService;
    private final EmailService emailService;
    private final SepaValidatorService validator;

    public SepaService(
            ContabilidadService contabilidadService,
            PdfService pdfService,
            EmailService emailService,
            SepaValidatorService validator
    ) {
        this.contabilidadService = contabilidadService;
        this.pdfService = pdfService;
        this.emailService = emailService;
        this.validator = validator;
    }

    public String generarCuaderno19(
            Comunidad comunidad,
            List<Vecino> vecinos,
            LocalDate fechaCobro,
            String tipoRemesa,      // "ORDINARIA" o "EXTRAORDINARIA"
            String etiquetaExtra,    // Ejemplo: "Derrama Fachada"
            boolean sustituir       // true para borrar lo anterior, false para añadir
    ) {

        log.info("🚀 INICIANDO REMESA GTI TURBO ESTABLE 4.0 [{}]: '{}'",
                tipoRemesa, comunidad.getNombre());

        // =========================================================
        // LIMPIEZA CONTABLE PREVIA SELECTIVA
        // =========================================================
        // Se delega en contabilidadService la limpieza inteligente basada en tipo y etiqueta
        contabilidadService.limpiarContabilidadMesAntesDeRemesa(
                comunidad.getId(),
                fechaCobro.getMonthValue(),
                fechaCobro.getYear(),
                tipoRemesa,
                etiquetaExtra,
                sustituir
        );

        final Administrador admin =
                inicializarAdmin(comunidad.getDatosAdministrador());

        String idAcreedor =
                safe(comunidad.getIdentificadorAcreedor());

        String ibanComunidad =
                safe(comunidad.getIban()).replace(" ", "");

        String entidadOficina =
                ibanComunidad.length() >= 12
                        ? ibanComunidad.substring(4, 12)
                        : "00000000";

        String hoy =
                LocalDate.now().format(ISO_DATE);

        String fCobro =
                (fechaCobro != null)
                        ? fechaCobro.format(ISO_DATE)
                        : hoy;

        String ahora =
                LocalTime.now().format(TIME_STAMP);

        int mesRemesa =
                (fechaCobro != null)
                        ? fechaCobro.getMonthValue()
                        : LocalDate.now().getMonthValue();

        int anioRemesa =
                (fechaCobro != null)
                        ? fechaCobro.getYear()
                        : LocalDate.now().getYear();

        AtomicReference<BigDecimal> totalRemesaBancaria =
                new AtomicReference<>(BigDecimal.ZERO);

        AtomicInteger numAdeudos003 =
                new AtomicInteger(0);

        List<String> registrosDeudores =
                new CopyOnWriteArrayList<>();

        // =========================================================
        // FASE 1: GENERAR REGISTROS
        // =========================================================
        for (Vecino v : vecinos) {

            if (v == null || !v.isActivo()) {
                continue;
            }

            // Si es ORDINARIA, filtramos conceptos del mes.
            // Si es EXTRAORDINARIA, solemos procesar conceptos específicos (esto depende de tu lógica de negocio)
            List<ConceptoCobro> conceptosAptos =
                    v.getListaConceptos()
                            .stream()
                            .filter(cc -> cc.correspondeMes(mesRemesa))
                            .collect(Collectors.toList());

            if (conceptosAptos.isEmpty()) {
                continue;
            }

            List<LineaRemesaDTO> lineasRemesa = new ArrayList<>();

            for (ConceptoCobro cc : conceptosAptos) {
                BigDecimal base = cc.getImporte() != null ? cc.getImporte() : BigDecimal.ZERO;
                if (base.compareTo(BigDecimal.ZERO) <= 0) continue;

                // LINEA BASE
                lineasRemesa.add(new LineaRemesaDTO(cc.getDescripcion(), base));

                // LINEA IMPUESTO
                if (cc.getTipoImpuesto() != null && cc.getTipoImpuesto() != TipoImpuesto.EXENTO
                        && cc.getPorcentajeImpuesto() != null && cc.getPorcentajeImpuesto().compareTo(BigDecimal.ZERO) > 0) {

                    BigDecimal impuesto = base.multiply(cc.getPorcentajeImpuesto())
                            .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

                    String textoImpuesto = cc.getTipoImpuesto().name() + " " + cc.getPorcentajeImpuesto() + "% " + cc.getDescripcion();
                    lineasRemesa.add(new LineaRemesaDTO(textoImpuesto, impuesto));
                }
            }

            if (lineasRemesa.isEmpty()) continue;

            BigDecimal totalRecibo = lineasRemesa.stream()
                    .map(LineaRemesaDTO::getImporte)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (totalRecibo.compareTo(BigDecimal.ZERO) <= 0) continue;

            String conceptContable = lineasRemesa.stream()
                    .map(LineaRemesaDTO::getDescripcion)
                    .collect(Collectors.joining(" / "));

            // =====================================================
            // ASIENTO CONTABLE Y GENERACIÓN DE RECIBO
            // =====================================================
            // IMPORTANTE: registrarDevengoCuota ahora debe asignar tipoRemesa y etiquetaExtra al Recibo
            Recibo reciboUnico =
                    contabilidadService.registrarDevengoCuota(
                            v,
                            totalRecibo,
                            conceptContable,
                            (fechaCobro != null) ? fechaCobro.withDayOfMonth(1) : LocalDate.now().withDayOfMonth(1),
                            tipoRemesa,
                            etiquetaExtra
                    );

            // =====================================================
            // GENERACION DE REGISTROS 03 (Norma 19-14)
            // =====================================================
            for (int i = 0; i < lineasRemesa.size(); i++) {
                LineaRemesaDTO linea = lineasRemesa.get(i);
                String numeroDato;

                switch (i) {
                    case 0: numeroDato = "003"; break;
                    case 1: numeroDato = "004"; break;
                    case 2: numeroDato = "005"; break;
                    case 3: numeroDato = "006"; break;
                    default: numeroDato = "007"; break;
                }

                // FIX GTI: Solo acumulamos el importe total del recibo y sumamos el adeudo cuando procesamos el bloque principal 003
                if ("003".equals(numeroDato)) {
                    numAdeudos003.incrementAndGet();
                    synchronized (totalRemesaBancaria) {
                        totalRemesaBancaria.set(totalRemesaBancaria.get().add(totalRecibo));
                    }
                }

                try {
                    String referenciaAdeudo = reciboUnico.getId() + hoy + ahora + numeroDato;
                    String r03 = "03" + CODIGO_NORMA_1915 + numeroDato
                            + completar(referenciaAdeudo, 35)
                            + completar(v.getReferenciaMandato(), 35)
                            + "RCUR" + "    "
                            // FIX CORRECCIÓN SEPA: El registro principal 003 se estampa con el total unificado. Los desgloses informativos (004, etc.) DEBEN ir rellenos a CERO para no duplicar sumas en los validadores bancarios.
                            + formatearImporte("003".equals(numeroDato) ? totalRecibo : BigDecimal.ZERO, 11)
                            + hoy
                            + completar(v.getBic(), 11)
                            + completar(v.getNombre(), 70)
                            + completar(v.getDireccion() != null ? v.getDireccion() : ".", 50)
                            + completar(v.getPoblacion() != null ? v.getPoblacion() : ".", 50)
                            + completar(".", 40)
                            + "ES" + completar("", 72) + "A"
                            + completar(v.getIban().replace(" ", ""), 34)
                            + completar("", 4)
                            + completar(linea.getDescripcion(), 140);

                    registrosDeudores.add(completarRegistro(r03));

                } catch (Exception e) {
                    log.error("❌ Error generando línea bancaria {}: {}", v.getNombre(), e.getMessage());
                }
            }
        }

        // =========================================================
        // ENSAMBLAJE FINAL DEL FICHERO .c19
        // =========================================================
        StringBuilder file = new StringBuilder();
        String idFicheroRef = "PRE" + hoy + ahora + "000" + idAcreedor.substring(Math.max(0, idAcreedor.length() - 9));

        // REGISTRO 01
        String r01 = "01" + CODIGO_NORMA_1915 + "001" + completar(idAcreedor, 35) + completar(comunidad.getNombre(), 70) + hoy + completar(idFicheroRef, 35) + completar(entidadOficina, 8) + completar("", 434);

        // REGISTRO 02
        String r02 = "02" + CODIGO_NORMA_1915 + "002" + completar(idAcreedor, 35) + fCobro + completar(comunidad.getNombre(), 70) + completar(comunidad.getDireccion(), 50) + completar(comunidad.getPoblacion(), 50) + completar(".", 40) + "ES" + completar(ibanComunidad, 34) + completar("", 301);

        file.append(completarRegistro(r01)).append("\n");
        file.append(completarRegistro(r02)).append("\n");

        for (String r : registrosDeudores) {
            file.append(r).append("\n");
        }

        // TOTALES Y CIERRES (04, 05, 99)
        BigDecimal sumaFinal = totalRemesaBancaria.get();
        int adeudosUnicos = numAdeudos003.get();
        int count04 = 1 + registrosDeudores.size() + 1;
        int count05 = count04 + 1;
        int totalRegistrosFichero = 1 + count05 + 1;

        String r04 = "04" + completar(idAcreedor, 35) + fCobro + formatearImporte(sumaFinal, 17) + padLeft(String.valueOf(adeudosUnicos), 8, '0') + padLeft(String.valueOf(count04), 10, '0');
        file.append(completarRegistro(r04)).append("\n");

        String r05 = "05" + completar(idAcreedor, 35) + formatearImporte(sumaFinal, 17) + padLeft(String.valueOf(adeudosUnicos), 8, '0') + padLeft(String.valueOf(count05), 10, '0');
        file.append(completarRegistro(r05)).append("\n");

        String r99 = "99" + formatearImporte(sumaFinal, 17) + padLeft(String.valueOf(adeudosUnicos), 8, '0') + padLeft(String.valueOf(totalRegistrosFichero), 10, '0');
        file.append(completarRegistro(r99)).append("\n");

        // VALIDACION FINAL
        List<String> errores = validator.validarFichero(file.toString());
        if (!errores.isEmpty()) {
            throw new RuntimeException("❌ REMESA INVÁLIDA: " + errores.get(0));
        }

        return file.toString();
    }

    // ---------------- UTILIDADES TÉCNICAS (INTEGRIDAD 100%) ----------------

    private String safe(String s) {
        return (s == null) ? "" : s;
    }

    private void validarRegistro(String r) {
        if (r.length() != LONGITUD_REGISTRO) {
            throw new RuntimeException("GTI_ERR: Longitud " + r.length() + ". Se requieren " + LONGITUD_REGISTRO);
        }
    }

    private String completarRegistro(String contenido) {
        if (contenido.length() >= LONGITUD_REGISTRO)
            return contenido.substring(0, LONGITUD_REGISTRO);

        StringBuilder sb = new StringBuilder(contenido);
        while (sb.length() < LONGITUD_REGISTRO) {
            sb.append(" ");
        }
        return sb.toString();
    }

    private String normalizarTexto(String texto) {
        if (texto == null) return "";
        String temp = Normalizer.normalize(texto, Normalizer.Form.NFD);
        temp = DIacritics.matcher(temp).replaceAll("");
        return NON_ALPHANUMERIC.matcher(temp.toUpperCase()).replaceAll(" ").trim();
    }

    private static final Pattern DIacritics = Pattern.compile("[\\p{InCombiningDiacriticalMarks}]");

    private String completar(String texto, int longitud) {
        String res = normalizarTexto(texto);
        if (res.length() >= longitud) return res.substring(0, longitud);
        return String.format("%-" + longitud + "s", res);
    }

    private String formatearImporte(BigDecimal importe, int longitud) {
        if (importe == null) importe = BigDecimal.ZERO;

        long centimos = importe.multiply(new BigDecimal("100"))
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();

        return padLeft(String.valueOf(centimos), longitud, '0');
    }

    private String padLeft(String s, int n, char c) {
        return String.format("%" + n + "s", s).replace(' ', c);
    }

    private Administrador inicializarAdmin(Administrador a) {
        if (a == null) return null;

        Administrador c = new Administrador();
        c.setNombre(a.getNombre());
        c.setEmail(a.getEmail());
        c.setSmtpHost(a.getSmtpHost());
        c.setSmtpPort(a.getSmtpPort());
        c.setSmtpUsername(a.getSmtpUsername());
        c.setSmtpPassword(a.getSmtpPassword());
        c.setSmtpAuth(a.isSmtpAuth());
        c.setSmtpStarttls(a.isSmtpStarttls());
        return c;
    }

    private String generarTextoConcepto(Vecino v, List<ConceptoCobro> conceptos) {
        String descripcion = conceptos.stream()
                .map(ConceptoCobro::getDescripcion)
                .collect(Collectors.joining(" / "));
        String finca = (v.getVivienda() != null) ? v.getVivienda() : "";
        String resultado = ("CUOTA COMUNIDAD " + finca + ": " + descripcion).trim();
        return resultado.length() > 140 ? resultado.substring(0, 140) : resultado;
    }

    private List<ConceptoCobro> filtrarConceptosPorPeriodo(Vecino v, int mesRemesa) {
        List<ConceptoCobro> aptos = new ArrayList<>();
        if (v.getListaConceptos() == null) return aptos;
        for (ConceptoCobro cc : v.getListaConceptos()) {
            if (cc != null && cc.isActivo() && cc.getImporte() != null
                    && cc.getImporte().compareTo(BigDecimal.ZERO) > 0) {
                aptos.add(cc);
            }
        }
        return aptos;
    }

    /**
     * =====================================================
     * DTO INTERNO PARA DESGLOSE DE REMESA
     * =====================================================
     */
    private static class LineaRemesaDTO {
        private String descripcion;
        private BigDecimal importe;
        public LineaRemesaDTO(
                String descripcion,
                BigDecimal importe
        ) {
            this.descripcion = descripcion;
            this.importe = importe;
        }
        public String getDescripcion() {
            return descripcion;
        }
        public void setDescripcion(String descripcion) {
            this.descripcion = descripcion;
        }
        public BigDecimal getImporte() {
            return importe;
        }
        public void setImporte(BigDecimal importe) {
            this.importe = importe;
        }
    }

    /**
     * Método puente para generar cuaderno 19 desde controladores antiguos (Bancos, Comunidad, Recibo)
     */
    public String generarCuaderno19(Comunidad comunidad, List<Vecino> vecinos, LocalDate fechaCobro) {
        // Por defecto, las llamadas antiguas se tratan como ORDINARIAS y SUSTITUIR = TRUE
        return this.generarCuaderno19(comunidad, vecinos, fechaCobro, "ORDINARIA", null, true);
    }
}