package com.zam.sunat_service.repository;

import com.zam.sunat_service.entity.ClientEntity;
import com.zam.sunat_service.entity.EstadoTicketSire;
import com.zam.sunat_service.entity.TicketSireEntity;
import com.zam.sunat_service.entity.TipoLibroSire;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

}
