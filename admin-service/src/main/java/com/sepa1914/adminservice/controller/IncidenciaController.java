package com.sepa1914.adminservice.controller;

// IMPORTS DE TU PROYECTO (Modelos y Repositorios)
import com.sepa1914.adminservice.model.Incidencia;
import com.sepa1914.adminservice.repository.IncidenciaRepository;

// IMPORTS DE SPRING FRAMEWORK (Fundamentales para que compile)
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;

@Controller
@RequestMapping("/incidencias")
public class IncidenciaController {

    @Autowired
    private IncidenciaRepository incidenciaRepository;

    /**
     * Muestra la lista completa de incidencias.
     */
    @GetMapping("/lista")
    public String listarIncidencias(Model model) {
        model.addAttribute("activePage", "incidencias");
        model.addAttribute("incidencias", incidenciaRepository.findAll());
        return "incidencias-lista";
    }

    /**
     * Muestra el detalle de una incidencia para su gestión.
     * Soluciona el error 404 al intentar entrar en /gestionar/{id}
     */
    @GetMapping("/gestionar/{id}")
    public String gestionarIncidencia(@PathVariable("id") Long id, Model model) {
        Incidencia incidencia = incidenciaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("ID de incidencia inválido: " + id));

        model.addAttribute("activePage", "incidencias");
        model.addAttribute("incidencia", incidencia);
        // Cargamos los estados posibles del Enum para el desplegable
        model.addAttribute("estados", Incidencia.EstadoIncidencia.values());

        return "incidencia-gestion";
    }

    /**
     * Procesa la actualización de la incidencia (cambio de estado o coste).
     */
    @PostMapping("/actualizar")
    public String actualizarIncidencia(@ModelAttribute("incidencia") Incidencia incidencia) {
        // Recuperamos la incidencia original de la BD para no perder datos sensibles
        Incidencia original = incidenciaRepository.findById(incidencia.getId())
                .orElseThrow(() -> new IllegalArgumentException("No existe la incidencia con ID: " + incidencia.getId()));

        // Solo actualizamos lo que el administrador puede cambiar
        original.setEstado(incidencia.getEstado());
        original.setCosteEstimado(incidencia.getCosteEstimado());

        incidenciaRepository.save(original);

        // Redirigimos a la lista con un parámetro de éxito
        return "redirect:/incidencias/lista?actualizado=true";
    }
}