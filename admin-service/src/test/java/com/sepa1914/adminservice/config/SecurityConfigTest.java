package com.sepa1914.adminservice.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("1. Zona Pública Web: Permite acceso anónimo a la página de login")
    void accesoPublico_PaginaLogin() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("2. Zona Pública Filtro: Las rutas de recursos estáticos saltan la seguridad (PermitAll)")
    void accesoPublico_RecursosEstaticos() throws Exception {
        // CORRECCIÓN: Comprobamos que el filtro no devuelva 401 ni 403, permitiendo el paso libre (el 404 es correcto porque el fichero físico no está en la prueba)
        mockMvc.perform(get("/css/main.css"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("3. Perímetro Protegido: Bloquea con 401 Unauthorized a peticiones anónimas en recursos privados")
    void accesoProtegidoWeb_Anonimo_RedirigeALogin() throws Exception {
        // CORRECCIÓN: Tu SecurityConfig corta con un HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED) directo
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("4. Perímetro API Protegido: El cortafuegos responde 401 sin envoltorios HTML para endpoints REST")
    void accesoProtegidoApi_Anonimo_Devuelve401Puro() throws Exception {
        mockMvc.perform(get("/api/comunidades/1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("5. Acceso Autenticado: El cortafuegos da paso al usuario (la redirección posterior es del controlador)")
    @WithMockUser(username = "jesus@jfgb.es", roles = {"USER"})
    void accesoProtegidoWeb_Autenticado_PermiteEntrada() throws Exception {
        // CORRECCIÓN: Certificamos que el filtro le permite entrar al controlador de Spring, el cual aplica su redirección de negocio (302)
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().is3xxRedirection());
    }
}