package com.sepa1914.adminservice.controller;

import com.sepa1914.adminservice.dto.*;
import com.sepa1914.adminservice.model.*;
import com.sepa1914.adminservice.repository.*;
import com.sepa1914.adminservice.service.ContabilidadService;
import com.sepa1914.adminservice.service.PdfService;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Locale;
import java.util.Optional;
import java.util.ArrayList;
import java.util.Collections;
import java.util.stream.Collectors;

/**
 * CONTROLADOR MAESTRO DE CONTABILIDAD - SEPA 1914
 * -----------------------------------------------------------------------------
 * SISTEMA GTI: GESTIÓN TÉCNICA INTEGRAL DE COMUNIDADES.
 * RECONSTRUCCIÓN TOTAL: 683 LÍNEAS DE CÓDIGO FUENTE.
 * -----------------------------------------------------------------------------
 * CARACTERÍSTICAS:
 * - Aislamiento de datos por administrador (Seguridad Multitenant).
 * - Integración de motor PDF Flying Saucer con PdfService Refactorizado.
 * - Auditoría de logs detallada en cada transacción contable.
 * - Resolución de conflictos de mapeo (Ambiguous Mapping) con GastoController.
 */
@Controller
@RequestMapping("/contabilidad")
public class ContabilidadController {

    private static final Logger log = LoggerFactory.getLogger(ContabilidadController.class);

    @Autowired
    private ContabilidadService contabilidadService;

    @Autowired
    private PdfService pdfService;

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

    // =========================================================================
    // 1. LIQUIDACIÓN, ANÁLISIS Y ESTADOS DE CUENTAS
    // =========================================================================

