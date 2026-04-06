package com.sepa1914.adminservice.service;

import com.sepa1914.adminservice.dto.DashboardStats;
import com.sepa1914.adminservice.model.MovimientoBancario;
import com.sepa1914.adminservice.model.Recibo;
import com.sepa1914.adminservice.repository.MovimientoBancarioRepository;
import com.sepa1914.adminservice.repository.ReciboRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    @Autowired
    private MovimientoBancarioRepository movRepo;

    @Autowired
    private ReciboRepository reciboRepo;

    /**
     * Genera las métricas financieras de una comunidad utilizando Records de Java 21.
     */
    public DashboardStats obtenerMetricas(Long comunidadId) {
        LocalDate ahora = LocalDate.now();
        List<MovimientoBancario> todosLosMovimientos = movRepo.findByComunidadId(comunidadId);

        // 1. Calcular Ingresos del Mes (Signo 2 en Norma 43)
        BigDecimal ingresosMes = todosLosMovimientos.stream()
                .filter(m -> "2".equals(m.getSigno()) && m.getFechaOperacion().getMonth() == ahora.getMonth())
                .map(MovimientoBancario::getImporte)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 2. Calcular Gastos del Mes (Signo 1 en Norma 43)
        BigDecimal gastosMes = todosLosMovimientos.stream()
                .filter(m -> "1".equals(m.getSigno()) && m.getFechaOperacion().getMonth() == ahora.getMonth())
                .map(MovimientoBancario::getImporte)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 3. Contadores de recibos (Usando el nuevo sistema de estados del Recibo)
        long pendientes = reciboRepo.countByComunidadIdAndEstado(comunidadId, Recibo.EstadoRecibo.PENDIENTE);
        long impagados = reciboRepo.countByComunidadIdAndEstado(comunidadId, Recibo.EstadoRecibo.DEVUELTO);

        // 4. Calcular Saldo Actual (Suma total de ingresos - suma total de gastos)
        BigDecimal saldoActual = todosLosMovimientos.stream()
                .map(m -> "2".equals(m.getSigno()) ? m.getImporte() : m.getImporte().negate())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 5. Distribución de Gastos (Agrupamos por los primeros 10 caracteres del concepto para el gráfico)
        Map<String, BigDecimal> distribucion = todosLosMovimientos.stream()
                .filter(m -> "1".equals(m.getSigno()))
                .collect(Collectors.groupingBy(
                        m -> m.getConcepto().length() > 15 ? m.getConcepto().substring(0, 15) : m.getConcepto(),
                        Collectors.reducing(BigDecimal.ZERO, MovimientoBancario::getImporte, BigDecimal::add)
                ));

        // Retornamos el Record (Constructor compacto de Java 21)
        return new DashboardStats(
                ingresosMes,
                gastosMes,
                pendientes,
                impagados,
                saldoActual,
                distribucion
        );
    }
}