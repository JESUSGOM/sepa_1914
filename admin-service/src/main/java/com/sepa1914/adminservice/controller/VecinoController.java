package com.sepa1914.adminservice.controller;

import com.sepa1914.adminservice.model.Comunidad;
import com.sepa1914.adminservice.model.Vecino;
import com.sepa1914.adminservice.model.ConceptoCobro;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Controlador para la gestión integral de Vecinos y Propiedades.
 * Refactorizado para Java 21 y Spring Boot 3.4.
 * MANTIENE: Gestión de mandatos, persistencia contable y lógica de seguridad.
 * CORREGIDO: Eliminada anotación errónea @Service para compilación limpia.
 */
@Controller
@RequestMapping("/vecinos")
public class VecinoController {

    private static final Logger log = LoggerFactory.getLogger(VecinoController.class);

    private final VecinoRepository vecinoRepository;
    private final ComunidadRepository comunidadRepository;
    private final ConceptoCobroRepository conceptoRepo;
    private final MovimientoBancarioRepository movimientoRepository;
    private final BankService bankService;
    private final PdfService pdfService;
    private final StorageService storageService;
    private final ContabilidadService contabilidadService;

    @Value("${storage.location}")
    private String storageLocation;

    public VecinoController(VecinoRepository vecinoRepository,
                            ComunidadRepository comunidadRepository,
                            ConceptoCobroRepository conceptoRepo,
                            MovimientoBancarioRepository movimientoRepository,
                            BankService bankService,
                            PdfService pdfService,
                            StorageService storageService,
                            ContabilidadService contabilidadService) {
        this.vecinoRepository = vecinoRepository;
        this.comunidadRepository = comunidadRepository;
        this.conceptoRepo = conceptoRepo;
        this.movimientoRepository = movimientoRepository;
        this.bankService = bankService;
        this.pdfService = pdfService;
        this.storageService = storageService;
        this.contabilidadService = contabilidadService;
    }

