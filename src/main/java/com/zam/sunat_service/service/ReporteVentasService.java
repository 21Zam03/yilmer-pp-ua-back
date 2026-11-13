package com.zam.sunat_service.service;

import com.zam.sunat_service.entity.TipoReporteVentas;
import com.zam.sunat_service.response.ReporteExcel;

public interface ReporteVentasService {

    /**
     * Excel con las ventas sincronizadas del periodo: hoja "Comprobantes" (detalle con totales) y hoja
     * "Resumen por día". Lee la BD; no llama a SUNAT.
     */
    ReporteExcel exportarVentas(String ruc, String periodo, TipoReporteVentas tipo);

}
