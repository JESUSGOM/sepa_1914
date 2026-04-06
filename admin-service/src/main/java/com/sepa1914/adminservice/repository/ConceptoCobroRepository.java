package com.sepa1914.adminservice.repository;

import com.sepa1914.adminservice.model.ConceptoCobro;
import com.sepa1914.adminservice.model.Vecino;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ConceptoCobroRepository extends JpaRepository<ConceptoCobro, Long> {

    @Query("SELECT COUNT(c) FROM ConceptoCobro c WHERE c.comunidad.administrador.id = :usuarioId")
    long contarPorUsuario(@Param("usuarioId") Long usuarioId);

    List<ConceptoCobro> findByVecino(Vecino vecino);
    List<ConceptoCobro> findByComunidadId(Long comunidadId);
    List<ConceptoCobro> findByVecinoIsNull();
    List<ConceptoCobro> findByVecinoAndActivoTrue(Vecino vecino);
    List<ConceptoCobro> findByVecinoIsNullAndComunidadId(Long comunidadId);

    @Query("SELECT c FROM ConceptoCobro c WHERE c.comunidad IS NULL AND c.vecino IS NULL")
    List<ConceptoCobro> findAllGenericConcepts();
}