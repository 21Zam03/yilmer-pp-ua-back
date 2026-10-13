package com.zam.sunat_service.config;

import com.zam.sunat_service.entity.PermissionEntity;
import com.zam.sunat_service.entity.RoleEntity;
import com.zam.sunat_service.repository.PermissionRepository;
import com.zam.sunat_service.repository.RoleRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

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
            "USER_READ", "USER_CREATE", "CONFIG_UPDATE"
    );

    private static final List<String> CONTADOR_PERMISSIONS = List.of(
            "INVOICE_READ", "INVOICE_VOID", "CREDIT_NOTE_CREATE", "REPORT_READ"
    );

    private static final List<String> VENDEDOR_PERMISSIONS = List.of(
            "INVOICE_CREATE", "INVOICE_READ"
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

    private void ensureRoleExists(String roleName, List<String> permissionNames, Map<String, PermissionEntity> permissionsByName) {
        if (roleRepository.findByName(roleName).isPresent()) {
            return;
        }
        RoleEntity role = new RoleEntity();
        role.setName(roleName);
        Set<PermissionEntity> permissions = permissionNames.stream()
                .map(permissionsByName::get)
                .collect(Collectors.toSet());
        role.setPermissionList(permissions);
        roleRepository.save(role);
    }
}
