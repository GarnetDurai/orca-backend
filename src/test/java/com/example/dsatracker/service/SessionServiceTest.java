package com.example.dsatracker.service;

import com.example.dsatracker.dto.ProblemMetadataDTO;
import com.example.dsatracker.dto.ProblemSessionDetailsDTO;
import com.example.dsatracker.dto.ProblemSessionRequestDTO;
import com.example.dsatracker.dto.ProblemSessionResponseDTO;
import com.example.dsatracker.dto.SessionEventDTO;
import com.example.dsatracker.exception.DuplicateResourceException;
import com.example.dsatracker.exception.ResourceNotFoundException;
import com.example.dsatracker.model.*;
import com.example.dsatracker.repository.ProblemRepository;
import com.example.dsatracker.repository.ProblemSessionRepository;
import com.example.dsatracker.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock
    private ProblemSessionRepository sessionRepository;

    @Mock
    private ProblemRepository problemRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ConfidenceService confidenceService;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private SessionService sessionService;

    private User testUser;
    private Problem testProblem;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .name("Garnet")
                .email("garnet@example.com")
                .password("encoded_pass")
                .build();

        testProblem = Problem.builder()
                .id(10L)
                .leetcodeId(1)
                .title("Two Sum")
                .difficulty(Difficulty.EASY)
                .url("https://leetcode.com/problems/two-sum/")
                .build();

        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        lenient().when(authentication.getName()).thenReturn("garnet@example.com");
        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    @DisplayName("1. Valid session ingestion with problem lookup by leetcodeId")
    void testValidSessionIngestion() {
        ProblemSessionRequestDTO request = ProblemSessionRequestDTO.builder()
                .sessionId("sess-123")
                .problem(ProblemMetadataDTO.builder()
                        .leetcodeId(1)
                        .title("Two Sum")
                        .difficulty("EASY")
                        .url("https://leetcode.com/problems/two-sum/")
                        .build())
                .sessionStartedAt(1725040000000L)
                .firstCodingAt(1725040120000L)
                .solvedAt(1725040700000L)
                .thinkingDuration(120000L)
                .codingDuration(580000L)
                .totalTimeAway(0L)
                .tabSwitchCount(0)
                .language("typescript")
                .attempts(1)
                .solved(true)
                .build();

        when(sessionRepository.existsBySessionId("sess-123")).thenReturn(false);
        when(userRepository.findByEmail("garnet@example.com")).thenReturn(Optional.of(testUser));
        when(problemRepository.findByLeetcodeId(1)).thenReturn(Optional.of(testProblem));
        when(sessionRepository.save(any(ProblemSession.class))).thenAnswer(invocation -> {
            ProblemSession ps = invocation.getArgument(0);
            ps.setId(100L);
            ps.setCreatedAt(LocalDateTime.now());
            return ps;
        });

        ProblemSessionResponseDTO response = sessionService.ingestSession(request);

        assertNotNull(response);
        assertEquals("sess-123", response.getSessionId());
        assertEquals("SAVED", response.getStatus());
        assertEquals(10L, response.getProblemId());
        assertEquals(1, response.getLeetcodeId());
        assertTrue(response.getSolved());
        assertEquals(1, response.getAttempts());
    }

    @Test
    @DisplayName("2. Session with multiple events and chronological ordering")
    void testSessionWithMultipleEvents() {
        List<SessionEventDTO> events = List.of(
                SessionEventDTO.builder().type(SessionEventType.CODING_STARTED).timestamp(1725040120000L).build(),
                SessionEventDTO.builder().type(SessionEventType.TAB_HIDDEN).timestamp(1725040200000L).build(),
                SessionEventDTO.builder().type(SessionEventType.TAB_VISIBLE).timestamp(1725040260000L).build(),
                SessionEventDTO.builder().type(SessionEventType.SUBMISSION).timestamp(1725040500000L).result("ACCEPTED").submissionId("2122866612").build(),
                SessionEventDTO.builder().type(SessionEventType.SOLVED).timestamp(1725040500000L).result("ACCEPTED").build()
        );

        ProblemSessionRequestDTO request = ProblemSessionRequestDTO.builder()
                .sessionId("sess-multi-events")
                .problem(ProblemMetadataDTO.builder().leetcodeId(1).build())
                .sessionStartedAt(1725040000000L)
                .solved(true)
                .attempts(1)
                .events(events)
                .build();

        when(sessionRepository.existsBySessionId("sess-multi-events")).thenReturn(false);
        when(userRepository.findByEmail("garnet@example.com")).thenReturn(Optional.of(testUser));
        when(problemRepository.findByLeetcodeId(1)).thenReturn(Optional.of(testProblem));
        when(sessionRepository.save(any(ProblemSession.class))).thenAnswer(invocation -> {
            ProblemSession ps = invocation.getArgument(0);
            ps.setId(101L);
            return ps;
        });

        ProblemSessionResponseDTO response = sessionService.ingestSession(request);

        assertNotNull(response);
        assertEquals(5, response.getEventCount());

        ArgumentCaptor<ProblemSession> sessionCaptor = ArgumentCaptor.forClass(ProblemSession.class);
        verify(sessionRepository).save(sessionCaptor.capture());
        ProblemSession captured = sessionCaptor.getValue();

        assertEquals(5, captured.getEvents().size());
        assertEquals(SessionEventType.CODING_STARTED, captured.getEvents().get(0).getEventType());
        assertEquals(SessionEventType.SOLVED, captured.getEvents().get(4).getEventType());
    }

    @Test
    @DisplayName("3. Wrong Answer -> Accepted multi-attempt event sequence")
    void testWrongAnswerToAcceptedSequence() {
        List<SessionEventDTO> events = List.of(
                SessionEventDTO.builder().type(SessionEventType.CODING_STARTED).timestamp(1725040120000L).build(),
                SessionEventDTO.builder().type(SessionEventType.SUBMISSION).timestamp(1725040300000L).result("WRONG_ANSWER").submissionId("sub-1").build(),
                SessionEventDTO.builder().type(SessionEventType.SUBMISSION).timestamp(1725040600000L).result("ACCEPTED").submissionId("sub-2").build(),
                SessionEventDTO.builder().type(SessionEventType.SOLVED).timestamp(1725040600000L).result("ACCEPTED").build()
        );

        ProblemSessionRequestDTO request = ProblemSessionRequestDTO.builder()
                .sessionId("sess-multi-attempt")
                .problem(ProblemMetadataDTO.builder().leetcodeId(1).build())
                .sessionStartedAt(1725040000000L)
                .attempts(2)
                .solved(true)
                .events(events)
                .build();

        when(sessionRepository.existsBySessionId("sess-multi-attempt")).thenReturn(false);
        when(userRepository.findByEmail("garnet@example.com")).thenReturn(Optional.of(testUser));
        when(problemRepository.findByLeetcodeId(1)).thenReturn(Optional.of(testProblem));
        when(sessionRepository.save(any(ProblemSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProblemSessionResponseDTO response = sessionService.ingestSession(request);

        assertEquals(2, response.getAttempts());
        assertEquals(4, response.getEventCount());
    }

    @Test
    @DisplayName("4. Hint event containing specific hintName")
    void testHintEventWithHintName() {
        List<SessionEventDTO> events = List.of(
                SessionEventDTO.builder()
                        .type(SessionEventType.HINT_OPENED)
                        .timestamp(1725040200000L)
                        .hintName("Hint 1")
                        .build()
        );

        ProblemSessionRequestDTO request = ProblemSessionRequestDTO.builder()
                .sessionId("sess-hint")
                .problem(ProblemMetadataDTO.builder().leetcodeId(1).build())
                .sessionStartedAt(1725040000000L)
                .hintOpened(true)
                .hintOpenCount(1)
                .solved(false)
                .events(events)
                .build();

        when(sessionRepository.existsBySessionId("sess-hint")).thenReturn(false);
        when(userRepository.findByEmail("garnet@example.com")).thenReturn(Optional.of(testUser));
        when(problemRepository.findByLeetcodeId(1)).thenReturn(Optional.of(testProblem));
        when(sessionRepository.save(any(ProblemSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        sessionService.ingestSession(request);

        ArgumentCaptor<ProblemSession> captor = ArgumentCaptor.forClass(ProblemSession.class);
        verify(sessionRepository).save(captor.capture());
        SessionEvent event = captor.getValue().getEvents().get(0);

        assertEquals(SessionEventType.HINT_OPENED, event.getEventType());
        assertEquals("Hint 1", event.getHintName());
    }

    @Test
    @DisplayName("5. Submission event containing unique submissionId")
    void testSubmissionEventWithSubmissionId() {
        List<SessionEventDTO> events = List.of(
                SessionEventDTO.builder()
                        .type(SessionEventType.SUBMISSION)
                        .timestamp(1725040400000L)
                        .result("ACCEPTED")
                        .submissionId("2122865699")
                        .build()
        );

        ProblemSessionRequestDTO request = ProblemSessionRequestDTO.builder()
                .sessionId("sess-sub-id")
                .problem(ProblemMetadataDTO.builder().leetcodeId(1).build())
                .sessionStartedAt(1725040000000L)
                .solved(true)
                .events(events)
                .build();

        when(sessionRepository.existsBySessionId("sess-sub-id")).thenReturn(false);
        when(userRepository.findByEmail("garnet@example.com")).thenReturn(Optional.of(testUser));
        when(problemRepository.findByLeetcodeId(1)).thenReturn(Optional.of(testProblem));
        when(sessionRepository.save(any(ProblemSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        sessionService.ingestSession(request);

        ArgumentCaptor<ProblemSession> captor = ArgumentCaptor.forClass(ProblemSession.class);
        verify(sessionRepository).save(captor.capture());
        SessionEvent event = captor.getValue().getEvents().get(0);

        assertEquals(SessionEventType.SUBMISSION, event.getEventType());
        assertEquals("ACCEPTED", event.getResult());
        assertEquals("2122865699", event.getSubmissionId());
    }

    @Test
    @DisplayName("6. Duplicate sessionId throws DuplicateResourceException")
    void testDuplicateSessionIdThrowsException() {
        ProblemSessionRequestDTO request = ProblemSessionRequestDTO.builder()
                .sessionId("duplicate-sess")
                .problem(ProblemMetadataDTO.builder().leetcodeId(1).build())
                .sessionStartedAt(1725040000000L)
                .solved(true)
                .build();

        when(sessionRepository.existsBySessionId("duplicate-sess")).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> sessionService.ingestSession(request));
        verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("7. Missing authenticated user throws ResourceNotFoundException")
    void testMissingUserThrowsException() {
        ProblemSessionRequestDTO request = ProblemSessionRequestDTO.builder()
                .sessionId("sess-missing-user")
                .problem(ProblemMetadataDTO.builder().leetcodeId(1).build())
                .sessionStartedAt(1725040000000L)
                .solved(true)
                .build();

        when(sessionRepository.existsBySessionId("sess-missing-user")).thenReturn(false);
        when(userRepository.findByEmail("garnet@example.com")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> sessionService.ingestSession(request));
        verify(sessionRepository, never()).save(any());
    }

    @Test
    @DisplayName("8. Problem auto-creation when problem does not exist in DB")
    void testProblemAutoCreation() {
        ProblemSessionRequestDTO request = ProblemSessionRequestDTO.builder()
                .sessionId("sess-auto-create-problem")
                .problem(ProblemMetadataDTO.builder()
                        .leetcodeId(999)
                        .title("New LeetCode Problem")
                        .difficulty("HARD")
                        .url("https://leetcode.com/problems/new-problem/")
                        .build())
                .sessionStartedAt(1725040000000L)
                .solved(true)
                .build();

        when(sessionRepository.existsBySessionId("sess-auto-create-problem")).thenReturn(false);
        when(userRepository.findByEmail("garnet@example.com")).thenReturn(Optional.of(testUser));
        when(problemRepository.findByLeetcodeId(999)).thenReturn(Optional.empty());
        when(problemRepository.save(any(Problem.class))).thenAnswer(invocation -> {
            Problem p = invocation.getArgument(0);
            p.setId(99L);
            return p;
        });
        when(sessionRepository.save(any(ProblemSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProblemSessionResponseDTO response = sessionService.ingestSession(request);

        assertNotNull(response);
        assertEquals(99L, response.getProblemId());
        assertEquals(999, response.getLeetcodeId());
        verify(problemRepository).save(any(Problem.class));
    }

    @Test
    @DisplayName("9. User A requests GET /sessions - returns only User A's sessions")
    void testGetUserSessionsUserIsolation() {
        ProblemSession sessionA = ProblemSession.builder()
                .id(1L)
                .sessionId("sess-user-a")
                .user(testUser)
                .problem(testProblem)
                .sessionStartedAt(LocalDateTime.now())
                .attempts(1)
                .solved(true)
                .events(new ArrayList<>())
                .build();

        when(userRepository.findByEmail("garnet@example.com")).thenReturn(Optional.of(testUser));
        when(sessionRepository.findByUserIdOrderBySessionStartedAtDesc(1L)).thenReturn(List.of(sessionA));

        List<ProblemSessionDetailsDTO> result = sessionService.getUserSessions();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("sess-user-a", result.get(0).getSessionId());
        assertEquals(1, result.get(0).getProblem().getLeetcodeId());
        verify(sessionRepository).findByUserIdOrderBySessionStartedAtDesc(1L);
    }

    @Test
    @DisplayName("10. User A requests specific session by ID - returns full details")
    void testGetSessionByIdSuccess() {
        ProblemSession sessionA = ProblemSession.builder()
                .id(1L)
                .sessionId("sess-user-a")
                .user(testUser)
                .problem(testProblem)
                .sessionStartedAt(LocalDateTime.now())
                .attempts(1)
                .solved(true)
                .events(new ArrayList<>())
                .build();

        when(userRepository.findByEmail("garnet@example.com")).thenReturn(Optional.of(testUser));
        when(sessionRepository.findBySessionIdAndUserId("sess-user-a", 1L)).thenReturn(Optional.of(sessionA));

        ProblemSessionDetailsDTO result = sessionService.getSessionById("sess-user-a");

        assertNotNull(result);
        assertEquals("sess-user-a", result.getSessionId());
    }

    @Test
    @DisplayName("11. User A requests User B's sessionId - throws ResourceNotFoundException (404)")
    void testGetSessionByIdBelongingToAnotherUserThrowsNotFound() {
        when(userRepository.findByEmail("garnet@example.com")).thenReturn(Optional.of(testUser));
        when(sessionRepository.findBySessionIdAndUserId("sess-user-b", 1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> sessionService.getSessionById("sess-user-b"));
    }
}
