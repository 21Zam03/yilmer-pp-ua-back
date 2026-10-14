package com.zam.sunat_service.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SireSyncRequest {

    @NotBlank(message = "El RUC es obligatorio")
    @Pattern(regexp = "^(10|15|17|20)\\d{9}$", message = "El RUC debe tener 11 dígitos y empezar con 10, 15, 17 o 20")
    private String ruc;

    @NotBlank(message = "El periodo es obligatorio")
    @Pattern(regexp = "^\\d{4}(0[1-9]|1[0-2])$", message = "El periodo debe tener el formato yyyyMM (ej. 202610)")
    private String periodo;

}
