package com.zam.sunat_service.scheduler;

import com.zam.sunat_service.service.SireService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Motor asíncrono del SIRE: cada 60 s (contados desde que termina la corrida anterior, así nunca se
 * solapan) avanza los tickets en curso. Requiere @EnableScheduling (ver SchedulingConfig).
 */
@Slf4j
@Component
public class SireScheduledTasks {

    private final SireService sireService;

    @Autowired
    public SireScheduledTasks(SireService sireService) {
        this.sireService = sireService;
    }

    @Scheduled(fixedDelay = 60000, initialDelay = 30000)
    public void procesarTicketsPendientes() {
        List<Long> ticketIds;
        try {
            ticketIds = sireService.obtenerTicketsEnProceso();
        } catch (RuntimeException e) {
            log.error("SIRE: no se pudieron obtener los tickets pendientes", e);
            return;
        }
        if (ticketIds.isEmpty()) {
            return;
        }

        log.info("SIRE: procesando {} ticket(s) en curso", ticketIds.size());
        for (Long ticketId : ticketIds) {
            // Un ticket que falla no debe impedir que se procesen los demás.
            try {
                sireService.procesarTicket(ticketId);
            } catch (RuntimeException e) {
                log.error("SIRE: error inesperado procesando el ticket {}", ticketId, e);
            }
        }
    }

}
