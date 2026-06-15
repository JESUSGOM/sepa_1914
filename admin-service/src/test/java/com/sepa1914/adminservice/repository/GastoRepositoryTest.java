package com.sepa1914.adminservice.repository;

import com.sepa1914.adminservice.model.*;
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

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect"
})
class GastoRepositoryTest {

    @Autowired
    private GastoRepository gastoRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Comunidad comA;
    private CuentaContable cuentaGasto;

    @BeforeEach
    void setUp() {
        Usuario admin = new Usuario();
        admin.setUsername("admin_gastos");
        admin.setPassword("pass");
        entityManager.persist(admin);

        comA = new Comunidad();
        comA.setNombre("Comunidad Gastos");
        comA.setAdministrador(admin);
        entityManager.persist(comA);

        cuentaGasto = new CuentaContable("629000", "Servicios Varios", TipoCuenta.GASTO, comA);
        entityManager.persist(cuentaGasto);
        entityManager.flush();
    }

    @Test
    @DisplayName("1. Suma de Gastos: Calcula correctamente el total pagado por cuenta y año")
    void sumImportePagadoByCuentaAndAnio_DebeSumarCorrectamente() {
        // GIVEN: Un gasto pagado en 2026 y otro pagado en 2025
        Gasto g1 = new Gasto();
        g1.setComunidad(comA);
        g1.setCuentaGasto(cuentaGasto);
        g1.setFecha(LocalDate.now()); // FECHA_FACTURA (obligatorio)
        g1.setImporteTotal(new BigDecimal("100.00"));
        g1.setPagado(true);
        g1.setFechaPago(LocalDate.of(2026, 5, 20));
        g1.setNumeroFactura("F2026");
        g1.setProveedor("Proveedor A");
        g1.setConcepto("Limpieza");
        entityManager.persist(g1);

        Gasto g2 = new Gasto();
        g2.setComunidad(comA);
        g2.setCuentaGasto(cuentaGasto);
        g2.setFecha(LocalDate.now()); // FECHA_FACTURA (obligatorio)
        g2.setImporteTotal(new BigDecimal("50.00"));
        g2.setPagado(true);
        g2.setFechaPago(LocalDate.of(2025, 12, 31));
        g2.setNumeroFactura("F2025");
        g2.setProveedor("Proveedor B");
        g2.setConcepto("Reparaciones");
        entityManager.persist(g2);
        entityManager.flush();

        // WHEN
        BigDecimal total2026 = gastoRepository.sumImportePagadoByCuentaAndAnio(cuentaGasto.getId(), 2026);

        // THEN
        assertNotNull(total2026);
        assertEquals(0, new BigDecimal("100.00").compareTo(total2026), "La suma de 2026 debería ser 100");
    }

    @Test
    @DisplayName("2. Integridad: Buscar factura por número de asiento")
    void findByNumeroAsiento_DebeEncontrarFactura() {
        // GIVEN
        Gasto g = new Gasto();
        g.setComunidad(comA);
        g.setCuentaGasto(cuentaGasto);
        g.setFecha(LocalDate.now()); // FECHA_FACTURA (obligatorio)
        g.setImporteTotal(BigDecimal.TEN);
        g.setNumeroFactura("F-1");
        g.setProveedor("Test");
        g.setConcepto("Test");
        g.setNumeroAsiento("AS-999");
        g.setPagado(false);
        entityManager.persist(g);
        entityManager.flush();

        // THEN
        assertTrue(gastoRepository.findByNumeroAsiento("AS-999").isPresent(), "Debería encontrar el gasto por asiento");
    }
}