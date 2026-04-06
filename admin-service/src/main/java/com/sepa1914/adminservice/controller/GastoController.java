package com.sepa1914.adminservice.controller;

import com.sepa1914.adminservice.model.*;
import com.sepa1914.adminservice.repository.*;
import com.sepa1914.adminservice.service.ContabilidadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;

/**
 * Controlador integral para la gestión de Facturas y Gastos de la comunidad.
 * Refactorizado para usar la nomenclatura unificada 'codigo' y asegurar integridad contable.
 */
@Controller
@RequestMapping("/contabilidad/gastos")
public class GastoController {

    private static final Logger log = LoggerFactory.getLogger(GastoController.class);

    @Autowired private GastoRepository gastoRepository;
    @Autowired private ComunidadRepository comunidadRepository;
    @Autowired private CuentaContableRepository cuentaRepository;
    @Autowired private ContabilidadService contabilidadService;

    /**
     * Muestra el listado de gastos de una comunidad específica.
     */
    @GetMapping("/{comunidadId}")
    public String listarGastos(@PathVariable Long comunidadId, Model model) {
        Comunidad comunidad = comunidadRepository.findById(comunidadId)
                .orElseThrow(() -> new RuntimeException("Comunidad no encontrada con ID: " + comunidadId));

        List<Gasto> listaGastos = gastoRepository.findByComunidadId(comunidadId);
        List<CuentaContable> cuentasGasto = cuentaRepository.findByComunidadIdAndTipo(comunidadId, TipoCuenta.GASTO);

        model.addAttribute("comunidad", comunidad);
        model.addAttribute("gastos", listaGastos);
        model.addAttribute("cuentasGasto", cuentasGasto);
        model.addAttribute("activePage", "gastos");

        return "contabilidad/gastos-lista";
    }

    /**
     * Formulario para el registro de una nueva factura (Alta).
     */
//    @GetMapping("/nuevo/{comunidadId}")
//    public String nuevoGastoForm(@PathVariable Long comunidadId, Model model) {
//        Comunidad comunidad = comunidadRepository.findById(comunidadId)
//                .orElseThrow(() -> new RuntimeException("Comunidad no encontrada"));
//
//        List<CuentaContable> cuentasGasto = cuentaRepository.findByComunidadIdAndTipo(comunidadId, TipoCuenta.GASTO);
//
//        Gasto gasto = new Gasto();
//        gasto.setComunidad(comunidad);
//
//        model.addAttribute("gasto", gasto);
//        model.addAttribute("comunidad", comunidad);
//        model.addAttribute("cuentasGasto", cuentasGasto);
//        model.addAttribute("activePage", "gastos");
//
//        return "contabilidad/gasto-form";
//    }

    /**
     * Formulario para la edición de un gasto existente.
     */
    @GetMapping("/editar/{id}")
    public String editarGasto(@PathVariable Long id, Model model) {
        Gasto gasto = gastoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Factura no encontrada con ID: " + id));

        Comunidad comunidad = gasto.getComunidad();
        List<CuentaContable> cuentasGasto = cuentaRepository.findByComunidadIdAndTipo(comunidad.getId(), TipoCuenta.GASTO);

        model.addAttribute("gasto", gasto);
        model.addAttribute("comunidad", comunidad);
        model.addAttribute("cuentasGasto", cuentasGasto);
        model.addAttribute("activePage", "gastos");

        return "contabilidad/gasto-form";
    }

    /**
     * Procesa el guardado y genera el asiento contable automático.
     */
    @PostMapping("/guardar")
    public String guardarGasto(@ModelAttribute("gasto") Gasto gasto,
                               @RequestParam("comunidadId") Long comunidadId,
                               @RequestParam(value = "cuentaGastoId", required = false) Long cuentaGastoId,
                               RedirectAttributes ra) {
        try {
            log.info("Iniciando guardado de gasto para comunidad {}. Cuenta recibida: {}", comunidadId, cuentaGastoId);

            Comunidad comunidad = comunidadRepository.findById(comunidadId)
                    .orElseThrow(() -> new RuntimeException("Comunidad no encontrada"));

            gasto.setComunidad(comunidad);

            if (cuentaGastoId != null) {
                CuentaContable cuenta = cuentaRepository.findById(cuentaGastoId)
                        .orElseThrow(() -> new RuntimeException("La cuenta contable seleccionada no existe"));
                gasto.setCuentaGasto(cuenta);
                // CORREGIDO: Uso de getCodigo() tras refactorización
                log.info("Cuenta vinculada correctamente: {}", cuenta.getCodigo());
            }

            if (gasto.getCuentaGasto() == null) {
                ra.addFlashAttribute("error", "Error: Debe seleccionar una cuenta contable de gasto.");
                return "redirect:/contabilidad/gastos/nuevo/" + comunidadId;
            }

            Gasto guardado = gastoRepository.save(gasto);
            log.info("Gasto guardado exitosamente con ID: {}", guardado.getId());

            // Contabilización automática en el libro diario
            contabilidadService.registrarGastoContable(guardado);

            ra.addFlashAttribute("exito", "Factura registrada y contabilizada correctamente.");
            return "redirect:/contabilidad/gastos/" + comunidadId;

        } catch (Exception e) {
            log.error("Error crítico al procesar el guardado del gasto: ", e);
            ra.addFlashAttribute("error", "No se pudo procesar la factura: " + e.getMessage());
            return "redirect:/contabilidad/gastos/" + comunidadId;
        }
    }

    /**
     * Confirma el pago de una factura y realiza la conciliación bancaria.
     */
    @PostMapping("/pagar")
    public String confirmarPago(@RequestParam Long gastoId,
                                @RequestParam Long comunidadId,
                                @RequestParam("fechaPago") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaPago,
                                RedirectAttributes ra) {

        Gasto gasto = gastoRepository.findById(gastoId)
                .orElseThrow(() -> new RuntimeException("Gasto no encontrado"));

        if (gasto.isPagado()) {
            ra.addFlashAttribute("error", "Esta factura ya consta como pagada en el sistema.");
            return "redirect:/contabilidad/gastos/" + comunidadId;
        }

        gasto.setPagado(true);
        gasto.setFechaPago(fechaPago);
        gastoRepository.save(gasto);

        // Lógica de servicio para mover saldos de Pasivo a Banco
        contabilidadService.confirmarPagoGasto(gastoId, fechaPago);

        ra.addFlashAttribute("exito", "Pago registrado y asiento de conciliación completado.");
        return "redirect:/contabilidad/gastos/" + comunidadId;
    }

    /**
     * Elimina una factura del sistema (uso restringido).
     */
    @GetMapping("/eliminar/{id}")
    public String eliminarGasto(@PathVariable Long id, RedirectAttributes ra) {
        Gasto gasto = gastoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Gasto no encontrado"));
        Long comunidadId = gasto.getComunidad().getId();

        gastoRepository.delete(gasto);

        ra.addFlashAttribute("exito", "La factura ha sido eliminada del sistema.");
        return "redirect:/contabilidad/gastos/" + comunidadId;
    }
}