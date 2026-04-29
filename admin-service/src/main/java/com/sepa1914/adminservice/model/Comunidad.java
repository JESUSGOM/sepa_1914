package com.sepa1914.adminservice.model;

import com.sepa1914.adminservice.util.AesEncryptor;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(name = "comunidades")
public class Comunidad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre", length = 70, nullable = false)
    private String nombre;

    @Column(name = "sufijo", length = 3)
    private String sufijo;

    @Convert(converter = AesEncryptor.class)
    @Column(name = "iban", length = 255)
    private String iban;

    @Column(name = "direccion", length = 100)
    private String direccion;

    @Column(name = "poblacion", length = 50)
    private String poblacion;

    @Column(name = "provincia", length = 40)
    private String provincia;

    @Column(name = "pais_cod", length = 2)
    private String paiscod = "ES";

    @Convert(converter = AesEncryptor.class)
    @Column(name = "identificador_acreedor", length = 255)
    private String identificadorAcreedor;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_reparto", length = 20)
    private TipoReparto tipoReparto = TipoReparto.PARTES_IGUALES;

    @Column(name = "codigo_postal", length = 10)
    private String codigoPostal;

    @Column(name = "bic", length = 11)
    private String bic;

    @Column(name = "token_qr", unique = true, length = 100)
    private String tokenQr;

    // 🔹 FK usuario_id (NOT NULL)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario administrador;

    // 🔹 FK administrador_id (NULL permitido)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "administrador_id")
    private Administrador datosAdministrador;

    @OneToMany(mappedBy = "comunidad", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, orphanRemoval = true)
    private List<Vecino> vecinos = new ArrayList<>();

    // --- CONSTRUCTORES ---
    public Comunidad() {}

    // --- MÉTODOS DE NEGOCIO ---

    public String getIbanLimpio() {
        return (iban != null) ? iban.replace(" ", "").toUpperCase() : "";
    }

    public String getNombreAdministradorParaInforme() {
        if (datosAdministrador != null &&
                datosAdministrador.getNombre() != null &&
                !datosAdministrador.getNombre().isBlank()) {
            return datosAdministrador.getNombre();
        }
        return (administrador != null) ? administrador.getUsername() : "EL ADMINISTRADOR";
    }

    public void addVecino(Vecino vecino) {
        vecinos.add(vecino);
        vecino.setComunidad(this);
    }

    // --- GETTERS Y SETTERS ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getSufijo() { return sufijo; }
    public void setSufijo(String sufijo) { this.sufijo = sufijo; }

    public String getIban() { return iban; }
    public void setIban(String iban) { this.iban = iban; }

    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }

    public String getPoblacion() { return poblacion; }
    public void setPoblacion(String poblacion) { this.poblacion = poblacion; }

    public String getProvincia() { return provincia; }
    public void setProvincia(String provincia) { this.provincia = provincia; }

    public String getPaiscod() { return paiscod; }
    public void setPaiscod(String paiscod) { this.paiscod = paiscod; }

    public String getIdentificadorAcreedor() { return identificadorAcreedor; }
    public void setIdentificadorAcreedor(String identificadorAcreedor) {
        this.identificadorAcreedor = identificadorAcreedor;
    }

    public TipoReparto getTipoReparto() { return tipoReparto; }
    public void setTipoReparto(TipoReparto tipoReparto) {
        this.tipoReparto = tipoReparto;
    }

    public String getCodigoPostal() { return codigoPostal; }
    public void setCodigoPostal(String codigoPostal) {
        this.codigoPostal = codigoPostal;
    }

    public String getBic() { return bic; }
    public void setBic(String bic) { this.bic = bic; }

    public Usuario getAdministrador() { return administrador; }
    public void setAdministrador(Usuario administrador) {
        this.administrador = administrador;
    }

    public Administrador getDatosAdministrador() { return datosAdministrador; }
    public void setDatosAdministrador(Administrador datosAdministrador) {
        this.datosAdministrador = datosAdministrador;
    }

    public List<Vecino> getVecinos() { return vecinos; }
    public void setVecinos(List<Vecino> vecinos) {
        this.vecinos = vecinos;
    }

    public String getTokenQr() { return tokenQr; }
    public void setTokenQr(String tokenQr) { this.tokenQr = tokenQr; }

    // --- equals / hashCode ---

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Comunidad)) return false;
        Comunidad that = (Comunidad) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Comunidad{" +
                "id=" + id +
                ", nombre='" + nombre + '\'' +
                '}';
    }
}