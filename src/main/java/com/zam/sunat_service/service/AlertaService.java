package com.zam.sunat_service.service;

import com.zam.sunat_service.entity.ClientEntity;
import com.zam.sunat_service.response.AlertaResponse;
import com.zam.sunat_service.response.PageResponse;

public interface AlertaService {

    /**
     * Revisa las ventas sincronizadas del periodo y deja una alerta SIN_VENTAS con los días que no tuvieron
     * comprobantes (los domingos no cuentan si el cliente no trabaja domingos). Si ya existía, la actualiza; si ya no
     * hay días sin ventas o el periodo no tiene ninguna venta (eso ya se ve como "Sin movimientos"), la elimina.
     * Solo evalúa periodos cerrados: en el mes en curso aún faltan días y comprobantes por llegar a SUNAT.
     */
    void actualizarAlertaSinVentas(ClientEntity client, String periodo);

    /** Más recientes primero. leida null = todas. */
    PageResponse<AlertaResponse> listar(Boolean leida, int page, int size);

    long contarNoLeidas();

    void marcarLeida(Long alertaId);

    /** @return cuántas se marcaron */
    int marcarTodasLeidas();

}
