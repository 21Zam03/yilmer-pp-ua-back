package com.zam.sunat_service.response;

public record VentasSireResponse(
        String ruc,
        String periodo,
        // Último ticket del periodo (en cualquier estado); null si nunca se sincronizó.
        TicketSireResponse ultimaSincronizacion,
        VentasSireResumenResponse resumen,
        PageResponse<VentaSireResponse> ventas
) {
}
