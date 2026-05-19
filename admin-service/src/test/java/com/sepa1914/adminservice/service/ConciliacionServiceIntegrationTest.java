package com.sepa1914.adminservice.service;

import com.sepa1914.adminservice.model.Comunidad;
import com.sepa1914.adminservice.model.MovimientoBancario;
import com.sepa1914.adminservice.model.Recibo;
import com.sepa1914.adminservice.model.Usuario;
import com.sepa1914.adminservice.model.Vecino;
import com.sepa1914.adminservice.model.Recibo.EstadoRecibo;
import com.sepa1914.adminservice.repository.ComunidadRepository;
import com.sepa1914.adminservice.repository.MovimientoBancarioRepository;
import com.sepa1914.adminservice.repository.ReciboRepository;
import com.sepa1914.adminservice.repository.UsuarioRepository;
import com.sepa1914.adminservice.repository.VecinoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional // Revierte todos los inserts de prueba limpiamente al terminar
class ConciliacionServiceIntegrationTest {

    @Autowired
    private ConciliacionService conciliacionService;

    @Autowired
    private ComunidadRepository comunidadRepository;

    @Autowired
    private MovimientoBancarioRepository movimientoRepository;

    @Autowired
    private ReciboRepository reciboRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private VecinoRepository vecinoRepository;

    @Test
    @DisplayName("GTI-INTEGRATION: Sincronización Automática de Extractos Norma 43")
    void probarConciliacionAutomaticaConEntidadesReales() {
        // 1. GIVEN: Creación de estructura de datos mínima para satisfacer restricciones NotNull de BD
        Usuario usuarioPrueba = new Usuario();
        usuarioPrueba.setUsername("admin_test_concilia");
        usuarioPrueba.setPassword("password_test");
        Usuario usuarioGuardado = usuarioRepository.save(usuarioPrueba);

        Comunidad fincaPrueba = new Comunidad();
        fincaPrueba.setNombre("CP TEST INTEGRACION");
        fincaPrueba.setIdentificadorAcreedor("G99999999");
        fincaPrueba.setIban("ES21001590000000000000");
        fincaPrueba.setAdministrador(usuarioGuardado);
        Comunidad fincaGuardada = comunidadRepository.save(fincaPrueba);

        Vecino vecinoPrueba = new Vecino();
        vecinoPrueba.setNombre("Propietario Test");
        vecinoPrueba.setComunidad(fincaGuardada);
        // SOLUCIÓN DEFINITIVA: Seteamos la propiedad obligatoria 'vivienda' para evitar el error de BD
        vecinoPrueba.setVivienda("1º A");
        Vecino vecinoGuardado = vecinoRepository.save(vecinoPrueba);

        // Generación del apunte bancario (Norma 43)
        MovimientoBancario movimiento = new MovimientoBancario();
        movimiento.setComunidad(fincaGuardada);
        movimiento.setImporte(new BigDecimal("120.50"));
        movimiento.setFechaOperacion(LocalDate.now());
        movimiento.setFechaValor(LocalDate.now());
        movimiento.setConcepto("EMISION RECIBO INTEGRACION MAYO");
        movimiento.setSigno("2");
        movimiento.setConciliado(false);
        MovimientoBancario movGuardado = movimientoRepository.save(movimiento);

        // Generación del recibo del propietario
        Recibo recibo = new Recibo();
        recibo.setComunidad(fincaGuardada);
        recibo.setImporte(new BigDecimal("120.50"));
        recibo.setEstado(EstadoRecibo.PENDIENTE);
        recibo.setConcepto("CUOTA ORDINARIA");
        recibo.setFechaEmision(LocalDate.now());
        recibo.setVecino(vecinoGuardado);
        Recibo reciboGuardado = reciboRepository.save(recibo);

        // 2. WHEN: Ejecución del motor transaccional real de conciliación
        int conciliados = conciliacionService.ejecutarConciliacionAutomatica(fincaGuardada.getId());

        // 3. THEN: Aserciones de integridad y éxito
        assertEquals(1, conciliados, "El motor debería haber enlazado el apunte exacto.");

        MovimientoBancario movVerificado = movimientoRepository.findById(movGuardado.getId()).orElseThrow();
        Recibo reciboVerificado = reciboRepository.findById(reciboGuardado.getId()).orElseThrow();

        assertTrue(movVerificado.isConciliado());
        assertEquals(EstadoRecibo.COBRADO, reciboVerificado.getEstado());
        assertEquals(movVerificado.getFechaOperacion(), reciboVerificado.getFechaCobroBanco());
    }
}