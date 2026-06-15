package com.sepa1914.adminservice.repository;

import com.sepa1914.adminservice.model.ConfiguracionRutas;
import com.sepa1914.adminservice.model.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
class ConfiguracionRutasRepositoryTest {

    @Autowired
    private ConfiguracionRutasRepository rutasRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("1. Búsqueda por Administrador: Recupera configuración única y aislada")
    void findByAdministrador_DebeRetornarConfiguracionCorrecta() {
        // GIVEN: Un administrador y su configuración de rutas
        Usuario admin = new Usuario();
        admin.setUsername("admin_rutas");
        admin.setPassword("pass");
        entityManager.persist(admin);

        ConfiguracionRutas config = new ConfiguracionRutas("C:/SEPA", "C:/PDF", admin);
        entityManager.persist(config);
        entityManager.flush();

        // WHEN
        Optional<ConfiguracionRutas> resultado = rutasRepository.findByAdministrador(admin);

        // THEN
        assertTrue(resultado.isPresent());
        assertEquals("C:/SEPA", resultado.get().getRutaC19());
        assertEquals(admin.getUsername(), resultado.get().getAdministrador().getUsername());
    }

    @Test
    @DisplayName("2. Aislamiento: No debe devolver configuración de otros administradores")
    void findByAdministrador_NoDebeMezclarConfiguraciones() {
        // GIVEN: Dos admins con rutas distintas
        Usuario admin1 = new Usuario(); admin1.setUsername("a1"); admin1.setPassword("p");
        Usuario admin2 = new Usuario(); admin2.setUsername("a2"); admin2.setPassword("p");
        entityManager.persist(admin1);
        entityManager.persist(admin2);

        ConfiguracionRutas config1 = new ConfiguracionRutas("PATH_1", "PDF_1", admin1);
        entityManager.persist(config1);
        entityManager.flush();

        // WHEN: Buscamos la del admin2 (que no tiene)
        Optional<ConfiguracionRutas> resultado = rutasRepository.findByAdministrador(admin2);

        // THEN
        assertFalse(resultado.isPresent());
    }
}