    /**
     * Lista vecinos con paginación y ordenación.
     */
    @GetMapping("/lista")
    public String listarVecinos(
            @RequestParam(required = false) Long comunidadId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size,
            Model model,
            Authentication auth) {

        if (comunidadId == null) return "redirect:/comunidades/lista";

        Comunidad comunidad = comunidadRepository.findById(comunidadId)
                .orElseThrow(() -> new RuntimeException("Comunidad no encontrada ID: " + comunidadId));

        if (!comunidad.getAdministrador().getUsername().equals(auth.getName())) {
            log.warn("Acceso denegado: Usuario {} intentó acceder a Comunidad {}", auth.getName(), comunidadId);
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

    @GetMapping("/nuevo")
    public String formularioNuevoVecino(@RequestParam Long comunidadId, Model model) {
        Comunidad comunidad = comunidadRepository.findById(comunidadId).orElseThrow();
        Vecino nuevoVecino = new Vecino();
        nuevoVecino.setComunidad(comunidad);
        nuevoVecino.setDomiciliado(true);
        nuevoVecino.setActivo(true);

        model.addAttribute("vecino", nuevoVecino);
        model.addAttribute("comunidad", comunidad);
        return "vecinos-formulario";
    }

    @GetMapping("/editar/{id}")
    public String editarVecino(@PathVariable Long id, Model model, Authentication auth) {
        Vecino vecino = vecinoRepository.findById(id).orElseThrow();
        if (!vecino.getComunidad().getAdministrador().getUsername().equals(auth.getName())) {
            return "redirect:/comunidades/lista?error=no_autorizado";
        }
        model.addAttribute("vecino", vecino);
        model.addAttribute("comunidad", vecino.getComunidad());
        return "vecinos-formulario";
    }

    @PostMapping("/guardar")
    @Transactional
    public String guardarVecino(@ModelAttribute Vecino vecino, @RequestParam Long comunidadId, RedirectAttributes ra) {
        try {
            if (vecino.getId() != null) {
                Vecino existente = vecinoRepository.findById(vecino.getId()).orElseThrow();
                existente.setNombre(vecino.getNombre());
                existente.setNif(vecino.getNif());
                existente.setVivienda(vecino.getVivienda());
                existente.setEmail(vecino.getEmail());
                existente.setDomiciliado(vecino.isDomiciliado());
                existente.setActivo(vecino.isActivo());
                existente.setCoeficiente(vecino.getCoeficiente());
                existente.setIban(vecino.getIban());
                existente.setBic(vecino.getBic());

                if (existente.getIban() != null && !existente.getIban().isBlank() &&
                        (existente.getReferenciaMandato() == null || existente.getReferenciaMandato().isBlank())) {
                    existente.setReferenciaMandato(generarReferenciaSepa35(existente.getComunidad(), existente));
                }

                log.info("ACTUALIZANDO VECINO: {}, Domiciliado: {}", existente.getNombre(), existente.isDomiciliado());
                vecinoRepository.save(existente);
            } else {
                Comunidad comunidad = comunidadRepository.findById(comunidadId).orElseThrow();
                vecino.setComunidad(comunidad);
                Vecino guardado = vecinoRepository.save(vecino);

                if (guardado.getIban() != null && !guardado.getIban().isBlank()) {
                    guardado.setReferenciaMandato(generarReferenciaSepa35(comunidad, guardado));
                    vecinoRepository.save(guardado);
                }
            }
            ra.addFlashAttribute("mensaje", "Datos guardados correctamente.");
            return "redirect:/vecinos/lista?comunidadId=" + comunidadId;
        } catch (Exception e) {
            log.error("Error al guardar: ", e);
            return "redirect:/comunidades/lista";
        }
    }

    @GetMapping("/eliminar/{id}")
    public String eliminarVecino(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        Vecino vecino = vecinoRepository.findById(id).orElseThrow();
        if (!vecino.getComunidad().getAdministrador().getUsername().equals(auth.getName())) {
            return "redirect:/comunidades/lista?error=no_autorizado";
        }
        Long comunidadId = vecino.getComunidad().getId();
        vecinoRepository.delete(vecino);
        ra.addFlashAttribute("mensaje", "Propiedad eliminada del sistema.");
        return "redirect:/vecinos/lista?comunidadId=" + comunidadId;
    }

    /**
     * MÉTDO CORREGIDO: Mapeo para descargar el Mandato SEPA en PDF.
     * Ajustado a /mandato-pdf/{id} para coincidir con el botón del HTML.
     */
    @GetMapping("/mandato-pdf/{id}")
    public ResponseEntity<byte[]> descargarMandato(@PathVariable Long id, Authentication auth) {
        try {
            Vecino vecino = vecinoRepository.findById(id).orElseThrow();

            if (!vecino.getComunidad().getAdministrador().getUsername().equals(auth.getName())) {
                return ResponseEntity.status(403).build();
            }

            if (vecino.getIban() == null || vecino.getIban().isBlank()) {
                return ResponseEntity.badRequest().build();
            }

            byte[] pdf = pdfService.generarMandatoSepa(vecino.getComunidad(), vecino);
            String nombreFichero = "MANDATO_SEPA_" + vecino.getNombre().replaceAll("\\s+", "_").toUpperCase() + ".pdf";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombreFichero + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(pdf);
        } catch (Exception e) {
            log.error("Error en generación de mandato PDF: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/subir-mandato/{id}")
    public String subirMandatoFirmado(@PathVariable Long id, @RequestParam("fichero") MultipartFile fichero, RedirectAttributes ra) {
        try {
            Vecino vecino = vecinoRepository.findById(id).orElseThrow();
            String nombreAsignado = storageService.guardarArchivo(fichero, vecino.getNif());
            vecino.setRutaMandatoFirmado(nombreAsignado);
            vecinoRepository.save(vecino);
            ra.addFlashAttribute("mensaje", "Mandato físico vinculado con éxito.");
            return "redirect:/vecinos/lista?comunidadId=" + vecino.getComunidad().getId();
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Error al cargar el fichero.");
            return "redirect:/comunidades/lista";
        }
    }

    @GetMapping("/ver-mandato-firmado/{id}")
    public ResponseEntity<Resource> verMandatoFirmado(@PathVariable Long id, Authentication auth) {
        Vecino vecino = vecinoRepository.findById(id).orElseThrow();
        if (!vecino.getComunidad().getAdministrador().getUsername().equals(auth.getName()) || vecino.getRutaMandatoFirmado() == null) {
            return ResponseEntity.status(403).build();
        }
        try {
            Path rutaArchivo = Path.of(storageLocation).resolve(vecino.getRutaMandatoFirmado());
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
            return ResponseEntity.internalServerError().build();
        }
    }

    @Transactional
    @GetMapping("/conceptos/eliminar/{id}")
    public String eliminarConcepto(@PathVariable("id") Long id, RedirectAttributes ra) {
        try {
            ConceptoCobro concepto = conceptoRepo.findById(id).orElseThrow();
            Long vecinoId = concepto.getVecino().getId();
            if (concepto.getMovimientoBancarioId() != null) {
                movimientoRepository.findById(concepto.getMovimientoBancarioId()).ifPresent(mb -> {
                    mb.setConciliado(false);
                    if (mb.getConcepto().contains(" [CARGADO A")) {
                        mb.setConcepto(mb.getConcepto().split(" \\[CARGADO A")[0]);
                    }
                    movimientoRepository.save(mb);
                });
            }
            conceptoRepo.delete(concepto);
            ra.addFlashAttribute("mensaje", "Concepto eliminado correctamente.");
            return "redirect:/conceptos/vecino/" + vecinoId;
        } catch (Exception e) {
            return "redirect:/comunidades/lista";
        }
    }

    private String generarReferenciaSepa35(Comunidad comunidad, Vecino vecino) {
        String cifC = comunidad.getIdentificadorAcreedor().replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        String nifV = vecino.getNif().replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        String idProp = String.valueOf(vecino.getId());
        int huecoParaCeros = 35 - (cifC.length() + nifV.length() + idProp.length());
        if (huecoParaCeros < 0) huecoParaCeros = 0;
        String ceros = "0".repeat(huecoParaCeros);
        String resultado = cifC + ceros + idProp + nifV;
        return resultado.length() > 35 ? resultado.substring(0, 35) : resultado;
    }
}