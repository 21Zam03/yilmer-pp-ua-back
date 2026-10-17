package com.zam.sunat_service.repository;

import com.zam.sunat_service.entity.AlertaEntity;
import com.zam.sunat_service.response.AlertaResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AlertaRepository extends JpaRepository<AlertaEntity, Long> {

    boolean existsByClienteRucAndTipoAndPeriodo(String clienteRuc, String tipo, String periodo);

    Optional<AlertaEntity> findFirstByClienteRucAndTipoAndPeriodo(String clienteRuc, String tipo, String periodo);

    // La alerta guarda el RUC (no una FK): se une con el cliente solo para mostrar la razón social.
    @Query(value = """
    SELECT new com.zam.sunat_service.response.AlertaResponse(
            a.id, a.clienteRuc, c.businessName, a.tipo, a.periodo, a.mensaje, a.leida, a.fechaCreacion)
    FROM AlertaEntity a LEFT JOIN ClientEntity c ON c.ruc = a.clienteRuc
    WHERE (:leida IS NULL OR a.leida = :leida)
    ORDER BY a.fechaCreacion DESC, a.id DESC
    """, countQuery = "SELECT COUNT(a) FROM AlertaEntity a WHERE (:leida IS NULL OR a.leida = :leida)")
    Page<AlertaResponse> findByLeida(@Param("leida") Boolean leida, Pageable pageable);

    long countByLeidaFalse();

    @Modifying
    @Query("UPDATE AlertaEntity a SET a.leida = true WHERE a.leida = false")
    int marcarTodasLeidas();

    @Modifying
    @Query("UPDATE AlertaEntity a SET a.clienteRuc = :nuevoRuc WHERE a.clienteRuc = :rucAnterior")
    int updateClienteRuc(@Param("rucAnterior") String rucAnterior, @Param("nuevoRuc") String nuevoRuc);

    @Modifying
    @Query("DELETE FROM AlertaEntity a WHERE a.clienteRuc = :ruc")
    int deleteByClienteRuc(@Param("ruc") String ruc);

}
