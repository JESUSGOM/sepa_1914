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
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Controller
@RequestMapping("/comunidades")
public class ComunidadController {

    private static final Logger log = LoggerFactory.getLogger(ComunidadController.class);

    private final ComunidadRepository comunidadRepository;
    private final UsuarioRepository usuarioRepository;
    private final VecinoRepository vecinoRepository;
    private final SepaService sepaService;
    private final PdfService pdfService;
    private final FileStorageService fileStorageService;
    private final ConfiguracionRutasRepository configuracionRutasRepository;
    private final ContabilidadService contabilidadService;
    private final FicheroGeneradoRepository ficheroRepository;
    // FIJATE AQUÍ: Añadida la pieza que faltaba para que compile
    private final LicenseService licenseService;

    public ComunidadController(ComunidadRepository comunidadRepository, UsuarioRepository usuarioRepository,
                               VecinoRepository vecinoRepository, SepaService sepaService,
                               PdfService pdfService, FileStorageService fileStorageService,
                               ConfiguracionRutasRepository configuracionRutasRepository,
                               ContabilidadService contabilidadService,
                               FicheroGeneradoRepository ficheroRepository,
                               LicenseService licenseService) { // Inyectado en constructor
        this.comunidadRepository = comunidadRepository;
        this.usuarioRepository = usuarioRepository;
        this.vecinoRepository = vecinoRepository;
        this.sepaService = sepaService;
        this.pdfService = pdfService;
        this.fileStorageService = fileStorageService;
        this.configuracionRutasRepository = configuracionRutasRepository;
        this.contabilidadService = contabilidadService;
        this.ficheroRepository = ficheroRepository;
        this.licenseService = licenseService; // Asignado aquí
    }

    @GetMapping("/lista")
    public String listarComunidades(Model model, Authentication auth) {
        Usuario actual = getUsuarioLogueado(auth);
        contabilidadService.sincronizarContabilidadExistente(actual.getId());
        List<Comunidad> misComunidades = comunidadRepository.findByAdministrador(actual);
        model.addAttribute("activePage", "comunidades");
        model.addAttribute("comunidades", misComunidades);
        return "comunidades-lista";
    }

    @GetMapping("/nueva")
    public String nuevaComunidad(Model model) {
        model.addAttribute("comunidad", new Comunidad());
        model.addAttribute("activePage", "comunidades");
        return "comunidades-formulario";
    }

    @GetMapping("/editar/{id}")
    public String editarComunidad(@PathVariable Long id, Model model, Authentication auth) {
        Usuario actual = getUsuarioLogueado(auth);
        Comunidad comunidad = comunidadRepository.findById(id)
                .filter(c -> c.getAdministrador().getId().equals(actual.getId()))
                .orElseThrow(() -> new RuntimeException("Comunidad no encontrada"));
        model.addAttribute("comunidad", comunidad);
        model.addAttribute("activePage", "comunidades");
        return "comunidades-formulario";
    }

    @GetMapping("/detalle/{id}")
    public String detalleComunidad(@PathVariable Long id, Model model, Authentication auth) {
        Usuario actual = getUsuarioLogueado(auth);
        Comunidad comunidad = comunidadRepository.findById(id)
                .filter(c -> c.getAdministrador().getId().equals(actual.getId()))
                .orElseThrow(() -> new RuntimeException("Sin permisos o comunidad no encontrada"));

        List<Vecino> vecinos = vecinoRepository.findByComunidad(comunidad);

        model.addAttribute("comunidad", comunidad);
        model.addAttribute("vecinos", vecinos);
        model.addAttribute("activePage", "comunidades");
        return "comunidades-detalle";
    }

    @GetMapping("/seleccionar/{id}")
    public String seleccionarComunidad(@PathVariable Long id, Authentication auth, HttpSession session, RedirectAttributes redirectAttributes) {
        Usuario actual = getUsuarioLogueado(auth);
        Comunidad comunidad = comunidadRepository.findById(id)
                .filter(c -> c.getAdministrador().getId().equals(actual.getId()))
                .orElseThrow(() -> new RuntimeException("Sin permisos"));
        session.setAttribute("comunidadSeleccionada", comunidad);
        redirectAttributes.addFlashAttribute("seleccionExito", "Comunidad '" + comunidad.getNombre() + "' activada.");
        return "redirect:/comunidades/lista";
    }

