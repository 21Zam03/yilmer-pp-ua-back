package com.zam.sunat_service.response.sunat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Error de las APIs del SIRE. Ej: {"cod":"422","msg":"Error de validación","errors":[{"cod":"1070","msg":"..."}]}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SireErrorResponse(String cod, String msg, List<Detalle> errors) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Detalle(String cod, String msg) {
    }

    // El detalle suele ser más útil que el mensaje general.
    public String descripcion() {
        if (errors != null && !errors.isEmpty() && errors.get(0).msg() != null) {
            return errors.get(0).msg();
        }
        return msg;
    }

}
