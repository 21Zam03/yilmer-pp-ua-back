package com.zam.sunat_service.service.impl;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.zam.sunat_service.entity.PermissionEntity;
import com.zam.sunat_service.entity.RoleEntity;
import com.zam.sunat_service.entity.UserEntity;
import com.zam.sunat_service.exceptions.NotFoundException;
import com.zam.sunat_service.repository.UserRepository;
import com.zam.sunat_service.request.SignInRequest;
import com.zam.sunat_service.response.SignInResponse;
import com.zam.sunat_service.response.UserLoggedResponse;
import com.zam.sunat_service.service.AuthService;
import com.zam.sunat_service.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuthServiceImpl implements AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    @Autowired
    public AuthServiceImpl(AuthenticationManager authenticationManager,
                           UserRepository userRepository,
                           JwtUtil jwtUtil) {
        this.authenticationManager = authenticationManager;
        this.userRepository = userRepository;
        this.jwtUtil = jwtUtil;
    }

    @Override
    public SignInResponse signIn(SignInRequest signInRequest) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            signInRequest.getUsername(),
                            signInRequest.getPassword()
                    )
            );
        } catch (BadCredentialsException e) {
            throw new BadCredentialsException("Usuario o contraseña incorrectos");
        }

        UserEntity user = userRepository.findByUsername(signInRequest.getUsername())
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));

        List<String> roleNames = getRoleNames(user);
        List<String> permissionNames = getPermissionNames(user);

        String accessToken = jwtUtil.createToken(user.getUsername(), roleNames, permissionNames);

        SignInResponse signInResponse = new SignInResponse();
        signInResponse.setRole(roleNames.isEmpty() ? null : roleNames.get(0));
        signInResponse.setEmail(user.getEmail());
        signInResponse.setUsername(user.getUsername());
        signInResponse.setFirstname(user.getFirstname());
        signInResponse.setLastname(user.getLastname());
        signInResponse.setPasswordReset(user.isPasswordReset());
        signInResponse.setMessage("User logged successfully");
        signInResponse.setToken(accessToken);
        signInResponse.setStatus(200);

        return signInResponse;
    }

    @Override
    public UserLoggedResponse validateSession(String token) {
        DecodedJWT decodedJWT = jwtUtil.verifyToken(token);
        String username = jwtUtil.extractUsername(decodedJWT);
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("Usuario no encontrado"));

        UserLoggedResponse userLoggedResponse = new UserLoggedResponse();
        userLoggedResponse.setUserId(user.getUserId());
        userLoggedResponse.setEmail(user.getEmail());
        userLoggedResponse.setUsername(user.getUsername());
        userLoggedResponse.setFirstName(user.getFirstname());
        userLoggedResponse.setLastName(user.getLastname());
        userLoggedResponse.setPasswordUpdateDate(user.getPasswordUpdateDate());
        userLoggedResponse.setPasswordReset(user.isPasswordReset());
        userLoggedResponse.setRoles(getRoleNames(user));
        userLoggedResponse.setPermissions(getPermissionNames(user));
        return userLoggedResponse;
    }

    private List<String> getRoleNames(UserEntity user) {
        return user.getRoleList().stream()
                .map(RoleEntity::getName)
                .toList();
    }

    private List<String> getPermissionNames(UserEntity user) {
        return user.getRoleList().stream()
                .flatMap(role -> role.getPermissionList().stream())
                .map(PermissionEntity::getName)
                .distinct()
                .toList();
    }

}
