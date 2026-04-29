package com.sepa1914.adminservice.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.lowagie.text.Document;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.pdf.PdfWriter;
import com.sepa1914.adminservice.model.Comunidad;
import com.sepa1914.adminservice.repository.ComunidadRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.util.UUID;

@Service
public class QrPdfService {

    @Autowired
    private ComunidadRepository comunidadRepository;

    // URL base de tu servidor PHP
    private static final String URL_BASE = "https://jfgb.es/incidenciacomunidad/?t=";

    /**
     * Genera un PDF en blanco con el código QR centrado.
     * Utiliza un token persistente de la base de datos.
     */
    public byte[] generarPdfSoloQr(Comunidad comunidad) throws Exception {

        // 1. Gestión del Token (Persistencia)
        // Si la comunidad no tiene token, generamos uno nuevo y lo guardamos
        String token = comunidad.getTokenQr();
        if (token == null || token.isEmpty()) {
            token = UUID.randomUUID().toString(); // Genera un ID único aleatorio
            comunidad.setTokenQr(token);
            comunidadRepository.save(comunidad);
        }

        String urlFinal = URL_BASE + token;

        // 2. Generar imagen del QR (ZXing)
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        // Generamos un QR de 400x400 píxeles
        BitMatrix bitMatrix = qrCodeWriter.encode(urlFinal, BarcodeFormat.QR_CODE, 400, 400);

        ByteArrayOutputStream pngStream = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngStream);
        byte[] pngData = pngStream.toByteArray();

        // 3. Crear el documento PDF (OpenPDF / iText)
        ByteArrayOutputStream pdfStream = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4);
        PdfWriter.getInstance(document, pdfStream);

        document.open();

        // Convertimos los bytes del QR en un objeto Imagen de PDF
        Image qrImage = Image.getInstance(pngData);

        // Centrado absoluto en la página A4
        qrImage.setAlignment(Image.MIDDLE);

        // Ajustamos el tamaño para que se vea bien al imprimir
        qrImage.scaleToFit(450, 450);

        // Añadimos un margen superior para que no pegue arriba
        qrImage.setSpacingBefore(100f);

        document.add(qrImage);
        document.close();

        return pdfStream.toByteArray();
    }
}