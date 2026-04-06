package com.sepa1914.adminservice.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "ficheros_generados")
public class FicheroGenerado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comunidad_id", nullable = false)
    private Comunidad comunidad;

    @Column(name = "identificador_fichero", length = 35, nullable = false)
    private String identificadorFichero;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDate fechaCreacion;

    @Column(name = "total_importe", precision = 17, scale = 2, nullable = false)
    private BigDecimal totalImporte;

    @Column(name = "numero_recibos", nullable = false)
    private Integer numeroRecibos;

    @Column(name = "nombre_archivo")
    private String nombreArchivo;

    @Lob
    @Column(name = "contenido", columnDefinition = "LONGTEXT")
    private String contenido;

    public FicheroGenerado() {
        this.fechaCreacion = LocalDate.now();
    }

    // Getters y Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Comunidad getComunidad() { return comunidad; }
    public void setComunidad(Comunidad comunidad) { this.comunidad = comunidad; }
    public String getIdentificadorFichero() { return identificadorFichero; }
    public void setIdentificadorFichero(String identificadorFichero) { this.identificadorFichero = identificadorFichero; }
    public LocalDate getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDate fechaCreacion) { this.fechaCreacion = fechaCreacion; }
    public BigDecimal getTotalImporte() { return totalImporte; }
    public void setTotalImporte(BigDecimal totalImporte) { this.totalImporte = totalImporte; }
    public Integer getNumeroRecibos() { return numeroRecibos; }
    public void setNumeroRecibos(Integer numeroRecibos) { this.numeroRecibos = numeroRecibos; }
    public String getNombreArchivo() { return nombreArchivo; }
    public void setNombreArchivo(String nombreArchivo) { this.nombreArchivo = nombreArchivo; }
    public String getContenido() { return contenido; }
    public void setContenido(String contenido) { this.contenido = contenido; }
}