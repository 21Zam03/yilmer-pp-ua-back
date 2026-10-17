package com.zam.sunat_service.service.impl;

import com.zam.sunat_service.entity.AlertaEntity;
import com.zam.sunat_service.entity.ClientEntity;
import com.zam.sunat_service.repository.AlertaRepository;
import com.zam.sunat_service.repository.VentaSireRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AlertaServiceImplTest {

    private static final String RUC = "20100070970";
    // Setiembre 2026: empieza martes; los domingos son 6, 13, 20 y 27.
    private static final String PERIODO = "202609";
    private static final YearMonth SETIEMBRE = YearMonth.of(2026, 9);

    private AlertaRepository alertaRepository;
    private VentaSireRepository ventaRepository;
    private AlertaServiceImpl service;
    private ClientEntity client;

    @BeforeEach
    void setUp() {
        alertaRepository = mock(AlertaRepository.class);
        ventaRepository = mock(VentaSireRepository.class);
        ZoneId lima = ZoneId.of("America/Lima");
        Clock hoy = Clock.fixed(ZonedDateTime.of(2026, 10, 16, 9, 0, 0, 0, lima).toInstant(), lima);
        service = new AlertaServiceImpl(alertaRepository, ventaRepository, hoy);

        client = new ClientEntity();
        client.setRuc(RUC);
        when(alertaRepository.findFirstByClienteRucAndTipoAndPeriodo(any(), any(), any())).thenReturn(Optional.empty());
    }

    @Test
    void listaLosDiasSinVentasSinContarDomingos() {
        when(ventaRepository.findFechasConVentas(client, PERIODO)).thenReturn(todosLosDiasMenos(7, 15, 22));

        service.actualizarAlertaSinVentas(client, PERIODO);

        AlertaEntity alerta = guardada();
        assertThat(alerta.getTipo()).isEqualTo("SIN_VENTAS");
        assertThat(alerta.getPeriodo()).isEqualTo(PERIODO);
        assertThat(alerta.getClienteRuc()).isEqualTo(RUC);
        assertThat(alerta.getLeida()).isFalse();
        assertThat(alerta.getMensaje()).isEqualTo("En Setiembre 2026 no hubo ventas 3 días: 07, 15 y 22.");
    }

    @Test
    void cuentaLosDomingosSiElClienteTrabajaDomingos() {
        client.setTrabajaDomingos(true);
        when(ventaRepository.findFechasConVentas(client, PERIODO)).thenReturn(todosLosDiasMenos(6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30));

        service.actualizarAlertaSinVentas(client, PERIODO);

        assertThat(guardada().getMensaje()).startsWith("En Setiembre 2026 no hubo ventas 25 días: 06, 07");
    }

    @Test
    void unSoloDia() {
        when(ventaRepository.findFechasConVentas(client, PERIODO)).thenReturn(todosLosDiasMenos(30));

        service.actualizarAlertaSinVentas(client, PERIODO);

        assertThat(guardada().getMensaje()).isEqualTo("En Setiembre 2026 no hubo ventas el día 30.");
    }

    @Test
    void ignoraFechasDeOtrosMeses() {
        List<LocalDate> fechas = todosLosDiasMenos(1);
        fechas.add(LocalDate.of(2026, 8, 31)); // comprobante de agosto anotado en setiembre
        when(ventaRepository.findFechasConVentas(client, PERIODO)).thenReturn(fechas);

        service.actualizarAlertaSinVentas(client, PERIODO);

        assertThat(guardada().getMensaje()).isEqualTo("En Setiembre 2026 no hubo ventas el día 01.");
    }

    @Test
    void sinDiasFaltantesEliminaLaAlertaAnterior() {
        AlertaEntity anterior = new AlertaEntity();
        when(alertaRepository.findFirstByClienteRucAndTipoAndPeriodo(RUC, "SIN_VENTAS", PERIODO)).thenReturn(Optional.of(anterior));
        when(ventaRepository.findFechasConVentas(client, PERIODO)).thenReturn(todosLosDiasMenos());

        service.actualizarAlertaSinVentas(client, PERIODO);

        verify(alertaRepository).delete(anterior);
        verify(alertaRepository, never()).save(any());
    }

    @Test
    void periodoSinNingunaVentaNoGeneraAlerta() {
        when(ventaRepository.findFechasConVentas(client, PERIODO)).thenReturn(List.of());

        service.actualizarAlertaSinVentas(client, PERIODO);

        verify(alertaRepository, never()).save(any());
    }

    @Test
    void mismoMensajeNoLaVuelveAMarcarComoNoLeida() {
        AlertaEntity anterior = new AlertaEntity();
        anterior.setMensaje("En Setiembre 2026 no hubo ventas el día 30.");
        anterior.setLeida(true);
        when(alertaRepository.findFirstByClienteRucAndTipoAndPeriodo(RUC, "SIN_VENTAS", PERIODO)).thenReturn(Optional.of(anterior));
        when(ventaRepository.findFechasConVentas(client, PERIODO)).thenReturn(todosLosDiasMenos(30));

        service.actualizarAlertaSinVentas(client, PERIODO);

        verify(alertaRepository, never()).save(any());
        assertThat(anterior.getLeida()).isTrue();
    }

    @Test
    void mensajeDistintoActualizaLaMismaAlerta() {
        AlertaEntity anterior = new AlertaEntity();
        anterior.setId(5L);
        anterior.setMensaje("En Setiembre 2026 no hubo ventas el día 30.");
        anterior.setLeida(true);
        when(alertaRepository.findFirstByClienteRucAndTipoAndPeriodo(RUC, "SIN_VENTAS", PERIODO)).thenReturn(Optional.of(anterior));
        when(ventaRepository.findFechasConVentas(client, PERIODO)).thenReturn(todosLosDiasMenos(29, 30));

        service.actualizarAlertaSinVentas(client, PERIODO);

        AlertaEntity alerta = guardada();
        assertThat(alerta.getId()).isEqualTo(5L);
        assertThat(alerta.getLeida()).isFalse();
        assertThat(alerta.getMensaje()).isEqualTo("En Setiembre 2026 no hubo ventas 2 días: 29 y 30.");
    }

    @Test
    void noEvaluaElMesEnCurso() {
        service.actualizarAlertaSinVentas(client, "202610");

        verifyNoInteractions(ventaRepository, alertaRepository);
    }

    private List<LocalDate> todosLosDiasMenos(int... excluidos) {
        List<LocalDate> dias = new ArrayList<>();
        for (int d = 1; d <= SETIEMBRE.lengthOfMonth(); d++) {
            final int dia = d;
            if (java.util.Arrays.stream(excluidos).noneMatch(x -> x == dia)) {
                dias.add(SETIEMBRE.atDay(d));
            }
        }
        return dias;
    }

    private AlertaEntity guardada() {
        ArgumentCaptor<AlertaEntity> captor = ArgumentCaptor.forClass(AlertaEntity.class);
        verify(alertaRepository).save(captor.capture());
        return captor.getValue();
    }

}
