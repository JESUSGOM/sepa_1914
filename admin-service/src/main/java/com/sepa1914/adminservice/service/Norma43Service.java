package com.sepa1914.adminservice.service;

import com.sepa1914.adminservice.model.MovimientoBancario;
import com.sepa1914.adminservice.model.Comunidad;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Servicio avanzado para el procesamiento de ficheros Norma 43.
 * OPTIMIZACIÓN: Captura completa de conceptos multilínea (Registros 23).
 */
@Service
public class Norma43Service {

    private static final DateTimeFormatter DF = DateTimeFormatter.ofPattern("yyMMdd");

    public List<MovimientoBancario> parsearFichero(List<String> lineas, Comunidad comunidad) {
        List<MovimientoBancario> lista = new ArrayList<>();
        MovimientoBancario actual = null;
        StringBuilder conceptoAcumulado = new StringBuilder();

        for (String linea : lineas) {
            if (linea == null || linea.length() < 10) continue;

            String tipo = linea.substring(0, 2);

            switch (tipo) {
                case "22": // --- INICIO DE MOVIMIENTO ---
                    // Si ya teníamos uno a medias, lo guardamos antes de empezar el nuevo
                    if (actual != null) {
                        actual.setConcepto(limpiarConcepto(conceptoAcumulado.toString()));
                        lista.add(actual);
                    }

                    actual = new MovimientoBancario();
                    actual.setComunidad(comunidad);
                    actual.setConciliado(false);
                    conceptoAcumulado = new StringBuilder();

                    // Fechas (AAMMDD)
                    actual.setFechaOperacion(LocalDate.parse(linea.substring(10, 16), DF));
                    actual.setFechaValor(LocalDate.parse(linea.substring(16, 22), DF));

                    // Signo e Importe (Pos 28-42)
                    actual.setSigno(linea.substring(27, 28));
                    String importeStr = linea.substring(28, 42).trim();
                    actual.setImporte(new BigDecimal(importeStr).movePointLeft(2));

                    // Concepto inicial del registro 22 (pos 52-80)
                    if (linea.length() >= 80) {
                        String conceptoBase = linea.substring(52, 80).trim();
                        conceptoAcumulado.append(conceptoBase).append(" ");
                    }
                    break;

                case "23": // --- LÍNEAS DE DETALLE (Conceptos extra) ---
                    if (actual != null && linea.length() >= 4) {
                        // El dato útil en el registro 23 va desde la pos 4 hasta la 80
                        String detalleExtra = linea.substring(4).trim();
                        if (!detalleExtra.isEmpty()) {
                            conceptoAcumulado.append(detalleExtra).append(" ");
                        }
                    }
                    break;

                case "33": // --- FIN DE CUENTA O CIERRE ---
                    if (actual != null) {
                        actual.setConcepto(limpiarConcepto(conceptoAcumulado.toString()));
                        lista.add(actual);
                        actual = null;
                    }
                    break;
            }
        }

        // Caso de seguridad por si el fichero no termina en registro 33/80
        if (actual != null && !lista.contains(actual)) {
            actual.setConcepto(limpiarConcepto(conceptoAcumulado.toString()));
            lista.add(actual);
        }

        return lista;
    }

    /**
     * Limpia el texto acumulado eliminando espacios dobles y formateando el resultado final.
     */
    private String limpiarConcepto(String texto) {
        if (texto == null) return "";
        // Reemplaza múltiples espacios por uno solo y recorta los extremos
        String limpio = texto.replaceAll("\\s+", " ").trim().toUpperCase();

        // Si el concepto es demasiado largo para tu base de datos (ej: 255 caracteres), lo cortamos
        if (limpio.length() > 255) {
            limpio = limpio.substring(0, 252) + "...";
        }
        return limpio;
    }
}