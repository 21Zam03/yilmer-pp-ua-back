package com.zam.sunat_service.response;

public record DashboardClientesResponse(
        long total,
        long activos,
        long inactivos,
        // Clientes activos que no pueden sincronizar el SIRE porque les falta el client_id o el client_secret.
        long activosSinCredencialesApi
) {
}
