package com.zam.sunat_service.response.sunat;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Respuesta del servicio 5.16 (consultar estado de tickets). Estructura según el manual del SIRE:
 * <pre>
 * {"paginacion": {...},
 *  "registros": [{"numTicket": "...", "perTributario": "202610", "codProceso": "10",
 *                 "codEstadoProceso": "06", "desEstadoProceso": "Terminado",
 *                 "detalleTicket": {"cntCPInformados": 15, ...},
 *                 "archivoReporte": [{"codTipoAchivoReporte": "00", "nomArchivoReporte": "LE...zip"}]}]}
 * </pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SireConsultaTicketsResponse(List<Registro> registros) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Registro(
            String numTicket,
            String perTributario,
            String codProceso,
            String desProceso,
            String codEstadoProceso,
            String desEstadoProceso,
            DetalleTicket detalleTicket,
            List<ArchivoReporte> archivoReporte
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DetalleTicket(
            String codEstadoEnvio,
            String desEstadoEnvio,
            Integer cntCPInformados
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ArchivoReporte(
            // SUNAT lo devuelve con la errata "codTipoAchivoReporte"; se aceptan ambas formas.
            @JsonAlias({"codTipoAchivoReporte", "codTipoArchivoReporte"}) String codTipoArchivoReporte,
            String nomArchivoReporte,
            String nomArchivoContenido
    ) {
    }

}
