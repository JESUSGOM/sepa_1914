package com.sepa1914.adminservice.repository;

import com.sepa1914.adminservice.model.Comunidad;
import com.sepa1914.adminservice.model.ConceptoCobro;
import com.sepa1914.adminservice.model.TipoReparto;
import com.sepa1914.adminservice.model.Usuario;
import com.sepa1914.adminservice.model.Vecino;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
        "spring.datasource.driver-class-name=org.h2.Driver"
})
class ConceptoCobroRepositoryTest {

    @Autowired
    private ConceptoCobroRepository conceptoCobroRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Usuario adminAlpha;
    private Comunidad comunidadAlpha;

    @BeforeEach
    void setUp() {
        // GIVEN: Inicializamos el entorno estructural mínimo obligatorio (NOT NULL)
        adminAlpha = new Usuario();
        adminAlpha.setUsername("gestor_conceptos");
        adminAlpha.setPassword("securepass");
        entityManager.persist(adminAlpha);

        comunidadAlpha = new Comunidad();
        comunidadAlpha.setNombre("CP MALGRAT CONCEPTO");
        comunidadAlpha.setTipoReparto(TipoReparto.PARTES_IGUALES);
        comunidadAlpha.setAdministrador(adminAlpha);
        entityManager.persist(comunidadAlpha);

        entityManager.flush();
    }

    @Test
    @DisplayName("1. Conteo de Conceptos: Calcula la cantidad exacta de cargos gestionados bajo el perímetro de un usuario")
    void contarPorUsuario_DebeContarCargosAsociadosALasComunidadesDelAdmin() {
        // GIVEN: Un vecino en la comunidad del Admin con un concepto asignado
        Vecino v = new Vecino();
        v.setNombre("JESÚS FRANCISCO");
        v.setVivienda("Portal 1ºA");
        v.setNif("12345678X");
        v.setComunidad(comunidadAlpha);
        entityManager.persist(v);

        ConceptoCobro cc = new ConceptoCobro();
        cc.setDescripcion("CUOTA ORDINARIA MENSUAL");
        cc.setImporte(new BigDecimal("75.00"));
        cc.setMesInicio(1);
        cc.setPeriodicidad(ConceptoCobro.Periodicidad.MENSUAL);
        cc.setComunidad(comunidadAlpha);
        cc.setVecino(v);
        entityManager.persist(cc);

        // GIVEN: Un concepto que pertenece a otro gestor distinto para verificar el aislamiento
        Usuario adminBeta = new Usuario();
        adminBeta.setUsername("gestor_beta");
        adminBeta.setPassword("pass");
        entityManager.persist(adminBeta);

        Comunidad comunidadBeta = new Comunidad();
        comunidadBeta.setNombre("CP COMUNIDAD BETA");
        comunidadBeta.setTipoReparto(TipoReparto.PARTES_IGUALES);
        comunidadBeta.setAdministrador(adminBeta);
        entityManager.persist(comunidadBeta);

        ConceptoCobro ccBeta = new ConceptoCobro();
        ccBeta.setDescripcion("DERRAMA EXTRA BETA");
        ccBeta.setImporte(new BigDecimal("120.00"));
        ccBeta.setMesInicio(3);
        ccBeta.setComunidad(comunidadBeta);
        entityManager.persist(ccBeta);

        entityManager.flush();

        // WHEN: Consultamos el totalizador del repositorio para Alpha
        long totalAlpha = conceptoCobroRepository.contarPorUsuario(adminAlpha.getId());

        // THEN: Debe reportar únicamente el concepto que cae bajo sus fincas
        assertEquals(1, totalAlpha, "El conteo para el administrador Alpha debe ser exactamente 1.");
    }

    @Test
    @DisplayName("2. Conceptos Globales: Localiza con precisión las plantillas genéricas (sin comunidad ni vecino asignados)")
    void findAllGenericConcepts_DebeRetornarSoloConceptosGlobales() {
        // GIVEN: Un concepto genérico del sistema (Comunidad y Vecino son NULL)
        ConceptoCobro globalConcept = new ConceptoCobro();
        globalConcept.setDescripcion("ALQUILER ANTENA TELEFONÍA");
        globalConcept.setImporte(new BigDecimal("450.00"));
        globalConcept.setMesInicio(1);
        globalConcept.setPeriodicidad(ConceptoCobro.Periodicidad.ANUAL);
        entityManager.persist(globalConcept);

        // GIVEN: Un concepto privado asociado a nuestra comunidad de pruebas
        ConceptoCobro localConcept = new ConceptoCobro();
        localConcept.setDescripcion("LIMPIEZA PORTAL LOCAL");
        localConcept.setImporte(new BigDecimal("50.00"));
        localConcept.setMesInicio(1);
        localConcept.setComunidad(comunidadAlpha);
        entityManager.persist(localConcept);

        entityManager.flush();

        // WHEN: Invocamos la consulta de plantillas del repositorio
        List<ConceptoCobro> genericos = conceptoCobroRepository.findAllGenericConcepts();

        // THEN: Comprobamos que el filtro intercepte y devuelva solo el registro global
        assertNotNull(genericos);
        assertEquals(1, genericos.size(), "Debería listar únicamente 1 concepto genérico.");
        assertNull(genericos.get(0).getComunidad(), "El concepto genérico no debe tener comunidad vinculada.");
        assertNull(genericos.get(0).getVecino(), "El concepto genérico no debe tener vecino vinculado.");
        assertEquals("ALQUILER ANTENA TELEFONÍA", genericos.get(0).getDescripcion());
    }
}