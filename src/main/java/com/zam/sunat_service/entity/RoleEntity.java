package com.zam.sunat_service.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Set;

@Entity
@Table(name = "tb_rol")
@Getter
@Setter
@NoArgsConstructor
public class RoleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_rol")
    private Integer roleId;

    @Column(name = "nombre", unique = true, nullable = false)
    private String name;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "tb_roles_permisos", joinColumns = @JoinColumn(name = "id_rol"), inverseJoinColumns = @JoinColumn(name = "id_permiso"))
    private Set<PermissionEntity> permissionList;

}
