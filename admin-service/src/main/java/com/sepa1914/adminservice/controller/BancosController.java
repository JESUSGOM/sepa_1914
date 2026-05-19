package com.sepa1914.adminservice.controller;

import com.sepa1914.adminservice.model.*;
import com.sepa1914.adminservice.repository.*;
import com.sepa1914.adminservice.service.*;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.security.core.Authentication;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Controlador GTI Turbo 2.2: Gestión de Tesorería y Remesas SEPA.
 */
@Controller
@RequestMapping("/bancos")
public class BancosController {

    private static final Logger log = LoggerFactory.getLogger(BancosController.class);

    private final Norma43Service n43Service;
    private final SepaService sepaService;
    private final ComunidadRepository comunidadRepository;
    private final VecinoRepository vecinoRepository;
    private final MovimientoBancarioRepository movimientoRepository;
    private final ConciliacionService conciliacionService;
    private final ConfiguracionRutasRepository rutaRepo;
    private final ContabilidadService contabilidadService;
    private final ConceptoCobroRepository conceptoRepo;
    private final CuentaContableRepository cuentaContableRepository;
    private final LicenseService licenseService;
    private final UsuarioRepository usuarioRepository;

    public BancosController(Norma43Service n43Service,
                            SepaService sepaService,
                            ComunidadRepository comunidadRepository,
                            VecinoRepository vecinoRepository,
                            MovimientoBancarioRepository movimientoRepository,
                            ConciliacionService conciliacionService,
                            ConfiguracionRutasRepository rutaRepo,
                            ContabilidadService contabilidadService,
                            ConceptoCobroRepository conceptoRepo,
                            CuentaContableRepository cuentaContableRepository,
                            LicenseService licenseService,
                            UsuarioRepository usuarioRepository) {

        this.n43Service = n43Service;
        this.sepaService = sepaService;
        this.comunidadRepository = comunidadRepository;
        this.vecinoRepository = vecinoRepository;
        this.movimientoRepository = movimientoRepository;
        this.conciliacionService = conciliacionService;
        this.rutaRepo = rutaRepo;
        this.contabilidadService = contabilidadService;
        this.conceptoRepo = conceptoRepo;
        this.cuentaContableRepository = cuentaContableRepository;
        this.licenseService = licenseService;
        this.usuarioRepository = usuarioRepository;
    }

