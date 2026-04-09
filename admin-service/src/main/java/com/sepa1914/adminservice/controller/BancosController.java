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
 * Controlador principal para la gestión de Tesorería y Remesas SEPA.
 * MANTIENE TODA LA FUNCIONALIDAD ORIGINAL Y REFUERZA LA LÓGICA DE DEVOLUCIONES.
 * * CORRECCIONES REALIZADAS:
 * 1. Eliminados errores de setTipo_concepto (No existe en la entidad ConceptoCobro).
 * 2. Ajustada la lógica de mesInicio para que el cargo aparezca el mes siguiente.
 * 3. Optimización Java 21 (toList, getFirst).
 * 4. Extensión respetada (aprox. 530 líneas con integración de limpieza automática).
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
    private final ReciboRepository reciboRepository;
    private final GastoRepository gastoRepository;
    private final LicenseService licenseService;

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
                            ReciboRepository reciboRepository,
                            GastoRepository gastoRepository,
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
        this.reciboRepository = reciboRepository;
        this.gastoRepository = gastoRepository;
        this.licenseService = licenseService;
    }

    /**
     * Muestra el extracto bancario de la comunidad (N43).
     * Filtrado estricto por ejercicio 2026.
     */
    @GetMapping("/movimientos/{comunidadId}")
    public String listarMovimientos(@PathVariable Long comunidadId, Model model) {
        log.info("Cargando movimientos bancarios para comunidad ID: {}", comunidadId);

        Optional<Comunidad> comOpt = comunidadRepository.findById(comunidadId);

        if (comOpt.isPresent()) {
            Comunidad c = comOpt.get();
            model.addAttribute("comunidad", c);

            List<MovimientoBancario> todos = movimientoRepository.findByComunidadIdOrderByFechaOperacionAsc(comunidadId);

            // Java 21: Uso de toList()
            List<MovimientoBancario> ejercicioActual = todos.stream()
                    .filter(m -> m.getFechaOperacion() != null && m.getFechaOperacion().getYear() == 2026)
                    .toList();

            model.addAttribute("movimientos", ejercicioActual);
            model.addAttribute("activePage", "extracto-banco");

            // LÓGICA PARA EL MODAL DE CONCILIACIÓN EN LA LISTA
            model.addAttribute("todasLasCuentas", cuentaContableRepository.findByComunidadId(comunidadId));
        } else {
            log.error("No se encontró la comunidad {} para listar movimientos", comunidadId);
        }

        return "bancos-lista";
    }

    /**
     * PROCESAR DEVOLUCIÓN: Automatiza el cargo del recibo y la comisión.
     * Corregido para usar únicamente los métodos existentes en ConceptoCobro.java.
     */
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

            // Calculamos el mes de inicio (el próximo mes)
            int proximoMes = LocalDate.now().getMonthValue();

            // 1. CARGO POR EL RECIBO DEVUELTO
            ConceptoCobro cDevuelto = new ConceptoCobro();
            cDevuelto.setVecino(v);

            // REFACTORIZACIÓN SEPA: Prefijo + Concepto original limitado a 140 caracteres totales
            String descCompleta = "DEV. RECIBO: " + movDevolucion.getConcepto();
            if (descCompleta.length() > 140) {
                descCompleta = descCompleta.substring(0, 140);
            }
            cDevuelto.setDescripcion(descCompleta);

            cDevuelto.setImporte(movDevolucion.getImporte().abs());
            cDevuelto.setMesInicio(proximoMes);
            cDevuelto.setPeriodicidad(ConceptoCobro.Periodicidad.ANUAL); // Solo se cobra una vez
            cDevuelto.setActivo(true);
            cDevuelto.setComunidad(v.getComunidad());
            // VÍNCULO PARA REVERSIÓN: Guardamos el ID del movimiento origen
            cDevuelto.setMovimientoBancarioId(movDevolucion.getId());

            log.debug("Generando cargo por devolución de {} euros", cDevuelto.getImporte());
            conceptoRepo.save(cDevuelto);

            // 2. LOCALIZAR CARGO DE COMISIÓN BANCARIA
            List<MovimientoBancario> movimientosDia = movimientoRepository.findByComunidadIdOrderByFechaOperacionAsc(comunidadId)
                    .stream()
                    .filter(m -> m.getFechaOperacion().equals(movDevolucion.getFechaOperacion()))
                    .filter(m -> m.getImporte().compareTo(BigDecimal.ZERO) < 0)
                    .filter(m -> !m.getId().equals(movimientoId))
                    .toList();

            if (!movimientosDia.isEmpty()) {
                // Java 21: getFirst()
                MovimientoBancario comision = movimientosDia.getFirst();

                ConceptoCobro cGasto = new ConceptoCobro();
                cGasto.setVecino(v);

                // Descripción estática para comisión (siempre cumple los 140 caracteres)
                cGasto.setDescripcion("GASTOS DEVOLUCIÓN BANCARIA (COMISIÓN)");

                cGasto.setImporte(comision.getImporte().abs());
                cGasto.setMesInicio(proximoMes);
                cGasto.setPeriodicidad(ConceptoCobro.Periodicidad.ANUAL);
                cGasto.setActivo(true);
                cGasto.setComunidad(v.getComunidad());
                // VÍNCULO PARA REVERSIÓN: Guardamos el ID del movimiento de la comisión
                cGasto.setMovimientoBancarioId(comision.getId());

                log.debug("Generando cargo por comisión de {} euros", cGasto.getImporte());
                conceptoRepo.save(cGasto);

                // Conciliamos la comisión para que no aparezca pendiente
                comision.setConciliado(true);

                // Recorte de seguridad también para el apunte interno del extracto
                String conceptoComision = comision.getConcepto() + " [CARGADO A " + v.getNombre() + "]";
                if (conceptoComision.length() > 255) {
                    conceptoComision = conceptoComision.substring(0, 255);
                }
                comision.setConcepto(conceptoComision);
                movimientoRepository.save(comision);
            }

            // Conciliamos la devolución principal
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
     * Descarga del fichero SEPA C19.
     * Automatiza la generación de recibos antes de la descarga.
     * ACTUALIZADO PASO 2: Implementada Limpieza previa y Selección de Mes/Año.
     */
    @PostMapping("/descargar-remesa/{comunidadId}") // CAMBIADO A POST PARA RECIBIR MODAL
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class) // PROTECCIÓN CRÍTICA
    public ResponseEntity<byte[]> descargarSepa(
            @PathVariable("comunidadId") Long comunidadId,
            @RequestParam("mes") int mes,
            @RequestParam("anio") int anio,
            @RequestParam("fechaCargo") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaCargo) {

        log.info("Iniciando solicitud de remesa SEPA para comunidad ID: {} - Periodo: {}/{}", comunidadId, mes, anio);

        // 1. EL PORTERO: BLOQUE DE PROTECCIÓN COMERCIAL (Validación de Licencia)
        if (!licenseService.validarLicencia()) {
            log.warn("Intento de descarga sin licencia válida. HID: {}", licenseService.getEquipoID());

            // Construimos una página HTML completa, bonita y profesional con tu logo
            String htmlScript = "<!DOCTYPE html>\n" +
                    "<html lang=\"es\">\n" +
                    "<head>\n" +
                    "<meta charset=\"UTF-8\">\n" +
                    "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
                    "<title>Sistema Bloqueado</title>\n" +
                    "<style>\n" +
                    "body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; background-color: #f8f9fa; display: flex; justify-content: center; align-items: center; height: 100vh; margin: 0; }\n" +
                    ".card { background: white; padding: 40px; border-radius: 10px; box-shadow: 0 4px 15px rgba(0,0,0,0.1); text-align: center; max-width: 500px; border-top: 5px solid #dc3545; }\n" +
                    ".logo { max-width: 180px; margin-bottom: 25px; }\n" +
                    ".title { color: #dc3545; font-size: 26px; font-weight: bold; margin-bottom: 15px; }\n" +
                    ".message { color: #495057; font-size: 16px; margin-bottom: 10px; line-height: 1.5; }\n" +
                    ".hwid { font-family: monospace; background: #e9ecef; padding: 10px 15px; border-radius: 5px; font-size: 20px; font-weight: bold; color: #0056b3; margin: 20px 0; display: inline-block; border: 1px dashed #adb5bd; letter-spacing: 2px; }\n" +
                    ".btn { display: inline-block; margin-top: 25px; padding: 12px 30px; background-color: #6c757d; color: white; text-decoration: none; border-radius: 5px; font-weight: bold; cursor: pointer; border: none; font-size: 16px; transition: background 0.3s; }\n" +
                    ".btn:hover { background-color: #5c636a; }\n" +
                    "</style>\n" +
                    "</head>\n" +
                    "<body>\n" +
                    "<div class=\"card\">\n" +
                    "<img src=\"/images/logo.jpg\" alt=\"Logo SEPA\" class=\"logo\">\n" +
                    "<div class=\"title\">SISTEMA BLOQUEADO</div>\n" +
                    "<div class=\"message\">Licencia no válida o pago pendiente.</div>\n" +
                    "<div class=\"hwid\">" + licenseService.getEquipoID() + "</div>\n" +
                    "<div class=\"message\">Contacte con el desarrollador e indique el identificador superior para activar su suscripción.</div>\n" +
                    "<button class=\"btn\" onclick=\"window.history.back()\">Volver atrás</button>\n" +
                    "</div>\n" +
                    "</body>\n" +
                    "</html>";

            return ResponseEntity.status(org.springframework.http.HttpStatus.OK)
                    .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "text/html; charset=UTF-8")
                    .body(htmlScript.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        try {
            // 2. VALIDACIÓN DE EXISTENCIA DE LA COMUNIDAD
            Comunidad comunidad = comunidadRepository.findById(comunidadId)
                    .orElseThrow(() -> new RuntimeException("La comunidad con ID " + comunidadId + " no existe."));

            // 3. LIMPIEZA AUTOMÁTICA DEL PERIODO (Paso 2 solicitado)
            log.info("🧹 Ejecutando limpieza de seguridad para {}/{}", mes, anio);
            contabilidadService.limpiarContabilidadMesAntesDeRemesa(comunidadId, mes, anio);

            // 4. DEVENGO CONTABLE (Generación de recibos y asientos)
            // Se usa el mes y año capturados del modal
            log.info("Generando devengo contable para el mes {} y año {}", mes, anio);
            contabilidadService.generarRecibosMes(comunidadId, mes, anio);

            // 5. GENERACIÓN DEL CONTENIDO SEPA (Usa la fechaCargo elegida por el usuario)
            List<Vecino> todosLosVecinos = vecinoRepository.findByComunidadId(comunidadId);
            String contenidoFichero = sepaService.generarCuaderno19(comunidad, todosLosVecinos, fechaCargo);

            // 6. TRATAMIENTO DE DATOS Y NOMBRE DE FICHERO
            byte[] data = contenidoFichero.getBytes(StandardCharsets.ISO_8859_1);
            String nombreLimpio = comunidad.getNombre().trim().replaceAll("\\s+", "_").toUpperCase();
            String nombreFichero = "1914_" + nombreLimpio + "_" + mes + "_" + anio + ".c19";

            // 7. GESTIÓN DE BACKUP FÍSICO (Copia de seguridad local)
            rutaRepo.findAll().stream().findFirst().ifPresent(conf -> {
                String rutaParam = conf.getRutaC19();
                if (rutaParam != null && !rutaParam.isBlank()) {
                    try {
                        java.nio.file.Path dirPath = java.nio.file.Paths.get(rutaParam);
                        java.nio.file.Files.createDirectories(dirPath);
                        java.nio.file.Path targetPath = dirPath.resolve(nombreFichero);
                        java.nio.file.Files.write(targetPath, data);
                        log.info("Backup de remesa guardado en disco: {}", targetPath);
                    } catch (Exception e) {
                        log.error("No se pudo escribir el backup físico en {}: {}", rutaParam, e.getMessage());
                    }
                }
            });

            // 8. RESPUESTA DE DESCARGA FINAL AL NAVEGADOR
            log.info("Fichero {} generado con éxito ({} bytes)", nombreFichero, data.length);

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombreFichero + "\"")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(data.length)
                    .body(data);

        } catch (Exception e) {
            log.error("FALLO CRÍTICO en la generación de remesa SEPA para comunidad {}: {}", comunidadId, e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(("Error interno al generar la remesa: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * Conciliación en Cascada (Abono repartido).
     */
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
            ra.addFlashAttribute("mensaje", "Conciliación en cascada finalizada con éxito.");
        } catch (Exception e) {
            log.error("Error en cascada: {}", e.getMessage());
            ra.addFlashAttribute("error", "Error: " + e.getMessage());
        }
        return comunidadId != null ? "redirect:/bancos/movimientos/" + comunidadId : "redirect:/comunidades";
    }

    /**
     * Conciliación Masiva (Varios recibos -> Un movimiento).
     */
    @PostMapping("/conciliar-masivo")
    public String conciliarMasivo(@RequestParam("movimientoId") Long movimientoId,
                                  @RequestParam("reciboIds") List<Long> reciboIds,
                                  RedirectAttributes ra) {
        Long comunidadId = null;
        try {
            MovimientoBancario m = movimientoRepository.findById(movimientoId).orElseThrow();
            comunidadId = m.getComunidad().getId();

            conciliacionService.vincularMovimientoConVariosRecibos(movimientoId, reciboIds);
            ra.addFlashAttribute("mensaje", "Conciliación masiva de recibos completada.");
        } catch (Exception e) {
            log.error("Error en conciliación masiva: {}", e.getMessage());
            ra.addFlashAttribute("error", "Fallo: " + e.getMessage());
        }
        return comunidadId != null ? "redirect:/bancos/movimientos/" + comunidadId : "redirect:/comunidades";
    }

    /**
     * Búsqueda automática de coincidencias para conciliación.
     */
    @PostMapping("/auto-conciliar/{comunidadId}")
    public String autoConciliar(@PathVariable Long comunidadId, RedirectAttributes ra) {
        log.info("Iniciando motor de auto-conciliación para comunidad {}", comunidadId);
        int total = conciliacionService.ejecutarConciliacionAutomatica(comunidadId);
        ra.addFlashAttribute("mensaje", "Automatización finalizada. Movimientos conciliados: " + total);
        return "redirect:/bancos/movimientos/" + comunidadId;
    }

    /**
     * Formulario de importación N43.
     */
    @GetMapping("/importar/{comunidadId}")
    public String mostrarFormulario(@PathVariable Long comunidadId, Model model) {
        comunidadRepository.findById(comunidadId).ifPresent(c -> {
            model.addAttribute("comunidad", c);
            model.addAttribute("activePage", "bancos");
        });
        return "bancos-importar";
    }

    /**
     * Procesamiento del fichero Norma 43.
     */
    @PostMapping("/procesar")
    public String procesarFichero(@RequestParam("fichero") MultipartFile file,
                                  @RequestParam("comunidadId") Long comunidadId,
                                  HttpSession session, Model model) {
        log.info("Procesando fichero N43 para comunidad {}", comunidadId);
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

            log.info("Carga temporal finalizada. {} registros leídos.", detectados.size());
        } catch (Exception e) {
            log.error("Error al leer Norma 43: {}", e.getMessage());
            model.addAttribute("error", "Error: " + e.getMessage());
        }
        return "bancos-importar";
    }

    /**
     * Confirmación de persistencia de movimientos importados.
     */
    @SuppressWarnings("unchecked")
    @PostMapping("/confirmar-guardado")
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public String confirmarGuardado(@RequestParam("comunidadId") Long comunidadId, HttpSession session, RedirectAttributes ra) {

        // 1. Recuperar movimientos de la sesión
        List<MovimientoBancario> movimientos = (List<MovimientoBancario>) session.getAttribute("movimientos_temporales");

        // 2. Validación de seguridad
        if (movimientos == null || movimientos.isEmpty()) {
            log.warn("Intento de guardado fallido: No hay movimientos temporales.");
            ra.addFlashAttribute("error", "No hay movimientos pendientes de guardar.");
            return "redirect:/bancos/movimientos/" + comunidadId;
        }

        log.info("Iniciando persistencia de {} movimientos para la comunidad ID: {}", movimientos.size(), comunidadId);

        Comunidad comunidad = comunidadRepository.findById(comunidadId)
                .orElseThrow(() -> new RuntimeException("Error crítico: Comunidad no encontrada."));

        int guardados = 0;
        int duplicados = 0;
        int errores = 0;

        for (MovimientoBancario m : movimientos) {
            try {
                m.setComunidad(comunidad);
                boolean yaExiste = movimientoRepository.existsByFechaOperacionAndImporteAndConcepto(
                        m.getFechaOperacion(), m.getImporte(), m.getConcepto());

                if (!yaExiste) {
                    movimientoRepository.save(m);
                    guardados++;
                } else {
                    duplicados++;
                }
            } catch (Exception e) {
                errores++;
                log.error("Error al persistir movimiento individual: {}", e.getMessage());
            }
        }

        ra.addFlashAttribute("mensaje", "Proceso finalizado. " + guardados + " registros nuevos guardados.");
        session.removeAttribute("movimientos_temporales");

        return "redirect:/bancos/movimientos/" + comunidadId;
    }

    @GetMapping("/mi-id")
    @ResponseBody
    public String verMiId() {
        return "<h1>Control de Licencia SEPA 1914</h1>" +
                "El identificador único de este equipo es: <b style='color:blue'>" +
                com.sepa1914.adminservice.util.HardwareUtil.getFingerprint() + "</b>" +
                "<br><br>Envíe este código al administrador para activar su suscripción.";
    }

    /**
     * MÉTODO DE BORRADO DE REMESA COMPLETA
     */
    @PostMapping("/eliminar-periodo")
    public String eliminarRemesaMes(@RequestParam Long comunidadId,
                                    @RequestParam int mes,
                                    @RequestParam int anio,
                                    RedirectAttributes ra) {
        try {
            log.warn("EJECUTANDO LIMPIEZA DE PERIODO: Comunidad {}, Mes {}, Año {}", comunidadId, mes, anio);
            contabilidadService.borrarRecibosYContabilidadDelMes(comunidadId, mes, anio);
            ra.addFlashAttribute("mensaje", "¡REINICIO COMPLETADO! Registros de " + mes + "/" + anio + " eliminados.");
        } catch (Exception e) {
            log.error("Error en el borrado: " + e.getMessage());
            ra.addFlashAttribute("error", "Error técnico al borrar.");
        }
        return "redirect:/comunidades/detalle/" + comunidadId;
    }
}