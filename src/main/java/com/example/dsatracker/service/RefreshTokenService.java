package com.example.dsatracker.service;

import com.example.dsatracker.dto.AuthenticationResponse;
import com.example.dsatracker.exception.InvalidCredentialsException;
import com.example.dsatracker.model.RefreshToken;
import com.example.dsatracker.model.User;
import com.example.dsatracker.repository.RefreshTokenRepository;
import com.example.dsatracker.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int TOKEN_BYTE_LENGTH = 32;

    @Value("${jwt.refresh-expiration:2592000000}")
    private long refreshExpirationMs;

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            JwtService jwtService
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
    }

    @Transactional
    public String createRefreshToken(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User must not be null when creating refresh token");
        }

        String rawToken = generateRawToken();
        String tokenHash = hashToken(rawToken);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusSeconds(refreshExpirationMs / 1000);

        RefreshToken refreshToken = RefreshToken.builder()
                .tokenHash(tokenHash)
                .user(user)
                .createdAt(now)
                .expiresAt(expiresAt)
                .revokedAt(null)
                .lastUsedAt(null)
                .build();

        refreshTokenRepository.save(refreshToken);
        log.info("Refresh token created for user ID {}", user.getId());

        return rawToken;
    }

    @Transactional
    public AuthenticationResponse refreshAccessToken(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new InvalidCredentialsException("Refresh token must not be blank");
        }

        String tokenHash = hashToken(rawRefreshToken.trim());

        RefreshToken currentToken = refreshTokenRepository.findByTokenHashWithUser(tokenHash)
                .or(() -> refreshTokenRepository.findByTokenHash(tokenHash))
                .orElseThrow(() -> {
                    log.warn("Refresh token rejected: token not found");
                    return new InvalidCredentialsException("Invalid, expired, or revoked refresh token");
                });

        if (!currentToken.isValid()) {
            log.warn("Refresh token rejected: expired or revoked for user ID {}", currentToken.getUser().getId());
            throw new InvalidCredentialsException("Invalid, expired, or revoked refresh token");
        }

        LocalDateTime now = LocalDateTime.now();

        // Strict rotation: revoke current token immediately
        currentToken.setRevokedAt(now);
        currentToken.setLastUsedAt(now);
        refreshTokenRepository.save(currentToken);

        User user = currentToken.getUser();
        String newAccessToken = jwtService.generateToken(user.getEmail());
        String newRefreshToken = createRefreshToken(user);

        log.info("Access token successfully refreshed with strict rotation for user ID {}", user.getId());

        return AuthenticationResponse.builder()
                .token(newAccessToken)
                .refreshToken(newRefreshToken)
                .build();
    }

    @Transactional
    public void revokeToken(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }

        String tokenHash = hashToken(rawRefreshToken.trim());
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(LocalDateTime.now());
                refreshTokenRepository.save(token);
                log.info("Refresh token explicitly revoked for user ID {}", token.getUser().getId());
            }
        });
    }

    public String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available", e);
        }
    }

    private String generateRawToken() {
        byte[] randomBytes = new byte[TOKEN_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
