package com.sepa1914.adminservice.controller;

import com.sepa1914.adminservice.model.*;
import com.sepa1914.adminservice.repository.*;
import com.sepa1914.adminservice.service.ContabilidadService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Optional;

/**
 * Controlador exclusivo para la conciliación bancaria de SEPA 1914.
 * MANTIENE TODA LA FUNCIONALIDAD ORIGINAL.
 * CORRECCIÓN: Resuelve errores de firma de métodos sincronizando con el ID de movimiento bancario.
 */
@Controller
@RequestMapping("/bancos/conciliar")
public class ConciliacionController {

    private static final Logger log = LoggerFactory.getLogger(ConciliacionController.class);

    @Autowired private MovimientoBancarioRepository movimientoRepository;
    @Autowired private ReciboRepository reciboRepository;
    @Autowired private GastoRepository gastoRepository;
    @Autowired private VecinoRepository vecinoRepository;
    @Autowired private CuentaContableRepository cuentaContableRepository;
    @Autowired private ContabilidadService contabilidadService;

    /**
     * Prepara la pantalla de conciliación discriminando por Signo (Pago vs Ingreso).
     * Carga 'todasLasCuentas' para permitir conciliación directa contra el mayor.
     */
    @GetMapping("/{movimientoId}")
    public String prepararConciliacion(@PathVariable Long movimientoId, Model model) {
        MovimientoBancario mov = movimientoRepository.findById(movimientoId)
                .orElseThrow(() -> new RuntimeException("Movimiento bancario no encontrado"));

        Long comunidadId = mov.getComunidad().getId();
        model.addAttribute("movimiento", mov);
        model.addAttribute("comunidad", mov.getComunidad());
        model.addAttribute("vecinos", vecinoRepository.findByComunidadId(comunidadId));
        model.addAttribute("todasLasCuentas", cuentaContableRepository.findByComunidadId(comunidadId));

        if ("1".equals(mov.getSigno())) {
            List<Gasto> facturas = gastoRepository.findByComunidadId(comunidadId)
                    .stream().filter(g -> !g.isPagado()).toList();
            model.addAttribute("facturas", facturas);
            model.addAttribute("tipoConciliacion", "PAGO");
        } else {
            List<Recibo> recibos = reciboRepository.findByComunidadId(comunidadId)
                    .stream().filter(r -> r.getEstado() != Recibo.EstadoRecibo.COBRADO).toList();
            model.addAttribute("recibos", recibos);
            model.addAttribute("tipoConciliacion", "INGRESO");
        }
        return "bancos-conciliar";
    }

    /**
     * Vincula un ingreso bancario con un recibo de vecino individual.
     * CORREGIDO: Pasa el movimientoId (Long) en lugar de la fecha para generar el asiento contra el banco.
     */
    @PostMapping("/vincular")
    public String vincularRecibo(@RequestParam Long movimientoId, @RequestParam Long reciboId, RedirectAttributes ra) {
        Optional<MovimientoBancario> movOpt = movimientoRepository.findById(movimientoId);
        if (movOpt.isPresent()) {
            try {
                // Sincronizado con la firma: confirmarCobroRecibo(Long reciboId, Long movimientoBancarioId)
                contabilidadService.confirmarCobroRecibo(reciboId, movimientoId);
                ra.addFlashAttribute("exito", "Recibo conciliado correctamente.");
            } catch (Exception e) {
                ra.addFlashAttribute("error", "Error en vinculación: " + e.getMessage());
            }
            return "redirect:/bancos/movimientos/" + movOpt.get().getComunidad().getId();
        }
        return "redirect:/comunidades";
    }

    /**
     * Vincula una salida bancaria con una factura de gasto de proveedor.
     */
    @PostMapping("/vincular-gasto")
    public String vincularGasto(@RequestParam Long movimientoId, @RequestParam Long gastoId, RedirectAttributes ra) {
        Optional<MovimientoBancario> movOpt = movimientoRepository.findById(movimientoId);
        if (movOpt.isPresent()) {
            try {
                MovimientoBancario mov = movOpt.get();
                contabilidadService.confirmarPagoGasto(gastoId, mov.getFechaOperacion());
                mov.setConciliado(true);
                movimientoRepository.save(mov);
                ra.addFlashAttribute("exito", "Pago de factura conciliado.");
            } catch (Exception e) {
                ra.addFlashAttribute("error", "Error en pago: " + e.getMessage());
            }
            return "redirect:/bancos/movimientos/" + movOpt.get().getComunidad().getId();
        }
        return "redirect:/comunidades";
    }

    /**
     * Procesa la conciliación de múltiples recibos (Remesas masivas).
     * CORREGIDO: Utiliza procesarCobroRemesaCompleta para generar un UNICO apunte en banco (Asiento Resumen).
     */
    @PostMapping("/vincular-masivo")
    public String vincularMasivo(@RequestParam Long movimientoId, @RequestParam List<Long> reciboIds, RedirectAttributes ra) {
        Optional<MovimientoBancario> movOpt = movimientoRepository.findById(movimientoId);
        if (movOpt.isPresent()) {
            try {
                MovimientoBancario mov = movOpt.get();
                // Invocamos el método de remesa completa para evitar N apuntes en la cuenta 572
                contabilidadService.procesarCobroRemesaCompleta(mov.getComunidad().getId(), reciboIds, movimientoId);
                ra.addFlashAttribute("exito", "Remesa conciliada con éxito (Asiento Resumen generado).");
            } catch (Exception e) {
                ra.addFlashAttribute("error", "Error masivo: " + e.getMessage());
            }
            return "redirect:/bancos/movimientos/" + movOpt.get().getComunidad().getId();
        }
        return "redirect:/comunidades";
    }

    /**
     * Conciliación directa contra una cuenta del mayor (Opción A).
     */
    @PostMapping("/directo")
    public String conciliarDirecto(@RequestParam Long movimientoId, @RequestParam Long cuentaId, RedirectAttributes ra) {
        Optional<MovimientoBancario> movOpt = movimientoRepository.findById(movimientoId);
        if (movOpt.isPresent()) {
            try {
                contabilidadService.conciliarManualDirecto(movimientoId, cuentaId);
                ra.addFlashAttribute("exito", "Conciliación directa realizada.");
            } catch (Exception e) {
                ra.addFlashAttribute("error", "Error en directo: " + e.getMessage());
            }
            return "redirect:/bancos/movimientos/" + movOpt.get().getComunidad().getId();
        }
        return "redirect:/comunidades";
    }

    /**
     * Endpoint para revertir una conciliación errónea.
     */
    @PostMapping("/desconciliar/{movimientoId}")
    public String desconciliar(@PathVariable Long movimientoId, RedirectAttributes ra) {
        try {
            contabilidadService.desconciliarMovimientoBancario(movimientoId);
            ra.addFlashAttribute("exito", "Movimiento desconciliado y Libro Mayor corregido.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Error al desconciliar: " + e.getMessage());
        }

        // Obtenemos la comunidad para redirigir al extracto
        MovimientoBancario m = movimientoRepository.findById(movimientoId).orElseThrow();
        return "redirect:/bancos/movimientos/" + m.getComunidad().getId();
    }
}