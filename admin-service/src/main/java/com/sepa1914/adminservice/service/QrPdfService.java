package com.sepa1914.adminservice.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.lowagie.text.Document;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfWriter;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Element;
import com.lowagie.text.Paragraph;
import com.sepa1914.adminservice.model.Comunidad;
import com.sepa1914.adminservice.repository.ComunidadRepository;
import java.awt.Color;
import org.springframework.stereotype.Service;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*; // Necesario para PdfContentByte y BaseFont
import java.awt.Color;

import java.io.ByteArrayOutputStream;
import java.util.UUID;

@Service
public class QrPdfService {

    // URL base de tu servidor PHP
    private static final String URL_BASE = "https://jfgb.es/incidenciacomunidad/?t=";

    private final ComunidadRepository comunidadRepository;

    // 2. Creamos el constructor (Spring inyectará la dependencia automáticamente)
    public QrPdfService(ComunidadRepository comunidadRepository) {
        this.comunidadRepository = comunidadRepository;
    }

    /**
     * Genera un PDF en blanco con el código QR centrado.
     * Utiliza un token persistente de la base de datos.
     */
    public byte[] generarPdfSoloQr(Comunidad comunidad) throws Exception {

        // 1. Gestión del Token (Se mantiene igual)
        String token = comunidad.getTokenQr();
        if (token == null || token.isEmpty()) {
            token = UUID.randomUUID().toString();
            comunidad.setTokenQr(token);
            comunidadRepository.save(comunidad);
        }

        String urlFinal = URL_BASE + token;

        // 2. Generar imagen del QR (Se mantiene igual)
        QRCodeWriter qrCodeWriter = new QRCodeWriter();
        BitMatrix bitMatrix = qrCodeWriter.encode(urlFinal, BarcodeFormat.QR_CODE, 400, 400);

        ByteArrayOutputStream pngStream = new ByteArrayOutputStream();
        MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngStream);
        byte[] pngData = pngStream.toByteArray();

        // 3. Crear el documento PDF mejorado
        ByteArrayOutputStream pdfStream = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4);
        PdfWriter.getInstance(document, pdfStream);

        PdfWriter writer = PdfWriter.getInstance(document, pdfStream);
        document.open();

        // --- NUEVO: Definición de Fuentes ---
        Font fuenteTitulo = FontFactory.getFont(FontFactory.HELVETICA, 24, Font.BOLD, Color.BLUE);
        Font fuenteDireccion = FontFactory.getFont(FontFactory.HELVETICA, 16);
        Font fuenteExplicacion = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 12, Font.BOLD, Color.BLUE);

        // 1. Añadir Nombre de la Comunidad
        Paragraph titulo = new Paragraph(comunidad.getNombre(), fuenteTitulo);
        titulo.setAlignment(Element.ALIGN_CENTER);
        titulo.setSpacingBefore(30f);
        document.add(titulo);

        // 2. Añadir Domicilio Completo
        // Componemos la dirección usando los campos de la base de datos
        String domicilioText = comunidad.getDireccion() + "\n" +
                comunidad.getCodigoPostal() + " " +
                comunidad.getPoblacion() + " (" +
                comunidad.getProvincia() + ")";

        Paragraph domicilio = new Paragraph(domicilioText, fuenteDireccion);
        domicilio.setAlignment(Element.ALIGN_CENTER);
        domicilio.setSpacingBefore(10f);
        document.add(domicilio);

        // 3. Añadir el QR (Ajustamos tamaño para que quepa el texto)
        Image qrImage = Image.getInstance(pngData);
        qrImage.setAlignment(Image.ALIGN_CENTER);
        qrImage.scaleToFit(350, 350); // Bajamos de 450 a 350 para dejar espacio al texto
        qrImage.setSpacingBefore(40f);
        qrImage.setSpacingAfter(40f);
        document.add(qrImage);

        // 4. Añadir Texto Explicativo
        Paragraph explicacion = new Paragraph(
                "Utilice este código oficial para reportar cualquier avería o incidencia " +
                        "en los elementos comunes del edificio directamente al administrador.",
                fuenteExplicacion
        );
        explicacion.setAlignment(Element.ALIGN_CENTER);
        // Añadimos margen a los lados para que no ocupe todo el ancho
        explicacion.setIndentationLeft(50f);
        explicacion.setIndentationRight(50f);
        document.add(explicacion);

        // --- POSICIONAMIENTO ABSOLUTO (Aquí va tu código) ---
        // Obtenemos el "Direct Content" para dibujar sobre la capa final
        PdfContentByte cb = writer.getDirectContent();

        cb.beginText();
        // Definimos fuente para el dibujo manual
        BaseFont bf = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.CP1252, BaseFont.NOT_EMBEDDED);
        cb.setFontAndSize(bf, 8);
        cb.setColorFill(Color.GRAY);

        // x = 297.5 (centro de A4), y = 20 (distancia desde el borde inferior)
        cb.showTextAligned(PdfContentByte.ALIGN_CENTER,
                "© 2026. Gombeth Softwares's. Todos los derechos reservados.",
                297.5f, 20f, 0);
        cb.endText();




//        Paragraph footer = new Paragraph("© 2026. Gombeth Softwares's. Todos los derechos reservados.");
//        footer.setAlignment(Element.ALIGN_CENTER);
//        footer.setSpacingBefore(10f);
//        document.add(footer);

        document.close();

        return pdfStream.toByteArray();
    }
}