    @PostMapping("/guardar")
    public String guardarComunidad(@ModelAttribute Comunidad comunidad, Authentication auth, RedirectAttributes redirectAttributes) {
        return procesarPersistenciaComunidad(comunidad, auth, redirectAttributes, true);
    }

    @PostMapping("/actualizar/{id}")
    public String actualizarComunidad(@PathVariable Long id, @ModelAttribute Comunidad comunidad, Authentication auth, RedirectAttributes redirectAttributes) {
        comunidad.setId(id);
        return procesarPersistenciaComunidad(comunidad, auth, redirectAttributes, false);
    }

    /**
     * Lógica de persistencia REFACTORIZADA para evitar el borrado accidental de vecinos (Error 500 SQL 1451).
     */
    private String procesarPersistenciaComunidad(Comunidad comunidadForm, Authentication auth, RedirectAttributes redirectAttributes, boolean esNueva) {
        Usuario actual = getUsuarioLogueado(auth);
        Comunidad comunidadAPersistir;

        if (!esNueva) {
            // CARGAMOS LA COMUNIDAD REAL DE LA DB PARA NO PERDER LOS VECINOS
            comunidadAPersistir = comunidadRepository.findById(comunidadForm.getId())
                    .orElseThrow(() -> new RuntimeException("No existe la comunidad a actualizar"));

            // Sincronizamos solo los campos del formulario
            comunidadAPersistir.setNombre(comunidadForm.getNombre());
            comunidadAPersistir.setDireccion(comunidadForm.getDireccion());
            comunidadAPersistir.setPoblacion(comunidadForm.getPoblacion());
            comunidadAPersistir.setCodigoPostal(comunidadForm.getCodigoPostal());
            comunidadAPersistir.setIdentificadorAcreedor(comunidadForm.getIdentificadorAcreedor());
            comunidadAPersistir.setIban(comunidadForm.getIban());
            comunidadAPersistir.setBic(comunidadForm.getBic());
            comunidadAPersistir.setTipoReparto(comunidadForm.getTipoReparto());
        } else {
            comunidadAPersistir = comunidadForm;
        }

        comunidadAPersistir.setAdministrador(actual);

        // Validaciones
        String cif = comunidadAPersistir.getIdentificadorAcreedor();
        if (cif != null && !cif.isEmpty() && !validarCIF(cif)) {
            redirectAttributes.addFlashAttribute("error", "CIF/NIF inválido.");
            return esNueva ? "redirect:/comunidades/nueva" : "redirect:/comunidades/editar/" + comunidadForm.getId();
        }

        // Guardado seguro
        Comunidad guardada = comunidadRepository.save(comunidadAPersistir);

        if (esNueva) {
            log.info("Inicializando Plan Contable para nueva comunidad ID: {}", guardada.getId());
            contabilidadService.inicializarPlanContable(guardada);
        }

        redirectAttributes.addFlashAttribute("mensaje", "Comunidad actualizada correctamente sin afectar a los vecinos.");
        return "redirect:/comunidades/lista";
    }

    private boolean validarCIF(String cif) {
        if (cif == null || cif.length() != 9) return false;
        cif = cif.toUpperCase();
        String letras = "ABCDEFGHJNPQRSUVW";
        if (letras.indexOf(cif.substring(0, 1)) == -1) return false;
        try {
            String digitos = cif.substring(1, 8);
            int sumaPares = 0;
            for (int i = 1; i < digitos.length(); i += 2) sumaPares += Character.getNumericValue(digitos.charAt(i));
            int sumaImpares = 0;
            for (int i = 0; i < digitos.length(); i += 2) {
                int doble = Character.getNumericValue(digitos.charAt(i)) * 2;
                sumaImpares += (doble > 9) ? (doble - 9) : doble;
            }
            int numControl = (10 - ((sumaPares + sumaImpares) % 10)) % 10;
            char letraControl = "JABCDEFGHI".charAt(numControl);
            char ultimo = cif.charAt(8);
            return (ultimo == Character.forDigit(numControl, 10) || ultimo == letraControl);
        } catch (Exception e) { return false; }
    }

