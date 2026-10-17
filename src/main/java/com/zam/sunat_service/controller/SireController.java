package com.zam.sunat_service.controller;

import com.zam.sunat_service.request.ProgramacionSireRequest;
import com.zam.sunat_service.request.SireSyncRequest;
import com.zam.sunat_service.service.ProgramacionSireService;
import com.zam.sunat_service.service.SireService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(SireController.API_PATH)
public class SireController {

    public static final String API_PATH = "/api/sire";

    private final SireService sireService;
    private final ProgramacionSireService programacionService;

    @Autowired
    public SireController(SireService sireService, ProgramacionSireService programacionService) {
        this.sireService = sireService;
        this.programacionService = programacionService;
    }

    // 202: SUNAT procesa la solicitud en segundo plano; el frontend consulta el estado del ticket devuelto.
    @PostMapping("/ventas/sincronizar")
    @PreAuthorize("hasAuthority('PERMISSION_SIRE_SYNC')")
    public ResponseEntity<?> sincronizarVentas(@Valid @RequestBody SireSyncRequest request) {
        return new ResponseEntity<>(sireService.sincronizarVentas(request), HttpStatus.ACCEPTED);
    }

    // Lee las ventas ya guardadas (no llama a SUNAT). Para refrescarlas se usa /ventas/sincronizar.
    @GetMapping("/ventas")
    @PreAuthorize("hasAuthority('PERMISSION_SIRE_READ')")
    public ResponseEntity<?> obtenerVentas(
            @RequestParam String ruc,
            @RequestParam String periodo,
            @RequestParam(required = false) String searchKey,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(sireService.obtenerVentas(ruc, periodo, searchKey, page, size));
    }

    @GetMapping("/ventas/programacion")
    @PreAuthorize("hasAuthority('PERMISSION_SIRE_READ')")
    public ResponseEntity<?> obtenerProgramacionVentas(@RequestParam String ruc) {
        return ResponseEntity.ok(programacionService.obtenerProgramacionVentas(ruc));
    }

    @PutMapping("/ventas/programacion")
    @PreAuthorize("hasAuthority('PERMISSION_SIRE_SYNC')")
    public ResponseEntity<?> guardarProgramacionVentas(@Valid @RequestBody ProgramacionSireRequest request) {
        return ResponseEntity.ok(programacionService.guardarProgramacionVentas(request));
    }

    @GetMapping("/tickets/{ticketId}/estado")
    @PreAuthorize("hasAuthority('PERMISSION_SIRE_READ')")
    public ResponseEntity<?> obtenerEstadoTicket(@PathVariable Long ticketId) {
        return ResponseEntity.ok(sireService.obtenerEstadoTicket(ticketId));
    }

}
