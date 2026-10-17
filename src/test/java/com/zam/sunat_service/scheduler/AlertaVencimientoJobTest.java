package com.zam.sunat_service.scheduler;

import com.zam.sunat_service.entity.AlertaEntity;
import com.zam.sunat_service.entity.ClientEntity;
import com.zam.sunat_service.entity.CronogramaSunatEntity;
import com.zam.sunat_service.repository.AlertaRepository;
import com.zam.sunat_service.repository.ClientRepository;
import com.zam.sunat_service.repository.CronogramaSunatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AlertaVencimientoJobTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 16);
    private static final LocalDate OBJETIVO = LocalDate.of(2026, 10, 19);

    private CronogramaSunatRepository cronogramaRepository;
    private ClientRepository clientRepository;
    private AlertaRepository alertaRepository;
    private AlertaVencimientoJob job;

    @BeforeEach
    void setUp() {
        cronogramaRepository = mock(CronogramaSunatRepository.class);
        clientRepository = mock(ClientRepository.class);
        alertaRepository = mock(AlertaRepository.class);
        job = new AlertaVencimientoJob(cronogramaRepository, clientRepository, alertaRepository);
    }

    @Test
    void sinVencimientosNoCreaAlertas() {
        when(cronogramaRepository.findByFechaVencimiento(OBJETIVO)).thenReturn(List.of());

        assertThat(job.generarAlertas(HOY)).isZero();
        verifyNoInteractions(clientRepository, alertaRepository);
    }

    @Test
    void creaUnaAlertaPorClienteCuyoRucTerminaEnElDigito() {
        when(cronogramaRepository.findByFechaVencimiento(OBJETIVO))
                .thenReturn(List.of(cronograma("202609", 3), cronograma("202609", 4)));
        when(clientRepository.findActivosPorUltimoDigitoRuc("3")).thenReturn(List.of(cliente("20100070973"), cliente("10733495273")));
        when(clientRepository.findActivosPorUltimoDigitoRuc("4")).thenReturn(List.of(cliente("20999999994")));

        assertThat(job.generarAlertas(HOY)).isEqualTo(3);

        ArgumentCaptor<AlertaEntity> captor = ArgumentCaptor.forClass(AlertaEntity.class);
        verify(alertaRepository, times(3)).save(captor.capture());
        AlertaEntity alerta = captor.getAllValues().get(0);
        assertThat(alerta.getClienteRuc()).isEqualTo("20100070973");
        assertThat(alerta.getTipo()).isEqualTo("VENCIMIENTO");
        assertThat(alerta.getPeriodo()).isEqualTo("202609");
        assertThat(alerta.getLeida()).isFalse();
        assertThat(alerta.getMensaje()).isEqualTo("El periodo 202609 vence en 3 días. Recuerda declarar a tiempo.");
    }

    @Test
    void noDuplicaAlertasSiElJobCorreDosVeces() {
        when(cronogramaRepository.findByFechaVencimiento(OBJETIVO)).thenReturn(List.of(cronograma("202609", 3)));
        when(clientRepository.findActivosPorUltimoDigitoRuc("3")).thenReturn(List.of(cliente("20100070973"), cliente("10733495273")));
        when(alertaRepository.existsByClienteRucAndTipoAndPeriodo("20100070973", "VENCIMIENTO", "202609")).thenReturn(true);

        assertThat(job.generarAlertas(HOY)).isEqualTo(1);
        verify(alertaRepository, times(1)).save(any());
    }

    private CronogramaSunatEntity cronograma(String periodo, int digito) {
        CronogramaSunatEntity c = new CronogramaSunatEntity();
        c.setPeriodo(periodo);
        c.setUltimoDigitoRuc(digito);
        c.setFechaVencimiento(OBJETIVO);
        return c;
    }

    private ClientEntity cliente(String ruc) {
        ClientEntity c = new ClientEntity();
        c.setRuc(ruc);
        c.setEnabled(true);
        return c;
    }

}
