package com.sepa1914.adminservice.controller;

import com.sepa1914.adminservice.model.Comunidad;
import com.sepa1914.adminservice.model.Usuario;
import com.sepa1914.adminservice.model.SepaUtils;
import com.sepa1914.adminservice.repository.ComunidadRepository;
import com.sepa1914.adminservice.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.ui.Model;

import java.util.List;

@Controller
@RequestMapping("/comunidades")
public class ComunidadController {

    @Autowired
    private ComunidadRepository comunidadRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    /**
     * Muestra la lista de comunidades filtradas por el administrador logueado.
     */
    @GetMapping("/lista")
    public String listarComunidades(Model model, Authentication auth) {
        Usuario actual = getUsuarioLogueado(auth);

        // Obtenemos solo las comunidades que pertenecen a este administrador
        List<Comunidad> misComunidades = comunidadRepository.findByAdministrador(actual);

        model.addAttribute("comunidades", misComunidades);
        return "comunidades-lista";
    }

    /**
     * Muestra el formulario para dar de alta una nueva comunidad.
     */
    @GetMapping("/nueva")
    public String mostrarFormularioNuevaComunidad(Model model) {
        model.addAttribute("comunidad", new Comunidad());
        return "comunidades-formulario"; // Asegúrate de tener este HTML
    }

    /**
     * Procesa el guardado de la comunidad vinculándola al administrador
     * y calculando el identificador SEPA AT-02.
     */
    @PostMapping("/guardar")
    public String guardarComunidad(@ModelAttribute Comunidad comunidad, Authentication auth) {
        Usuario actual = getUsuarioLogueado(auth);

        // 1. Vinculamos la comunidad al administrador que sesión abierta
        comunidad.setAdministrador(actual);

        // 2. Lógica SEPA: Convertimos el NIF en Identificador de Acreedor AT-02
        // Si el usuario introduce "G12345678", se convertirá en "ESXX000G12345678"
        String nifBase = comunidad.getIdentificadorAcreedor();
        if (nifBase != null && !nifBase.isEmpty()) {
            String idAt02 = SepaUtils.calcularAT02(nifBase, "000");
            comunidad.setIdentificadorAcreedor(idAt02);
        }

        comunidadRepository.save(comunidad);
        return "redirect:/comunidades/lista?exito=true";
    }

    /**
     * Permite ver los detalles de una comunidad específica (antes de entrar a vecinos).
     */
    @GetMapping("/detalle/{id}")
    public String verDetalle(@PathVariable Long id, Model model, Authentication auth) {
        Usuario actual = getUsuarioLogueado(auth);

        Comunidad comunidad = comunidadRepository.findById(id)
                .filter(c -> c.getAdministrador().getId().equals(actual.getId()))
                .orElseThrow(() -> new RuntimeException("Comunidad no encontrada o no tiene permisos"));

        model.addAttribute("comunidad", comunidad);
        return "comunidades-detalle";
    }

    /**
     * Método privado de utilidad para evitar repetir código de búsqueda de usuario.
     */
    private Usuario getUsuarioLogueado(Authentication auth) {
        if (auth == null) throw new RuntimeException("No hay sesión activa");
        return usuarioRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado en la base de datos"));
    }
}