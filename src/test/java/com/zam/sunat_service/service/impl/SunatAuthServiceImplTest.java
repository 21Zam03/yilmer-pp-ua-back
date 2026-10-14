package com.zam.sunat_service.service.impl;

import com.zam.sunat_service.entity.ClientEntity;
import com.zam.sunat_service.exceptions.BusinessException;
import com.zam.sunat_service.exceptions.SunatApiException;
import com.zam.sunat_service.repository.ClientRepository;
import com.zam.sunat_service.util.SolPasswordEncryptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SunatAuthServiceImplTest {

    private static final String TOKEN_URL = "https://api-seguridad.sunat.gob.pe/v1/clientessol/{client_id}/oauth2/token";
    private static final String RUC = "10733495273";
    private static final String CLIENT_ID = "abc-123";

    private final SolPasswordEncryptor encryptor = new SolPasswordEncryptor("D/JpGrIbqVUUpPD22Tks/trZD6xAhRKgDSnxqRKwsb8=");
    private ClientRepository repository;
    private MockRestServiceServer sunat;
    private SunatAuthServiceImpl service;
    private ClientEntity client;

    @BeforeEach
    void setUp() {
        repository = mock(ClientRepository.class);
        RestTemplate restTemplate = new RestTemplate();
        sunat = MockRestServiceServer.bindTo(restTemplate).build();
        service = new SunatAuthServiceImpl(repository, encryptor, restTemplate, TOKEN_URL);

        client = new ClientEntity();
        client.setRuc(RUC);
        client.setSolUser("MODDATOS");
        client.setSolPassword(encryptor.encrypt("moddatos"));
        client.setSunatClientId(CLIENT_ID);
        client.setSunatClientSecret(encryptor.encrypt("secreto"));
        when(repository.findByRuc(RUC)).thenReturn(Optional.of(client));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void reutilizaTokenVigenteSinLlamarASunat() {
        client.setAccessToken("token-guardado");
        client.setTokenExpiraEl(LocalDateTime.now().plusMinutes(10));

        assertThat(service.obtenerTokenValido(RUC)).isEqualTo("token-guardado");
        sunat.verify();
        verify(repository, never()).save(any());
    }

    @Test
    void pideTokenNuevoCuandoExpiroYLoGuardaCon58Minutos() {
        client.setAccessToken("token-viejo");
        client.setTokenExpiraEl(LocalDateTime.now().minusSeconds(1));

        MultiValueMap<String, String> expectedBody = new LinkedMultiValueMap<>();
        expectedBody.add("grant_type", "password");
        expectedBody.add("scope", "https://api.sunat.gob.pe");
        expectedBody.add("client_id", CLIENT_ID);
        expectedBody.add("client_secret", "secreto");
        expectedBody.add("username", "10733495273MODDATOS");
        expectedBody.add("password", "moddatos");

        sunat.expect(requestTo("https://api-seguridad.sunat.gob.pe/v1/clientessol/abc-123/oauth2/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().formData(expectedBody))
                .andRespond(withSuccess("{\"access_token\":\"token-nuevo\",\"token_type\":\"JWT\",\"expires_in\":3600}",
                        MediaType.APPLICATION_JSON));

        LocalDateTime antes = LocalDateTime.now();
        assertThat(service.obtenerTokenValido(RUC)).isEqualTo("token-nuevo");

        sunat.verify();
        assertThat(client.getAccessToken()).isEqualTo("token-nuevo");
        assertThat(client.getTokenExpiraEl())
                .isAfterOrEqualTo(antes.plusMinutes(58))
                .isBefore(antes.plusMinutes(58).plusSeconds(5));
        verify(repository).save(client);
    }

    @Test
    void credencialesRechazadasLanzaSunatApiExceptionConElDetalle() {
        sunat.expect(requestTo("https://api-seguridad.sunat.gob.pe/v1/clientessol/abc-123/oauth2/token"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"invalid_grant\",\"error_description\":\"Usuario o contraseña incorrectos\"}"));

        assertThatThrownBy(() -> service.obtenerTokenValido(RUC))
                .isInstanceOf(SunatApiException.class)
                .hasMessageContaining("Usuario o contraseña incorrectos");
        verify(repository, never()).save(any());
    }

    @Test
    void sinClientIdNoLlamaASunat() {
        client.setSunatClientId(null);

        assertThatThrownBy(() -> service.obtenerTokenValido(RUC)).isInstanceOf(BusinessException.class);
        sunat.verify();
    }

}
