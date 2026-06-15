package com.sepa1914.adminservice.service;

import com.sepa1914.adminservice.model.LicenciaMaestra;
import com.sepa1914.adminservice.repository.LicenciaMaestraRepository;
import com.sepa1914.adminservice.util.HardwareUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Service
public class LicenseService {

    private static final Logger log = LoggerFactory.getLogger(LicenseService.class);

    private static final String API_URL = "https://jfgb.es/sepa/validar_licencia.php";

    private final RestTemplate restTemplate = new RestTemplate();
    private final LicenciaMaestraRepository licenciaMaestraRepository;

    private static volatile boolean cacheValida = false;
    private static volatile boolean validacionEnCurso = false;
    private static volatile LocalDateTime fechaProximaValidacion = null;

    private static final int MINUTOS_CACHE_OK = 30;
    private static final int MINUTOS_REINTENTO_ERROR = 5;
    private static final int SEGUNDOS_PRIMER_ARRANQUE = 30;

    public LicenseService(LicenciaMaestraRepository licenciaMaestraRepository) {
        this.licenciaMaestraRepository = licenciaMaestraRepository;
    }

    /**
     * Método rápido usado por controladores y vistas.
     * Devuelve el último estado conocido y lanza validación en segundo plano cuando toca.
     */
    public boolean validarLicencia() {
        if (debeValidarAhora() && !validacionEnCurso) {
            ejecutarValidacionEnSegundoPlano();

            if (fechaProximaValidacion == null) {
                fechaProximaValidacion = LocalDateTime.now().plusSeconds(SEGUNDOS_PRIMER_ARRANQUE);
            }
        }

        return cacheValida;
    }

    @Async
    public void ejecutarValidacionEnSegundoPlano() {
        if (validacionEnCurso) {
            return;
        }

        validacionEnCurso = true;

        try {
            String hid = getEquipoID();

            log.info("🌐 [GTI BACKGROUND] Iniciando consulta de licencia para equipo [{}]...", hid);

            Optional<LicenciaMaestra> licenciaLocal =
                    licenciaMaestraRepository.findByHardwareIdAndActivoTrue(hid);

            if (licenciaLocal.isPresent()) {
                log.info("✅ [GTI] Licencia local activa para equipo [{}].", hid);
                actualizarCache(true, MINUTOS_CACHE_OK);
                return;
            }

            String urlCheck = API_URL + "?hid=" + hid;

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.getForObject(urlCheck, Map.class);

            boolean licenciaActiva = response != null && Boolean.TRUE.equals(response.get("activo"));

            if (licenciaActiva) {
                log.info("✅ [GTI] Licencia remota activa para equipo [{}].", hid);
                actualizarCache(true, MINUTOS_CACHE_OK);
            } else {
                log.warn("❌ [GTI] Licencia inactiva para equipo [{}].", hid);
                actualizarCache(false, MINUTOS_CACHE_OK);
            }

        } catch (Exception e) {
            log.error("⚠️ [GTI] Error validando licencia: {}. Se mantiene el estado previo.", e.getMessage());
            fechaProximaValidacion = LocalDateTime.now().plusMinutes(MINUTOS_REINTENTO_ERROR);
        } finally {
            validacionEnCurso = false;
        }
    }

    private boolean debeValidarAhora() {
        return fechaProximaValidacion == null
                || LocalDateTime.now().isAfter(fechaProximaValidacion);
    }

    private void actualizarCache(boolean estado, int minutos) {
        cacheValida = estado;
        fechaProximaValidacion = LocalDateTime.now().plusMinutes(minutos);
    }

    public String getEquipoID() {
        return HardwareUtil.getFingerprint();
    }

    public boolean isCacheValida() {
        return cacheValida;
    }
}