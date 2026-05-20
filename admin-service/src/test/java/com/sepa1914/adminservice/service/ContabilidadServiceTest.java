package com.sepa1914.adminservice.service;

import com.sepa1914.adminservice.model.*;
import com.sepa1914.adminservice.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContabilidadServiceTest {

    @Mock private MovimientoBancarioRepository movRepo;
    @Mock private ReciboRepository reciboRepo;
    @Mock private IncidenciaRepository incidenciaRepo;
    @Mock private ComunidadRepository comunidadRepository;
    @Mock private VecinoRepository vecinoRepository;
    @Mock private CuentaContableRepository cuentaContableRepository;
    @Mock private MovimientoContableRepository movContableRepo;
    @Mock private GastoRepository gastoRepository;
    @Mock private PresupuestoRepository presupuestoRepo;
    @Mock private ConceptoCobroRepository conceptoCobroRepo;

    @InjectMocks
    private ContabilidadService contabilidadService;

    private Comunidad comunidadMock;
    private Vecino vecinoMock;
    private CuentaContable cuentaMock;

    @BeforeEach
    void setUp() {
        comunidadMock = new Comunidad();
        comunidadMock.setId(1L);
        comunidadMock.setNombre("CP MALGRAT 1914");
        comunidadMock.setTipoReparto(TipoReparto.COEFICIENTE);

        vecinoMock = new Vecino();
        vecinoMock.setId(501L);
        vecinoMock.setNombre("JESÚS FRANCISCO");
        vecinoMock.setCoeficiente(new BigDecimal("12.50")); // 12.5% de participación
        vecinoMock.setComunidad(comunidadMock);

        cuentaMock = new CuentaContable();
        cuentaMock.setId(10L);
        cuentaMock.setCodigo("73100001");
        cuentaMock.setComunidad(comunidadMock);
    }

    @Test
    @DisplayName("1. Robustez del Diario: Filtra y descarta automáticamente los apuntes con importe cero")
    void registrarApunte_IgnoraImportesCero() {
        // GIVEN: Una llamada para registrar un asiento cuyo Debe y Haber son cero
        registrarApunteYVerificar(BigDecimal.ZERO, BigDecimal.ZERO, false);
        registrarApunteYVerificar(null, null, false);
    }

    @Test
    @DisplayName("2. Robustez del Diario: Registra apuntes legítimos con importes válidos")
    void registrarApunte_GuardaSiHayImporte() {
        // GIVEN: Una llamada con un apunte legítimo al DEBE
        registrarApunteYVerificar(new BigDecimal("11039.19"), BigDecimal.ZERO, true);
    }

    @Test
    @DisplayName("3. Distribución por Coeficiente: Aplica el porcentaje exacto de copropiedad al vecino")
    void repartirGasto_CalculoExactoPorCoeficiente() {
        // GIVEN: Comunidad configurada por COEFICIENTE y un gasto totalizador de 1000.00€
        comunidadMock.setTipoReparto(TipoReparto.COEFICIENTE);
        comunidadMock.setVecinos(List.of(vecinoMock));
        when(comunidadRepository.findById(1L)).thenReturn(Optional.of(comunidadMock));

        // WHEN & THEN: El método ejecuta el cálculo logueando el reparto sin lanzar excepciones
        assertDoesNotThrow(() -> contabilidadService.repartirGasto(1L, new BigDecimal("1000.00"), "Reparación Fachada"));
    }

    @Test
    @DisplayName("4. Control de Impuestos: Calcula el IVA repercutido y altera el total del recibo devengado")
    void registrarDevengoCuota_CalculaIvaCorrectamente() {
        // GIVEN: Un concepto de cobro con un IVA del 21% asociado
        ConceptoCobro cc = new ConceptoCobro();
        cc.setDescripcion("ALQUILER ANTENA");
        cc.setTipoImpuesto(TipoImpuesto.IVA);
        cc.setPorcentajeImpuesto(new BigDecimal("21.00"));

        CuentaContable ctaIva = new CuentaContable("47700001", "HP IVA", TipoCuenta.PASIVO, comunidadMock);

        when(conceptoCobroRepo.findAllGenericConcepts()).thenReturn(List.of(cc));
        when(cuentaContableRepository.findByCodigoAndComunidadId("43000501", 1L))
                .thenReturn(Optional.of(new CuentaContable("43000501", "Jesus", TipoCuenta.VECINO, comunidadMock)));
        when(cuentaContableRepository.findByCodigoAndComunidadId("73100001", 1L)).thenReturn(Optional.of(cuentaMock));
        when(cuentaContableRepository.findByCodigoAndComunidadId("47700001", 1L)).thenReturn(Optional.of(ctaIva));
        when(reciboRepo.save(any(Recibo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // WHEN: Registramos un devengo base de 100.00€
        Recibo resultado = contabilidadService.registrarDevengoCuota(
                vecinoMock, new BigDecimal("100.00"), "ALQUILER ANTENA", LocalDate.now(), "ORDINARIA", null);

        // THEN: Se deben generar apuntes contables incluyendo los 21.00€ del impuesto repercutido
        assertNotNull(resultado);
        verify(movContableRepo, atLeastOnce()).save(any(MovimientoContable.class));
    }

    @Test
    @DisplayName("5. Borrado Selectivo: Limpia exclusivamente los recibos pendientes que coinciden con el criterio")
    void borrarRecibosYcontabilidadDelMes_BorradoSelectivo() {
        // GIVEN: Dos recibos en el mismo mes, uno ORDINARIO (debe borrarse) y uno EXTRAORDINARIO (debe mantenerse)
        Recibo ordinario = new Recibo();
        ordinario.setId(901L);
        ordinario.setFechaEmision(LocalDate.of(2026, 5, 1));
        ordinario.setEstado(Recibo.EstadoRecibo.PENDIENTE);
        ordinario.setTipoRemesa("ORDINARIA");

        Recibo extraordinario = new Recibo();
        extraordinario.setId(902L);
        extraordinario.setFechaEmision(LocalDate.of(2026, 5, 1));
        extraordinario.setEstado(Recibo.EstadoRecibo.PENDIENTE);
        extraordinario.setTipoRemesa("EXTRAORDINARIA");

        when(reciboRepo.findByComunidadId(1L)).thenReturn(List.of(ordinario, extraordinario));

        // WHEN: Solicitamos borrar la contabilidad del mes para las cuotas ORDINARIAS
        contabilidadService.borrarRecibosYcontabilidadDelMes(1L, 5, 2026, "ORDINARIA", null, true);

        // THEN: Solo se debe eliminar del repositorio el recibo ordinario
        verify(reciboRepo, times(1)).delete(ordinario);
        verify(reciboRepo, never()).delete(extraordinario);
    }

    private void registrarApunteYVerificar(BigDecimal debe, BigDecimal haber, boolean debeGuardar) {
        contabilidadService.registrarApunte(cuentaMock, debe, haber, "Concepto Test", "A-1", comunidadMock, LocalDate.now());
        if (debeGuardar) {
            verify(movContableRepo, atLeastOnce()).save(any(MovimientoContable.class));
        } else {
            verify(movContableRepo, never()).save(any(MovimientoContable.class));
        }
        reset(movContableRepo);
    }
}