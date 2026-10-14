package com.zam.sunat_service.service;

import com.zam.sunat_service.request.SireSyncRequest;
import com.zam.sunat_service.response.TicketSireResponse;
import com.zam.sunat_service.response.VentasSireResponse;

import java.util.List;

public interface SireService {

    /**
     * Solicita a SUNAT la propuesta RVIE del periodo (servicio 5.18) y registra el ticket como PENDIENTE.
     * Si ya hay una sincronización en curso para ese cliente y periodo, devuelve ese ticket.
     */
    TicketSireResponse sincronizarVentas(SireSyncRequest request);

    TicketSireResponse obtenerEstadoTicket(Long ticketId);

    /**
     * Ventas ya sincronizadas del periodo (lee la BD, no llama a SUNAT), con totales y la última sincronización.
     */
    VentasSireResponse obtenerVentas(String ruc, String periodo, String searchKey, int page, int size);

    /** IDs de los tickets que el cron debe procesar (PENDIENTE y DESCARGANDO). */
    List<Long> obtenerTicketsEnProceso();

    /**
     * Avanza un ticket: consulta su estado en SUNAT (5.16) y, si terminó, descarga (5.17) y guarda las ventas.
     */
    void procesarTicket(Long ticketId);

}
