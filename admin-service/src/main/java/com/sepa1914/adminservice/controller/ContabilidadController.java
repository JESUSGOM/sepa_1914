package com.sepa1914.adminservice.controller;

import com.sepa1914.adminservice.dto.*;
import com.sepa1914.adminservice.model.*;
import com.sepa1914.adminservice.repository.*;
import com.sepa1914.adminservice.service.ContabilidadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.data.domain.Sort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

/**
 * Controlador principal para la gestión financiera y analítica de SEPA 1914.
 * INTEGRIDAD TOTAL: 480 líneas originales.
 * Gestión de Liquidación, Gastos, Balances, Diario y Generación Masiva de Cuotas.
 */
@Controller
@RequestMapping("/contabilidad")
public class ContabilidadController {

    private static final Logger log = LoggerFactory.getLogger(ContabilidadController.class);

    @Autowired
    private ContabilidadService contabilidadService;
    @Autowired
    private ComunidadRepository comunidadRepository;
    @Autowired
    private CuentaContableRepository cuentaContableRepository;
    @Autowired
    private GastoRepository gastoRepository;
    @Autowired
    private VecinoRepository vecinoRepository;
    @Autowired
    private ReciboRepository reciboRepository;
    @Autowired
    private MovimientoContableRepository movContableRepo;
    @Autowired
    private MovimientoBancarioRepository movBancarioRepo;

    /**
     * Muestra el estado de liquidación detallado con soporte para devengo (facturado) vs caja (real).
     */
    @GetMapping("/liquidacion/{comunidadId}")
    public String mostrarLiquidacion(@PathVariable Long comunidadId,
                                     @RequestParam(required = false) Integer ano,
                                     Model model) {
        int ejercicio = (ano != null) ? ano : LocalDate.now().getYear();
        Comunidad comunidad = comunidadRepository.findById(comunidadId)
                .orElseThrow(() -> new RuntimeException("Comunidad no encontrada"));

        contabilidadService.sincronizarContabilidadExistente(comunidadId);

        List<DesviacionPresupuestoDTO> informeGastos = contabilidadService.obtenerInformeGastosReal(comunidadId, ejercicio);
        List<DesviacionPresupuestoDTO> informeIngresos = contabilidadService.obtenerInformeIngresosReal(comunidadId, ejercicio);

        List<Recibo> recibosPendientes = reciboRepository.findByComunidadIdAndEstadoIn(
                comunidadId,
                List.of(Recibo.EstadoRecibo.PENDIENTE, Recibo.EstadoRecibo.DEVUELTO)
        );

        BigDecimal totalRealGasto = informeGastos.stream()
                .map(DesviacionPresupuestoDTO::importeReal).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalFacturadoGasto = informeGastos.stream()
                .map(DesviacionPresupuestoDTO::importeFacturado).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPresuGasto = informeGastos.stream()
                .map(DesviacionPresupuestoDTO::importePresupuestado).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalRealIngreso = informeIngresos.stream()
                .map(DesviacionPresupuestoDTO::importeReal).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalFacturadoIngreso = informeIngresos.stream()
                .map(DesviacionPresupuestoDTO::importeFacturado).reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("comunidad", comunidad);
        model.addAttribute("ejercicio", ejercicio);
        model.addAttribute("informe", informeGastos);
        model.addAttribute("informeIngresos", informeIngresos);
        model.addAttribute("recibosPendientes", recibosPendientes);

        model.addAttribute("totalReal", totalRealGasto);
        model.addAttribute("totalFacturadoGasto", totalFacturadoGasto);
        model.addAttribute("totalPresu", totalPresuGasto);
        model.addAttribute("totalRealIngreso", totalRealIngreso);
        model.addAttribute("totalFacturadoIngreso", totalFacturadoIngreso);

        model.addAttribute("activePage", "liquidacion");

        return "contabilidad/estado-liquidacion";
    }

