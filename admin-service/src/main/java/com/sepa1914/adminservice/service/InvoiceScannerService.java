package com.sepa1914.adminservice.service;

import com.sepa1914.adminservice.dto.GastoDTO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import org.apache.pdfbox.rendering.PDFRenderer;

import java.awt.image.BufferedImage;

@Service
public class InvoiceScannerService {

    static {
        // Configuración robusta PDFBox
        System.setProperty("org.apache.pdfbox.rendering.UsePureJavaCMAP", "true");
        System.setProperty("org.apache.pdfbox.fontcache", "false");
    }

    public GastoDTO analizarFactura(MultipartFile file) {
        try {
            if (file == null || file.isEmpty()) {
                return new GastoDTO("Error", "", "", "", "");
            }

            String text = extraerTextoSeguro(file);

            if (text == null || text.trim().isEmpty()) {
                return new GastoDTO("No se pudo leer", "", "", "", "");
            }

            // Detectar proveedor
            String proveedor = detectarProveedor(text);

            // Extraer datos
            String fecha = extraerFecha(text);
            String importe = extraerImporte(text);
            String numeroFactura = extraerNumeroFactura(text);

            return new GastoDTO(proveedor, fecha, importe, numeroFactura, "");

        } catch (Exception e) {
            System.err.println("Error analizando factura: " + e.getMessage());
            return new GastoDTO("Error", "", "", "", "");
        }
    }

    /**
     * 🔴 MÉTODO CRÍTICO CORREGIDO
     */
    private String extraerTextoSeguro(MultipartFile file) {
        try (PDDocument doc = Loader.loadPDF(file.getBytes())) {

            doc.setAllSecurityToBeRemoved(true);

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);

            String texto = "";

            try {
                texto = stripper.getText(doc);
            } catch (Exception e) {
                System.err.println("PDF parcialmente ilegible: " + e.getMessage());
            }

            // 🔍 LOG CLAVE
            System.out.println("TEXTO EXTRAIDO LONGITUD: " +
                    (texto != null ? texto.length() : "null"));

            // 👉 FORZAMOS OCR SI TEXTO INSUFICIENTE
            if (texto == null || texto.trim().length() < 20) {
                System.out.println("⚠️ PDF sin texto → usando OCR...");
                return aplicarOCR(doc);
            }

            return texto;

        } catch (Exception e) {
            System.err.println("Error cargando PDF: " + e.getMessage());
            return "";
        }
    }

    /**
     * OCR con logs de depuración
     */
    private String aplicarOCR(PDDocument document) {
        try {
            System.out.println(">>> OCR EJECUTADO <<<");

            ITesseract tesseract = new Tesseract();

            // Ruta instalación (ajústala si es necesario)
            tesseract.setDatapath("C:\\Program Files\\Tesseract-OCR\\tessdata");
            tesseract.setLanguage("spa"); // prueba "eng" si falla

            PDFRenderer pdfRenderer = new PDFRenderer(document);

            StringBuilder resultado = new StringBuilder();

            for (int page = 0; page < document.getNumberOfPages(); ++page) {

                BufferedImage image = pdfRenderer.renderImageWithDPI(page, 400);

                String texto = tesseract.doOCR(image);

                System.out.println("----- OCR PAGE " + page + " -----");
                System.out.println(texto);

                resultado.append(texto).append("\n");
            }

            return resultado.toString();

        } catch (Exception e) {
            System.err.println("Error OCR: " + e.getMessage());
            return "";
        }
    }

    /**
     * Detecta proveedor por contenido
     */
    private String detectarProveedor(String text) {
        text = text.toLowerCase();

        if (text.contains("endesa")) return "Endesa";
        if (text.contains("iberdrola")) return "Iberdrola";
        if (text.contains("naturgy")) return "Naturgy";
        if (text.contains("movistar")) return "Movistar";
        if (text.contains("vodafone")) return "Vodafone";

        return "Proveedor desconocido";
    }

    /**
     * Extrae fecha
     */
    private String extraerFecha(String text) {
        Pattern pattern = Pattern.compile("(\\d{2}/\\d{2}/\\d{4})");
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return "";
    }

    /**
     * Extrae importe
     */
    private String extraerImporte(String text) {
        Pattern pattern = Pattern.compile("(\\d+[.,]\\d{2})\\s?€");
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return "";
    }

    /**
     * Extrae número de factura
     */
    private String extraerNumeroFactura(String text) {
        Pattern pattern = Pattern.compile("(factura\\s*n[ºo]?\\s*[:\\-]?\\s*\\w+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);

        if (matcher.find()) {
            return matcher.group(1);
        }

        return "";
    }
}