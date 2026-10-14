package com.zam.sunat_service.controller;

import com.zam.sunat_service.request.ClientRequest;
import com.zam.sunat_service.service.ClientService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(ClientController.API_PATH)
public class ClientController {

    public static final String API_PATH = "/api/clients";

    private final ClientService clientService;

    @Autowired
    public ClientController(ClientService clientService) {
        this.clientService = clientService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERMISSION_CLIENT_READ')")
    public ResponseEntity<?> getClients(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String searchKey,
            @RequestParam(required = false) Boolean active
    ) {
        return ResponseEntity.ok(clientService.getClients(searchKey, active, page, size));
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAuthority('PERMISSION_CLIENT_READ')")
    public ResponseEntity<?> getStats() {
        return ResponseEntity.ok(clientService.getStats());
    }

    @GetMapping("/{clientId}")
    @PreAuthorize("hasAuthority('PERMISSION_CLIENT_READ')")
    public ResponseEntity<?> getClient(@PathVariable Integer clientId) {
        return ResponseEntity.ok(clientService.getClient(clientId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERMISSION_CLIENT_CREATE')")
    public ResponseEntity<?> createClient(@Valid @RequestBody ClientRequest request) {
        return new ResponseEntity<>(clientService.createClient(request), HttpStatus.CREATED);
    }

    @PutMapping("/{clientId}")
    @PreAuthorize("hasAuthority('PERMISSION_CLIENT_UPDATE')")
    public ResponseEntity<?> updateClient(@PathVariable Integer clientId, @Valid @RequestBody ClientRequest request) {
        return ResponseEntity.ok(clientService.updateClient(clientId, request));
    }

    // Activar / desactivar (baja lógica), usado desde el menú de acciones de "Mis clientes".
    @PatchMapping("/{clientId}/status")
    @PreAuthorize("hasAuthority('PERMISSION_CLIENT_UPDATE')")
    public ResponseEntity<?> updateStatus(@PathVariable Integer clientId, @RequestParam boolean enabled) {
        return ResponseEntity.ok(clientService.updateStatus(clientId, enabled));
    }

    @DeleteMapping("/{clientId}")
    @PreAuthorize("hasAuthority('PERMISSION_CLIENT_DELETE')")
    public ResponseEntity<?> deleteClient(@PathVariable Integer clientId) {
        clientService.deleteClient(clientId);
        return ResponseEntity.noContent().build();
    }

}
