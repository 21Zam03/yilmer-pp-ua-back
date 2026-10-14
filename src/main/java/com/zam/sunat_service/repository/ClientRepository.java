package com.zam.sunat_service.repository;

import com.zam.sunat_service.entity.ClientEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ClientRepository extends JpaRepository<ClientEntity, Integer> {

    Optional<ClientEntity> findByRuc(String ruc);
    boolean existsByRuc(String ruc);
    boolean existsByRucAndClientIdNot(String ruc, Integer clientId);

    @Query("""
    SELECT c FROM ClientEntity c
    WHERE (:active IS NULL OR c.enabled = :active)
      AND (
            :searchKey IS NULL
            OR LOWER(c.businessName) LIKE LOWER(CONCAT('%', :searchKey, '%'))
            OR LOWER(c.tradeName) LIKE LOWER(CONCAT('%', :searchKey, '%'))
            OR c.ruc LIKE CONCAT('%', :searchKey, '%')
            OR LOWER(c.contactName) LIKE LOWER(CONCAT('%', :searchKey, '%'))
            OR LOWER(c.email) LIKE LOWER(CONCAT('%', :searchKey, '%'))
            OR c.phoneNumber LIKE CONCAT('%', :searchKey, '%')
      )
    """)
    Page<ClientEntity> findByFilters(
            @Param("searchKey") String searchKey,
            @Param("active") Boolean active,
            Pageable pageable
    );

    long countByEnabled(boolean enabled);

}
