package com.sepa1914.adminservice.controller;

import com.sepa1914.adminservice.model.Comunidad;
import com.sepa1914.adminservice.model.Vecino;
import com.sepa1914.adminservice.model.ConceptoCobro;
import com.sepa1914.adminservice.repository.*;
import com.sepa1914.adminservice.service.BankService;
import com.sepa1914.adminservice.service.ContabilidadService;
import com.sepa1914.adminservice.service.PdfService;
import com.sepa1914.adminservice.service.StorageService;
import com.sepa1914.adminservice.util.EncryptionUtil;
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
    private final CuentaContableRepository cuentaContableRepository;
    private final ConceptoCobroRepository conceptoCobroRepo;

    @Value("${storage.location}")
    private String storageLocation;

    public VecinoController(ComunidadRepository comunidadRepository,
                            VecinoRepository vecinoRepository,
                            ConceptoCobroRepository conceptoCobroRepo,
                            MovimientoBancarioRepository movimientoRepository,
                            ConceptoCobroRepository conceptoRepo,
                            BankService bankService,
                            ContabilidadService contabilidadService,
                            PdfService pdfService,
                            StorageService storageService,
                            CuentaContableRepository cuentaContableRepository) {
        this.comunidadRepository = comunidadRepository;
        this.vecinoRepository = vecinoRepository;
        this.conceptoRepo = conceptoRepo;
        this.movimientoRepository = movimientoRepository;
        this.conceptoCobroRepo = conceptoCobroRepo;
        this.bankService = bankService;
        this.contabilidadService = contabilidadService;
        this.pdfService = pdfService;
        this.storageService = storageService;
        this.cuentaContableRepository = cuentaContableRepository;
    }

    /**
     * Lista vecinos con BÚSQUEDA, PAGINACIÓN y ORDENACIÓN.
     */
    @GetMapping("/lista")
    public String listarVecinos(
            @RequestParam(required = false) Long comunidadId,
            @RequestParam(required = false) String buscar,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "vivienda") String sortField,
            @RequestParam(defaultValue = "asc") String sortDir,
            Model model,
            Authentication auth,
            RedirectAttributes ra) {

        if (comunidadId == null) return "redirect:/comunidades/lista";

        Comunidad comunidad = comunidadRepository.findById(comunidadId).orElse(null);
        if (comunidad == null) {
            ra.addFlashAttribute("error", "La comunidad con ID " + comunidadId + " no existe.");
            return "redirect:/comunidades/lista";
        }

        if (!comunidad.getAdministrador().getUsername().equals(auth.getName())) {
            log.warn("Acceso denegado: Usuario {} intentó acceder a Comunidad {}", auth.getName(), comunidadId);
            return "redirect:/comunidades/lista?error=no_autorizado";
        }

        Sort sort = sortDir.equalsIgnoreCase(Sort.Direction.ASC.name())
                ? Sort.by(sortField).ascending()
                : Sort.by(sortField).descending();

        Pageable pageable = PageRequest.of(page, size, sort);
        Page<Vecino> vecinosPage;

        if (buscar != null && !buscar.trim().isEmpty()) {
            vecinosPage = vecinoRepository.buscarVecinos(comunidadId, buscar, pageable);
        } else {
            vecinosPage = vecinoRepository.findByComunidad(comunidad, pageable);
        }

        model.addAttribute("activePage", "vecinos");
        model.addAttribute("comunidad", comunidad);
        model.addAttribute("vecinos", vecinosPage.getContent());
        model.addAttribute("pagina", vecinosPage);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", vecinosPage.getTotalPages());
        model.addAttribute("totalItems", vecinosPage.getTotalElements());
        model.addAttribute("sortField", sortField);
        model.addAttribute("sortDir", sortDir);
        model.addAttribute("reverseSortDir", sortDir.equals("asc") ? "desc" : "asc");
        model.addAttribute("searchTerm", buscar);

        return "vecinos-lista";
    }

    @GetMapping("/nuevo")
    public String nuevoVecino(@RequestParam Long comunidadId, Model model) {
        Comunidad com = comunidadRepository.findById(comunidadId).orElseThrow();
        Vecino vecino = new Vecino();
        vecino.setComunidad(com);
        vecino.setActivo(true);
        vecino.setEnvioDigital(true);
        vecino.setReferenciaMandato("GTI-" + System.currentTimeMillis() / 1000);
        model.addAttribute("vecino", vecino);
        model.addAttribute("comunidad", com);
        model.addAttribute("todasLasCuentas", cuentaContableRepository.findByComunidadId(comunidadId));
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
        model.addAttribute("todasLasCuentas", cuentaContableRepository.findByComunidadId(vecino.getComunidad().getId()));
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
                existente.setEnvioDigital(vecino.isEnvioDigital());
                existente.setActivo(vecino.isActivo());
                existente.setCoeficiente(vecino.getCoeficiente());
                existente.setIban(vecino.getIban());
                existente.setBic(vecino.getBic());
                existente.setTelefono_1(vecino.getTelefono_1());
                existente.setTelefono_2(vecino.getTelefono_2());
                existente.setTelefono_3(vecino.getTelefono_3());
                existente.setNotas(vecino.getNotas());

                if (existente.getIban() != null && !existente.getIban().isBlank() &&
                        (existente.getReferenciaMandato() == null || existente.getReferenciaMandato().isBlank())) {
                    existente.setReferenciaMandato(generarReferenciaSepa35(existente.getComunidad(), existente));
                }
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
        try {
            Vecino vecino = vecinoRepository.findById(id).orElseThrow();
            if (!vecino.getComunidad().getAdministrador().getUsername().equals(auth.getName())) {
                return "redirect:/comunidades/lista?error=no_autorizado";
            }
            Long comunidadId = vecino.getComunidad().getId();
            vecinoRepository.delete(vecino);
            ra.addFlashAttribute("mensaje", "Propiedad eliminada del sistema.");
            return "redirect:/vecinos/lista?comunidadId=" + comunidadId;
        } catch (Exception e) {
            log.error("Violación de integridad al borrar: {}", e.getMessage());
            ra.addFlashAttribute("error", "No se puede eliminar: Esta propiedad tiene recibos o conceptos vinculados. Primero debe borrar sus recibos.");
            return "redirect:/vecinos/lista?comunidadId=" + id;
        }
    }

    @GetMapping("/mandato-pdf/{id}")
    public String descargarMandato(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        try {
            Vecino vecino = vecinoRepository.findById(id).orElseThrow();
            pdfService.generarMandatoSepaLocal(vecino.getComunidad(), vecino);
            ra.addFlashAttribute("mensaje", "Mandato guardado en disco local.");
            return "redirect:/vecinos/lista?comunidadId=" + vecino.getComunidad().getId();
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Error al procesar el PDF.");
            return "redirect:/vecinos/lista?comunidadId=" + id;
        }
    }

    @PostMapping("/subir-mandato/{id}")
    public String subirMandatoFirmado(@PathVariable Long id, @RequestParam("fichero") MultipartFile fichero, RedirectAttributes ra) {
        try {
            Vecino vecino = vecinoRepository.findById(id).orElseThrow();
            String nombreAsignado = storageService.guardarArchivo(fichero, vecino.getNif());
            vecino.setRutaMandatoFirmado(nombreAsignado);
            vecinoRepository.save(vecino);
            ra.addFlashAttribute("mensaje", "Mandato físico vinculado.");
            return "redirect:/vecinos/lista?comunidadId=" + vecino.getComunidad().getId();
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Error al cargar el fichero.");
            return "redirect:/comunidades/lista";
        }
    }

    @GetMapping("/ver-mandato-firmado/{id}")
    public ResponseEntity<Resource> verMandatoFirmado(@PathVariable Long id, Authentication auth) {
        Vecino vecino = vecinoRepository.findById(id).orElseThrow();
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
        } catch (Exception e) { return ResponseEntity.internalServerError().build(); }
    }

    @Transactional
    @GetMapping("/conceptos/eliminar/{id}")
    public String eliminarConcepto(@PathVariable("id") Long id, RedirectAttributes ra) {
        try {
            ConceptoCobro concepto = conceptoRepo.findById(id).orElseThrow();
            Long vecinoId = concepto.getVecino().getId();
            conceptoRepo.delete(concepto);
            ra.addFlashAttribute("mensaje", "Concepto eliminado.");
            return "redirect:/conceptos/vecino/" + vecinoId;
        } catch (Exception e) { return "redirect:/comunidades/lista"; }
    }

    private String generarReferenciaSepa35(Comunidad comunidad, Vecino vecino) {
        String cifC = comunidad.getIdentificadorAcreedor().replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        String nifV = vecino.getNif().replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        String idProp = String.valueOf(vecino.getId());
        int huecoParaCeros = 35 - (cifC.length() + nifV.length() + idProp.length());
        if (huecoParaCeros < 0) huecoParaCeros = 0;
        String ceros = "0".repeat(huecoParaCeros);
        return (cifC + ceros + idProp + nifV);
    }

    // --- MÉTODOS DE MIGRACIÓN (MANTENIDOS AL 100%) ---

    // @GetMapping("/migrar-ahora-mismo")
    // @ResponseBody
    // public String encriptarVecinosExistentes() {
    //     try {
    //         List<Vecino> lista = vecinoRepository.findAll();
    //         int procesados = 0;
    //         for (Vecino v : lista) {
    //             if (v.getNif() != null && !v.getNif().contains("==") && v.getNif().length() < 20) {
    //                 if (v.getNif() != null) v.setNif(EncryptionUtil.encrypt(v.getNif()));
    //                 if (v.getEmail() != null) v.setEmail(EncryptionUtil.encrypt(v.getEmail()));
    //                 if (v.getIban() != null) v.setIban(EncryptionUtil.encrypt(v.getIban()));
    //                 if (v.getBic() != null) v.setBic(EncryptionUtil.encrypt(v.getBic()));
    //                 vecinoRepository.save(v);
    //                 procesados++;
    //             }
    //         }
    //         return "Éxito: Se han encriptado " + procesados + " vecinos.";
    //     } catch (Exception e) { return "Error: " + e.getMessage(); }
    // }

    // @GetMapping("/migrar-comunidades-ahora")
    // @ResponseBody
    // public String migrarComunidadesSeguras() {
    //     try {
    //         List<Comunidad> comunidades = comunidadRepository.findAll();
    //         int contador = 0;
    //         for (Comunidad c : comunidades) {
    //             if (c.getIban() != null && c.getIban().length() < 30) {
    //                 if (c.getIdentificadorAcreedor() != null)
    //                     c.setIdentificadorAcreedor(EncryptionUtil.encrypt(c.getIdentificadorAcreedor()));
    //                 if (c.getIban() != null)
    //                     c.setIban(EncryptionUtil.encrypt(c.getIban()));
    //                 comunidadRepository.save(c);
    //                 contador++;
    //             }
    //         }
    //         return "ÉXITO: Se han blindado " + contador + " comunidades.";
    //     } catch (Exception e) { return "ERROR: " + e.getMessage(); }
    // }
}