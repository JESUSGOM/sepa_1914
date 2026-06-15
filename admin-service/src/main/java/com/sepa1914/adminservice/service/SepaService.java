package com.sepa1914.adminservice.service;

import com.sepa1914.adminservice.model.*;
import com.sepa1914.adminservice.repository.CuentaPresentadorRepository;
import com.sepa1914.adminservice.repository.FicheroGeneradoRepository;
import com.sepa1914.adminservice.repository.RemesaLineaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class SepaService {

    private static final Logger log = LoggerFactory.getLogger(SepaService.class);

    private static final int LONGITUD_REGISTRO = 600;
    private static final String CODIGO_NORMA_1915 = "19154";

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter TIME_STAMP = DateTimeFormatter.ofPattern("HHmmss");

    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^A-Z0-9 ]");
    private static final Pattern DIACRITICS = Pattern.compile("[\\p{InCombiningDiacriticalMarks}]");

    private final ContabilidadService contabilidadService;
    private final PdfService pdfService;
    private final EmailService emailService;
    private final SepaValidatorService validator;
    private final RemesaService remesaService;
    private final RemesaLineaRepository remesaLineaRepository;
    private final FicheroGeneradoRepository ficheroGeneradoRepository;
    private final CuentaPresentadorRepository cuentaPresentadorRepository;

    public SepaService(
            ContabilidadService contabilidadService,
            PdfService pdfService,
            EmailService emailService,
            SepaValidatorService validator,
            RemesaService remesaService,
            RemesaLineaRepository remesaLineaRepository,
            FicheroGeneradoRepository ficheroGeneradoRepository,
            CuentaPresentadorRepository cuentaPresentadorRepository
    ) {
        this.contabilidadService = contabilidadService;
        this.pdfService = pdfService;
        this.emailService = emailService;
        this.validator = validator;
        this.remesaService = remesaService;
        this.remesaLineaRepository = remesaLineaRepository;
        this.ficheroGeneradoRepository = ficheroGeneradoRepository;
        this.cuentaPresentadorRepository = cuentaPresentadorRepository;
    }

    public String generarCuaderno19(
            Comunidad comunidad,
            List<Vecino> vecinos,
            LocalDate fechaVencimiento,
            String tipoRemesa,
            String etiquetaEspecial,
            boolean sustituir
    ) {
        return generarCuaderno19(
                comunidad,
                vecinos,
                fechaVencimiento,
                tipoRemesa,
                etiquetaEspecial,
                sustituir,
                null
        );
    }

    public String generarCuaderno19(
            Comunidad comunidad,
            List<Vecino> vecinos,
            LocalDate fechaCobro,
            String tipoRemesa,
            String etiquetaExtra,
            boolean sustituir,
            Long cuentaPresentadorId
    ) {

        log.info("🚀 INICIANDO REMESA GTI TURBO ESTABLE 4.0 [{}]: '{}'",
                tipoRemesa, comunidad.getNombre());

        FicheroGenerado remesa = remesaService.crearCabeceraRemesa(
                comunidad.getId(),
                tipoRemesa,
                fechaCobro,
                "CORE"
        );

        contabilidadService.limpiarContabilidadMesAntesDeRemesa(
                comunidad.getId(),
                fechaCobro.getMonthValue(),
                fechaCobro.getYear(),
                tipoRemesa,
                etiquetaExtra,
                sustituir
        );

        String idAcreedor = safe(comunidad.getIdentificadorAcreedor());
        String ibanComunidad = safe(comunidad.getIban()).replace(" ", "");

        String entidadOficina = ibanComunidad.length() >= 12
                ? ibanComunidad.substring(4, 12)
                : "00000000";

        String hoy = LocalDate.now().format(ISO_DATE);

        String fCobro = fechaCobro != null
                ? fechaCobro.format(ISO_DATE)
                : hoy;

        String ahora = LocalTime.now().format(TIME_STAMP);

        int mesRemesa = fechaCobro != null
                ? fechaCobro.getMonthValue()
                : LocalDate.now().getMonthValue();

        String idPresentador = idAcreedor;
        String nombrePresentador = comunidad.getNombre();

        if (cuentaPresentadorId != null) {
            CuentaPresentador cuentaPresentador = cuentaPresentadorRepository.findById(cuentaPresentadorId)
                    .orElseThrow(() -> new RuntimeException("Cuenta presentadora no encontrada"));

            idPresentador = safe(cuentaPresentador.getIdentificadorPresentador());

            if (cuentaPresentador.getAdministrador() != null
                    && cuentaPresentador.getAdministrador().getNombre() != null
                    && !cuentaPresentador.getAdministrador().getNombre().isBlank()) {
                nombrePresentador = cuentaPresentador.getAdministrador().getNombre();
            } else {
                nombrePresentador = safe(cuentaPresentador.getAlias());
            }

            if (idPresentador.isBlank()) {
                throw new RuntimeException("La cuenta presentadora no tiene identificador SEPA informado.");
            }

            if (nombrePresentador.isBlank()) {
                throw new RuntimeException("La cuenta presentadora no tiene nombre/alias informado.");
            }
        }

        AtomicReference<BigDecimal> totalRemesaBancaria = new AtomicReference<>(BigDecimal.ZERO);
        AtomicInteger numAdeudos003 = new AtomicInteger(0);
        List<String> registrosDeudores = new CopyOnWriteArrayList<>();

        for (Vecino v : vecinos) {

            if (v == null || !v.isActivo()) {
                continue;
            }

            List<ConceptoCobro> conceptosAptos = v.getListaConceptos()
                    .stream()
                    .filter(cc -> cc.correspondeMes(mesRemesa))
                    .collect(Collectors.toList());

            if (conceptosAptos.isEmpty()) {
                continue;
            }

            List<LineaRemesaDTO> lineasRemesa = new ArrayList<>();

            for (ConceptoCobro cc : conceptosAptos) {
                BigDecimal base = cc.getImporte() != null ? cc.getImporte() : BigDecimal.ZERO;

                if (base.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }

                lineasRemesa.add(new LineaRemesaDTO(cc.getDescripcion(), base));

                if (cc.getTipoImpuesto() != null
                        && cc.getTipoImpuesto() != TipoImpuesto.EXENTO
                        && cc.getPorcentajeImpuesto() != null
                        && cc.getPorcentajeImpuesto().compareTo(BigDecimal.ZERO) > 0) {

                    BigDecimal impuesto = base.multiply(cc.getPorcentajeImpuesto())
                            .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

                    String textoImpuesto = cc.getTipoImpuesto().name()
                            + " "
                            + cc.getPorcentajeImpuesto()
                            + "% "
                            + cc.getDescripcion();

                    lineasRemesa.add(new LineaRemesaDTO(textoImpuesto, impuesto));
                }
            }

            if (lineasRemesa.isEmpty()) {
                continue;
            }

            lineasRemesa = limitarLineasConceptoAFormatoNorma(lineasRemesa);

            BigDecimal totalRecibo = lineasRemesa.stream()
                    .map(LineaRemesaDTO::getImporte)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (totalRecibo.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            String conceptContable = lineasRemesa.stream()
                    .map(LineaRemesaDTO::getDescripcion)
                    .collect(Collectors.joining(" / "));

            Recibo reciboUnico = contabilidadService.registrarDevengoCuota(
                    v,
                    totalRecibo,
                    conceptContable,
                    fechaCobro != null ? fechaCobro.withDayOfMonth(1) : LocalDate.now().withDayOfMonth(1),
                    tipoRemesa,
                    etiquetaExtra
            );

            RemesaLinea lineaBD = new RemesaLinea();

            lineaBD.setRemesaId(remesa.getId());
            lineaBD.setVecinoId(v.getId());
            lineaBD.setMandatoId(null);
            lineaBD.setReciboContableId(reciboUnico.getId());
            lineaBD.setReciboSepaId(null);
            lineaBD.setImporte(totalRecibo);
            lineaBD.setConcepto(conceptContable);

            boolean domiciliado = v.isDomiciliado()
                    && v.getIban() != null
                    && !v.getIban().isBlank()
                    && v.getReferenciaMandato() != null
                    && !v.getReferenciaMandato().isBlank();

            lineaBD.setDomiciliado(domiciliado);
            lineaBD.setIncluidoSepa(domiciliado);
            lineaBD.setAsientoGenerado(true);
            lineaBD.setPdfGenerado(false);
            lineaBD.setEmailEnviado(false);

            remesaLineaRepository.save(lineaBD);

            if (!domiciliado) {
                log.info("⚠️ Vecino no domiciliado. Se genera recibo y contabilidad, pero NO se incluye en SEPA: {}",
                        v.getNombre());
                continue;
            }

            String referenciaAdeudoBase = reciboUnico.getId() + hoy + ahora + "003";

            for (int i = 0; i < lineasRemesa.size(); i++) {
                LineaRemesaDTO linea = lineasRemesa.get(i);

                String numeroDato;

                switch (i) {
                    case 0:
                        numeroDato = "003";
                        break;
                    case 1:
                        numeroDato = "004";
                        break;
                    case 2:
                        numeroDato = "005";
                        break;
                    case 3:
                        numeroDato = "006";
                        break;
                    default:
                        numeroDato = "007";
                        break;
                }

                if ("003".equals(numeroDato)) {
                    numAdeudos003.incrementAndGet();

                    synchronized (totalRemesaBancaria) {
                        totalRemesaBancaria.set(totalRemesaBancaria.get().add(totalRecibo));
                    }
                }

                try {
                    String referenciaAdeudo = referenciaAdeudoBase;

                    String r03 = "03" + CODIGO_NORMA_1915 + numeroDato
                            + completar(referenciaAdeudo, 35)
                            + completar(v.getReferenciaMandato(), 35)
                            + "RCUR" + "    "
                            + formatearImporte("003".equals(numeroDato) ? totalRecibo : BigDecimal.ZERO, 11)
                            + hoy
                            + completar(v.getBic(), 11)
                            + completar(v.getNombre(), 70)
                            + completar(v.getDireccion() != null ? v.getDireccion() : ".", 50)
                            + completar(v.getPoblacion() != null ? v.getPoblacion() : ".", 50)
                            + completar(".", 40)
                            + "ES"
                            + completar("", 72)
                            + "A"
                            + completar(v.getIban().replace(" ", ""), 34)
                            + completar("", 4)
                            + completar(linea.getDescripcion(), 140);

                    registrosDeudores.add(completarRegistro(r03));

                } catch (Exception e) {
                    log.error("❌ Error generando línea bancaria {}: {}", v.getNombre(), e.getMessage());
                }
            }
        }

        StringBuilder file = new StringBuilder();

        String idFicheroRef = "PRE" + hoy + ahora + "000"
                + idPresentador.substring(Math.max(0, idPresentador.length() - 9));

        String r01 = "01" + CODIGO_NORMA_1915 + "001"
                + completar(idPresentador, 35)
                + completar(nombrePresentador, 70)
                + hoy
                + completar(idFicheroRef, 35)
                + completar(entidadOficina, 8)
                + completar("", 434);

        String r02 = "02" + CODIGO_NORMA_1915 + "002"
                + completar(idAcreedor, 35)
                + fCobro
                + completar(comunidad.getNombre(), 70)
                + completar(comunidad.getDireccion(), 50)
                + completar(comunidad.getPoblacion(), 50)
                + completar(".", 40)
                + "ES"
                + completar(ibanComunidad, 34)
                + completar("", 301);

        file.append(completarRegistro(r01)).append("\n");
        file.append(completarRegistro(r02)).append("\n");

        for (String r : registrosDeudores) {
            file.append(r).append("\n");
        }

        BigDecimal sumaFinal = totalRemesaBancaria.get();
        int adeudosUnicos = numAdeudos003.get();

        int count04 = 1 + registrosDeudores.size() + 1;
        int count05 = count04 + 1;
        int totalRegistrosFichero = 1 + count05 + 1;

        String r04 = "04"
                + completar(idAcreedor, 35)
                + fCobro
                + formatearImporte(sumaFinal, 17)
                + padLeft(String.valueOf(adeudosUnicos), 8, '0')
                + padLeft(String.valueOf(count04), 10, '0');

        file.append(completarRegistro(r04)).append("\n");

        String r05 = "05"
                + completar(idAcreedor, 35)
                + formatearImporte(sumaFinal, 17)
                + padLeft(String.valueOf(adeudosUnicos), 8, '0')
                + padLeft(String.valueOf(count05), 10, '0');

        file.append(completarRegistro(r05)).append("\n");

        String r99 = "99"
                + formatearImporte(sumaFinal, 17)
                + padLeft(String.valueOf(adeudosUnicos), 8, '0')
                + padLeft(String.valueOf(totalRegistrosFichero), 10, '0');

        file.append(completarRegistro(r99)).append("\n");

        List<String> errores = validator.validarFichero(file.toString());

        if (!errores.isEmpty()) {
            throw new RuntimeException("❌ REMESA INVÁLIDA: " + errores.get(0));
        }

        remesaService.recalcularTotalesRemesa(remesa.getId());

        remesa.setContenido(file.toString());
        remesa.setEstado("GENERADA");
        remesa.setNombreArchivo(remesa.getIdentificadorFichero() + ".c19");

        ficheroGeneradoRepository.save(remesa);

        return file.toString();
    }

    private List<LineaRemesaDTO> limitarLineasConceptoAFormatoNorma(List<LineaRemesaDTO> lineasOriginales) {
        if (lineasOriginales == null || lineasOriginales.size() <= 5) {
            return lineasOriginales;
        }

        List<LineaRemesaDTO> resultado = new ArrayList<>();

        resultado.add(lineasOriginales.get(0));
        resultado.add(lineasOriginales.get(1));
        resultado.add(lineasOriginales.get(2));
        resultado.add(lineasOriginales.get(3));

        BigDecimal importeAgrupado = BigDecimal.ZERO;
        List<String> descripcionesAgrupadas = new ArrayList<>();

        for (int i = 4; i < lineasOriginales.size(); i++) {
            LineaRemesaDTO linea = lineasOriginales.get(i);

            importeAgrupado = importeAgrupado.add(
                    linea.getImporte() != null ? linea.getImporte() : BigDecimal.ZERO
            );

            if (linea.getDescripcion() != null && !linea.getDescripcion().isBlank()) {
                descripcionesAgrupadas.add(linea.getDescripcion());
            }
        }

        String descripcionAgrupada = descripcionesAgrupadas.isEmpty()
                ? "OTROS CONCEPTOS"
                : "OTROS CONCEPTOS: " + String.join(" / ", descripcionesAgrupadas);

        if (descripcionAgrupada.length() > 140) {
            descripcionAgrupada = descripcionAgrupada.substring(0, 140);
        }

        resultado.add(new LineaRemesaDTO(descripcionAgrupada, importeAgrupado));

        return resultado;
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    private void validarRegistro(String r) {
        if (r.length() != LONGITUD_REGISTRO) {
            throw new RuntimeException("GTI_ERR: Longitud " + r.length() + ". Se requieren " + LONGITUD_REGISTRO);
        }
    }

    private String completarRegistro(String contenido) {
        if (contenido.length() >= LONGITUD_REGISTRO) {
            return contenido.substring(0, LONGITUD_REGISTRO);
        }

        StringBuilder sb = new StringBuilder(contenido);

        while (sb.length() < LONGITUD_REGISTRO) {
            sb.append(" ");
        }

        return sb.toString();
    }

    private String normalizarTexto(String texto) {
        if (texto == null) {
            return "";
        }

        String temp = Normalizer.normalize(texto, Normalizer.Form.NFD);
        temp = DIACRITICS.matcher(temp).replaceAll("");

        return NON_ALPHANUMERIC.matcher(temp.toUpperCase()).replaceAll(" ").trim();
    }

    private String completar(String texto, int longitud) {
        String res = normalizarTexto(texto);

        if (res.length() >= longitud) {
            return res.substring(0, longitud);
        }

        return String.format("%-" + longitud + "s", res);
    }

    private String formatearImporte(BigDecimal importe, int longitud) {
        if (importe == null) {
            importe = BigDecimal.ZERO;
        }

        long centimos = importe.multiply(new BigDecimal("100"))
                .setScale(0, RoundingMode.HALF_UP)
                .longValue();

        return padLeft(String.valueOf(centimos), longitud, '0');
    }

    private String padLeft(String s, int n, char c) {
        return String.format("%" + n + "s", s).replace(' ', c);
    }

    private Administrador inicializarAdmin(Administrador a) {
        if (a == null) {
            return null;
        }

        Administrador c = new Administrador();

        c.setNombre(a.getNombre());
        c.setEmail(a.getEmail());
        c.setNifCif(a.getNifCif());
        c.setSufijo(a.getSufijo());
        c.setIban(a.getIban());
        c.setBic(a.getBic());
        c.setDireccion(a.getDireccion());
        c.setPoblacion(a.getPoblacion());
        c.setProvincia(a.getProvincia());
        c.setPaisCod(a.getPaisCod());
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

        String finca = v.getVivienda() != null ? v.getVivienda() : "";
        String resultado = ("CUOTA COMUNIDAD " + finca + ": " + descripcion).trim();

        return resultado.length() > 140 ? resultado.substring(0, 140) : resultado;
    }

    private List<ConceptoCobro> filtrarConceptosPorPeriodo(Vecino v, int mesRemesa) {
        List<ConceptoCobro> aptos = new ArrayList<>();

        if (v.getListaConceptos() == null) {
            return aptos;
        }

        for (ConceptoCobro cc : v.getListaConceptos()) {
            if (cc != null
                    && cc.isActivo()
                    && cc.getImporte() != null
                    && cc.getImporte().compareTo(BigDecimal.ZERO) > 0) {
                aptos.add(cc);
            }
        }

        return aptos;
    }

    private static class LineaRemesaDTO {

        private String descripcion;
        private BigDecimal importe;

        public LineaRemesaDTO(String descripcion, BigDecimal importe) {
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

    public String generarCuaderno19(Comunidad comunidad, List<Vecino> vecinos, LocalDate fechaCobro) {
        return this.generarCuaderno19(
                comunidad,
                vecinos,
                fechaCobro,
                "ORDINARIA",
                null,
                true,
                null
        );
    }
}