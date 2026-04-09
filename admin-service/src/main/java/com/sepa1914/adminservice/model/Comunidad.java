package com.sepa1914.adminservice.model;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Entidad Comunidad.
 * Mapeada a la tabla 'comunidades' de la base de datos.
 * OPTIMIZADA: Uso de FetchType.LAZY para mejorar el rendimiento y evitar lentitud.
 * MULTI-ADMIN: Vincula al Usuario (acceso) con el Administrador (datos profesionales y SMTP).
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
     * Cerebro de liquidación: Determina el cálculo de cuotas (Coeficiente o Partes Iguales).
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

    /**
     * RELACIÓN 1: Usuario de acceso (Dueño de la cuenta en el sistema).
     * Se usa LAZY para evitar cargar el usuario si solo necesitamos datos de la comunidad.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario administrador;

    /**
     * RELACIÓN 2: Administrador profesional.
     * Contiene la firma, el logo y la CONFIGURACIÓN SMTP para los envíos.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "administrador_id")
    private Administrador datosAdministrador;

    /**
     * RELACIÓN 3: Lista de vecinos.
     * Cascade PERSIST/MERGE para que los cambios en vecinos se guarden con la comunidad.
     */
    @OneToMany(mappedBy = "comunidad", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, orphanRemoval = true)
    private List<Vecino> vecinos = new ArrayList<>();

    // --- CONSTRUCTORES ---

    public Comunidad() {}

    // --- MÉTODOS DE LÓGICA Y AYUDA (Mantenidos y optimizados) ---

    /**
     * Devuelve el IBAN sin espacios para procesos bancarios SEPA.
     */
    public String getIbanLimpio() {
        return (iban != null) ? iban.replace(" ", "").toUpperCase() : "";
    }

    /**
     * Lógica de prioridad para el nombre del administrador en informes y emails.
     */
    public String getNombreAdministradorParaInforme() {
        if (datosAdministrador != null && datosAdministrador.getNombre() != null && !datosAdministrador.getNombre().isBlank()) {
            return datosAdministrador.getNombre();
        }
        return (administrador != null) ? administrador.getUsername() : "EL ADMINISTRADOR";
    }

    /**
     * Método de conveniencia para añadir vecinos asegurando la bidireccionalidad.
     */
    public void addVecino(Vecino vecino) {
        vecinos.add(vecino);
        vecino.setComunidad(this);
    }

    // --- GETTERS Y SETTERS ---

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
     * Mantiene compatibilidad con controladores que usan getAdministrador para referirse al Usuario.
     */
    public Usuario getAdministrador() { return administrador; }
    public void setAdministrador(Usuario usuario) { this.administrador = usuario; }

    /**
     * Acceso a los datos profesionales (SMTP, Firma, etc.)
     */
    public Administrador getDatosAdministrador() { return datosAdministrador; }
    public void setDatosAdministrador(Administrador datosAdmin) { this.datosAdministrador = datosAdmin; }

    public List<Vecino> getVecinos() { return vecinos; }
    public void setVecinos(List<Vecino> vecinos) { this.vecinos = vecinos; }

    // --- MÉTODOS ESTÁNDAR (Sustitutos de Lombok para estabilidad en colecciones) ---

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Comunidad comunidad = (Comunidad) o;
        return Objects.equals(id, comunidad.id);
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
                ", nif='" + identificadorAcreedor + '\'' +
                '}';
    }
}