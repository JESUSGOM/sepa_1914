package com.sepa1914.adminservice.controller;

import com.sepa1914.adminservice.model.*;
import com.sepa1914.adminservice.repository.*;
import com.sepa1914.adminservice.service.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication; // Necesario para SEPA
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate; // Necesario para SEPA
import java.util.List;
import java.util.stream.Collectors;

/**
 * Controlador para la gestión de Recibos y Remesas SEPA.
 * MANTIENE TODA LA FUNCIONALIDAD ORIGINAL (209 LÍNEAS).
 * CORRECCIÓN: Alineación de firmas con ContabilidadService para evitar errores de compilación.
 * Gestiona el ciclo de vida de los recibos: generación, limpieza y conciliación masiva.
 */
@Controller
@RequestMapping // Se deja vacío para que convivan las rutas /recibos y /sepa sin romper tus enlaces
public class ReciboController {

    private static final Logger log = LoggerFactory.getLogger(ReciboController.class);

    // --- TUS INYECCIONES ORIGINALES (MANTENIDAS) ---
    @Autowired
    private ReciboRepository reciboRepository;

    @Autowired
    private ComunidadRepository comunidadRepository;

    @Autowired
    private ContabilidadService contabilidadService;

    // --- NUEVAS INYECCIONES PARA SEPA (AÑADIDAS AL FINAL) ---
    @Autowired
    private VecinoRepository vecinoRepository;

    @Autowired
    private SepaService sepaService;

    @Autowired
    private FileStorageService storageService;

    @Autowired
    private ConfiguracionRutasRepository rutasRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    // =========================================================================
    // 1. MÉTODOS ORIGINALES (ÍNTEGROS - COPIADOS LÍNEA A LÍNEA)
    // =========================================================================

    /**
     * Muestra la pantalla principal para generar recibos SEPA.
     * Permite seleccionar la comunidad y visualizar el estado actual.
     */
    @GetMapping("/recibos/generar")
    public String mostrarGenerador(Model model) {
        log.info("Accediendo al generador de recibos de comunidad");

        List<Comunidad> comunidades = comunidadRepository.findAll();
        model.addAttribute("comunidades", comunidades);
        model.addAttribute("activePage", "recibos");

        return "recibos-generar";
    }

    /**
     * PROCESO DE REGENERACIÓN: Borra recibos pendientes y crea los nuevos.
     * Útil cuando se cambia el importe de un vecino y hay que repetir la remesa del mes.
     */
    @PostMapping("/recibos/limpiar-y-generar")
    public String limpiarYGenerar(@RequestParam Long comunidadId,
                                  @RequestParam int mes,
                                  @RequestParam int anio,
                                  RedirectAttributes ra) {

        log.info("Iniciando regeneración de recibos para comunidad {} - Mes: {} Año: {}", comunidadId, mes, anio);

        try {
            // 1. Borramos los recibos PENDIENTES del mes/año para evitar duplicados
            reciboRepository.deleteRecibosNoCobradosMes(comunidadId, mes, anio);
            log.debug("Limpieza de recibos pendientes completada");

            // 2. Generamos los nuevos recibos con los importes configurados actualmente
            contabilidadService.generarRecibosMes(comunidadId, mes, anio);
            log.debug("Generación de nuevos recibos finalizada");

            ra.addFlashAttribute("mensaje", "Recibos borrados y regenerados con éxito para el periodo seleccionado.");

        } catch (Exception e) {
            log.error("Error crítico al regenerar recibos: {}", e.getMessage());
            ra.addFlashAttribute("error", "Error en el proceso de regeneración: " + e.getMessage());
        }

        return "redirect:/recibos/generar";
    }

