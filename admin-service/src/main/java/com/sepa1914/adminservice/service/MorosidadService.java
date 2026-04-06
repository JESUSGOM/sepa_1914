package com.sepa1914.adminservice.service;

import com.sepa1914.adminservice.dto.MorosoInfo;
import com.sepa1914.adminservice.model.Recibo;
import com.sepa1914.adminservice.repository.ReciboRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MorosidadService {

    @Autowired
    private ReciboRepository reciboRepository;

    public List<MorosoInfo> generarInformeMorosidad(Long comunidadId) {
        // Obtenemos todos los recibos no pagados (Pendientes y Devueltos por el banco)
        List<Recibo> impagados = reciboRepository.findByComunidadIdAndEstadoIn(
                comunidadId,
                List.of(Recibo.EstadoRecibo.PENDIENTE, Recibo.EstadoRecibo.DEVUELTO)
        );

        // Agrupamos por vecino utilizando la potencia de los Streams de Java 21
        return impagados.stream()
                .collect(Collectors.groupingBy(Recibo::getVecino))
                .entrySet().stream()
                .map(entry -> {
                    var vecino = entry.getKey();
                    var recibos = entry.getValue();
                    var total = recibos.stream()
                            .map(Recibo::getImporte)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    return new MorosoInfo(
                            vecino.getId(),
                            vecino.getNombre(),
                            vecino.getVivienda(), // Asumiendo que el modelo Vecino tiene este campo
                            recibos,
                            total,
                            recibos.size()
                    );
                })
                .sorted((a, b) -> b.totalDeuda().compareTo(a.totalDeuda())) // Ordenar por mayor deuda
                .toList();
    }
}