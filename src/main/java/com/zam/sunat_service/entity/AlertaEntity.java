package com.zam.sunat_service.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "tb_alerta", indexes = {
        @Index(name = "idx_alerta_cliente", columnList = "cliente_ruc, leida"),
        @Index(name = "idx_alerta_dedup", columnList = "cliente_ruc, tipo, periodo")
})
@Getter
@Setter
@NoArgsConstructor
public class AlertaEntity {

    public static final String TIPO_VENCIMIENTO = "VENCIMIENTO";
    public static final String TIPO_INFORMATIVA = "INFORMATIVA";
    // Días de un periodo cerrado sin comprobantes de venta (ver AlertaServiceImpl).
    public static final String TIPO_SIN_VENTAS = "SIN_VENTAS";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_alerta")
    private Long id;

    @Column(name = "cliente_ruc", length = 11, nullable = false)
    private String clienteRuc;

    @Column(name = "mensaje", length = 500, nullable = false)
    private String mensaje;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    // "VENCIMIENTO", "INFORMATIVA"...
    @Column(name = "tipo", length = 30, nullable = false)
    private String tipo;

    @Column(name = "leida", nullable = false)
    private Boolean leida = false;

    // Periodo al que se refiere la alerta (si aplica). Evita generar dos veces la misma alerta de vencimiento.
    @Column(name = "periodo", length = 6)
    private String periodo;

    @PrePersist
    public void prePersist() {
        if (fechaCreacion == null) {
            fechaCreacion = LocalDateTime.now();
        }
        if (leida == null) {
            leida = false;
        }
    }

}
