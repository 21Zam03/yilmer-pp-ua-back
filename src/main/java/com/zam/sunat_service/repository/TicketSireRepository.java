package com.zam.sunat_service.repository;

import com.zam.sunat_service.entity.ClientEntity;
import com.zam.sunat_service.entity.EstadoTicketSire;
import com.zam.sunat_service.entity.TicketSireEntity;
import com.zam.sunat_service.entity.TipoLibroSire;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface TicketSireRepository extends JpaRepository<TicketSireEntity, Long> {

    // Con el cliente cargado: open-in-view está desactivado y el cron trabaja fuera de transacción.
    @Query("SELECT t FROM TicketSireEntity t JOIN FETCH t.client WHERE t.ticketId = :ticketId")
    Optional<TicketSireEntity> findWithClientById(@Param("ticketId") Long ticketId);

    @Query("SELECT t.ticketId FROM TicketSireEntity t WHERE t.estado IN :estados ORDER BY t.fechaCreacion")
    List<Long> findIdsByEstadoIn(@Param("estados") Collection<EstadoTicketSire> estados);

    Optional<TicketSireEntity> findFirstByClientAndPeriodoAndTipoOrderByFechaCreacionDesc(
            ClientEntity client, String periodo, TipoLibroSire tipo
    );

    Optional<TicketSireEntity> findFirstByClientAndPeriodoAndTipoAndEstadoInOrderByFechaCreacionDesc(
            ClientEntity client, String periodo, TipoLibroSire tipo, Collection<EstadoTicketSire> estados
    );

    // --- Dashboard ---

    /** Filas [EstadoTicketSire, Long]: tickets creados desde la fecha indicada, agrupados por estado. */
    @Query("SELECT t.estado, COUNT(t) FROM TicketSireEntity t WHERE t.fechaCreacion >= :desde GROUP BY t.estado")
    List<Object[]> contarPorEstadoDesde(@Param("desde") LocalDateTime desde);

    // Tickets en ERROR que siguen siendo el último de su cliente, periodo y libro (nadie volvió a sincronizar).
    String ERRORES_VIGENTES_WHERE = """
     WHERE t.estado = com.zam.sunat_service.entity.EstadoTicketSire.ERROR
       AND NOT EXISTS (
             SELECT 1 FROM TicketSireEntity t2
             WHERE t2.client = t.client AND t2.periodo = t.periodo AND t2.tipo = t.tipo
               AND t2.fechaCreacion > t.fechaCreacion
       )
    """;

    @Query(value = "SELECT t FROM TicketSireEntity t JOIN FETCH t.client" + ERRORES_VIGENTES_WHERE
            + " ORDER BY t.fechaActualizacion DESC",
            countQuery = "SELECT COUNT(t) FROM TicketSireEntity t" + ERRORES_VIGENTES_WHERE)
    Page<TicketSireEntity> findErroresVigentes(Pageable pageable);

}
