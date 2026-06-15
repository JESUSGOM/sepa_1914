package com.sepa1914.adminservice.controller;

import com.sepa1914.adminservice.model.Administrador;
import com.sepa1914.adminservice.model.CuentaPresentador;
import com.sepa1914.adminservice.model.Usuario;
import com.sepa1914.adminservice.repository.UsuarioRepository;
import com.sepa1914.adminservice.service.CuentaPresentadorService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/cuentas-presentador")
public class CuentaPresentadorController {

    private final CuentaPresentadorService cuentaPresentadorService;
    private final UsuarioRepository usuarioRepository;

    public CuentaPresentadorController(CuentaPresentadorService cuentaPresentadorService,
                                       UsuarioRepository usuarioRepository) {
        this.cuentaPresentadorService = cuentaPresentadorService;
        this.usuarioRepository = usuarioRepository;
    }

    @GetMapping
    public String listar(Model model, Authentication auth) {
        Usuario usuario = getUsuarioLogueado(auth);
        Administrador administrador = obtenerAdministradorDelUsuario(usuario);

        model.addAttribute("cuentas",
                cuentaPresentadorService.obtenerActivasAdministrador(administrador.getId()));

        model.addAttribute("cuentaForm", new CuentaPresentador());
        model.addAttribute("activePage", "cuentas-presentador");

        return "cuentas-presentador-lista";
    }

    @PostMapping("/guardar")
    public String guardar(@ModelAttribute CuentaPresentador cuenta,
                          Authentication auth,
                          RedirectAttributes ra) {
        Usuario usuario = getUsuarioLogueado(auth);
        Administrador administrador = obtenerAdministradorDelUsuario(usuario);

        cuenta.setAdministrador(administrador);

        if (cuenta.getIban() != null) {
            cuenta.setIban(cuenta.getIban().replaceAll("\\s+", ""));
        }

        if (cuenta.getIdentificadorPresentador() != null) {
            cuenta.setIdentificadorPresentador(
                    cuenta.getIdentificadorPresentador().replaceAll("\\s+", "").toUpperCase()
            );
        }

        if (cuenta.getNifCif() != null) {
            cuenta.setNifCif(cuenta.getNifCif().replaceAll("\\s+", "").toUpperCase());
        }

        if (cuenta.getSufijo() != null) {
            cuenta.setSufijo(cuenta.getSufijo().replaceAll("\\s+", ""));
        }

        cuentaPresentadorService.guardar(cuenta);

        ra.addFlashAttribute("mensaje", "Cuenta presentadora guardada correctamente.");
        return "redirect:/cuentas-presentador";
    }

    @GetMapping("/eliminar/{id}")
    public String eliminar(@PathVariable Long id,
                           Authentication auth,
                           RedirectAttributes ra) {
        Usuario usuario = getUsuarioLogueado(auth);
        Administrador administrador = obtenerAdministradorDelUsuario(usuario);

        CuentaPresentador cuenta = cuentaPresentadorService.obtenerPorId(id);

        if (cuenta.getAdministrador() == null ||
                !cuenta.getAdministrador().getId().equals(administrador.getId())) {
            throw new RuntimeException("No tiene permisos para eliminar esta cuenta presentadora.");
        }

        cuentaPresentadorService.eliminar(id);

        ra.addFlashAttribute("mensaje", "Cuenta presentadora eliminada.");
        return "redirect:/cuentas-presentador";
    }

    private Usuario getUsuarioLogueado(Authentication auth) {
        return usuarioRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }

    private Administrador obtenerAdministradorDelUsuario(Usuario usuario) {
        Administrador administrador = usuario.getAdministrador();

        if (administrador == null) {
            throw new RuntimeException("El usuario no tiene administrador asociado.");
        }

        if (administrador.getId() == null) {
            throw new RuntimeException("El administrador asociado al usuario no tiene ID.");
        }

        return administrador;
    }
}