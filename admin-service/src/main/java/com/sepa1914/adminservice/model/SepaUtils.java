package com.sepa1914.adminservice.model;

import java.math.BigInteger;

public class SepaUtils {

    public static String calcularAT02(String nif, String sufijo) {
        if (nif == null || nif.length() < 9) return nif;

        // Limpiamos el NIF de espacios o guiones
        String limpio = nif.trim().toUpperCase();

        // El algoritmo SEPA requiere convertir letras a números
        // para el cálculo del dígito de control (A=10, B=11...)
        StringBuilder nifNumerico = new StringBuilder();
        for (char c : limpio.toCharArray()) {
            if (Character.isLetter(c)) {
                nifNumerico.append(Character.getNumericValue(c));
            } else {
                nifNumerico.append(c);
            }
        }

        // Añadimos el código de país (ES = 1428) y el 00 para el cálculo
        String paraCalculo = nifNumerico.toString() + "142800";

        // Cálculo del Dígito de Control (MOD 97-10)
        BigInteger bigInt = new BigInteger(paraCalculo);
        int resto = bigInt.remainder(new BigInteger("97")).intValue();
        int dc = 98 - resto;
        String dcStr = (dc < 10) ? "0" + dc : String.valueOf(dc);

        // Formato final: ES + DC + Sufijo (3 caracteres) + NIF original
        // Ejemplo: ES + 92 + 000 + G12345678
        return "ES" + dcStr + sufijo + limpio;
    }
}