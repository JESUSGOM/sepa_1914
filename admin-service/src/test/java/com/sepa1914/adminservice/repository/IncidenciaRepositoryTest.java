package com.sepa1914.adminservice.repository;

import com.sepa1914.adminservice.model.Comunidad;
import com.sepa1914.adminservice.model.Incidencia;
import com.sepa1914.adminservice.model.Incidencia.EstadoIncidencia;
import com.sepa1914.adminservice.model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class IncidenciaRepositoryTest {

    @Autowired
    private IncidenciaRepository incidenciaRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Comunidad comA;

    @BeforeEach
    void setUp() {
        Usuario admin = new Usuario();
        admin.setUsername("admin_incidencias");
        admin.setPassword("pass");
        entityManager.persist(admin);

        comA = new Comunidad();
        comA.setNombre("Comunidad Incidencias");
        comA.setAdministrador(admin);
        entityManager.persist(comA);
        entityManager.flush();
    }

    @Test
    @DisplayName("1. Filtrado por Estado: Recupera incidencias de una comunidad según su estado")
    void findByComunidadIdAndEstado_DebeFiltrarCorrectamente() {
        // GIVEN
        Incidencia i1 = new Incidencia();
        i1.setTitulo("Fuga Agua");
        i1.setEstado(EstadoIncidencia.ABIERTA);
        i1.setComunidad(comA);
        entityManager.persist(i1);

        Incidencia i2 = new Incidencia();
        i2.setTitulo("Revisión Ascensor");
        i2.setEstado(EstadoIncidencia.PENDIENTE);
        i2.setComunidad(comA);
        entityManager.persist(i2);
        entityManager.flush();

        // WHEN
        List<Incidencia> abiertas = incidenciaRepository.findByComunidadIdAndEstado(comA.getId(), EstadoIncidencia.ABIERTA);

        // THEN
        assertEquals(1, abiertas.size());
        assertEquals("Fuga Agua", abiertas.get(0).getTitulo());
    }

    @Test
    @DisplayName("2. Dashboard Global: Cuenta incidencias por estado")
    void countByEstado_DebeContarGlobalmente() {
        // GIVEN
        crearIncidencia("Inc 1", EstadoIncidencia.PENDIENTE);
        crearIncidencia("Inc 2", EstadoIncidencia.PENDIENTE);
        crearIncidencia("Inc 3", EstadoIncidencia.FINALIZADA);
        entityManager.flush();

        // WHEN
        long pendientes = incidenciaRepository.countByEstado(EstadoIncidencia.PENDIENTE);

        // THEN
        assertEquals(2, pendientes);
    }

    private void crearIncidencia(String titulo, EstadoIncidencia estado) {
        Incidencia i = new Incidencia();
        i.setTitulo(titulo);
        i.setEstado(estado);
        i.setComunidad(comA);
        entityManager.persist(i);
    }
}