package com.zam.sunat_service.response;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class UserLoggedResponse {

    private Integer userId;
    private String firstName;
    private String lastName;
    private String email;
    private String username;
    private List<String> roles;
    private List<String> permissions;
    private LocalDateTime passwordUpdateDate;
    private boolean passwordReset;

}
