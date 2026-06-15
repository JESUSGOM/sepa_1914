package com.sepa1914.adminservice.repository;

import com.sepa1914.adminservice.model.Comunidad;
import com.sepa1914.adminservice.model.FicheroGenerado;
import com.sepa1914.adminservice.model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class FicheroGeneradoRepositoryTest {

    @Autowired
    private FicheroGeneradoRepository ficheroRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Usuario admin;
    private Comunidad comunidad;

    @BeforeEach
    void setUp() {
        admin = new Usuario();
        admin.setUsername("gestor_ficheros");
        admin.setPassword("pass");
        entityManager.persist(admin);

        comunidad = new Comunidad();
        comunidad.setNombre("Comunidad Ficheros");
        comunidad.setAdministrador(admin);
        entityManager.persist(comunidad);
        entityManager.flush();
    }

    @Test
    @DisplayName("1. Conteo de Ficheros: Calcula correctamente los ficheros de un usuario")
    void contarPorUsuario_DebeRetornarTotalCorrecto() {
        // GIVEN: Dos ficheros generados para nuestro admin
        persistirFichero("FICH_001");
        persistirFichero("FICH_002");
        entityManager.flush();

        // WHEN
        long total = ficheroRepository.contarPorUsuario(admin.getId());

        // THEN
        assertEquals(2, total);
    }

    @Test
    @DisplayName("2. Listado Ordenado: Retorna ficheros del usuario en orden descendente por ID")
    void findByUsuarioId_DebeRetornarListaOrdenadaDesc() {
        // GIVEN
        persistirFichero("FICH_001");
        persistirFichero("FICH_002");

        // WHEN
        List<FicheroGenerado> resultados = ficheroRepository.findByUsuarioId(admin.getId());

        // THEN
        assertEquals(2, resultados.size());
        assertTrue(resultados.get(0).getIdentificadorFichero().equals("FICH_002"), "Debe ser el último insertado");
    }

    private void persistirFichero(String idFichero) {
        FicheroGenerado f = new FicheroGenerado();
        f.setComunidad(comunidad);
        f.setIdentificadorFichero(idFichero);
        f.setFechaCreacion(LocalDate.now());
        f.setTotalImporte(new BigDecimal("100.00"));
        f.setNumeroRecibos(5);
        entityManager.persist(f);
    }
}