    @GetMapping("/descargar-c19/{id}")
    public ResponseEntity<byte[]> descargarC19(@PathVariable Long id,
                                               @RequestParam("fechaVencimiento") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaVencimiento,
                                               Authentication auth) {

        // 1. VALIDACIÓN DE LICENCIA (Bloqueo Funcional)
        if (!licenseService.validarLicencia()) {
            log.warn("Descarga bloqueada: El equipo [{}] no dispone de licencia activa para generar SEPA.", licenseService.getEquipoID());

            String mensajeInformativo = "SISTEMA NO ACTIVADO: La generación de ficheros de remesas Cuaderno 19 " +
                    "está restringida a la versión con licencia. \n\n" +
                    "ID de su equipo: " + licenseService.getEquipoID();

            return ResponseEntity.status(402) // HTTP 402: Payment Required
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(mensajeInformativo.getBytes(StandardCharsets.UTF_8));
        }

        // 2. SEGURIDAD: Verificación de acceso del usuario a la comunidad
        Usuario actual = getUsuarioLogueado(auth);
        Comunidad comunidad = comunidadRepository.findById(id)
                .filter(c -> c.getAdministrador().getId().equals(actual.getId()))
                .orElseThrow(() -> new RuntimeException("Sin permisos o comunidad no encontrada"));

        // 3. PROCESAMIENTO: Generación del contenido SEPA
        List<Vecino> vecinos = vecinoRepository.findByComunidad(comunidad);
        String contenido = sepaService.generarCuaderno19(comunidad, vecinos, fechaVencimiento);

        // 4. CÁLCULOS: Resumen para el registro histórico
        BigDecimal totalRemesa = vecinos.stream()
                .filter(Vecino::isDomiciliado)
                .map(Vecino::getImporteTotalConceptos)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 5. PERSISTENCIA: Guardar copia en el historial de remesas generadas
        FicheroGenerado historico = new FicheroGenerado();
        historico.setComunidad(comunidad);
        historico.setIdentificadorFichero("REM-" + System.currentTimeMillis());
        historico.setTotalImporte(totalRemesa);
        historico.setNumeroRecibos((int) vecinos.stream().filter(Vecino::isDomiciliado).count());
        historico.setNombreArchivo("REMESA_" + normalizarNombreFichero(comunidad.getNombre()) + "_" +
                fechaVencimiento.format(DateTimeFormatter.ofPattern("dd-MM-yyyy")) + ".c19");
        historico.setContenido(contenido);
        ficheroRepository.save(historico);

        // 6. RESPUESTA: Entrega del archivo al navegador
        byte[] data = contenido.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + historico.getNombreArchivo() + "\"")
                .body(data);
    }

    @GetMapping("/historial-remesas")
    public String verHistorial(Model model, Authentication auth) {
        Usuario actual = getUsuarioLogueado(auth);
        model.addAttribute("remesas", ficheroRepository.findByUsuarioId(actual.getId()));
        model.addAttribute("activePage", "generar");
        return "remesas-historial";
    }

    @GetMapping("/descargar-remesa-guardada/{id}")
    public ResponseEntity<byte[]> descargarGuardada(@PathVariable Long id, Authentication auth) {
        Usuario actual = getUsuarioLogueado(auth);
        FicheroGenerado f = ficheroRepository.findById(id)
                .filter(remesa -> remesa.getComunidad().getAdministrador().getId().equals(actual.getId()))
                .orElseThrow(() -> new RuntimeException("Fichero no encontrado"));

        byte[] data = f.getContenido().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + f.getNombreArchivo() + "\"")
                .body(data);
    }

    private String normalizarNombreFichero(String nombre) {
        if (nombre == null) return "COMUNIDAD";
        String temp = java.text.Normalizer.normalize(nombre, java.text.Normalizer.Form.NFD);
        return temp.replaceAll("[\\p{InCombiningDiacriticalMarks}]", "").replace("Ñ", "N").replace("ñ", "n").replaceAll("[^a-zA-Z0-9]", "_").toUpperCase();
    }

    private Usuario getUsuarioLogueado(Authentication auth) {
        return usuarioRepository.findByUsername(auth.getName()).orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }
}