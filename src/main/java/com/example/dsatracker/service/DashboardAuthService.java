package com.example.dsatracker.service;

import com.example.dsatracker.dto.AuthenticationResponse;
import com.example.dsatracker.exception.InvalidCredentialsException;
import com.example.dsatracker.exception.ResourceNotFoundException;
import com.example.dsatracker.model.DashboardAuthCode;
import com.example.dsatracker.model.User;
import com.example.dsatracker.repository.DashboardAuthCodeRepository;
import com.example.dsatracker.repository.UserRepository;
import com.example.dsatracker.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class DashboardAuthService {

    private static final Logger log = LoggerFactory.getLogger(DashboardAuthService.class);
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final int CODE_BYTE_LENGTH = 32;
    private static final long CODE_LIFETIME_SECONDS = 60;

    private final DashboardAuthCodeRepository repository;
    private final UserRepository userRepository;
    private final JwtService jwtService;

    public DashboardAuthService(
            DashboardAuthCodeRepository repository,
            UserRepository userRepository,
            JwtService jwtService
    ) {
        this.repository = repository;
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    @Transactional
    public String createDashboardCode(String userEmail) {
        if (userEmail == null || userEmail.isBlank()) {
            throw new ResourceNotFoundException("Authenticated user email required");
        }

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found"));

        String rawCode = generateRawCode();
        String codeHash = hashRawCode(rawCode);

        LocalDateTime now = LocalDateTime.now();
        DashboardAuthCode authCode = DashboardAuthCode.builder()
                .codeHash(codeHash)
                .user(user)
                .createdAt(now)
                .expiresAt(now.plusSeconds(CODE_LIFETIME_SECONDS))
                .usedAt(null)
                .build();

        repository.save(authCode);
        log.info("Dashboard authorization code generated for user ID {}", user.getId());

        return rawCode;
    }

    @Transactional
    public AuthenticationResponse exchangeDashboardCode(String rawCode) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new IllegalArgumentException("Authorization code must not be blank");
        }

        String codeHash = hashRawCode(rawCode.trim());
        LocalDateTime now = LocalDateTime.now();

        // Concurrency-safe atomic consumption:
        // Updates usedAt only if codeHash matches, usedAt IS NULL, and expiresAt > now.
        int updated = repository.consumeCodeIfValid(codeHash, now, now);
        if (updated == 0) {
            log.warn("Dashboard authorization code exchange rejected: invalid, expired, or already used");
            throw new InvalidCredentialsException("Invalid or expired authorization code");
        }

        DashboardAuthCode authCode = repository.findByCodeHashWithUser(codeHash)
                .or(() -> repository.findByCodeHash(codeHash))
                .orElseThrow(() -> new InvalidCredentialsException("Invalid or expired authorization code"));

        User user = authCode.getUser();
        String token = jwtService.generateToken(user.getEmail());
        log.info("Dashboard authorization code successfully consumed for user ID {}", user.getId());

        return AuthenticationResponse.builder()
                .token(token)
                .build();
    }

    public String hashRawCode(String rawCode) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawCode.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available", e);
        }
    }

    private String generateRawCode() {
        byte[] randomBytes = new byte[CODE_BYTE_LENGTH];
        SECURE_RANDOM.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
