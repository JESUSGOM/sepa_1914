package com.sepa1914.adminservice.util;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

public class CryptoUtil {
    private static final String ALGORITHM = "AES";
    private static final String KEY = "GTI_Turbo_Stable_Secret_Key_2026"; // 32 caracteres para AES-256

    public static String encriptar(String data) throws Exception {
        SecretKeySpec secretKey = new SecretKeySpec(KEY.getBytes(), ALGORITHM);
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey);
        byte[] encrypted = cipher.doFinal(data.getBytes());
        // Hacerlo seguro para URL (Base64 URL Safe)
        return Base64.getUrlEncoder().encodeToString(encrypted);
    }
}

// Uso al generar el QR:
// String token = CryptoUtil.encriptar("14");
// URL final: https://www.jfgb.es/incidenciascomunidad?t=TOKEN_ENCRIPTADO