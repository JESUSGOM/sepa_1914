package com.sepa1914.adminservice.controller;

import com.sepa1914.adminservice.dto.PresupuestoFormRecord;
import com.sepa1914.adminservice.dto.PresupuestoLineaRecord;
import com.sepa1914.adminservice.model.Comunidad;
import com.sepa1914.adminservice.repository.ComunidadRepository;
import com.sepa1914.adminservice.service.PresupuestoService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Controlador refactorizado para la gestión de presupuestos.
 * Se han eliminado las inyecciones @Autowired por constructor manual.
 * Se integra con PresupuestoFormRecord para permitir edición masiva.
 */
@Controller
@RequestMapping("/contabilidad/presupuestos")
public class PresupuestoController {

    private final PresupuestoService presupuestoService;
    private final ComunidadRepository comunidadRepository;

    /**
     * Constructor manual para inyección de dependencias (Cumpliendo restricción NO LOMBOK).
     */
    public PresupuestoController(PresupuestoService presupuestoService,
                                 ComunidadRepository comunidadRepository) {
        this.presupuestoService = presupuestoService;
        this.comunidadRepository = comunidadRepository;
    }

    /**
     * Carga la pantalla del presupuesto.
     * Mantiene la funcionalidad de filtrar por ejercicio y calcular totales.
     */
    @GetMapping("/{comunidadId}")
    public String verPresupuesto(@PathVariable Long comunidadId,
                                 @RequestParam(required = false) Integer ejercicio,
                                 Model model) {

        // 1. Determinar el año (ejercicio)
        int anioBusqueda = (ejercicio == null) ? LocalDate.now().getYear() : ejercicio;

        // 2. Obtener la comunidad para la miga de pan y títulos
        Comunidad comunidad = comunidadRepository.findById(comunidadId)
                .orElseThrow(() -> new RuntimeException("Comunidad no encontrada"));

        // 3. Obtener el Record del formulario que contiene todas las líneas (Cuentas 6 y 7)
        PresupuestoFormRecord form = presupuestoService.obtenerFormularioPresupuesto(comunidadId, anioBusqueda);

        // 4. Calcular el total acumulado de las líneas para mostrarlo en la cabecera
        // Usamos Streams para sumar los importes del Record
        BigDecimal totalPresupuesto = form.lineas().stream()
                .map(PresupuestoLineaRecord::importe)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 5. Pasar todo al modelo
        model.addAttribute("comunidad", comunidad);
        model.addAttribute("ejercicio", anioBusqueda);
        model.addAttribute("form", form);
        model.addAttribute("totalPresupuesto", totalPresupuesto);
        model.addAttribute("activePage", "presupuestos");

        return "contabilidad/presupuesto-form";
    }

    /**
     * Guarda el presupuesto completo enviado desde la tabla.
     * Refactorizado para usar el Record inmutable reconstruido por Spring.
     */
    @PostMapping("/guardar")
    public String guardarPresupuesto(@ModelAttribute("form") PresupuestoFormRecord form,
                                     RedirectAttributes ra) {
        try {
            // Llamamos al servicio para persistir todas las líneas del Record
            presupuestoService.guardarPresupuesto(form);

            ra.addFlashAttribute("exito", "El presupuesto del ejercicio " + form.anio() + " se ha actualizado correctamente.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Error al procesar el presupuesto: " + e.getMessage());
        }

        // Redirigimos manteniendo el contexto de la comunidad y el año
        return "redirect:/contabilidad/presupuestos/" + form.comunidadId() + "?ejercicio=" + form.anio();
    }
}