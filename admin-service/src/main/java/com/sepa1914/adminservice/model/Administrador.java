package com.sepa1914.adminservice.model;

import jakarta.persistence.*;
import java.util.List;

@Entity
@Table(name = "administradores")
public class Administrador {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nombre; // Este es el que saldrá en el PDF
    private String email;

    @OneToMany(mappedBy = "datosAdministrador")
    private List<Comunidad> comunidades;

    // Getters y Setters...
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
}