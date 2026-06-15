package com.sepa1914.adminservice.model;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Entidad Administrador.
 * Representa al profesional que gestiona comunidades.
 *
 * Incluye:
 * - configuración SMTP;
 * - datos SEPA;
 * - cuentas profesionales de presentación.
 */
@Entity
@Table(name = "administradores")
public class Administrador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // =====================================================
    // DATOS BÁSICOS
    // =====================================================

    @Column(name = "nombre", length = 255)
    private String nombre;

    @Column(name = "email", length = 255)
    private String email;

    // =====================================================
    // DATOS SEPA BÁSICOS
    // =====================================================

    @Column(name = "nif_cif", length = 20)
    private String nifCif;

    @Column(name = "sufijo", length = 3)
    private String sufijo;

    @Column(name = "iban", length = 34)
    private String iban;

    @Column(name = "bic", length = 11)
    private String bic;

    @Column(name = "direccion", length = 100)
    private String direccion;

    @Column(name = "poblacion", length = 50)
    private String poblacion;

    @Column(name = "provincia", length = 40)
    private String provincia;

    @Column(name = "pais_cod", length = 2)
    private String paisCod = "ES";

    // =====================================================
    // SMTP
    // =====================================================

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

    // =====================================================
    // RELACIONES
    // =====================================================

    /**
     * Comunidades gestionadas por el administrador.
     */
    @OneToMany(
            mappedBy = "datosAdministrador",
            cascade = CascadeType.ALL,
            fetch = FetchType.LAZY
    )
    private List<Comunidad> comunidades = new ArrayList<>();

    /**
     * Cuentas profesionales de presentación SEPA.
     */
    @OneToMany(
            mappedBy = "administrador",
            cascade = CascadeType.ALL,
            fetch = FetchType.LAZY
    )
    private List<CuentaPresentador> cuentasPresentador = new ArrayList<>();

    // =====================================================
    // CONSTRUCTORES
    // =====================================================

    public Administrador() {
    }

    // =====================================================
    // GETTERS / SETTERS
    // =====================================================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    // -------------------------
    // BÁSICOS
    // -------------------------

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

    // -------------------------
    // SEPA
    // -------------------------

    public String getNifCif() {
        return nifCif;
    }

    public void setNifCif(String nifCif) {
        this.nifCif = nifCif;
    }

    public String getSufijo() {
        return sufijo;
    }

    public void setSufijo(String sufijo) {
        this.sufijo = sufijo;
    }

    public String getIban() {
        return iban;
    }

    public void setIban(String iban) {
        this.iban = iban;
    }

    public String getBic() {
        return bic;
    }

    public void setBic(String bic) {
        this.bic = bic;
    }

    public String getDireccion() {
        return direccion;
    }

    public void setDireccion(String direccion) {
        this.direccion = direccion;
    }

    public String getPoblacion() {
        return poblacion;
    }

    public void setPoblacion(String poblacion) {
        this.poblacion = poblacion;
    }

    public String getProvincia() {
        return provincia;
    }

    public void setProvincia(String provincia) {
        this.provincia = provincia;
    }

    public String getPaisCod() {
        return paisCod;
    }

    public void setPaisCod(String paisCod) {
        this.paisCod = paisCod;
    }

    // -------------------------
    // SMTP
    // -------------------------

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

    // -------------------------
    // RELACIONES
    // -------------------------

    public List<Comunidad> getComunidades() {
        return comunidades;
    }

    public void setComunidades(List<Comunidad> comunidades) {
        this.comunidades = comunidades;
    }

    public List<CuentaPresentador> getCuentasPresentador() {
        return cuentasPresentador;
    }

    public void setCuentasPresentador(List<CuentaPresentador> cuentasPresentador) {
        this.cuentasPresentador = cuentasPresentador;
    }

    // =====================================================
    // MÉTODOS AUXILIARES
    // =====================================================

    public void addComunidad(Comunidad comunidad) {
        comunidades.add(comunidad);
        comunidad.setDatosAdministrador(this);
    }

    public void addCuentaPresentador(CuentaPresentador cuenta) {
        cuentasPresentador.add(cuenta);
        cuenta.setAdministrador(this);
    }

    // =====================================================
    // EQUALS / HASHCODE
    // =====================================================

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

    // =====================================================
    // TOSTRING
    // =====================================================

    @Override
    public String toString() {
        return "Administrador{" +
                "id=" + id +
                ", nombre='" + nombre + '\'' +
                ", email='" + email + '\'' +
                ", nifCif='" + nifCif + '\'' +
                '}';
    }
}