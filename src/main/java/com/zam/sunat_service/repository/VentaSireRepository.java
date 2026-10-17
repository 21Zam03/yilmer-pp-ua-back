package com.zam.sunat_service.repository;

import com.zam.sunat_service.entity.ClientEntity;
import com.zam.sunat_service.entity.VentaSireEntity;
import com.zam.sunat_service.response.DashboardTendenciaResponse;
import com.zam.sunat_service.response.DashboardTopClienteResponse;
import com.zam.sunat_service.response.VentasSireResumenResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface VentaSireRepository extends JpaRepository<VentaSireEntity, Long> {

    // DELETE masivo en una sola sentencia (el deleteBy derivado borraría fila por fila).
    @Modifying
    @Query("DELETE FROM VentaSireEntity v WHERE v.client = :client AND v.periodo = :periodo")
    int deleteByClientAndPeriodo(@Param("client") ClientEntity client, @Param("periodo") String periodo);

    long countByClientAndPeriodo(ClientEntity client, String periodo);

    @Query("""
    SELECT DISTINCT v.fechaEmision FROM VentaSireEntity v
    WHERE v.client = :client AND v.periodo = :periodo AND v.fechaEmision IS NOT NULL
    """)
    List<LocalDate> findFechasConVentas(@Param("client") ClientEntity client, @Param("periodo") String periodo);

    @Query("""
    SELECT v FROM VentaSireEntity v
    WHERE v.client = :client
      AND v.periodo = :periodo
      AND (
            :searchKey IS NULL
            OR LOWER(v.clienteNombre) LIKE LOWER(CONCAT('%', :searchKey, '%'))
            OR v.clienteNroDoc LIKE CONCAT('%', :searchKey, '%')
            OR LOWER(v.serie) LIKE LOWER(CONCAT('%', :searchKey, '%'))
            OR v.numero LIKE CONCAT('%', :searchKey, '%')
      )
    """)
    Page<VentaSireEntity> findByFilters(
            @Param("client") ClientEntity client,
            @Param("periodo") String periodo,
            @Param("searchKey") String searchKey,
            Pageable pageable
    );

    @Query("""
    SELECT new com.zam.sunat_service.response.VentasSireResumenResponse(
            COUNT(v),
            SUM(v.baseImponible),
            SUM(v.igv),
            SUM(v.importeTotal))
    FROM VentaSireEntity v
    WHERE v.client = :client AND v.periodo = :periodo
    """)
    VentasSireResumenResponse resumen(@Param("client") ClientEntity client, @Param("periodo") String periodo);

    // --- Dashboard: todos los clientes ---

    @Query("""
    SELECT new com.zam.sunat_service.response.VentasSireResumenResponse(
            COUNT(v),
            SUM(v.baseImponible),
            SUM(v.igv),
            SUM(v.importeTotal))
    FROM VentaSireEntity v
    WHERE v.periodo = :periodo
    """)
    VentasSireResumenResponse resumenPeriodo(@Param("periodo") String periodo);

    @Query("SELECT COUNT(DISTINCT v.client) FROM VentaSireEntity v WHERE v.periodo = :periodo")
    long countClientesConVentas(@Param("periodo") String periodo);

    @Query("""
    SELECT new com.zam.sunat_service.response.DashboardTopClienteResponse(
            c.ruc, c.businessName, COUNT(v), SUM(v.importeTotal))
    FROM VentaSireEntity v JOIN v.client c
    WHERE v.periodo = :periodo
    GROUP BY c.clientId, c.ruc, c.businessName
    ORDER BY SUM(v.importeTotal) DESC
    """)
    List<DashboardTopClienteResponse> topClientes(@Param("periodo") String periodo, Pageable pageable);

    // periodo es yyyyMM: el orden alfabético coincide con el cronológico.
    @Query("""
    SELECT new com.zam.sunat_service.response.DashboardTendenciaResponse(
            v.periodo, COUNT(v), SUM(v.importeTotal), COUNT(DISTINCT v.client))
    FROM VentaSireEntity v
    WHERE v.periodo BETWEEN :desde AND :hasta
    GROUP BY v.periodo
    ORDER BY v.periodo
    """)
    List<DashboardTendenciaResponse> tendencia(@Param("desde") String desde, @Param("hasta") String hasta);

}
