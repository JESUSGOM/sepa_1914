package com.sepa1914.adminservice.model;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Entidad Administrador.
 * Representa al profesional que gestiona las comunidades.
 * Contiene la configuración SMTP personalizada para el envío de recibos.
 * SIN LOMBOK - OPTIMIZADA PARA RENDIMIENTO.
 */
@Entity
@Table(name = "administradores")
public class Administrador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre", length = 255)
    private String nombre; // Nombre que aparece en los PDF y como remitente

    @Column(name = "email", length = 255)
    private String email;

    // --- CONFIGURACIÓN SMTP PERSONALIZADA ---

    @Column(name = "smtp_host")
    private String smtpHost;

    @Column(name = "smtp_port")
    private Integer smtpPort;

    @Column(name = "smtp_username")
    private String smtpUsername;

    @Column(name = "smtp_password")
    private String smtpPassword;

    @Column(name = "smtp_auth")
    private boolean smtpAuth = true;

    @Column(name = "smtp_starttls")
    private boolean smtpStarttls = true;

    // --- RELACIONES ---

    /**
     * Relación con las comunidades que gestiona.
     * Se usa LAZY para no sobrecargar la memoria al cargar el admin.
     */
    @OneToMany(mappedBy = "datosAdministrador", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Comunidad> comunidades = new ArrayList<>();

    // --- CONSTRUCTORES ---

    public Administrador() {
    }

    // --- GETTERS Y SETTERS ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getSmtpHost() {
        return smtpHost;
    }

    public void setSmtpHost(String smtpHost) {
        this.smtpHost = smtpHost;
    }

    public Integer getSmtpPort() {
        return smtpPort;
    }

    public void setSmtpPort(Integer smtpPort) {
        this.smtpPort = smtpPort;
    }

    public String getSmtpUsername() {
        return smtpUsername;
    }

    public void setSmtpUsername(String smtpUsername) {
        this.smtpUsername = smtpUsername;
    }

    public String getSmtpPassword() {
        return smtpPassword;
    }

    public void setSmtpPassword(String smtpPassword) {
        this.smtpPassword = smtpPassword;
    }

    public boolean isSmtpAuth() {
        return smtpAuth;
    }

    public void setSmtpAuth(boolean smtpAuth) {
        this.smtpAuth = smtpAuth;
    }

    public boolean isSmtpStarttls() {
        return smtpStarttls;
    }

    public void setSmtpStarttls(boolean smtpStarttls) {
        this.smtpStarttls = smtpStarttls;
    }

    public List<Comunidad> getComunidades() {
        return comunidades;
    }

    public void setComunidades(List<Comunidad> comunidades) {
        this.comunidades = comunidades;
    }

    // --- MÉTODOS DE CONVENIENCIA ---

    public void addComunidad(Comunidad comunidad) {
        comunidades.add(comunidad);
        comunidad.setDatosAdministrador(this);
    }

    // --- MÉTODOS ESTÁNDAR (equals, hashCode, toString) ---

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Administrador that = (Administrador) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Administrador{" +
                "id=" + id +
                ", nombre='" + nombre + '\'' +
                ", email='" + email + '\'' +
                ", smtpHost='" + smtpHost + '\'' +
                '}';
    }
}