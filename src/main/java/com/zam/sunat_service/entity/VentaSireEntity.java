package com.zam.sunat_service.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Comprobante de venta de la propuesta RVIE descargada del SIRE. Solo se mapean las columnas
 * esenciales del TXT de SUNAT (ver SireVentasTxtParser).
 */
@Entity
@Table(name = "tb_venta_sire", indexes = {
        @Index(name = "idx_venta_sire_cliente_periodo", columnList = "id_cliente, periodo")
})
@Getter
@Setter
@NoArgsConstructor
public class VentaSireEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_venta")
    private Long ventaId;

    // Al eliminar el cliente se eliminan sus ventas (ON DELETE CASCADE en la FK).
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_cliente", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ClientEntity client;

    @Column(name = "periodo", length = 6, nullable = false)
    private String periodo;

    // Código de Anotación Registral: identifica el comprobante en SUNAT.
    @Column(name = "car_sunat", length = 40, nullable = false, unique = true)
    private String carSunat;

    @Column(name = "fecha_emision")
    private LocalDate fechaEmision;

    @Column(name = "tipo_comprobante", length = 4)
    private String tipoComprobante;

    @Column(name = "serie", length = 20)
    private String serie;

    @Column(name = "numero", length = 20)
    private String numero;

    @Column(name = "cliente_tipo_doc", length = 4)
    private String clienteTipoDoc;

    @Column(name = "cliente_nro_doc", length = 20)
    private String clienteNroDoc;

    @Column(name = "cliente_nombre")
    private String clienteNombre;

    @Column(name = "base_imponible", precision = 15, scale = 2, nullable = false)
    private BigDecimal baseImponible;

    @Column(name = "igv", precision = 15, scale = 2, nullable = false)
    private BigDecimal igv;

    @Column(name = "importe_total", precision = 15, scale = 2, nullable = false)
    private BigDecimal importeTotal;

    @Column(name = "fecha_sincronizacion", nullable = false)
    private LocalDateTime fechaSincronizacion;

    @PrePersist
    public void prePersist() {
        fechaSincronizacion = LocalDateTime.now();
    }

}
