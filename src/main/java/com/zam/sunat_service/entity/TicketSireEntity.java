package com.zam.sunat_service.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/**
 * Ticket de un proceso masivo del SIRE (ej. descarga de la propuesta RVIE). SUNAT procesa la
 * solicitud de forma asíncrona; SireScheduledTasks consulta el estado hasta que termine.
 */
@Entity
@Table(name = "tb_ticket_sire", indexes = {
        @Index(name = "idx_ticket_sire_estado", columnList = "estado"),
        @Index(name = "idx_ticket_sire_cliente_periodo", columnList = "id_cliente, periodo, tipo")
})
@Getter
@Setter
@NoArgsConstructor
public class TicketSireEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_ticket")
    private Long ticketId;

    // Al eliminar el cliente se eliminan sus tickets (ON DELETE CASCADE en la FK).
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_cliente", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ClientEntity client;

    // Formato SUNAT: yyyyMM (ej. 202610).
    @Column(name = "periodo", length = 6, nullable = false)
    private String periodo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", length = 20, nullable = false)
    private TipoLibroSire tipo;

    @Column(name = "num_ticket", length = 30, nullable = false)
    private String numTicket;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", length = 30, nullable = false)
    private EstadoTicketSire estado;

    // Detalle del último error o aviso (ej. "SUNAT no responde, reintentando").
    @Column(name = "mensaje", length = 500)
    private String mensaje;

    @Column(name = "cantidad_registros")
    private Integer cantidadRegistros;

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
