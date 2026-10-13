package com.zam.sunat_service.response;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SignInResponse {

    private String firstname;
    private String lastname;
    private String username;
    private String email;
    private String role;
    private boolean passwordReset;
    private String message;
    private String token;
    private Integer status;

}
