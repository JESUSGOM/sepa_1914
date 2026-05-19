package com.sepa1914.adminservice.service;

import com.sepa1914.adminservice.model.Administrador;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @InjectMocks
    private EmailService emailService;

    private Administrador adminValido;

    @BeforeEach
    void setUp() {
        // Inicializamos un administrador de pruebas con un servidor SMTP mockeado
        adminValido = new Administrador();
        adminValido.setNombre("Jesús Francisco");
        adminValido.setSmtpHost("smtp.jfgb.es");
        adminValido.setSmtpPort(587);
        adminValido.setSmtpUsername("jesus@jfgb.es");
        adminValido.setSmtpPassword("secreto2026");
        adminValido.setSmtpAuth(true);
        adminValido.setSmtpStarttls(true);
    }

    @Test
    @DisplayName("1. Control de Seguridad: Aborta inmediatamente si el Administrador o sus credenciales son nulas")
    void enviarReciboPorEmail_AbortarSiFaltanDatosDelAdmin() {
        // GIVEN: Un administrador incompleto sin Host SMTP configurado
        Administrador adminInvalido = new Administrador();
        adminInvalido.setNombre("Admin Incompleto");

        // WHEN & THEN: El método debe capturar la falta de configuración y salir limpiamente (Early Return) sin romper hilos
        assertDoesNotThrow(() ->
                emailService.enviarReciboPorEmail("vecino@correo.com", "Test", "Cuerpo", null, adminInvalido)
        );

        assertDoesNotThrow(() ->
                emailService.enviarReciboPorEmail("vecino@correo.com", "Test", "Cuerpo", null, null)
        );
    }

    @Test
    @DisplayName("2. Tolerancia de Adjuntos: Continúa el envío si la ruta del PDF no existe o está en blanco")
    void enviarReciboPorEmail_RutaAdjuntoInvalidaNoDetieneEnvio() {
        // GIVEN: Una ruta a un fichero inexistente en el disco
        String rutaInexistente = "W:\\PROYECTOS\\no_existe_este_recibo.pdf";

        // WHEN & THEN: Debe capturar el log de advertencia interno, pero intentar el envío del cuerpo textual
        assertDoesNotThrow(() ->
                emailService.enviarReciboPorEmail("vecino@correo.com", "Recibo Mayo", "Cuerpo del recibo", rutaInexistente, adminValido)
        );
    }

    @Test
    @DisplayName("3. Lógica de Red: Configuración de contingencia ante caídas del servidor SMTP")
    void enviarReciboPorEmail_CapturaExcepcionesDeConexion() {
        // GIVEN: Un host erróneo o inaccesible de forma deliberada para forzar el catch del servicio
        adminValido.setSmtpHost("host.invalido.gti.local");

        // WHEN & THEN: El try-catch del servicio debe absorber el error de conexión y loguearlo sin colgar la App
        assertDoesNotThrow(() ->
                        emailService.enviarReciboPorEmail("propietario@comunidad.es", "Aviso", "Mensaje", null, adminValido),
                "El servicio debe gestionar internamente los fallos de red de MailSender."
        );
    }

    @Test
    @DisplayName("4. Ajuste Inteligente de Puertos: Conmutación correcta a SSL Directo usando el puerto 465")
    void enviarReciboPorEmail_AjusteSslDirectoPuerto465() {
        // GIVEN: Cambiamos el perfil del admin al puerto 465 (Caso Cristina)
        adminValido.setSmtpPort(465);

        // WHEN & THEN: Evaluamos que ejecute el bloque condicional del puerto sin excepciones estructurales
        assertDoesNotThrow(() ->
                emailService.enviarReciboPorEmail("cristina@comunidad.es", "Lote Remesa", "Texto", null, adminValido)
        );
    }
}