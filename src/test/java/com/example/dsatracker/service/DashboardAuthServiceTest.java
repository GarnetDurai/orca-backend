package com.example.dsatracker.service;

import com.example.dsatracker.dto.AuthenticationResponse;
import com.example.dsatracker.exception.InvalidCredentialsException;
import com.example.dsatracker.exception.ResourceNotFoundException;
import com.example.dsatracker.model.DashboardAuthCode;
import com.example.dsatracker.model.User;
import com.example.dsatracker.repository.DashboardAuthCodeRepository;
import com.example.dsatracker.repository.UserRepository;
import com.example.dsatracker.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DashboardAuthServiceTest {

    @Mock
    private DashboardAuthCodeRepository repository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private DashboardAuthService service;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .name("Alex Smith")
                .email("alex@example.com")
                .password("encoded_pwd")
                .build();
    }

    @Test
    @DisplayName("1. Authenticated user can generate dashboard code")
    void testCreateDashboardCodeSuccess() {
        when(userRepository.findByEmail("alex@example.com")).thenReturn(Optional.of(testUser));

        String rawCode = service.createDashboardCode("alex@example.com");

        assertThat(rawCode).isNotBlank();
        // 32 bytes URL-safe base64 without padding is 43 characters
        assertThat(rawCode).hasSize(43);
        assertThat(rawCode).matches("^[A-Za-z0-9_-]+$");

        verify(repository, times(1)).save(any(DashboardAuthCode.class));
    }

    @Test
    @DisplayName("2. Unauthenticated / missing user cannot generate dashboard code")
    void testCreateDashboardCodeUserNotFound() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createDashboardCode("unknown@example.com"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Authenticated user not found");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("3. Generated code has 60-second expiration")
    void testGeneratedCodeExpiration() {
        when(userRepository.findByEmail("alex@example.com")).thenReturn(Optional.of(testUser));

        ArgumentCaptor<DashboardAuthCode> captor = ArgumentCaptor.forClass(DashboardAuthCode.class);

        LocalDateTime before = LocalDateTime.now();
        service.createDashboardCode("alex@example.com");
        LocalDateTime after = LocalDateTime.now();

        verify(repository).save(captor.capture());
        DashboardAuthCode saved = captor.getValue();

        assertThat(saved.getCreatedAt()).isAfterOrEqualTo(before).isBeforeOrEqualTo(after);
        assertThat(saved.getExpiresAt()).isEqualTo(saved.getCreatedAt().plusSeconds(60));
        assertThat(saved.getUsedAt()).isNull();
    }

    @Test
    @DisplayName("4. Generated code is persisted as a hash, not raw code")
    void testGeneratedCodePersistedAsHash() {
        when(userRepository.findByEmail("alex@example.com")).thenReturn(Optional.of(testUser));

        ArgumentCaptor<DashboardAuthCode> captor = ArgumentCaptor.forClass(DashboardAuthCode.class);

        String rawCode = service.createDashboardCode("alex@example.com");

        verify(repository).save(captor.capture());
        DashboardAuthCode saved = captor.getValue();

        // Must NOT equal raw code
        assertThat(saved.getCodeHash()).isNotEqualTo(rawCode);
        // SHA-256 hex string is 64 characters
        assertThat(saved.getCodeHash()).hasSize(64);
        assertThat(saved.getCodeHash()).isEqualTo(service.hashRawCode(rawCode));
    }

    @Test
    @DisplayName("5. Valid code can be exchanged")
    void testExchangeValidCode() {
        String rawCode = "valid-random-temporary-code-123456789012345";
        String hash = service.hashRawCode(rawCode);

        DashboardAuthCode authCode = DashboardAuthCode.builder()
                .id(100L)
                .codeHash(hash)
                .user(testUser)
                .expiresAt(LocalDateTime.now().plusSeconds(50))
                .createdAt(LocalDateTime.now().minusSeconds(10))
                .build();

        when(repository.consumeCodeIfValid(eq(hash), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(1);
        when(repository.findByCodeHashWithUser(hash)).thenReturn(Optional.of(authCode));
        when(jwtService.generateToken("alex@example.com")).thenReturn("mock-dashboard-jwt-token");

        AuthenticationResponse response = service.exchangeDashboardCode(rawCode);

        assertThat(response).isNotNull();
        assertThat(response.getToken()).isEqualTo("mock-dashboard-jwt-token");
    }

    @Test
    @DisplayName("6. Exchange returns a valid JWT bound to user")
    void testExchangeReturnsValidJwt() {
        String rawCode = "code-for-jwt-check";
        String hash = service.hashRawCode(rawCode);

        DashboardAuthCode authCode = DashboardAuthCode.builder()
                .id(101L)
                .codeHash(hash)
                .user(testUser)
                .build();

        when(repository.consumeCodeIfValid(eq(hash), any(), any())).thenReturn(1);
        when(repository.findByCodeHashWithUser(hash)).thenReturn(Optional.of(authCode));
        when(jwtService.generateToken("alex@example.com")).thenReturn("jwt.token.alex");

        AuthenticationResponse response = service.exchangeDashboardCode(rawCode);

        assertThat(response.getToken()).isEqualTo("jwt.token.alex");
        verify(jwtService).generateToken("alex@example.com");
    }

    @Test
    @DisplayName("7. Exchanged code is marked used atomically")
    void testExchangedCodeMarkedUsed() {
        String rawCode = "code-to-mark-used";
        String hash = service.hashRawCode(rawCode);

        when(repository.consumeCodeIfValid(eq(hash), any(LocalDateTime.class), any(LocalDateTime.class))).thenReturn(1);
        when(repository.findByCodeHashWithUser(hash)).thenReturn(Optional.of(DashboardAuthCode.builder().user(testUser).build()));
        when(jwtService.generateToken(any())).thenReturn("token");

        service.exchangeDashboardCode(rawCode);

        // Verify repository atomic consumption was called with codeHash and server times
        verify(repository).consumeCodeIfValid(eq(hash), any(LocalDateTime.class), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("8. Same code cannot be exchanged twice (rejects already-used code)")
    void testSameCodeCannotBeExchangedTwice() {
        String rawCode = "code-single-use";
        String hash = service.hashRawCode(rawCode);

        // When code is already used, consumeCodeIfValid returns 0 updated rows
        when(repository.consumeCodeIfValid(eq(hash), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.exchangeDashboardCode(rawCode))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid or expired authorization code");

        verify(jwtService, never()).generateToken(any());
    }

    @Test
    @DisplayName("9. Expired code is rejected")
    void testExpiredCodeRejected() {
        String rawCode = "code-expired";
        String hash = service.hashRawCode(rawCode);

        // DB condition expires_at > now fails, returning 0 updated rows
        when(repository.consumeCodeIfValid(eq(hash), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.exchangeDashboardCode(rawCode))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid or expired authorization code");

        verify(jwtService, never()).generateToken(any());
    }

    @Test
    @DisplayName("10. Invalid / random non-existent code is rejected")
    void testInvalidRandomCodeRejected() {
        String rawCode = "non-existent-random-code";
        String hash = service.hashRawCode(rawCode);

        when(repository.consumeCodeIfValid(eq(hash), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.exchangeDashboardCode(rawCode))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid or expired authorization code");

        verify(jwtService, never()).generateToken(any());
    }

    @Test
    @DisplayName("11. Blank or null code is rejected with 400 IllegalArgumentException")
    void testBlankCodeRejected() {
        assertThatThrownBy(() -> service.exchangeDashboardCode(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");

        assertThatThrownBy(() -> service.exchangeDashboardCode(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");

        assertThatThrownBy(() -> service.exchangeDashboardCode("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");

        verify(repository, never()).consumeCodeIfValid(any(), any(), any());
    }

    @Test
    @DisplayName("12. Code is associated with the correct user")
    void testCodeAssociatedWithCorrectUser() {
        User userB = User.builder().id(2L).email("bob@example.com").build();
        String rawCode = "code-for-bob";
        String hash = service.hashRawCode(rawCode);

        DashboardAuthCode authCode = DashboardAuthCode.builder()
                .codeHash(hash)
                .user(userB)
                .build();

        when(repository.consumeCodeIfValid(eq(hash), any(), any())).thenReturn(1);
        when(repository.findByCodeHashWithUser(hash)).thenReturn(Optional.of(authCode));
        when(jwtService.generateToken("bob@example.com")).thenReturn("bob.jwt.token");

        AuthenticationResponse response = service.exchangeDashboardCode(rawCode);

        assertThat(response.getToken()).isEqualTo("bob.jwt.token");
        verify(jwtService).generateToken("bob@example.com");
    }

    @Test
    @DisplayName("13. User A cannot obtain User B's dashboard identity through code exchange")
    void testUserCannotObtainAnotherUsersIdentity() {
        // User B generates code
        User userB = User.builder().id(2L).email("bob@example.com").build();
        when(userRepository.findByEmail("bob@example.com")).thenReturn(Optional.of(userB));

        ArgumentCaptor<DashboardAuthCode> captor = ArgumentCaptor.forClass(DashboardAuthCode.class);
        String bobRawCode = service.createDashboardCode("bob@example.com");
        verify(repository).save(captor.capture());

        DashboardAuthCode bCode = captor.getValue();
        assertThat(bCode.getUser().getEmail()).isEqualTo("bob@example.com");

        // When exchanged, it must generate token for Bob, NOT Alex
        when(repository.consumeCodeIfValid(eq(bCode.getCodeHash()), any(), any())).thenReturn(1);
        when(repository.findByCodeHashWithUser(bCode.getCodeHash())).thenReturn(Optional.of(bCode));
        when(jwtService.generateToken("bob@example.com")).thenReturn("token-for-bob");

        AuthenticationResponse response = service.exchangeDashboardCode(bobRawCode);
        assertThat(response.getToken()).isEqualTo("token-for-bob");
        verify(jwtService, never()).generateToken("alex@example.com");
    }

    @Test
    @DisplayName("14. Concurrent redemption cannot result in two successful exchanges")
    void testConcurrentRedemptionSimultaneousRequests() throws Exception {
        String rawCode = "concurrent-code";
        String hash = service.hashRawCode(rawCode);

        // Simulate DB behavior: first thread updates 1 row, second thread updates 0 rows
        AtomicInteger callCount = new AtomicInteger(0);
        when(repository.consumeCodeIfValid(eq(hash), any(), any())).thenAnswer(invocation -> {
            if (callCount.getAndIncrement() == 0) {
                return 1; // 1st succeeds
            }
            return 0; // 2nd fails
        });

        DashboardAuthCode authCode = DashboardAuthCode.builder().user(testUser).build();
        when(repository.findByCodeHashWithUser(hash)).thenReturn(Optional.of(authCode));
        when(jwtService.generateToken(any())).thenReturn("token");

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch latch = new CountDownLatch(2);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        for (int i = 0; i < 2; i++) {
            executor.submit(() -> {
                try {
                    service.exchangeDashboardCode(rawCode);
                    successCount.incrementAndGet();
                } catch (InvalidCredentialsException e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(1);
    }
}
