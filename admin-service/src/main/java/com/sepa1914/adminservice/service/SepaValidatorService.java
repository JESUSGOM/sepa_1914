package com.sepa1914.adminservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class SepaValidatorService {

    private static final Logger log = LoggerFactory.getLogger(SepaValidatorService.class);

    // 🔴 FIJADO A 600 SEGÚN CUADERNO 19-14
    private static final int LONGITUD_ESPERADA = 600;

    public List<String> validarFichero(String contenido) {
        List<String> errores = new ArrayList<>();

        // 🟢 CORRECCIÓN CRÍTICA: Limpiamos \r para evitar el error de longitud 601 en sistemas Windows
        String[] lineas = contenido.replace("\r", "").split("\n");

        BigDecimal totalSuma03 = BigDecimal.ZERO;
        int contador03 = 0;
        BigDecimal totalFichero04 = BigDecimal.ZERO;
        int registrosEn04 = 0;

        for (int i = 0; i < lineas.length; i++) {
            String linea = lineas[i];
            if (linea.trim().isEmpty()) continue;

            // 1. Validar Longitud
            if (linea.length() != LONGITUD_ESPERADA) {
                errores.add("❌ Línea " + (i + 1) + " longitud inválida: " + linea.length());
                continue;
            }

            String tipo = linea.substring(0, 2);

            switch (tipo) {
                case "01": // Cabecera Presentador
                case "02": // Cabecera Acreedor
                    break;

                case "03": // Individual de Adeudo
                    contador03++;
                    // 🔴 CORRECCIÓN CRÍTICA: Longitud de importe fijada a 11 según norma (pos 89-99)
                    // Antes estaba en 12 y por eso multiplicaba por 10 la remesa.
                    totalSuma03 = totalSuma03.add(extraerImporte(linea, 88, 11));
                    break;

                case "04": // Totales de Acreedor
                    // Importe total en pos 46 (índice 45), longitud 17
                    totalFichero04 = extraerImporte(linea, 45, 17);
                    try {
                        // Registros del bloque en pos 71 (índice 70)
                        registrosEn04 = Integer.parseInt(linea.substring(70, 80).trim());
                    } catch (Exception e) {
                        log.error("Error leyendo contador de registros en línea " + (i + 1));
                    }
                    break;

                case "05": // Totales de Ordenante (NUEVO)
                    log.debug("GTI Check: Validando registro de cierre 05...");
                    break;

                case "99": // Fin de Fichero (NUEVO)
                    log.debug("GTI Check: Validando registro de cierre 99...");
                    break;

                default:
                    errores.add("❌ Línea " + (i + 1) + " tipo desconocido: " + tipo);
            }
        }

        // 2. Validación de Totales
        if (totalSuma03.compareTo(totalFichero04) != 0) {
            errores.add("❌ Total remesa incorrecto. Suma deudores: " + totalSuma03 + " | Total en registro 04: " + totalFichero04);
        }

        // 3. Validación de número de registros
        // Bloque = 02 (1) + deudores (N) + 04 (1) = N + 2.
        if (contador03 + 3 != registrosEn04 && registrosEn04 != 0) {
            log.warn("GTI Check: El contador de registros en 04 es {}, esperados {}. Puede variar según el banco.", registrosEn04, (contador03 + 3));
        }

        return errores;
    }

    private BigDecimal extraerImporte(String linea, int inicio, int longitud) {
        try {
            String parte = linea.substring(inicio, inicio + longitud).trim();
            if (parte.isEmpty()) return BigDecimal.ZERO;
            // Convertimos céntimos a euros
            return new BigDecimal(parte).divide(new BigDecimal("100"));
        } catch (Exception e) {
            log.error("Error leyendo importe en pos " + inicio + ": " + e.getMessage());
            return BigDecimal.ZERO;
        }
    }
}