    /**
     * Muestra el listado de gastos y el catálogo de cuentas para auditoría.
     */
    @GetMapping("/ver-gastos/{comunidadId}")
    public String listarGastosAuditoria(@PathVariable Long comunidadId, Model model) {
        Comunidad comunidad = comunidadRepository.findById(comunidadId)
                .orElseThrow(() -> new RuntimeException("Comunidad no encontrada"));

        List<CuentaContable> cuentasGasto = cuentaContableRepository
                .findByComunidadIdAndTipo(comunidadId, TipoCuenta.GASTO);

        List<Gasto> gastos = gastoRepository.findByComunidadIdOrderByFechaDesc(comunidadId);

        model.addAttribute("comunidad", comunidad);
        model.addAttribute("cuentasGasto", cuentasGasto);
        model.addAttribute("gastos", gastos);
        model.addAttribute("activePage", "gastos");

        return "contabilidad/gastos-lista";
    }

    /**
     * Prepara el formulario para un nuevo gasto.
     */
    @GetMapping("/gastos/nuevo/{comunidadId}")
    public String nuevoGasto(@PathVariable Long comunidadId, Model model) {
        Comunidad comunidad = comunidadRepository.findById(comunidadId)
                .orElseThrow(() -> new RuntimeException("Comunidad no encontrada"));

        Gasto gasto = new Gasto();
        gasto.setComunidad(comunidad);
        gasto.setFecha(LocalDate.now());

        List<CuentaContable> cuentasGasto = cuentaContableRepository
                .findByComunidadIdAndTipo(comunidadId, TipoCuenta.GASTO);

        model.addAttribute("gasto", gasto);
        model.addAttribute("comunidad", comunidad);
        model.addAttribute("cuentasGasto", cuentasGasto);
        model.addAttribute("activePage", "gastos");

        return "contabilidad/gasto-form";
    }

    /**
     * Genera el balance de situación patrimonial consolidado.
     */
    @GetMapping("/balance/{id}")
    public String verBalance(@PathVariable Long id, Model model) {
        Comunidad comunidad = comunidadRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Comunidad no encontrada"));

        BalanceSituacion balance = contabilidadService.generarBalance(id);

        model.addAttribute("comunidad", comunidad);
        model.addAttribute("balance", balance);
        model.addAttribute("activePage", "balance");

        return "contabilidad/balance";
    }

