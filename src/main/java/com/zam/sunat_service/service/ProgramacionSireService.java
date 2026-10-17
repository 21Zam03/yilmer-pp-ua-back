package com.zam.sunat_service.service;

import com.zam.sunat_service.request.ProgramacionSireRequest;
import com.zam.sunat_service.response.ProgramacionSireResponse;

import java.util.List;

public interface ProgramacionSireService {

    /** Programación de ventas del cliente; si nunca se configuró, devuelve una inactiva. */
    ProgramacionSireResponse obtenerProgramacionVentas(String ruc);

    /** Activa, desactiva o cambia el intervalo. Al guardar se reinicia el contador de errores. */
    ProgramacionSireResponse guardarProgramacionVentas(ProgramacionSireRequest request);

    /** IDs de las programaciones que el cron debe ejecutar ahora. */
    List<Long> obtenerProgramacionesVencidas();

    /** Lanza la sincronización del periodo anterior y reprograma la siguiente ejecución. */
    void ejecutarProgramacion(Long programacionId);

}
