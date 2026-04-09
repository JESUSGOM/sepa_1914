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

@Service
public class LicenseService {

    private static final Logger log = LoggerFactory.getLogger(LicenseService.class);
    private final RestTemplate restTemplate = new RestTemplate();

    @Autowired
    private LicenciaMaestraRepository licenciaMaestraRepository;

    private final String API_URL = "https://jfgb.es/sepa/validar_licencia.php";

    // --- SISTEMA DE CACHÉ GTI (Aceleración) ---
    private static boolean cacheValida = false;
    private static LocalDateTime fechaProximaValidacion = null;
    private final int MINUTOS_CACHE = 30; // El sistema solo "llamará a casa" cada 30 min.

    public boolean validarLicencia() {
        // 1. COMPROBACIÓN DE CACHÉ (Impacto inmediato en velocidad)
        if (cacheValida && fechaProximaValidacion != null && LocalDateTime.now().isBefore(fechaProximaValidacion)) {
            // Si ya validamos hace poco, no perdemos tiempo en consultas ni red
            return true;
        }

        String hid = getEquipoID();
        log.info("🔍 Ejecutando validación de seguridad completa (Caché expirada o primer inicio)...");

        // --- PASO 1: VALIDACIÓN LOCAL (MAESTRA) ---
        Optional<LicenciaMaestra> licenciaLocal = licenciaMaestraRepository.findByHardwareIdAndActivoTrue(hid);

        if (licenciaLocal.isPresent()) {
            log.info("✅ Acceso local concedido para: {}", hid);
            actualizarCache(true);
            return true;
        }

        // --- PASO 2: VALIDACIÓN REMOTA (API PHP) ---
        try {
            log.info("🌐 Consultando servidor remoto jfgb.es...");
            String urlCheck = API_URL + "?hid=" + hid;

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(urlCheck, Map.class);

            if (response != null && Boolean.TRUE.equals(response.get("activo"))) {
                log.info("✅ Licencia validada remotamente.");
                actualizarCache(true);
                return true;
            }

            log.warn("❌ Licencia rechazada por el servidor.");
            actualizarCache(false);
            return false;

        } catch (Exception e) {
            log.error("⚠️ Error de conexión: {}", e.getMessage());

            // Lógica de gracia: Si antes funcionó, damos 24h de margen sin internet
            if (cacheValida) {
                log.info("⏳ Usando periodo de gracia por fallo de red.");
                return true;
            }
            return false;
        }
    }

    private void actualizarCache(boolean estado) {
        cacheValida = estado;
        // Establecemos cuándo será la próxima vez que obligaremos a validar de verdad
        fechaProximaValidacion = LocalDateTime.now().plusMinutes(MINUTOS_CACHE);
    }

    public String getEquipoID() {
        return HardwareUtil.getFingerprint();
    }
}