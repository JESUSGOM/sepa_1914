package com.sepa1914.adminservice.service;

import com.lowagie.text.*;
import com.lowagie.text.Font;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.*;
import com.sepa1914.adminservice.model.Comunidad;
import com.sepa1914.adminservice.model.ConceptoCobro;
import com.sepa1914.adminservice.model.Vecino;
import com.sepa1914.adminservice.model.Recibo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Servicio integral para la generación de documentos PDF.
 * MANTIENE TODA LA LÓGICA DE ITEXT Y FLYING SAUCER.
 */
@Service
public class PdfService {

    @Autowired
    private TemplateEngine templateEngine;

    @Value("${app.recibos.pdf.path}")
    private String pdfPath;

    /**
     * Genera un recibo individual, lo guarda en el disco y devuelve la ruta.
     * Utilizado por SepaService para el envío de correos.
     */
    public String generarReciboPdfLocal(Recibo recibo, String nombreFichero) {
        try {
            File folder = new File(pdfPath);
            if (!folder.exists()) folder.mkdirs();

            Map<String, Object> data = new HashMap<>();
            data.put("recibo", recibo);
            data.put("comunidad", recibo.getComunidad());
            data.put("vecino", recibo.getVecino());

            byte[] pdfBytes = generarPdfDesdePlantilla("recibo-template", data);

            // Limpieza de nombre de fichero para evitar errores de sistema operativo
            String nombreLimpio = nombreFichero.replaceAll("[^a-zA-Z0-9.-]", "_");
            String fullPath = pdfPath + nombreLimpio + ".pdf";

            try (FileOutputStream fos = new FileOutputStream(fullPath)) {
                fos.write(pdfBytes);
            }
            return fullPath;
        } catch (Exception e) {
            throw new RuntimeException("Error al guardar PDF en disco: " + e.getMessage());
        }
    }

    /**
     * Genera un PDF a partir de una plantilla HTML de Thymeleaf.
     */
    public byte[] generarPdfDesdePlantilla(String templateName, Map<String, Object> data) {
        try {
            Context context = new Context();
            context.setVariables(data);

            String htmlContent = templateEngine.process("pdf/" + templateName, context);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            ITextRenderer renderer = new ITextRenderer();

            renderer.setDocumentFromString(htmlContent);
            renderer.layout();
            renderer.createPDF(outputStream);

            return outputStream.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Error al generar PDF desde plantilla: " + e.getMessage(), e);
        }
    }

