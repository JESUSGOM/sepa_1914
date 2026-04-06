package com.sepa1914.adminservice.controller;

import com.sepa1914.adminservice.model.Comunidad;
import com.sepa1914.adminservice.model.Vecino;
import com.sepa1914.adminservice.model.ConceptoCobro;
import com.sepa1914.adminservice.model.MovimientoBancario;
import com.sepa1914.adminservice.repository.ComunidadRepository;
import com.sepa1914.adminservice.repository.VecinoRepository;
import com.sepa1914.adminservice.repository.ConceptoCobroRepository;
import com.sepa1914.adminservice.repository.MovimientoBancarioRepository;
import com.sepa1914.adminservice.service.BankService;
import com.sepa1914.adminservice.service.ContabilidadService;
import com.sepa1914.adminservice.service.PdfService;
import com.sepa1914.adminservice.service.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.transaction.annotation.Transactional;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.List;

// IMPORTS PARA PAGINACIÓN Y ORDENACIÓN
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Controlador para la gestión integral de Vecinos/Propiedades.
 * Refactorizado para soportar todos los campos LOPD y gestión de coeficientes.
 */
@Controller
@RequestMapping("/vecinos")
public class VecinoController {

    private static final Logger log = LoggerFactory.getLogger(VecinoController.class);

    @Autowired
    private VecinoRepository vecinoRepository;

    @Autowired
    private ComunidadRepository comunidadRepository;

    @Autowired
    private ConceptoCobroRepository conceptoRepo;

    @Autowired
    private MovimientoBancarioRepository movimientoRepository;

    @Autowired
    private BankService bankService;

    @Autowired
    private PdfService pdfService;

    @Autowired
    private StorageService storageService;

    @Autowired
    private ContabilidadService contabilidadService;

    @Value("${storage.location}")
    private String storageLocation;

