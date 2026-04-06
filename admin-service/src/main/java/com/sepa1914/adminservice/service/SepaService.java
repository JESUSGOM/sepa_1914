package com.sepa1914.adminservice.service;

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

/**
 * Servicio de Generación de Ficheros SEPA 19-14 (Norma 19-15 COR1).
 * REPARADO: Soporte total a no domiciliados y coincidencia de argumentos.
 * NUEVO: Generación de recibos PDF y envío automático por email a propietarios.
 * MANTIENE: Toda la lógica de persistencia contable y filtrado de periodos.
 */
@Service
public class SepaService {

    private static final Logger log = LoggerFactory.getLogger(SepaService.class);
    private static final int LONGITUD_REGISTRO = 600;
    private static final String CODIGO_NORMA_1915 = "19154";
    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final ContabilidadService contabilidadService;
    private final PdfService pdfService;
    private final EmailService emailService;

    public SepaService(ContabilidadService contabilidadService, PdfService pdfService, EmailService emailService) {
        this.contabilidadService = contabilidadService;
        this.pdfService = pdfService;
        this.emailService = emailService;
    }

    /**
     * Genera el cuaderno bancario y registra los movimientos contables.
     * @Transactional asegura la integridad entre el archivo generado y la base de datos.
     */
    @Transactional
    public String generarCuaderno19(Comunidad comunidad, List<Vecino> vecinos, LocalDate fechaCobro) {
        log.info("### INICIANDO PROCESO SEPA 19-15: Comunidad '{}' - Fecha: {} ###", comunidad.getNombre(), fechaCobro);

        // 1. LIMPIEZA PREVIA
        contabilidadService.limpiarContabilidadMesAntesDeRemesa(
                comunidad.getId(),
                fechaCobro.getMonthValue(),
                fechaCobro.getYear()
        );

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
        r01.append(completar(normalizarTexto(comunidad.getNombre()), 40));
        r01.append(completar("", 20)).append(hoy);
        sb.append(completarRegistro(r01.toString())).append("\n");

        // --- REGISTRO 02: CABECERA ACREEDOR ---
        StringBuilder r02 = new StringBuilder();
        r02.append("02").append(CODIGO_NORMA_1915).append("002");
        r02.append(completar(idAcreedor, 35)).append(fCobro);
        r02.append(completar(normalizarTexto(comunidad.getNombre()), 40));
        r02.append(completar(normalizarTexto(comunidad.getDireccion()), 40));
        r02.append(completar(normalizarTexto(comunidad.getPoblacion()), 40));
        r02.append(completar(comunidad.getCodigoPostal(), 10));
        r02.append(completar(ibanComunidad, 34));
        sb.append(completarRegistro(r02.toString())).append("\n");

        // --- 3. PROCESAMIENTO DE RECIBOS Y ADEUDOS ---
        BigDecimal totalRemesaAcumuladoBancario = BigDecimal.ZERO;
        int numRecibosEnFichero = 0;
        int numRecibosContablesTotales = 0;

        for (Vecino v : vecinos) {
            if (v == null || !v.isActivo()) continue;

            List<ConceptoCobro> conceptosAptos = filtrarConceptosPorPeriodo(v, mesRemesa);

            BigDecimal totalVecino = conceptosAptos.stream()
                    .map(ConceptoCobro::getImporte)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (totalVecino.compareTo(BigDecimal.ZERO) > 0) {
                String conceptoDetallado = generarTextoConcepto(v, conceptosAptos);

                // --- A. PERSISTENCIA SIEMPRE (DOMICILIADO O NO) ---
                // REPARADO: Se obtiene el objeto Recibo para poder generar el PDF
                Recibo reciboGenerado = contabilidadService.registrarDevengoCuota(v, totalVecino, conceptoDetallado, fechaCobro);
                numRecibosContablesTotales++;

                // --- NUEVO: GENERACIÓN DE PDF ---
                // Nombre: NIF + Recibo Emitido a su cargo + Mes + Año
                String nombreFicheroPdf = idAcreedor + " Recibo Emitido a su cargo " + mesRemesa + " " + anioRemesa;
                String rutaPdf = pdfService.generarReciboPdfLocal(reciboGenerado, nombreFicheroPdf);

                // --- NUEVO: ENVÍO POR EMAIL ---
                if (v.getEmail() != null && !v.getEmail().trim().isEmpty()) {
                    emailService.enviarReciboPorEmail(v, reciboGenerado, rutaPdf);
                }

                // --- B. ADICIÓN AL FICHERO BANCARIO (SOLO SI ES DOMICILIADO Y TIENE IBAN) ---
                boolean tieneIbanValido = (v.getIban() != null && !v.getIban().trim().isEmpty());

                if (v.isDomiciliado() && tieneIbanValido) {
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
                    r03.append(completar(normalizarTexto(v.getNombre()), 40));
                    r03.append(completar("", 40));
                    r03.append(completar(v.getIban().replace(" ", ""), 34));
                    r03.append(completar(conceptoDetallado, 140));

                    sb.append(completarRegistro(r03.toString())).append("\n");
                } else {
                    log.info("Vecino {} procesado contablemente y PDF generado, pero omitido de remesa SEPA.", v.getNombre());
                }
            }
        }

        // --- REGISTRO 04: TOTALES FINALES ---
        StringBuilder r04 = new StringBuilder();
        r04.append("04").append(CODIGO_NORMA_1915).append("004");
        r04.append(completar(idAcreedor, 35)).append(fCobro);
        r04.append(formatearImporte(totalRemesaAcumuladoBancario, 17));
        r04.append(String.format("%08d", numRecibosEnFichero));
        r04.append(String.format("%010d", numRecibosEnFichero + 3));
        sb.append(completarRegistro(r04.toString())).append("\n");

        return sb.toString();
    }

    // =========================================================================
    // MÉTODOS AUXILIARES (MANTENIDOS ÍNTEGROS)
    // =========================================================================

    private String generarTextoConcepto(Vecino v, List<ConceptoCobro> conceptos) {
        StringBuilder sb = new StringBuilder();
        String finca = (v.getVivienda() != null) ? v.getVivienda() : "";
        sb.append("CUOTA COMUNIDAD ").append(finca).append(": ");
        for (int i = 0; i < conceptos.size(); i++) {
            sb.append(conceptos.get(i).getDescripcion());
            if (i < conceptos.size() - 1) sb.append(" / ");
        }
        String res = sb.toString().trim();
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
        return String.format("%-600s", contenido);
    }

    private String normalizarTexto(String texto) {
        if (texto == null) return "";
        String temp = Normalizer.normalize(texto, Normalizer.Form.NFD);
        temp = temp.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "");
        return temp.toUpperCase().replace("Ñ", "N").trim();
    }

    private String completar(String texto, int longitud) {
        if (texto == null) texto = "";
        String res = texto.trim().toUpperCase();
        return res.length() > longitud ? res.substring(0, longitud) : String.format("%-" + longitud + "s", res);
    }

    private String formatearImporte(BigDecimal importe, int longitud) {
        if (importe == null) importe = BigDecimal.ZERO;
        long centimos = importe.multiply(new BigDecimal("100")).setScale(0, RoundingMode.HALF_UP).longValue();
        return String.format("%0" + longitud + "d", centimos);
    }
}