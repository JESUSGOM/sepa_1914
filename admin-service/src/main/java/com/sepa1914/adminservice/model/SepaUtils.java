package com.sepa1914.adminservice.model;

import org.iban4j.IbanUtil;
import org.iban4j.IbanFormatException;
import org.iban4j.InvalidCheckDigitException;
import java.math.BigInteger;

public class SepaUtils {

    /**
     * Genera el Identificador del Acreedor SEPA (AT-02)
     * Mantiene la funcionalidad original de conversión y cálculo MOD 97.
     */
    public static String calcularAT02(String nif, String sufijo) {
        if (nif == null || nif.length() < 9) return nif;

        String limpio = nif.trim().toUpperCase();

        StringBuilder nifNumerico = new StringBuilder();
        for (char c : limpio.toCharArray()) {
            if (Character.isLetter(c)) {
                nifNumerico.append(Character.getNumericValue(c));
            } else {
                nifNumerico.append(c);
            }
        }

        String paraCalculo = nifNumerico.toString() + "142800";

        BigInteger bigInt = new BigInteger(paraCalculo);
        int resto = bigInt.remainder(new BigInteger("97")).intValue();
        int dc = 98 - resto;
        String dcStr = (dc < 10) ? "0" + dc : String.valueOf(dc);

        return "ES" + dcStr + sufijo + limpio;
    }

    /**
     * Valida el formato del CIF (Entidades) o NIF (Personas físicas) español.
     * Ahora detecta automáticamente el tipo de documento por el primer carácter.
     */
    public static boolean esCifValido(String identificador) {
        if (identificador == null || identificador.length() != 9) return false;

        identificador = identificador.trim().toUpperCase();
        char primerChar = identificador.charAt(0);

        // Si empieza por número, validamos como NIF (Persona Física)
        if (Character.isDigit(primerChar)) {
            return validarNIF(identificador);
        }
        // Si empieza por letra, validamos como CIF (Persona Jurídica/Comunidad)
        else {
            return validarCIF(identificador);
        }
    }

    /**
     * Algoritmo de validación para NIF (8 números + 1 letra)
     */
    private static boolean validarNIF(String nif) {
        try {
            String numerosPart = nif.substring(0, 8);
            char letraPropuesta = nif.charAt(8);

            if (!numerosPart.matches("[0-9]{8}")) return false;

            String letrasValidas = "TRWAGMYFPDXBNJZSQVHLCKE";
            int valDni = Integer.parseInt(numerosPart);
            char letraCorrecta = letrasValidas.charAt(valDni % 23);

            return letraPropuesta == letraCorrecta;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Algoritmo de validación para CIF (1 letra + 7 números + 1 control)
     * Mantiene tu lógica original pero encapsulada y protegida.
     */
    private static boolean validarCIF(String cif) {
        try {
            char primeraLetra = cif.charAt(0);
            String numeros = cif.substring(1, 8);
            char digitoControl = cif.charAt(8);

            if (!numeros.matches("[0-9]{7}")) return false;

            int sumaPares = 0;
            int sumaImpares = 0;

            for (int i = 0; i < numeros.length(); i++) {
                int n = Character.getNumericValue(numeros.charAt(i));
                if ((i + 1) % 2 == 0) {
                    sumaPares += n;
                } else {
                    int multi = n * 2;
                    sumaImpares += (multi > 9) ? (multi - 9) : multi;
                }
            }

            int total = sumaPares + sumaImpares;
            int unidad = total % 10;
            int digitoEsperadoNum = (unidad == 0) ? 0 : (10 - unidad);
            char digitoEsperadoLetra = "JABCDEFGHI".charAt(digitoEsperadoNum);

            return digitoControl == Character.forDigit(digitoEsperadoNum, 10) ||
                    digitoControl == digitoEsperadoLetra;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Valida la estructura y el dígito de control de un IBAN utilizando iban4j.
     */
    public static boolean esIbanValido(String iban) {
        if (iban == null || iban.isEmpty()) return false;
        try {
            String limpio = iban.replace(" ", "").toUpperCase();
            IbanUtil.validate(limpio);
            return true;
        } catch (IbanFormatException | InvalidCheckDigitException e) {
            return false;
        }
    }
}