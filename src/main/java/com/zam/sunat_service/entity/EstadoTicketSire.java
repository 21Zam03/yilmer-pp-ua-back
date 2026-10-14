package com.zam.sunat_service.entity;

public enum EstadoTicketSire {

    // Ticket generado en SUNAT; el cron consulta su estado cada minuto.
    PENDIENTE,
    // SUNAT terminó y se está descargando/procesando el ZIP.
    DESCARGANDO,
    COMPLETADO,
    // SUNAT terminó sin comprobantes para el periodo (cntCPInformados = 0).
    VACIO_SIN_MOVIMIENTOS,
    ERROR;

    public boolean isActivo() {
        return this == PENDIENTE || this == DESCARGANDO;
    }

}
