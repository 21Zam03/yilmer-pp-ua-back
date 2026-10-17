package com.zam.sunat_service.service;

import com.zam.sunat_service.response.DashboardClientesResponse;
import com.zam.sunat_service.response.DashboardSireResponse;

public interface DashboardService {

    DashboardClientesResponse obtenerResumenClientes();

    /** Ventas del mes anterior, tendencia de 12 meses, estado de las sincronizaciones y programaciones. */
    DashboardSireResponse obtenerResumenSire();

}
