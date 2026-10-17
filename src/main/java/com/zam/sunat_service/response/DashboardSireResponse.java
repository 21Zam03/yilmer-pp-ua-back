package com.zam.sunat_service.response;

import com.zam.sunat_service.entity.ProgramacionSireEntity;
import com.zam.sunat_service.entity.TicketSireEntity;

import java.time.LocalDateTime;
import java.util.List;

public record DashboardSireResponse(
        // Mes anterior (yyyyMM): el que normalmente se declara.
        String periodo,
        VentasSireResumenResponse ventasPeriodo,
        long clientesConVentas,
        List<DashboardTopClienteResponse> topClientes,
        // Últimos 12 meses, del más antiguo al más reciente; los meses sin datos vienen en cero.
        List<DashboardTendenciaResponse> tendencia,
        Sincronizaciones sincronizaciones,
        Programaciones programaciones
) {

    /**
     * Tickets creados en los últimos {@code dias} días, por estado. erroresVigentes son los tickets en ERROR que
     * siguen siendo la última sincronización de su cliente y periodo (si después se sincronizó bien, ya no cuentan).
     */
    public record Sincronizaciones(
            int dias,
            long enCurso,
            long completados,
            long vacios,
            long errores,
            long totalErroresVigentes,
            List<ErrorSincronizacion> erroresVigentes
    ) {
    }

    public record ErrorSincronizacion(
            Long ticketId,
            String ruc,
            String razonSocial,
            String periodo,
            String mensaje,
            LocalDateTime fecha
    ) {

        public static ErrorSincronizacion from(TicketSireEntity t) {
            return new ErrorSincronizacion(t.getTicketId(), t.getClient().getRuc(), t.getClient().getBusinessName(),
                    t.getPeriodo(), t.getMensaje(), t.getFechaActualizacion());
        }

    }

    public record Programaciones(
            long activas,
            long pausadas,
            List<ProgramacionItem> proximas,
            List<ProgramacionItem> pausadasLista
    ) {
    }

    public record ProgramacionItem(
            String ruc,
            String razonSocial,
            int intervaloHoras,
            LocalDateTime proximaEjecucion,
            LocalDateTime ultimaEjecucion,
            String ultimoError
    ) {

        public static ProgramacionItem from(ProgramacionSireEntity p) {
            return new ProgramacionItem(p.getClient().getRuc(), p.getClient().getBusinessName(), p.getIntervaloHoras(),
                    p.isActivo() ? p.getProximaEjecucion() : null, p.getUltimaEjecucion(), p.getUltimoError());
        }

    }

}
