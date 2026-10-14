package com.zam.sunat_service.response.sunat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Respuesta del servicio 5.18 (descargar propuesta RVIE). Ej: {"numTicket":"20260300000007"}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SireTicketResponse(String numTicket) {
}
