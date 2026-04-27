package com.sepa1914.adminservice.service;

import org.springframework.stereotype.Service;

import java.util.Random;

@Service
public class SepaTestService {

    public String romperFichero(String contenido) {

        String[] lineas = contenido.split("\\r?\\n");

        Random r = new Random();

        int linea = r.nextInt(lineas.length);

        // 🔴 Rompe longitud
        if (lineas[linea].length() > 10) {
            lineas[linea] = lineas[linea].substring(0, lineas[linea].length() - 5);
        }

        // 🔴 Rompe importe
        if (lineas[linea].startsWith("03")) {
            lineas[linea] = lineas[linea].replaceFirst("\\d{5}", "XXXXX");
        }

        return String.join("\n", lineas);
    }
}