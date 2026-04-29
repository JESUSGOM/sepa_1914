package com.sepa1914.adminservice.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "gestion_incidencias")
public class Incidencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String titulo;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @Enumerated(EnumType.STRING)
    private Prioridad prioridad = Prioridad.MEDIA;

    @Enumerated(EnumType.STRING)
    private EstadoIncidencia estado = EstadoIncidencia.PENDIENTE;

    private LocalDateTime fechaRegistro = LocalDateTime.now();

    @Column(precision = 19, scale = 2)
    private BigDecimal costeEstimado;

    // --- CAMBIO CRÍTICO PARA EL REPOSITORIO ---
    // Este campo permite al Repositorio buscar por ID sin errores
    @Column(name = "comunidad_id", insertable = false, updatable = false)
    private Long comunidadId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comunidad_id")
    private Comunidad comunidad;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movimiento_bancario_id")
    private MovimientoBancario pagoAsociado;

    public enum Prioridad { BAJA, MEDIA, ALTA, URGENTE }
    public enum EstadoIncidencia { PENDIENTE, ABIERTA, EN_PROCESO, FINALIZADA, CANCELADA }

    public Incidencia() {}

    // --- SETTER PARA EL LISTENER DE KAFKA ---
    public void setComunidadId(Long comunidadId) {
        this.comunidadId = comunidadId;
        if (comunidadId != null) {
            Comunidad c = new Comunidad();
            c.setId(comunidadId);
            this.comunidad = c;
        }
    }

    // --- RESTO DE GETTERS Y SETTERS ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public Prioridad getPrioridad() { return prioridad; }
    public void setPrioridad(Prioridad prioridad) { this.prioridad = prioridad; }
    public EstadoIncidencia getEstado() { return estado; }
    public void setEstado(EstadoIncidencia estado) { this.estado = estado; }
    public LocalDateTime getFechaRegistro() { return fechaRegistro; }
    public void setFechaRegistro(LocalDateTime fechaRegistro) { this.fechaRegistro = fechaRegistro; }
    public BigDecimal getCosteEstimado() { return costeEstimado; }
    public void setCosteEstimado(BigDecimal costeEstimado) { this.costeEstimado = costeEstimado; }
    public Comunidad getComunidad() { return comunidad; }
    public void setComunidad(Comunidad comunidad) { this.comunidad = comunidad; }
    public Long getComunidadId() { return comunidadId; }
    public MovimientoBancario getPagoAsociado() { return pagoAsociado; }
    public void setPagoAsociado(MovimientoBancario pagoAsociado) { this.pagoAsociado = pagoAsociado; }
}