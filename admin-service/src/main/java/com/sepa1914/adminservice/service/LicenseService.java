package com.sepa1914.adminservice.service;

import com.sepa1914.adminservice.model.LicenciaMaestra;
import com.sepa1914.adminservice.repository.LicenciaMaestraRepository;
import com.sepa1914.adminservice.util.HardwareUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
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

    // --- SISTEMA DE CACHÉ GTI (Aceleración Máxima) ---
    private static boolean cacheValida = true; // Inicializamos en true para permitir el primer arranque instantáneo
    private static LocalDateTime fechaProximaValidacion = null;
    private final int MINUTOS_CACHE = 30;

    /**
     * MÉTODO DE ENTRADA (INSTANTÁNEO)
     * No bloquea el hilo principal. Devuelve el último estado conocido de inmediato.
     */
    public boolean validarLicencia() {
        // Si no hemos validado nunca o la caché ha expirado...
        if (fechaProximaValidacion == null || LocalDateTime.now().isAfter(fechaProximaValidacion)) {
            // Disparamos la validación pesada EN SEGUNDO PLANO
            // Usamos un hilo aparte para que el usuario no espere los 1.9s
            ejecutarValidacionEnSegundoPlano();

            // Si es la primera vez (null), establecemos una fecha provisional para no saturar a hilos
            if (fechaProximaValidacion == null) {
                fechaProximaValidacion = LocalDateTime.now().plusSeconds(30);
            }
        }

        // Devolvemos el estado de la caché de inmediato (0 segundos de espera)
        return cacheValida;
    }

    /**
     * MÉTODO ASÍNCRONO (EL QUE TARDA)
     * Se ejecuta en un hilo de "task-executor".
     */
    @Async
    public void ejecutarValidacionEnSegundoPlano() {
        try {
            String hid = getEquipoID();
            log.info("🌐 [GTI BACKGROUND] Iniciando consulta de licencia en segundo plano...");

            // 1. Check Local
            Optional<LicenciaMaestra> licenciaLocal = licenciaMaestraRepository.findByHardwareIdAndActivoTrue(hid);
            if (licenciaLocal.isPresent()) {
                log.info("✅ [GTI] Acceso local verificado.");
                actualizarCache(true);
                return;
            }

            // 2. Check Remoto (jfgb.es) - Aquí es donde se perdían los 2 segundos
            String urlCheck = API_URL + "?hid=" + hid;
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(urlCheck, Map.class);

            if (response != null && Boolean.TRUE.equals(response.get("activo"))) {
                log.info("✅ [GTI] Licencia remota validada correctamente.");
                actualizarCache(true);
            } else {
                log.warn("❌ [GTI] El servidor remoto indica licencia inactiva.");
                actualizarCache(false);
            }

        } catch (Exception e) {
            log.error("⚠️ [GTI] Error en validación asíncrona: {}. Se mantiene estado previo.", e.getMessage());
            // En caso de error de red, mantenemos la caché como válida para no bloquear al cliente
            fechaProximaValidacion = LocalDateTime.now().plusMinutes(5); // Reintentar en 5 min
        }
    }

    private void actualizarCache(boolean estado) {
        cacheValida = estado;
        fechaProximaValidacion = LocalDateTime.now().plusMinutes(MINUTOS_CACHE);
    }

    public String getEquipoID() {
        return HardwareUtil.getFingerprint();
    }
}