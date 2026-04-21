package com.sepa1914.adminservice.service;

import com.sepa1914.adminservice.model.Acta;
import com.sepa1914.adminservice.model.EstadoActa;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfStamper;
import com.lowagie.text.pdf.PdfSignatureAppearance;
import com.lowagie.text.pdf.PdfName;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfSignatureAppearance;

@Service
public class PdfActaService {

    @Value("${storage.path:C:/sepa1914/ficheros}")
    private String baseStorage;

    // 🛡️ Ajusta estas rutas a tu realidad
    private final String certPath = "C:/sepa1914/certificados/CertificadoJesus.p12";
    private final String certPassword = "1801";

    public String generarPdfActa(Acta acta) throws Exception {
        String folderPath = baseStorage + "/actas/comunidad_" + acta.getComunidad().getId();
        Files.createDirectories(Paths.get(folderPath));

        String fileName = (acta.getEstado() == EstadoActa.BORRADOR ? "Acta_" + acta.getId() + "_Preview.pdf" : "Acta_" + acta.getId() + "_FIRMADA.pdf");
        String fullPath = folderPath + "/" + fileName;

        // 1. Generar HTML
        String htmlContent = prepararHtml(acta);

        // 2. GENERACIÓN EN MEMORIA (Para evitar bloqueos de archivo de Windows)
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ITextRenderer renderer = new ITextRenderer();
        renderer.setDocumentFromString(htmlContent);
        renderer.layout();
        renderer.createPDF(baos);
        byte[] pdfBytes = baos.toByteArray();

        // 3. SELLO FNMT (Si no es borrador, firmamos los bytes en memoria)
        if (acta.getEstado() != EstadoActa.BORRADOR) {
            pdfBytes = firmarBytes(pdfBytes);
        }

        // 4. ESCRITURA FINAL AL DISCO
        // Usamos un bloque try para intentar guardar, si falla por estar abierto, al menos no rompe el flujo
        try (FileOutputStream fos = new FileOutputStream(fullPath)) {
            fos.write(pdfBytes);
            fos.flush();
        } catch (IOException e) {
            System.err.println("AVISO: El archivo " + fileName + " está abierto en otro programa. No se pudo actualizar en disco, pero se servirá la versión actual.");
        }

        return fullPath;
    }

    private String prepararHtml(Acta acta) {
        String html = "<html><head><style>" +
                "body { font-family: 'Helvetica', sans-serif; margin: 50px; color: #333; }" +
                ".header { text-align: center; border-bottom: 2px solid #00458b; margin-bottom: 20px; padding-bottom: 10px; }" +
                ".comunidad { font-size: 18px; font-weight: bold; color: #00458b; text-transform: uppercase; }" +
                ".titulo { font-size: 22px; margin-top: 10px; font-weight: bold; }" +
                ".fecha { text-align: right; font-style: italic; margin-bottom: 30px; font-size: 14px; }" +
                ".contenido { line-height: 1.6; text-align: justify; }" +
                ".watermark { position: absolute; top: 40%; left: 10%; font-size: 80px; color: rgba(200, 0, 0, 0.1); transform: rotate(-45deg); z-index: -1; }" +
                "</style></head><body>";

        if (acta.getEstado() == EstadoActa.BORRADOR) {
            html += "<div class='watermark'>BORRADOR</div>";
        }

        html += "<div class='header'><div class='comunidad'>" + acta.getComunidad().getNombre() + "</div>";
        html += "<div class='titulo'>" + acta.getTitulo() + "</div></div>";
        html += "<div class='fecha'>En fecha: " + acta.getFechaReunion() + "</div>";

        // Limpieza de entidades para evitar error 500
        String contenidoSaneado = acta.getContenido()
                .replace("&nbsp;", "&#160;")
                .replace("&aacute;", "&#225;")
                .replace("&eacute;", "&#233;")
                .replace("&iacute;", "&#237;")
                .replace("&oacute;", "&#243;")
                .replace("&uacute;", "&#250;")
                .replace("&ntilde;", "&#241;")
                .replace("&euro;", "&#8364;");

        html += "<div class='contenido'>" + contenidoSaneado + "</div></body></html>";
        return html;
    }

    private byte[] firmarBytes(byte[] inputPdf) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (FileInputStream fis = new FileInputStream(certPath)) {
            ks.load(fis, certPassword.toCharArray());
        }

        String alias = ks.aliases().nextElement();
        PrivateKey pk = (PrivateKey) ks.getKey(alias, certPassword.toCharArray());
        Certificate[] chain = ks.getCertificateChain(alias);

        PdfReader reader = new PdfReader(inputPdf);
        ByteArrayOutputStream signedBaos = new ByteArrayOutputStream();

        PdfStamper stamper = PdfStamper.createSignature(reader, signedBaos, '\0');
        PdfSignatureAppearance appearance = stamper.getSignatureAppearance();

        appearance.setVisibleSignature(new Rectangle(70, 50, 350, 150), reader.getNumberOfPages(),"Firma GTI");

        // Uso de PdfName.ADOBE_PPKLITE para OpenPDF 2.0.3
        appearance.setCrypto(pk, chain, null, PdfSignatureAppearance.SELF_SIGNED);
        appearance.setReason("Certificación Oficial de la Administración");
        appearance.setLocation("España");
        appearance.setCertificationLevel(PdfSignatureAppearance.NOT_CERTIFIED);

        stamper.close();
        reader.close();

        return signedBaos.toByteArray();
    }
}