package com.sepa1914.adminservice.service;

import com.sepa1914.adminservice.model.*;
import com.sepa1914.adminservice.repository.ConceptoCobroRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Servicio para la gestión y cálculo de recibos.
 * Crucial para la norma SEPA 19-14: Calcula importes y genera conceptos descriptivos.
 */
@Service
public class ReciboService {

    private static final Logger log = LoggerFactory.getLogger(ReciboService.class);

    @Autowired
    private ConceptoCobroRepository conceptoRepository;

    /**
     * Calcula el importe total a cobrar a una propiedad específica.
     * Utiliza el método de la entidad para asegurar consistencia con la vista.
     * Soporta que un vecino tenga varios IBANs al tratar cada propiedad por separado.
     */
    public BigDecimal calcularTotalVecino(Vecino vecino) {
        if (vecino == null) return BigDecimal.ZERO;

        // Delegamos en la lógica de la entidad para mantener el "Single Source of Truth"
        BigDecimal total = vecino.getImporteTotalConceptos();

        log.debug("Calculando total para {} ({}): {}€",
                vecino.getNombre(), vecino.getVivienda(), total);

        return total;
    }

    /**
     * Genera la descripción compuesta del recibo (Atributo AT-05 / RmtInf).
     * Ejemplo: "CUOTA COMUNIDAD + CONSUMO AGUA"
     * Vital para que el vecino identifique el cargo en su extracto bancario.
     */
    public String generarConceptoRemesa(Vecino vecino) {
        if (vecino == null) return "";

        List<ConceptoCobro> conceptos = conceptoRepository.findByVecino(vecino);
        StringBuilder sb = new StringBuilder();

        // Identificamos la propiedad en el concepto si es necesario para mayor claridad
        sb.append(vecino.getVivienda().toUpperCase()).append(": ");

        boolean tieneConceptos = false;
        for (ConceptoCobro c : conceptos) {
            if (c.isActivo() && c.getImporte().compareTo(BigDecimal.ZERO) > 0) {
                if (tieneConceptos) sb.append(" + ");
                sb.append(c.getDescripcion().toUpperCase());
                tieneConceptos = true;
            }
        }

        if (!tieneConceptos) {
            sb.append("CUOTA PERIODICA");
        }

        String resultado = sb.toString();

        // La norma ISO 20022 / SEPA limita este campo a 140 caracteres
        if (resultado.length() > 140) {
            return resultado.substring(0, 137) + "...";
        }

        return resultado;
    }

    /**
     * Verifica si una propiedad es apta para ser incluida en una remesa.
     * Comprueba IBAN, Mandato y que el importe sea positivo.
     */
    public boolean esAptoParaRemesa(Vecino vecino) {
        if (vecino == null) return false;

        boolean tieneIban = vecino.getIban() != null && !vecino.getIban().isBlank();
        boolean tieneMandato = vecino.getReferenciaMandato() != null && !vecino.getReferenciaMandato().isBlank();
        boolean tieneImporte = calcularTotalVecino(vecino).compareTo(BigDecimal.ZERO) > 0;

        return tieneIban && tieneMandato && tieneImporte;
    }
}