package com.zam.sunat_service.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Cronograma de vencimientos de SUNAT: para cada periodo, la fecha límite según el último dígito del RUC.
 */
@Entity
@Table(name = "tb_cronograma_sunat",
        uniqueConstraints = @UniqueConstraint(name = "uk_cronograma_periodo_digito", columnNames = {"periodo", "ultimo_digito_ruc"}),
        indexes = @Index(name = "idx_cronograma_fecha", columnList = "fecha_vencimiento"))
@Getter
@Setter
@NoArgsConstructor
public class CronogramaSunatEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cronograma")
    private Long id;

    // Formato SUNAT: yyyyMM (ej. 202609).
    @Column(name = "periodo", length = 6, nullable = false)
    private String periodo;

    // 0 a 9.
    @Column(name = "ultimo_digito_ruc", nullable = false)
    private Integer ultimoDigitoRuc;

    @Column(name = "fecha_vencimiento", nullable = false)
    private LocalDate fechaVencimiento;

}
