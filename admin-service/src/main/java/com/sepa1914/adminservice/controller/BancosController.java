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

/**
 * Controlador GTI Turbo 2.2: Gestión de Tesorería y Remesas SEPA.
 * Optimizado para eliminar advertencias de campos no usados y errores de concurrencia.
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

    // Inyección optimizada: Se eliminan reciboRepository y gastoRepository por no ser utilizados.
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
                            LicenseService licenseService) {

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
    }

    @GetMapping("/movimientos/{comunidadId}")
    public String listarMovimientos(@PathVariable Long comunidadId, Model model) {
        log.info("Cargando movimientos bancarios para comunidad ID: {}", comunidadId);
        Optional<Comunidad> comOpt = comunidadRepository.findById(comunidadId);

        if (comOpt.isPresent()) {
            Comunidad c = comOpt.get();
            model.addAttribute("comunidad", c);
            List<MovimientoBancario> todos = movimientoRepository.findByComunidadIdOrderByFechaOperacionAsc(comunidadId);
            List<MovimientoBancario> ejercicioActual = todos.stream()
                    .filter(m -> m.getFechaOperacion() != null && m.getFechaOperacion().getYear() == 2026)
                    .toList();
            model.addAttribute("movimientos", ejercicioActual);
            model.addAttribute("activePage", "extracto-banco");
            model.addAttribute("todasLasCuentas", cuentaContableRepository.findByComunidadId(comunidadId));
        } else {
            log.error("No se encontró la comunidad {} para listar movimientos", comunidadId);
        }
        return "bancos-lista";
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

    /**
     * Descarga SEPA GTI Turbo 2.2: Se elimina @Transactional para permitir que los hilos
     * paralelos del SepaService no colisionen con la sesión de Hibernate.
     */
    @PostMapping("/descargar-remesa/{comunidadId}")
    public ResponseEntity<byte[]> descargarSepa(
            @PathVariable("comunidadId") Long comunidadId,
            @RequestParam("mes") int mes,
            @RequestParam("anio") int anio,
            @RequestParam("fechaCargo") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaCargo) {

        log.info("Iniciando remesa SEPA - Periodo: {}/{}", mes, anio);

        if (!licenseService.validarLicencia()) {
            log.warn("Descarga bloqueada: Sin licencia válida.");
            String htmlScript = "<!DOCTYPE html><html lang=\"es\"><head><meta charset=\"UTF-8\"><title>Sistema Bloqueado</title><style>body { font-family: sans-serif; background: #f8f9fa; display: flex; justify-content: center; align-items: center; height: 100vh; } .card { background: white; padding: 40px; border-radius: 10px; box-shadow: 0 4px 15px rgba(0,0,0,0.1); text-align: center; border-top: 5px solid #dc3545; } .hwid { background: #e9ecef; padding: 10px; font-weight: bold; color: #0056b3; margin: 20px 0; display: inline-block; }</style></head><body><div class=\"card\"><h2>SISTEMA BLOQUEADO</h2><p>Licencia no válida o pago pendiente.</p><div class=\"hwid\">" + licenseService.getEquipoID() + "</div><p>Contacte con soporte para activar su suscripción.</p><button onclick=\"window.history.back()\">Volver</button></div></body></html>";
            return ResponseEntity.status(200).header("Content-Type", "text/html; charset=UTF-8").body(htmlScript.getBytes(StandardCharsets.UTF_8));
        }

        try {
            Comunidad comunidad = comunidadRepository.findById(comunidadId)
                    .orElseThrow(() -> new RuntimeException("Comunidad no encontrada."));

            log.info("🧹 Limpieza y generación de devengos para periodo {}/{}", mes, anio);
            contabilidadService.limpiarContabilidadMesAntesDeRemesa(comunidadId, mes, anio);
            contabilidadService.generarRecibosMes(comunidadId, mes, anio);

            // OPTIMIZACIÓN N+1: Carga masiva de vecinos con sus conceptos
            List<Vecino> vecinos = vecinoRepository.findAllByComunidadIdWithConceptos(comunidadId);

            // Generación con motor paralelo
            String contenidoFichero = sepaService.generarCuaderno19(comunidad, vecinos, fechaCargo);

            byte[] data = contenidoFichero.getBytes(StandardCharsets.ISO_8859_1);
            String nombreFichero = "1914_" + comunidad.getNombre().trim().replaceAll("\\s+", "_").toUpperCase() + "_" + mes + "_" + anio + ".c19";

            rutaRepo.findAll().stream().findFirst().ifPresent(conf -> {
                if (conf.getRutaC19() != null && !conf.getRutaC19().isBlank()) {
                    try {
                        java.nio.file.Path dirPath = java.nio.file.Paths.get(conf.getRutaC19());
                        java.nio.file.Files.createDirectories(dirPath);
                        java.nio.file.Files.write(dirPath.resolve(nombreFichero), data);
                        log.info("Backup SEPA guardado en: {}", dirPath.resolve(nombreFichero));
                    } catch (Exception e) {
                        log.error("Fallo al escribir backup: {}", e.getMessage());
                    }
                }
            });

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombreFichero + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(data.length)
                    .body(data);

        } catch (Exception e) {
            log.error("FALLO CRÍTICO en remesa SEPA {}: {}", comunidadId, e.getMessage());
            return ResponseEntity.internalServerError().contentType(MediaType.TEXT_PLAIN)
                    .body(("Error interno: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
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
            log.info("Carga temporal N43 finalizada: {} registros.", detectados.size());
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
                log.error("Error persistiendo movimiento: {}", e.getMessage());
            }
        }
        // Resolviendo advertencias de variables no usadas mediante log detallado
        log.info("Persistencia N43 finalizada. Guardados: {}, Duplicados: {}, Errores: {}", guardados, duplicados, errores);
        ra.addFlashAttribute("mensaje", "Proceso finalizado. " + guardados + " nuevos registros.");
        session.removeAttribute("movimientos_temporales");
        return "redirect:/bancos/movimientos/" + comunidadId;
    }

    @GetMapping("/mi-id")
    @ResponseBody
    public String verMiId() {
        return "<h1>Control de Licencia SEPA 1914</h1>El identificador único de este equipo es: <b style='color:blue'>" + com.sepa1914.adminservice.util.HardwareUtil.getFingerprint() + "</b><br><br>Envíe este código para activar su suscripción.";
    }

    @PostMapping("/eliminar-periodo")
    public String eliminarRemesaMes(@RequestParam Long comunidadId, @RequestParam int mes, @RequestParam int anio, RedirectAttributes ra) {
        try {
            log.warn("ELIMINANDO PERIODO: Comunidad {}, {}/{}", comunidadId, mes, anio);
            contabilidadService.borrarRecibosYContabilidadDelMes(comunidadId, mes, anio);
            ra.addFlashAttribute("mensaje", "¡REINICIO COMPLETADO!");
        } catch (Exception e) {
            log.error("Error borrando periodo: {}", e.getMessage());
            ra.addFlashAttribute("error", "Error técnico al borrar.");
        }
        return "redirect:/comunidades/detalle/" + comunidadId;
    }
}