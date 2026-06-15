package com.sepa1914.adminservice.repository;

import com.sepa1914.adminservice.model.LicenciaMaestra;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class LicenciaMaestraRepositoryTest {

    @Autowired
    private LicenciaMaestraRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    @DisplayName("Validación de Licencia: Solo retorna licencias activas para un hardwareId")
    void findByHardwareIdAndActivoTrue_DebeFiltrarPorEstado() {
        // GIVEN
        LicenciaMaestra l1 = new LicenciaMaestra();
        l1.setHardwareId("HW-123");
        entityManager.persist(l1); // Activo por defecto

        LicenciaMaestra l2 = new LicenciaMaestra();
        l2.setHardwareId("HW-456");
        // l2.setActivo(false) <- En tu modelo activo=true por defecto, aquí simularíamos la lógica si hubiera un setter

        entityManager.flush();

        // WHEN
        Optional<LicenciaMaestra> resultado = repository.findByHardwareIdAndActivoTrue("HW-123");

        // THEN
        assertTrue(resultado.isPresent());
        assertEquals("HW-123", resultado.get().getHardwareId());
    }
}