    /**
     * Lista los recibos de una comunidad para su conciliación bancaria manual.
     * Muestra tanto cobrados como pendientes, ordenados por fecha de emisión descendente.
     */
    @GetMapping("/recibos/lista/{comunidadId}")
    public String listarPendientes(@PathVariable Long comunidadId,
                                   @RequestParam(defaultValue = "0") int page, Model model) {

        log.debug("Listando recibos para comunidad ID: {}", comunidadId);

        Comunidad comunidad = comunidadRepository.findById(comunidadId)
                .orElseThrow(() -> new RuntimeException("Error: Comunidad no encontrada"));

        // Implementación de paginación real (10 registros por página)
        PageRequest paginaRequest = PageRequest.of(page, 10);
        Page<Recibo> paginaRecibos = reciboRepository.findByComunidadIdOrderByFechaEmisionAsc(comunidadId, paginaRequest);

        model.addAttribute("comunidad", comunidad);
        model.addAttribute("recibos", paginaRecibos.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", paginaRecibos.getTotalPages());
        model.addAttribute("activePage", "recibos-conciliacion");

        return "contabilidad/recibos-lista";
    }

    /**
     * FUNCIONALIDAD MANTENIDA: Procesa la confirmación de cobro INDIVIDUAL.
     * Realiza el asiento contable automático y marca el recibo como cobrado.
     */
    @PostMapping("/recibos/confirmar")
    public String confirmarCobro(@RequestParam Long reciboId,
                                 @RequestParam Long comunidadId,
                                 @RequestParam Long movimientoBancarioId) {

        log.info("Confirmando cobro individual para recibo ID: {} vinculado al banco ID: {}", reciboId, movimientoBancarioId);

        try {
            // Se envía movimientoBancarioId (Long) para que el Service genere el Asiento Resumen contra el banco real
            contabilidadService.confirmarCobroRecibo(reciboId, movimientoBancarioId);

            log.debug("Cobro individual procesado y asiento contable generado correctamente");

        } catch (Exception e) {
            log.error("Error al procesar el cobro individual: {}", e.getMessage());
            return "redirect:/recibos/lista/" + comunidadId + "?error=fallo_contable";
        }

        return "redirect:/recibos/lista/" + comunidadId + "?exito=true";
    }

    /**
     * NUEVA FUNCIONALIDAD: Procesa la confirmación de una REMESA COMPLETA.
     * Agrupa varios recibos en el proceso contable masivo (Asiento Resumen).
     */
    @PostMapping("/recibos/confirmar-masivo")
    public String confirmarCobroMasivo(@RequestParam("reciboIds") List<Long> reciboIds,
                                       @RequestParam Long comunidadId,
                                       @RequestParam Long movimientoBancarioId) {

        log.info("Iniciando cobro masivo (Remesa) para {} recibos. Banco ID: {}",
                reciboIds != null ? reciboIds.size() : 0, movimientoBancarioId);

        if (reciboIds == null || reciboIds.isEmpty()) {
            log.warn("Intento de cobro masivo sin selección de recibos");
            return "redirect:/recibos/lista/" + comunidadId + "?error=sin_seleccion";
        }

        try {
            // El servicio genera un UNICO apunte en la 572 (Banco) y múltiples en la 430 (Vecinos)
            contabilidadService.procesarCobroRemesaCompleta(comunidadId, reciboIds, movimientoBancarioId);

            log.info("Conciliación masiva finalizada con éxito");

        } catch (Exception e) {
            log.error("Error crítico en el proceso de conciliación masiva: {}", e.getMessage());
            return "redirect:/recibos/lista/" + comunidadId + "?error=proceso_fallido";
        }

        return "redirect:/recibos/lista/" + comunidadId + "?exito_masivo=true";
    }

    /**
     * NUEVA FUNCIONALIDAD: Concilia TODOS los recibos pendientes de un mes específico.
     * Facilita la gestión de remesas bancarias completas.
     */
    @PostMapping("/recibos/confirmar-mes-completo")
    public String confirmarMesCompleto(@RequestParam Long comunidadId,
                                       @RequestParam int mes,
                                       @RequestParam int anio,
                                       @RequestParam Long movimientoBancarioId) {

        log.info("Confirmación mes completo {}/{} - Comunidad {} - Banco ID: {}", mes, anio, comunidadId, movimientoBancarioId);

        // Localizamos los IDs de los recibos PENDIENTES de la comunidad para el periodo solicitado
        List<Long> recibosIds = reciboRepository.findByComunidadId(comunidadId).stream()
                .filter(r -> r.getFechaEmision().getMonthValue() == mes
                        && r.getFechaEmision().getYear() == anio
                        && r.getEstado() == Recibo.EstadoRecibo.PENDIENTE)
                .map(Recibo::getId)
                .collect(Collectors.toList());

        if (recibosIds.isEmpty()) {
            log.warn("No existen recibos pendientes para el periodo indicado");
            return "redirect:/recibos/lista/" + comunidadId + "?error=no_pendientes";
        }

        try {
            // Genera el asiento resumen contra el movimiento bancario especificado
            contabilidadService.procesarCobroRemesaCompleta(comunidadId, recibosIds, movimientoBancarioId);

            log.info("Procesados con éxito {} recibos del mes mediante asiento resumen", recibosIds.size());

        } catch (Exception e) {
            log.error("Error crítico al procesar el cierre mensual de recibos: {}", e.getMessage());
            return "redirect:/recibos/lista/" + comunidadId + "?error=error_proceso";
        }

        return "redirect:/recibos/lista/" + comunidadId + "?exito_masivo=true";
    }

    // =========================================================================
    // 2. NUEVA FUNCIONALIDAD SEPA (AÑADIDA AL FINAL - SIN BORRAR NADA)
    // =========================================================================

    /**
     * Pantalla para generar el fichero bancario SEPA (.C19)
     * URL: /sepa/generar
     */
    @GetMapping("/sepa/generar")
    public String mostrarGeneradorSepa(Model model) {
        log.info("Accediendo a la pantalla de generación de ficheros SEPA");
        model.addAttribute("comunidades", comunidadRepository.findAll());
        model.addAttribute("activePage", "sepa");
        return "sepa/generar-form";
    }

    /**
     * Procesa la remesa y guarda el archivo físico en el disco duro.
     * Usa la ruta configurada en la tabla configuracion_rutas.
     */
    @PostMapping("/sepa/procesar")
    public String procesarRemesaSepa(@RequestParam Long comunidadId,
                                     @RequestParam String fechaCobro,
                                     Authentication auth,
                                     RedirectAttributes ra) {
        try {
            Comunidad comunidad = comunidadRepository.findById(comunidadId).orElseThrow();
            List<Vecino> vecinos = vecinoRepository.findByComunidadId(comunidadId);
            LocalDate fecha = LocalDate.parse(fechaCobro);

            // Generamos el contenido Norma 19-14
            String contenidoC19 = sepaService.generarCuaderno19(comunidad, vecinos, fecha);

            // Obtenemos la ruta configurada para este usuario
            Usuario actual = usuarioRepository.findByUsername(auth.getName()).orElseThrow();
            ConfiguracionRutas config = rutasRepository.findByAdministrador(actual)
                    .orElseThrow(() -> new RuntimeException("No has configurado las rutas de guardado en el menú 'Rutas Archivos'."));

            // Guardado automático en la carpeta de producción
            String nombreArchivo = "REMES_C19_" + comunidad.getId() + "_" + fecha + ".c19";
            storageService.guardarArchivoAutomatico(config.getRutaC19(), nombreArchivo, contenidoC19.getBytes());

            ra.addFlashAttribute("mensaje", "Fichero SEPA generado y guardado en: " + config.getRutaC19());

        } catch (Exception e) {
            log.error("Error al generar fichero SEPA: {}", e.getMessage());
            ra.addFlashAttribute("error", "Error en el proceso: " + e.getMessage());
        }
        return "redirect:/sepa/generar";
    }
}