package com.example.dsatracker.controller;

import com.example.dsatracker.dto.*;
import com.example.dsatracker.service.AuthService;
import com.example.dsatracker.service.DashboardAuthService;
import com.example.dsatracker.service.RefreshTokenService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService service;
    private final DashboardAuthService dashboardAuthService;
    private final RefreshTokenService refreshTokenService;

    public AuthController(
            AuthService service,
            DashboardAuthService dashboardAuthService,
            RefreshTokenService refreshTokenService
    ) {
        this.service = service;
        this.dashboardAuthService = dashboardAuthService;
        this.refreshTokenService = refreshTokenService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthenticationResponse> register(
            @Valid @RequestBody RegisterRequestDTO request) {
        return ResponseEntity.ok(service.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthenticationResponse> login(
            @Valid @RequestBody LoginRequestDTO request) {
        return ResponseEntity.ok(service.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthenticationResponse> refresh(
            @Valid @RequestBody RefreshTokenRequestDTO request) {
        AuthenticationResponse response = refreshTokenService.refreshAccessToken(request.getRefreshToken());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestBody(required = false) RefreshTokenRequestDTO request) {
        if (request != null && request.getRefreshToken() != null) {
            refreshTokenService.revokeToken(request.getRefreshToken());
        }
        return ResponseEntity.ok().build();
    }

    @PostMapping("/dashboard-code")
    public ResponseEntity<DashboardCodeResponseDTO> generateDashboardCode() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        String code = dashboardAuthService.createDashboardCode(email);
        return ResponseEntity.ok(DashboardCodeResponseDTO.builder().code(code).build());
    }

    @PostMapping("/exchange-code")
    public ResponseEntity<AuthenticationResponse> exchangeCode(
            @Valid @RequestBody ExchangeCodeRequestDTO request) {
        AuthenticationResponse response = dashboardAuthService.exchangeDashboardCode(request.getCode());
        return ResponseEntity.ok(response);
    }
}