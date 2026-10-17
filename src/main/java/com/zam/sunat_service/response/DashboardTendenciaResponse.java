package com.zam.sunat_service.response;

import java.math.BigDecimal;

/** Ventas de un periodo sumando todos los clientes que lo tienen sincronizado. */
public record DashboardTendenciaResponse(
        String periodo,
        long cantidad,
        BigDecimal importeTotal,
        // Cuántos clientes aportan a este periodo: un mes con menos clientes sincronizados no es comparable.
        long clientes
) {

    public DashboardTendenciaResponse {
        importeTotal = importeTotal != null ? importeTotal : BigDecimal.ZERO;
    }

}
