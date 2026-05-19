package com.sepa1914.adminservice.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class CriptoYHardwareUnitTest {

    private AesEncryptor aesEncryptor;

    @BeforeEach
    void setUp() {
        this.aesEncryptor = new AesEncryptor();
    }

    @Test
    @DisplayName("1. EncryptionUtil y AesEncryptor: Cifrado y Descifrado Simétrico")
    void testFlujoCifradoIban() {
        String ibanOriginal = "ES21001590100200089604";

        String cifradoBD = aesEncryptor.convertToDatabaseColumn(ibanOriginal);
        assertNotNull(cifradoBD);
        assertNotEquals(ibanOriginal, cifradoBD);

        String descifradoEntidad = aesEncryptor.convertToEntityAttribute(cifradoBD);
        assertEquals(ibanOriginal, descifradoEntidad);

        String cifradoUtil = EncryptionUtil.encrypt(ibanOriginal);
        assertEquals(ibanOriginal, EncryptionUtil.decrypt(cifradoUtil));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t", "\n"})
    @DisplayName("2. AesEncryptor: Control estricto de nulos y espacios en blanco")
    void testValoresVacios(String valorVacio) {
        assertNull(aesEncryptor.convertToDatabaseColumn(valorVacio));
        assertNull(aesEncryptor.convertToEntityAttribute(valorVacio));
    }

    @Test
    @DisplayName("3. AesEncryptor: Tolerancia si el dato en BD ya está en texto plano")
    void testTextoPlanoResidual() {
        String textoPlano = "ES91 2100 1590 1002 0008 9604";
        String resultado = aesEncryptor.convertToEntityAttribute(textoPlano);
        assertEquals(textoPlano, resultado);
    }

    @Test
    @DisplayName("4. CryptoUtil: Encriptación segura Base64 URL-Safe para códigos QR")
    void testCryptoUtilUrlSafe() throws Exception {
        String idComunidad = "14";
        String tokenCifrado = CryptoUtil.encriptar(idComunidad);

        assertNotNull(tokenCifrado);
        // CORRECCIÓN DEFINITIVA: El estándar Base64 URL-Safe sustituye '+' por '-' y '/' por '_'
        assertFalse(tokenCifrado.contains("+"), "No debe contener el carácter estándar '+'");
        assertFalse(tokenCifrado.contains("/"), "No debe contener el carácter estándar '/'");

        // El signo '=' es un relleno válido en encodeToString de getUrlEncoder, por lo que no lo evaluamos negativamente
    }

    @Test
    @DisplayName("5. HardwareUtil: Generación inmutable de la huella digital del servidor")
    void testHardwareFingerprint() {
        String fingerprint1 = HardwareUtil.getFingerprint();
        String fingerprint2 = HardwareUtil.getFingerprint();

        assertNotNull(fingerprint1);
        assertEquals(16, fingerprint1.length());
        assertEquals(fingerprint1, fingerprint1.toUpperCase());
        assertEquals(fingerprint1, fingerprint2);
        assertNotEquals("ERROR-HARDWARE", fingerprint1);
    }
}