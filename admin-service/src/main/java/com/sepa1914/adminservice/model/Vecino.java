package com.sepa1914.adminservice.model;

import com.sepa1914.adminservice.util.AesEncryptor;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Entidad Vecino (Propiedad).
 * Representa a un propietario y su unidad (vivienda/local).
 * MANTIENE: Gestión SEPA, comunicación digital, contabilidad y multitélefono.
 */
@Entity
@Table(name = "vecinos")
public class Vecino {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(length = 20)
    private String telefono_1;

    @Column(length = 20)
    private String telefono_2;

    @Column(length = 20)
    private String telefono_3;

    @Convert(converter = AesEncryptor.class)
    @Column(length = 150)
    private String email;

    @Convert(converter = AesEncryptor.class)
    @Column(name = "nif", nullable = false, length = 20)
    private String nif;

    @Column(nullable = false, length = 50)
    private String vivienda;

    @Column(name = "piso_porton", length = 50)
    private String pisoPorton;

    @Column(precision = 10, scale = 4)
    private BigDecimal coeficiente = BigDecimal.ZERO;

    @Column(name = "direccion_notificacion", length = 200)
    private String direccionNotificacion;

    @Convert(converter = AesEncryptor.class)
    @Column(length = 34)
    private String iban;

    @Convert(converter = AesEncryptor.class)
    @Column(length = 11)
    private String bic;

    @Column(name = "referencia_mandato", length = 35)
    private String referenciaMandato;

    @Column(name = "ruta_mandato_firmado", length = 255)
    private String rutaMandatoFirmado;

    @Column(name = "cuenta_contable", length = 15)
    private String cuentaContable;

    // LÓGICA DE ESTADOS (Mantenemos boolean primitivo para evitar nulos accidentales)
    @Column(nullable = false)
    private boolean domiciliado = true;

    @Column(name = "envio_digital", nullable = false)
    private boolean envioDigital = true;

    @Column(nullable = false)
    private boolean activo = true;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String notas;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comunidad_id", nullable = false)
    private Comunidad comunidad;

    @OneToMany(mappedBy = "vecino", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ConceptoCobro> listaConceptos = new ArrayList<>();

    // Constructor vacío requerido por JPA
    public Vecino() {}

    // --- MÉTODOS DE CÁLCULO ---

    /**
     * Calcula el importe total sumando los conceptos de cobro activos del vecino.
     */
    public BigDecimal getImporteTotalConceptos() {
        if (listaConceptos == null || listaConceptos.isEmpty()) return BigDecimal.ZERO;
        return listaConceptos.stream()
                .filter(ConceptoCobro::isActivo)
                .map(ConceptoCobro::getImporte)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public List<ConceptoCobro> getConceptosPropios() {
        return listaConceptos;
    }

    // --- MÉTODOS DE COMPATIBILIDAD (LEGACY) ---
    public void setDni(String dni) { this.nif = dni; }
    public String getDni() { return nif; }

    // --- GETTERS Y SETTERS ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getTelefono_1() { return telefono_1; }
    public void setTelefono_1(String telefono_1) { this.telefono_1 = telefono_1; }

    public String getTelefono_2() { return telefono_2; }
    public void setTelefono_2(String telefono_2) { this.telefono_2 = telefono_2; }

    public String getTelefono_3() { return telefono_3; }
    public void setTelefono_3(String telefono_3) { this.telefono_3 = telefono_3; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getNif() { return nif; }
    public void setNif(String nif) { this.nif = nif; }

    public String getVivienda() { return vivienda; }
    public void setVivienda(String vivienda) { this.vivienda = vivienda; }

    public String getPisoPorton() { return pisoPorton; }
    public void setPisoPorton(String pisoPorton) { this.pisoPorton = pisoPorton; }

    public BigDecimal getCoeficiente() { return coeficiente; }
    public void setCoeficiente(BigDecimal coeficiente) { this.coeficiente = coeficiente; }

    public String getDireccionNotificacion() { return direccionNotificacion; }
    public void setDireccionNotificacion(String direccionNotificacion) { this.direccionNotificacion = direccionNotificacion; }

    public String getIban() { return iban; }
    public void setIban(String iban) { this.iban = iban; }

    public String getBic() { return bic; }
    public void setBic(String bic) { this.bic = bic; }

    public String getReferenciaMandato() { return referenciaMandato; }
    public void setReferenciaMandato(String referenciaMandato) { this.referenciaMandato = referenciaMandato; }

    public String getRutaMandatoFirmado() { return rutaMandatoFirmado; }
    public void setRutaMandatoFirmado(String rutaMandatoFirmado) { this.rutaMandatoFirmado = rutaMandatoFirmado; }

    public String getCuentaContable() { return cuentaContable; }
    public void setCuentaContable(String cuentaContable) { this.cuentaContable = cuentaContable; }

    public boolean isDomiciliado() { return domiciliado; }
    public void setDomiciliado(boolean domiciliado) { this.domiciliado = domiciliado; }

    public boolean isEnvioDigital() { return envioDigital; }
    public void setEnvioDigital(boolean envioDigital) { this.envioDigital = envioDigital; }

    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }

    public String getNotas() { return notas; }
    public void setNotas(String notas) { this.notas = notas; }

    public Comunidad getComunidad() { return comunidad; }
    public void setComunidad(Comunidad comunidad) { this.comunidad = comunidad; }

    public List<ConceptoCobro> getListaConceptos() { return listaConceptos; }
    public void setListaConceptos(List<ConceptoCobro> listaConceptos) { this.listaConceptos = listaConceptos; }
}