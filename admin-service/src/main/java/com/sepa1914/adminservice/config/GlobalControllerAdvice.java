package com.sepa1914.adminservice.config;

import com.sepa1914.adminservice.service.LicenseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class GlobalControllerAdvice {

    @Autowired
    private LicenseService licenseService;

    @ModelAttribute
    public void addAttributes(Model model) {
        // Inyectamos el estado de la licencia y el ID del equipo en todas las vistas
        boolean activado = licenseService.validarLicencia();
        model.addAttribute("softwareActivado", activado);
        model.addAttribute("equipoID", licenseService.getEquipoID());
    }
}