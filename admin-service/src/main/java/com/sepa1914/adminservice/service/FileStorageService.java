package com.sepa1914.adminservice.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;

@Service
public class FileStorageService {

    private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

    /**
     * Guarda un archivo en la ruta base especificada, organizándolo en subcarpetas /YYYY/MM/
     */
    public void guardarArchivoAutomatico(String rutaBase, String nombreArchivo, byte[] contenido) throws IOException {
        // 1. Validación de entrada y limpieza de ruta
        if (rutaBase == null || rutaBase.trim().isEmpty()) {
            log.warn("Operación abortada: La ruta base para el archivo '{}' es nula o está vacía", nombreArchivo);
            return;
        }

        // Limpiamos espacios y normalizamos la ruta base para el SO actual
        String rutaLimpia = rutaBase.trim();

        // 2. Definición de la estructura temporal (Año y Mes)
        LocalDate hoy = LocalDate.now();
        String anio = String.valueOf(hoy.getYear());
        String mes = String.format("%02d", hoy.getMonthValue());

        // 3. Construcción de la ruta jerárquica (Usa Paths.get para manejar separadores / o \ automáticamente)
        Path rutaDirectorio = Paths.get(rutaLimpia, anio, mes);

        try {
            // 4. Creación recursiva de directorios si no existen
            if (!Files.exists(rutaDirectorio)) {
                Files.createDirectories(rutaDirectorio);
                log.info("Estructura de directorios creada: {}", rutaDirectorio.toAbsolutePath());
            }

            // 5. Verificación de permisos de escritura (Crítico para unidades de red W:)
            if (!Files.isWritable(rutaDirectorio)) {
                log.error("ERROR DE PERMISOS: El sistema no tiene permisos de escritura en la ruta: {}", rutaDirectorio.toAbsolutePath());
                throw new IOException("Sin permisos de escritura en el destino: " + rutaDirectorio);
            }

            // 6. Resolución de la ruta del archivo final
            Path rutaArchivoFinal = rutaDirectorio.resolve(nombreArchivo);

            // 7. Escritura física del archivo (Uso de StandardOpenOption para mayor seguridad)
            Files.write(rutaArchivoFinal, contenido);

            log.info(">>> ÉXITO: Archivo SEPA guardado correctamente en: {}", rutaArchivoFinal.toAbsolutePath());

        } catch (IOException e) {
            log.error("Fallo técnico al intentar escribir en el disco: {}", e.getMessage());
            // Re-lanzamos la excepción para que el controlador pueda informar al usuario si es necesario
            throw e;
        }
    }
}