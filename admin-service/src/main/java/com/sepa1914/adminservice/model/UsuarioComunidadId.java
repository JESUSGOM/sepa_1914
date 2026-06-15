package com.sepa1914.adminservice.model;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class UsuarioComunidadId implements Serializable {

    private Long usuarioId;
    private Long comunidadId;

    public UsuarioComunidadId() {
    }

    public UsuarioComunidadId(Long usuarioId, Long comunidadId) {
        this.usuarioId = usuarioId;
        this.comunidadId = comunidadId;
    }

    public Long getUsuarioId() {
        return usuarioId;
    }

    public void setUsuarioId(Long usuarioId) {
        this.usuarioId = usuarioId;
    }

    public Long getComunidadId() {
        return comunidadId;
    }

    public void setComunidadId(Long comunidadId) {
        this.comunidadId = comunidadId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UsuarioComunidadId that)) return false;
        return Objects.equals(usuarioId, that.usuarioId)
                && Objects.equals(comunidadId, that.comunidadId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(usuarioId, comunidadId);
    }
}