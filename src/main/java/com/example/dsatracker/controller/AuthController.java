package com.example.dsatracker.controller;

import com.example.dsatracker.dto.AuthenticationResponse;
import com.example.dsatracker.dto.DashboardCodeResponseDTO;
import com.example.dsatracker.dto.ExchangeCodeRequestDTO;
import com.example.dsatracker.dto.LoginRequestDTO;
import com.example.dsatracker.dto.RegisterRequestDTO;
import com.example.dsatracker.service.AuthService;
import com.example.dsatracker.service.DashboardAuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService service;
    private final DashboardAuthService dashboardAuthService;

    public AuthController(
            AuthService service,
            DashboardAuthService dashboardAuthService
    ) {
        this.service = service;
        this.dashboardAuthService = dashboardAuthService;
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