    /**
     * Informe de Vecinos con Importes (iText).
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
                cell.setPadding(5);
                table.addCell(cell);
            }

            for (Vecino v : vecinos) {
                String infoVecino = v.getNombre() + "\nRef: " + v.getVivienda();
                table.addCell(new Phrase(infoVecino, fontTablaCuerpo));

                StringBuilder desglose = new StringBuilder();
                for (ConceptoCobro c : v.getListaConceptos()) {
                    if (c.isActivo()) {
                        desglose.append("- ").append(c.getDescripcion())
                                .append(": ").append(String.format("%.2f", c.getImporte())).append("€\n");
                    }
                }
                table.addCell(new Phrase(desglose.length() > 0 ? desglose.toString() : "Sin conceptos", fontTablaCuerpo));

                PdfPCell cellMonto = new PdfPCell(new Phrase(String.format("%.2f", v.getImporteTotalConceptos()) + " €", fontTablaCuerpo));
                cellMonto.setHorizontalAlignment(Element.ALIGN_RIGHT);
                table.addCell(cellMonto);
            }
            document.add(table);

            Paragraph pTotal = new Paragraph("\nTOTAL REMESA: " + String.format("%.2f", totalRemesa) + " €", fontTitulo);
            pTotal.setAlignment(Element.ALIGN_RIGHT);
            document.add(pTotal);

            document.close();
        } catch (Exception e) {
            throw new RuntimeException("Error al generar resumen", e);
        }
        return out.toByteArray();
    }

    /**
     * Informe de Domiciliaciones (Landscape - iText).
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

            Paragraph title = new Paragraph("LISTADO DE DOMICILIACIONES BANCARIAS POR COMUNIDAD", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(20);
            document.add(title);

            for (Comunidad comunidad : comunidades) {
                document.add(new Paragraph("COMUNIDAD: " + comunidad.getNombre(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11)));
                PdfPTable table = new PdfPTable(new float[]{3f, 1.5f, 3.5f, 3f, 1.2f});
                table.setWidthPercentage(100);

                String[] headers = {"Propietario", "Vivienda", "IBAN", "Conceptos", "Total"};
                for (String h : headers) {
                    PdfPCell cell = new PdfPCell(new Phrase(h, headerFont));
                    cell.setBackgroundColor(Color.BLACK);
                    cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                    cell.setPadding(4);
                    table.addCell(cell);
                }

                List<Vecino> listaVecinos = vecinosPorComunidad.get(comunidad.getId());
                if (listaVecinos != null) {
                    for (Vecino v : listaVecinos) {
                        table.addCell(new Phrase(v.getNombre(), bodyFont));
                        table.addCell(new Phrase(v.getVivienda(), bodyFont));
                        table.addCell(new Phrase(v.getIban() != null ? v.getIban() : "PENDIENTE", bodyFont));

                        StringBuilder sb = new StringBuilder();
                        for (ConceptoCobro c : v.getListaConceptos()) {
                            if (c.isActivo()) {
                                sb.append(c.getDescripcion()).append(" (").append(String.format("%.2f", c.getImporte())).append("€)\n");
                            }
                        }
                        table.addCell(new Phrase(sb.toString(), bodyFont));

                        PdfPCell cTotal = new PdfPCell(new Phrase(String.format("%.2f", v.getImporteTotalConceptos()) + " €", bodyFont));
                        cTotal.setHorizontalAlignment(Element.ALIGN_RIGHT);
                        table.addCell(cTotal);
                    }
                }
                document.add(table);
                document.add(new Paragraph("\n"));
            }
            document.close();
        } catch (Exception e) {
            throw new RuntimeException("Error al generar listado", e);
        }
        return out.toByteArray();
    }

    /**
     * Genera la Orden de Mandato SEPA (iText).
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
            document.add(tit);
            document.add(new Paragraph(" "));

            document.add(new Paragraph("DATOS DEL ACREEDOR:", smallBold));
            document.add(new Paragraph(comunidad.getNombre(), normalFont));
            document.add(new Paragraph("ID Acreedor: " + comunidad.getIdentificadorAcreedor(), normalFont));
            document.add(new Paragraph(" "));

            document.add(new Paragraph("DATOS DEL DEUDOR (PAGADOR):", smallBold));
            document.add(new Paragraph("Nombre: " + vecino.getNombre(), normalFont));
            document.add(new Paragraph("NIF: " + vecino.getNif(), normalFont));
            document.add(new Paragraph("Vivienda: " + vecino.getVivienda(), normalFont));
            document.add(new Paragraph("IBAN: " + vecino.getIban(), normalFont));

            document.add(new Paragraph("\nMediante la firma de esta orden de domiciliación, el deudor autoriza al acreedor a enviar instrucciones a la entidad del deudor para adeudar su cuenta...", normalFont));
            document.add(new Paragraph("\n\nFecha: _________________  Firma: ___________________________", normalFont));

            document.close();
        } catch (Exception e) {
            throw new RuntimeException("Error al generar mandato", e);
        }
        return out.toByteArray();
    }

    private void addInfoCell(PdfPTable table, String label, String value, Font fLabel, Font fValue) {
        PdfPCell cL = new PdfPCell(new Phrase(label, fLabel));
        cL.setBorder(Rectangle.NO_BORDER);
        table.addCell(cL);
        PdfPCell cV = new PdfPCell(new Phrase(value != null ? value : "---", fValue));
        cV.setBorder(Rectangle.NO_BORDER);
        table.addCell(cV);
    }
}