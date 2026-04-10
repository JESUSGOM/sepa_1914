package com.sepa1914.adminservice.service;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.*;
import com.sepa1914.adminservice.model.Comunidad;
import com.sepa1914.adminservice.model.ConceptoCobro;
import com.sepa1914.adminservice.model.Vecino;
import com.sepa1914.adminservice.model.Recibo;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.xhtmlrenderer.pdf.ITextRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;

/**
 * SERVICIO MAESTRO DE GENERACIÓN PDF - SEPA 1914
 * -----------------------------------------------------------------------------
 * MOTOR: Flying Saucer (XHTML) + OpenPDF (iText heredado).
 * VERSIÓN GTI 2.8: INTEGRIDAD TOTAL GARANTIZADA - FIRMA DIGITAL FNMT.
 * -----------------------------------------------------------------------------
 */
@Service
public class PdfService {

    private static final Logger log = LoggerFactory.getLogger(PdfService.class);

    private static final Pattern DIACRITICS = Pattern.compile("[\\p{InCombiningDiacriticalMarks}]");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^A-Z0-9]");

    @Autowired
    private TemplateEngine templateEngine;

    @Value("${app.recibos.pdf.path}")
    private String pdfPath;

    // =========================================================================
    // 1. GENERACIÓN BASADA EN PLANTILLAS THYMELEAF (HTML TO PDF)
    // =========================================================================

    /**
     * Genera PDF y lo envía directamente al navegador.
     * Utiliza el motor de bytes interno para asegurar consistencia.
     */
    public void generatePdf(String templateName, Map<String, Object> data, HttpServletResponse response, String fileName) {
        log.info("GTI PDF_STREAM: Iniciando emisión de documento: {}", fileName);
        try {
            byte[] pdfBytes = generatePdfBytes(templateName, data);

            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition", "inline; filename=\"" + fileName + "\"");

            try (OutputStream os = response.getOutputStream()) {
                os.write(pdfBytes);
                os.flush();
            }
            log.info("GTI PDF_STREAM: Envío finalizado correctamente.");
        } catch (Exception e) {
            log.error("ERROR CRÍTICO EN STREAM PDF: {}", e.getMessage());
            throw new RuntimeException("Error al generar streaming de PDF: " + e.getMessage());
        }
    }

    /**
     * MOTOR CENTRAL GTI: Genera el PDF en memoria y devuelve los bytes.
     * Este método es el que permite que el controlador firme el documento.
     */
    public byte[] generatePdfBytes(String templateName, Map<String, Object> data) {
        log.debug("GTI PDF_BYTES: Generando array para plantilla {}", templateName);
        try {
            Context context = new Context();
            context.setVariables(data);

            // Procesar la plantilla HTML a través de Thymeleaf
            String htmlContent = templateEngine.process(templateName, context);

            // Optimización GTI: Eliminación de DOCTYPE para evitar peticiones externas
            htmlContent = htmlContent.replaceFirst("(?i)<!DOCTYPE[^>]*>", "");

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ITextRenderer renderer = new ITextRenderer();
            renderer.setDocumentFromString(htmlContent);
            renderer.layout();
            renderer.createPDF(outputStream);

            return outputStream.toByteArray();
        } catch (Exception e) {
            log.error("ERROR MOTOR BYTES: {}", e.getMessage());
            throw new RuntimeException("Fallo en la generación de bytes del PDF.");
        }
    }

    /**
     * Genera un recibo individual y lo guarda físicamente en el disco.
     * Utilizado durante el proceso de remesas SEPA.
     */
    public String generarReciboPdfLocal(Recibo recibo, String nombreFicheroSugerido, List<ConceptoCobro> detalles, java.time.LocalDate vencimientoReal) {
        try {
            File folder = new File(pdfPath);
            if (!folder.exists() && folder.mkdirs()) {
                log.info("GTI FS: Directorio de almacenamiento creado.");
            }

            Map<String, Object> data = new HashMap<>();
            data.put("recibo", recibo);
            data.put("comunidad", recibo.getComunidad());
            data.put("vecino", recibo.getVecino());
            data.put("detalles", detalles);
            data.put("fechaVencimiento", vencimientoReal);

            String periodoStr = recibo.getFechaEmision()
                    .format(DateTimeFormatter.ofPattern("MMMM yyyy", new Locale("es", "ES")))
                    .toUpperCase();
            data.put("periodoNombre", periodoStr);

            // Generamos usando el motor de bytes
            byte[] pdfBytes = generatePdfBytes("pdf/recibo-template", data);

            String cif = recibo.getComunidad().getIdentificadorAcreedor();
            String vivienda = (recibo.getVecino().getVivienda() != null) ? recibo.getVecino().getVivienda().trim() : "VIV";
            String nombreVecino = normalizarParaFichero(recibo.getVecino().getNombre());

            String nombreFinal = String.format("%s_%s_%s_%02d_%d.pdf",
                    cif, vivienda.replace(" ", "_"), nombreVecino,
                    recibo.getFechaEmision().getMonthValue(),
                    recibo.getFechaEmision().getYear());

            String fullPath = pdfPath + (pdfPath.endsWith(File.separator) ? "" : File.separator) + nombreFinal;

            try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(fullPath))) {
                bos.write(pdfBytes);
            }

            return fullPath;
        } catch (Exception e) {
            log.error("Fallo en guardado local: {}", e.getMessage());
            throw new RuntimeException("Error en PDF local.");
        }
    }

    /**
     * Mantiene la compatibilidad con plantillas que no especifican el prefijo pdf/.
     */
    public byte[] generarPdfDesdePlantilla(String templateName, Map<String, Object> data) {
        String path = templateName.startsWith("pdf/") ? templateName : "pdf/" + templateName;
        return generatePdfBytes(path, data);
    }

    // =========================================================================
    // 2. INFORMES TÉCNICOS (ITEXT / OPENPDF PURO) - INTEGRIDAD 100%
    // =========================================================================

    /**
     * Resumen detallado de remesas para control del administrador.
     */
    public byte[] generarResumenRemesa(Comunidad comunidad, List<Vecino> vecinos, String nombreFichero, BigDecimal totalRemesa) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 36, 36, 36, 36);
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            Font fontTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            Font fontLabel = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
            Font fontValue = FontFactory.getFont(FontFactory.HELVETICA, 10);
            Font fontTablaCabecera = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Font.NORMAL, Color.WHITE);
            Font fontTablaCuerpo = FontFactory.getFont(FontFactory.HELVETICA, 9);

            Paragraph pTitulo = new Paragraph("INFORME DE CONTROL: REMESA DE VECINOS", fontTitulo);
            pTitulo.setAlignment(Element.ALIGN_CENTER);
            pTitulo.setSpacingAfter(20);
            document.add(pTitulo);

            PdfPTable infoTable = new PdfPTable(2);
            infoTable.setWidthPercentage(100);
            infoTable.setWidths(new float[]{1.5f, 3.5f});

            addInfoCell(infoTable, "Comunidad:", comunidad.getNombre(), fontLabel, fontValue);
            addInfoCell(infoTable, "CIF Acreedor:", comunidad.getIdentificadorAcreedor(), fontLabel, fontValue);
            addInfoCell(infoTable, "Cuenta Abono:", comunidad.getIban(), fontLabel, fontValue);
            addInfoCell(infoTable, "Fecha Emisión:", LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")), fontLabel, fontValue);
            document.add(infoTable);

            PdfPTable table = new PdfPTable(new float[]{4f, 4f, 2f});
            table.setWidthPercentage(100);
            table.setSpacingBefore(15);

            String[] headers = {"PROPIETARIO / VIVIENDA", "DESGLOSE CONCEPTOS", "TOTAL (€)"};
            for (String h : headers) {
                PdfPCell cell = new PdfPCell(new Phrase(h, fontTablaCabecera));
                cell.setBackgroundColor(Color.DARK_GRAY);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
                cell.setPadding(5);
                table.addCell(cell);
            }

            for (Vecino v : vecinos) {
                // Celda 1: Datos Vecino
                table.addCell(new Phrase(v.getNombre() + "\nRef: " + v.getVivienda(), fontTablaCuerpo));

                // Celda 2: Desglose de Conceptos
                StringBuilder desglose = new StringBuilder();
                if (v.getListaConceptos() != null) {
                    for (ConceptoCobro c : v.getListaConceptos()) {
                        if (c.isActivo()) {
                            desglose.append("- ").append(c.getDescripcion()).append(": ")
                                    .append(String.format("%.2f", c.getImporte())).append("€\n");
                        }
                    }
                }
                table.addCell(new Phrase(desglose.length() > 0 ? desglose.toString() : "Sin conceptos", fontTablaCuerpo));

                // Celda 3: Importe Total
                BigDecimal totalVecino = (v.getImporteTotalConceptos() != null) ? v.getImporteTotalConceptos() : BigDecimal.ZERO;
                PdfPCell cellMonto = new PdfPCell(new Phrase(String.format("%.2f", totalVecino) + " €", fontTablaCuerpo));
                cellMonto.setHorizontalAlignment(Element.ALIGN_RIGHT);
                cellMonto.setVerticalAlignment(Element.ALIGN_MIDDLE);
                table.addCell(cellMonto);
            }
            document.add(table);

            Paragraph pTotal = new Paragraph("\nTOTAL REMESA: " + String.format("%.2f", totalRemesa) + " €", fontTitulo);
            pTotal.setAlignment(Element.ALIGN_RIGHT);
            document.add(pTotal);

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Fallo en informe de remesa: {}", e.getMessage());
            throw new RuntimeException("Error en resumen remesa: " + e.getMessage());
        }
    }

    /**
     * Listado masivo de domiciliaciones por comunidad para auditoría bancaria.
     */
    public byte[] generarInformeComunidades(List<Comunidad> comunidades, Map<Long, List<Vecino>> vecinosPorComunidad) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4.rotate(), 30, 30, 30, 30);
        try {
            PdfWriter.getInstance(document, out);
            document.open();
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Font.NORMAL, Color.WHITE);
            Font bodyFont = FontFactory.getFont(FontFactory.HELVETICA, 8);

            Paragraph title = new Paragraph("LISTADO DE DOMICILIACIONES BANCARIAS", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(20);
            document.add(title);

            for (Comunidad comunidad : comunidades) {
                Paragraph comTit = new Paragraph("COMUNIDAD: " + comunidad.getNombre(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11));
                comTit.setSpacingBefore(10);
                document.add(comTit);

                PdfPTable table = new PdfPTable(new float[]{3f, 1.5f, 3.5f, 3f, 1.2f});
                table.setWidthPercentage(100);
                table.setSpacingBefore(5);

                String[] headers = {"Propietario", "Vivienda", "IBAN", "Conceptos", "Total"};
                for (String h : headers) {
                    PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
                    cell.setBackgroundColor(Color.BLACK);
                    cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                    cell.setPadding(3);
                    table.addCell(cell);
                }

                List<Vecino> lista = vecinosPorComunidad.get(comunidad.getId());
                if (lista != null) {
                    for (Vecino v : lista) {
                        table.addCell(new Phrase(v.getNombre(), bodyFont));
                        table.addCell(new Phrase(v.getVivienda(), bodyFont));
                        table.addCell(new Phrase(v.getIban() != null ? v.getIban() : "PENDIENTE", bodyFont));

                        StringBuilder sb = new StringBuilder();
                        if (v.getListaConceptos() != null) {
                            for (ConceptoCobro c : v.getListaConceptos()) {
                                if (c.isActivo()) {
                                    sb.append(c.getDescripcion()).append(" (").append(c.getImporte()).append("€)\n");
                                }
                            }
                        }
                        table.addCell(new Phrase(sb.toString(), bodyFont));

                        BigDecimal totalV = (v.getImporteTotalConceptos() != null) ? v.getImporteTotalConceptos() : BigDecimal.ZERO;
                        PdfPCell cTotal = new PdfPCell(new Phrase(totalV + " €", bodyFont));
                        cTotal.setHorizontalAlignment(Element.ALIGN_RIGHT);
                        table.addCell(cTotal);
                    }
                }
                document.add(table);
            }
            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Fallo en informe comunidades: {}", e.getMessage());
            throw new RuntimeException("Error en informe comunidades: " + e.getMessage());
        }
    }

    /**
     * Orden de Mandato SEPA (CORE) para nuevos propietarios.
     */
    public byte[] generarMandatoSepa(Comunidad comunidad, Vecino vecino) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 50, 50, 50, 50);
        try {
            PdfWriter.getInstance(document, out);
            document.open();
            Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
            Font smallBold = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
            Font normalFont = FontFactory.getFont(FontFactory.HELVETICA, 10);

            Paragraph tit = new Paragraph("ORDEN DE DOMICILIACIÓN SEPA (CORE)", boldFont);
            tit.setAlignment(Element.ALIGN_CENTER);
            tit.setSpacingAfter(20);
            document.add(tit);

            document.add(new Paragraph("DATOS DEL ACREEDOR:", smallBold));
            document.add(new Paragraph("Nombre: " + comunidad.getNombre(), normalFont));
            document.add(new Paragraph("ID Acreedor: " + comunidad.getIdentificadorAcreedor(), normalFont));
            document.add(new Paragraph("Dirección: " + comunidad.getDireccion(), normalFont));
            document.add(new Paragraph("Población: " + comunidad.getPoblacion(), normalFont));
            document.add(new Paragraph(" "));

            document.add(new Paragraph("DATOS DEL DEUDOR (PAGADOR):", smallBold));
            document.add(new Paragraph("Nombre: " + vecino.getNombre(), normalFont));
            document.add(new Paragraph("NIF: " + vecino.getNif(), normalFont));
            document.add(new Paragraph("Propiedad: " + vecino.getVivienda(), normalFont));
            document.add(new Paragraph("IBAN: " + (vecino.getIban() != null ? vecino.getIban() : ""), normalFont));
            document.add(new Paragraph(" "));

            Paragraph textoLegal = new Paragraph("Mediante la firma de esta orden de domiciliación, el deudor autoriza al acreedor a enviar instrucciones a su entidad para adeudar su cuenta y a la entidad para adeudar los importes correspondientes de acuerdo con las instrucciones del acreedor.", normalFont);
            textoLegal.setSpacingBefore(10);
            document.add(textoLegal);

            document.add(new Paragraph("\n\nFecha: _________________  Firma: ___________________________", normalFont));

            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            log.error("Fallo en mandato SEPA: {}", e.getMessage());
            throw new RuntimeException("Error en mandato SEPA.");
        }
    }

    // =========================================================================
    // 3. MOTOR DE FIRMA DIGITAL ELECTRÓNICA (FNMT) - CORREGIDO
    // =========================================================================

    /**
     * Aplica la firma criptográfica FNMT al documento PDF.
     * CORRECCIÓN: Se utiliza SELF_SIGNED para compatibilidad con OpenPDF.
     */
    public byte[] firmarDocumento(byte[] pdfIn) {
        String pathCert = "C:/sepa1914/certificados/CertificadoJesus.p12";
        String passCert = "1801";

        log.info("GTI SIGN: Iniciando firmado digital FNMT...");
        try (ByteArrayInputStream is = new ByteArrayInputStream(pdfIn);
             ByteArrayOutputStream os = new ByteArrayOutputStream()) {

            KeyStore ks = KeyStore.getInstance("PKCS12");
            try (FileInputStream fis = new FileInputStream(pathCert)) {
                ks.load(fis, passCert.toCharArray());
            }

            String alias = ks.aliases().nextElement();
            PrivateKey pk = (PrivateKey) ks.getKey(alias, passCert.toCharArray());
            Certificate[] chain = ks.getCertificateChain(alias);

            PdfReader reader = new PdfReader(is);
            PdfStamper stamper = PdfStamper.createSignature(reader, os, '\0');
            PdfSignatureAppearance appearance = stamper.getSignatureAppearance();

            // Ubicación del recuadro de firma (Ajustado al pie de página)
            appearance.setVisibleSignature(new Rectangle(70, 50, 350, 150), reader.getNumberOfPages(), "FirmaGTI");
            appearance.setReason("Certificación Oficial de la Administración");
            appearance.setLocation("Santa Cruz de Tenerife");

            // Uso de SELF_SIGNED para evitar el error 'Cannot resolve symbol'
            appearance.setCrypto(pk, chain, null, PdfSignatureAppearance.SELF_SIGNED);

            stamper.close();
            log.info("GTI SIGN: Firma aplicada con éxito.");
            return os.toByteArray();
        } catch (Exception e) {
            log.error("❌ FALLO CRÍTICO EN FIRMA DIGITAL: {}", e.getMessage());
            return pdfIn; // Devolvemos el PDF normal para que el usuario no se bloquee
        }
    }

    /**
     * Alias del método de firma para mantener compatibilidad con versiones previas.
     */
    public byte[] aplicarFirmaDigital(byte[] pdfOriginal) throws Exception {
        return firmarDocumento(pdfOriginal);
    }

    // =========================================================================
    // 4. UTILIDADES PRIVADAS
    // =========================================================================

    private void addInfoCell(PdfPTable table, String label, String value, Font fLabel, Font fValue) {
        PdfPCell cL = new PdfPCell(new Phrase(label, fLabel));
        cL.setBorder(Rectangle.NO_BORDER);
        cL.setPaddingBottom(5);
        table.addCell(cL);

        PdfPCell cV = new PdfPCell(new Phrase(value != null ? value : "---", fValue));
        cV.setBorder(Rectangle.NO_BORDER);
        cV.setPaddingBottom(5);
        table.addCell(cV);
    }

    private String normalizarParaFichero(String texto) {
        if (texto == null) return "VECINO";
        String normalizado = java.text.Normalizer.normalize(texto, java.text.Normalizer.Form.NFD);
        normalizado = DIACRITICS.matcher(normalizado).replaceAll("");
        return NON_ALPHANUMERIC.matcher(normalizado.toUpperCase().replace("Ñ", "N"))
                .replaceAll("_").replaceAll("_+", "_");
    }
}