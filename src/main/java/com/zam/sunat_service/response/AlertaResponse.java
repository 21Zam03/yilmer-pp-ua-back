package com.zam.sunat_service.response;

import java.time.LocalDateTime;

public record AlertaResponse(
        Long id,
        String clienteRuc,
        // null si el cliente ya no existe o cambió de RUC (la alerta guarda el RUC, no una FK).
        String razonSocial,
        String tipo,
        String periodo,
        String mensaje,
        Boolean leida,
        LocalDateTime fechaCreacion
) {
}
