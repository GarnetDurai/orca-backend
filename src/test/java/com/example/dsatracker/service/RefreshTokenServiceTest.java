package com.example.dsatracker.service;

import com.example.dsatracker.dto.AuthenticationResponse;
import com.example.dsatracker.exception.InvalidCredentialsException;
import com.example.dsatracker.model.RefreshToken;
import com.example.dsatracker.model.User;
import com.example.dsatracker.repository.RefreshTokenRepository;
import com.example.dsatracker.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    private User testUser;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(refreshTokenService, "refreshExpirationMs", 2592000000L); // 30 days

        testUser = User.builder()
                .id(1L)
                .name("Test User")
                .email("test@example.com")
                .password("encoded_pass")
                .build();
    }

    @Test
    @DisplayName("1. Successfully creates and hashes refresh token")
    void testCreateRefreshToken() {
        String rawToken = refreshTokenService.createRefreshToken(testUser);

        assertThat(rawToken).isNotNull().isNotBlank();

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(captor.capture());

        RefreshToken saved = captor.getValue();
        assertThat(saved.getUser()).isEqualTo(testUser);
        assertThat(saved.getTokenHash()).isEqualTo(refreshTokenService.hashToken(rawToken));
        assertThat(saved.getExpiresAt()).isAfter(LocalDateTime.now().plusDays(29));
        assertThat(saved.getRevokedAt()).isNull();
    }

    @Test
    @DisplayName("2. Refresh with valid token succeeds and strictly rotates the token")
    void testRefreshAccessToken_SuccessWithRotation() {
        String rawToken = "valid-raw-refresh-token";
        String tokenHash = refreshTokenService.hashToken(rawToken);

        RefreshToken currentToken = RefreshToken.builder()
                .id(10L)
                .tokenHash(tokenHash)
                .user(testUser)
                .createdAt(LocalDateTime.now().minusDays(1))
                .expiresAt(LocalDateTime.now().plusDays(29))
                .revokedAt(null)
                .build();

        when(refreshTokenRepository.findByTokenHashWithUser(tokenHash)).thenReturn(Optional.of(currentToken));
        when(jwtService.generateToken("test@example.com")).thenReturn("new-jwt-access-token");

        AuthenticationResponse response = refreshTokenService.refreshAccessToken(rawToken);

        assertThat(response).isNotNull();
        assertThat(response.getToken()).isEqualTo("new-jwt-access-token");
        assertThat(response.getRefreshToken()).isNotNull().isNotEqualTo(rawToken);

        // Verify current token was revoked
        assertThat(currentToken.getRevokedAt()).isNotNull();
        assertThat(currentToken.getLastUsedAt()).isNotNull();

        // Verify new refresh token was saved
        verify(refreshTokenRepository, atLeast(2)).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("3. Refresh with already-revoked token is rejected")
    void testRefreshAccessToken_RevokedToken_Rejected() {
        String rawToken = "revoked-raw-refresh-token";
        String tokenHash = refreshTokenService.hashToken(rawToken);

        RefreshToken revokedToken = RefreshToken.builder()
                .id(11L)
                .tokenHash(tokenHash)
                .user(testUser)
                .createdAt(LocalDateTime.now().minusDays(2))
                .expiresAt(LocalDateTime.now().plusDays(28))
                .revokedAt(LocalDateTime.now().minusHours(1))
                .build();

        when(refreshTokenRepository.findByTokenHashWithUser(tokenHash)).thenReturn(Optional.of(revokedToken));

        assertThatThrownBy(() -> refreshTokenService.refreshAccessToken(rawToken))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("Invalid, expired, or revoked refresh token");

        verify(jwtService, never()).generateToken(any());
    }

    @Test
    @DisplayName("4. Refresh with expired token is rejected")
    void testRefreshAccessToken_ExpiredToken_Rejected() {
        String rawToken = "expired-raw-refresh-token";
        String tokenHash = refreshTokenService.hashToken(rawToken);

        RefreshToken expiredToken = RefreshToken.builder()
                .id(12L)
                .tokenHash(tokenHash)
                .user(testUser)
                .createdAt(LocalDateTime.now().minusDays(35))
                .expiresAt(LocalDateTime.now().minusDays(5))
                .revokedAt(null)
                .build();

        when(refreshTokenRepository.findByTokenHashWithUser(tokenHash)).thenReturn(Optional.of(expiredToken));

        assertThatThrownBy(() -> refreshTokenService.refreshAccessToken(rawToken))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("Invalid, expired, or revoked refresh token");

        verify(jwtService, never()).generateToken(any());
    }

    @Test
    @DisplayName("5. Refresh with unknown token hash is rejected")
    void testRefreshAccessToken_NotFound_Rejected() {
        String rawToken = "unknown-raw-refresh-token";
        String tokenHash = refreshTokenService.hashToken(rawToken);

        when(refreshTokenRepository.findByTokenHashWithUser(tokenHash)).thenReturn(Optional.empty());
        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.refreshAccessToken(rawToken))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("Invalid, expired, or revoked refresh token");
    }

    @Test
    @DisplayName("6. Refresh with blank or null token is rejected")
    void testRefreshAccessToken_BlankOrNull_Rejected() {
        assertThatThrownBy(() -> refreshTokenService.refreshAccessToken(null))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("Refresh token must not be blank");

        assertThatThrownBy(() -> refreshTokenService.refreshAccessToken("   "))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessageContaining("Refresh token must not be blank");
    }

    @Test
    @DisplayName("7. Explicit revocation marks token revoked")
    void testRevokeToken() {
        String rawToken = "token-to-revoke";
        String tokenHash = refreshTokenService.hashToken(rawToken);

        RefreshToken activeToken = RefreshToken.builder()
                .id(15L)
                .tokenHash(tokenHash)
                .user(testUser)
                .createdAt(LocalDateTime.now().minusDays(1))
                .expiresAt(LocalDateTime.now().plusDays(29))
                .revokedAt(null)
                .build();

        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(activeToken));

        refreshTokenService.revokeToken(rawToken);

        assertThat(activeToken.getRevokedAt()).isNotNull();
        verify(refreshTokenRepository).save(activeToken);
    }
}
