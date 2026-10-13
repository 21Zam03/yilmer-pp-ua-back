package com.zam.sunat_service.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Set;

@Entity
@Table(name = "tb_usuario")
@Getter
@Setter
@NoArgsConstructor
public class UserEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario")
    private Integer userId;

    @Column(name = "nombres")
    private String firstname;

    @Column(name = "apellidos")
    private String lastname;

    @Column(name = "correo", unique = true, length = 50, nullable = false)
    private String email;

    @Column(name = "nombre_usuario", unique = true, nullable = false)
    private String username;

    @Column(name = "contrasena", length = 100)
    private String password;

    @Column(name = "activo")
    private boolean enabled;

    @Column(name = "cuenta_expirada")
    private boolean accountExpired;

    @Column(name = "cuenta_bloqueada")
    private boolean accountLocked;

    @Column(name = "credenciales_expiradas")
    private boolean credentialExpired;

    @Column(name = "reseteo_contrasena")
    private boolean passwordReset;

    @Column(name = "fecha_actualizacion_contrasena")
    private LocalDateTime passwordUpdateDate;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "tb_usuarios_roles", joinColumns = @JoinColumn(name = "id_usuario"), inverseJoinColumns = @JoinColumn(name = "id_rol"))
    private Set<RoleEntity> roleList;

}
