package com.zam.sunat_service.service;

import com.zam.sunat_service.request.SignInRequest;
import com.zam.sunat_service.response.SignInResponse;
import com.zam.sunat_service.response.UserLoggedResponse;

public interface AuthService {

    SignInResponse signIn(SignInRequest signInRequest);
    UserLoggedResponse validateSession(String token);

}