    /**
     * Lista vecinos con paginación y ordenación por nombre.
     */
    @GetMapping("/lista")
    public String listarVecinos(
            @RequestParam(required = false) Long comunidadId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            Model model,
            Authentication auth) {

        if (comunidadId == null) {
            return "redirect:/comunidades/lista";
        }

        Comunidad comunidad = comunidadRepository.findById(comunidadId)
                .orElseThrow(() -> new RuntimeException("Comunidad no encontrada"));

        // Seguridad: Verificar administrador
        if (!comunidad.getAdministrador().getUsername().equals(auth.getName())) {
            log.warn("Acceso no autorizado de {} a comunidad {}", auth.getName(), comunidadId);
            return "redirect:/comunidades/lista?error=no_autorizado";
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by("nombre").ascending());
        Page<Vecino> vecinosPage = vecinoRepository.findByComunidad(comunidad, pageable);

        model.addAttribute("activePage", "vecinos");
        model.addAttribute("comunidad", comunidad);
        model.addAttribute("vecinos", vecinosPage.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", vecinosPage.getTotalPages());
        model.addAttribute("totalItems", vecinosPage.getTotalElements());

        return "vecinos-lista";
    }

    /**
     * Prepara el formulario para un nuevo vecino.
     */
    @GetMapping("/nuevo")
    public String formularioNuevoVecino(@RequestParam Long comunidadId, Model model) {
        Comunidad comunidad = comunidadRepository.findById(comunidadId)
                .orElseThrow(() -> new RuntimeException("Comunidad no encontrada"));

        Vecino nuevoVecino = new Vecino();
        nuevoVecino.setComunidad(comunidad);
        nuevoVecino.setDomiciliado(true);
        nuevoVecino.setActivo(true); // Funcionalidad añadida: estado inicial activo

        model.addAttribute("activePage", "vecinos");
        model.addAttribute("vecino", nuevoVecino);
        model.addAttribute("comunidad", comunidad);
        return "vecinos-formulario";
    }

    /**
     * Carga datos para editar un vecino existente.
     */
    @GetMapping("/editar/{id}")
    public String editarVecino(@PathVariable Long id, Model model, Authentication auth) {
        Vecino vecino = vecinoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Vecino no encontrado"));

        if (!vecino.getComunidad().getAdministrador().getUsername().equals(auth.getName())) {
            return "redirect:/comunidades/lista?error=no_autorizado";
        }

        model.addAttribute("activePage", "vecinos");
        model.addAttribute("vecino", vecino);
        model.addAttribute("comunidad", vecino.getComunidad());
        return "vecinos-formulario";
    }

    /**
     * Guarda o actualiza la propiedad (Soporta nuevos campos: teléfonos, email, coeficiente).
     */
    @PostMapping("/guardar")
    public String guardarVecino(@ModelAttribute Vecino vecino, @RequestParam Long comunidadId, RedirectAttributes ra) {
        try {
            Comunidad comunidad = comunidadRepository.findById(comunidadId)
                    .orElseThrow(() -> new RuntimeException("Comunidad no encontrada"));

            vecino.setComunidad(comunidad);

            // PERSISTENCIA DEL VECINO
            Vecino guardado = vecinoRepository.save(vecino);

            // AUTOMATIZACIÓN CONTABLE: Crear cuenta 430XXXXX si no existe
            if (guardado.getCuentaContable() == null || guardado.getCuentaContable().isEmpty()) {
                String nuevaCuenta = contabilidadService.crearCuentaParaVecino(guardado);
                guardado.setCuentaContable(nuevaCuenta);
                vecinoRepository.save(guardado);
            }

            log.info("Guardado exitoso de vecino: {}", guardado.getNombre());
            ra.addFlashAttribute("mensaje", "Propiedad guardada correctamente.");
            return "redirect:/vecinos/lista?comunidadId=" + comunidadId;

        } catch (Exception e) {
            log.error("Error crítico al guardar vecino", e);
            ra.addFlashAttribute("error", "Error al procesar los datos de la propiedad.");
            return "redirect:/comunidades/lista";
        }
    }

    @GetMapping("/eliminar/{id}")
    public String eliminarVecino(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        Vecino vecino = vecinoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Vecino no encontrado"));

        if (!vecino.getComunidad().getAdministrador().getUsername().equals(auth.getName())) {
            return "redirect:/comunidades/lista?error=no_autorizado";
        }

        Long comunidadId = vecino.getComunidad().getId();
        vecinoRepository.delete(vecino);
        ra.addFlashAttribute("mensaje", "Propiedad eliminada con éxito.");

        return "redirect:/vecinos/lista?comunidadId=" + comunidadId;
    }

    // --- GENERACIÓN DE MANDATOS PDF (Mantenido 100%) ---
    @GetMapping("/descargar-mandato/{id}")
    public ResponseEntity<byte[]> descargarMandato(@PathVariable Long id, Authentication auth) {
        try {
            Vecino vecino = vecinoRepository.findById(id).orElseThrow();
            if (!vecino.getComunidad().getAdministrador().getUsername().equals(auth.getName())) {
                return ResponseEntity.status(403).build();
            }

            if (vecino.getIban() == null || vecino.getIban().isEmpty()) {
                return ResponseEntity.badRequest().build();
            }

            byte[] pdf = pdfService.generarMandatoSepa(vecino.getComunidad(), vecino);
            String nombreFichero = "MANDATO_" + vecino.getNombre().replaceAll("\\s+", "_").toUpperCase() + ".pdf";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombreFichero + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf);
        } catch (Exception e) {
            log.error("Error al generar PDF de mandato", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    // --- SUBIDA DE MANDATOS FIRMADOS (Mantenido 100%) ---
    @PostMapping("/subir-mandato/{id}")
    public String subirMandatoFirmado(@PathVariable Long id, @RequestParam("fichero") MultipartFile fichero, RedirectAttributes ra) {
        try {
            Vecino vecino = vecinoRepository.findById(id).orElseThrow();
            String nombreAsignado = storageService.guardarArchivo(fichero, vecino.getNif());

            vecino.setRutaMandatoFirmado(nombreAsignado);
            vecinoRepository.save(vecino);

            ra.addFlashAttribute("mensaje", "Documento de mandato guardado en servidor.");
            return "redirect:/vecinos/lista?comunidadId=" + vecino.getComunidad().getId();
        } catch (Exception e) {
            log.error("Error al subir archivo de mandato", e);
            ra.addFlashAttribute("error", "No se pudo subir el archivo.");
            return "redirect:/comunidades/lista";
        }
    }

    // --- VISUALIZACIÓN DE ARCHIVOS ALMACENADOS (Mantenido 100%) ---
    @GetMapping("/ver-mandato-firmado/{id}")
    public ResponseEntity<Resource> verMandatoFirmado(@PathVariable Long id, Authentication auth) {
        Vecino vecino = vecinoRepository.findById(id).orElseThrow();

        if (!vecino.getComunidad().getAdministrador().getUsername().equals(auth.getName()) || vecino.getRutaMandatoFirmado() == null) {
            return ResponseEntity.status(403).build();
        }

        try {
            Path rutaArchivo = Paths.get(storageLocation).resolve(vecino.getRutaMandatoFirmado());
            Resource recurso = new UrlResource(rutaArchivo.toUri());

            if (recurso.exists() || recurso.isReadable()) {
                String contentType = Files.probeContentType(rutaArchivo);
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(contentType != null ? contentType : "application/pdf"))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + recurso.getFilename() + "\"")
                        .body(recurso);
            }
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error al leer archivo del disco", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Elimina un concepto de cobro y revierte su estado en el extracto bancario.
     */
    @Transactional
    @GetMapping("/conceptos/eliminar/{id}")
    public String eliminarConcepto(@PathVariable("id") Long id, RedirectAttributes ra) {
        try {
            ConceptoCobro concepto = conceptoRepo.findById(id)
                    .orElseThrow(() -> new RuntimeException("Concepto no encontrado"));

            Long vecinoId = concepto.getVecino().getId();

            if (concepto.getMovimientoBancarioId() != null) {
                movimientoRepository.findById(concepto.getMovimientoBancarioId()).ifPresent(mb -> {
                    mb.setConciliado(false);
                    String conceptoLimpio = mb.getConcepto();
                    if (conceptoLimpio.contains(" [CARGADO A")) {
                        mb.setConcepto(conceptoLimpio.split(" \\[CARGADO A")[0]);
                    }
                    movimientoRepository.save(mb);
                });
            }

            conceptoRepo.delete(concepto);
            ra.addFlashAttribute("mensaje", "Concepto eliminado y banco liberado.");
            return "redirect:/conceptos/vecino/" + vecinoId;

        } catch (Exception e) {
            log.error("Error al eliminar concepto", e);
            ra.addFlashAttribute("error", "Fallo al eliminar concepto.");
            return "redirect:/comunidades/lista";
        }
    }
}