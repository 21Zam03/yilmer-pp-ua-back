package com.zam.sunat_service.service.impl;

import com.zam.sunat_service.entity.AlertaEntity;
import com.zam.sunat_service.entity.ClientEntity;
import com.zam.sunat_service.exceptions.NotFoundException;
import com.zam.sunat_service.repository.AlertaRepository;
import com.zam.sunat_service.repository.VentaSireRepository;
import com.zam.sunat_service.response.AlertaResponse;
import com.zam.sunat_service.response.PageResponse;
import com.zam.sunat_service.service.AlertaService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
public class AlertaServiceImpl implements AlertaService {

    private static final DateTimeFormatter PERIODO = DateTimeFormatter.ofPattern("yyyyMM");
    private static final int MAX_PAGE_SIZE = 100;
    private static final String[] MESES = {"Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio", "Agosto",
            "Setiembre", "Octubre", "Noviembre", "Diciembre"};

    private final AlertaRepository alertaRepository;
    private final VentaSireRepository ventaRepository;
    private final Clock clock;

    @Autowired
    public AlertaServiceImpl(AlertaRepository alertaRepository, VentaSireRepository ventaRepository) {
        this(alertaRepository, ventaRepository, Clock.system(ZoneId.of("America/Lima")));
    }

    // Para tests: permite fijar "hoy".
    AlertaServiceImpl(AlertaRepository alertaRepository, VentaSireRepository ventaRepository, Clock clock) {
        this.alertaRepository = alertaRepository;
        this.ventaRepository = ventaRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void actualizarAlertaSinVentas(ClientEntity client, String periodo) {
        YearMonth mes = YearMonth.parse(periodo, PERIODO);
        if (!mes.isBefore(YearMonth.now(clock))) {
            return; // periodo abierto
        }

        Optional<AlertaEntity> existente = alertaRepository.findFirstByClienteRucAndTipoAndPeriodo(
                client.getRuc(), AlertaEntity.TIPO_SIN_VENTAS, periodo);

        // Puede traer fechas de otros meses (comprobantes emitidos antes y anotados en este periodo): se ignoran.
        Set<LocalDate> conVentas = new HashSet<>(ventaRepository.findFechasConVentas(client, periodo));
        conVentas.removeIf(fecha -> !YearMonth.from(fecha).equals(mes));

        List<LocalDate> sinVentas = new ArrayList<>();
        if (!conVentas.isEmpty()) {
            for (LocalDate dia = mes.atDay(1); !dia.isAfter(mes.atEndOfMonth()); dia = dia.plusDays(1)) {
                boolean domingoLibre = dia.getDayOfWeek() == DayOfWeek.SUNDAY && !client.isTrabajaDomingos();
                if (!domingoLibre && !conVentas.contains(dia)) {
                    sinVentas.add(dia);
                }
            }
        }

        if (sinVentas.isEmpty()) {
            existente.ifPresent(alerta -> {
                alertaRepository.delete(alerta);
                log.info("Alerta SIN_VENTAS {} {}: eliminada (ya no hay días sin ventas)", client.getRuc(), periodo);
            });
            return;
        }

        String mensaje = mensaje(mes, sinVentas);
        AlertaEntity alerta = existente.orElseGet(AlertaEntity::new);
        if (mensaje.equals(alerta.getMensaje())) {
            return; // misma información: no se vuelve a marcar como no leída
        }
        alerta.setClienteRuc(client.getRuc());
        alerta.setPeriodo(periodo);
        alerta.setTipo(AlertaEntity.TIPO_SIN_VENTAS);
        alerta.setMensaje(mensaje);
        alerta.setLeida(false);
        alerta.setFechaCreacion(LocalDateTime.now(clock));
        alertaRepository.save(alerta);
        log.info("Alerta SIN_VENTAS {} {}: {} día(s) sin ventas", client.getRuc(), periodo, sinVentas.size());
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AlertaResponse> listar(Boolean leida, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE));
        return PageResponse.from(alertaRepository.findByLeida(leida, pageable));
    }

    @Override
    @Transactional(readOnly = true)
    public long contarNoLeidas() {
        return alertaRepository.countByLeidaFalse();
    }

    @Override
    @Transactional
    public void marcarLeida(Long alertaId) {
        AlertaEntity alerta = alertaRepository.findById(alertaId)
                .orElseThrow(() -> new NotFoundException("No se encontró la alerta con id " + alertaId));
        if (!Boolean.TRUE.equals(alerta.getLeida())) {
            alerta.setLeida(true);
            alertaRepository.save(alerta);
        }
    }

    @Override
    @Transactional
    public int marcarTodasLeidas() {
        return alertaRepository.marcarTodasLeidas();
    }

    // "En Setiembre 2026 no hubo ventas 3 días: 07, 15 y 22."
    static String mensaje(YearMonth mes, List<LocalDate> dias) {
        String nombreMes = MESES[mes.getMonthValue() - 1] + " " + mes.getYear();
        List<String> numeros = dias.stream().map(d -> String.format("%02d", d.getDayOfMonth())).toList();
        if (numeros.size() == 1) {
            return "En " + nombreMes + " no hubo ventas el día " + numeros.get(0) + ".";
        }
        String lista = String.join(", ", numeros.subList(0, numeros.size() - 1)) + " y " + numeros.get(numeros.size() - 1);
        return "En " + nombreMes + " no hubo ventas " + numeros.size() + " días: " + lista + ".";
    }

}
