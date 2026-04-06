package com.sepa1914.adminservice.controller;

import com.sepa1914.adminservice.model.Usuario;
import com.sepa1914.adminservice.repository.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import java.security.Principal;

@Controller
public class HomeController {

    private final ComunidadRepository comunidadRepository;
    private final VecinoRepository vecinoRepository;
    private final ConceptoCobroRepository conceptoRepository;
    private final UsuarioRepository usuarioRepository;
    private final FicheroGeneradoRepository ficheroRepository;

    public HomeController(ComunidadRepository comunidadRepository,
                          VecinoRepository vecinoRepository,
                          ConceptoCobroRepository conceptoRepository,
                          UsuarioRepository usuarioRepository,
                          FicheroGeneradoRepository ficheroRepository) {
        this.comunidadRepository = comunidadRepository;
        this.vecinoRepository = vecinoRepository;
        this.conceptoRepository = conceptoRepository;
        this.usuarioRepository = usuarioRepository;
        this.ficheroRepository = ficheroRepository;
    }

    @GetMapping("/")
    @ResponseBody
    public String index() {
        return "Servidor SEPA 1914 Activo y Conectado a Dinahosting";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model, Principal principal) {
        String nombreUsuario = principal.getName();
        Usuario usuario = usuarioRepository.findByUsername(nombreUsuario).orElse(null);
        if (usuario == null) return "redirect:/login";

        model.addAttribute("activePage", "dashboard");
        model.addAttribute("totalComunidades", comunidadRepository.contarPorUsuario(usuario.getId()));
        model.addAttribute("totalVecinos", vecinoRepository.contarPorUsuario(usuario.getId()));
        model.addAttribute("totalConceptos", conceptoRepository.contarPorUsuario(usuario.getId()));
        model.addAttribute("totalRemesas", ficheroRepository.contarPorUsuario(usuario.getId()));

        return "dashboard";
    }
}