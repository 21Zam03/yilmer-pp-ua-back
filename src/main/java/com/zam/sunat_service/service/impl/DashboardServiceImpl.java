package com.zam.sunat_service.service.impl;

import com.zam.sunat_service.entity.EstadoTicketSire;
import com.zam.sunat_service.repository.ClientRepository;
import com.zam.sunat_service.repository.ProgramacionSireRepository;
import com.zam.sunat_service.repository.TicketSireRepository;
import com.zam.sunat_service.repository.VentaSireRepository;
import com.zam.sunat_service.response.DashboardClientesResponse;
import com.zam.sunat_service.response.DashboardSireResponse;
import com.zam.sunat_service.response.DashboardSireResponse.ErrorSincronizacion;
import com.zam.sunat_service.response.DashboardSireResponse.ProgramacionItem;
import com.zam.sunat_service.response.DashboardSireResponse.Programaciones;
import com.zam.sunat_service.response.DashboardSireResponse.Sincronizaciones;
import com.zam.sunat_service.response.DashboardTendenciaResponse;
import com.zam.sunat_service.response.DashboardTopClienteResponse;
import com.zam.sunat_service.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class DashboardServiceImpl implements DashboardService {

    private static final DateTimeFormatter PERIODO = DateTimeFormatter.ofPattern("yyyyMM");
    private static final int MESES_TENDENCIA = 12;
    private static final int DIAS_SINCRONIZACIONES = 7;
    private static final int TOP_CLIENTES = 5;
    private static final int MAX_ITEMS_LISTA = 5;

    private final ClientRepository clientRepository;
    private final VentaSireRepository ventaRepository;
    private final TicketSireRepository ticketRepository;
    private final ProgramacionSireRepository programacionRepository;

    @Autowired
    public DashboardServiceImpl(ClientRepository clientRepository,
                                VentaSireRepository ventaRepository,
                                TicketSireRepository ticketRepository,
                                ProgramacionSireRepository programacionRepository) {
        this.clientRepository = clientRepository;
        this.ventaRepository = ventaRepository;
        this.ticketRepository = ticketRepository;
        this.programacionRepository = programacionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardClientesResponse obtenerResumenClientes() {
        long activos = clientRepository.countByEnabled(true);
        long inactivos = clientRepository.countByEnabled(false);
        return new DashboardClientesResponse(activos + inactivos, activos, inactivos,
                clientRepository.countActivosSinCredencialesApi());
    }

    @Override
    @Transactional(readOnly = true)
    public DashboardSireResponse obtenerResumenSire() {
        YearMonth mesAnterior = YearMonth.now().minusMonths(1);
        String periodo = mesAnterior.format(PERIODO);

        List<DashboardTopClienteResponse> top = ventaRepository.topClientes(periodo, PageRequest.of(0, TOP_CLIENTES));

        return new DashboardSireResponse(
                periodo,
                ventaRepository.resumenPeriodo(periodo),
                ventaRepository.countClientesConVentas(periodo),
                top,
                tendencia(mesAnterior),
                sincronizaciones(),
                programaciones()
        );
    }

    // Termina en el mes anterior (el mes en curso aún no tiene propuesta completa) y rellena con cero los meses sin datos.
    private List<DashboardTendenciaResponse> tendencia(YearMonth hasta) {
        YearMonth desde = hasta.minusMonths(MESES_TENDENCIA - 1L);
        Map<String, DashboardTendenciaResponse> porPeriodo = ventaRepository
                .tendencia(desde.format(PERIODO), hasta.format(PERIODO)).stream()
                .collect(Collectors.toMap(DashboardTendenciaResponse::periodo, Function.identity()));

        List<DashboardTendenciaResponse> meses = new ArrayList<>(MESES_TENDENCIA);
        for (YearMonth mes = desde; !mes.isAfter(hasta); mes = mes.plusMonths(1)) {
            String p = mes.format(PERIODO);
            meses.add(porPeriodo.getOrDefault(p, new DashboardTendenciaResponse(p, 0, BigDecimal.ZERO, 0)));
        }
        return meses;
    }

    private Sincronizaciones sincronizaciones() {
        Map<EstadoTicketSire, Long> porEstado = new EnumMap<>(EstadoTicketSire.class);
        for (Object[] fila : ticketRepository.contarPorEstadoDesde(LocalDateTime.now().minusDays(DIAS_SINCRONIZACIONES))) {
            porEstado.put((EstadoTicketSire) fila[0], (Long) fila[1]);
        }
        Page<ErrorSincronizacion> errores = ticketRepository
                .findErroresVigentes(PageRequest.of(0, MAX_ITEMS_LISTA))
                .map(ErrorSincronizacion::from);

        return new Sincronizaciones(
                DIAS_SINCRONIZACIONES,
                porEstado.getOrDefault(EstadoTicketSire.PENDIENTE, 0L) + porEstado.getOrDefault(EstadoTicketSire.DESCARGANDO, 0L),
                porEstado.getOrDefault(EstadoTicketSire.COMPLETADO, 0L),
                porEstado.getOrDefault(EstadoTicketSire.VACIO_SIN_MOVIMIENTOS, 0L),
                porEstado.getOrDefault(EstadoTicketSire.ERROR, 0L),
                errores.getTotalElements(),
                errores.getContent()
        );
    }

    private Programaciones programaciones() {
        int maxErrores = ProgramacionSireServiceImpl.MAX_ERRORES_CONSECUTIVOS;
        return new Programaciones(
                programacionRepository.countActivas(),
                programacionRepository.countPausadas(maxErrores),
                programacionRepository.findProximas(PageRequest.of(0, MAX_ITEMS_LISTA)).stream().map(ProgramacionItem::from).toList(),
                programacionRepository.findPausadas(maxErrores, PageRequest.of(0, MAX_ITEMS_LISTA)).stream().map(ProgramacionItem::from).toList()
        );
    }

}
