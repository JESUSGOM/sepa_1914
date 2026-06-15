package com.sepa1914.adminservice.model;

import jakarta.persistence.*;

import java.util.Objects;

/**
 * Cuenta bancaria profesional de presentación SEPA.
 *
 * Permite que un administrador pueda:
 * - presentar remesas desde distintos bancos;
 * - usar distintos identificadores SEPA;
 * - trabajar multiempresa/multibanco.
 */
@Entity
@Table(name = "cuentas_presentador")
public class CuentaPresentador {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // =====================================================
    // RELACIÓN
    // =====================================================

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "administrador_id", nullable = false)
    private Administrador administrador;

    // =====================================================
    // DATOS DESCRIPTIVOS
    // =====================================================

    @Column(name = "alias", nullable = false, length = 100)
    private String alias;

    @Column(name = "banco", length = 100)
    private String banco;

    // =====================================================
    // DATOS SEPA
    // =====================================================

    @Column(name = "identificador_presentador", nullable = false, length = 35)
    private String identificadorPresentador;

    @Column(name = "nif_cif", length = 20)
    private String nifCif;

    @Column(name = "sufijo", length = 3)
    private String sufijo;

    @Column(name = "iban", length = 34)
    private String iban;

    @Column(name = "bic", length = 11)
    private String bic;

    // =====================================================
    // CONTROL
    // =====================================================

    @Column(name = "activa")
    private boolean activa = true;

    @Column(name = "observaciones", columnDefinition = "TEXT")
    private String observaciones;

    // =====================================================
    // CONSTRUCTORES
    // =====================================================

    public CuentaPresentador() {
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

    public Administrador getAdministrador() {
        return administrador;
    }

    public void setAdministrador(Administrador administrador) {
        this.administrador = administrador;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public String getBanco() {
        return banco;
    }

    public void setBanco(String banco) {
        this.banco = banco;
    }

    public String getIdentificadorPresentador() {
        return identificadorPresentador;
    }

    public void setIdentificadorPresentador(String identificadorPresentador) {
        this.identificadorPresentador = identificadorPresentador;
    }

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

    public boolean isActiva() {
        return activa;
    }

    public void setActiva(boolean activa) {
        this.activa = activa;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }

    // =====================================================
    // EQUALS / HASHCODE
    // =====================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;

        if (o == null || getClass() != o.getClass()) return false;

        CuentaPresentador that = (CuentaPresentador) o;

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
        return "CuentaPresentador{" +
                "id=" + id +
                ", alias='" + alias + '\'' +
                ", banco='" + banco + '\'' +
                ", identificadorPresentador='" + identificadorPresentador + '\'' +
                '}';
    }
}