package com.zam.sunat_service.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ClientRequest {

    @NotBlank(message = "El RUC es obligatorio")
    @Pattern(regexp = "^(10|15|17|20)\\d{9}$", message = "El RUC debe tener 11 dígitos y empezar con 10, 15, 17 o 20")
    private String ruc;

    @NotBlank(message = "La razón social es obligatoria")
    @Size(max = 255, message = "La razón social no puede superar los 255 caracteres")
    private String businessName;

    @Size(max = 255, message = "El nombre comercial no puede superar los 255 caracteres")
    private String tradeName;

    @Size(max = 255, message = "La dirección no puede superar los 255 caracteres")
    private String address;

    @NotBlank(message = "El usuario SOL es obligatorio")
    @Size(max = 20, message = "El usuario SOL no puede superar los 20 caracteres")
    private String solUser;

    // Obligatoria al crear; al editar es opcional (si viene vacía se conserva la actual).
    @Size(max = 100, message = "La clave SOL no puede superar los 100 caracteres")
    private String solPassword;

    // Credenciales de la API SUNAT (opcionales). client_id vacío = el cliente no usa la API (se borran ambas).
    @Size(max = 100, message = "El client_id no puede superar los 100 caracteres")
    @Pattern(regexp = "^\\S*$", message = "El client_id no puede contener espacios")
    private String sunatClientId;

    // Obligatorio si se indica client_id y aún no hay uno guardado; al editar, vacío = conservar el actual.
    @Size(max = 100, message = "El client_secret no puede superar los 100 caracteres")
    private String sunatClientSecret;

    @Size(max = 255, message = "La persona de contacto no puede superar los 255 caracteres")
    private String contactName;

    @Pattern(regexp = "^$|^\\d{9}$", message = "El teléfono debe tener 9 dígitos")
    private String phoneNumber;

    // Opcional: null = al crear, false; al editar, conservar el valor actual.
    private Boolean trabajaDomingos;

    @Email(message = "El correo no tiene un formato válido")
    @Size(max = 100, message = "El correo no puede superar los 100 caracteres")
    private String email;

}
