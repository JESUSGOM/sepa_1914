package com.sepa1914.adminservice.service;

import com.sepa1914.adminservice.model.Administrador;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.Objects;
import java.util.Properties;

/**
 * Servicio para el envío automático de recibos por correo electrónico.
 * CONFIGURACIÓN DINÁMICA: Permite que cada Administrador use su propio SMTP.
 * MANTIENE: Lógica de continuidad GTI, gestión de adjuntos PDF y UTF-8.
 * AJUSTE PROFESIONAL: Soporte para Puerto 465 (SSL directo) y 587 (STARTTLS).
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    /**
     * Envía el recibo generado en PDF al email del vecino de forma DINÁMICA.
     * @param to               Email del destinatario (vecino).
     * @param subject          Asunto del correo.
     * @param body             Cuerpo del mensaje.
     * @param pathToAttachment Ruta física del PDF generado.
     * @param admin            Objeto Administrador con sus credenciales SMTP.
     */
    @Async
    public void enviarReciboPorEmail(String to, String subject, String body, String pathToAttachment, Administrador admin) {

        // Verificación de seguridad: ¿El administrador tiene configurado el email?
        if (admin == null || admin.getSmtpHost() == null || admin.getSmtpUsername() == null) {
            log.error("❌ ABORTO DE ENVÍO: El administrador '{}' no tiene configurado su servidor SMTP.",
                    (admin != null ? admin.getNombre() : "NULO"));
            return;
        }

        log.info("📧 Iniciando envío DINÁMICO para: {} (Vía Admin: {})", to, admin.getNombre());

        try {
            // 1. CREACIÓN DEL MOTOR SMTP SOBRE LA MARCHA
            JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
            mailSender.setHost(admin.getSmtpHost());
            int puerto = admin.getSmtpPort() != null ? admin.getSmtpPort() : 587;
            mailSender.setPort(puerto);
            mailSender.setUsername(admin.getSmtpUsername());
            mailSender.setPassword(admin.getSmtpPassword());

            // 2. PROPIEDADES DE SEGURIDAD (Blindadas para Multi-Administrador)
            Properties props = mailSender.getJavaMailProperties();
            props.put("mail.smtp.auth", String.valueOf(admin.isSmtpAuth()));

            // LÓGICA INTELIGENTE DE PUERTOS:
            if (puerto == 465) {
                // Configuración para Cristina (SSL directo)
                props.put("mail.smtp.ssl.enable", "true");
                props.put("mail.smtp.starttls.enable", "false");
            } else {
                // Configuración para Jesús Francisco (STARTTLS)
                props.put("mail.smtp.ssl.enable", "false");
                props.put("mail.smtp.starttls.enable", String.valueOf(admin.isSmtpStarttls()));
            }

            props.put("mail.smtp.starttls.required", "false");
            props.put("mail.smtp.ssl.trust", admin.getSmtpHost());
            props.put("mail.smtp.ssl.checkserveridentity", "false");

            // Tiempos de espera para no bloquear el hilo si el servidor no responde
            props.put("mail.smtp.connectiontimeout", "10000");
            props.put("mail.smtp.timeout", "10000");
            props.put("mail.smtp.writetimeout", "10000");

            // 3. CONSTRUCCIÓN DEL MENSAJE
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(admin.getSmtpUsername());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body);

            // 4. GESTIÓN DEL ADJUNTO PDF
            if (pathToAttachment != null && !pathToAttachment.isBlank()) {
                File file = new File(pathToAttachment);
                if (file.exists() && file.isFile()) {
                    FileSystemResource res = new FileSystemResource(file);
                    String fileName = Objects.requireNonNullElse(res.getFilename(), "Recibo.pdf");
                    helper.addAttachment(fileName, res);
                    log.debug("📎 Adjunto acoplado con éxito: {}", fileName);
                } else {
                    log.warn("⚠️ Advertencia: Archivo adjunto no encontrado en: {}", pathToAttachment);
                }
            }

            // 5. ENVÍO EFECTIVO
            mailSender.send(message);
            log.info("✅ ¡ÉXITO! Email enviado correctamente a: {} usando el servidor del admin {}", to, admin.getSmtpHost());

        } catch (Exception e) {
            log.error("❌ ERROR CRÍTICO al enviar correo a {} vía {}: {}", to, admin.getSmtpHost(), e.getMessage());
            log.debug("Stacktrace detallado: ", e);
        }
    }
}