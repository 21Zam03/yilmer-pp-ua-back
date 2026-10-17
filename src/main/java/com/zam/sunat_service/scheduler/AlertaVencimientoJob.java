package com.zam.sunat_service.scheduler;

import com.zam.sunat_service.entity.AlertaEntity;
import com.zam.sunat_service.entity.ClientEntity;
import com.zam.sunat_service.entity.CronogramaSunatEntity;
import com.zam.sunat_service.repository.AlertaRepository;
import com.zam.sunat_service.repository.ClientRepository;
import com.zam.sunat_service.repository.CronogramaSunatRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Alertas proactivas de vencimiento: todos los días a las 8 a. m. (hora de Lima) busca en el cronograma de SUNAT
 * los vencimientos de dentro de {@value #DIAS_ANTICIPACION} días y crea una alerta para cada cliente activo cuyo
 * RUC termina en el dígito que vence.
 */
@Slf4j
@Service
public class AlertaVencimientoJob {

    public static final int DIAS_ANTICIPACION = 3;
    private static final ZoneId ZONA_LIMA = ZoneId.of("America/Lima");

    private final CronogramaSunatRepository cronogramaRepository;
    private final ClientRepository clientRepository;
    private final AlertaRepository alertaRepository;

    @Autowired
    public AlertaVencimientoJob(CronogramaSunatRepository cronogramaRepository,
                                ClientRepository clientRepository,
                                AlertaRepository alertaRepository) {
        this.cronogramaRepository = cronogramaRepository;
        this.clientRepository = clientRepository;
        this.alertaRepository = alertaRepository;
    }

    // zone explícita: el servidor puede estar en UTC y las 8 a. m. deben ser de Perú.
    // Todo en una transacción: si algo falla, no queda la mitad de los clientes con alerta y la otra mitad sin ella.
    // Si lanza una excepción, el scheduler de Spring la registra en el log y el job vuelve a correr al día siguiente.
    @Scheduled(cron = "0 0 8 * * *", zone = "America/Lima")
    @Transactional
    public void ejecutar() {
        generarAlertas(LocalDate.now(ZONA_LIMA));
    }

    /**
     * Crea las alertas de los vencimientos de {@code hoy + DIAS_ANTICIPACION}. Es idempotente: si el job corre dos
     * veces el mismo día (reinicio, ejecución manual), no duplica alertas.
     *
     * @return cantidad de alertas creadas
     */
    public int generarAlertas(LocalDate hoy) {
        LocalDate fechaObjetivo = hoy.plusDays(DIAS_ANTICIPACION);
        List<CronogramaSunatEntity> vencimientos = cronogramaRepository.findByFechaVencimiento(fechaObjetivo);
        if (vencimientos.isEmpty()) {
            log.debug("Alertas de vencimiento: no hay vencimientos el {}", fechaObjetivo);
            return 0;
        }

        int creadas = 0;
        for (CronogramaSunatEntity vencimiento : vencimientos) {
            String periodo = vencimiento.getPeriodo();
            List<ClientEntity> clientes = clientRepository.findActivosPorUltimoDigitoRuc(
                    String.valueOf(vencimiento.getUltimoDigitoRuc()));

            for (ClientEntity cliente : clientes) {
                if (alertaRepository.existsByClienteRucAndTipoAndPeriodo(cliente.getRuc(), AlertaEntity.TIPO_VENCIMIENTO, periodo)) {
                    continue;
                }
                AlertaEntity alerta = new AlertaEntity();
                alerta.setClienteRuc(cliente.getRuc());
                alerta.setPeriodo(periodo);
                alerta.setTipo(AlertaEntity.TIPO_VENCIMIENTO);
                alerta.setMensaje("El periodo " + periodo + " vence en " + DIAS_ANTICIPACION + " días. Recuerda declarar a tiempo.");
                alerta.setLeida(false);
                alertaRepository.save(alerta);
                creadas++;
            }
        }

        log.info("Alertas de vencimiento: {} alerta(s) creadas para los vencimientos del {}", creadas, fechaObjetivo);
        return creadas;
    }

}
