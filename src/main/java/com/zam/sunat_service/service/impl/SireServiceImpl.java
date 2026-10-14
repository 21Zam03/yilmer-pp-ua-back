package com.zam.sunat_service.service.impl;

import com.zam.sunat_service.entity.*;
import com.zam.sunat_service.exceptions.BusinessException;
import com.zam.sunat_service.exceptions.NotFoundException;
import com.zam.sunat_service.exceptions.SunatApiException;
import com.zam.sunat_service.integration.SireApiClient;
import com.zam.sunat_service.integration.SireVentasTxtParser;
import com.zam.sunat_service.repository.ClientRepository;
import com.zam.sunat_service.repository.TicketSireRepository;
import com.zam.sunat_service.repository.VentaSireRepository;
import com.zam.sunat_service.request.SireSyncRequest;
import com.zam.sunat_service.response.PageResponse;
import com.zam.sunat_service.response.TicketSireResponse;
import com.zam.sunat_service.response.VentaSireResponse;
import com.zam.sunat_service.response.VentasSireResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import com.zam.sunat_service.response.sunat.SireConsultaTicketsResponse.ArchivoReporte;
import com.zam.sunat_service.response.sunat.SireConsultaTicketsResponse.Registro;
import com.zam.sunat_service.service.SireService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
public class SireServiceImpl implements SireService {

    private static final List<EstadoTicketSire> ESTADOS_ACTIVOS = List.of(EstadoTicketSire.PENDIENTE, EstadoTicketSire.DESCARGANDO);
    // Si SUNAT no termina en este tiempo, el ticket se da por perdido para no consultarlo indefinidamente.
    private static final Duration TIEMPO_MAXIMO_PROCESO = Duration.ofHours(2);
    // codEstadoProceso de SUNAT para "Terminado".
    private static final String COD_ESTADO_TERMINADO = "06";
    private static final int MAX_MENSAJE = 500;
    private static final int MAX_PAGE_SIZE = 100;
    private static final java.util.regex.Pattern RUC = java.util.regex.Pattern.compile("^(10|15|17|20)\\d{9}$");
    private static final java.util.regex.Pattern PERIODO = java.util.regex.Pattern.compile("^\\d{4}(0[1-9]|1[0-2])$");
    // ZIP vacío (0 bytes) = sin movimientos. Desde 1 KB se espera que traiga comprobantes; si no, es un error
    // de formato. Por debajo de 1 KB se procesa igual: un ZIP con 1 o 2 comprobantes puede pesar menos.
    private static final int TAMANO_MINIMO_CON_DATOS = 1024;

