package com.zam.sunat_service.config;

import com.zam.sunat_service.entity.PermissionEntity;
import com.zam.sunat_service.entity.RoleEntity;
import com.zam.sunat_service.repository.PermissionRepository;
import com.zam.sunat_service.repository.RoleRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Siembra el catálogo de permisos y roles si aún no existen. Corre en cada arranque pero es
 * idempotente: solo crea lo que falte por nombre, nunca actualiza roles/permisos ya existentes.
 */
@Component
public class RoleAndPermissionSeeder implements CommandLineRunner {

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;

    public RoleAndPermissionSeeder(PermissionRepository permissionRepository, RoleRepository roleRepository) {
        this.permissionRepository = permissionRepository;
        this.roleRepository = roleRepository;
    }

    private static final List<String> ALL_PERMISSIONS = List.of(
            "INVOICE_CREATE", "INVOICE_READ", "INVOICE_VOID",
            "CREDIT_NOTE_CREATE", "REPORT_READ",
            "USER_READ", "USER_CREATE", "CONFIG_UPDATE",
            "CLIENT_READ", "CLIENT_CREATE", "CLIENT_UPDATE", "CLIENT_DELETE",
            "SIRE_READ", "SIRE_SYNC"
    );

    private static final List<String> CONTADOR_PERMISSIONS = List.of(
            "INVOICE_READ", "INVOICE_VOID", "CREDIT_NOTE_CREATE", "REPORT_READ",
            "CLIENT_READ", "CLIENT_CREATE", "CLIENT_UPDATE",
            "SIRE_READ", "SIRE_SYNC"
    );

    private static final List<String> VENDEDOR_PERMISSIONS = List.of(
            "INVOICE_CREATE", "INVOICE_READ",
            "CLIENT_READ"
    );

    @Override
    public void run(String... args) {
        Map<String, PermissionEntity> permissionsByName = ensurePermissionsExist();
        ensureRoleExists("ADMIN", ALL_PERMISSIONS, permissionsByName);
        ensureRoleExists("CONTADOR", CONTADOR_PERMISSIONS, permissionsByName);
        ensureRoleExists("VENDEDOR", VENDEDOR_PERMISSIONS, permissionsByName);
    }

    private Map<String, PermissionEntity> ensurePermissionsExist() {
        Map<String, PermissionEntity> existing = permissionRepository.findAll().stream()
                .collect(Collectors.toMap(PermissionEntity::getName, p -> p));
        for (String name : ALL_PERMISSIONS) {
            if (!existing.containsKey(name)) {
                PermissionEntity toCreate = new PermissionEntity();
                toCreate.setName(name);
                existing.put(name, permissionRepository.save(toCreate));
            }
        }
        return existing;
    }

    // Crea el rol si no existe; si ya existe, solo le agrega los permisos que le falten
    // (para que los permisos nuevos lleguen a bases ya sembradas). Nunca quita permisos.
    private void ensureRoleExists(String roleName, List<String> permissionNames, Map<String, PermissionEntity> permissionsByName) {
        RoleEntity role = roleRepository.findByName(roleName).orElseGet(() -> {
            RoleEntity toCreate = new RoleEntity();
            toCreate.setName(roleName);
            toCreate.setPermissionList(new HashSet<>());
            return toCreate;
        });
        Set<String> current = role.getPermissionList().stream()
                .map(PermissionEntity::getName)
                .collect(Collectors.toSet());
        List<PermissionEntity> missing = permissionNames.stream()
                .filter(name -> !current.contains(name))
                .map(permissionsByName::get)
                .toList();
        if (role.getRoleId() != null && missing.isEmpty()) {
            return;
        }
        role.getPermissionList().addAll(missing);
        roleRepository.save(role);
    }
}