    /**
     * Listado de movimientos optimizado con Paginación, Ordenación de Columnas y Búsqueda por Importe en Servidor.
     */
    @GetMapping("/movimientos/{comunidadId}")
    public String listarMovimientos(
            @PathVariable Long comunidadId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "25") int size,
            @RequestParam(value = "sortField", defaultValue = "fechaOperacion") String sortField,
            @RequestParam(value = "sortDir", defaultValue = "asc") String sortDir,
            @RequestParam(value = "importe", required = false) String importeBusqueda,
            Model model) {

        log.info("Cargando movimientos bancarios para comunidad ID: {} | Pág: {} | Orden: {} {}", comunidadId, page, sortField, sortDir);
        Optional<Comunidad> comOpt = comunidadRepository.findById(comunidadId);

        if (comOpt.isPresent()) {
            Comunidad c = comOpt.get();
            model.addAttribute("comunidad", c);

            Sort sort = sortDir.equalsIgnoreCase("desc")
                    ? Sort.by(sortField).descending().and(Sort.by("id").descending())
                    : Sort.by(sortField).ascending().and(Sort.by("id").ascending());

            Pageable pageable = PageRequest.of(page, size, sort);
            Page<MovimientoBancario> paginaMovimientos;

            if (importeBusqueda != null && !importeBusqueda.isBlank()) {
                try {
                    BigDecimal valor = new BigDecimal(importeBusqueda.replace(",", "."));
                    paginaMovimientos = movimientoRepository.findByComunidadIdAndImporte(comunidadId, valor, pageable);
                } catch (Exception e) {
                    log.warn("Formato de importe de búsqueda no válido: {}", importeBusqueda);
                    paginaMovimientos = movimientoRepository.findByComunidadId(comunidadId, pageable);
                }
            } else {
                paginaMovimientos = movimientoRepository.findByComunidadId(comunidadId, pageable);
            }

            model.addAttribute("movimientos", paginaMovimientos.getContent());
            model.addAttribute("pagina", paginaMovimientos);
            model.addAttribute("currentPage", page);
            model.addAttribute("totalPages", paginaMovimientos.getTotalPages());
            model.addAttribute("totalItems", paginaMovimientos.getTotalElements());
            model.addAttribute("importeBusqueda", importeBusqueda);

            model.addAttribute("sortField", sortField);
            model.addAttribute("sortDir", sortDir);
            model.addAttribute("reverseSortDir", sortDir.equals("asc") ? "desc" : "asc");

            model.addAttribute("activePage", "extracto-banco");
            model.addAttribute("todasLasCuentas", cuentaContableRepository.findByComunidadId(comunidadId));

            // NUEVO GTI: Pasamos los recibos pendientes para el Modal de Conciliación Colectiva
            model.addAttribute("recibosPendientes", conciliacionService.obtenerRecibosPendientes(comunidadId));
        } else {
            log.error("No se encontró la comunidad {} para listar movimientos", comunidadId);
            return "redirect:/comunidades/lista?error=finca_no_encontrada";
        }
        return "bancos-lista";
    }

    /**
     * NUEVO ENDPOINT GTI: Procesa la conciliación manual de múltiples apuntes contra múltiples recibos.
     * Resuelve de forma definitiva el abono fraccionado de remesas por parte de las entidades bancarias.
     */
    @PostMapping("/conciliar-multiples")
    public String conciliarMultiplesMovimientos(
            @RequestParam("movimientoIds") List<Long> movimientoIds,
            @RequestParam(value = "reciboIds", required = false) List<Long> reciboIds,
            RedirectAttributes ra) {

        Long comunidadId = null;
        try {
            if (movimientoIds == null || movimientoIds.isEmpty()) {
                throw new RuntimeException("Debe marcar al menos un apunte bancario mediante el casillero.");
            }
            if (reciboIds == null || reciboIds.isEmpty()) {
                throw new RuntimeException("Debe seleccionar al menos un recibo pendiente para cruzar la operación.");
            }

            MovimientoBancario primerMov = movimientoRepository.findById(movimientoIds.get(0))
                    .orElseThrow(() -> new RuntimeException("Movimiento no localizado."));
            comunidadId = primerMov.getComunidad().getId();

            conciliacionService.vincularMultiplesMovimientosConVariosRecibos(movimientoIds, reciboIds);
            ra.addFlashAttribute("mensaje", "¡GTI ÉXITO! Sincronización completada. Se han conciliado los apuntes y liquidado los recibos.");

        } catch (Exception e) {
            log.error("Fallo en conciliación multi-abono: {}", e.getMessage());
            ra.addFlashAttribute("error", "Error al conciliar bloque: " + e.getMessage());
        }

        return comunidadId != null ? "redirect:/bancos/movimientos/" + comunidadId : "redirect:/comunidades";
    }

    @PostMapping("/desconciliar/{id}")
    public String desconciliarMovimientoUnitario(@PathVariable("id") Long id, RedirectAttributes ra) {
        Long comunidadId = null;
        try {
            MovimientoBancario mov = movimientoRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Movimiento bancario no localizado."));

            comunidadId = mov.getComunidad().getId();

            // Cambiamos el estado técnico del movimiento
            mov.setConciliado(false);
            movimientoRepository.save(mov);

            // Aquí puedes llamar a tu servicio contable si necesitas reabrir
            // los recibos que estaban vinculados originalmente a este movimiento
            // contabilidadService.deshacerAsientoCobroPorMovimiento(id);

            ra.addFlashAttribute("mensaje", "¡GTI ÉXITO! Movimiento bancario liberado y desconciliado correctamente.");
        } catch (Exception e) {
            log.error("Fallo al desconciliar movimiento bancario: {}", e.getMessage());
            ra.addFlashAttribute("error", "Error al revertir la conciliación: " + e.getMessage());
        }

        return comunidadId != null ? "redirect:/bancos/movimientos/" + comunidadId : "redirect:/comunidades/lista";
    }

    @PostMapping("/procesar-devolucion")
    public String procesarDevolucion(@RequestParam("movimientoId") Long movimientoId,
                                     @RequestParam("vecinoId") Long vecinoId,
                                     RedirectAttributes ra) {
        log.info("Procesando devolución: Movimiento {}, Vecino {}", movimientoId, vecinoId);
        try {
            MovimientoBancario movDevolucion = movimientoRepository.findById(movimientoId)
                    .orElseThrow(() -> new RuntimeException("Error: Movimiento no encontrado"));
            Vecino v = vecinoRepository.findById(vecinoId)
                    .orElseThrow(() -> new RuntimeException("Error: Vecino no encontrado"));

            Long comunidadId = v.getComunidad().getId();
            int proximoMes = LocalDate.now().getMonthValue();

            ConceptoCobro cDevuelto = new ConceptoCobro();
            cDevuelto.setVecino(v);
            String descCompleta = "DEV. RECIBO: " + movDevolucion.getConcepto();
            if (descCompleta.length() > 140) descCompleta = descCompleta.substring(0, 140);
            cDevuelto.setDescripcion(descCompleta);
            cDevuelto.setImporte(movDevolucion.getImporte().abs());
            cDevuelto.setMesInicio(proximoMes);
            cDevuelto.setPeriodicidad(ConceptoCobro.Periodicidad.ANUAL);
            cDevuelto.setActivo(true);
            cDevuelto.setComunidad(v.getComunidad());
            cDevuelto.setMovimientoBancarioId(movDevolucion.getId());
            conceptoRepo.save(cDevuelto);

            List<MovimientoBancario> movimientosDia = movimientoRepository.findByComunidadIdOrderByFechaOperacionAsc(comunidadId)
                    .stream()
                    .filter(m -> m.getFechaOperacion().equals(movDevolucion.getFechaOperacion()))
                    .filter(m -> m.getImporte().compareTo(BigDecimal.ZERO) < 0)
                    .filter(m -> !m.getId().equals(movimientoId))
                    .toList();

            if (!movimientosDia.isEmpty()) {
                MovimientoBancario comision = movimientosDia.getFirst();
                ConceptoCobro cGasto = new ConceptoCobro();
                cGasto.setVecino(v);
                cGasto.setDescripcion("GASTOS DEVOLUCIÓN BANCARIA (COMISIÓN)");
                cGasto.setImporte(comision.getImporte().abs());
                cGasto.setMesInicio(proximoMes);
                cGasto.setPeriodicidad(ConceptoCobro.Periodicidad.ANUAL);
                cGasto.setActivo(true);
                cGasto.setComunidad(v.getComunidad());
                cGasto.setMovimientoBancarioId(comision.getId());
                conceptoRepo.save(cGasto);

                comision.setConciliado(true);
                String conceptoComision = comision.getConcepto() + " [CARGADO A " + v.getNombre() + "]";
                if (conceptoComision.length() > 255) conceptoComision = conceptoComision.substring(0, 255);
                comision.setConcepto(conceptoComision);
                movimientoRepository.save(comision);
            }

            movDevolucion.setConciliado(true);
            movimientoRepository.save(movDevolucion);
            ra.addFlashAttribute("mensaje", "Éxito: Se han repercutido el recibo (" + movDevolucion.getImporte().abs() + "€) y su comisión al vecino.");
            return "redirect:/bancos/movimientos/" + comunidadId;
        } catch (Exception e) {
            log.error("Fallo al procesar la devolución: {}", e.getMessage());
            ra.addFlashAttribute("error", "Fallo al registrar la devolución.");
            return "redirect:/comunidades";
        }
    }

    @PostMapping("/descargar-remesa/{comunidadId}")
    public ResponseEntity<byte[]> descargarSepa(
            @PathVariable("comunidadId") Long comunidadId,
            @RequestParam("mes") int mes,
            @RequestParam("anio") int anio,
            @RequestParam("fechaCargo") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaCargo) {

        log.info("Iniciando remesa SEPA - Periodo: {}/{}", mes, anio);

        if (!licenseService.validarLicencia()) {
            log.warn("Descarga bloqueada: Sin licencia válida.");
            String htmlScript = "<!DOCTYPE html><html lang=\"es\"><head><meta charset=\"UTF-8\"><title>Sistema Bloqueado</title></head><body><div style='text-align:center; margin-top:50px;'><h2>SISTEMA BLOQUEADO</h2><p>Contacte con soporte.</p></div></body></html>";
            return ResponseEntity.status(200).header("Content-Type", "text/html; charset=UTF-8").body(htmlScript.getBytes(StandardCharsets.UTF_8));
        }

        try {
            Comunidad comunidad = comunidadRepository.findById(comunidadId)
                    .orElseThrow(() -> new RuntimeException("Comunidad no encontrada."));

            contabilidadService.limpiarContabilidadMesAntesDeRemesa(comunidadId, mes, anio);
            contabilidadService.generarRecibosMes(comunidadId, mes, anio);

            List<Vecino> vecinos = vecinoRepository.findAllByComunidadIdWithConceptos(comunidadId);
            String contenidoFichero = sepaService.generarCuaderno19(comunidad, vecinos, fechaCargo);

            byte[] data = contenidoFichero.getBytes(StandardCharsets.ISO_8859_1);
            String nombreFichero = "RMS-" + comunidad.getNombre().trim().replaceAll("\\s+", "_").toUpperCase() + "_" + mes + "_" + anio + ".c19";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombreFichero + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(data);

        } catch (Exception e) {
            log.error("FALLO CRÍTICO en remesa SEPA {}: {}", comunidadId, e.getMessage());
            return ResponseEntity.internalServerError().body(("Error interno: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
        }
    }

    @PostMapping("/conciliar-cascada")
    public String conciliarCascada(@RequestParam("movimientoId") Long movimientoId,
                                   @RequestParam("vecinoId") Long vecinoId,
                                   RedirectAttributes ra) {
        Long comunidadId = null;
        try {
            MovimientoBancario m = movimientoRepository.findById(movimientoId)
                    .orElseThrow(() -> new RuntimeException("Movimiento no encontrado"));
            comunidadId = m.getComunidad().getId();
            conciliacionService.conciliarEnCascada(movimientoId, vecinoId);
            ra.addFlashAttribute("mensaje", "Conciliación en cascada finalizada.");
        } catch (Exception e) {
            log.error("Error en cascada: {}", e.getMessage());
            ra.addFlashAttribute("error", "Error: " + e.getMessage());
        }
        return comunidadId != null ? "redirect:/bancos/movimientos/" + comunidadId : "redirect:/comunidades";
    }

    @PostMapping("/conciliar-masivo")
    public String conciliarMasivo(@RequestParam("movimientoId") Long movimientoId,
                                  @RequestParam("reciboIds") List<Long> reciboIds,
                                  RedirectAttributes ra) {
        Long comunidadId = null;
        try {
            MovimientoBancario m = movimientoRepository.findById(movimientoId).orElseThrow();
            comunidadId = m.getComunidad().getId();
            conciliacionService.vincularMovimientoConVariosRecibos(movimientoId, reciboIds);
            ra.addFlashAttribute("mensaje", "Conciliación masiva completada.");
        } catch (Exception e) {
            log.error("Error en conciliación masiva: {}", e.getMessage());
            ra.addFlashAttribute("error", "Fallo: " + e.getMessage());
        }
        return comunidadId != null ? "redirect:/bancos/movimientos/" + comunidadId : "redirect:/comunidades";
    }

    @PostMapping("/auto-conciliar/{comunidadId}")
    public String autoConciliar(@PathVariable Long comunidadId, RedirectAttributes ra) {
        log.info("Iniciando motor de auto-conciliación...");
        int total = conciliacionService.ejecutarConciliacionAutomatica(comunidadId);
        ra.addFlashAttribute("mensaje", "Automatización finalizada. Conciliados: " + total);
        return "redirect:/bancos/movimientos/" + comunidadId;
    }

    @GetMapping("/importar/{comunidadId}")
    public String mostrarFormulario(@PathVariable Long comunidadId, Model model) {
        comunidadRepository.findById(comunidadId).ifPresent(c -> {
            model.addAttribute("comunidad", c);
            model.addAttribute("activePage", "bancos");
        });
        return "bancos-importar";
    }

    @PostMapping("/procesar")
    public String procesarFichero(@RequestParam("fichero") MultipartFile file,
                                  @RequestParam("comunidadId") Long comunidadId,
                                  HttpSession session, Model model) {
        try {
            Comunidad comunidad = comunidadRepository.findById(comunidadId).orElseThrow();
            BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.ISO_8859_1));
            List<String> lineas = reader.lines().toList();
            reader.close();
            List<MovimientoBancario> detectados = n43Service.parsearFichero(lineas, comunidad);
            session.setAttribute("movimientos_temporales", detectados);
            model.addAttribute("movimientos", detectados);
            model.addAttribute("comunidad", comunidad);
            model.addAttribute("activePage", "bancos");
        } catch (Exception e) {
            log.error("Error al leer Norma 43: {}", e.getMessage());
            model.addAttribute("error", "Error: " + e.getMessage());
        }
        return "bancos-importar";
    }

    @SuppressWarnings("unchecked")
    @PostMapping("/confirmar-guardado")
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public String confirmarGuardado(@RequestParam("comunidadId") Long comunidadId, HttpSession session, RedirectAttributes ra) {
        List<MovimientoBancario> movimientos = (List<MovimientoBancario>) session.getAttribute("movimientos_temporales");
        if (movimientos == null || movimientos.isEmpty()) {
            ra.addFlashAttribute("error", "No hay movimientos pendientes.");
            return "redirect:/bancos/movimientos/" + comunidadId;
        }

        Comunidad comunidad = comunidadRepository.findById(comunidadId).orElseThrow();
        int guardados = 0, duplicados = 0, errores = 0;

        for (MovimientoBancario m : movimientos) {
            try {
                m.setComunidad(comunidad);
                if (!movimientoRepository.existsByFechaOperacionAndImporteAndConcepto(m.getFechaOperacion(), m.getImporte(), m.getConcepto())) {
                    movimientoRepository.save(m);
                    guardados++;
                } else {
                    duplicados++;
                }
            } catch (Exception e) {
                errores++;
                log.error("Error persisting movimiento: {}", e.getMessage());
            }
        }
        log.info("Persistencia N43 finalizada. Guardados: {}, Duplicados: {}, Errores: {}", guardados, duplicados, errores);
        ra.addFlashAttribute("mensaje", "Proceso finalizado. " + guardados + " nuevos registros.");
        session.removeAttribute("movimientos_temporales");
        return "redirect:/bancos/movimientos/" + comunidadId;
    }

    @GetMapping("/mi-id")
    @ResponseBody
    public String verMiId() {
        return "<h1>Control de Licencia SEPA 1914</h1>ID: " + com.sepa1914.adminservice.util.HardwareUtil.getFingerprint();
    }

    @PostMapping("/eliminar-periodo")
    public String eliminarRemesaMes(@RequestParam Long comunidadId, @RequestParam int mes, @RequestParam int anio, RedirectAttributes ra) {
        try {
            log.warn("ELIMINANDO PERIODO: Comunidad {}, {}/{}", comunidadId, mes, anio);
            contabilidadService.borrarRecibosYContabilidadDelMes(comunidadId, mes, anio);
            ra.addFlashAttribute("mensaje", "¡REINICIO COMPLETADO!");
        } catch (Exception e) {
            log.error("Error borrando periodo: {}", e.getMessage());
            ra.addFlashAttribute("error", "Error técnico.");
        }
        return "redirect:/comunidades/detalle/" + comunidadId;
    }

    @PostMapping("/confirmar-n43")
    public String confirmarImportacionN43(@RequestParam Long comunidadId, HttpSession session, RedirectAttributes ra) {
        @SuppressWarnings("unchecked")
        List<MovimientoBancario> temporales = (List<MovimientoBancario>) session.getAttribute("movimientos_temporales");
        Comunidad com = comunidadRepository.findById(comunidadId).orElse(null);

        if (temporales == null || com == null) {
            ra.addFlashAttribute("error", "Error de sesión.");
            return "redirect:/comunidades/lista";
        }

        int guardados = 0;
        for (MovimientoBancario m : temporales) {
            m.setComunidad(com);
            if (!movimientoRepository.existsByFechaOperacionAndImporteAndConcepto(m.getFechaOperacion(), m.getImporte(), m.getConcepto())) {
                movimientoRepository.save(m);
                guardados++;
            }
        }
        session.removeAttribute("movimientos_temporales");
        ra.addFlashAttribute("mensaje", "Importados: " + guardados);
        return "redirect:/bancos/movimientos/" + comunidadId;
    }

    @PostMapping("/vaciar-extracto")
    public String vaciarExtracto(@RequestParam Long comunidadId, RedirectAttributes ra, Authentication auth) {
        Usuario actual = getUsuarioLogueado(auth);
        Comunidad com = comunidadRepository.findById(comunidadId).orElse(null);

        if (com != null && com.getAdministrador().getId().equals(actual.getId())) {
            try {
                contabilidadService.vaciarExtractoBancario(comunidadId);
                ra.addFlashAttribute("mensaje", "Extracto vaciado correctamente.");
            } catch (Exception e) {
                ra.addFlashAttribute("error", "Error técnico.");
            }
        }
        return "redirect:/bancos/movimientos/" + comunidadId;
    }

    private Usuario getUsuarioLogueado(Authentication auth) {
        if (auth == null) return null;
        return usuarioRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }
}