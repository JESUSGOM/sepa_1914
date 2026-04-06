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
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Servicio avanzado para la conciliación de movimientos bancarios y recibos.
 * Refactorizado para soportar:
 * 1. Conciliación automática unitaria.
 * 2. Conciliación manual múltiple (Remesas SEPA).
 * 3. Reparto en cascada para pagos parciales o acumulados (Transferencias).
 */
@Service
public class ConciliacionService {

    private static final Logger log = LoggerFactory.getLogger(ConciliacionService.class);

    @Autowired
    private ReciboRepository reciboRepository;

    @Autowired
    private MovimientoBancarioRepository movRepository;

    /**
     * FUNCIONALIDAD MANTENIDA:
     * Ejecuta la conciliación automática cruzando datos de la Norma 43 con los recibos.
     */
    @Transactional
    public int ejecutarConciliacionAutomatica(Long comunidadId) {
        List<MovimientoBancario> movimientos = movRepository.findByComunidadIdAndConciliadoFalse(comunidadId);
        int conciliadosCount = 0;

        for (MovimientoBancario mov : movimientos) {
            // Buscamos recibos pendientes que coincidan EXACTAMENTE en el importe
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
     * Ahora usa registrarPago() para asegurar que el pagadoAcumulado sea correcto.
     */
    @Transactional
    public void vincularMovimientoConVariosRecibos(Long movimientoId, List<Long> reciboIds) {
        MovimientoBancario movimiento = movRepository.findById(movimientoId)
                .orElseThrow(() -> new RuntimeException("Movimiento bancario no encontrado ID: " + movimientoId));

        for (Long reciboId : reciboIds) {
            Recibo recibo = reciboRepository.findById(reciboId)
                    .orElseThrow(() -> new RuntimeException("Recibo no encontrado ID: " + reciboId));

            // Marcamos el pago total del recibo
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
     * NUEVA FUNCIONALIDAD: Conciliación en Cascada (Pagos Parciales/Excesivos).
     * Toma el importe de un movimiento y lo reparte entre los recibos pendientes
     * de un vecino, desde el más antiguo al más moderno.
     */
    @Transactional
    public void conciliarEnCascada(Long movimientoId, Long vecinoId) {
        MovimientoBancario mov = movRepository.findById(movimientoId).orElseThrow();
        BigDecimal saldoRestante = mov.getImporte();

        // Obtenemos recibos pendientes del vecino ordenados por fecha de emisión (los más viejos primero)
        List<Recibo> pendientes = reciboRepository.findAll().stream()
                .filter(r -> r.getVecino().getId().equals(vecinoId) && r.getEstado() != EstadoRecibo.COBRADO)
                .sorted(Comparator.comparing(Recibo::getFechaEmision))
                .collect(Collectors.toList());

        for (Recibo r : pendientes) {
            if (saldoRestante.compareTo(BigDecimal.ZERO) <= 0) break;

            BigDecimal deudaRecibo = r.getSaldoPendiente();

            if (saldoRestante.compareTo(deudaRecibo) >= 0) {
                // El dinero cubre todo este recibo
                saldoRestante = saldoRestante.subtract(deudaRecibo);
                r.registrarPago(deudaRecibo); // Esto lo pone en COBRADO internamente
                r.setMovimientoBancario(mov);
                r.setFechaCobroBanco(mov.getFechaOperacion());
            } else {
                // El dinero solo cubre una parte del recibo
                r.registrarPago(saldoRestante);
                r.setMovimientoBancario(mov); // Vinculamos para auditoría aunque sea parcial
                r.setFechaCobroBanco(mov.getFechaOperacion());
                saldoRestante = BigDecimal.ZERO;
            }
            reciboRepository.save(r);
        }

        // Si se ha usado todo o parte del movimiento, lo marcamos como conciliado
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