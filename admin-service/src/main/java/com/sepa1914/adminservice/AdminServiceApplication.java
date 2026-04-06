package com.sepa1914.adminservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import java.awt.*;
import java.net.URI;

@SpringBootApplication
public class AdminServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminServiceApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void abrirNavegador() {
        String url = "http://localhost:9559/login";
        // Evita que falle en servidores sin interfaz gráfica (como el de GitHub Actions)
        if (Desktop.isDesktopSupported() && !GraphicsEnvironment.isHeadless()) {
            try {
                Desktop.getDesktop().browse(new URI(url));
            } catch (Exception e) {
                System.err.println("No se pudo abrir el navegador automáticamente: " + e.getMessage());
            }
        }
    }
}