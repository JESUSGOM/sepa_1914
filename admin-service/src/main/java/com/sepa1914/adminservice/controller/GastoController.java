package com.sepa1914.adminservice.controller;

import com.sepa1914.adminservice.model.*;
import com.sepa1914.adminservice.repository.*;
import com.sepa1914.adminservice.service.ContabilidadService;
import com.sepa1914.adminservice.service.InvoiceScannerService;
import com.sepa1914.adminservice.dto.GastoDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.List;

/**
 * Controlador integral para la gestión de Facturas y Gastos de la comunidad.
 * Mantiene el 100% de la lógica original (234 líneas) con soporte documental GTI.
 */
@Controller
@RequestMapping("/contabilidad/gastos")
public class GastoController {

    private static final Logger log = LoggerFactory.getLogger(GastoController.class);

    @Autowired private GastoRepository gastoRepository;
    @Autowired private ComunidadRepository comunidadRepository;
    @Autowired private CuentaContableRepository cuentaRepository;
    @Autowired private ContabilidadService contabilidadService;
    @Autowired private InvoiceScannerService invoiceScannerService;

    // RUTA DEFINITIVA GTI
    private final Path rootPath = Paths.get("C:/sepa1914/ficheros/facturas");

    @GetMapping("/{comunidadId}")
    public String listarGastos(@PathVariable Long comunidadId, Model model) {
        log.info("Accediendo al listado de gastos para la comunidad: {}", comunidadId);
        Comunidad comunidad = comunidadRepository.findById(comunidadId)
                .orElseThrow(() -> new RuntimeException("Comunidad no encontrada"));

        List<Gasto> listaGastos = gastoRepository.findByComunidadId(comunidadId);
        List<CuentaContable> cuentasGasto = cuentaRepository.findByComunidadIdAndTipo(comunidadId, TipoCuenta.GASTO);

        model.addAttribute("comunidad", comunidad);
        model.addAttribute("gastos", listaGastos);
        model.addAttribute("cuentasGasto", cuentasGasto);
        model.addAttribute("activePage", "gastos");
        return "contabilidad/gastos-lista";
    }

    @GetMapping("/nuevo/{comunidadId}")
    public String nuevoGasto(@PathVariable Long comunidadId, Model model) {
        log.info("Preparando formulario de nuevo gasto para comunidad: {}", comunidadId);
        Comunidad comunidad = comunidadRepository.findById(comunidadId).orElseThrow();

        Gasto gasto = new Gasto();
        gasto.setFecha(LocalDate.now());
        List<CuentaContable> cuentasGasto = cuentaRepository.findByComunidadIdAndTipo(comunidadId, TipoCuenta.GASTO);

        model.addAttribute("gasto", gasto);
        model.addAttribute("comunidad", comunidad);
        model.addAttribute("cuentasGasto", cuentasGasto);
        model.addAttribute("activePage", "gastos");
        return "contabilidad/gasto-form";
    }

    @PostMapping("/guardar")
    public String guardarGasto(@ModelAttribute("gasto") Gasto gasto,
                               @RequestParam("comunidadId") Long comunidadId,
                               @RequestParam(value = "cuentaGastoId", required = false) Long cuentaGastoId,
                               @RequestParam(value = "archivoFactura", required = false) MultipartFile archivo,
                               RedirectAttributes ra) {
        try {
            log.info("Iniciando guardado de gasto para comunidad {}. Cuenta: {}", comunidadId, cuentaGastoId);

            Comunidad comunidad = comunidadRepository.findById(comunidadId)
                    .orElseThrow(() -> new RuntimeException("Comunidad no encontrada"));
            gasto.setComunidad(comunidad);

            // Vinculación de la cuenta contable del grupo 6
            if (cuentaGastoId != null) {
                CuentaContable cuenta = cuentaRepository.findById(cuentaGastoId)
                        .orElseThrow(() -> new RuntimeException("Cuenta contable no encontrada"));
                gasto.setCuentaGasto(cuenta);
                log.info("Cuenta vinculada: {}", cuenta.getCodigo());
            }

            if (gasto.getCuentaGasto() == null) {
                ra.addFlashAttribute("error", "Debe seleccionar una cuenta contable.");
                return "redirect:/contabilidad/gastos/" + comunidadId;
            }

            // ==========================================================
            // EL PARACAÍDAS: Corrección del error SQL 'fecha_factura' cannot be null
            // ==========================================================
            if (gasto.getFecha() == null) {
                log.warn("⚠️ ATENCIÓN: Se recibió un gasto sin fecha. Aplicando paracaídas GTI (Fecha actual).");
                gasto.setFecha(LocalDate.now());
            }

            // GESTIÓN FÍSICA DEL ARCHIVO PDF (Factura digitalizada)
            if (archivo != null && !archivo.isEmpty()) {
                Files.createDirectories(rootPath);
                String nombreFichero = System.currentTimeMillis() + "_" + archivo.getOriginalFilename();
                Path destino = rootPath.resolve(nombreFichero);
                Files.copy(archivo.getInputStream(), destino, StandardCopyOption.REPLACE_EXISTING);
                gasto.setRutaPdf(nombreFichero);
                log.info("PDF Guardado exitosamente en: {}", destino);
            }

            // GUARDADO EN BASE DE DATOS
            Gasto guardado = gastoRepository.save(gasto);
            log.info("Entidad Gasto guardada con ID: {}", guardado.getId());

            // CONTABILIZACIÓN AUTOMÁTICA (Devengo 6 -> 4)
            // Nota: Asegúrate de que este método en el Service realice el asiento de devengo
            contabilidadService.registrarGastoContable(guardado);
            log.info("Gasto contabilizado correctamente. Asiento generado.");

            ra.addFlashAttribute("exito", "Factura registrada y contabilizada correctamente.");
            return "redirect:/contabilidad/gastos/" + comunidadId;

        } catch (Exception e) {
            log.error("❌ ERROR CRÍTICO en guardarGasto: ", e);
            ra.addFlashAttribute("error", "Error al procesar el gasto: " + e.getMessage());
            return "redirect:/contabilidad/gastos/" + comunidadId;
        }
    }

