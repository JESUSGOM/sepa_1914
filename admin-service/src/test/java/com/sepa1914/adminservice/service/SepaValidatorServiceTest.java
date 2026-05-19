package com.sepa1914.adminservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SepaValidatorServiceTest {

    private SepaValidatorService sepaValidatorService;

    @BeforeEach
    void setUp() {
        // Inicialización limpia aislada
        this.sepaValidatorService = new SepaValidatorService();
    }

    @Test
    @DisplayName("1. Flujo Feliz: Cuaderno SEPA válido de 600 posiciones con importes correctos")
    void validarFichero_FlujoFeliz() {
        // GIVEN: Un lote mock estructurado con líneas exactas de 600 caracteres
        // Registro 03: Posición 88 (longitud 11) -> 00000012050 (120.50€)
        // Registro 04: Posición 45 (longitud 17) -> 00000000000012050 (120.50€), Posición 70 -> 0000000004
        String linea01 = "0119154001" + " ".repeat(590);
        String linea02 = "0219154002" + " ".repeat(590);
        String linea03 = "0319154003" + " ".repeat(78) + "00000012050" + " ".repeat(501);
        String linea04 = "04ES60000" + " ".repeat(36) + "00000000000012050" + " ".repeat(8) + "0000000004" + " ".repeat(520);

        String contenidoFichero = linea01 + "\n" + linea02 + "\n" + linea03 + "\n" + linea04;

        // WHEN
        List<String> errores = sepaValidatorService.validarFichero(contenidoFichero);

        // THEN
        assertTrue(errores.isEmpty(), "Un fichero con formato correcto no debería arrojar errores: " + errores);
    }

    @Test
    @DisplayName("2. Robustez: Limpieza de retornos de carro de Windows (\\r\\n) para evitar descuadres de longitud")
    void validarFichero_SoportaSaltosDeLineaWindows() {
        // GIVEN: Línea válida terminada con el retorno de carro \r nativo de Windows antes del \n
        String lineaValora = "0119154001" + " ".repeat(590);
        String contenidoConCarriageReturn = lineaValora + "\r\n" + lineaValora + "\r\n";

        // WHEN
        List<String> errores = sepaValidatorService.validarFichero(contenidoConCarriageReturn);

        // THEN: El validador debe filtrar los \r y evaluar únicamente las 600 posiciones reales
        // Nota: Saldrá un error de totales, pero NO de longitud de línea (601)
        boolean tieneErrorDeLongitud = errores.stream().anyMatch(e -> e.contains("longitud inválida"));
        assertFalse(tieneErrorDeLongitud, "El sistema no debe fallar por la presencia de caracteres ocultos \\r.");
    }

    @Test
    @DisplayName("3. Detección de Errores: Fallo si la suma del registro 03 no cuadra con el total del registro 04")
    void validarFichero_DetectaDescuadreDeImportes() {
        // GIVEN: Registro 03 tiene 120.50€ pero el totalizador del registro 04 declara 500.00€
        String linea01 = "0119154001" + " ".repeat(590);
        String linea02 = "0219154002" + " ".repeat(590);
        String linea03 = "0319154003" + " ".repeat(78) + "00000012050" + " ".repeat(501);
        String linea04 = "04ES60000" + " ".repeat(45) + "00000000000050000" + " ".repeat(8) + "0000000004" + " ".repeat(520);

        String contenidoFichero = linea01 + "\n" + linea02 + "\n" + linea03 + "\n" + linea04;

        // WHEN
        List<String> errores = sepaValidatorService.validarFichero(contenidoFichero);

        // THEN
        assertFalse(errores.isEmpty(), "Debería saltar una alerta indicando el descuadre de importes.");
        boolean detectaTotalIncorrecto = errores.stream().anyMatch(e -> e.contains("Total remesa incorrecto"));
        assertTrue(detectaTotalIncorrecto, "El mensaje de error específico de totales no se ha generado.");
    }

    @Test
    @DisplayName("4. Protección de Longitud: Rechaza líneas corruptas que no midan exactamente 600")
    void validarFichero_DetectaLineaConLongitudInvalida() {
        // GIVEN: Una línea de cabecera recortada que solo mide 400 caracteres
        String lineaCorrupta = "0119154001" + " ".repeat(390);

        // WHEN
        List<String> errores = sepaValidatorService.validarFichero(lineaCorrupta);

        // THEN
        assertFalse(errores.isEmpty());
        assertTrue(errores.get(0).contains("longitud inválida"), "Debería notificar el fallo de longitud fija.");
    }
}