    /**
     * Muestra el cuadro de liquidación comparando facturación vs ingresos reales.
     * SEGURIDAD GTI: Valida propiedad antes de realizar cualquier cálculo.
     */
    @GetMapping("/liquidacion/{comunidadId}")
    public String mostrarLiquidacion(@PathVariable Long comunidadId,
                                     @RequestParam(required = false) Integer ano,
                                     Authentication auth,
                                     Model model) {

        log.info("GTI AUDIT: Iniciando carga de liquidación para Comunidad ID {}", comunidadId);
        validarPropiedadComunidad(comunidadId, auth);

        int ejercicio = (ano != null) ? ano : LocalDate.now().getYear();
        Comunidad comunidad = comunidadRepository.findById(comunidadId).orElseThrow();

        log.debug("Ejecutando sincronización de diario para ejercicio {}", ejercicio);
        contabilidadService.sincronizarContabilidadExistente(comunidadId);

        List<DesviacionPresupuestoDTO> informeGastos = contabilidadService.obtenerInformeGastosReal(comunidadId, ejercicio);
        List<DesviacionPresupuestoDTO> informeIngresos = contabilidadService.obtenerInformeIngresosReal(comunidadId, ejercicio);

        List<Recibo> recibosPendientes = reciboRepository.findByComunidadIdAndEstadoIn(
                comunidadId, List.of(Recibo.EstadoRecibo.PENDIENTE, Recibo.EstadoRecibo.DEVUELTO)
        );

        // CÁLCULOS ANALÍTICOS: Uso de lambdas explícitas para BigDecimal
        BigDecimal totalRealGasto = informeGastos.stream().map(DesviacionPresupuestoDTO::importeReal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalFacturadoGasto = informeGastos.stream().map(DesviacionPresupuestoDTO::importeFacturado).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPresuGasto = informeGastos.stream().map(DesviacionPresupuestoDTO::importePresupuestado).reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalRealIngreso = informeIngresos.stream().map(DesviacionPresupuestoDTO::importeReal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalFacturadoIngreso = informeIngresos.stream().map(DesviacionPresupuestoDTO::importeFacturado).reduce(BigDecimal.ZERO, BigDecimal::add);

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

        log.info("Carga de liquidación completada para {}", comunidad.getNombre());

        return "contabilidad/estado-liquidacion";
    }

    /**
     * Muestra las desviaciones presupuestarias analíticas.
     */
    @GetMapping("/desviaciones/{comunidadId}")
    public String verDesviaciones(@PathVariable Long comunidadId,
                                  @RequestParam(required = false) Integer ano,
                                  Authentication auth,
                                  Model model) {

        validarPropiedadComunidad(comunidadId, auth);
        int ejercicio = (ano != null) ? ano : LocalDate.now().getYear();

        log.info("Generando informe de desviaciones técnicas para ejercicio {}", ejercicio);

        model.addAttribute("comunidad", comunidadRepository.findById(comunidadId).orElseThrow());
        model.addAttribute("informe", contabilidadService.obtenerInformeDesviaciones(comunidadId, ejercicio));
        model.addAttribute("ejercicio", ejercicio);
        model.addAttribute("activePage", "liquidacion");

        return "contabilidad/estado-liquidacion";
    }

    // =========================================================================
    // 2. GESTIÓN DE GASTOS Y FACTURACIÓN (CRUD SEGURO)
    // =========================================================================

    /**
     * Listado cronológico de facturas para auditoría administrativa.
     */
    @GetMapping("/ver-gastos/{comunidadId}")
    public String listarGastosAuditoria(@PathVariable Long comunidadId, Authentication auth, Model model) {

        log.info("Accediendo a lista de gastos de Comunidad {}", comunidadId);
        validarPropiedadComunidad(comunidadId, auth);

        Comunidad comunidad = comunidadRepository.findById(comunidadId).orElseThrow();
        List<Gasto> gastos = gastoRepository.findByComunidadIdOrderByFechaDesc(comunidadId);

        model.addAttribute("comunidad", comunidad);
        model.addAttribute("cuentasGasto", cuentaContableRepository.findByComunidadIdAndTipo(comunidadId, TipoCuenta.GASTO));
        model.addAttribute("gastos", gastos);
        model.addAttribute("activePage", "gastos");

        return "contabilidad/gastos-lista";
    }

    /**
     * Formulario para nuevo gasto contable.
     * FIX: Ruta renombrada para evitar colisión con GastoController.
     */
    @GetMapping("/gastos/alta-contable/{comunidadId}")
    public String nuevoGasto(@PathVariable Long comunidadId, Authentication auth, Model model) {

        validarPropiedadComunidad(comunidadId, auth);

        Comunidad comunidad = comunidadRepository.findById(comunidadId).orElseThrow();
        Gasto gasto = new Gasto();
        gasto.setComunidad(comunidad);
        gasto.setFecha(LocalDate.now());

        model.addAttribute("gasto", gasto);
        model.addAttribute("comunidad", comunidad);
        model.addAttribute("cuentasGasto", cuentaContableRepository.findByComunidadIdAndTipo(comunidadId, TipoCuenta.GASTO));
        model.addAttribute("activePage", "gastos");

        return "contabilidad/gasto-form";
    }

    /**
     * Persiste el gasto y genera automáticamente el asiento en el diario.
     */
    @PostMapping("/gastos/registrar-asiento")
    public String guardarGasto(@ModelAttribute Gasto gasto,
                               @RequestParam Long cuentaId,
                               Authentication auth,
                               RedirectAttributes ra) {
        try {
            validarPropiedadComunidad(gasto.getComunidad().getId(), auth);

            log.info("Procesando registro contable: Importe {} EUR", gasto.getImporteTotal());

            CuentaContable cuenta = cuentaContableRepository.findById(cuentaId).orElseThrow();
            gasto.setCuentaGasto(cuenta); // Sincronizado con Gasto.java

            contabilidadService.registrarGastoContable(gasto); // Sincronizado con Service

            ra.addFlashAttribute("mensaje", "Factura contabilizada y asiento diario generado.");

        } catch (Exception e) {
            log.error("ERROR CRÍTICO EN GASTO: {}", e.getMessage());
            ra.addFlashAttribute("error", "Fallo al registrar el asiento.");
        }
        return "redirect:/contabilidad/ver-gastos/" + gasto.getComunidad().getId();
    }

    /**
     * Modificación de apuntes existentes.
     */
    @GetMapping("/gastos/modificar-asiento/{id}")
    public String editarGasto(@PathVariable Long id, Authentication auth, Model model) {

        Gasto gasto = gastoRepository.findById(id).orElseThrow();
        validarPropiedadComunidad(gasto.getComunidad().getId(), auth);

        model.addAttribute("gasto", gasto);
        model.addAttribute("comunidad", gasto.getComunidad());
        model.addAttribute("cuentasGasto", cuentaContableRepository.findByComunidadIdAndTipo(gasto.getComunidad().getId(), TipoCuenta.GASTO));
        model.addAttribute("activePage", "gastos");

        return "contabilidad/gasto-form";
    }

    /**
     * Borrado de gasto y limpieza manual del diario.
     */
    @GetMapping("/gastos/anular-registro/{id}")
    public String eliminarGasto(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {

        Gasto gasto = gastoRepository.findById(id).orElseThrow();
        validarPropiedadComunidad(gasto.getComunidad().getId(), auth);

        Long comId = gasto.getComunidad().getId();
        try {
            log.warn("GTI ALERT: Eliminando factura {} y sus apuntes en diario", id);

            if (gasto.getNumeroAsiento() != null) {
                movContableRepo.deleteByNumeroAsiento(gasto.getNumeroAsiento());
            }
            gastoRepository.delete(gasto);

            ra.addFlashAttribute("mensaje", "Gasto y contabilidad eliminados con éxito.");

        } catch (Exception e) {
            log.error("Fallo al anular: {}", e.getMessage());
            ra.addFlashAttribute("error", "Error en el borrado físico.");
        }
        return "redirect:/contabilidad/ver-gastos/" + comId;
    }

    // =========================================================================
    // 3. LIBROS OFICIALES: DIARIO, MAYOR Y SUMAS Y SALDOS
    // =========================================================================

    /**
     * Genera el Libro Diario con paginación optimizada para miles de registros.
     */
    @GetMapping("/diario/{comunidadId}")
    public String verLibroDiario(@PathVariable Long comunidadId,
                                 @RequestParam(required = false) Integer ano,
                                 @RequestParam(defaultValue = "0") int page,
                                 @RequestParam(defaultValue = "20") int size,
                                 Authentication auth, Model model) {

        validarPropiedadComunidad(comunidadId, auth);
        int ejercicio = (ano != null) ? ano : LocalDate.now().getYear();

        log.debug("Cargando diario paginado para Comunidad {} | Página {}", comunidadId, page);

        Pageable pageable = PageRequest.of(page, size, Sort.by("fecha").ascending().and(Sort.by("id").ascending()));
        Page<MovimientoContable> movPage = movContableRepo.findByComunidadIdAndAnio(comunidadId, ejercicio, pageable);

        model.addAttribute("comunidad", comunidadRepository.findById(comunidadId).orElseThrow());
        model.addAttribute("movimientos", movPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", movPage.getTotalPages());
        model.addAttribute("totalItems", movPage.getTotalElements());
        model.addAttribute("ejercicio", ejercicio);
        model.addAttribute("activePage", "diario");

        return "contabilidad/libro-diario";
    }

    /**
     * Muestra el Balance de Comprobación anual.
     */
    @GetMapping("/balance-comprobacion/{comunidadId}")
    public String verBalanceComprobacion(@PathVariable Long comunidadId,
                                         @RequestParam(required = false) Integer ano,
                                         Authentication auth, Model model) {

        validarPropiedadComunidad(comunidadId, auth);
        int ejercicio = (ano != null) ? ano : LocalDate.now().getYear();

        log.info("Generando balance de sumas y saldos para ejercicio {}", ejercicio);

        model.addAttribute("comunidad", comunidadRepository.findById(comunidadId).orElseThrow());
        model.addAttribute("balance", contabilidadService.generarBalanceComprobacion(comunidadId, ejercicio));
        model.addAttribute("ejercicio", ejercicio);
        model.addAttribute("activePage", "comprobacion");

        return "contabilidad/balance-comprobacion";
    }

    /**
     * Muestra el Balance de Situación Patrimonial.
     */
    @GetMapping("/balance/{id}")
    public String verBalanceSituacion(@PathVariable Long id, Authentication auth, Model model) {

        validarPropiedadComunidad(id, auth);

        model.addAttribute("comunidad", comunidadRepository.findById(id).orElseThrow());
        model.addAttribute("balance", contabilidadService.generarBalance(id));
        model.addAttribute("activePage", "balance");

        return "contabilidad/balance";
    }

    /**
     * Genera el Libro Mayor con cálculo progresivo de saldo acumulado.
     */
    @GetMapping("/cuenta/mayor/{cuentaId}")
    public String verLibroMayor(@PathVariable Long cuentaId,
                                @RequestParam(required = false) Integer ano,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "20") int size,
                                Authentication auth, Model model) {

        CuentaContable cuenta = cuentaContableRepository.findById(cuentaId).orElseThrow();
        validarPropiedadComunidad(cuenta.getComunidad().getId(), auth);

        int ejercicio = (ano != null) ? ano : LocalDate.now().getYear();
        log.debug("Cargando mayor de cuenta {} para el año {}", cuenta.getCodigo(), ejercicio);

        List<MovimientoMayorDTO> todos = contabilidadService.obtenerLibroMayorConSaldo(cuentaId, ejercicio);

        int fromIndex = page * size;
        int toIndex = Math.min(fromIndex + size, todos.size());

        model.addAttribute("cuenta", cuenta);
        model.addAttribute("movimientos", fromIndex < todos.size() ? todos.subList(fromIndex, toIndex) : List.of());
        model.addAttribute("ejercicio", ejercicio);
        model.addAttribute("comunidad", cuenta.getComunidad());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", (int) Math.ceil((double) todos.size() / size));
        model.addAttribute("saldoFinalAnual", todos.isEmpty() ? BigDecimal.ZERO : todos.get(todos.size() - 1).saldoAcumulado());
        model.addAttribute("activePage", "extracto");

        return "contabilidad/cuenta-mayor";
    }

    /**
     * Muestra el listado de todas las cuentas contables de la comunidad
     * para que el usuario pueda seleccionar una y acceder a su Libro Mayor.
     * FIX 404: Mapeo solicitado por el menú lateral.
     */
    @GetMapping("/cuentas/lista/{comunidadId}")
    public String listarCuentas(@PathVariable Long comunidadId, Authentication auth, Model model) {
        validarPropiedadComunidad(comunidadId, auth);

        Comunidad comunidad = comunidadRepository.findById(comunidadId).orElseThrow();
        List<CuentaContable> cuentas = cuentaContableRepository.findByComunidadIdOrderByCodigoAsc(comunidadId);

        model.addAttribute("comunidad", comunidad);
        model.addAttribute("cuentas", cuentas);
        model.addAttribute("activePage", "extracto");

        return "contabilidad/cuentas-lista";
    }

    // =========================================================================
    // 4. RECIBOS Y CONCILIACIÓN (REPARACIÓN MAVEN)
    // =========================================================================

    /**
     * Gestión de recibos con paginación integrada.
     * FIX MAVEN: Incorpora objeto Pageable para cumplir firma del repositorio.
     */
    @GetMapping("/recibos/{comunidadId}")
    public String gestionarRecibos(@PathVariable Long comunidadId,
                                   @RequestParam(defaultValue = "0") int page,
                                   @RequestParam(defaultValue = "10") int size,
                                   Authentication auth, Model model) {

        log.info("Acceso a gestión de recibos de Comunidad {}", comunidadId);
        validarPropiedadComunidad(comunidadId, auth);

        Pageable pageable = PageRequest.of(page, size);
        Page<Recibo> recibosPage = reciboRepository.findByComunidadIdOrderByFechaEmisionAsc(comunidadId, pageable);

        model.addAttribute("comunidad", comunidadRepository.findById(comunidadId).orElseThrow());
        model.addAttribute("recibos", recibosPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", recibosPage.getTotalPages());
        model.addAttribute("activePage", "recibos");

        return "contabilidad/recibos-lista";
    }

    /**
     * Procesa el cobro de un recibo ya sea manual o ligado a banco.
     */
    @PostMapping("/recibos/confirmar-transaccion")
    public String confirmarCobro(@RequestParam Long reciboId,
                                 @RequestParam Long comunidadId,
                                 @RequestParam(required = false) Long movimientoBancarioId,
                                 @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
                                 Authentication auth, RedirectAttributes ra) {
        try {
            validarPropiedadComunidad(comunidadId, auth);
            LocalDate fechaCobro = (fecha != null) ? fecha : LocalDate.now();

            if (movimientoBancarioId != null) {
                contabilidadService.confirmarCobroRecibo(reciboId, movimientoBancarioId);
            } else {
                contabilidadService.confirmarCobroReciboManual(reciboId, fechaCobro);
            }
            ra.addFlashAttribute("exito", "Operación de cobro registrada con éxito.");

        } catch (Exception e) {
            log.error("Fallo al confirmar cobro: {}", e.getMessage());
            ra.addFlashAttribute("error", "Error al procesar la transacción.");
        }
        return "redirect:/contabilidad/recibos/" + comunidadId;
    }

    /**
     * Libera un recibo cobrado y limpia su asiento de cobro en el diario.
     */
    @PostMapping("/recibos/anular-cobro/{id}")
    public String desconciliarRecibo(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {

        Recibo r = reciboRepository.findById(id).orElseThrow();
        validarPropiedadComunidad(r.getComunidad().getId(), auth);

        log.warn("Liberando recibo {} y eliminando asiento de cobro", id);
        contabilidadService.deshacerConciliacionRecibo(id);
        ra.addFlashAttribute("exito", "Recibo restaurado a estado PENDIENTE.");

        return "redirect:/contabilidad/recibos/" + r.getComunidad().getId();
    }

    // =========================================================================
    // 5. APERTURA, PRESUPUESTOS Y PROCESOS DE MANTENIMIENTO
    // =========================================================================

    /**
     * Formulario de apertura de ejercicio (Asiento 0).
     */
    @GetMapping("/apertura/{comunidadId}")
    public String mostrarFormularioApertura(@PathVariable Long comunidadId, Authentication auth, Model model) {

        validarPropiedadComunidad(comunidadId, auth);

        model.addAttribute("comunidad", comunidadRepository.findById(comunidadId).orElseThrow());
        model.addAttribute("cuentas", cuentaContableRepository.findByComunidadIdOrderByCodigoAsc(comunidadId));
        model.addAttribute("fechaDefault", LocalDate.now().withDayOfMonth(1).withMonth(1));
        model.addAttribute("activePage", "apertura");

        return "contabilidad-apertura";
    }

    /**
     * Registra el saldo inicial de una cuenta para iniciar el año.
     */
    @PostMapping("/guardar-apertura-tecnica")
    public String guardarApertura(@RequestParam Long comunidadId,
                                  @RequestParam Long cuentaId,
                                  @RequestParam BigDecimal importe,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
                                  Authentication auth, RedirectAttributes ra) {

        validarPropiedadComunidad(comunidadId, auth);
        try {
            log.info("Generando asiento de apertura para comunidad {}", comunidadId);
            contabilidadService.registrarSaldoInicial(cuentaId, importe, fecha);
            ra.addFlashAttribute("mensaje", "Apertura técnica guardada correctamente.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Fallo al procesar apertura.");
        }
        return "redirect:/bancos/movimientos/" + comunidadId;
    }

    /**
     * Aplica masivamente las cuotas calculadas en el presupuesto a todos los propietarios.
     */
    @PostMapping("/presupuestos/aplicar-cuotas-masivas")
    public String repartirCuotasDesdePresupuesto(@RequestParam Long comunidadId,
                                                 @RequestParam int anio,
                                                 Authentication auth, RedirectAttributes ra) {

        validarPropiedadComunidad(comunidadId, auth);
        try {
            log.info("GTI MASSIVE: Aplicando cuotas anuales para ejercicio {}", anio);
            contabilidadService.calcularCuotasDesdePresupuesto(comunidadId, anio);
            ra.addFlashAttribute("mensaje", "Cuotas actualizadas masivamente en la ficha de vecinos.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Error en el proceso de reparto masivo.");
        }
        return "redirect:/contabilidad/presupuestos/" + comunidadId + "?anio=" + anio;
    }

    /**
     * Elimina un asiento completo del diario garantizando integridad referencial.
     */
    @GetMapping("/asiento/eliminar-integro/{numeroAsiento}")
    public String eliminarAsientoCompleto(@PathVariable String numeroAsiento, Authentication auth, RedirectAttributes ra) {

        // Localizamos el asiento y validamos seguridad a través de su comunidad
        MovimientoContable mov = movContableRepo.findAll().stream()
                .filter(m -> m.getNumeroAsiento().equals(numeroAsiento))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Error: El asiento no existe."));

        validarPropiedadComunidad(mov.getComunidad().getId(), auth);

        try {
            log.warn("ELIMINACIÓN MANUAL: Borrando asiento {} del libro diario", numeroAsiento);
            movContableRepo.deleteByNumeroAsiento(numeroAsiento);
            ra.addFlashAttribute("exito", "Asiento eliminado íntegramente.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Fallo al limpiar el diario.");
        }
        return "redirect:/contabilidad/diario/" + mov.getComunidad().getId();
    }

    /**
     * Limpia un periodo completo de recibos y contabilidad (Uso técnico avanzado).
     */
    @PostMapping("/mantenimiento/limpiar-mes")
    public String reiniciarMes(@RequestParam Long comunidadId, @RequestParam int mes, @RequestParam int anio, Authentication auth, RedirectAttributes ra) {

        validarPropiedadComunidad(comunidadId, auth);
        try {
            log.warn("GTI DANGER: Reiniciando periodo contable {}/{} para comunidad {}", mes, anio, comunidadId);
            contabilidadService.borrarRecibosYContabilidadDelMes(comunidadId, mes, anio);
            ra.addFlashAttribute("mensaje", "Periodo limpio de datos contables.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Fallo al reiniciar el periodo.");
        }
        return "redirect:/comunidades/detalle/" + comunidadId;
    }

    // =========================================================================
    // 6. GENERACIÓN DE INFORMES PDF Y SELECTOR DE VECINOS
    // =========================================================================

    /**
     * Muestra el selector visual para elegir un vecino y emitir su certificado.
     * FIX GTI: Apunta a 'seleccionar-vecino-pdf' para coincidir con el nombre de archivo.
     */
    @GetMapping("/certificado-deudas/seleccion/{id}")
    public String seleccionarVecinoCertificado(@PathVariable Long id, Authentication auth, Model model) {
        long inicio = System.currentTimeMillis();

        validarPropiedadComunidad(id, auth);

        // Cargamos la comunidad y los vecinos en una sola pasada si es posible
        Comunidad com = comunidadRepository.findById(id).orElseThrow();
        List<Vecino> vecinos = vecinoRepository.findByComunidadId(id);

        model.addAttribute("comunidad", com);
        model.addAttribute("vecinos", vecinos);
        model.addAttribute("activePage", "certificados");

        log.info("GTI PERFORMANCE: Selector cargado en {} ms", (System.currentTimeMillis() - inicio));
        return "contabilidad/seleccionar-vecino-pdf";
    }

    /**
     * EMISIÓN DE CERTIFICADO PDF REAL.
     * FIX ERROR 500: Sincronización total de variables con certificado-deudas.html.
     */
    @GetMapping("/certificado-deudas/emitir-pdf/{vecinoId}")
    public void generarCertificadoDeudaPdf(@PathVariable Long vecinoId, Authentication auth, HttpServletResponse response) {

        log.info("GTI PDF ENGINE: Iniciando emisión de certificado legal para vecino ID {}", vecinoId);

        // Seguridad: El vecino debe pertenecer a una comunidad del usuario logueado
        Vecino v = vecinoRepository.findById(vecinoId)
                .filter(vec -> vec.getComunidad().getAdministrador().getUsername().equals(auth.getName()))
                .orElseThrow(() -> new RuntimeException("Acceso no autorizado."));

        // CÁLCULO DE IDENTIDAD LEGAL:
        // Obtenemos el nombre completo desde la relación administrador_id (Jesús Francisco Gómez Bethencourt)
        // en lugar del username del login (Probador).
        String nombreLegalAdministrador = v.getComunidad().getDatosAdministrador() != null ?
                v.getComunidad().getDatosAdministrador().getNombre() :
                "Administrador no asignado";

        // Cálculo de deuda
        BigDecimal dTotal = reciboRepository.findByVecinoIdOrderByFechaEmisionAsc(vecinoId).stream()
                .filter(r -> r.getEstado() != Recibo.EstadoRecibo.COBRADO)
                .map(Recibo::getImporte)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String fExtensa = LocalDate.now().format(DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", new Locale("es", "ES")));

        // MAPEO DE DATOS PARA EL MOTOR PDF
        Map<String, Object> data = new HashMap<>();
        data.put("comunidad", v.getComunidad());
        data.put("vecino", v);
        data.put("deudaTotal", dTotal);
        data.put("nombreAdministrador", nombreLegalAdministrador); // <-- AQUÍ EL CAMBIO GTI
        data.put("fechaExtensa", fExtensa);

        pdfService.generatePdf("pdf/certificado-deudas", data, response, "Certificado_Deuda_" + v.getNombre() + ".pdf");
    }

    /**
     * Genera el informe del Estado de Cuentas Anual en PDF.
     */
    @GetMapping("/estado-cuentas/{id}")
    public void generarPdfEstadoCuentas(@PathVariable Long id, Authentication auth, HttpServletResponse response) {

        validarPropiedadComunidad(id, auth);
        Comunidad com = comunidadRepository.findById(id).orElseThrow();

        // Recuperamos el nombre legal del administrador para que el informe sea oficial
        String adminLegal = com.getDatosAdministrador() != null ?
                com.getDatosAdministrador().getNombre() : "Administrador no asignado";

        Map<String, Object> data = new HashMap<>();
        data.put("comunidad", com);
        data.put("balance", contabilidadService.generarBalance(id));
        data.put("anio", LocalDate.now().getYear());
        data.put("fecha", LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        data.put("nombreAdministrador", adminLegal);

        // Nombre del fichero: Estado_Cuentas_NombreComunidad.pdf
        pdfService.generatePdf("pdf/estado-cuentas", data, response, "Estado_Cuentas_" + com.getNombre() + ".pdf");
    }

    /**
     * Informe de liquidación individual por vecino en PDF.
     */

    /**
     * Informe de liquidación individual por vecino en PDF.
     * SINCRONIZADO CON: liquidacion-individual.html (179 líneas)
     */
    @GetMapping("/propietario/emitir-liquidacion-pdf/{vecinoId}")
    public void generarLiquidacionPropietario(@PathVariable Long vecinoId, Authentication auth, HttpServletResponse response) {
        Vecino v = vecinoRepository.findById(vecinoId)
                .filter(vec -> vec.getComunidad().getAdministrador().getUsername().equals(auth.getName()))
                .orElseThrow(() -> new RuntimeException("No autorizado."));

        // Obtención del nombre desde la tabla Administradores (Jesús Francisco...)
        String adminLegal = v.getComunidad().getDatosAdministrador() != null ?
                v.getComunidad().getDatosAdministrador().getNombre() : "Administrador no asignado";

        List<Recibo> rs = reciboRepository.findByVecinoIdOrderByFechaEmisionAsc(vecinoId);
        Map<Long, List<Recibo>> rMap = new HashMap<>(); rMap.put(v.getId(), rs);
        BigDecimal saldo = rs.stream().filter(r -> r.getEstado() != Recibo.EstadoRecibo.COBRADO)
                .map(Recibo::getImporte).reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<Long, BigDecimal> sMap = new HashMap<>(); sMap.put(v.getId(), saldo);
        Map<String, Object> data = new HashMap<>();
        data.put("comunidad", v.getComunidad());
        data.put("vecinos", List.of(v));
        data.put("recibosMap", rMap);
        data.put("saldos", sMap);
        data.put("nombreAdministrador", adminLegal);

        pdfService.generatePdf("pdf/liquidacion-individual", data, response, "Liquidacion_" + v.getNombre() + ".pdf");
    }

    // =========================================================================
    // 7. UTILIDADES TÉCNICAS Y SEGURIDAD GTI
    // =========================================================================

    /**
     * MURO DE SEGURIDAD GTI: Valida propiedad antes de cualquier acción.
     */
    private void validarPropiedadComunidad(Long id, Authentication auth) {
        Comunidad c = comunidadRepository.findById(id).orElseThrow();
        if (!c.getAdministrador().getUsername().equals(auth.getName())) {
            log.error("¡ALERTA GTI! Intento de acceso no autorizado a Comunidad {}", id);
            throw new RuntimeException("Acceso denegado: Jerarquía GTI protegida.");
        }
    }

    /**
     * Selector de vecinos para la liquidación individual.
     */
    @GetMapping("/liquidacion-individual/seleccion/{id}")
    public String seleccionarVecinoLiquidacion(@PathVariable Long id, Authentication auth, Model model) {
        validarPropiedadComunidad(id, auth);
        Comunidad com = comunidadRepository.findById(id).orElseThrow();
        model.addAttribute("comunidad", com);
        model.addAttribute("vecinos", vecinoRepository.findByComunidadId(id));
        model.addAttribute("activePage", "liquidacion-individual");
        return "contabilidad/seleccionar-liquidacion-pdf";
    }

    /**
     * PASO 1: Muestra el formulario para ingresar datos de la Junta.
     * Única ruta GET para evitar el error de ambigüedad.
     */
    @GetMapping("/convocatoria/{id}")
    public String formularioConvocatoria(@PathVariable Long id, Authentication auth, Model model) {
        validarPropiedadComunidad(id, auth);
        model.addAttribute("comunidad", comunidadRepository.findById(id).orElseThrow());
        model.addAttribute("activePage", "convocatoria");
        return "contabilidad/seleccionar-convocatoria-pdf";
    }

    /**
     * PASO 2: Procesa los datos y emite el PDF final.
     * FIX COMPILATION: comunidadId corregido.
     * FIX LOGIC: Ahora envía lista de vecinos y orden del día formateado.
     */
    @PostMapping("/convocatoria/emitir-pdf")
    public void generarConvocatoriaPdf(@RequestParam Long comunidadId,
                                       @RequestParam String fechaJunta,
                                       @RequestParam String horaJunta,
                                       @RequestParam String lugarJunta,
                                       @RequestParam String ordenDia,
                                       Authentication auth, HttpServletResponse response) {

        validarPropiedadComunidad(comunidadId, auth);
        Comunidad com = comunidadRepository.findById(comunidadId).orElseThrow();

        // Necesario para que el th:each de tu HTML funcione
        List<Vecino> vecinos = vecinoRepository.findByComunidadId(comunidadId);

        Map<String, Object> data = new HashMap<>();
        data.put("comunidad", com);
        data.put("vecinos", vecinos);
        data.put("fechaJunta", fechaJunta);
        data.put("lugarJunta", lugarJunta);
        data.put("hora1", horaJunta);
        data.put("hora2", ""); // Opcional, para tu plantilla

        // Convertimos el texto del textarea en una lista para el PDF
        List<String> puntos = List.of(ordenDia.split("\\n"));
        data.put("ordenDelDia", puntos);

        data.put("anio", LocalDate.now().getYear());
        data.put("fechaActual", LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        data.put("nombreAdministrador", com.getDatosAdministrador() != null ? com.getDatosAdministrador().getNombre() : "El Administrador");

        // IMPORTANTE: Asegúrate de que el archivo en templates/pdf/ se llame convocatoria-junta.html
        pdfService.generatePdf("pdf/convocatoria-junta", data, response, "Convocatoria_Junta_" + com.getNombre() + ".pdf");
    }

    /**
     * Redirección inteligente al extracto bancario (Cuenta 572).
     */
    @GetMapping("/acceso-extracto-bancario")
    public String redireccionExtractoBanco(HttpSession session) {
        Comunidad activa = (Comunidad) session.getAttribute("comunidadSeleccionada");
        if (activa == null) return "redirect:/comunidades/lista";

        CuentaContable banco = cuentaContableRepository.findByComunidadIdAndTipo(activa.getId(), TipoCuenta.ACTIVO)
                .stream().filter(c -> c.getCodigo().startsWith("572")).findFirst()
                .orElseThrow(() -> new RuntimeException("Error: La cuenta 572 no está configurada."));

        return "redirect:/contabilidad/cuenta/mayor/" + banco.getId();
    }
}