    @PostMapping("/pagar")
    public String confirmarPago(@RequestParam Long gastoId,
                                @RequestParam Long comunidadId,
                                @RequestParam("fechaPago") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaPago,
                                RedirectAttributes ra) {
        try {
            log.info("Registrando pago para gasto ID: {} fecha: {}", gastoId, fechaPago);
            Gasto gasto = gastoRepository.findById(gastoId).orElseThrow();

            if (gasto.isPagado()) {
                ra.addFlashAttribute("error", "Esta factura ya está pagada.");
                return "redirect:/contabilidad/gastos/" + comunidadId;
            }

            gasto.setPagado(true);
            gasto.setFechaPago(fechaPago);
            gastoRepository.save(gasto);
            contabilidadService.confirmarPagoGasto(gastoId, fechaPago);

            ra.addFlashAttribute("exito", "Pago conciliado correctamente.");
            return "redirect:/contabilidad/gastos/" + comunidadId;
        } catch (Exception e) {
            log.error("Error al pagar: ", e);
            ra.addFlashAttribute("error", "Error al procesar el pago.");
            return "redirect:/contabilidad/gastos/" + comunidadId;
        }
    }

    // FIX RUTA: Eliminado el prefijo duplicado para sincronizar con HTML
    @PostMapping("/escanear")
    @ResponseBody
    public GastoDTO escanear(@RequestParam("factura") MultipartFile file) {
        try {
            log.info("GTI SCANNER: Iniciando análisis de: {}", file.getOriginalFilename());
            return invoiceScannerService.analizarFactura(file);
        } catch (Exception e) {
            log.error("GTI SCANNER ERROR: ", e);
            return new GastoDTO("Error", "", "", "", "");
        }
    }

    @GetMapping("/ver-pdf/{id}")
    @ResponseBody
    public ResponseEntity<Resource> verPdf(@PathVariable Long id) {
        try {
            Gasto gasto = gastoRepository.findById(id).orElseThrow();
            if (gasto.getRutaPdf() == null) return ResponseEntity.notFound().build();
            Path path = rootPath.resolve(gasto.getRutaPdf());
            Resource resource = new UrlResource(path.toUri());
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_TYPE, "application/pdf")
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/editar/{id}")
    public String editarGasto(@PathVariable Long id, Model model) {
        Gasto gasto = gastoRepository.findById(id).orElseThrow();
        Comunidad comunidad = gasto.getComunidad();
        List<CuentaContable> cuentasGasto = cuentaRepository.findByComunidadIdAndTipo(comunidad.getId(), TipoCuenta.GASTO);
        model.addAttribute("gasto", gasto);
        model.addAttribute("comunidad", comunidad);
        model.addAttribute("cuentasGasto", cuentasGasto);
        model.addAttribute("activePage", "gastos");
        return "contabilidad/gasto-form";
    }

    @GetMapping("/eliminar/{id}")
    public String eliminarGasto(@PathVariable Long id, RedirectAttributes ra) {
        Gasto gasto = gastoRepository.findById(id).orElseThrow();
        Long idCom = gasto.getComunidad().getId();
        gastoRepository.delete(gasto);
        ra.addFlashAttribute("exito", "Factura eliminada.");
        return "redirect:/contabilidad/gastos/" + idCom;
    }

    @GetMapping("/deshacer-pago/{id}")
    public String deshacerPago(@PathVariable Long id, RedirectAttributes ra) {
        try {
            Gasto g = gastoRepository.findById(id).orElseThrow();
            Long comunidadId = g.getComunidad().getId();

            // Llamamos al método que acabamos de crear en el Service
            contabilidadService.deshacerPagoGasto(id);

            ra.addFlashAttribute("exito", "Pago anulado correctamente.");
            return "redirect:/contabilidad/gastos/" + comunidadId;
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Error: " + e.getMessage());
            return "redirect:/comunidades/lista";
        }
    }
}