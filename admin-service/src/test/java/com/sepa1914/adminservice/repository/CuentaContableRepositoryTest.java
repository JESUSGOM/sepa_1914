package com.sepa1914.adminservice.repository;

import com.sepa1914.adminservice.model.Comunidad;
import com.sepa1914.adminservice.model.CuentaContable;
import com.sepa1914.adminservice.model.TipoCuenta;
import com.sepa1914.adminservice.model.Usuario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class CuentaContableRepositoryTest {

    @Autowired
    private CuentaContableRepository cuentaRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Comunidad comA;

    @BeforeEach
    void setUp() {
        Usuario admin = new Usuario();
        admin.setUsername("admin");
        admin.setPassword("pass");
        entityManager.persist(admin);

        comA = new Comunidad();
        comA.setNombre("Comunidad A");
        comA.setAdministrador(admin);
        entityManager.persist(comA);
        entityManager.flush();
    }

    @Test
    @DisplayName("1. Lógica de Secuencia: Recupera el máximo código de vecino (430...) correctamente")
    void findMaxCodigoVecino_DebeRetornarElCodigoMasAlto() {
        // GIVEN: Dos vecinos numerados en la misma comunidad
        entityManager.persist(new CuentaContable("43000001", "Vecino 1", TipoCuenta.VECINO, comA));
        entityManager.persist(new CuentaContable("43000005", "Vecino 5", TipoCuenta.VECINO, comA));
        entityManager.persist(new CuentaContable("60000001", "Gasto", TipoCuenta.GASTO, comA)); // Ignorado
        entityManager.flush();

        // WHEN
        String maxCodigo = cuentaRepository.findMaxCodigoVecino(comA.getId());

        // THEN
        assertEquals("43000005", maxCodigo);
    }

    @Test
    @DisplayName("2. Búsqueda Paginada: Filtro combinado por código o nombre")
    void buscarPorComunidadYTexto_DebeFiltrarAmbosCampos() {
        // GIVEN
        entityManager.persist(new CuentaContable("70001", "Cuota General", TipoCuenta.INGRESO, comA));
        entityManager.persist(new CuentaContable("70002", "Derrama Extra", TipoCuenta.INGRESO, comA));
        entityManager.flush();

        // WHEN: Buscar por el nombre "Extra"
        Page<CuentaContable> resultado = cuentaRepository.buscarPorComunidadYTexto(comA.getId(), "extra", PageRequest.of(0, 10));

        // THEN
        assertEquals(1, resultado.getContent().size());
        assertEquals("70002", resultado.getContent().get(0).getCodigo());
    }
}