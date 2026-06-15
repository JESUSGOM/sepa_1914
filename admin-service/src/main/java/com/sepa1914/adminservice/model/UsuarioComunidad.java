package com.sepa1914.adminservice.model;

import jakarta.persistence.*;

@Entity
@Table(name = "usuario_comunidades")
public class UsuarioComunidad {

    @EmbeddedId
    private UsuarioComunidadId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("usuarioId")
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("comunidadId")
    @JoinColumn(name = "comunidad_id")
    private Comunidad comunidad;

    public UsuarioComunidad() {
    }

    public UsuarioComunidad(Usuario usuario, Comunidad comunidad) {
        this.usuario = usuario;
        this.comunidad = comunidad;
        this.id = new UsuarioComunidadId(usuario.getId(), comunidad.getId());
    }

    public UsuarioComunidadId getId() {
        return id;
    }

    public void setId(UsuarioComunidadId id) {
        this.id = id;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public void setUsuario(Usuario usuario) {
        this.usuario = usuario;
    }

    public Comunidad getComunidad() {
        return comunidad;
    }

    public void setComunidad(Comunidad comunidad) {
        this.comunidad = comunidad;
    }
}