    private final ClientRepository clientRepository;
    private final TicketSireRepository ticketRepository;
    private final VentaSireRepository ventaRepository;
    private final SireApiClient sireApiClient;
    private final SireVentasTxtParser ventasParser;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    public SireServiceImpl(ClientRepository clientRepository,
                           TicketSireRepository ticketRepository,
                           VentaSireRepository ventaRepository,
                           SireApiClient sireApiClient,
                           SireVentasTxtParser ventasParser,
                           PlatformTransactionManager transactionManager) {
        this.clientRepository = clientRepository;
        this.ticketRepository = ticketRepository;
        this.ventaRepository = ventaRepository;
        this.sireApiClient = sireApiClient;
        this.ventasParser = ventasParser;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    // Sin @Transactional: la llamada HTTP a SUNAT no debe retener una conexión de BD abierta.
    @Override
    public TicketSireResponse sincronizarVentas(SireSyncRequest request) {
        String ruc = request.getRuc().trim();
        String periodo = request.getPeriodo().trim();
        validarPeriodo(periodo);

        ClientEntity client = clientRepository.findByRuc(ruc)
                .orElseThrow(() -> new NotFoundException("No existe un cliente con el RUC " + ruc));
        if (!client.isEnabled()) {
            throw new BusinessException("El cliente " + ruc + " está desactivado");
        }

        // Evita pedir a SUNAT el mismo periodo dos veces mientras el primer ticket sigue en curso.
        var enCurso = ticketRepository.findFirstByClientAndPeriodoAndTipoAndEstadoInOrderByFechaCreacionDesc(
                client, periodo, TipoLibroSire.VENTAS, ESTADOS_ACTIVOS);
        if (enCurso.isPresent()) {
            return TicketSireResponse.from(enCurso.get());
        }

        String numTicket = sireApiClient.generarTicketPropuestaVentas(ruc, periodo);

        TicketSireEntity ticket = new TicketSireEntity();
        ticket.setClient(client);
        ticket.setPeriodo(periodo);
        ticket.setTipo(TipoLibroSire.VENTAS);
        ticket.setNumTicket(numTicket);
        ticket.setEstado(EstadoTicketSire.PENDIENTE);
        ticket = ticketRepository.save(ticket);

        log.info("SIRE ventas {} {}: ticket SUNAT {} registrado (id {})", ruc, periodo, numTicket, ticket.getTicketId());
        return TicketSireResponse.from(ticket);
    }

    @Override
    @Transactional(readOnly = true)
    public TicketSireResponse obtenerEstadoTicket(Long ticketId) {
        return TicketSireResponse.from(findTicket(ticketId));
    }

    @Override
    @Transactional(readOnly = true)
    public VentasSireResponse obtenerVentas(String ruc, String periodo, String searchKey, int page, int size) {
        String rucLimpio = ruc == null ? "" : ruc.trim();
        String periodoLimpio = periodo == null ? "" : periodo.trim();
        if (!RUC.matcher(rucLimpio).matches()) {
            throw new BusinessException("El RUC debe tener 11 dígitos y empezar con 10, 15, 17 o 20");
        }
        if (!PERIODO.matcher(periodoLimpio).matches()) {
            throw new BusinessException("El periodo debe tener el formato yyyyMM (ej. 202610)");
        }

        ClientEntity client = clientRepository.findByRuc(rucLimpio)
                .orElseThrow(() -> new NotFoundException("No existe un cliente con el RUC " + rucLimpio));

        Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by("fechaEmision", "tipoComprobante", "serie", "numero")
        );
        String key = searchKey == null || searchKey.isBlank() ? null : searchKey.trim();

        TicketSireResponse ultimaSincronizacion = ticketRepository
                .findFirstByClientAndPeriodoAndTipoOrderByFechaCreacionDesc(client, periodoLimpio, TipoLibroSire.VENTAS)
                .map(TicketSireResponse::from)
                .orElse(null);

        return new VentasSireResponse(
                rucLimpio,
                periodoLimpio,
                ultimaSincronizacion,
                ventaRepository.resumen(client, periodoLimpio),
                PageResponse.from(ventaRepository.findByFilters(client, periodoLimpio, key, pageable).map(VentaSireResponse::from))
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> obtenerTicketsEnProceso() {
        return ticketRepository.findIdsByEstadoIn(ESTADOS_ACTIVOS);
    }

    @Override
    public void procesarTicket(Long ticketId) {
        TicketSireEntity ticket = findTicket(ticketId);
        if (!ticket.getEstado().isActivo()) {
            return;
        }
        String ruc = ticket.getClient().getRuc();

        if (ticket.getFechaCreacion().plus(TIEMPO_MAXIMO_PROCESO).isBefore(LocalDateTime.now())) {
            actualizar(ticket, EstadoTicketSire.ERROR, "SUNAT no terminó de procesar el ticket en "
                    + TIEMPO_MAXIMO_PROCESO.toHours() + " horas", null);
            return;
        }

        try {
            Registro registro = sireApiClient.consultarEstadoTicket(ruc, ticket.getPeriodo(), ticket.getNumTicket());
            if (registro == null) {
                log.debug("SIRE: ticket {} aún no aparece en la consulta de SUNAT", ticket.getNumTicket());
                return;
            }

            if (esError(registro)) {
                actualizar(ticket, EstadoTicketSire.ERROR, "SUNAT reportó error: " + registro.desEstadoProceso(), null);
                return;
            }
            if (!esTerminado(registro)) {
                return; // sigue PENDIENTE; el cron lo vuelve a consultar en el siguiente ciclo
            }

            // cntCPInformados no es confiable (SUNAT envía 0 aunque el periodo tenga comprobantes):
            // solo se usa para el log; la decisión se toma con el archivo descargado.
            Integer informados = registro.detalleTicket() != null ? registro.detalleTicket().cntCPInformados() : null;

            ArchivoReporte archivo = elegirArchivo(registro);
            if (archivo == null) {
                log.info("SIRE ticket {}: terminado sin archivo (cntCPInformados={})", ticket.getNumTicket(), informados);
                guardarVentas(ticket, List.of(), EstadoTicketSire.VACIO_SIN_MOVIMIENTOS);
                return;
            }

            actualizar(ticket, EstadoTicketSire.DESCARGANDO, null, null);
            byte[] zip = sireApiClient.descargarArchivo(ruc, registro, archivo);
            log.info("SIRE ticket {}: ZIP descargado ({} bytes, cntCPInformados={})", ticket.getNumTicket(), zip.length, informados);

            if (zip.length == 0) {
                guardarVentas(ticket, List.of(), EstadoTicketSire.VACIO_SIN_MOVIMIENTOS);
                return;
            }

            List<VentaSireEntity> ventas = ventasParser.parsear(zip, ticket.getClient(), ticket.getPeriodo());
            if (ventas.isEmpty() && zip.length >= TAMANO_MINIMO_CON_DATOS) {
                // Un archivo de ese tamaño debería traer comprobantes: probablemente cambió el formato del TXT.
                actualizar(ticket, EstadoTicketSire.ERROR, "El archivo de SUNAT pesa " + zip.length
                        + " bytes pero no se encontraron comprobantes válidos", null);
                return;
            }
            guardarVentas(ticket, ventas, ventas.isEmpty() ? EstadoTicketSire.VACIO_SIN_MOVIMIENTOS : EstadoTicketSire.COMPLETADO);

        } catch (SunatApiException e) {
            // Falla de comunicación o credenciales: se reintenta en el siguiente ciclo (hasta TIEMPO_MAXIMO_PROCESO).
            log.warn("SIRE ticket {} (RUC {}): {}. Se reintentará", ticket.getNumTicket(), ruc, e.getMessage());
            actualizar(ticket, EstadoTicketSire.PENDIENTE, "Reintentando: " + e.getMessage(), null);
        } catch (RuntimeException e) {
            // Archivo corrupto, formato inesperado, error de BD...: no tiene sentido reintentar.
            log.error("SIRE ticket {} (RUC {}) falló", ticket.getNumTicket(), ruc, e);
            actualizar(ticket, EstadoTicketSire.ERROR, e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName(), null);
        }
    }

    // Reemplaza las ventas del periodo y cierra el ticket en una sola transacción: o queda todo o nada.
    private void guardarVentas(TicketSireEntity ticket, List<VentaSireEntity> ventas, EstadoTicketSire estadoFinal) {
        transactionTemplate.executeWithoutResult(status -> {
            int borradas = ventaRepository.deleteByClientAndPeriodo(ticket.getClient(), ticket.getPeriodo());
            ventaRepository.saveAll(ventas);
            ticket.setEstado(estadoFinal);
            ticket.setMensaje(null);
            ticket.setCantidadRegistros(ventas.size());
            ticketRepository.save(ticket);
            log.info("SIRE ventas {} {}: {} comprobantes guardados ({} reemplazados), ticket {} -> {}",
                    ticket.getClient().getRuc(), ticket.getPeriodo(), ventas.size(), borradas, ticket.getNumTicket(), estadoFinal);
        });
    }

    private void actualizar(TicketSireEntity ticket, EstadoTicketSire estado, String mensaje, Integer cantidad) {
        ticket.setEstado(estado);
        ticket.setMensaje(mensaje == null || mensaje.length() <= MAX_MENSAJE ? mensaje : mensaje.substring(0, MAX_MENSAJE));
        ticket.setCantidadRegistros(cantidad);
        ticketRepository.save(ticket);
    }

    private boolean esTerminado(Registro registro) {
        return COD_ESTADO_TERMINADO.equals(registro.codEstadoProceso())
                || contiene(registro.desEstadoProceso(), "terminado");
    }

    private boolean esError(Registro registro) {
        return contiene(registro.desEstadoProceso(), "error") || contiene(registro.desEstadoProceso(), "rechaz");
    }

    private boolean contiene(String texto, String fragmento) {
        return texto != null && texto.toLowerCase(Locale.ROOT).contains(fragmento);
    }

    // Prefiere el .zip; si SUNAT solo lista otro archivo, usa el primero.
    private ArchivoReporte elegirArchivo(Registro registro) {
        if (registro.archivoReporte() == null || registro.archivoReporte().isEmpty()) {
            return null;
        }
        return registro.archivoReporte().stream()
                .filter(a -> a.nomArchivoReporte() != null && a.nomArchivoReporte().toLowerCase(Locale.ROOT).endsWith(".zip"))
                .findFirst()
                .orElse(registro.archivoReporte().get(0));
    }

    private void validarPeriodo(String periodo) {
        YearMonth solicitado = YearMonth.parse(periodo, DateTimeFormatter.ofPattern("yyyyMM"));
        if (solicitado.isAfter(YearMonth.now())) {
            throw new BusinessException("No se puede sincronizar un periodo futuro (" + periodo + ")");
        }
    }

    private TicketSireEntity findTicket(Long ticketId) {
        return ticketRepository.findWithClientById(ticketId)
                .orElseThrow(() -> new NotFoundException("No se encontró el ticket SIRE con id " + ticketId));
    }

}
