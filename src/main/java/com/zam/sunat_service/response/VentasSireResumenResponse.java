package com.zam.sunat_service.response;

import java.math.BigDecimal;

/**
 * Totales de todo el periodo (no solo de la página ni del filtro de búsqueda).
 */
public record VentasSireResumenResponse(
        long cantidad,
        BigDecimal baseImponible,
        BigDecimal igv,
        BigDecimal importeTotal
) {

    // SUM devuelve null cuando el periodo no tiene ventas.
    public VentasSireResumenResponse {
        baseImponible = baseImponible != null ? baseImponible : BigDecimal.ZERO;
        igv = igv != null ? igv : BigDecimal.ZERO;
        importeTotal = importeTotal != null ? importeTotal : BigDecimal.ZERO;
    }

}
