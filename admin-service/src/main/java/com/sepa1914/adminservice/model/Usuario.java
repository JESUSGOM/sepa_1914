package com.sepa1914.adminservice.model;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Entidad Usuario.
 * Mapeada a la tabla 'usuarios' que contiene el registro 'admin1'.
 */
@Entity
@Table(name = "usuarios") // <--- Usamos la tabla que SI tiene datos (admin1)
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Sincronizado con los nombres de columna de la tabla 'usuarios'
    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    // RELACIÓN: Mantenemos la funcionalidad de gestión de comunidades.
    //mappedBy apunta al campo 'administrador' en la clase Comunidad.
    @OneToMany(mappedBy = "administrador", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Comunidad> comunidades = new ArrayList<>();

    public Usuario() {}

    // --- GETTERS Y SETTERS ---

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public List<Comunidad> getComunidades() {
        return comunidades;
    }

    public void setComunidades(List<Comunidad> comunidades) {
        this.comunidades = comunidades;
    }
}