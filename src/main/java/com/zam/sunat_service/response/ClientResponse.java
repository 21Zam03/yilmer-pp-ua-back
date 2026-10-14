package com.zam.sunat_service.response;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
public class ClientResponse {

    private Integer clientId;
    private String ruc;
    private String businessName;
    private String tradeName;
    private String address;
    private String solUser;
    // Solo indica si hay clave SOL registrada; la clave nunca sale del backend.
    private boolean hasSolPassword;
    // El client_id no es secreto y se muestra; el client_secret, igual que la clave SOL, nunca sale del backend.
    private String sunatClientId;
    private boolean hasSunatClientSecret;
    private String contactName;
    private String phoneNumber;
    private String email;
    private boolean enabled;
    private LocalDateTime createdDate;
    private LocalDateTime updatedDate;

}
