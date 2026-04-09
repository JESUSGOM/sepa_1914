package com.sepa1914.adminservice.service;

import com.sepa1914.adminservice.model.Administrador;
import com.sepa1914.adminservice.model.Comunidad;
import com.sepa1914.adminservice.model.Vecino;
import com.sepa1914.adminservice.model.ConceptoCobro;
import com.sepa1914.adminservice.model.Recibo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;
import java.text.Normalizer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Servicio de Generación de Ficheros SEPA 19-14 (Norma 19-15 COR1).
 * ACTUALIZADO: Soporte Multi-Administrador y envío dinámico de emails.
 */
@Service
public class SepaService {

    private static final Logger log = LoggerFactory.getLogger(SepaService.class);
    private static final int LONGITUD_REGISTRO = 600;
    private static final String CODIGO_NORMA_1915 = "19154";
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    // Optimización GTI: Patrones pre-compilados para evitar latencia en bases gigantes
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

    /**
     * Proceso principal de generación de remesa, contabilidad, PDF y envío de emails.
     */
    @Transactional
    public String generarCuaderno19(Comunidad comunidad, List<Vecino> vecinos, LocalDate fechaCobro) {
        log.info("🚀 INICIANDO REMESA MULTI-ADMIN: '{}' para el {}", comunidad.getNombre(), fechaCobro);

        // 1. LIMPIEZA DE DATOS PREVIOS (Evita duplicados si se repite el proceso)
        contabilidadService.limpiarContabilidadMesAntesDeRemesa(
                comunidad.getId(),
                fechaCobro.getMonthValue(),
                fechaCobro.getYear()
        );

        // Datos de cabecera
        Administrador admin = comunidad.getDatosAdministrador();
        String idAcreedor = comunidad.getIdentificadorAcreedor();
        String ibanComunidad = (comunidad.getIban() != null) ? comunidad.getIban().replace(" ", "") : "";
        String hoy = LocalDate.now().format(ISO_DATE);
        String fCobro = fechaCobro.format(ISO_DATE);
        int mesRemesa = fechaCobro.getMonthValue();
        int anioRemesa = fechaCobro.getYear();

        StringBuilder sb = new StringBuilder();

        // --- REGISTRO 01: CABECERA PRESENTADOR ---
        StringBuilder r01 = new StringBuilder();
        r01.append("01").append(CODIGO_NORMA_1915).append("001");
        r01.append(completar(idAcreedor, 35));
        r01.append(completar(comunidad.getNombre(), 40));
        r01.append(completar("", 20)).append(hoy);
        sb.append(completarRegistro(r01.toString())).append("\n");

        // --- REGISTRO 02: CABECERA ACREEDOR ---
        StringBuilder r02 = new StringBuilder();
        r02.append("02").append(CODIGO_NORMA_1915).append("002");
        r02.append(completar(idAcreedor, 35)).append(fCobro);
        r02.append(completar(comunidad.getNombre(), 40));
        r02.append(completar(comunidad.getDireccion(), 40));
        r02.append(completar(comunidad.getPoblacion(), 40));
        r02.append(completar(comunidad.getCodigoPostal(), 10));
        r02.append(completar(ibanComunidad, 34));
        sb.append(completarRegistro(r02.toString())).append("\n");

        // --- 3. PROCESAMIENTO INDIVIDUAL DE VECINOS ---
        BigDecimal totalRemesaAcumuladoBancario = BigDecimal.ZERO;
        int numRecibosEnFichero = 0;

        for (Vecino v : vecinos) {
            if (v == null || !v.isActivo()) continue;

            try {
                List<ConceptoCobro> conceptosAptos = filtrarConceptosPorPeriodo(v, mesRemesa);
                BigDecimal totalVecino = conceptosAptos.stream()
                        .map(ConceptoCobro::getImporte)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                if (totalVecino.compareTo(BigDecimal.ZERO) > 0) {
                    String conceptoDetallado = generarTextoConcepto(v, conceptosAptos);

                    // A. Contabilidad
                    Recibo reciboGenerado = contabilidadService.registrarDevengoCuota(v, totalVecino, conceptoDetallado, fechaCobro);

                    // B. PDF (Nombre de fichero único por vecino y periodo)
                    String nombreFicheroPdf = v.getNif() + "_" + v.getVivienda() + "_" + mesRemesa + "_" + anioRemesa;
                    String rutaPdf = pdfService.generarReciboPdfLocal(reciboGenerado, nombreFicheroPdf);

                    // C. Email Dinámico (Usando el servidor SMTP del Administrador)
                    if (v.getEmail() != null && !v.getEmail().isBlank()) {
                        String asunto = "Recibo " + comunidad.getNombre() + " - " + mesRemesa + "/" + anioRemesa;
                        String cuerpo = "Estimado/a " + v.getNombre() + ",\n\n" +
                                "Le adjuntamos el recibo de su cuota de comunidad correspondiente al mes " + mesRemesa + ".\n\n" +
                                "Saludos,\n" + comunidad.getNombre();

                        emailService.enviarReciboPorEmail(v.getEmail(), asunto, cuerpo, rutaPdf, admin);
                    }

                    // D. Inclusión en Cuaderno 19 (Solo si está domiciliado)
                    if (v.isDomiciliado() && v.getIban() != null && !v.getIban().isBlank()) {
                        numRecibosEnFichero++;
                        totalRemesaAcumuladoBancario = totalRemesaAcumuladoBancario.add(totalVecino);

                        StringBuilder r03 = new StringBuilder();
                        r03.append("03").append(CODIGO_NORMA_1915).append("003");
                        r03.append(completar(idAcreedor, 35));
                        r03.append(completar(v.getReferenciaMandato(), 35));
                        r03.append("RCUR").append("A");
                        r03.append(formatearImporte(totalVecino, 11));
                        r03.append("20240101");
                        r03.append(completar(v.getBic(), 11));
                        r03.append(completar(v.getNombre(), 40));
                        r03.append(completar("", 40));
                        r03.append(completar(v.getIban().replace(" ", ""), 34));
                        r03.append(completar(conceptoDetallado, 140));

                        sb.append(completarRegistro(r03.toString())).append("\n");
                    }
                }
            } catch (Exception e) {
                log.error("⚠️ Error procesando al vecino {}: {}", v.getNombre(), e.getMessage());
            }
        }

        // --- REGISTRO 04: TOTALES FINALES ---
        StringBuilder r04 = new StringBuilder();
        r04.append("04").append(CODIGO_NORMA_1915).append("004");
        r04.append(completar(idAcreedor, 35)).append(fCobro);
        r04.append(formatearImporte(totalRemesaAcumuladoBancario, 17));
        r04.append(padLeft(String.valueOf(numRecibosEnFichero), 8, '0'));
        r04.append(padLeft(String.valueOf(numRecibosEnFichero + 3), 10, '0'));
        sb.append(completarRegistro(r04.toString())).append("\n");

        log.info("✅ REMESA FINALIZADA: {} recibos bancarios, Total: {} €", numRecibosEnFichero, totalRemesaAcumuladoBancario);
        return sb.toString();
    }

    // --- MÉTODOS AUXILIARES ---

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
}