package com.sepa1914.adminservice.controller;

import com.sepa1914.adminservice.model.Usuario;
import com.sepa1914.adminservice.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/registro")
public class UsuarioController {

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping
    public String mostrarRegistro() {
        return "registro";
    }

    @PostMapping
    public String registrarAdmin(@ModelAttribute Usuario usuario) {
        usuario.setPassword(passwordEncoder.encode(usuario.getPassword()));
        usuarioRepository.save(usuario);
        return "redirect:/login?registrado";
    }

    @GetMapping("/login")
    public String login() {
        return "login"; // Debe coincidir con login.html en templates
    }
}