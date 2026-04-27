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

    public String generarCuaderno19(Comunidad comunidad, List<Vecino> vecinos, LocalDate fechaCobro) {

        log.info("🚀 INICIANDO REMESA GTI TURBO ESTABLE 3.0: '{}'", comunidad.getNombre());

        // Limpieza previa del periodo contable para evitar duplicidades
        contabilidadService.limpiarContabilidadMesAntesDeRemesa(
                comunidad.getId(),
                fechaCobro.getMonthValue(),
                fechaCobro.getYear()
        );

        final Administrador admin = inicializarAdmin(comunidad.getDatosAdministrador());

        String idAcreedor = safe(comunidad.getIdentificadorAcreedor());
        String ibanComunidad = safe(comunidad.getIban()).replace(" ", "");
        String entidadOficina = ibanComunidad.length() >= 12 ? ibanComunidad.substring(4, 12) : "00000000";

        String hoy = LocalDate.now().format(ISO_DATE);
        String fCobro = (fechaCobro != null) ? fechaCobro.format(ISO_DATE) : hoy;
        String ahora = LocalTime.now().format(TIME_STAMP);

        int mesRemesa = (fechaCobro != null) ? fechaCobro.getMonthValue() : LocalDate.now().getMonthValue();
        int anioRemesa = (fechaCobro != null) ? fechaCobro.getYear() : LocalDate.now().getYear();

        AtomicReference<BigDecimal> totalRemesaBancaria = new AtomicReference<>(BigDecimal.ZERO);
        AtomicInteger numAdeudos003 = new AtomicInteger(0); // Contador de deudores únicos (registros 003)

        List<String> registrosDeudores = new CopyOnWriteArrayList<>();
        List<RemesaTaskDTO> tareasBancarias = new ArrayList<>();

        // ---------------- FASE 1: Registro Contable (Asiento Único por Vecino) ----------------
        for (Vecino v : vecinos) {
            if (v == null || !v.isActivo()) continue;

            List<ConceptoCobro> conceptosAptos = v.getListaConceptos().stream()
                    .filter(cc -> cc.correspondeMes(mesRemesa))
                    .collect(Collectors.toList());

            if (conceptosAptos.isEmpty()) continue;

            BigDecimal totalRecibo = conceptosAptos.stream()
                    .map(ConceptoCobro::getImporte)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (totalRecibo.compareTo(BigDecimal.ZERO) > 0) {
                String conceptoContable = generarTextoConcepto(v, conceptosAptos);

                Recibo reciboUnico = contabilidadService.registrarDevengoCuota(
                        v, totalRecibo, conceptoContable,
                        (fechaCobro != null) ? fechaCobro.withDayOfMonth(1) : LocalDate.now().withDayOfMonth(1)
                );

                // Desglose técnico para el banco (máximo 5 slots del 003 al 007)
                int maxSlots = 5;
                for (int i = 0; i < conceptosAptos.size(); i++) {
                    if (i < maxSlots - 1) {
                        ConceptoCobro cc = conceptosAptos.get(i);
                        tareasBancarias.add(new RemesaTaskDTO(v, reciboUnico, Collections.singletonList(cc),
                                cc.getImporte(), "00" + (3 + i), cc.getDescripcion()));
                    } else {
                        List<ConceptoCobro> restantes = conceptosAptos.subList(i, conceptosAptos.size());
                        BigDecimal sumaResto = restantes.stream().map(ConceptoCobro::getImporte).reduce(BigDecimal.ZERO, BigDecimal::add);
                        tareasBancarias.add(new RemesaTaskDTO(v, reciboUnico, restantes, sumaResto, "007",
                                restantes.stream().map(ConceptoCobro::getDescripcion).collect(Collectors.joining(" / "))));
                        break;
                    }
                }
            }
        }

        // ---------------- FASE 2: Generación Bancaria ----------------
        tareasBancarias.parallelStream().forEach(task -> {
            Vecino v = task.vecino;
            try {
                if (v.isDomiciliado() && v.getIban() != null && !v.getIban().isBlank()) {
                    if ("003".equals(task.numeroDato)) {
                        numAdeudos003.incrementAndGet();
                    }

                    synchronized (totalRemesaBancaria) {
                        totalRemesaBancaria.set(totalRemesaBancaria.get().add(task.total));
                    }

                    // REGISTRO 03: Estructura fija de 600 posiciones
                    String r03 = "03" + CODIGO_NORMA_1915 + task.numeroDato + // 01-10
                            completar(task.recibo.getId().toString() + hoy + ahora, 35) + // 11-45 (ID)
                            completar(v.getReferenciaMandato(), 35) + // 46-80 (Referencia)
                            "RCUR" + "    " + // 81-88
                            formatearImporte(task.total, 11) + // 89-99 (Importe)
                            hoy + // 100-107 (Fecha)
                            completar(v.getBic(), 11) + // 108-118 (BIC)
                            completar(v.getNombre(), 70) + // 119-188 (Nombre deudor)
                            completar(v.getDireccion() != null ? v.getDireccion() : ".", 50) + // 189-238 (D1)
                            completar(v.getPoblacion() != null ? v.getPoblacion() : ".", 50) + // 239-288 (D2)
                            completar(".", 40) + // 289-328 (D3 - Provincia que faltaba)
                            "ES" + // 329-330 (País)
                            completar("", 72) + // 331-402 (Relleno)
                            "A" + // 403 (Marcador Identificador Cuenta IBAN)
                            completar(v.getIban().replace(" ", ""), 34) + // 404-437 (IBAN deudor)
                            completar("", 4) + // 438-441
                            completar(task.conceptoTexto, 140); // 442-581 (Concepto)

                    registrosDeudores.add(completarRegistro(r03));
                }
            } catch (Exception e) { log.error("❌ Error en línea bancaria {}: {}", v.getNombre(), e.getMessage()); }
        });

        // ---------------- ENSAMBLAJE FINAL DEL FICHERO .C19 ----------------
        StringBuilder file = new StringBuilder();

        // R01: Presentador (Aseguramos ID y Entidad)
        String idFicheroRef = "PRE" + hoy + ahora + "000" + idAcreedor.substring(Math.max(0, idAcreedor.length()-9));
        String r01 = "01" + CODIGO_NORMA_1915 + "001" +
                completar(idAcreedor, 35) +
                completar(comunidad.getNombre(), 70) +
                hoy +
                completar(idFicheroRef, 35) + // Pos 124-158
                completar(entidadOficina, 8) + // Pos 159-166
                completar("", 434);

        // R02: Acreedor (Añadimos D3 y País para alinear IBAN en 266)
        String r02 = "02" + CODIGO_NORMA_1915 + "002" +
                completar(idAcreedor, 35) +
                fCobro +
                completar(comunidad.getNombre(), 70) +
                completar(comunidad.getDireccion(), 50) +   // 124-173 (D1)
                completar(comunidad.getPoblacion(), 50) +   // 174-223 (D2)
                completar(".", 40) +                         // 224-263 (D3 Provincia)
                "ES" +                                      // 264-265 (País)
                completar(ibanComunidad, 34) +               // 266-299 (IBAN abono)
                completar("", 301);

        file.append(completarRegistro(r01)).append("\n").append(completarRegistro(r02)).append("\n");
        for (String r : registrosDeudores) { file.append(r).append("\n"); }

        // --- CÁLCULOS PARA CIERRES ---
        BigDecimal sumaFinal = totalRemesaBancaria.get();
        int adeudosUnicos = numAdeudos003.get();

        // Conteo para Registro 04: Incluye cabecera 02, todos los deudores 03 y el propio 04
        int count04 = 1 + registrosDeudores.size() + 1;

        // Conteo para Registro 05: Incluye todo lo anterior más el propio 05 (Total 12 en tu caso)
        int count05 = count04 + 1;

        // Conteo para Registro 99: Incluye registro 01, el bloque anterior y el propio 99 (Total 14)
        int totalRegistrosFichero = 1 + count05 + 1;

        // R04: Totales Acreedor por Fecha (Pág 32 PDF)
        String r04 = "04" + completar(idAcreedor, 35) + fCobro + formatearImporte(sumaFinal, 17) +
                padLeft(String.valueOf(adeudosUnicos), 8, '0') +
                padLeft(String.valueOf(count04), 10, '0');
        file.append(completarRegistro(r04)).append("\n");

        // R05: Totales Ordenante / Acreedor General (Pág 34 PDF)
        String r05 = "05" + completar(idAcreedor, 35) + formatearImporte(sumaFinal, 17) +
                padLeft(String.valueOf(adeudosUnicos), 8, '0') +
                padLeft(String.valueOf(count05), 10, '0');
        file.append(completarRegistro(r05)).append("\n");

        // R99: Totales General (Pág 35 PDF)
        String r99 = "99" +
                formatearImporte(sumaFinal, 17) +
                padLeft(String.valueOf(adeudosUnicos), 8, '0') +
                padLeft(String.valueOf(totalRegistrosFichero), 10, '0');
        file.append(completarRegistro(r99)).append("\n");
        // Validación final
        List<String> errores = validator.validarFichero(file.toString());
        if (!errores.isEmpty()) throw new RuntimeException("❌ REMESA INVÁLIDA: " + errores.get(0));

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



    private static class RemesaTaskDTO {
        Vecino vecino;
        Recibo recibo;
        List<ConceptoCobro> conceptos;
        BigDecimal total;
        String numeroDato; // Asegúrate de que esta línea sea String
        String conceptoTexto;

        RemesaTaskDTO(Vecino v, Recibo r, List<ConceptoCobro> c, BigDecimal t, String nd, String txt) {
            this.vecino = v;
            this.recibo = r;
            this.conceptos = c;
            this.total = t;
            this.numeroDato = nd; // Y aquí también
            this.conceptoTexto = txt;
        }
    }
}