package com.zam.sunat_service.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "tb_cliente")
@Getter
@Setter
@NoArgsConstructor
public class ClientEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_cliente")
    private Integer clientId;

    @Column(name = "ruc", length = 11, nullable = false, unique = true)
    private String ruc;

    @Column(name = "razon_social", nullable = false)
    private String businessName;

    @Column(name = "nombre_comercial")
    private String tradeName;

    @Column(name = "direccion_fiscal")
    private String address;

    @Column(name = "usuario_sol", length = 20, nullable = false)
    private String solUser;

    // Cifrada con AES-GCM (ver SolPasswordEncryptor). Nunca se expone en las respuestas.
    @Column(name = "clave_sol", nullable = false)
    private String solPassword;

    // Credenciales de la API SUNAT (se generan en SOL > "Credenciales de API SUNAT").
    @Column(name = "sunat_client_id", length = 100)
    private String sunatClientId;

    // Cifrado con SolPasswordEncryptor, igual que la clave SOL.
    @Column(name = "sunat_client_secret")
    private String sunatClientSecret;

    // Caché del token OAuth2 de SUNAT (ver SunatAuthServiceImpl).
    @Column(name = "access_token", columnDefinition = "TEXT")
    private String accessToken;

    @Column(name = "token_expira_el")
    private LocalDateTime tokenExpiraEl;

    @Column(name = "persona_contacto")
    private String contactName;

    @Column(name = "telefono", length = 20)
    private String phoneNumber;

    @Column(name = "correo", length = 100)
    private String email;

    @Column(name = "activo", nullable = false)
    private boolean enabled;

    // Si es false, los domingos sin ventas no generan la alerta SIN_VENTAS. El DEFAULT rellena los clientes existentes.
    @Column(name = "trabaja_domingos", nullable = false, columnDefinition = "bit(1) NOT NULL DEFAULT 0")
    private boolean trabajaDomingos;

    @Column(name = "fecha_creacion")
    private LocalDateTime createdDate;

    @Column(name = "fecha_actualizacion")
    private LocalDateTime updatedDate;

    @PrePersist
    public void prePersist() {
        createdDate = LocalDateTime.now();
        updatedDate = LocalDateTime.now();
    }

    @PreUpdate
    public void preUpdate() {
        updatedDate = LocalDateTime.now();
    }

}
