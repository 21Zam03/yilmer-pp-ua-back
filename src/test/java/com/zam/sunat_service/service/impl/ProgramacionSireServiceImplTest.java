package com.zam.sunat_service.service.impl;

import com.zam.sunat_service.entity.ClientEntity;
import com.zam.sunat_service.entity.ProgramacionSireEntity;
import com.zam.sunat_service.entity.TipoLibroSire;
import com.zam.sunat_service.exceptions.BusinessException;
import com.zam.sunat_service.exceptions.SunatApiException;
import com.zam.sunat_service.repository.ClientRepository;
import com.zam.sunat_service.repository.ProgramacionSireRepository;
import com.zam.sunat_service.request.ProgramacionSireRequest;
import com.zam.sunat_service.request.SireSyncRequest;
import com.zam.sunat_service.response.ProgramacionSireResponse;
import com.zam.sunat_service.service.SireService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ProgramacionSireServiceImplTest {

    private static final String RUC = "10733495273";

    private ProgramacionSireRepository programacionRepository;
    private ClientRepository clientRepository;
    private SireService sireService;
    private ProgramacionSireServiceImpl service;
    private ClientEntity client;

    @BeforeEach
    void setUp() {
        programacionRepository = mock(ProgramacionSireRepository.class);
        clientRepository = mock(ClientRepository.class);
        sireService = mock(SireService.class);
        service = new ProgramacionSireServiceImpl(programacionRepository, clientRepository, sireService);

        client = new ClientEntity();
        client.setRuc(RUC);
        client.setEnabled(true);
        client.setSunatClientId("id");
        client.setSunatClientSecret("secret");
        when(clientRepository.findByRuc(RUC)).thenReturn(Optional.of(client));
        when(programacionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void sinConfigurarDevuelveInactiva() {
        when(programacionRepository.findByClientAndTipo(client, TipoLibroSire.VENTAS)).thenReturn(Optional.empty());

        ProgramacionSireResponse r = service.obtenerProgramacionVentas(RUC);

        assertThat(r.activo()).isFalse();
        assertThat(r.intervaloHoras()).isNull();
    }

    @Test
    void activarPorPrimeraVezProgramaLaEjecucionInmediata() {
        when(programacionRepository.findByClientAndTipo(client, TipoLibroSire.VENTAS)).thenReturn(Optional.empty());

        ProgramacionSireResponse r = service.guardarProgramacionVentas(request(true, 6));

        assertThat(r.activo()).isTrue();
        assertThat(r.intervaloHoras()).isEqualTo(6);
        assertThat(r.proximaEjecucion()).isBeforeOrEqualTo(LocalDateTime.now());
    }

    @Test
    void reactivarRespetaLaUltimaEjecucion() {
        ProgramacionSireEntity p = programacion(false, 3);
        LocalDateTime ultima = LocalDateTime.now().minusHours(1);
        p.setUltimaEjecucion(ultima);
        when(programacionRepository.findByClientAndTipo(client, TipoLibroSire.VENTAS)).thenReturn(Optional.of(p));

        ProgramacionSireResponse r = service.guardarProgramacionVentas(request(true, 12));

        assertThat(r.proximaEjecucion()).isEqualTo(ultima.plusHours(12));
    }

    @Test
    void rechazaIntervaloNoPermitido() {
        assertThatThrownBy(() -> service.guardarProgramacionVentas(request(true, 5)))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void noActivaSinCredencialesApi() {
        client.setSunatClientSecret(null);

        assertThatThrownBy(() -> service.guardarProgramacionVentas(request(true, 1)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("credenciales");
    }

    @Test
    void desactivarNoExigeCredenciales() {
        client.setSunatClientSecret(null);
        when(programacionRepository.findByClientAndTipo(client, TipoLibroSire.VENTAS)).thenReturn(Optional.of(programacion(true, 1)));

        assertThat(service.guardarProgramacionVentas(request(false, 1)).activo()).isFalse();
    }

    @Test
    void ejecutarSincronizaElPeriodoAnteriorYReprograma() {
        ProgramacionSireEntity p = programacion(true, 3);
        p.setErroresConsecutivos(2);
        when(programacionRepository.findWithClientById(1L)).thenReturn(Optional.of(p));
        when(programacionRepository.reservar(eq(1L), any(), any())).thenReturn(1);

        service.ejecutarProgramacion(1L);

        ArgumentCaptor<SireSyncRequest> captor = ArgumentCaptor.forClass(SireSyncRequest.class);
        verify(sireService).sincronizarVentas(captor.capture());
        assertThat(captor.getValue().getRuc()).isEqualTo(RUC);
        assertThat(captor.getValue().getPeriodo())
                .isEqualTo(YearMonth.now().minusMonths(1).format(DateTimeFormatter.ofPattern("yyyyMM")));
        assertThat(p.getErroresConsecutivos()).isZero();
        assertThat(p.getUltimaEjecucion()).isNotNull();
        assertThat(p.getProximaEjecucion()).isBetween(
                LocalDateTime.now().plusHours(3).minusMinutes(1), LocalDateTime.now().plusHours(3).plusMinutes(6));
    }

    @Test
    void noEjecutaSiOtraInstanciaLaReservo() {
        when(programacionRepository.findWithClientById(1L)).thenReturn(Optional.of(programacion(true, 1)));
        when(programacionRepository.reservar(eq(1L), any(), any())).thenReturn(0);

        service.ejecutarProgramacion(1L);

        verifyNoInteractions(sireService);
        verify(programacionRepository, never()).save(any());
    }

    @Test
    void sePausaTrasTresErroresSeguidos() {
        ProgramacionSireEntity p = programacion(true, 1);
        when(programacionRepository.findWithClientById(1L)).thenReturn(Optional.of(p));
        when(programacionRepository.reservar(eq(1L), any(), any())).thenReturn(1);
        when(sireService.sincronizarVentas(any())).thenThrow(new SunatApiException("SUNAT rechazó las credenciales"));

        service.ejecutarProgramacion(1L);
        service.ejecutarProgramacion(1L);
        assertThat(p.isActivo()).isTrue();
        assertThat(p.getErroresConsecutivos()).isEqualTo(2);

        service.ejecutarProgramacion(1L);
        assertThat(p.isActivo()).isFalse();
        assertThat(p.getUltimoError()).contains("credenciales");
    }

    private ProgramacionSireRequest request(boolean activo, int intervalo) {
        ProgramacionSireRequest r = new ProgramacionSireRequest();
        r.setRuc(RUC);
        r.setActivo(activo);
        r.setIntervaloHoras(intervalo);
        return r;
    }

    private ProgramacionSireEntity programacion(boolean activo, int intervalo) {
        ProgramacionSireEntity p = new ProgramacionSireEntity();
        p.setProgramacionId(1L);
        p.setClient(client);
        p.setTipo(TipoLibroSire.VENTAS);
        p.setActivo(activo);
        p.setIntervaloHoras(intervalo);
        p.setProximaEjecucion(LocalDateTime.now().minusMinutes(1));
        return p;
    }

}
