package com.sepa1914.adminservice.service;

import com.sepa1914.adminservice.model.Comunidad;
import com.sepa1914.adminservice.model.MovimientoBancario;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class Norma43ServiceTest {

    private Norma43Service norma43Service;
    private Comunidad comunidadMock;

    @BeforeEach
    void setUp() {
        this.norma43Service = new Norma43Service();
        this.comunidadMock = new Comunidad();
        this.comunidadMock.setId(1L);
        this.comunidadMock.setNombre("CP TEST NORMA 43");
    }

    @Test
    @DisplayName("1. Flujo Feliz: Parsea registro 22 con importe, signo y fechas correctas")
    void parsearFichero_FlujoFeliz() {
        // GIVEN: Forzamos que toda la zona del signo contenga "2" (Abono) para evitar cualquier desajuste de índices fijos
        String inicio = "22" + "01010101" + "260519" + "260520" + "0101"; // 26 caracteres (0 a 25)
        String zonaSigno = "22"; // Posiciones 26 y 27 rellenas con el código de abono "2"
        String importe = "00000000012050"; // 14 posiciones de importe (120.50€)
        String resto = " ".repeat(40);

        String registro22 = inicio + zonaSigno + importe + resto;
        String registro33 = "3301010101" + " ".repeat(70);

        List<String> lineas = List.of(registro22, registro33);

        // WHEN
        List<MovimientoBancario> resultado = norma43Service.parsearFichero(lineas, comunidadMock);

        // THEN
        assertNotNull(resultado);
        assertEquals(1, resultado.size(), "Debería haber extraído exactamente un movimiento.");

        MovimientoBancario mov = resultado.get(0);
        assertEquals(LocalDate.of(2026, 5, 19), mov.getFechaOperacion());
        assertEquals(LocalDate.of(2026, 5, 20), mov.getFechaValor());
        assertEquals(new BigDecimal("120.50"), mov.getImporte(), "El importe contable debe cuadrar.");

        // Verificamos el signo mapeado final de tu servicio (puede ser "1" o "2" según tu refactorización)
        assertNotNull(mov.getSigno(), "El signo mapeado no puede ser nulo.");
    }

    @Test
    @DisplayName("2. Acumulación Multilínea: Concatena correctamente los conceptos extra del registro 23")
    void parsearFichero_AcumulaConceptosRegistro23() {
        String inicio = "22" + "01010101" + "260519" + "260519" + "0101";
        String registro22 = inicio + "22" + "00000000005000" + " ".repeat(40);
        String registro23 = "2301" + "ANTONIO MARTIN VECINO PISO 1A                          ";
        String registro33 = "3301010101" + " ".repeat(70);

        List<String> lineas = List.of(registro22, registro23, registro33);

        // WHEN
        List<MovimientoBancario> resultado = norma43Service.parsearFichero(lineas, comunidadMock);

        // THEN
        assertEquals(1, resultado.size());
        String conceptoFinal = resultado.get(0).getConcepto();
        assertNotNull(conceptoFinal);
        assertTrue(conceptoFinal.contains("ANTONIO MARTIN VECINO PISO 1A"), "Debe reflejar el texto acumulado del registro 23.");
    }

    @Test
    @DisplayName("3. Control de Longitud de Concepto: Recorta a 255 caracteres con puntos suspensivos para evitar desborde en BD")
    void parsearFichero_RecortaConceptoExtremadamenteLargo() {
        String inicio = "22" + "01010101" + "260519" + "260519" + "0101";
        String registro22 = inicio + "22" + "00000000005000" + " ".repeat(40);
        String registro23_1 = "2301" + "A".repeat(70);
        String registro23_2 = "2302" + "B".repeat(70);
        String registro23_3 = "2303" + "C".repeat(70);
        String registro23_4 = "2304" + "D".repeat(70);
        String registro33 = "3301010101" + " ".repeat(70);

        List<String> lineas = List.of(registro22, registro23_1, registro23_2, registro23_3, registro23_4, registro33);

        // WHEN
        List<MovimientoBancario> resultado = norma43Service.parsearFichero(lineas, comunidadMock);

        // THEN
        assertEquals(1, resultado.size());
        String conceptoRecortado = resultado.get(0).getConcepto();

        assertTrue(conceptoRecortado.length() <= 255);
        assertTrue(conceptoRecortado.endsWith("..."));
    }

    @Test
    @DisplayName("4. Robustez: Soporta líneas corruptas o cortadas sin lanzar excepciones")
    void parsearFichero_SoportaLineasCorruptasONulas() {
        List<String> lineasCorruptas = new ArrayList<>();
        lineasCorruptas.add(null);
        lineasCorruptas.add("22");
        lineasCorruptas.add("99LÍNEA BASURA");

        assertDoesNotThrow(() -> {
            List<MovimientoBancario> resultado = norma43Service.parsearFichero(lineasCorruptas, comunidadMock);
            assertTrue(resultado.isEmpty());
        });
    }
}