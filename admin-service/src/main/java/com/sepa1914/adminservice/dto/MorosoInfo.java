package com.sepa1914.adminservice.dto;

import java.math.BigDecimal;
import java.util.List;
import com.sepa1914.adminservice.model.Recibo;

public record MorosoInfo(
        Long vecinoId,
        String nombreVecino,
        String vivienda,
        List<Recibo> recibosPendientes,
        BigDecimal totalDeuda,
        int numeroRecibos
) {}