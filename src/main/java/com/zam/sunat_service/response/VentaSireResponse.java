package com.zam.sunat_service.response;

import com.zam.sunat_service.entity.VentaSireEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

public record VentaSireResponse(
        Long ventaId,
        String carSunat,
        LocalDate fechaEmision,
        String tipoComprobante,
        String serie,
        String numero,
        String clienteTipoDoc,
        String clienteNroDoc,
        String clienteNombre,
        BigDecimal baseImponible,
        BigDecimal igv,
        BigDecimal importeTotal
) {

    public static VentaSireResponse from(VentaSireEntity venta) {
        return new VentaSireResponse(
                venta.getVentaId(),
                venta.getCarSunat(),
                venta.getFechaEmision(),
                venta.getTipoComprobante(),
                venta.getSerie(),
                venta.getNumero(),
                venta.getClienteTipoDoc(),
                venta.getClienteNroDoc(),
                venta.getClienteNombre(),
                venta.getBaseImponible(),
                venta.getIgv(),
                venta.getImporteTotal()
        );
    }

}
