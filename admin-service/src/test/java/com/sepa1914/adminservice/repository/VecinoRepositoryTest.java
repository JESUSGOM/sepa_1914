package com.sepa1914.adminservice.repository;

import com.sepa1914.adminservice.model.Comunidad;
import com.sepa1914.adminservice.model.ConceptoCobro;
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

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class VecinoRepositoryTest {

    @Autowired
    private VecinoRepository vecinoRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Comunidad comA;
    private Usuario admin;

    @BeforeEach
    void setUp() {
        admin = new Usuario();
        admin.setUsername("gestor_vecinos");
        admin.setPassword("pass");
        entityManager.persist(admin);

        comA = new Comunidad();
        comA.setNombre("Comunidad Test");
        comA.setAdministrador(admin);
        entityManager.persist(comA);
        entityManager.flush();
    }

    @Test
    @DisplayName("1. Optimización JOIN FETCH: Carga vecinos con conceptos en una sola consulta")
    void findAllByComunidadIdWithConceptos_DebeCargarRelaciones() {
        // GIVEN: Vecino con conceptos persistidos
        Vecino v = new Vecino();
        v.setNombre("Propietario 1");
        v.setVivienda("1A");
        v.setNif("12345678Z");
        v.setComunidad(comA);
        entityManager.persist(v);

        ConceptoCobro concepto = new ConceptoCobro();
        concepto.setDescripcion("Cuota");
        concepto.setMesInicio(1); // <--- AÑADE ESTA LÍNEA QUE FALTABA
        concepto.setVecino(v);
        entityManager.persist(concepto);
        entityManager.flush();
        entityManager.clear(); // Limpiamos caché para forzar carga desde DB

        // WHEN
        List<Vecino> resultado = vecinoRepository.findAllByComunidadIdWithConceptos(comA.getId());

        // THEN
        assertFalse(resultado.isEmpty());
        assertFalse(resultado.get(0).getListaConceptos().isEmpty(), "La lista de conceptos debería estar cargada");
        assertEquals("Cuota", resultado.get(0).getListaConceptos().get(0).getDescripcion());
    }

    @Test
    @DisplayName("2. Seguridad: Validación de acceso por nombre de usuario")
    void findByIdAndAdminUsername_DebeAislarPorAdministrador() {
        // GIVEN
        Vecino v = new Vecino();
        v.setNombre("Solo accesible para gestor_vecinos");
        v.setVivienda("2B");
        v.setNif("87654321A");
        v.setComunidad(comA);
        entityManager.persist(v);
        entityManager.flush();

        // WHEN: Intentar buscar con usuario erróneo
        Optional<Vecino> resultado = vecinoRepository.findByIdAndAdminUsername(v.getId(), "usuario_desconocido");

        // THEN
        assertFalse(resultado.isPresent(), "Un usuario ajeno no debe poder ver datos de este vecino.");
    }
}