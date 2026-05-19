package com.sepa1914.adminservice.api.auth;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthenticationManager authenticationManager;

    public AuthController(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {

        try {

            Authentication authentication =
                    authenticationManager.authenticate(
                            new UsernamePasswordAuthenticationToken(
                                    request.getUsername(),
                                    request.getPassword()
                            )
                    );

            if (authentication.isAuthenticated()) {

                LoginResponse response = new LoginResponse(
                        true,
                        "Login correcto",
                        request.getUsername()
                );

                return ResponseEntity.ok(response);
            }

        } catch (AuthenticationException e) {

            LoginResponse response = new LoginResponse(
                    false,
                    "Usuario o contraseña incorrectos",
                    null
            );

            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }

        LoginResponse response = new LoginResponse(
                false,
                "Error de autenticación",
                null
        );

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }
}