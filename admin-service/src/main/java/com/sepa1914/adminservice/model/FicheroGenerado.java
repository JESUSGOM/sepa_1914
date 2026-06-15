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

    @Column(name = "estado", length = 30, nullable = false)
    private String estado = "GENERADA";

    @Column(name = "tipo_remesa", length = 30)
    private String tipoRemesa = "ORDINARIA";

    @Column(name = "fecha_cobro")
    private LocalDate fechaCobro;

    @Column(name = "esquema_sepa", length = 10)
    private String esquemaSepa = "CORE";

    @Column(name = "total_domiciliado", precision = 17, scale = 2)
    private BigDecimal totalDomiciliado = BigDecimal.ZERO;

    @Column(name = "total_no_domiciliado", precision = 17, scale = 2)
    private BigDecimal totalNoDomiciliado = BigDecimal.ZERO;

    @Lob
    @Column(name = "observaciones", columnDefinition = "TEXT")
    private String observaciones;

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
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public String getTipoRemesa() { return tipoRemesa; }
    public void setTipoRemesa(String tipoRemesa) { this.tipoRemesa = tipoRemesa; }
    public LocalDate getFechaCobro() { return fechaCobro; }
    public void setFechaCobro(LocalDate fechaCobro) { this.fechaCobro = fechaCobro; }
    public String getEsquemaSepa() { return esquemaSepa; }
    public void setEsquemaSepa(String esquemaSepa) { this.esquemaSepa = esquemaSepa; }
    public BigDecimal getTotalDomiciliado() { return totalDomiciliado; }
    public void setTotalDomiciliado(BigDecimal totalDomiciliado) { this.totalDomiciliado = totalDomiciliado; }
    public BigDecimal getTotalNoDomiciliado() { return totalNoDomiciliado; }
    public void setTotalNoDomiciliado(BigDecimal totalNoDomiciliado) { this.totalNoDomiciliado = totalNoDomiciliado; }
    public String getObservaciones() { return observaciones; }
    public void setObservaciones(String observaciones) { this.observaciones = observaciones; }
    public String getContenido() { return contenido; }
    public void setContenido(String contenido) { this.contenido = contenido; }
}