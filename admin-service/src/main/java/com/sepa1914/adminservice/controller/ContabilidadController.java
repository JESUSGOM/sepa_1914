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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
 * VERSION 2.5: INTEGRACIÓN DE FIRMA DIGITAL FNMT SIN PÉRDIDA DE LÓGICA.
 * -----------------------------------------------------------------------------
 */
@Controller
@RequestMapping("/contabilidad")
public class ContabilidadController {

    private static final Logger log = LoggerFactory.getLogger(ContabilidadController.class);


    private final ContabilidadService contabilidadService;
    private final PdfService pdfService;
    private final ComunidadRepository comunidadRepository;
    private final CuentaContableRepository cuentaContableRepository;
    private final GastoRepository gastoRepository;
    private final VecinoRepository vecinoRepository;
    private final ReciboRepository reciboRepository;
    private final MovimientoContableRepository movContableRepo;
    private final MovimientoBancarioRepository movBancarioRepo;

    public ContabilidadController(ContabilidadService contabilidadService,
                                  PdfService pdfService,
                                  ComunidadRepository comunidadRepository,
                                  CuentaContableRepository cuentaContableRepository,
                                  GastoRepository gastoRepository,
                                  VecinoRepository vecinoRepository,
                                  ReciboRepository reciboRepository,
                                  MovimientoContableRepository movContableRepo,
                                  MovimientoBancarioRepository movBancarioRepo) {
        this.contabilidadService = contabilidadService;
        this.pdfService = pdfService;
        this.comunidadRepository = comunidadRepository;
        this.cuentaContableRepository = cuentaContableRepository;
        this.gastoRepository = gastoRepository;
        this.vecinoRepository = vecinoRepository;
        this.reciboRepository = reciboRepository;
        this.movContableRepo = movContableRepo;
        this.movBancarioRepo = movBancarioRepo;
    }

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
                                 @RequestParam(defaultValue = "15") int size,
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
    /**
     * Genera el Libro Mayor con rango de fechas flexible, buscador global y saldo anterior dinámico.
     * MEJORA GTI: Filtra conceptos, importes, fechas y asientos sin perder la integridad del saldo acumulado.
     */
    @GetMapping("/cuenta/mayor/{cuentaId}")
    public String verLibroMayor(@PathVariable Long cuentaId,
                                @RequestParam(required = false) Integer ano,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
                                @RequestParam(required = false) String buscar,
                                @RequestParam(defaultValue = "0") int page,
                                @RequestParam(defaultValue = "20") int size,
                                Authentication auth, Model model) {

        CuentaContable cuenta = cuentaContableRepository.findById(cuentaId).orElseThrow();
        validarPropiedadComunidad(cuenta.getComunidad().getId(), auth);

        int ejercicio = (ano != null) ? ano : LocalDate.now().getYear();

        // Estrategia de asignación temporal GTI
        LocalDate desde = (fechaDesde != null) ? fechaDesde : LocalDate.of(ejercicio, 1, 1);
        LocalDate hasta = (fechaHasta != null) ? fechaHasta : LocalDate.of(ejercicio, 12, 31);

        log.info("GTI LEDGER: Procesando mayor de cuenta {} entre {} y {} con filtro [{}]", cuenta.getCodigo(), desde, hasta, buscar);

        // Cargamos el histórico completo de movimientos para procesar el arrastre de saldos
        List<MovimientoContable> todosLosMovimientos = movContableRepo.findByCuentaIdOrderByFechaAsc(cuentaId);

        List<MovimientoMayorDTO> filtrados = new ArrayList<>();
        BigDecimal saldoAnterior = BigDecimal.ZERO;
        BigDecimal currentSaldo = BigDecimal.ZERO;

        for (MovimientoContable m : todosLosMovimientos) {
            BigDecimal debe = m.getDebe() != null ? m.getDebe() : BigDecimal.ZERO;
            BigDecimal haber = m.getHaber() != null ? m.getHaber() : BigDecimal.ZERO;

            // Si el movimiento es anterior a la ventana de fechas, acumula en saldo anterior
            if (m.getFecha().isBefore(desde)) {
                saldoAnterior = saldoAnterior.add(debe).subtract(haber);
            }

            // El saldo acumulado en ese instante del tiempo histórico
            currentSaldo = currentSaldo.add(debe).subtract(haber);

            // Verificamos si entra en el marco temporal
            if (!m.getFecha().isBefore(desde) && !m.getFecha().isAfter(hasta)) {
                boolean coincide = true;

                // Motor de filtrado de campos GTI (Concepto, Asiento, Fecha, Importes)
                if (buscar != null && !buscar.isBlank()) {
                    String bLower = buscar.toLowerCase();
                    boolean conMatch = m.getConcepto() != null && m.getConcepto().toLowerCase().contains(bLower);
                    boolean asMatch = m.getNumeroAsiento() != null && m.getNumeroAsiento().toLowerCase().contains(bLower);
                    boolean fechaMatch = m.getFecha().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")).contains(buscar) || m.getFecha().toString().contains(buscar);
                    boolean impMatch = debe.toString().contains(buscar) || haber.toString().contains(buscar);
                    coincide = conMatch || asMatch || fechaMatch || impMatch;
                }

                if (coincide) {
                    filtrados.add(new MovimientoMayorDTO(m.getFecha(), m.getConcepto(), m.getNumeroAsiento(), debe, haber, currentSaldo));
                }
            }
        }

        // Paginador en memoria RAM sobre resultados filtrados
        int fromIndex = page * size;
        int toIndex = Math.min(fromIndex + size, filtrados.size());

        model.addAttribute("cuenta", cuenta);
        model.addAttribute("movimientos", fromIndex < filtrados.size() ? filtrados.subList(fromIndex, toIndex) : List.of());
        model.addAttribute("ejercicio", ejercicio);
        model.addAttribute("fechaDesde", desde);
        model.addAttribute("fechaHasta", hasta);
        model.addAttribute("searchTerm", buscar);
        model.addAttribute("saldoAnterior", saldoAnterior);
        model.addAttribute("comunidad", cuenta.getComunidad());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", (int) Math.ceil((double) filtrados.size() / size));
        model.addAttribute("totalItems", filtrados.size());
        model.addAttribute("saldoFinalAnual", filtrados.isEmpty() ? saldoAnterior : filtrados.get(filtrados.size() - 1).saldoAcumulado());
        model.addAttribute("activePage", "extracto");

        return "contabilidad/cuenta-mayor";
    }

