package com.sepa1914.adminservice.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "licencias_maestras")
public class LicenciaMaestra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hardware_id", unique = true, nullable = false)
    private String hardwareId;

    @Column(name = "nombre_cliente")
    private String nombreCliente;

    @Column(name = "fecha_activacion")
    private LocalDateTime fechaActivacion = LocalDateTime.now();

    private boolean activo = true;

    // Getters y Setters
    public Long getId() { return id; }
    public String getHardwareId() { return hardwareId; }
    public void setHardwareId(String hardwareId) { this.hardwareId = hardwareId; }
    public boolean isActivo() { return activo; }
}