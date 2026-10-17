package com.zam.sunat_service.response;

import java.math.BigDecimal;

public record DashboardTopClienteResponse(
        String ruc,
        String razonSocial,
        long cantidad,
        BigDecimal importeTotal
) {
}
