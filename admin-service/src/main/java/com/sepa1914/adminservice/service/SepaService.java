package com.sepa1914.adminservice.service;

import com.sepa1914.adminservice.model.Administrador;
import com.sepa1914.adminservice.model.Comunidad;
import com.sepa1914.adminservice.model.Vecino;
import com.sepa1914.adminservice.model.ConceptoCobro;
import com.sepa1914.adminservice.model.Recibo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;
import java.text.Normalizer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Servicio GTI TURBO ESTABLE 2.3.
 * Refactorizado para paralelismo seguro y eliminación de N+1.
 */
@Service
public class SepaService {

    private static final Logger log = LoggerFactory.getLogger(SepaService.class);
    private static final int LONGITUD_REGISTRO = 600;
    private static final String CODIGO_NORMA_1915 = "19154";
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final Pattern DIACRITICS = Pattern.compile("[\\p{InCombiningDiacriticalMarks}]");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^A-Z0-9 ]");

    private final ContabilidadService contabilidadService;
    private final PdfService pdfService;
    private final EmailService emailService;

    public SepaService(ContabilidadService contabilidadService, PdfService pdfService, EmailService emailService) {
        this.contabilidadService = contabilidadService;
        this.pdfService = pdfService;
        this.emailService = emailService;
    }

    public String generarCuaderno19(Comunidad comunidad, List<Vecino> vecinos, LocalDate fechaCobro) {
        log.info("🚀 INICIANDO REMESA GTI TURBO ESTABLE 2.3: '{}'", comunidad.getNombre());

        // 1. Limpieza contable previa
        contabilidadService.limpiarContabilidadMesAntesDeRemesa(comunidad.getId(), fechaCobro.getMonthValue(), fechaCobro.getYear());

        // Preparación de datos del Administrador (Objeto desconectado de Hibernate)
        final Administrador admin = inicializarAdmin(comunidad.getDatosAdministrador());

        String idAcreedor = comunidad.getIdentificadorAcreedor();
        String ibanComunidad = (comunidad.getIban() != null) ? comunidad.getIban().replace(" ", "") : "";
        String hoy = LocalDate.now().format(ISO_DATE);
        String fCobro = (fechaCobro != null) ? fechaCobro.format(ISO_DATE) : hoy;
        int mesRemesa = (fechaCobro != null) ? fechaCobro.getMonthValue() : LocalDate.now().getMonthValue();
        int anioRemesa = (fechaCobro != null) ? fechaCobro.getYear() : LocalDate.now().getYear();

        AtomicReference<BigDecimal> totalRemesaAcumuladoBancario = new AtomicReference<>(BigDecimal.ZERO);
        AtomicInteger numRecibosEnFichero = new AtomicInteger(0);
        List<String> registrosDeudores = new CopyOnWriteArrayList<>();

        // --- FASE 1: CONTABILIDAD (Secuencial para evitar bloqueos JdbcValuesSource) ---
        List<RemesaTaskDTO> tareas = new ArrayList<>();
        for (Vecino v : vecinos) {
            if (v == null || !v.isActivo()) continue;

            List<ConceptoCobro> conceptosAptos = filtrarConceptosPorPeriodo(v, mesRemesa);
            BigDecimal totalVecino = conceptosAptos.stream()
                    .map(ConceptoCobro::getImporte)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (totalVecino.compareTo(BigDecimal.ZERO) > 0) {
                String conceptoDetallado = generarTextoConcepto(v, conceptosAptos);
                LocalDate fechaEmision = (fechaCobro != null) ? fechaCobro.withDayOfMonth(1) : LocalDate.now().withDayOfMonth(1);

                // Cada llamada a registrarDevengoCuota maneja su propia @Transactional
                Recibo recibo = contabilidadService.registrarDevengoCuota(v, totalVecino, conceptoDetallado, fechaEmision);
                tareas.add(new RemesaTaskDTO(v, recibo, conceptosAptos, totalVecino, conceptoDetallado));
            }
        }

        // --- FASE 2: PDF Y EMAIL (Turbo Paralelo - Sin tocar la Base de Datos) ---
        log.info("⚡ GTI TURBO: Procesando {} deudores en paralelo...", tareas.size());
        tareas.parallelStream().forEach(task -> {
            Vecino v = task.vecino;
            try {
                String nombreFicheroPdf = v.getNif() + "_" + v.getVivienda() + "_" + mesRemesa + "_" + anioRemesa;
                String rutaPdf = pdfService.generarReciboPdfLocal(task.recibo, nombreFicheroPdf, task.conceptos, fechaCobro);

                if (v.getEmail() != null && !v.getEmail().isBlank()) {
                    String asunto = "Recibo " + comunidad.getNombre() + " - " + mesRemesa + "/" + anioRemesa;
                    String cuerpo = "Estimado/a " + v.getNombre() + ",\n\nLe adjuntamos el recibo de su cuota de comunidad.\n\nSaludos.";
                    emailService.enviarReciboPorEmail(v.getEmail(), asunto, cuerpo, rutaPdf, admin);
                }

                if (v.isDomiciliado() && v.getIban() != null && !v.getIban().isBlank()) {
                    numRecibosEnFichero.incrementAndGet();
                    synchronized (totalRemesaAcumuladoBancario) {
                        totalRemesaAcumuladoBancario.set(totalRemesaAcumuladoBancario.get().add(task.total));
                    }

                    String r03 = "03" + CODIGO_NORMA_1915 + "003" +
                            completar(idAcreedor, 35) +
                            completar(v.getReferenciaMandato(), 35) +
                            "RCUR" + "A" +
                            formatearImporte(task.total, 11) +
                            "20240101" +
                            completar(v.getBic(), 11) +
                            completar(v.getNombre(), 40) +
                            completar("", 40) +
                            completar(v.getIban().replace(" ", ""), 34) +
                            completar(task.conceptoTexto, 140);

                    registrosDeudores.add(completarRegistro(r03));
                }
            } catch (Exception e) {
                log.error("❌ Error en tarea paralela del vecino {}: {}", v.getNombre(), e.getMessage());
            }
        });

        // --- 3. ENSAMBLAJE FINAL DEL FICHERO ---
        StringBuilder finalFile = new StringBuilder();
        String r01 = "01" + CODIGO_NORMA_1915 + "001" + completar(idAcreedor, 35) + completar(comunidad.getNombre(), 40) + completar("", 20) + hoy;
        String r02 = "02" + CODIGO_NORMA_1915 + "002" + completar(idAcreedor, 35) + fCobro + completar(comunidad.getNombre(), 40) + completar(comunidad.getDireccion(), 40) + completar(comunidad.getPoblacion(), 40) + completar(comunidad.getCodigoPostal(), 10) + completar(ibanComunidad, 34);

        finalFile.append(completarRegistro(r01)).append("\n");
        finalFile.append(completarRegistro(r02)).append("\n");
        for (String record : registrosDeudores) {
            finalFile.append(record).append("\n");
        }

        String r04 = "04" + CODIGO_NORMA_1915 + "004" +
                completar(idAcreedor, 35) + fCobro +
                formatearImporte(totalRemesaAcumuladoBancario.get(), 17) +
                padLeft(String.valueOf(numRecibosEnFichero.get()), 8, '0') +
                padLeft(String.valueOf(numRecibosEnFichero.get() + 3), 10, '0');

        finalFile.append(completarRegistro(r04)).append("\n");

        log.info("✅ GTI TURBO FINALIZADO CON ÉXITO.");
        return finalFile.toString();
    }

    private Administrador inicializarAdmin(Administrador actual) {
        if (actual == null) return null;
        Administrador clon = new Administrador();
        clon.setNombre(actual.getNombre());
        clon.setEmail(actual.getEmail());
        clon.setSmtpHost(actual.getSmtpHost());
        clon.setSmtpPort(actual.getSmtpPort());
        clon.setSmtpUsername(actual.getSmtpUsername());
        clon.setSmtpPassword(actual.getSmtpPassword());
        clon.setSmtpAuth(actual.isSmtpAuth());
        clon.setSmtpStarttls(actual.isSmtpStarttls());
        return clon;
    }

    private String generarTextoConcepto(Vecino v, List<ConceptoCobro> conceptos) {
        String descripcionUnida = conceptos.stream()
                .map(ConceptoCobro::getDescripcion)
                .collect(Collectors.joining(" / "));
        String finca = (v.getVivienda() != null) ? v.getVivienda() : "";
        String res = ("CUOTA COMUNIDAD " + finca + ": " + descripcionUnida).trim();
        return res.length() > 140 ? res.substring(0, 140) : res;
    }

    private List<ConceptoCobro> filtrarConceptosPorPeriodo(Vecino v, int mesRemesa) {
        List<ConceptoCobro> aptos = new ArrayList<>();
        if (v.getListaConceptos() == null) return aptos;
        for (ConceptoCobro cc : v.getListaConceptos()) {
            if (cc != null && cc.isActivo() && cc.getImporte() != null && cc.getImporte().compareTo(BigDecimal.ZERO) > 0) {
                String p = (cc.getPeriodicidad() != null) ? cc.getPeriodicidad().toString() : "MENSUAL";
                int inicio = (cc.getMesInicio() != null) ? cc.getMesInicio() : 1;
                boolean corresponde = switch (p) {
                    case "BIMESTRAL" -> (mesRemesa - inicio) % 2 == 0;
                    case "TRIMESTRAL" -> (mesRemesa - inicio) % 3 == 0;
                    case "CUATRIMESTRAL" -> (mesRemesa - inicio) % 4 == 0;
                    case "SEMESTRAL" -> (mesRemesa - inicio) % 6 == 0;
                    case "ANUAL" -> mesRemesa == inicio;
                    default -> true;
                };
                if (corresponde) aptos.add(cc);
            }
        }
        return aptos;
    }

    private String completarRegistro(String contenido) {
        if (contenido.length() >= LONGITUD_REGISTRO) return contenido.substring(0, LONGITUD_REGISTRO);
        StringBuilder res = new StringBuilder(contenido);
        while (res.length() < LONGITUD_REGISTRO) res.append(" ");
        return res.toString();
    }

    private String normalizarTexto(String texto) {
        if (texto == null) return "";
        String temp = Normalizer.normalize(texto, Normalizer.Form.NFD);
        temp = DIACRITICS.matcher(temp).replaceAll("");
        return NON_ALPHANUMERIC.matcher(temp.toUpperCase().replace("Ñ", "N")).replaceAll("").trim();
    }

    private String completar(String texto, int longitud) {
        String res = normalizarTexto(texto);
        if (res.length() >= longitud) return res.substring(0, longitud);
        StringBuilder sb = new StringBuilder(res);
        while (sb.length() < longitud) sb.append(" ");
        return sb.toString();
    }

    private String formatearImporte(BigDecimal importe, int longitud) {
        if (importe == null) importe = BigDecimal.ZERO;
        long centimos = importe.multiply(new BigDecimal("100")).setScale(0, RoundingMode.HALF_UP).longValue();
        return padLeft(String.valueOf(centimos), longitud, '0');
    }

    private String padLeft(String s, int n, char c) {
        if (s.length() >= n) return s.substring(s.length() - n);
        StringBuilder sb = new StringBuilder();
        while (sb.length() < (n - s.length())) sb.append(c);
        sb.append(s);
        return sb.toString();
    }

    private static class RemesaTaskDTO {
        Vecino vecino;
        Recibo recibo;
        List<ConceptoCobro> conceptos;
        BigDecimal total;
        String conceptoTexto;
        RemesaTaskDTO(Vecino v, Recibo r, List<ConceptoCobro> c, BigDecimal t, String txt) {
            this.vecino = v; this.recibo = r; this.conceptos = c; this.total = t; this.conceptoTexto = txt;
        }
    }
}