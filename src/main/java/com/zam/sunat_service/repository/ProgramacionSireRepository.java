package com.zam.sunat_service.repository;

import com.zam.sunat_service.entity.ClientEntity;
import com.zam.sunat_service.entity.ProgramacionSireEntity;
import com.zam.sunat_service.entity.TipoLibroSire;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ProgramacionSireRepository extends JpaRepository<ProgramacionSireEntity, Long> {

    Optional<ProgramacionSireEntity> findByClientAndTipo(ClientEntity client, TipoLibroSire tipo);

    // Con el cliente cargado: el cron trabaja fuera de transacción.
    @Query("SELECT p FROM ProgramacionSireEntity p JOIN FETCH p.client WHERE p.programacionId = :id")
    Optional<ProgramacionSireEntity> findWithClientById(@Param("id") Long id);

    @Query("""
    SELECT p.programacionId FROM ProgramacionSireEntity p
    WHERE p.activo = true AND p.client.enabled = true AND p.proximaEjecucion <= :ahora
    ORDER BY p.proximaEjecucion
    """)
    List<Long> findIdsVencidas(@Param("ahora") LocalDateTime ahora);

    // --- Dashboard ---

    @Query("SELECT COUNT(p) FROM ProgramacionSireEntity p WHERE p.activo = true AND p.client.enabled = true")
    long countActivas();

    // Pausadas por el cron tras MAX_ERRORES_CONSECUTIVOS (no las que el usuario desactivó a mano).
    @Query("SELECT COUNT(p) FROM ProgramacionSireEntity p WHERE p.activo = false AND p.erroresConsecutivos >= :maxErrores")
    long countPausadas(@Param("maxErrores") int maxErrores);

    @Query("""
    SELECT p FROM ProgramacionSireEntity p JOIN FETCH p.client c
    WHERE p.activo = true AND c.enabled = true
    ORDER BY p.proximaEjecucion
    """)
    List<ProgramacionSireEntity> findProximas(Pageable pageable);

    @Query("""
    SELECT p FROM ProgramacionSireEntity p JOIN FETCH p.client
    WHERE p.activo = false AND p.erroresConsecutivos >= :maxErrores
    ORDER BY p.fechaActualizacion DESC
    """)
    List<ProgramacionSireEntity> findPausadas(@Param("maxErrores") int maxErrores, Pageable pageable);

    /**
     * Reserva la ejecución moviendo proximaEjecucion. Devuelve 0 si otra instancia del backend ya la tomó
     * (o si la desactivaron entre la consulta y la reserva), así una programación nunca se dispara dos veces.
     */
    @Modifying
    @Transactional
    @Query("""
    UPDATE ProgramacionSireEntity p SET p.proximaEjecucion = :nuevaProxima
    WHERE p.programacionId = :id AND p.activo = true AND p.proximaEjecucion <= :ahora
    """)
    int reservar(@Param("id") Long id, @Param("ahora") LocalDateTime ahora, @Param("nuevaProxima") LocalDateTime nuevaProxima);

}
