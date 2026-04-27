package com.sepa1914.adminservice.controller;

import com.sepa1914.adminservice.model.Comunidad;
import com.sepa1914.adminservice.model.Vecino;
import com.sepa1914.adminservice.model.Recibo; // Añadido para resolver símbolo
import com.sepa1914.adminservice.repository.ComunidadRepository;
import com.sepa1914.adminservice.repository.VecinoRepository;
import com.sepa1914.adminservice.repository.ReciboRepository; // Añadido para resolver símbolo
import com.sepa1914.adminservice.service.ContabilidadService;
import com.sepa1914.adminservice.service.PdfService;
import com.sepa1914.adminservice.dto.BalanceSituacion;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal; // Añadido para resolver símbolo
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Controlador para la gestión y generación de informes PDF de la comunidad.
 * Centraliza la lógica de exportación para asambleas y control administrativo.
 */
@Controller
@RequestMapping("/informes")
public class InformesController {

    @Autowired
    private PdfService pdfService;

    @Autowired
    private ContabilidadService contabilidadService;

    @Autowired
    private ComunidadRepository comunidadRepository;

    @Autowired
    private VecinoRepository vecinoRepository;

    @Autowired
    private ReciboRepository reciboRepository; // Inyectado para cálculos de deuda



    /**
     * Genera el Estado de Cuentas Anual (Balance de Situación + Resumen Gastos/Ingresos)
     */
    @GetMapping("/estado-cuentas/{id}")
    public ResponseEntity<byte[]> generarEstadoCuentas(@PathVariable("id") Long comunidadId) {
        Comunidad comunidad = comunidadRepository.findById(comunidadId)
                .orElseThrow(() -> new RuntimeException("Comunidad no encontrada"));

        // Obtenemos el balance que ya tenemos cuadrado por partida doble
        BalanceSituacion balance = contabilidadService.generarBalance(comunidadId);

        // Preparamos los datos para la plantilla HTML
        Map<String, Object> data = new HashMap<>();
        data.put("comunidad", comunidad);
        data.put("balance", balance);
        data.put("fecha", LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        data.put("anio", LocalDate.now().getYear());

        byte[] pdfBytes = pdfService.generarPdfDesdePlantilla("estado-cuentas", data);

        return crearResponsePdf("Estado_Cuentas_" + comunidad.getNombre() + ".pdf", pdfBytes);
    }

    /**
     * Genera la Convocatoria personalizada para cada vecino con datos dinámicos.
     * Refactorizado para procesar el orden del día desde un área de texto.
     */
    @GetMapping("/convocatoria/{id}")
    public ResponseEntity<byte[]> generarConvocatoria(
            @PathVariable("id") Long comunidadId,
            @RequestParam(value = "fechaJunta", required = false, defaultValue = "TBD") String fechaJunta,
            @RequestParam(value = "lugar", required = false, defaultValue = "Portales de la Comunidad") String lugar,
            @RequestParam(value = "hora1", required = false, defaultValue = "19:00") String hora1,
            @RequestParam(value = "hora2", required = false, defaultValue = "19:30") String hora2,
            @RequestParam(value = "puntosArea", required = false) String puntosArea) {

        // 1. Buscamos la comunidad con validación de existencia
        Comunidad comunidad = comunidadRepository.findById(comunidadId)
                .orElseThrow(() -> new RuntimeException("Comunidad no encontrada con ID: " + comunidadId));

        // 2. Obtenemos la lista de vecinos para la personalización masiva (una carta por folio)
        List<Vecino> vecinos = vecinoRepository.findByComunidadId(comunidadId);

        // 3. Procesamos los puntos del Orden del Día
        List<String> ordenDelDia;
        if (puntosArea != null && !puntosArea.isBlank()) {
            // Dividimos el texto del textarea por saltos de línea para crear la lista
            ordenDelDia = Arrays.asList(puntosArea.split("\\r?\\n"));
        } else {
            // Puntos por defecto si el administrador deja el campo vacío
            ordenDelDia = List.of(
                    "Lectura y aprobación del acta anterior.",
                    "Estado de cuentas y aprobación del ejercicio económico.",
                    "Renovación de cargos directivos.",
                    "Ruegos y preguntas."
            );
        }

        // 4. Preparamos el modelo de datos para Thymeleaf
        Map<String, Object> data = new HashMap<>();
        data.put("comunidad", comunidad);
        data.put("vecinos", vecinos); // Lista completa para el bucle th:each
        data.put("fechaActual", LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")));
        data.put("fechaJunta", fechaJunta);
        data.put("lugarJunta", lugar);
        data.put("hora1", hora1);
        data.put("hora2", hora2);
        data.put("ordenDelDia", ordenDelDia); // Pasamos la lista procesada
        data.put("anio", LocalDate.now().getYear());

        // 5. Generamos el PDF usando el servicio de plantillas
        byte[] pdfBytes = pdfService.generarPdfDesdePlantilla("convocatoria", data);

        // 6. Retornamos el documento para su visualización en el navegador
        return crearResponsePdf("Convocatoria_" + comunidad.getNombre().replace(" ", "_") + ".pdf", pdfBytes);
    }

    /**
     * Genera el listado de liquidación individual para todos los vecinos
     */
    @GetMapping("/liquidacion-individual/{id}")
    public ResponseEntity<byte[]> generarLiquidacionIndividual(@PathVariable("id") Long comunidadId) {
        Comunidad comunidad = comunidadRepository.findById(comunidadId)
                .orElseThrow(() -> new RuntimeException("Comunidad no encontrada")); // Corregida advertencia get()

        List<Vecino> vecinos = vecinoRepository.findByComunidadId(comunidadId);

        // Mapas para la plantilla (recibos y deudas calculadas)
        Map<Long, List<Recibo>> recibosMap = new HashMap<>();
        Map<Long, BigDecimal> saldos = new HashMap<>();

        for (Vecino v : vecinos) {
            // Buscamos todos los recibos del vecino
            List<Recibo> recibosVecino = reciboRepository.findByVecinoId(v.getId());
            recibosMap.put(v.getId(), recibosVecino);

            // Calculamos la deuda sumando Pendientes y Devueltos
            // SOLUCIÓN AL ERROR DE OPERADOR: Usamos Recibo.EstadoRecibo ya que es un Enum interno
            BigDecimal deudaTotal = recibosVecino.stream()
                    .filter(r -> r.getEstado() == Recibo.EstadoRecibo.PENDIENTE ||
                            r.getEstado() == Recibo.EstadoRecibo.DEVUELTO)
                    .map(Recibo::getImporte)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            saldos.put(v.getId(), deudaTotal);

            // OPCIONAL: También podemos cargar los recibos en el objeto de forma temporal
            // v.setRecibos(recibosVecino);
        }

        Map<String, Object> data = new HashMap<>();
        data.put("comunidad", comunidad);
        data.put("vecinos", vecinos);
        data.put("recibosMap", recibosMap); // Mapa de recibos para el HTML
        data.put("saldos", saldos);         // Mapa de saldos para el HTML

        byte[] pdfBytes = pdfService.generarPdfDesdePlantilla("liquidacion-individual", data);
        return crearResponsePdf("Liquidaciones_" + comunidad.getNombre() + ".pdf", pdfBytes);
    }

    /**
     * REFACTORIZADO: Genera certificados de deuda para un vecino específico.
     * Ahora solo requiere el ID del vecino para garantizar concordancia de datos.
     */
    @GetMapping("/certificado-deudas/{id}")
    public ResponseEntity<byte[]> generarCertificadoDeudas(@PathVariable("id") Long vecinoId) {

        // 1. Buscamos al vecino (esto garantiza que el ID sea real)
        Vecino vecino = vecinoRepository.findById(vecinoId)
                .orElseThrow(() -> new RuntimeException("Vecino no encontrado con ID: " + vecinoId));

        // 2. Obtenemos su comunidad directamente de la ficha del vecino
        // Esto soluciona tu error anterior: la comunidad siempre será la del vecino seleccionado
        Comunidad comunidad = vecino.getComunidad();

        // 3. Lógica de Administrador (Basado en tu SQL)
        // Usamos el método que creamos en Comunidad.java para obtener el nombre profesional
        String nombreFirmaAdmin = "EL ADMINISTRADOR";
        if (comunidad.getDatosAdministrador() != null) {
            nombreFirmaAdmin = comunidad.getDatosAdministrador().getNombre();
        }

        // 4. Calculamos la deuda (Recibos PENDIENTES o DEVUELTOS)
        List<Recibo> recibosVecino = reciboRepository.findByVecinoId(vecinoId);
        BigDecimal deudaTotal = recibosVecino != null ? recibosVecino.stream()
                .filter(r -> r.getImporte() != null)
                .filter(r -> r.getEstado() == Recibo.EstadoRecibo.PENDIENTE ||
                        r.getEstado() == Recibo.EstadoRecibo.DEVUELTO)
                .map(Recibo::getImporte)
                .reduce(BigDecimal.ZERO, BigDecimal::add) : BigDecimal.ZERO;

        // 5. Preparar datos para la plantilla HTML
        Map<String, Object> data = new HashMap<>();
        data.put("comunidad", comunidad);
        data.put("vecino", vecino);
        data.put("deudaTotal", deudaTotal);
        data.put("nombreAdministrador", nombreFirmaAdmin); // <--- NUEVO: Para que salga tu nombre real
        data.put("fechaExtensa", LocalDate.now().format(DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.of("es", "ES"))));

        // 6. Generación del PDF mediante el servicio
        byte[] pdfBytes = pdfService.generarPdfDesdePlantilla("certificado-deudas", data);

        // 7. Retorno del archivo con nombre personalizado
        String nombreArchivo = "Certificado_Deuda_" + vecino.getNombre().trim().replace(" ", "_") + ".pdf";
        return crearResponsePdf(nombreArchivo, pdfBytes);
    }

    /**
     * Método auxiliar para configurar las cabeceras de respuesta del navegador
     */
    private ResponseEntity<byte[]> crearResponsePdf(String filename, byte[] contents) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("inline", filename); // "inline" para abrir en el navegador
        headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");

        return ResponseEntity.ok()
                .headers(headers)
                .body(contents);
    }
}