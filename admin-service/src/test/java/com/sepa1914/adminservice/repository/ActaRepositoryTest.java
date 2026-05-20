package com.sepa1914.adminservice.repository;

import com.sepa1914.adminservice.model.Acta;
import com.sepa1914.adminservice.model.Comunidad;
import com.sepa1914.adminservice.model.TipoReparto;
import com.sepa1914.adminservice.model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
class ActaRepositoryTest {

    @Autowired
    private ActaRepository actaRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Usuario usuarioMock;

    @BeforeEach
    void setUp() {
        // GIVEN: Creamos y persistimos el usuario dueño obligatorio (NOT NULL) de las comunidades
        usuarioMock = new Usuario();
        usuarioMock.setUsername("admin_test");
        usuarioMock.setPassword("secret_pass");
        entityManager.persist(usuarioMock);
    }

    @Test
    @DisplayName("1. Búsqueda por Comunidad: Filtra y aísla correctamente las actas de una comunidad específica")
    void findByComunidad_DebeRetornarSoloActasDeEsaComunidad() {
        // GIVEN: Comunidad A configurada correctamente con su Administrador
        Comunidad comunidadA = new Comunidad();
        comunidadA.setNombre("COMUNIDAD MALGRAT A");
        comunidadA.setTipoReparto(TipoReparto.PARTES_IGUALES); // Usamos tu enum real
        comunidadA.setAdministrador(usuarioMock); // Ajustado al nombre real de tu propiedad
        entityManager.persist(comunidadA);

        // GIVEN: Acta asociada a la comunidad A con sus campos obligatorios
        Acta actaA = new Acta();
        actaA.setComunidad(comunidadA);
        actaA.setTitulo("Junta Ordinaria 2026");
        actaA.setFechaReunion(LocalDate.of(2026, 5, 20));
        actaA.setContenido("Contenido encriptado de la junta A.");
        entityManager.persist(actaA);

        // GIVEN: Comunidad B para validar el aislamiento perimetral de consultas
        Comunidad comunidadB = new Comunidad();
        comunidadB.setNombre("COMUNIDAD BARRANCO B");
        comunidadB.setTipoReparto(TipoReparto.PARTES_IGUALES);
        comunidadB.setAdministrador(usuarioMock);
        entityManager.persist(comunidadB);

        Acta actaB = new Acta();
        actaB.setComunidad(comunidadB);
        actaB.setTitulo("Junta Extraordinaria 2026");
        actaB.setFechaReunion(LocalDate.of(2026, 5, 21));
        actaB.setContenido("Contenido encriptado de la junta B.");
        entityManager.persist(actaB);

        entityManager.flush();

        // WHEN: Solicitamos al repositorio las actas vinculadas exclusivamente a la Comunidad A
        List<Acta> resultado = actaRepository.findByComunidad(comunidadA);

        // THEN: Comprobamos que no se mezclen los datos entre administradores o comunidades
        assertNotNull(resultado, "El resultado no debe ser nulo.");
        assertEquals(1, resultado.size(), "Debería haber exactamente 1 acta para la comunidad A.");
        assertEquals(comunidadA.getId(), resultado.get(0).getComunidad().getId());
        assertEquals("Contenido encriptado de la junta A.", resultado.get(0).getContenido());
    }

    @Test
    @DisplayName("2. Búsqueda por Comunidad Inexistente: Retorna lista vacía si la comunidad no tiene actas")
    void findByComunidad_VacioSiNoHayActas() {
        // GIVEN: Una comunidad nueva que todavía no ha celebrado ninguna junta
        Comunidad comunidadVacia = new Comunidad();
        comunidadVacia.setNombre("COMUNIDAD NUEVA SIN JUNTAS");
        comunidadVacia.setTipoReparto(TipoReparto.PARTES_IGUALES);
        comunidadVacia.setAdministrador(usuarioMock);
        entityManager.persist(comunidadVacia);
        entityManager.flush();

        // WHEN
        List<Acta> resultado = actaRepository.findByComunidad(comunidadVacia);

        // THEN
        assertNotNull(resultado);
        assertTrue(resultado.isEmpty(), "La lista de actas debería estar vacía.");
    }
}