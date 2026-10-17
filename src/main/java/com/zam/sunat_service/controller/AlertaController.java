package com.zam.sunat_service.controller;

import com.zam.sunat_service.service.AlertaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Alertas del sistema (vencimientos, días sin ventas...). Son compartidas: marcar una como leída la marca para todos.
 */
@RestController
@RequestMapping(AlertaController.API_PATH)
@PreAuthorize("hasAuthority('PERMISSION_ALERT_READ')")
public class AlertaController {

    public static final String API_PATH = "/api/alertas";

    private final AlertaService alertaService;

    @Autowired
    public AlertaController(AlertaService alertaService) {
        this.alertaService = alertaService;
    }

    @GetMapping
    public ResponseEntity<?> listar(
            @RequestParam(required = false) Boolean leida,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(alertaService.listar(leida, page, size));
    }

    @GetMapping("/no-leidas/count")
    public ResponseEntity<?> contarNoLeidas() {
        return ResponseEntity.ok(Map.of("total", alertaService.contarNoLeidas()));
    }

    @PatchMapping("/{alertaId}/leida")
    public ResponseEntity<?> marcarLeida(@PathVariable Long alertaId) {
        alertaService.marcarLeida(alertaId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/leidas")
    public ResponseEntity<?> marcarTodasLeidas() {
        return ResponseEntity.ok(Map.of("marcadas", alertaService.marcarTodasLeidas()));
    }

}
