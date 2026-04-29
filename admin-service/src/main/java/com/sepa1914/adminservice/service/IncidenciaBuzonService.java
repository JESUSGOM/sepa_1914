package com.sepa1914.adminservice.service;

import com.sepa1914.adminservice.model.Incidencia;
import com.sepa1914.adminservice.repository.IncidenciaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class IncidenciaBuzonService {

    private static final Logger log = LoggerFactory.getLogger(IncidenciaBuzonService.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private IncidenciaRepository incidenciaRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    /**
     * Revisa la tabla 'cola_incidencias' en Dinaserver cada 5 segundos.
     * Si hay datos, los mueve a la tabla definitiva y avisa por WebSocket.
     */
    @Scheduled(fixedDelay = 5000)
    public void revisarBuzonIncidencias() {
        String sqlSelect = "SELECT * FROM cola_incidencias";
        List<Map<String, Object>> incidenciasNuevas = jdbcTemplate.queryForList(sqlSelect);

        if (incidenciasNuevas.isEmpty()) return;

        log.info("📩 Detectadas {} incidencias nuevas en el buzón web.", incidenciasNuevas.size());

        for (Map<String, Object> datos : incidenciasNuevas) {
            // Procesamos cada una de forma independiente para que si una falla, las demás sigan
            try {
                guardarIncidenciaIndividual(datos);
            } catch (Exception e) {
                log.error("❌ Error crítico procesando ID {}: {}", datos.get("id"), e.getMessage());
            }
        }
    }

    @Transactional
    public void guardarIncidenciaIndividual(Map<String, Object> datos) {
        Incidencia nueva = new Incidencia();
        nueva.setComunidadId(((Number) datos.get("comunidad_id")).longValue());
        nueva.setTitulo((String) datos.get("titulo"));
        nueva.setDescripcion((String) datos.get("descripcion"));

        // Aseguramos que el texto coincida con el Enum
        String prio = (String) datos.get("prioridad");
        nueva.setPrioridad(Incidencia.Prioridad.valueOf(prio.toUpperCase()));

        // ESTADO QUE DABA EL ERROR: Ahora la BD lo aceptará tras el ALTER TABLE
        nueva.setEstado(Incidencia.EstadoIncidencia.PENDIENTE);

        incidenciaRepository.save(nueva);

        // Borramos del buzón temporal
        jdbcTemplate.update("DELETE FROM cola_incidencias WHERE id = ?", datos.get("id"));

        // Notificamos por WebSocket
        messagingTemplate.convertAndSend("/topic/incidencias", nueva);
    }
}