package com.sepa1914.adminservice.controller;

import com.sepa1914.adminservice.model.Comunidad;
import com.sepa1914.adminservice.model.Vecino;
import com.sepa1914.adminservice.repository.ComunidadRepository;
import com.sepa1914.adminservice.repository.VecinoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/vecinos")
public class VecinoController {

    @Autowired
    private VecinoRepository vecinoRepository;

    @Autowired
    private ComunidadRepository comunidadRepository;

    @GetMapping("/lista")
    public String listarVecinos(@RequestParam Long comunidadId, Model model, Authentication auth) {
        Comunidad comunidad = comunidadRepository.findById(comunidadId)
                .orElseThrow(() -> new RuntimeException("Comunidad no encontrada"));

        // Seguridad: Comprobar que la finca pertenece al usuario logueado
        if (!comunidad.getAdministrador().getUsername().equals(auth.getName())) {
            return "redirect:/comunidades/lista?error=no_autorizado";
        }

        model.addAttribute("comunidad", comunidad);
        model.addAttribute("vecinos", vecinoRepository.findByComunidad(comunidad));
        return "vecinos-lista";
    }

    @GetMapping("/nuevo")
    public String formularioNuevoVecino(@RequestParam Long comunidadId, Model model) {
        Comunidad comunidad = comunidadRepository.findById(comunidadId)
                .orElseThrow(() -> new RuntimeException("Comunidad no encontrada"));

        Vecino nuevoVecino = new Vecino();
        nuevoVecino.setComunidad(comunidad);

        model.addAttribute("vecino", nuevoVecino);
        model.addAttribute("comunidad", comunidad);
        return "vecinos-formulario";
    }

    @PostMapping("/guardar")
    public String guardarVecino(@ModelAttribute Vecino vecino, @RequestParam Long comunidadId) {
        Comunidad comunidad = comunidadRepository.findById(comunidadId)
                .orElseThrow(() -> new RuntimeException("Comunidad no encontrada"));

        vecino.setComunidad(comunidad);
        vecinoRepository.save(vecino);

        return "redirect:/vecinos/lista?comunidadId=" + comunidadId;
    }
}