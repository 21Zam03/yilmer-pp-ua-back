package com.zam.sunat_service.response.sunat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Respuesta exitosa de POST /v1/clientessol/{client_id}/oauth2/token.
 * Ej: {"access_token":"eyJ...","token_type":"JWT","expires_in":3600}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SunatTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") Long expiresIn
) {
}
