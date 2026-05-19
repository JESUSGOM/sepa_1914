package com.sepa1914.adminservice.service;

import com.sepa1914.adminservice.model.MovimientoBancario;
import com.sepa1914.adminservice.model.Recibo;
import com.sepa1914.adminservice.model.Recibo.EstadoRecibo;
import com.sepa1914.adminservice.model.Vecino;
import com.sepa1914.adminservice.repository.MovimientoBancarioRepository;
import com.sepa1914.adminservice.repository.ReciboRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConciliacionServiceTest {

    @Mock
    private ReciboRepository reciboRepository;

    @Mock
    private MovimientoBancarioRepository movRepository;

    @InjectMocks
    private ConciliacionService conciliacionService;

    private Long comunidadId;
    private Vecino vecinoMock;

    @BeforeEach
    void setUp() {
        this.comunidadId = 1L;

        this.vecinoMock = new Vecino();
        this.vecinoMock.setId(101L);
        this.vecinoMock.setNombre("ANTONIO MARTIN");
    }

    @Test
    @DisplayName("1. Conciliación Automática: Cruce exitoso por coincidencia única de importe")
    void ejecutarConciliacionAutomatica_CoincidenciaUnica() {
        // GIVEN: Un apunte bancario pendiente de 90.00€
        MovimientoBancario mov = new MovimientoBancario();
        mov.setId(10L);
        mov.setImporte(new BigDecimal("90.00"));
        mov.setFechaOperacion(LocalDate.now());
        mov.setConciliado(false);

        // Un único recibo que coincide exactamente en importe
        Recibo recibo = new Recibo();
        recibo.setId(20L);
        recibo.setImporte(new BigDecimal("90.00"));
        recibo.setEstado(EstadoRecibo.PENDIENTE);

        when(movRepository.findByComunidadIdAndConciliadoFalse(comunidadId)).thenReturn(List.of(mov));
        when(reciboRepository.findByComunidadIdAndImporteAndEstado(comunidadId, mov.getImporte(), EstadoRecibo.PENDIENTE))
                .thenReturn(List.of(recibo));

        // WHEN: Ejecutamos el motor de cruce automático
        int conciliados = conciliacionService.ejecutarConciliacionAutomatica(comunidadId);

        // THEN: Debe realizar 1 emparejamiento y conmutar estados contables
        assertEquals(1, conciliados);
        assertTrue(mov.isConciliado());
        assertEquals(EstadoRecibo.COBRADO, recibo.getEstado());
        assertEquals(mov.getImporte(), recibo.getPagadoAcumulado());
        verify(movRepository, times(1)).save(mov);
        verify(reciboRepository, times(1)).save(recibo);
    }

    @Test
    @DisplayName("2. Conciliación Automática Inteligente: Desambiguación analizando el nombre del vecino en el concepto")
    void ejecutarConciliacionAutomatica_MultiplesCandidatos_FiltraPorConcepto() {
        // GIVEN: Un apunte bancario con el nombre del vecino incrustado en el concepto
        MovimientoBancario mov = new MovimientoBancario();
        mov.setId(11L);
        mov.setImporte(new BigDecimal("65.00"));
        mov.setConcepto("TRANSFERENCIA DE DEUDOR ANTONIO MARTIN MAYO");
        mov.setConciliado(false);

        // Dos recibos del mismo importe pero de diferentes vecinos
        Recibo reciboErroneo = new Recibo();
        reciboErroneo.setId(31L);

        Recibo reciboCorrecto = new Recibo();
        reciboCorrecto.setId(32L);
        reciboCorrecto.setVecino(vecinoMock); // Ligado a ANTONIO MARTIN

        when(movRepository.findByComunidadIdAndConciliadoFalse(comunidadId)).thenReturn(List.of(mov));
        when(reciboRepository.findByComunidadIdAndImporteAndEstado(comunidadId, mov.getImporte(), EstadoRecibo.PENDIENTE))
                .thenReturn(List.of(reciboErroneo, reciboCorrecto));

        // WHEN
        int conciliados = conciliacionService.ejecutarConciliacionAutomatica(comunidadId);

        // THEN: Debe discriminar los candidatos y enlazar exclusivamente al propietario indicado en el texto
        assertEquals(1, conciliados);
        assertTrue(mov.isConciliado());
        assertEquals(EstadoRecibo.COBRADO, reciboCorrecto.getEstado());
        assertNotEquals(EstadoRecibo.COBRADO, reciboErroneo.getEstado());
    }

    @Test
    @DisplayName("3. Conciliación Manual Múltiple: Vinculación colectiva de una remesa unificada")
    void vincularMovimientoConVariosRecibos_CasoExito() {
        // GIVEN: Un ingreso totalizador en el banco
        MovimientoBancario abonoRemesa = new MovimientoBancario();
        abonoRemesa.setId(50L);
        abonoRemesa.setFechaOperacion(LocalDate.now());

        Recibo r1 = new Recibo(); r1.setId(61L); r1.setImporte(new BigDecimal("50.00"));
        Recibo r2 = new Recibo(); r2.setId(62L); r2.setImporte(new BigDecimal("60.00"));

        when(movRepository.findById(50L)).thenReturn(Optional.of(abonoRemesa));
        when(reciboRepository.findById(61L)).thenReturn(Optional.of(r1));
        when(reciboRepository.findById(62L)).thenReturn(Optional.of(r2));

        // WHEN: Vinculamos el apunte bancario con las IDs de los recibos que lo componen
        conciliacionService.vincularMovimientoConVariosRecibos(50L, List.of(61L, 62L));

        // THEN: Los recibos se saldan y se asocian al movimiento unificado
        assertTrue(abonoRemesa.isConciliado());
        assertEquals(EstadoRecibo.COBRADO, r1.getEstado());
        assertEquals(EstadoRecibo.COBRADO, r2.getEstado());
        assertEquals(abonoRemesa, r1.getMovimientoBancario());
    }

    @Test
    @DisplayName("4. Reparto en Cascada Contable: Amortización cronológica de deuda por pagos parciales")
    void conciliarEnCascada_ReparteImporteEntreRecibosPendientes() {
        // GIVEN: El vecino realiza una transferencia genérica de 150.00€
        MovimientoBancario movEntrega = new MovimientoBancario();
        movEntrega.setId(70L);
        movEntrega.setImporte(new BigDecimal("150.00"));
        movEntrega.setFechaOperacion(LocalDate.now());

        // Tiene dos recibos pendientes (uno de 100€ más antiguo y otro de 100€ más reciente)
        Recibo reciboViejo = new Recibo();
        reciboViejo.setId(81L);
        reciboViejo.setImporte(new BigDecimal("100.00"));
        reciboViejo.setPagadoAcumulado(BigDecimal.ZERO);
        reciboViejo.setFechaEmision(LocalDate.now().minusMonths(1));
        reciboViejo.setVecino(vecinoMock);

        Recibo reciboNuevo = new Recibo();
        reciboNuevo.setId(82L);
        reciboNuevo.setImporte(new BigDecimal("100.00"));
        reciboNuevo.setPagadoAcumulado(BigDecimal.ZERO);
        reciboNuevo.setFechaEmision(LocalDate.now());
        reciboNuevo.setVecino(vecinoMock);

        // Simulamos la respuesta de la base de datos
        List<Recibo> todosLosRecibos = new ArrayList<>(List.of(reciboNuevo, reciboViejo));
        when(movRepository.findById(70L)).thenReturn(Optional.of(movEntrega));
        when(reciboRepository.findAll()).thenReturn(todosLosRecibos);

        // WHEN: El motor liquida los saldos en cascada
        conciliacionService.conciliarEnCascada(70L, 101L);

        // THEN: El primer recibo debe quedar liquidado (100€) y el segundo parcialmente amortizado (50€)
        assertTrue(movEntrega.isConciliado());
        assertEquals(new BigDecimal("100.00"), reciboViejo.getPagadoAcumulado());
        assertEquals(new BigDecimal("50.00"), reciboNuevo.getPagadoAcumulado());
        verify(reciboRepository, times(2)).save(any(Recibo.class));
    }
}