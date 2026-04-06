package com.sepa1914.adminservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.nio.file.*;

@Service
public class StorageService {

    @Value("${storage.location}")
    private String storageLocation;

    public String guardarArchivo(MultipartFile archivo, String nifVecino) throws IOException {
        if (archivo.isEmpty()) throw new IOException("Archivo vacío");

        Path root = Paths.get(storageLocation);
        if (!Files.exists(root)) Files.createDirectories(root);

        // Nombre único: NIF_timestamp_nombreOriginal
        String nombreFichero = nifVecino + "_" + System.currentTimeMillis() + "_" + archivo.getOriginalFilename();
        Files.copy(archivo.getInputStream(), root.resolve(nombreFichero), StandardCopyOption.REPLACE_EXISTING);

        return nombreFichero;
    }
}