package com.sepa1914.adminservice.model;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Entidad Comunidad.
 * Mapeada a la tabla 'comunidades' existente en la base de datos.
 * INTEGRIDAD TOTAL: Mantiene gestión de firmas, lógica de informes y añade Cerebro de Reparto.
 */
@Entity
@Table(name = "comunidades")
public class Comunidad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nombre", length = 70, nullable = false)
    private String nombre;

    @Column(name = "identificador_acreedor", length = 35, nullable = false)
    private String identificadorAcreedor;

    /**
     * NUEVO: Cerebro de liquidación.
     * Determina si las derramas se calculan por Coeficiente o Partes Iguales.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_reparto", length = 20)
    private TipoReparto tipoReparto = TipoReparto.PARTES_IGUALES;

    @Column(name = "iban", length = 34, nullable = false)
    private String iban;

    @Column(name = "direccion", length = 100)
    private String direccion;

    @Column(name = "poblacion", length = 50)
    private String poblacion;

    @Column(name = "codigo_postal", length = 10)
    private String codigoPostal;

    @Column(name = "bic", length = 11)
    private String bic;

    // RELACIÓN 1: Usuario de acceso (Login)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario administrador;

    // RELACIÓN 2: Administrador profesional (Firma real en PDF)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "administrador_id")
    private Administrador datosAdministrador;

    @OneToMany(mappedBy = "comunidad", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    private List<Vecino> vecinos = new ArrayList<>();


    public Comunidad() {}

    // --- MÉTODOS DE LÓGICA (Mantenidos al 100%) ---

    public String getIbanLimpio() {
        return iban != null ? iban.replace(" ", "").toUpperCase() : "";
    }

    /**
     * Este es el método que usaremos en el PDF.
     * Prioriza el nombre de la tabla 'administradores', si no, usa el del usuario.
     */
    public String getNombreAdministradorParaInforme() {
        if (datosAdministrador != null && datosAdministrador.getNombre() != null) {
            return datosAdministrador.getNombre();
        }
        return (administrador != null) ? administrador.getUsername() : "EL ADMINISTRADOR";
    }

    // --- GETTERS Y SETTERS (Refactorizados y Completos) ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getIdentificadorAcreedor() { return identificadorAcreedor; }
    public void setIdentificadorAcreedor(String idAcreedor) { this.identificadorAcreedor = idAcreedor; }

    public TipoReparto getTipoReparto() { return tipoReparto; }
    public void setTipoReparto(TipoReparto tipoReparto) { this.tipoReparto = tipoReparto; }

    public String getIban() { return iban; }
    public void setIban(String iban) { this.iban = iban; }

    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }

    public String getPoblacion() { return poblacion; }
    public void setPoblacion(String poblacion) { this.poblacion = poblacion; }

    public String getCodigoPostal() { return codigoPostal; }
    public void setCodigoPostal(String cp) { this.codigoPostal = cp; }

    public String getBic() { return bic; }
    public void setBic(String bic) { this.bic = bic; }

    /**
     * Mantenemos nombre 'getAdministrador' para compatibilidad con controladores existentes.
     */
    public Usuario getAdministrador() { return administrador; }
    public void setAdministrador(Usuario usuario) { this.administrador = usuario; }

    public Administrador getDatosAdministrador() { return datosAdministrador; }
    public void setDatosAdministrador(Administrador datosAdmin) { this.datosAdministrador = datosAdmin; }

    public List<Vecino> getVecinos() { return vecinos; }
    public void setVecinos(List<Vecino> vecinos) { this.vecinos = vecinos; }
}