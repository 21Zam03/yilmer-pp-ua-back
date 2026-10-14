package com.zam.sunat_service.service.impl;

import com.zam.sunat_service.entity.*;
import com.zam.sunat_service.exceptions.BusinessException;
import com.zam.sunat_service.integration.SireApiClient;
import com.zam.sunat_service.integration.SireVentasTxtParser;
import com.zam.sunat_service.repository.ClientRepository;
import com.zam.sunat_service.repository.TicketSireRepository;
import com.zam.sunat_service.repository.VentaSireRepository;
import com.zam.sunat_service.request.SireSyncRequest;
import com.zam.sunat_service.response.TicketSireResponse;
import com.zam.sunat_service.service.SunatAuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.zam.sunat_service.integration.SireVentasTxtParserTest.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class SireServiceImplTest {

    private static final String BASE = "https://api-sire.sunat.gob.pe/v1/contribuyente/migeigv/libros";
    private static final String RUC = "10733495273";
    private static final String PERIODO = "202610";
    private static final String NUM_TICKET = "20260300000007";
    private static final String CONSULTA_URL = BASE + "/rvierce/gestionprocesosmasivos/web/masivo/consultaestadotickets"
            + "?perIni=202610&perFin=202610&page=1&perPage=20&numTicket=" + NUM_TICKET;

    private ClientRepository clientRepository;
    private TicketSireRepository ticketRepository;
    private VentaSireRepository ventaRepository;
    private SunatAuthService authService;
    private MockRestServiceServer sunat;
    private SireServiceImpl service;
    private ClientEntity client;

    @BeforeEach
    void setUp() {
        clientRepository = mock(ClientRepository.class);
        ticketRepository = mock(TicketSireRepository.class);
        ventaRepository = mock(VentaSireRepository.class);
        authService = mock(SunatAuthService.class);
        when(authService.obtenerTokenValido(RUC)).thenReturn("tok-1");

        RestTemplate restTemplate = new RestTemplate();
        sunat = MockRestServiceServer.bindTo(restTemplate).build();
        SireApiClient apiClient = new SireApiClient(restTemplate, authService, BASE);
        service = new SireServiceImpl(clientRepository, ticketRepository, ventaRepository, apiClient,
                new SireVentasTxtParser(), mock(PlatformTransactionManager.class));

        client = new ClientEntity();
        client.setRuc(RUC);
        client.setEnabled(true);
        when(clientRepository.findByRuc(RUC)).thenReturn(Optional.of(client));
        when(ticketRepository.save(any())).thenAnswer(inv -> {
            TicketSireEntity t = inv.getArgument(0);
            if (t.getTicketId() == null) {
                t.setTicketId(99L);
                t.prePersist();
            }
            return t;
        });
    }

    private SireSyncRequest request(String periodo) {
        SireSyncRequest r = new SireSyncRequest();
        r.setRuc(RUC);
        r.setPeriodo(periodo);
        return r;
    }

    private TicketSireEntity ticketPendiente() {
        TicketSireEntity t = new TicketSireEntity();
        t.setTicketId(99L);
        t.setClient(client);
        t.setPeriodo(PERIODO);
        t.setTipo(TipoLibroSire.VENTAS);
        t.setNumTicket(NUM_TICKET);
        t.setEstado(EstadoTicketSire.PENDIENTE);
        t.setFechaCreacion(LocalDateTime.now().minusMinutes(3));
        when(ticketRepository.findWithClientById(99L)).thenReturn(Optional.of(t));
        return t;
    }

    private String consulta(String codEstado, String desEstado, Integer cnt) {
        return """
                {"paginacion":{"page":1,"perPage":20,"totalRegistros":1},
                 "registros":[{"numTicket":"%s","perTributario":"202610","codProceso":"10","desProceso":"Descargar propuesta",
                   "codEstadoProceso":"%s","desEstadoProceso":"%s",
                   "detalleTicket":{"numTicket":"%s","codEstadoEnvio":"%s","desEstadoEnvio":"%s","cntCPInformados":%s},
                   "archivoReporte":[{"codTipoAchivoReporte":"00","nomArchivoReporte":"LE1073349527320261000014040001EXP2.zip",
                                      "nomArchivoContenido":"LE1073349527320261000014040001EXP2.txt"}]}]}
                """.formatted(NUM_TICKET, codEstado, desEstado, NUM_TICKET, codEstado, desEstado, cnt);
    }

    @Test
    void sincronizarGeneraTicketYLoGuardaPendiente() {
        when(ticketRepository.findFirstByClientAndPeriodoAndTipoAndEstadoInOrderByFechaCreacionDesc(any(), any(), any(), any()))
                .thenReturn(Optional.empty());
        sunat.expect(once(), requestTo(BASE + "/rvie/propuesta/web/propuesta/202610/exportapropuesta?codTipoArchivo=0"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer tok-1"))
                .andRespond(withSuccess("{\"numTicket\":\"" + NUM_TICKET + "\"}", MediaType.APPLICATION_JSON));

        TicketSireResponse response = service.sincronizarVentas(request(PERIODO));

        sunat.verify();
        assertThat(response.ticketId()).isEqualTo(99L);
        assertThat(response.numTicket()).isEqualTo(NUM_TICKET);
        assertThat(response.estado()).isEqualTo(EstadoTicketSire.PENDIENTE);
        assertThat(response.enProceso()).isTrue();
    }

    @Test
    void sincronizarReutilizaTicketEnCursoSinLlamarASunat() {
        TicketSireEntity enCurso = ticketPendiente();
        when(ticketRepository.findFirstByClientAndPeriodoAndTipoAndEstadoInOrderByFechaCreacionDesc(any(), any(), any(), any()))
                .thenReturn(Optional.of(enCurso));

        assertThat(service.sincronizarVentas(request(PERIODO)).ticketId()).isEqualTo(99L);
        sunat.verify();
        verify(ticketRepository, never()).save(any());
    }

    @Test
    void sincronizarRechazaPeriodoFuturo() {
        assertThatThrownBy(() -> service.sincronizarVentas(request("209912"))).isInstanceOf(BusinessException.class);
    }

    @Test
    void ticketTerminadoConComprobantesDescargaZipYGuardaVentas() throws Exception {
        TicketSireEntity ticket = ticketPendiente();
        String txt = CABECERA + "\n"
                + linea("CAR1", "05/10/2026", "01", "F001", "1", "6", "20100070970", "CLIENTE A", "100", "18", "118") + "\n"
                + linea("CAR2", "06/10/2026", "03", "B001", "2", "1", "12345678", "CLIENTE B", "50", "9", "59");

        sunat.expect(requestTo(CONSULTA_URL))
                .andRespond(withSuccess(consulta("06", "Terminado", 2), MediaType.APPLICATION_JSON));
        sunat.expect(requestTo(BASE + "/rvierce/gestionprocesosmasivos/web/masivo/archivoreporte"
                        + "?nomArchivoReporte=LE1073349527320261000014040001EXP2.zip&codTipoArchivoReporte=00&codLibro=140000"
                        + "&numTicket=" + NUM_TICKET + "&perTributario=202610&codProceso=10"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer tok-1"))
                .andRespond(withSuccess(zip("LE1073349527320261000014040001EXP2.txt", txt, StandardCharsets.UTF_8),
                        MediaType.APPLICATION_OCTET_STREAM));

        service.procesarTicket(99L);

        sunat.verify();
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<VentaSireEntity>> guardadas = ArgumentCaptor.forClass(List.class);
        verify(ventaRepository).deleteByClientAndPeriodo(client, PERIODO);
        verify(ventaRepository).saveAll(guardadas.capture());
        assertThat(guardadas.getValue()).extracting(VentaSireEntity::getCarSunat).containsExactly("CAR1", "CAR2");
        assertThat(ticket.getEstado()).isEqualTo(EstadoTicketSire.COMPLETADO);
        assertThat(ticket.getCantidadRegistros()).isEqualTo(2);
    }

    private static final String DESCARGA_URL = BASE + "/rvierce/gestionprocesosmasivos/web/masivo/archivoreporte"
            + "?nomArchivoReporte=LE1073349527320261000014040001EXP2.zip&codTipoArchivoReporte=00&codLibro=140000"
            + "&numTicket=" + NUM_TICKET + "&perTributario=202610&codProceso=10";

    @Test
    void cntCPInformadosCeroIgualDescargaYGuardaLasVentas() throws Exception {
        // SUNAT informa 0 comprobantes aunque el archivo sí los trae.
        TicketSireEntity ticket = ticketPendiente();
        String txt = CABECERA + "\n" + linea("CAR1", "05/10/2026", "01", "F001", "1", "6", "20100070970", "A", "100", "18", "118");
        sunat.expect(requestTo(CONSULTA_URL))
                .andRespond(withSuccess(consulta("06", "Terminado", 0), MediaType.APPLICATION_JSON));
        sunat.expect(requestTo(DESCARGA_URL))
                .andRespond(withSuccess(zip("rvie.txt", txt, StandardCharsets.UTF_8), MediaType.APPLICATION_OCTET_STREAM));

        service.procesarTicket(99L);

        sunat.verify();
        assertThat(ticket.getEstado()).isEqualTo(EstadoTicketSire.COMPLETADO);
        assertThat(ticket.getCantidadRegistros()).isEqualTo(1);
    }

    @Test
    void zipDeCeroBytesQuedaVacioSinMovimientos() {
        TicketSireEntity ticket = ticketPendiente();
        sunat.expect(requestTo(CONSULTA_URL))
                .andRespond(withSuccess(consulta("06", "Terminado", 0), MediaType.APPLICATION_JSON));
        sunat.expect(requestTo(DESCARGA_URL))
                .andRespond(withSuccess(new byte[0], MediaType.APPLICATION_OCTET_STREAM));

        service.procesarTicket(99L);

        sunat.verify();
        assertThat(ticket.getEstado()).isEqualTo(EstadoTicketSire.VACIO_SIN_MOVIMIENTOS);
        assertThat(ticket.getCantidadRegistros()).isZero();
    }

    @Test
    void zipPequenoSoloConCabeceraQuedaVacio() throws Exception {
        TicketSireEntity ticket = ticketPendiente();
        sunat.expect(requestTo(CONSULTA_URL))
                .andRespond(withSuccess(consulta("06", "Terminado", 0), MediaType.APPLICATION_JSON));
        byte[] soloCabecera = zip("rvie.txt", CABECERA, StandardCharsets.UTF_8);
        assertThat(soloCabecera.length).isLessThan(1024);
        sunat.expect(requestTo(DESCARGA_URL))
                .andRespond(withSuccess(soloCabecera, MediaType.APPLICATION_OCTET_STREAM));

        service.procesarTicket(99L);

        assertThat(ticket.getEstado()).isEqualTo(EstadoTicketSire.VACIO_SIN_MOVIMIENTOS);
    }

    @Test
    void zipDeMasDe1KbSinComprobantesValidosEsError() throws Exception {
        TicketSireEntity ticket = ticketPendiente();
        sunat.expect(requestTo(CONSULTA_URL))
                .andRespond(withSuccess(consulta("06", "Terminado", 0), MediaType.APPLICATION_JSON));
        // Más de 1 KB comprimido, pero sin ninguna línea con formato válido.
        StringBuilder basura = new StringBuilder(CABECERA);
        java.util.Random random = new java.util.Random(1);
        for (int i = 0; i < 200; i++) {
            basura.append('\n').append(Long.toHexString(random.nextLong()));
        }
        byte[] archivo = zip("rvie.txt", basura.toString(), StandardCharsets.UTF_8);
        assertThat(archivo.length).isGreaterThanOrEqualTo(1024);
        sunat.expect(requestTo(DESCARGA_URL))
                .andRespond(withSuccess(archivo, MediaType.APPLICATION_OCTET_STREAM));

        service.procesarTicket(99L);

        assertThat(ticket.getEstado()).isEqualTo(EstadoTicketSire.ERROR);
        assertThat(ticket.getMensaje()).contains("bytes");
        verify(ventaRepository, never()).saveAll(any());
    }

    @Test
    void ticketEnProcesoSigueSiendoPendiente() {
        TicketSireEntity ticket = ticketPendiente();
        sunat.expect(requestTo(CONSULTA_URL))
                .andRespond(withSuccess(consulta("02", "En proceso", null), MediaType.APPLICATION_JSON));

        service.procesarTicket(99L);

        sunat.verify();
        assertThat(ticket.getEstado()).isEqualTo(EstadoTicketSire.PENDIENTE);
        verify(ventaRepository, never()).saveAll(any());
    }

    @Test
    void token401SeRenuevaYReintentaUnaVez() {
        ticketPendiente();
        when(authService.obtenerTokenValido(RUC)).thenReturn("tok-viejo", "tok-nuevo");
        sunat.expect(requestTo(CONSULTA_URL))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer tok-viejo"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        sunat.expect(requestTo(CONSULTA_URL))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer tok-nuevo"))
                .andRespond(withSuccess(consulta("02", "En proceso", null), MediaType.APPLICATION_JSON));

        service.procesarTicket(99L);

        sunat.verify();
        verify(authService).invalidarToken(RUC);
    }

    @Test
    void sunatCaidoMantienePendienteConMensaje() {
        TicketSireEntity ticket = ticketPendiente();
        sunat.expect(requestTo(CONSULTA_URL))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        service.procesarTicket(99L);

        assertThat(ticket.getEstado()).isEqualTo(EstadoTicketSire.PENDIENTE);
        assertThat(ticket.getMensaje()).startsWith("Reintentando");
    }

    @Test
    void ticketConMasDeDosHorasPasaAError() {
        TicketSireEntity ticket = ticketPendiente();
        ticket.setFechaCreacion(LocalDateTime.now().minusHours(3));

        service.procesarTicket(99L);

        sunat.verify(); // no consulta a SUNAT
        assertThat(ticket.getEstado()).isEqualTo(EstadoTicketSire.ERROR);
    }

}
