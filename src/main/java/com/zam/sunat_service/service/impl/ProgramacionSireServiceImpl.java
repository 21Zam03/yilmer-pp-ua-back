package com.zam.sunat_service.service.impl;

import com.zam.sunat_service.entity.ClientEntity;
import com.zam.sunat_service.entity.ProgramacionSireEntity;
import com.zam.sunat_service.entity.TipoLibroSire;
import com.zam.sunat_service.exceptions.BusinessException;
import com.zam.sunat_service.exceptions.NotFoundException;
import com.zam.sunat_service.repository.ClientRepository;
import com.zam.sunat_service.repository.ProgramacionSireRepository;
import com.zam.sunat_service.request.ProgramacionSireRequest;
import com.zam.sunat_service.request.SireSyncRequest;
import com.zam.sunat_service.response.ProgramacionSireResponse;
import com.zam.sunat_service.service.ProgramacionSireService;
import com.zam.sunat_service.service.SireService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
public class ProgramacionSireServiceImpl implements ProgramacionSireService {

    public static final Set<Integer> INTERVALOS_PERMITIDOS = Set.of(1, 3, 6, 12, 24);
    // Tras esta cantidad de fallos seguidos la programación se pausa para no insistir con credenciales malas.
    public static final int MAX_ERRORES_CONSECUTIVOS = 3;
    // Desfase aleatorio para que los clientes con el mismo intervalo no consulten a SUNAT a la vez.
    private static final int MAX_DESFASE_SEGUNDOS = 300;
    private static final int MAX_MENSAJE = 500;
    private static final DateTimeFormatter PERIODO = DateTimeFormatter.ofPattern("yyyyMM");

    private final ProgramacionSireRepository programacionRepository;
    private final ClientRepository clientRepository;
    private final SireService sireService;

    @Autowired
    public ProgramacionSireServiceImpl(ProgramacionSireRepository programacionRepository,
                                       ClientRepository clientRepository,
                                       SireService sireService) {
        this.programacionRepository = programacionRepository;
        this.clientRepository = clientRepository;
        this.sireService = sireService;
    }

    @Override
    @Transactional(readOnly = true)
    public ProgramacionSireResponse obtenerProgramacionVentas(String ruc) {
        ClientEntity client = findClient(ruc);
        return programacionRepository.findByClientAndTipo(client, TipoLibroSire.VENTAS)
                .map(ProgramacionSireResponse::from)
                .orElse(ProgramacionSireResponse.sinConfigurar(client.getRuc(), TipoLibroSire.VENTAS));
    }

    @Override
    @Transactional
    public ProgramacionSireResponse guardarProgramacionVentas(ProgramacionSireRequest request) {
        ClientEntity client = findClient(request.getRuc());
        int intervalo = request.getIntervaloHoras();
        if (!INTERVALOS_PERMITIDOS.contains(intervalo)) {
            throw new BusinessException("El intervalo debe ser de 1, 3, 6, 12 o 24 horas");
        }
        boolean activar = request.getActivo();
        if (activar) {
            if (!client.isEnabled()) {
                throw new BusinessException("El cliente " + client.getRuc() + " está desactivado");
            }
            if (client.getSunatClientId() == null || client.getSunatClientSecret() == null) {
                throw new BusinessException("El cliente " + client.getRuc()
                        + " no tiene configuradas sus credenciales de API SUNAT (client_id / client_secret)");
            }
        }

        ProgramacionSireEntity programacion = programacionRepository.findByClientAndTipo(client, TipoLibroSire.VENTAS)
                .orElseGet(() -> {
                    ProgramacionSireEntity nueva = new ProgramacionSireEntity();
                    nueva.setClient(client);
                    nueva.setTipo(TipoLibroSire.VENTAS);
                    return nueva;
                });

        boolean recalcular = activar && (!programacion.isActivo() || programacion.getIntervaloHoras() != intervalo);
        programacion.setActivo(activar);
        programacion.setIntervaloHoras(intervalo);
        programacion.setErroresConsecutivos(0);
        programacion.setUltimoError(null);
        if (recalcular) {
            // Respeta la última ejecución: si ya pasó el intervalo, el cron la toma en su siguiente ciclo.
            LocalDateTime ahora = LocalDateTime.now();
            LocalDateTime siguiente = programacion.getUltimaEjecucion() != null
                    ? programacion.getUltimaEjecucion().plusHours(intervalo)
                    : ahora;
            programacion.setProximaEjecucion(siguiente.isBefore(ahora) ? ahora : siguiente);
        }
        programacion = programacionRepository.save(programacion);

        log.info("SIRE ventas {}: sincronización automática {} cada {} h", client.getRuc(),
                activar ? "activada" : "desactivada", intervalo);
        return ProgramacionSireResponse.from(programacion);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> obtenerProgramacionesVencidas() {
        return programacionRepository.findIdsVencidas(LocalDateTime.now());
    }

    // Sin @Transactional: sincronizarVentas llama a SUNAT y no debe retener una conexión de BD abierta.
    @Override
    public void ejecutarProgramacion(Long programacionId) {
        ProgramacionSireEntity programacion = programacionRepository.findWithClientById(programacionId).orElse(null);
        if (programacion == null) {
            return;
        }

        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime siguiente = ahora.plusHours(programacion.getIntervaloHoras())
                .plusSeconds(ThreadLocalRandom.current().nextInt(MAX_DESFASE_SEGUNDOS + 1));
        if (programacionRepository.reservar(programacionId, ahora, siguiente) == 0) {
            return; // otra instancia ya la tomó o la desactivaron
        }
        programacion.setProximaEjecucion(siguiente);

        String ruc = programacion.getClient().getRuc();
        // El periodo anterior es el que se está declarando; el mes en curso aún no tiene propuesta completa.
        String periodo = YearMonth.now().minusMonths(1).format(PERIODO);
        try {
            SireSyncRequest request = new SireSyncRequest();
            request.setRuc(ruc);
            request.setPeriodo(periodo);
            sireService.sincronizarVentas(request);

            programacion.setUltimaEjecucion(ahora);
            programacion.setErroresConsecutivos(0);
            programacion.setUltimoError(null);
            log.info("SIRE ventas {} {}: sincronización automática lanzada, próxima {}", ruc, periodo, siguiente);
        } catch (RuntimeException e) {
            int errores = programacion.getErroresConsecutivos() + 1;
            String mensaje = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            programacion.setErroresConsecutivos(errores);
            programacion.setUltimoError(mensaje.length() <= MAX_MENSAJE ? mensaje : mensaje.substring(0, MAX_MENSAJE));
            if (errores >= MAX_ERRORES_CONSECUTIVOS) {
                programacion.setActivo(false);
                log.warn("SIRE ventas {}: sincronización automática pausada tras {} errores seguidos: {}", ruc, errores, mensaje);
            } else {
                log.warn("SIRE ventas {} {}: falló la sincronización automática ({}/{}): {}",
                        ruc, periodo, errores, MAX_ERRORES_CONSECUTIVOS, mensaje);
            }
        }
        programacionRepository.save(programacion);
    }

    private ClientEntity findClient(String ruc) {
        String rucLimpio = ruc == null ? "" : ruc.trim();
        return clientRepository.findByRuc(rucLimpio)
                .orElseThrow(() -> new NotFoundException("No existe un cliente con el RUC " + rucLimpio));
    }

}
