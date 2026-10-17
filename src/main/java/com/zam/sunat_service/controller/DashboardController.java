package com.zam.sunat_service.controller;

import com.zam.sunat_service.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Un endpoint por sección, cada uno con el permiso de su módulo: el frontend pide solo las que el rol puede ver.
 */
@RestController
@RequestMapping(DashboardController.API_PATH)
public class DashboardController {

    public static final String API_PATH = "/api/dashboard";

    private final DashboardService dashboardService;

    @Autowired
    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/clientes")
    @PreAuthorize("hasAuthority('PERMISSION_CLIENT_READ')")
    public ResponseEntity<?> resumenClientes() {
        return ResponseEntity.ok(dashboardService.obtenerResumenClientes());
    }

    @GetMapping("/sire")
    @PreAuthorize("hasAuthority('PERMISSION_SIRE_READ')")
    public ResponseEntity<?> resumenSire() {
        return ResponseEntity.ok(dashboardService.obtenerResumenSire());
    }

}
