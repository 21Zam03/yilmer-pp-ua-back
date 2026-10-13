package com.zam.sunat_service.controller;

import com.zam.sunat_service.request.SignInRequest;
import com.zam.sunat_service.response.SignInResponse;
import com.zam.sunat_service.response.UserLoggedResponse;
import com.zam.sunat_service.service.AuthService;
import com.zam.sunat_service.util.CookieUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(AuthController.API_PATH)
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    public static final String API_PATH = "/api/auth";

    private final AuthService authService;
    private final CookieUtil cookieUtil;

    @Autowired
    public AuthController(AuthService authService, CookieUtil cookieUtil) {
        this.authService = authService;
        this.cookieUtil = cookieUtil;
    }

    @PostMapping("/signIn")
    public ResponseEntity<?> signIn(@Valid @RequestBody SignInRequest signInRequest, HttpServletResponse response) {
        logger.info("Intento de login para usuario: {}", signInRequest.getUsername());
        SignInResponse signInResponse = authService.signIn(signInRequest);

        response.addCookie(cookieUtil.getCookie(signInResponse.getToken()));

        signInResponse.setToken(null);
        return new ResponseEntity<>(signInResponse, HttpStatus.OK);
    }

    @PostMapping("/signOut")
    public ResponseEntity<?> signOut(HttpServletResponse response) {
        response.addCookie(cookieUtil.getExpiredCookie());
        return new ResponseEntity<>(HttpStatus.NO_CONTENT);
    }

    @GetMapping("/validate")
    public ResponseEntity<?> validate(HttpServletRequest request) {
        String token = extractTokenFromCookie(request);
        if (token == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("No token provided on cookie: Usuario no esta logueado");
        }
        UserLoggedResponse userLoggedResponse = authService.validateSession(token);
        return new ResponseEntity<>(userLoggedResponse, HttpStatus.OK);
    }

    private String extractTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (cookieUtil.getCookieName().equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

}
