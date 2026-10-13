package com.zam.sunat_service.repository;

import com.zam.sunat_service.entity.PermissionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PermissionRepository extends JpaRepository<PermissionEntity, Integer> {
    Optional<PermissionEntity> findByName(String name);
}
