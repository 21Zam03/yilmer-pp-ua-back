package com.zam.sunat_service.integration;

import com.zam.sunat_service.exceptions.SunatApiException;
import com.zam.sunat_service.response.sunat.SireConsultaTicketsResponse;
import com.zam.sunat_service.response.sunat.SireErrorResponse;
import com.zam.sunat_service.response.sunat.SireTicketResponse;
import com.zam.sunat_service.service.SunatAuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.function.Function;

/**
 * Llamadas HTTP a la API del SIRE (ver resources/API SUNAT.postman_collection.json).
 * Obtiene el token con SunatAuthService y, si SUNAT responde 401, lo renueva y reintenta una vez.
 */
@Slf4j
@Component
public class SireApiClient {

    // Código de libro del RVIE (Registro de Ventas e Ingresos Electrónico).
    public static final String COD_LIBRO_RVIE = "140000";
    // 0 = TXT, 1 = Excel.
    private static final String COD_TIPO_ARCHIVO_TXT = "0";

    private final RestTemplate restTemplate;
    private final SunatAuthService sunatAuthService;
    private final String baseUrl;

    @Autowired
    public SireApiClient(RestTemplate sunatRestTemplate,
                         SunatAuthService sunatAuthService,
                         @Value("${sunat.sire.base-url}") String baseUrl) {
        this.restTemplate = sunatRestTemplate;
        this.sunatAuthService = sunatAuthService;
        this.baseUrl = baseUrl;
    }

    /**
     * Servicio 5.18: solicita la descarga de la propuesta RVIE del periodo. Devuelve el número de ticket.
     */
    public String generarTicketPropuestaVentas(String ruc, String periodo) {
        SireTicketResponse response = conToken(ruc, "generar el ticket de ventas", token -> restTemplate.exchange(
                baseUrl + "/rvie/propuesta/web/propuesta/{periodo}/exportapropuesta?codTipoArchivo={codTipoArchivo}",
                HttpMethod.GET, request(token, MediaType.APPLICATION_JSON), SireTicketResponse.class,
                periodo, COD_TIPO_ARCHIVO_TXT
        ).getBody());

        if (response == null || response.numTicket() == null || response.numTicket().isBlank()) {
            throw new SunatApiException("SUNAT no devolvió un número de ticket para el periodo " + periodo);
        }
        return response.numTicket();
    }

    /**
     * Servicio 5.16: consulta el estado de un ticket. Devuelve null si SUNAT aún no lo lista.
     */
    public SireConsultaTicketsResponse.Registro consultarEstadoTicket(String ruc, String periodo, String numTicket) {
        SireConsultaTicketsResponse response = conToken(ruc, "consultar el ticket " + numTicket, token -> restTemplate.exchange(
                baseUrl + "/rvierce/gestionprocesosmasivos/web/masivo/consultaestadotickets"
                        + "?perIni={periodo}&perFin={periodo}&page=1&perPage=20&numTicket={numTicket}",
                HttpMethod.GET, request(token, MediaType.APPLICATION_JSON), SireConsultaTicketsResponse.class,
                periodo, periodo, numTicket
        ).getBody());

        if (response == null || response.registros() == null) {
            return null;
        }
        return response.registros().stream()
                .filter(r -> numTicket.equals(r.numTicket()))
                .findFirst()
                .orElse(null);
    }

    /**
     * Servicio 5.17: descarga el archivo (ZIP) generado por el ticket.
     */
    public byte[] descargarArchivo(String ruc, SireConsultaTicketsResponse.Registro registro,
                                   SireConsultaTicketsResponse.ArchivoReporte archivo) {
        ResponseEntity<byte[]> respuesta = conToken(ruc, "descargar el archivo " + archivo.nomArchivoReporte(), token -> restTemplate.exchange(
                baseUrl + "/rvierce/gestionprocesosmasivos/web/masivo/archivoreporte"
                        + "?nomArchivoReporte={nomArchivo}&codTipoArchivoReporte={codTipo}&codLibro={codLibro}"
                        + "&numTicket={numTicket}&perTributario={periodo}&codProceso={codProceso}",
                HttpMethod.GET, request(token, MediaType.ALL), byte[].class,
                archivo.nomArchivoReporte(), archivo.codTipoArchivoReporte(), COD_LIBRO_RVIE,
                registro.numTicket(), registro.perTributario(), registro.codProceso()
        ));
        byte[] contenido = respuesta.getBody();
        log.info("SIRE descarga {}: Content-Type={}, Content-Encoding={}, {} bytes", archivo.nomArchivoReporte(),
                respuesta.getHeaders().getContentType(), respuesta.getHeaders().getFirst(HttpHeaders.CONTENT_ENCODING),
                contenido != null ? contenido.length : 0);

        // Vacío no es error: SireServiceImpl lo trata como periodo sin movimientos.
        return contenido != null ? contenido : new byte[0];
    }

    private <T> T conToken(String ruc, String operacion, Function<String, T> llamada) {
        try {
            try {
                return llamada.apply(sunatAuthService.obtenerTokenValido(ruc));
            } catch (HttpClientErrorException.Unauthorized e) {
                // El token guardado dejó de ser válido antes de tiempo: se pide otro y se reintenta una vez.
                log.info("SUNAT rechazó el token del RUC {} al {}; se renueva y reintenta", ruc, operacion);
                sunatAuthService.invalidarToken(ruc);
                return llamada.apply(sunatAuthService.obtenerTokenValido(ruc));
            }
        } catch (HttpStatusCodeException e) {
            String detalle = describirError(e);
            log.warn("SIRE respondió {} al {} (RUC {}): {}", e.getStatusCode(), operacion, ruc, detalle);
            throw new SunatApiException("SUNAT respondió con error al " + operacion + ": " + detalle, e);
        } catch (RestClientException e) {
            log.error("No se pudo conectar con el SIRE al {} (RUC {})", operacion, ruc, e);
            throw new SunatApiException("No se pudo conectar con SUNAT al " + operacion + ". Intente nuevamente en unos minutos", e);
        }
    }

    private HttpEntity<Void> request(String token, MediaType accept) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setAccept(List.of(accept));
        return new HttpEntity<>(headers);
    }

    private String describirError(HttpStatusCodeException e) {
        try {
            SireErrorResponse error = e.getResponseBodyAs(SireErrorResponse.class);
            if (error != null && error.descripcion() != null) {
                return error.descripcion();
            }
        } catch (RuntimeException ignored) {
            // Cuerpo no JSON (ej. página HTML de error del balanceador).
        }
        return e.getStatusCode() + " " + e.getStatusText();
    }

}
