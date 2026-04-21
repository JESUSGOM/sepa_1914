package com.sepa1914.adminservice.controller;

import com.sepa1914.adminservice.model.Acta;
import com.sepa1914.adminservice.model.Comunidad;
import com.sepa1914.adminservice.model.EstadoActa;
import com.sepa1914.adminservice.repository.ActaRepository;
import com.sepa1914.adminservice.repository.ComunidadRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.sepa1914.adminservice.service.PdfActaService;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/actas")
public class ActaController {

    private final ActaRepository actaRepository;
    private final ComunidadRepository comunidadRepository;
    private final PdfActaService pdfActaService;

    public ActaController(ActaRepository actaRepository,
                          ComunidadRepository comunidadRepository,
                          PdfActaService pdfActaService) {
        this.actaRepository = actaRepository;
        this.comunidadRepository = comunidadRepository;
        this.pdfActaService = pdfActaService;
    }

    /**
     * Lista las actas de una comunidad con validación de seguridad.
     */
    @GetMapping("/comunidad/{comunidadId}")
    public String listarActas(@PathVariable Long comunidadId, Model model, Authentication auth) {
        Comunidad comunidad = comunidadRepository.findById(comunidadId)
                .orElseThrow(() -> new RuntimeException("Comunidad no encontrada"));

        // SEGURIDAD: Solo el administrador de esta comunidad puede ver sus actas
        if (!comunidad.getAdministrador().getUsername().equals(auth.getName())) {
            return "redirect:/comunidades/lista?error=no_autorizado";
        }

        List<Acta> actas = actaRepository.findByComunidad(comunidad);
        model.addAttribute("actas", actas);
        model.addAttribute("comunidad", comunidad);
        model.addAttribute("activePage", "actas");
        return "actas-lista";
    }

    /**
     * Formulario para redactar un nuevo borrador de acta.
     */
    @GetMapping("/nueva")
    public String nuevaActa(@RequestParam Long comunidadId, Model model, Authentication auth) {
        Comunidad comunidad = comunidadRepository.findById(comunidadId).orElseThrow();

        if (!comunidad.getAdministrador().getUsername().equals(auth.getName())) {
            return "redirect:/comunidades/lista?error=no_autorizado";
        }

        Acta acta = new Acta();
        acta.setComunidad(comunidad);
        acta.setFechaReunion(LocalDate.now());
        acta.setEstado(EstadoActa.BORRADOR);

        model.addAttribute("acta", acta);
        model.addAttribute("comunidad", comunidad);
        model.addAttribute("activePage", "actas");
        return "actas-formulario";
    }

    /**
     * Guarda el acta. Gracias al @Convert en la entidad, el contenido se encripta solo.
     */
    @PostMapping("/guardar")
    public String guardarActa(@ModelAttribute Acta acta, @RequestParam Long comunidadId,
                              Authentication auth, RedirectAttributes ra) {
        Comunidad comunidad = comunidadRepository.findById(comunidadId).orElseThrow();

        if (!comunidad.getAdministrador().getUsername().equals(auth.getName())) {
            return "redirect:/comunidades/lista?error=no_autorizado";
        }

        acta.setComunidad(comunidad);
        // El estado inicial siempre es BORRADOR si es nueva
        if (acta.getId() == null) {
            acta.setEstado(EstadoActa.BORRADOR);
        }

        actaRepository.save(acta);

        ra.addFlashAttribute("mensaje", "Acta guardada correctamente y contenido blindado.");
        return "redirect:/actas/comunidad/" + comunidadId;
    }

    @GetMapping("/ver-pdf/{id}")
    public ResponseEntity<Resource> verPdf(@PathVariable Long id, Authentication auth) {
        try {
            Acta acta = actaRepository.findById(id).orElseThrow();

            // SEGURIDAD: Solo el admin de la comunidad
            if (!acta.getComunidad().getAdministrador().getUsername().equals(auth.getName())) {
                return ResponseEntity.status(403).build();
            }

            // Generamos el PDF al vuelo
            String path = pdfActaService.generarPdfActa(acta);
            Path filePath = Paths.get(path);
            Resource resource = new UrlResource(filePath.toUri());

            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header("Content-Disposition", "inline; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/editar/{id}")
    public String editarActa(@PathVariable Long id, Model model, Authentication auth) {
        Acta acta = actaRepository.findById(id).orElseThrow();

        // Validación de seguridad habitual
        if (!acta.getComunidad().getAdministrador().getUsername().equals(auth.getName())) {
            return "redirect:/comunidades/lista?error=no_autorizado";
        }

        model.addAttribute("acta", acta);
        model.addAttribute("comunidad", acta.getComunidad());
        model.addAttribute("activePage", "actas");
        return "actas-formulario";
    }

    @GetMapping("/finalizar/{id}")
    public String finalizarActa(@PathVariable Long id, Authentication auth, RedirectAttributes ra) {
        try {
            // 1. Localizar el acta
            Acta acta = actaRepository.findById(id).orElseThrow();

            // 2. SEGURIDAD: Solo el admin de la comunidad puede cerrar el acta
            if (!acta.getComunidad().getAdministrador().getUsername().equals(auth.getName())) {
                ra.addFlashAttribute("error", "No tiene permisos para finalizar actas de esta comunidad.");
                return "redirect:/comunidades/lista?error=no_autorizado";
            }

            // 3. LÓGICA DE ESTADO: Cambiamos a CERRADA y generamos token
            acta.setEstado(EstadoActa.CERRADA);
            acta.setTokenPresidente(java.util.UUID.randomUUID().toString());

            // Guardamos en BD para que el estado persista antes de firmar
            actaRepository.save(acta);

            // 4. SELLO FNMT: Invocamos al servicio para generar el PDF y firmarlo
            // Al haber cambiado el estado a CERRADA, el método generarPdfActa
            // detectará que ya no es un BORRADOR y aplicará el sello digital.
            pdfActaService.generarPdfActa(acta);

            ra.addFlashAttribute("mensaje", "Acta finalizada y sellada electrónicamente con certificado FNMT.");

        } catch (Exception e) {
            // Si algo falla en la firma (archivo .p12 no encontrado, pass incorrecta, etc.)
            e.printStackTrace();
            ra.addFlashAttribute("error", "Error crítico en el sellado digital: " + e.getMessage());
        }

        // Redirigimos de vuelta al histórico de la comunidad
        // Usamos una búsqueda segura para el ID de comunidad en la redirección
        Long comunidadId = actaRepository.findById(id)
                .map(a -> a.getComunidad().getId())
                .orElse(0L);

        return "redirect:/actas/comunidad/" + comunidadId;
    }
}