package com.sepa1914.adminservice.util;

import java.net.NetworkInterface;
import java.net.SocketException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Enumeration;

public class HardwareUtil {

    public static String getFingerprint() {
        StringBuilder sb = new StringBuilder();

        try {
            // 1- Recoger todas las direcciones MAC físicas
            // Corregido: Se añade gestión de SocketException
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface ni = networkInterfaces.nextElement();
                byte[] mac = ni.getHardwareAddress();
                if (mac != null) {
                    for (byte b : mac) {
                        sb.append(String.format("%02X:", b));
                    }
                }
            }

            // 2- Añadir info del procesador y arquitectura
            sb.append(System.getProperty("os.arch"));
            sb.append(System.getProperty("os.name"));
            // Corregido: El método correcto es availableProcessors (con 'l')
            sb.append(Runtime.getRuntime().availableProcessors());

            // 3- Hashear todo con SHA-256
            // Corregido: Es MessageDigest (sin 'a') y se gestiona NoSuchAlgorithmException
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(sb.toString().getBytes());

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02X", b));
            }

            // Devolvemos los primeros 16 caracteres
            // Corregido: Se quita .toString() redundante porque StringBuilder ya tiene .substring()
            return hexString.substring(0, 16).toUpperCase();

        } catch (SocketException | NoSuchAlgorithmException e) {
            // Si algo falla, devolvemos un ID genérico para que la App no se caiga
            return "ERROR-HARDWARE-ID";
        }
    }
}