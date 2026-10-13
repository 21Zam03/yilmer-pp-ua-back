package com.zam.sunat_service.security.filter;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.zam.sunat_service.security.custom.CustomUserDetailService;
import com.zam.sunat_service.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class JwtCookieTokenFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtCookieTokenFilter.class);

    private final JwtUtil jwtUtil;
    private final CustomUserDetailService customUserDetailService;

    @Value("${parameters.cookie_name}")
    private String cookieName;

    public JwtCookieTokenFilter(JwtUtil jwtUtil, CustomUserDetailService customUserDetailService) {
        this.jwtUtil = jwtUtil;
        this.customUserDetailService = customUserDetailService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        Cookie[] cookies = request.getCookies();

        if (cookies != null) {
            for (Cookie cookie : cookies) {

                if (cookieName.equals(cookie.getName())) {
                    try {
                        String jwtToken = cookie.getValue();

                        DecodedJWT decodedJWT = jwtUtil.verifyToken(jwtToken);

                        String username = decodedJWT.getSubject();

                        UserDetails userDetails = customUserDetailService.loadUserByUsername(username);

                        List<String> roles = decodedJWT.getClaim("roles").asList(String.class);
                        List<String> permisos = decodedJWT.getClaim("permissions").asList(String.class);

                        List<GrantedAuthority> authorities = new ArrayList<>();

                        if (roles != null) {
                            authorities.addAll(
                                    roles.stream()
                                            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                                            .toList()
                            );
                        }

                        if (permisos != null) {
                            authorities.addAll(
                                    permisos.stream()
                                            .map(perm -> new SimpleGrantedAuthority("PERMISSION_" + perm))
                                            .toList()
                            );
                        }

                        Authentication auth = new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                authorities
                        );

                        SecurityContextHolder.getContext().setAuthentication(auth);

                    } catch (Exception e) {
                        // A diferencia de Zavefy, no se corta la request: con una cookie vencida o
                        // inválida el usuario quedaría bloqueado incluso para /api/auth/signIn.
                        // Se sigue como anónimo y las rutas protegidas responden 401 vía el entry point.
                        logger.warn("Token inválido: {}", e.getMessage());
                        SecurityContextHolder.clearContext();
                    }
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
