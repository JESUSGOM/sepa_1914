package com.sepa1914.adminservice.controller;

import com.sepa1914.adminservice.model.ConfiguracionRutas;
import com.sepa1914.adminservice.model.Usuario;
import com.sepa1914.adminservice.repository.ConfiguracionRutasRepository;
import com.sepa1914.adminservice.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/rutas") // Coincide con la primera parte de la URL del menú
public class ConfiguracionController {

    @Autowired
    private ConfiguracionRutasRepository configRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @GetMapping("/archivos") // Coincide con la segunda parte: /rutas/archivos
    public String mostrarConfiguracion(Model model, Authentication auth) {
        Usuario actual = getUsuarioLogueado(auth);

        ConfiguracionRutas config = configRepository.findByAdministrador(actual)
                .orElseGet(() -> {
                    ConfiguracionRutas nueva = new ConfiguracionRutas();
                    nueva.setAdministrador(actual);
                    nueva.setRutaC19("");
                    nueva.setRutaPdf("");
                    return nueva;
                });

        model.addAttribute("config", config);
        model.addAttribute("activePage", "rutas");
        return "configuracion/rutas-form";
    }

    @PostMapping("/guardar") // URL de guardado: /rutas/guardar
    public String guardarConfiguracion(ConfiguracionRutas config, Authentication auth, RedirectAttributes ra) {
        Usuario actual = getUsuarioLogueado(auth);

        ConfiguracionRutas existente = configRepository.findByAdministrador(actual)
                .orElse(new ConfiguracionRutas());

        existente.setRutaC19(config.getRutaC19());
        existente.setRutaPdf(config.getRutaPdf());
        existente.setAdministrador(actual);

        configRepository.save(existente);

        ra.addFlashAttribute("mensaje", "Configuración de rutas actualizada correctamente.");
        return "redirect:/rutas/archivos";
    }

    private Usuario getUsuarioLogueado(Authentication auth) {
        if (auth == null) throw new RuntimeException("No hay una sesión activa");
        return usuarioRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }
}