package com.zam.sunat_service.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/**
 * Sincronización automática de un libro SIRE de un cliente. SireScheduledTasks revisa periódicamente las
 * programaciones activas con proximaEjecucion vencida y lanza la sincronización del periodo anterior.
 */
@Entity
@Table(name = "tb_programacion_sire",
        uniqueConstraints = @UniqueConstraint(name = "uk_programacion_sire_cliente_tipo", columnNames = {"id_cliente", "tipo"}),
        indexes = @Index(name = "idx_programacion_sire_proxima", columnList = "activo, proxima_ejecucion"))
@Getter
@Setter
@NoArgsConstructor
public class ProgramacionSireEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_programacion")
    private Long programacionId;

    // Al eliminar el cliente se elimina su programación (ON DELETE CASCADE en la FK).
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_cliente", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ClientEntity client;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", length = 20, nullable = false)
    private TipoLibroSire tipo;

    @Column(name = "intervalo_horas", nullable = false)
    private Integer intervaloHoras;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    @Column(name = "proxima_ejecucion")
    private LocalDateTime proximaEjecucion;

    @Column(name = "ultima_ejecucion")
    private LocalDateTime ultimaEjecucion;

    // Se reinicia con cada ejecución exitosa; al llegar al máximo la programación se pausa.
    @Column(name = "errores_consecutivos", nullable = false)
    private int erroresConsecutivos;

    @Column(name = "ultimo_error", length = 500)
    private String ultimoError;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_actualizacion")
    private LocalDateTime fechaActualizacion;

    @PrePersist
    public void prePersist() {
        fechaCreacion = LocalDateTime.now();
        fechaActualizacion = fechaCreacion;
    }

    @PreUpdate
    public void preUpdate() {
        fechaActualizacion = LocalDateTime.now();
    }

}
