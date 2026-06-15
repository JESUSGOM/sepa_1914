package com.sepa1914.adminservice.service;

import com.sepa1914.adminservice.model.Comunidad;
import com.sepa1914.adminservice.model.FicheroGenerado;
import com.sepa1914.adminservice.model.RemesaLinea;
import com.sepa1914.adminservice.repository.ComunidadRepository;
import com.sepa1914.adminservice.repository.FicheroGeneradoRepository;
import com.sepa1914.adminservice.repository.RemesaLineaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class RemesaService {

    private final FicheroGeneradoRepository ficheroGeneradoRepository;
    private final ComunidadRepository comunidadRepository;
    private final RemesaLineaRepository remesaLineaRepository;

    public RemesaService(FicheroGeneradoRepository ficheroGeneradoRepository,
                         ComunidadRepository comunidadRepository,
                         RemesaLineaRepository remesaLineaRepository) {
        this.ficheroGeneradoRepository = ficheroGeneradoRepository;
        this.comunidadRepository = comunidadRepository;
        this.remesaLineaRepository = remesaLineaRepository;
    }

    @Transactional
    public FicheroGenerado crearCabeceraRemesa(Long comunidadId,
                                               String tipoRemesa,
                                               LocalDate fechaCobro,
                                               String esquemaSepa) {

        Comunidad comunidad = comunidadRepository.findById(comunidadId)
                .orElseThrow(() -> new IllegalArgumentException("No existe la comunidad con id: " + comunidadId));

        FicheroGenerado remesa = new FicheroGenerado();

        remesa.setComunidad(comunidad);
        remesa.setIdentificadorFichero(generarIdentificadorFichero(comunidadId));
        remesa.setFechaCreacion(LocalDate.now());
        remesa.setFechaCobro(fechaCobro);

        remesa.setEstado("BORRADOR");
        remesa.setTipoRemesa(tipoRemesa != null ? tipoRemesa : "ORDINARIA");
        remesa.setEsquemaSepa(esquemaSepa != null ? esquemaSepa : "CORE");

        remesa.setTotalImporte(BigDecimal.ZERO);
        remesa.setTotalDomiciliado(BigDecimal.ZERO);
        remesa.setTotalNoDomiciliado(BigDecimal.ZERO);
        remesa.setNumeroRecibos(0);

        return ficheroGeneradoRepository.save(remesa);
    }

    @Transactional
    public void recalcularTotalesRemesa(Long remesaId) {
        FicheroGenerado remesa = ficheroGeneradoRepository.findById(remesaId)
                .orElseThrow(() -> new IllegalArgumentException("No existe la remesa con id: " + remesaId));

        List<RemesaLinea> lineas = remesaLineaRepository.findByRemesaId(remesaId);

        BigDecimal total = BigDecimal.ZERO;
        BigDecimal totalDomiciliado = BigDecimal.ZERO;
        BigDecimal totalNoDomiciliado = BigDecimal.ZERO;

        for (RemesaLinea linea : lineas) {
            BigDecimal importe = linea.getImporte() != null ? linea.getImporte() : BigDecimal.ZERO;

            total = total.add(importe);

            if (Boolean.TRUE.equals(linea.getDomiciliado())) {
                totalDomiciliado = totalDomiciliado.add(importe);
            } else {
                totalNoDomiciliado = totalNoDomiciliado.add(importe);
            }
        }

        remesa.setTotalImporte(total);
        remesa.setTotalDomiciliado(totalDomiciliado);
        remesa.setTotalNoDomiciliado(totalNoDomiciliado);
        remesa.setNumeroRecibos(lineas.size());

        ficheroGeneradoRepository.save(remesa);
    }

    private String generarIdentificadorFichero(Long comunidadId) {
        return "REM-" + comunidadId + "-" + System.currentTimeMillis();
    }
}