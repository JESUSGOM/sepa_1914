package com.sepa1914.adminservice.service;

import com.sepa1914.adminservice.model.Recibo;
import com.sepa1914.adminservice.model.Vecino;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.File;

/**
 * Servicio para el envío automático de recibos por correo electrónico.
 * Configurado para utilizar la cuenta sucomunidaddepropietarios@outlook.es
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);
    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Envía el recibo generado en PDF al email del vecino.
     * @param vecino El destinatario
     * @param recibo Datos del recibo para el cuerpo del mensaje
     * @param rutaAdjunto Ruta física del archivo PDF generado
     */
    public void enviarReciboPorEmail(Vecino vecino, Recibo recibo, String rutaAdjunto) {
        if (vecino.getEmail() == null || vecino.getEmail().trim().isEmpty()) {
            log.warn("El vecino {} no tiene email configurado. Omitiendo envío.", vecino.getNombre());
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            // true indica que es un mensaje multipart (para adjuntos)
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom("sucomunidaddepropietarios@outlook.es");
            helper.setTo(vecino.getEmail());
            helper.setSubject("Recibo de Comunidad - " + recibo.getComunidad().getNombre());

            // Cuerpo del mensaje en HTML
            String contenidoHtml = "<html><body style='font-family: sans-serif;'>" +
                    "<h2>Hola, " + vecino.getNombre() + "</h2>" +
                    "<p>Le enviamos adjunto el recibo de la comunidad correspondiente al mes actual.</p>" +
                    "<p><strong>Detalles:</strong></p>" +
                    "<ul>" +
                    "<li>Concepto: " + recibo.getConcepto() + "</li>" +
                    "<li>Importe: " + recibo.getImporte() + " €</li>" +
                    "</ul>" +
                    "<p>Atentamente,<br>La Administración de su Comunidad.</p>" +
                    "</body></html>";

            helper.setText(contenidoHtml, true);

            // Adjuntar el fichero PDF
            File f = new File(rutaAdjunto);
            if (f.exists()) {
                FileSystemResource res = new FileSystemResource(f);
                helper.addAttachment(f.getName(), res);

                mailSender.send(message);
                log.info("Email enviado con éxito a: {} (Recibo ID: {})", vecino.getEmail(), recibo.getId());
            } else {
                log.error("No se pudo enviar el email a {}: El archivo PDF no existe en {}", vecino.getEmail(), rutaAdjunto);
            }

        } catch (MessagingException e) {
            log.error("Error técnico al preparar el email para {}: {}", vecino.getEmail(), e.getMessage());
        } catch (Exception e) {
            log.error("Fallo inesperado al enviar correo a {}: {}", vecino.getEmail(), e.getMessage());
        }
    }
}