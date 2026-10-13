package com.zam.sunat_service.controller;

import com.zam.sunat_service.entity.UserEntity;
import com.zam.sunat_service.security.annotation.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// Endpoints de prueba para verificar roles y permisos. Eliminar cuando ya no se necesiten.
@RestController
@RequestMapping("/api/test")
public class TestController {

    @GetMapping("/me")
    public ResponseEntity<?> me(@CurrentUser UserEntity user, Authentication authentication) {
        return ResponseEntity.ok(Map.of(
                "username", user.getUsername(),
                "authorities", authentication.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .sorted()
                        .toList()
        ));
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> adminOnly() {
        return ResponseEntity.ok(Map.of("message", "Acceso permitido: rol ADMIN"));
    }

    @PostMapping("/invoices")
    @PreAuthorize("hasAuthority('PERMISSION_INVOICE_CREATE')")
    public ResponseEntity<?> createInvoice() {
        return ResponseEntity.ok(Map.of("message", "Acceso permitido: INVOICE_CREATE"));
    }

    @PostMapping("/invoices/void")
    @PreAuthorize("hasAuthority('PERMISSION_INVOICE_VOID')")
    public ResponseEntity<?> voidInvoice() {
        return ResponseEntity.ok(Map.of("message", "Acceso permitido: INVOICE_VOID"));
    }

}
