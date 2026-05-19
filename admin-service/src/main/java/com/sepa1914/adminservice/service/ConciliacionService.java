package com.sepa1914.adminservice.service;

import com.sepa1914.adminservice.model.MovimientoBancario;
import com.sepa1914.adminservice.model.Recibo;
import com.sepa1914.adminservice.model.Recibo.EstadoRecibo;
import com.sepa1914.adminservice.repository.MovimientoBancarioRepository;
import com.sepa1914.adminservice.repository.ReciboRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Servicio avanzado para la conciliación de movimientos bancarios y recibos.
 * Refactorizado para soportar:
 * 1. Conciliación automática unitaria.
 * 2. Conciliación manual múltiple (Remesas SEPA).
 * 3. Reparto en cascada para pagos parciales o acumulados (Transferencias).
 * 4. NUEVO: Agrupación de múltiples apuntes bancarios (Remesas fragmentadas).
 */
@Service
public class ConciliacionService {

    private static final Logger log = LoggerFactory.getLogger(ConciliacionService.class);

    @Autowired
    private ReciboRepository reciboRepository;

    @Autowired
    private MovimientoBancarioRepository movRepository;

    /**
     * NUEVO MÉTODO GTI: Obtiene los recibos pendientes o devueltos de una comunidad.
     */
    public List<Recibo> obtenerRecibosPendientes(Long comunidadId) {
        return reciboRepository.findByComunidadIdAndEstadoIn(
                comunidadId, List.of(EstadoRecibo.PENDIENTE, EstadoRecibo.DEVUELTO));
    }

    /**
     * FUNCIONALIDAD MANTENIDA:
     * Ejecuta la conciliación automática cruzando datos de la Norma 43 con los recibos.
     */
    @Transactional
    public int ejecutarConciliacionAutomatica(Long comunidadId) {
        List<MovimientoBancario> movimientos = movRepository.findByComunidadIdAndConciliadoFalse(comunidadId);
        int conciliadosCount = 0;

        for (MovimientoBancario mov : movimientos) {
            List<Recibo> candidatos = reciboRepository.findByComunidadIdAndImporteAndEstado(
                    comunidadId, mov.getImporte(), EstadoRecibo.PENDIENTE);

            if (candidatos.size() == 1) {
                vincular(mov, candidatos.get(0));
                conciliadosCount++;
            }
            else if (candidatos.size() > 1) {
                for (Recibo r : candidatos) {
                    if (mov.getConcepto() != null && r.getVecino() != null &&
                            mov.getConcepto().toUpperCase().contains(r.getVecino().getNombre().toUpperCase())) {
                        vincular(mov, r);
                        conciliadosCount++;
                        break;
                    }
                }
            }
        }
        return conciliadosCount;
    }

    /**
     * FUNCIONALIDAD MANTENIDA Y MEJORADA: Conciliación Múltiple (Manual).
     * Vincula un único movimiento (ej: abono remesa 487,68€) con varios recibos.
     */
    @Transactional
    public void vincularMovimientoConVariosRecibos(Long movimientoId, List<Long> reciboIds) {
        MovimientoBancario movimiento = movRepository.findById(movimientoId)
                .orElseThrow(() -> new RuntimeException("Movimiento bancario no encontrado ID: " + movimientoId));

        for (Long reciboId : reciboIds) {
            Recibo recibo = reciboRepository.findById(reciboId)
                    .orElseThrow(() -> new RuntimeException("Recibo no encontrado ID: " + reciboId));

            recibo.setPagadoAcumulado(recibo.getImporte());
            recibo.setEstado(EstadoRecibo.COBRADO);
            recibo.setFechaCobroBanco(movimiento.getFechaOperacion());
            recibo.setMovimientoBancario(movimiento);
            reciboRepository.save(recibo);
        }

        movimiento.setConciliado(true);
        movRepository.save(movimiento);
    }