    /**
     * Muestra el listado de todas las cuentas contables de la comunidad
     * para que el usuario pueda seleccionar una y acceder a su Libro Mayor.
     * FIX 404: Mapeo solicitado por el menú lateral.
     */
    @GetMapping("/cuentas/lista/{comunidadId}")
    public String listarCuentas(@PathVariable Long comunidadId,
                                @RequestParam(value = "buscar", required = false) String buscar,
                                @RequestParam(value = "page", defaultValue = "0") int page,
                                @RequestParam(value = "size", defaultValue = "12") int size,
                                @RequestParam(value = "sortField", defaultValue = "codigo") String sortField,
                                @RequestParam(value = "sortDir", defaultValue = "asc") String sortDir,
                                Authentication auth, Model model) {
        validarPropiedadComunidad(comunidadId, auth);

        Comunidad comunidad = comunidadRepository.findById(comunidadId).orElseThrow();

        // Configuración de ordenación dinámica
        Sort sort = sortDir.equalsIgnoreCase("desc") ? Sort.by(sortField).descending() : Sort.by(sortField).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        Page<CuentaContable> paginaCuentas;

        // Desvío inteligente hacia motor de búsqueda o paginado general
        if (buscar != null && !buscar.trim().isEmpty()) {
            paginaCuentas = cuentaContableRepository.buscarPorComunidadYTexto(comunidadId, buscar, pageable);
        } else {
            paginaCuentas = cuentaContableRepository.findByComunidadId(comunidadId, pageable);
        }

        model.addAttribute("comunidad", comunidad);
        model.addAttribute("cuentas", paginaCuentas.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", paginaCuentas.getTotalPages());
        model.addAttribute("totalItems", paginaCuentas.getTotalElements());
        model.addAttribute("searchTerm", buscar);
        model.addAttribute("sortField", sortField);
        model.addAttribute("sortDir", sortDir);
        model.addAttribute("reverseSortDir", sortDir.equals("asc") ? "desc" : "asc");

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
    @PostMapping("/guardar-apertura")
    public String guardarApertura(
            @RequestParam Long comunidadId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam Long cuentaId,
            @RequestParam BigDecimal importe,
            RedirectAttributes ra) {

        try {
            log.info("Registrando saldo inicial para comunidad ID: {} en cuenta ID: {}", comunidadId, cuentaId);

            // Llamamos al servicio para que cree el asiento contable real
            contabilidadService.crearAsientoAperturaManual(comunidadId, fecha, cuentaId, importe);

            ra.addFlashAttribute("mensaje", "Asiento de apertura generado correctamente. Saldo inicial registrado.");
        } catch (Exception e) {
            log.error("Error al guardar apertura: {}", e.getMessage());
            ra.addFlashAttribute("error", "Error al generar el asiento: " + e.getMessage());
        }

        // Redirigimos al extracto bancario de esa comunidad para ver el resultado
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

    /**
     * Proceso de renumeración de asientos para mantener la correlatividad legal.
     * FIX 404: Endpoint para la acción de renumerar asientos.
     */
    @PostMapping("/comunidades/{id}/renumerar-asientos")
    public String renumerarAsientos(@PathVariable Long id,
                                    @RequestParam(required = false) Integer ano,
                                    Authentication auth,
                                    RedirectAttributes ra) {

        // Muro de seguridad GTI
        validarPropiedadComunidad(id, auth);

        // Si no se especifica año, usamos el ejercicio actual (2026)
        int ejercicio = (ano != null) ? ano : LocalDate.now().getYear();

        try {
            log.info("GTI MAINTENANCE: Iniciando renumeración de asientos para comunidad {} - Ejercicio {}", id, ejercicio);

            // Llamada al método existente en el Service
            contabilidadService.renumerarAsientosEjercicio(id, ejercicio);

            ra.addFlashAttribute("mensaje", "Renumeración de asientos completada con éxito para el ejercicio " + ejercicio + ".");
        } catch (Exception e) {
            log.error("Fallo en renumeración: {}", e.getMessage());
            ra.addFlashAttribute("error", "No se pudo completar la renumeración de los asientos.");
        }

        // Redirigimos de vuelta al libro diario para ver el resultado
        return "redirect:/contabilidad/diario/" + id;
    }

    // =========================================================================
    // 6. GENERACIÓN DE INFORMES PDF CON FIRMA ELECTRÓNICA GTI
    // =========================================================================

    /**
     * Muestra el selector visual para elegir un vecino y emitir su certificado.
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
     * EMISIÓN DE CERTIFICADO PDF REAL CON FIRMA DIGITAL.
     * REFACTOR GTI: Ahora devuelve ResponseEntity<byte[]> para integrar la firma FNMT.
     */
    @GetMapping("/certificado-deudas/emitir-pdf/{vecinoId}")
    public ResponseEntity<byte[]> generarCertificadoDeudaPdf(@PathVariable Long vecinoId, Authentication auth) {

        log.info("GTI SIGN ENGINE: Iniciando certificación legal firmada para vecino ID {}", vecinoId);

        Vecino v = vecinoRepository.findById(vecinoId)
                .filter(vec -> vec.getComunidad().getAdministrador().getUsername().equals(auth.getName()))
                .orElseThrow(() -> new RuntimeException("Acceso no autorizado."));

        String nombreLegalAdministrador = v.getComunidad().getDatosAdministrador() != null ?
                v.getComunidad().getDatosAdministrador().getNombre() : "Jesús Francisco Gómez Bethencourt";

        BigDecimal dTotal = reciboRepository.findByVecinoIdOrderByFechaEmisionAsc(vecinoId).stream()
                .filter(r -> r.getEstado() != Recibo.EstadoRecibo.COBRADO)
                .map(r -> r.getImporte() != null ? r.getImporte() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String fExtensa = LocalDate.now().format(DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.of("es", "ES")));

        Map<String, Object> data = new HashMap<>();
        data.put("comunidad", v.getComunidad());
        data.put("vecino", v);
        data.put("deudaTotal", dTotal);
        data.put("nombreAdministrador", nombreLegalAdministrador);
        data.put("fechaExtensa", fExtensa);

        try {
            // Generamos los bytes del PDF y aplicamos la firma digital
            byte[] pdfLimpio = pdfService.generatePdfBytes("pdf/certificado-deudas", data);
            byte[] pdfFirmado = pdfService.firmarDocumento(pdfLimpio);

            String fileName = "Certificado_Deuda_" + v.getNombre().replace(" ", "_") + ".pdf";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdfFirmado);
        } catch (Exception e) {
            log.error("Error en motor de firma: {}", e.getMessage());
            throw new RuntimeException("No se pudo generar el documento firmado.");
        }
    }

    /**
     * Genera el informe del Estado de Cuentas Anual en PDF.
     */
    @GetMapping("/estado-cuentas/{id}")
    public ResponseEntity<byte[]> generarPdfEstadoCuentas(@PathVariable Long id, Authentication auth) {

        validarPropiedadComunidad(id, auth);
        Comunidad com = comunidadRepository.findById(id).orElseThrow();

        String adminLegal = com.getDatosAdministrador() != null ?
                com.getDatosAdministrador().getNombre() : "Administrador no asignado";

        Map<String, Object> data = new HashMap<>();
        data.put("comunidad", com);
        data.put("balance", contabilidadService.generarBalance(id));
        data.put("anio", LocalDate.now().getYear());
        data.put("fecha", LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        data.put("nombreAdministrador", adminLegal);

        try {
            byte[] pdfBytes = pdfService.generatePdfBytes("pdf/estado-cuentas", data);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Estado_Cuentas_" + com.getNombre() + ".pdf\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdfBytes);
        } catch (Exception e) {
            throw new RuntimeException("Error al generar informe.");
        }
    }

    /**
     * Informe de liquidación individual con Firma Digital GTI.
     */
    @GetMapping("/propietario/emitir-liquidacion-pdf/{vecinoId}")
    public ResponseEntity<byte[]> generarLiquidacionPropietario(@PathVariable Long vecinoId, Authentication auth) {
        Vecino v = vecinoRepository.findById(vecinoId)
                .filter(vec -> vec.getComunidad().getAdministrador().getUsername().equals(auth.getName()))
                .orElseThrow(() -> new RuntimeException("No autorizado."));

        String adminLegal = v.getComunidad().getDatosAdministrador() != null ?
                v.getComunidad().getDatosAdministrador().getNombre() : "Administrador";

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

        try {
            byte[] pdfIn = pdfService.generatePdfBytes("pdf/liquidacion-individual", data);
            byte[] pdfOut = pdfService.firmarDocumento(pdfIn);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Liquidacion_" + v.getNombre() + ".pdf\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdfOut);
        } catch (Exception e) {
            throw new RuntimeException("Error en firma de liquidación.");
        }
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

    @GetMapping("/liquidacion-individual/seleccion/{id}")
    public String seleccionarVecinoLiquidacion(@PathVariable Long id, Authentication auth, Model model) {
        validarPropiedadComunidad(id, auth);
        Comunidad com = comunidadRepository.findById(id).orElseThrow();
        model.addAttribute("comunidad", com);
        model.addAttribute("vecinos", vecinoRepository.findByComunidadId(id));
        model.addAttribute("activePage", "liquidacion-individual");
        return "contabilidad/seleccionar-liquidacion-pdf";
    }

    @GetMapping("/convocatoria/{id}")
    public String formularioConvocatoria(@PathVariable Long id, Authentication auth, Model model) {
        validarPropiedadComunidad(id, auth);
        model.addAttribute("comunidad", comunidadRepository.findById(id).orElseThrow());
        model.addAttribute("activePage", "convocatoria");
        return "contabilidad/seleccionar-convocatoria-pdf";
    }

    @PostMapping("/convocatoria/emitir-pdf")
    public ResponseEntity<byte[]> generarConvocatoriaPdf(@RequestParam Long comunidadId,
                                                         @RequestParam String fechaJunta,
                                                         @RequestParam String horaJunta,
                                                         @RequestParam String lugarJunta,
                                                         @RequestParam String ordenDia,
                                                         Authentication auth) {

        validarPropiedadComunidad(comunidadId, auth);
        Comunidad com = comunidadRepository.findById(comunidadId).orElseThrow();

        Map<String, Object> data = new HashMap<>();
        data.put("comunidad", com);
        data.put("vecinos", vecinoRepository.findByComunidadId(comunidadId));
        data.put("fechaJunta", fechaJunta);
        data.put("lugarJunta", lugarJunta);
        data.put("hora1", horaJunta);
        data.put("ordenDelDia", List.of(ordenDia.split("\\n")));
        data.put("anio", LocalDate.now().getYear());
        data.put("fechaActual", LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        data.put("nombreAdministrador", com.getDatosAdministrador() != null ? com.getDatosAdministrador().getNombre() : "El Administrador");

        try {
            byte[] pdfBytes = pdfService.generatePdfBytes("pdf/convocatoria-junta", data);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"Convocatoria.pdf\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdfBytes);
        } catch (Exception e) {
            throw new RuntimeException("Error al generar PDF.");
        }
    }

    @GetMapping("/acceso-extracto-bancario")
    public String redireccionExtractoBanco(HttpSession session) {
        Comunidad activa = (Comunidad) session.getAttribute("comunidadSeleccionada");
        if (activa == null) return "redirect:/comunidades/lista";

        CuentaContable banco = cuentaContableRepository.findByComunidadIdAndTipo(activa.getId(), TipoCuenta.ACTIVO)
                .stream().filter(c -> c.getCodigo().startsWith("572")).findFirst()
                .orElseThrow(() -> new RuntimeException("Error: La cuenta 572 no está configurada."));

        return "redirect:/contabilidad/cuenta/mayor/" + banco.getId();
    }

    @GetMapping("/gastos/regenerar/{id}")
    public String regenerarGastos(@PathVariable Long id, RedirectAttributes ra) {
        try {
            log.info("GTI Operación: Regenerando asientos de gastos para comunidad {}", id);
            contabilidadService.regenerarAsientosGastos(id);
            ra.addFlashAttribute("mensaje", "Sincronización completada: Se han generado los asientos de devengo (6 -> 4) para los gastos pendientes.");
        } catch (Exception e) {
            log.error("Error en regeneración: {}", e.getMessage());
            ra.addFlashAttribute("error", "Error al regenerar asientos: " + e.getMessage());
        }
        return "redirect:/contabilidad/gastos/" + id;
    }

    /**
     * CONCILIACIÓN MANUAL DIRECTA GTI: Vincula un apunte bancario directamente con una cuenta.
     * PROTEGIDA: Evita errores 400 si faltan parámetros en el envío desde el front-end.
     */
    @PostMapping("/conciliar-manual-directo")
    public String conciliarManualDirecto(@RequestParam(value = "movId", required = false) Long movId,
                                         @RequestParam(value = "cuentaId", required = false) Long cuentaId,
                                         @RequestParam(value = "comunidadId", required = false) Long comunidadId,
                                         RedirectAttributes ra) {

        // Si falta la Comunidad, volvemos a la lista general por seguridad
        if (comunidadId == null) {
            ra.addFlashAttribute("error", "Error: No se ha recibido el identificador de la comunidad.");
            return "redirect:/comunidades/lista";
        }

        try {
            if (movId == null || cuentaId == null) {
                throw new RuntimeException("Parámetros insuficientes recibidos en el lote de conciliación directa.");
            }

            log.info("GTI LEDGER: Conciliando apunte ID {} con cuenta contable ID {}", movId, cuentaId);
            contabilidadService.conciliarManualDirecto(movId, cuentaId);
            ra.addFlashAttribute("mensaje", "¡Éxito! Movimiento bancario conciliado directamente con la cuenta seleccionada.");

        } catch (Exception e) {
            log.error("Fallo en conciliación manual directa: {}", e.getMessage());
            ra.addFlashAttribute("error", "No se pudo realizar la conciliación: " + e.getMessage());
        }

        return "redirect:/bancos/movimientos/" + comunidadId;
    }
}