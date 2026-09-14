package com.example.dsatracker.controller;

import com.example.dsatracker.config.SecurityConfig;
import com.example.dsatracker.dto.AuthenticationResponse;
import com.example.dsatracker.dto.ExchangeCodeRequestDTO;
import com.example.dsatracker.exception.GlobalExceptionHandler;
import com.example.dsatracker.exception.InvalidCredentialsException;
import com.example.dsatracker.security.CustomUserDetailsService;
import com.example.dsatracker.security.JwtAuthenticationFilter;
import com.example.dsatracker.security.JwtService;
import com.example.dsatracker.service.AuthService;
import com.example.dsatracker.service.DashboardAuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class AuthControllerAuthCodeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private DashboardAuthService dashboardAuthService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private CustomUserDetailsService userDetailsService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setUp() throws Exception {
        // Pass-through filter for mock
        doAnswer(invocation -> {
            HttpServletRequest request = invocation.getArgument(0);
            HttpServletResponse response = invocation.getArgument(1);
            FilterChain filterChain = invocation.getArgument(2);
            filterChain.doFilter(request, response);
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    @Test
    @DisplayName("POST /auth/dashboard-code - Authenticated user succeeds (200 OK)")
    @WithMockUser(username = "alex@example.com")
    void testGenerateDashboardCodeSuccess() throws Exception {
        when(dashboardAuthService.createDashboardCode("alex@example.com"))
                .thenReturn("mock-temp-code-12345");

        mockMvc.perform(post("/auth/dashboard-code")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("mock-temp-code-12345"));

        verify(dashboardAuthService).createDashboardCode("alex@example.com");
    }

    @Test
    @DisplayName("POST /auth/dashboard-code - Unauthenticated request is rejected (401/403)")
    void testGenerateDashboardCodeUnauthenticated() throws Exception {
        mockMvc.perform(post("/auth/dashboard-code")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());

        verify(dashboardAuthService, never()).createDashboardCode(anyString());
    }

    @Test
    @DisplayName("POST /auth/exchange-code - Valid code returns JWT (200 OK)")
    void testExchangeCodeSuccess() throws Exception {
        ExchangeCodeRequestDTO request = ExchangeCodeRequestDTO.builder()
                .code("valid-temporary-code")
                .build();

        when(dashboardAuthService.exchangeDashboardCode("valid-temporary-code"))
                .thenReturn(AuthenticationResponse.builder().token("dashboard-jwt-abc").build());

        mockMvc.perform(post("/auth/exchange-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("dashboard-jwt-abc"));
    }

    @Test
    @DisplayName("POST /auth/exchange-code - Invalid / expired code returns 401 Unauthorized")
    void testExchangeCodeInvalidOrExpired() throws Exception {
        ExchangeCodeRequestDTO request = ExchangeCodeRequestDTO.builder()
                .code("expired-code")
                .build();

        when(dashboardAuthService.exchangeDashboardCode("expired-code"))
                .thenThrow(new InvalidCredentialsException("Invalid or expired authorization code"));

        mockMvc.perform(post("/auth/exchange-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid or expired authorization code"));
    }

    @Test
    @DisplayName("POST /auth/exchange-code - Blank code returns 400 Bad Request")
    void testExchangeCodeBlank() throws Exception {
        ExchangeCodeRequestDTO request = ExchangeCodeRequestDTO.builder()
                .code("")
                .build();

        mockMvc.perform(post("/auth/exchange-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("POST /auth/exchange-code - Malformed JSON body returns 400 Bad Request")
    void testExchangeCodeMalformedJson() throws Exception {
        mockMvc.perform(post("/auth/exchange-code")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{invalid-json}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Malformed JSON request body"));
    }
}
