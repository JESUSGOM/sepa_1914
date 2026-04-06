package com.sepa1914.adminservice.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;

/**
 * Entidad para almacenar la configuración de rutas locales del sistema.
 * Sincronizada con la base de datos remota en Dinahosting.
 */
@Entity
@Table(name = "configuracion_rutas")
public class ConfiguracionRutas {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * IMPORTANTE: Se añade @Column(name = "rutac19") para coincidir con el SQL.
     * Sin esto, Hibernate buscaría 'ruta_c19' y lanzaría un error en producción.
     */
    @NotBlank(message = "La ruta para los ficheros C19 es obligatoria")
    @Column(name = "rutac19")
    private String rutaC19;

    /**
     * Sincronizado con la columna 'ruta_pdf' de la base de datos.
     */
    @NotBlank(message = "La ruta para los informes PDF es obligatoria")
    @Column(name = "ruta_pdf")
    private String rutaPdf;

    /**
     * Cada administrador (Usuario) tiene su propia configuración de carpetas locales.
     */
    @OneToOne
    @JoinColumn(name = "usuario_id", unique = true)
    private Usuario administrador;

    // Constructor vacío requerido por JPA
    public ConfiguracionRutas() {
    }

    // Constructor con parámetros para facilitar el uso en el código
    public ConfiguracionRutas(String rutaC19, String rutaPdf, Usuario administrador) {
        this.rutaC19 = rutaC19;
        this.rutaPdf = rutaPdf;
        this.administrador = administrador;
    }

    // --- GETTERS Y SETTERS ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getRutaC19() { return rutaC19; }
    public void setRutaC19(String rutaC19) { this.rutaC19 = rutaC19; }

    public String getRutaPdf() { return rutaPdf; }
    public void setRutaPdf(String rutaPdf) { this.rutaPdf = rutaPdf; }

    public Usuario getAdministrador() { return administrador; }
    public void setAdministrador(Usuario administrador) { this.administrador = administrador; }

    @Override
    public String toString() {
        return "ConfiguracionRutas{" +
                "id=" + id +
                ", administrador=" + (administrador != null ? administrador.getUsername() : "null") +
                ", rutaC19='" + rutaC19 + '\'' +
                ", rutaPdf='" + rutaPdf + '\'' +
                '}';
    }
}