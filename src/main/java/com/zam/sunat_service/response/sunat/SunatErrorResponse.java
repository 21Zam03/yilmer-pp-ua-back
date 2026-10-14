package com.zam.sunat_service.response.sunat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Error OAuth2 que devuelve SUNAT (400/401).
 * Ej: {"error":"invalid_grant","error_description":"Usuario o contraseña incorrectos"}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SunatErrorResponse(
        @JsonProperty("error") String error,
        @JsonProperty("error_description") String errorDescription
) {
}
