package com.zam.sunat_service.service.impl;

import com.zam.sunat_service.entity.ClientEntity;
import com.zam.sunat_service.exceptions.BusinessException;
import com.zam.sunat_service.exceptions.NotFoundException;
import com.zam.sunat_service.exceptions.SunatApiException;
import com.zam.sunat_service.repository.ClientRepository;
import com.zam.sunat_service.response.sunat.SunatErrorResponse;
import com.zam.sunat_service.response.sunat.SunatTokenResponse;
import com.zam.sunat_service.service.SunatAuthService;
import com.zam.sunat_service.util.SolPasswordEncryptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class SunatAuthServiceImpl implements SunatAuthService {

    private static final String GRANT_TYPE = "password";
    private static final String SCOPE = "https://api.sunat.gob.pe";
    // Se renueva 2 minutos antes de que SUNAT lo invalide (3600 s -> se guarda como válido 58 min).
    private static final long SAFETY_MARGIN_SECONDS = 120;
    private static final long DEFAULT_EXPIRES_IN_SECONDS = 3600;

    private final ClientRepository clientRepository;
    private final SolPasswordEncryptor encryptor;
    private final RestTemplate restTemplate;
    private final String tokenUrl;

    // Un lock por RUC: si llegan varias peticiones a la vez con el token vencido, solo una llama a SUNAT.
    private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

    @Autowired
    public SunatAuthServiceImpl(ClientRepository clientRepository,
                                SolPasswordEncryptor encryptor,
                                RestTemplate sunatRestTemplate,
                                @Value("${sunat.api.token-url}") String tokenUrl) {
        this.clientRepository = clientRepository;
        this.encryptor = encryptor;
        this.restTemplate = sunatRestTemplate;
        this.tokenUrl = tokenUrl;
    }

    @Override
    public String obtenerTokenValido(String ruc) {
        ClientEntity client = findClient(ruc);
        if (isTokenVigente(client)) {
            return client.getAccessToken();
        }

        synchronized (locks.computeIfAbsent(ruc, k -> new Object())) {
            // Otro hilo pudo haberlo renovado mientras esperábamos el lock.
            client = findClient(ruc);
            if (isTokenVigente(client)) {
                return client.getAccessToken();
            }

            SunatTokenResponse token = solicitarToken(client);
            long expiresIn = token.expiresIn() != null ? token.expiresIn() : DEFAULT_EXPIRES_IN_SECONDS;

            client.setAccessToken(token.accessToken());
            client.setTokenExpiraEl(LocalDateTime.now().plusSeconds(expiresIn - SAFETY_MARGIN_SECONDS));
            clientRepository.save(client);

            log.info("Nuevo token SUNAT generado para RUC {} (vence {})", ruc, client.getTokenExpiraEl());
            return token.accessToken();
        }
    }

    @Override
    public void invalidarToken(String ruc) {
        synchronized (locks.computeIfAbsent(ruc, k -> new Object())) {
            clientRepository.findByRuc(ruc).ifPresent(client -> {
                client.setAccessToken(null);
                client.setTokenExpiraEl(null);
                clientRepository.save(client);
            });
        }
    }

    private boolean isTokenVigente(ClientEntity client) {
        return client.getAccessToken() != null
                && client.getTokenExpiraEl() != null
                && client.getTokenExpiraEl().isAfter(LocalDateTime.now());
    }

    private ClientEntity findClient(String ruc) {
        ClientEntity client = clientRepository.findByRuc(ruc)
                .orElseThrow(() -> new NotFoundException("No existe un cliente con el RUC " + ruc));
        if (client.getSunatClientId() == null || client.getSunatClientId().isBlank()
                || client.getSunatClientSecret() == null || client.getSunatClientSecret().isBlank()) {
            throw new BusinessException("El cliente " + ruc + " no tiene configuradas sus credenciales de API SUNAT (client_id / client_secret)");
        }
        return client;
    }

    private SunatTokenResponse solicitarToken(ClientEntity client) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", GRANT_TYPE);
        body.add("scope", SCOPE);
        body.add("client_id", client.getSunatClientId());
        body.add("client_secret", encryptor.decrypt(client.getSunatClientSecret()));
        body.add("username", client.getRuc() + client.getSolUser());
        body.add("password", encryptor.decrypt(client.getSolPassword()));

        try {
            SunatTokenResponse response = restTemplate.postForObject(
                    tokenUrl, new HttpEntity<>(body, headers), SunatTokenResponse.class, client.getSunatClientId()
            );
            if (response == null || response.accessToken() == null || response.accessToken().isBlank()) {
                throw new SunatApiException("SUNAT no devolvió un access_token para el RUC " + client.getRuc());
            }
            return response;
        } catch (HttpStatusCodeException e) {
            // 400/401: credenciales SOL o de API inválidas. No se reintenta para no provocar el bloqueo de la clave SOL.
            SunatErrorResponse error = parseError(e);
            String detalle = error != null && error.errorDescription() != null ? error.errorDescription() : e.getStatusText();
            log.warn("SUNAT rechazó la autenticación del RUC {}: {} {}", client.getRuc(), e.getStatusCode(), detalle);
            throw new SunatApiException("SUNAT rechazó las credenciales del RUC " + client.getRuc() + ": " + detalle, e);
        } catch (RestClientException e) {
            log.error("No se pudo conectar con SUNAT para el RUC {}", client.getRuc(), e);
            throw new SunatApiException("No se pudo conectar con SUNAT. Intente nuevamente en unos minutos", e);
        }
    }

    private SunatErrorResponse parseError(HttpStatusCodeException e) {
        try {
            return e.getResponseBodyAs(SunatErrorResponse.class);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

}
