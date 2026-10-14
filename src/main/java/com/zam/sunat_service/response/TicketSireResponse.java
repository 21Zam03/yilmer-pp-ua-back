package com.zam.sunat_service.response;

import com.zam.sunat_service.entity.EstadoTicketSire;
import com.zam.sunat_service.entity.TicketSireEntity;
import com.zam.sunat_service.entity.TipoLibroSire;

import java.time.LocalDateTime;

public record TicketSireResponse(
        Long ticketId,
        String ruc,
        String periodo,
        TipoLibroSire tipo,
        String numTicket,
        EstadoTicketSire estado,
        // true mientras el frontend deba seguir consultando (PENDIENTE / DESCARGANDO).
        boolean enProceso,
        String mensaje,
        Integer cantidadRegistros,
        LocalDateTime fechaCreacion,
        LocalDateTime fechaActualizacion
) {

    // El ticket debe venir con el cliente cargado (findWithClientById).
    public static TicketSireResponse from(TicketSireEntity ticket) {
        return new TicketSireResponse(
                ticket.getTicketId(),
                ticket.getClient().getRuc(),
                ticket.getPeriodo(),
                ticket.getTipo(),
                ticket.getNumTicket(),
                ticket.getEstado(),
                ticket.getEstado().isActivo(),
                ticket.getMensaje(),
                ticket.getCantidadRegistros(),
                ticket.getFechaCreacion(),
                ticket.getFechaActualizacion()
        );
    }

}
