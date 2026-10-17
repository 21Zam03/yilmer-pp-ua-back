package com.zam.sunat_service.repository;

import com.zam.sunat_service.entity.CronogramaSunatEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface CronogramaSunatRepository extends JpaRepository<CronogramaSunatEntity, Long> {

    List<CronogramaSunatEntity> findByFechaVencimiento(LocalDate fechaVencimiento);

}