    /**
     * NUEVO MÉTODO GTI MAESTRO: Conciliación de Múltiples Movimientos Fragmentados por el Banco.
     * Consolida una lista de apuntes bancarios y los aplica contra una lista de recibos elegidos.
     */
    @Transactional
    public void vincularMultiplesMovimientosConVariosRecibos(List<Long> movimientoIds, List<Long> reciboIds) {
        List<MovimientoBancario> movimientos = movRepository.findAllById(movimientoIds);
        if (movimientos.isEmpty()) {
            throw new RuntimeException("No se han localizado los movimientos bancarios seleccionados.");
        }

        // Establecemos la fecha oficial de cobro basada en la fecha del apunte más reciente del grupo
        LocalDate fechaCobro = movimientos.stream()
                .map(MovimientoBancario::getFechaOperacion)
                .max(LocalDate::compareTo)
                .orElse(LocalDate.now());

        // El primer apunte actúa como nexo de auditoría principal en la ficha de los recibos
        MovimientoBancario movPrincipal = movimientos.get(0);

        for (Long reciboId : reciboIds) {
            Recibo recibo = reciboRepository.findById(reciboId)
                    .orElseThrow(() -> new RuntimeException("Recibo no encontrado ID: " + reciboId));

            recibo.setPagadoAcumulado(recibo.getImporte());
            recibo.setEstado(EstadoRecibo.COBRADO);
            recibo.setFechaCobroBanco(fechaCobro);
            recibo.setMovimientoBancario(movPrincipal);
            reciboRepository.save(recibo);
        }

        // Marcamos de forma colectiva todos los movimientos bancarios elegidos como conciliados
        for (MovimientoBancario mov : movimientos) {
            mov.setConciliado(true);
            movRepository.save(mov);
        }
    }

    /**
     * CONCILIACIÓN EN CASCADA (Pagos Parciales/Excesivos).
     */
    @Transactional
    public void conciliarEnCascada(Long movimientoId, Long vecinoId) {
        MovimientoBancario mov = movRepository.findById(movimientoId).orElseThrow();
        BigDecimal saldoRestante = mov.getImporte();

        List<Recibo> pendientes = reciboRepository.findAll().stream()
                .filter(r -> r.getVecino().getId().equals(vecinoId) && r.getEstado() != EstadoRecibo.COBRADO)
                .sorted(Comparator.comparing(Recibo::getFechaEmision))
                .collect(Collectors.toList());

        for (Recibo r : pendientes) {
            if (saldoRestante.compareTo(BigDecimal.ZERO) <= 0) break;

            BigDecimal deudaRecibo = r.getSaldoPendiente();

            if (saldoRestante.compareTo(deudaRecibo) >= 0) {
                saldoRestante = saldoRestante.subtract(deudaRecibo);
                r.registrarPago(deudaRecibo);
                r.setMovimientoBancario(mov);
                r.setFechaCobroBanco(mov.getFechaOperacion());
            } else {
                r.registrarPago(saldoRestante);
                r.setMovimientoBancario(mov);
                r.setFechaCobroBanco(mov.getFechaOperacion());
                saldoRestante = BigDecimal.ZERO;
            }
            reciboRepository.save(r);
        }

        mov.setConciliado(true);
        movRepository.save(mov);

        log.info("Conciliación en cascada finalizada para vecino {}. Sobrante: {}", vecinoId, saldoRestante);
    }

    /**
     * Realiza el vínculo técnico y contable unitario.
     */
    private void vincular(MovimientoBancario mov, Recibo rec) {
        mov.setConciliado(true);
        rec.setPagadoAcumulado(rec.getImporte());
        rec.setEstado(EstadoRecibo.COBRADO);
        rec.setFechaCobroBanco(mov.getFechaOperacion());
        rec.setMovimientoBancario(mov);

        movRepository.save(mov);
        reciboRepository.save(rec);
    }
}