package com.zam.sunat_service.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ProgramacionSireRequest {

    @NotBlank(message = "El RUC es obligatorio")
    @Pattern(regexp = "^(10|15|17|20)\\d{9}$", message = "El RUC debe tener 11 dígitos y empezar con 10, 15, 17 o 20")
    private String ruc;

    @NotNull(message = "Indique si la sincronización automática está activa")
    private Boolean activo;

    // 1, 3, 6, 12 o 24 (se valida en el servicio).
    @NotNull(message = "El intervalo es obligatorio")
    private Integer intervaloHoras;

}