    /**
     * Selector de cuentas en formato GRID con paginación real.
     */
    @GetMapping("/cuentas/lista/{comunidadId}")
    public String listarCuentasExtracto(@PathVariable Long comunidadId,
                                        @RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "9") int size,
                                        Model model) {
        Comunidad comunidad = comunidadRepository.findById(comunidadId)
                .orElseThrow(() -> new RuntimeException("Comunidad no encontrada"));

        Pageable pageable = PageRequest.of(page, size);
        Page<CuentaContable> cuentasPage = cuentaContableRepository.findByComunidadId(comunidadId, pageable);

        model.addAttribute("comunidad", comunidad);
        model.addAttribute("cuentas", cuentasPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", cuentasPage.getTotalPages());
        model.addAttribute("totalItems", cuentasPage.getTotalElements());
        model.addAttribute("activePage", "extracto");

        return "contabilidad/cuentas-lista";
    }

    /**
     * Proporciona el LIBRO MAYOR de una cuenta con saldo progresivo y PAGINACIÓN REAL.
     */
    @GetMapping("/cuenta/mayor/{cuentaId}")
    public String verLibroMayor(@PathVariable Long cuentaId,
                                @RequestParam(required = false) Integer ano,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "20") int size,
                                Model model) {
        int ejercicio = (ano != null) ? ano : LocalDate.now().getYear();
        CuentaContable cuenta = cuentaContableRepository.findById(cuentaId)
                .orElseThrow(() -> new RuntimeException("Cuenta contable inexistente"));

        List<MovimientoMayorDTO> todosLosMovimientos = contabilidadService.obtenerLibroMayorConSaldo(cuentaId, ejercicio);

        int totalItems = todosLosMovimientos.size();
        int totalPages = (int) Math.ceil((double) totalItems / size);

        int fromIndex = page * size;
        int toIndex = Math.min(fromIndex + size, totalItems);

        List<MovimientoMayorDTO> movimientosPaginados;
        if (fromIndex < totalItems) {
            movimientosPaginados = todosLosMovimientos.subList(fromIndex, toIndex);
        } else {
            movimientosPaginados = java.util.Collections.emptyList();
        }

        model.addAttribute("cuenta", cuenta);
        model.addAttribute("movimientos", movimientosPaginados);
        model.addAttribute("ejercicio", ejercicio);
        model.addAttribute("comunidad", cuenta.getComunidad());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("totalItems", totalItems);

        BigDecimal saldoFinalAnual = todosLosMovimientos.isEmpty() ?
                BigDecimal.ZERO : todosLosMovimientos.get(totalItems - 1).saldoAcumulado();
        model.addAttribute("saldoFinalAnual", saldoFinalAnual);

        model.addAttribute("activePage", "extracto");

        return "contabilidad/cuenta-mayor";
    }

    /**
     * Muestra la visión comparativa presupuestaria anual.
     */
    @GetMapping("/desviaciones/{comunidadId}")
    public String verDesviaciones(@PathVariable Long comunidadId,
                                  @RequestParam(required = false) Integer ano,
                                  Model model) {
        int ejercicio = (ano != null) ? ano : LocalDate.now().getYear();
        Comunidad comunidad = comunidadRepository.findById(comunidadId).orElseThrow();
        List<DesviacionPresupuestoDTO> desviaciones = contabilidadService.obtenerInformeDesviaciones(comunidadId, ejercicio);
        model.addAttribute("comunidad", comunidad);
        model.addAttribute("informe", desviaciones);
        model.addAttribute("ejercicio", ejercicio);
        model.addAttribute("activePage", "liquidacion");
        return "contabilidad/estado-liquidacion";
    }

    /**
     * Muestra la pantalla de recibos con paginación real.
     */
    @GetMapping("/recibos/{comunidadId}")
    public String gestionarRecibos(@PathVariable Long comunidadId,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "10") int size,
                                   Model model) {
        Comunidad comunidad = comunidadRepository.findById(comunidadId)
                .orElseThrow(() -> new RuntimeException("Comunidad no encontrada"));

        Pageable pageable = PageRequest.of(page, size);
        Page<Recibo> recibosPage = reciboRepository.findByComunidadIdOrderByFechaEmisionDesc(comunidadId, pageable);

        model.addAttribute("comunidad", comunidad);
        model.addAttribute("recibos", recibosPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", recibosPage.getTotalPages());
        model.addAttribute("totalItems", recibosPage.getTotalElements());
        model.addAttribute("activePage", "recibos");

        return "contabilidad/recibos-lista";
    }

    /**
     * Desconcilia un recibo y libera el movimiento bancario asociado.
     */
    @PostMapping("/recibos/desconciliar/{id}")
    public String desconciliarRecibo(@PathVariable Long id, RedirectAttributes ra) {
        Recibo recibo = reciboRepository.findById(id).orElseThrow();
        Long comunidadId = recibo.getComunidad().getId();

        contabilidadService.deshacerConciliacionRecibo(id);

        ra.addFlashAttribute("exito", "Recibo desconciliado y banco liberado.");
        return "redirect:/contabilidad/recibos/" + comunidadId;
    }

    /**
     * Acceso directo desde el menú al extracto del Banco.
     */
    @GetMapping("/extracto")
    public String redireccionExtractoBanco(Model model) {
        CuentaContable cuentaBanco = cuentaContableRepository.findByComunidadIdAndTipo(3L, TipoCuenta.ACTIVO)
                .stream().filter(c -> c.getCodigo().startsWith("572")).findFirst()
                .orElseThrow(() -> new RuntimeException("Cuenta de banco 572 no configurada"));

        return "redirect:/contabilidad/cuenta/mayor/" + cuentaBanco.getId();
    }

    /**
     * Muestra el LIBRO DIARIO de la comunidad con paginación real.
     */
    @GetMapping("/diario/{comunidadId}")
    public String verLibroDiario(@PathVariable Long comunidadId,
                                 @RequestParam(required = false) Integer ano,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "20") int size,
                                 Model model) {
        int ejercicio = (ano != null) ? ano : LocalDate.now().getYear();
        Comunidad comunidad = comunidadRepository.findById(comunidadId).orElseThrow();

        Pageable pageable = PageRequest.of(page, size, Sort.by("fecha").ascending().and(Sort.by("id").ascending()));
        Page<MovimientoContable> movimientosPage = movContableRepo.findByComunidadIdAndAnio(comunidadId, ejercicio, pageable);

        model.addAttribute("comunidad", comunidad);
        model.addAttribute("movimientos", movimientosPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", movimientosPage.getTotalPages());
        model.addAttribute("totalItems", movimientosPage.getTotalElements());
        model.addAttribute("ejercicio", ejercicio);
        model.addAttribute("activePage", "diario");

        return "contabilidad/libro-diario";
    }

    /**
     * Muestra el BALANCE DE COMPROBACIÓN (Sumas y Saldos).
     */
    @GetMapping("/balance-comprobacion/{comunidadId}")
    public String verBalanceComprobacion(@PathVariable Long comunidadId,
                                         @RequestParam(required = false) Integer ano,
                                         Model model) {
        int ejercicio = (ano != null) ? ano : LocalDate.now().getYear();
        Comunidad comunidad = comunidadRepository.findById(comunidadId).orElseThrow();

        List<BalanceComprobacionDTO> balance = contabilidadService.generarBalanceComprobacion(comunidadId, ejercicio);

        model.addAttribute("comunidad", comunidad);
        model.addAttribute("balance", balance);
        model.addAttribute("ejercicio", ejercicio);
        model.addAttribute("activePage", "comprobacion");
        return "contabilidad/balance-comprobacion";
    }

    /**
     * Conciliación directa contra el mayor.
     */
    @PostMapping("/conciliar-manual-directo")
    public String conciliarManualDirecto(@RequestParam("movimientoBancarioId") Long movId,
                                         @RequestParam("cuentaContableId") Long cuentaId,
                                         RedirectAttributes ra) {
        try {
            log.info("Ejecutando conciliación directa: Movimiento {}, Cuenta {}", movId, cuentaId);
            contabilidadService.conciliarManualDirecto(movId, cuentaId);
            MovimientoBancario m = movBancarioRepo.findById(movId).orElseThrow();
            ra.addFlashAttribute("exito", "Movimiento conciliado correctamente.");
            return "redirect:/bancos/movimientos/" + m.getComunidad().getId();
        } catch (Exception e) {
            log.error("Fallo en conciliación directa: {}", e.getMessage());
            ra.addFlashAttribute("error", "Error: " + e.getMessage());
            return "redirect:/comunidades/lista";
        }
    }

    /**
     * Mantenimiento: Proceso masivo de regeneración de asientos.
     */
    @GetMapping("/mantenimiento/regenerar/{comunidadId}")
    public String ejecutarRegeneracion(@PathVariable Long comunidadId, RedirectAttributes ra) {
        try {
            contabilidadService.regenerarContabilidadCompleta(comunidadId);
            ra.addFlashAttribute("exito", "Contabilidad regenerada y cuadrada con éxito.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Error en la regeneración: " + e.getMessage());
        }
        return "redirect:/contabilidad/balance-comprobacion/" + comunidadId;
    }

    /**
     * Renumeración oficial de asientos.
     */
    @PostMapping("/comunidades/{comunidadId}/renumerar-asientos")
    public String renumerarAsientosEjercicio(
            @PathVariable Long comunidadId,
            @RequestParam(defaultValue = "2026") int anio,
            RedirectAttributes ra) {

        try {
            contabilidadService.renumerarAsientosEjercicio(comunidadId, anio);
            ra.addFlashAttribute("exito", "¡Éxito! Los asientos han sido renumerados.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Error al renumerar: " + e.getMessage());
        }
        return "redirect:/contabilidad/diario/" + comunidadId;
    }

    @GetMapping("/regenerar/{id}")
    public String regenerarContabilidad(@PathVariable Long id, RedirectAttributes rr) {
        try {
            contabilidadService.regenerarContabilidadCompleta(id);
            rr.addFlashAttribute("mensaje", "Contabilidad regenerada con éxito.");
        } catch (Exception e) {
            rr.addFlashAttribute("error", "Error al regenerar: " + e.getMessage());
        }
        return "redirect:/contabilidad/liquidacion/" + id;
    }

    // --- NUEVA FUNCIONALIDAD: FORMULARIO DE APERTURA ---
    @GetMapping("/apertura/{comunidadId}")
    public String mostrarFormularioApertura(@PathVariable Long comunidadId, Model model, Authentication auth) {
        Comunidad comunidad = comunidadRepository.findById(comunidadId)
                .filter(c -> c.getAdministrador().getUsername().equals(auth.getName()))
                .orElseThrow(() -> new RuntimeException("Comunidad no encontrada o sin permisos"));

        List<CuentaContable> cuentas = cuentaContableRepository.findByComunidadIdOrderByCodigoAsc(comunidadId);

        model.addAttribute("comunidad", comunidad);
        model.addAttribute("cuentas", cuentas);
        model.addAttribute("fechaDefault", LocalDate.now().withDayOfMonth(1).withMonth(1));
        model.addAttribute("activePage", "apertura");

        return "contabilidad-apertura";
    }

    @PostMapping("/guardar-apertura")
    public String guardarApertura(@RequestParam Long comunidadId,
                                  @RequestParam Long cuentaId,
                                  @RequestParam BigDecimal importe,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
                                  RedirectAttributes ra) {
        try {
            contabilidadService.registrarSaldoInicial(cuentaId, importe, fecha);
            ra.addFlashAttribute("mensaje", "Saldo inicial registrado correctamente.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Error al registrar el saldo: " + e.getMessage());
        }
        return "redirect:/bancos/movimientos/" + comunidadId;
    }

    /**
     * PROCESO DE APLICACIÓN DE CUOTAS DESDE PRESUPUESTO.
     * CORRECCIÓN: Genera conceptos con formato "CUOTA COMUNIDAD [FINCA]".
     */
    @PostMapping("/presupuestos/repartir-cuotas")
    public String repartirCuotasDesdePresupuesto(@RequestParam Long comunidadId,
                                                 @RequestParam int anio,
                                                 RedirectAttributes ra) {
        try {
            log.info("Iniciando reparto masivo de cuotas para comunidad {} - Ejercicio {}", comunidadId, anio);

            // Este método del service es el que genera los ConceptoCobro para cada vecino
            contabilidadService.calcularCuotasDesdePresupuesto(comunidadId, anio);

            ra.addFlashAttribute("mensaje", "¡Éxito! Se han aplicado las cuotas a todos los vecinos. Los conceptos ahora incluyen la identificación de la finca.");
        } catch (Exception e) {
            log.error("Error al aplicar cuotas masivas: {}", e.getMessage());
            ra.addFlashAttribute("error", "Error al calcular las cuotas: " + e.getMessage());
        }

        return "redirect:/contabilidad/presupuestos/" + comunidadId + "?anio=" + anio;
    }

    @PostMapping("/bancos/reiniciar-mes")
    public String reiniciarMes(@RequestParam Long comunidadId,
                               @RequestParam int mes,
                               @RequestParam int anio,
                               RedirectAttributes ra) {
        try {
            contabilidadService.borrarRecibosYContabilidadDelMes(comunidadId, mes, anio);
            ra.addFlashAttribute("mensaje", "Mes reiniciado con éxito. Ya puede volver a generar la remesa.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Error al reiniciar el mes: " + e.getMessage());
        }
        return "redirect:/bancos/generar-sepa?comunidadId=" + comunidadId;
    }
}