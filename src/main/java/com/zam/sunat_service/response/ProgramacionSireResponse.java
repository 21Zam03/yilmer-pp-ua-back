package com.zam.sunat_service.response;

import com.zam.sunat_service.entity.ProgramacionSireEntity;
import com.zam.sunat_service.entity.TipoLibroSire;

import java.time.LocalDateTime;

public record ProgramacionSireResponse(
        String ruc,
        TipoLibroSire tipo,
        boolean activo,
        // null si el cliente nunca configuró la sincronización automática.
        Integer intervaloHoras,
        LocalDateTime proximaEjecucion,
        LocalDateTime ultimaEjecucion,
        int erroresConsecutivos,
        String ultimoError
) {

    public static ProgramacionSireResponse from(ProgramacionSireEntity p) {
        return new ProgramacionSireResponse(
                p.getClient().getRuc(),
                p.getTipo(),
                p.isActivo(),
                p.getIntervaloHoras(),
                p.isActivo() ? p.getProximaEjecucion() : null,
                p.getUltimaEjecucion(),
                p.getErroresConsecutivos(),
                p.getUltimoError()
        );
    }

    public static ProgramacionSireResponse sinConfigurar(String ruc, TipoLibroSire tipo) {
        return new ProgramacionSireResponse(ruc, tipo, false, null, null, null, 0, null);
    }

}
