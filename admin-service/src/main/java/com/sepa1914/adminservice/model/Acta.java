package com.sepa1914.adminservice.model;

import com.sepa1914.adminservice.util.AesEncryptor;
import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Entidad para la gestión de Actas de reuniones.
 * El contenido está encriptado en base de datos.
 * Vinculada a una Comunidad para garantizar el aislamiento de datos por administrador.
 */
@Entity
@Table(name = "actas")
public class Acta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comunidad_id", nullable = false)
    private Comunidad comunidad;

    @Column(nullable = false)
    private String titulo;

    @Column(name = "fecha_reunion", nullable = false)
    private LocalDate fechaReunion;

    @Convert(converter = AesEncryptor.class)
    @Column(columnDefinition = "LONGTEXT", nullable = false)
    private String contenido;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private EstadoActa estado = EstadoActa.BORRADOR;

    @Column(name = "ruta_pdf")
    private String rutaPdf;

    @Column(name = "token_presidente", length = 100)
    private String tokenPresidente;

    // --- CONSTRUCTORES ---

    public Acta() {
    }

    public Acta(Comunidad comunidad, String titulo, LocalDate fechaReunion, String contenido) {
        this.comunidad = comunidad;
        this.titulo = titulo;
        this.fechaReunion = fechaReunion;
        this.contenido = contenido;
        this.estado = EstadoActa.BORRADOR;
    }

    // --- GETTERS Y SETTERS ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Comunidad getComunidad() {
        return comunidad;
    }

    public void setComunidad(Comunidad comunidad) {
        this.comunidad = comunidad;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public LocalDate getFechaReunion() {
        return fechaReunion;
    }

    public void setFechaReunion(LocalDate fechaReunion) {
        this.fechaReunion = fechaReunion;
    }

    public String getContenido() {
        return contenido;
    }

    public void setContenido(String contenido) {
        this.contenido = contenido;
    }

    public EstadoActa getEstado() {
        return estado;
    }

    public void setEstado(EstadoActa estado) {
        this.estado = estado;
    }

    public String getRutaPdf() {
        return rutaPdf;
    }

    public void setRutaPdf(String rutaPdf) {
        this.rutaPdf = rutaPdf;
    }

    public String getTokenPresidente() {
        return tokenPresidente;
    }

    public void setTokenPresidente(String tokenPresidente) {
        this.tokenPresidente = tokenPresidente;
    }

    // --- MÉTODOS ESTÁNDAR (Equals, HashCode y ToString) ---

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Acta acta = (Acta) o;
        return Objects.equals(id, acta.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Acta{" +
                "id=" + id +
                ", titulo='" + titulo + '\'' +
                ", fechaReunion=" + fechaReunion +
                ", estado=" + estado +
                '}';
    }
}