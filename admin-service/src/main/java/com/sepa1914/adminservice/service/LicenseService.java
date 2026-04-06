package com.sepa1914.adminservice.service;

import com.sepa1914.adminservice.model.LicenciaMaestra;
import com.sepa1914.adminservice.repository.LicenciaMaestraRepository;
import com.sepa1914.adminservice.util.HardwareUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * Servicio de Licenciamiento Híbrido SEPA 1914.
 * 1. Verifica contra la tabla local 'licencias_maestras'.
 * 2. Si falla, verifica contra el servidor remoto jfgb.es.
 */
@Service
public class LicenseService {

    private static final Logger log = LoggerFactory.getLogger(LicenseService.class);
    private final RestTemplate restTemplate = new RestTemplate();

    @Autowired
    private LicenciaMaestraRepository licenciaMaestraRepository;

    // URL de tu servidor de control remoto
    private final String API_URL = "https://jfgb.es/sepa/validar_licencia.php";

    // Variables de control para permitir uso offline (Periodo de gracia)
    private static boolean ultimaValidacionOk = false;
    private static LocalDateTime fechaUltimoCheck = null;

    /**
     * Lógica de validación Maestra + Remota.
     * No se elimina ninguna funcionalidad, se añade la capa de base de datos local.
     */
    public boolean validarLicencia() {
        String hid = getEquipoID();
        log.info("Iniciando validación de seguridad para Hardware ID: {}", hid);

        // --- PASO 1: VALIDACIÓN LOCAL (MAESTRA) ---
        // Buscamos si el ID de este PC está en tu tabla local y está marcado como activo
        Optional<LicenciaMaestra> licenciaLocal = licenciaMaestraRepository.findByHardwareIdAndActivoTrue(hid);

        if (licenciaLocal.isPresent()) {
            log.info("Acceso concedido mediante Licencia Maestra Local para: {}", hid);
            ultimaValidacionOk = true;
            return true;
        }

        // --- PASO 2: VALIDACIÓN REMOTA (API PHP) ---
        try {
            log.info("No hay licencia maestra local. Consultando servidor remoto...");
            String urlCheck = API_URL + "?hid=" + hid;

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(urlCheck, Map.class);

            if (response != null && Boolean.TRUE.equals(response.get("activo"))) {
                log.info("Licencia validada correctamente en servidor remoto jfgb.es");
                ultimaValidacionOk = true;
                fechaUltimoCheck = LocalDateTime.now();
                return true;
            }

            log.warn("El servidor remoto rechazó la licencia para el equipo {}", hid);
            ultimaValidacionOk = false;
            return false;

        } catch (Exception e) {
            log.error("Error de conexión con el servidor de licencias: {}", e.getMessage());

            // Lógica de gracia: Si validó correctamente hace menos de 24h, permitimos entrar aunque no haya internet
            if (ultimaValidacionOk && fechaUltimoCheck != null &&
                    fechaUltimoCheck.isAfter(LocalDateTime.now().minusHours(24))) {
                log.info("Servidor remoto caído, pero se permite acceso por periodo de gracia (24h).");
                return true;
            }

            return false;
        }
    }

    /**
     * Mantiene la compatibilidad con tu utilidad de hardware.
     */
    public String getEquipoID() {
        return HardwareUtil.getFingerprint();
    }
}