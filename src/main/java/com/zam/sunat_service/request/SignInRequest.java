package com.zam.sunat_service.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SignInRequest {

    @NotNull(message = "Username must not be null")
    private String username;

    @NotNull(message = "Password must not be null")
    private String password;

}
