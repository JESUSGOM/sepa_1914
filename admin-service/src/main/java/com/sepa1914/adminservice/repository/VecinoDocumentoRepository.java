package com.sepa1914.adminservice.repository;

import com.sepa1914.adminservice.model.Vecino;
import com.sepa1914.adminservice.model.VecinoDocumento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VecinoDocumentoRepository extends JpaRepository<VecinoDocumento, Long> {

    List<VecinoDocumento> findByVecino(Vecino vecino);

    List<VecinoDocumento> findByVecinoId(Long vecinoId);

    List<VecinoDocumento> findByVecinoIdAndTipoDocumento(Long vecinoId, String tipoDocumento);
}