package com.example.dsatracker.service;

import com.example.dsatracker.dto.SessionAnalyticsDTO;
import com.example.dsatracker.exception.ResourceNotFoundException;
import com.example.dsatracker.model.*;
import com.example.dsatracker.repository.ProblemSessionRepository;
import com.example.dsatracker.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionAnalyticsServiceTest {

    @Mock
    private ProblemSessionRepository sessionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private SessionAnalyticsService analyticsService;

    private User testUser;
    private Problem testProblem;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .name("Garnet")
                .email("garnet@example.com")
                .password("pass")
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
    @DisplayName("TEST 1: Solved in one attempt (firstAttemptAccepted = true, wrongSubmissions = 0)")
    void testSingleAttemptSuccess() {
        List<SessionEvent> events = List.of(
                SessionEvent.builder().eventType(SessionEventType.CODING_STARTED).timestamp(LocalDateTime.now()).build(),
                SessionEvent.builder().eventType(SessionEventType.SUBMISSION).result("ACCEPTED").submissionId("sub-1").timestamp(LocalDateTime.now()).build(),
                SessionEvent.builder().eventType(SessionEventType.SOLVED).result("ACCEPTED").timestamp(LocalDateTime.now()).build()
        );

        ProblemSession session = ProblemSession.builder()
                .id(1L)
                .sessionId("sess-1")
                .user(testUser)
                .problem(testProblem)
                .language("python3")
                .thinkingDuration(10000L)
                .codingDuration(50000L)
                .totalTimeAway(0L)
                .tabSwitchCount(0)
                .attempts(1)
                .solved(true)
                .events(events)
                .build();

        when(userRepository.findByEmail("garnet@example.com")).thenReturn(Optional.of(testUser));
        when(sessionRepository.findBySessionIdAndUserId("sess-1", 1L)).thenReturn(Optional.of(session));

        SessionAnalyticsDTO analytics = analyticsService.getSessionAnalytics("sess-1");

        assertNotNull(analytics);
        assertEquals("sess-1", analytics.getSessionId());
        assertEquals(60000L, analytics.getTotalActiveTime());
        assertEquals(10000L, analytics.getThinkingTime());
        assertEquals(50000L, analytics.getCodingTime());
        assertEquals(0L, analytics.getTimeAway());
        assertEquals(0, analytics.getTabSwitchCount());
        assertEquals(1, analytics.getAttempts());
        assertEquals(0, analytics.getWrongSubmissionCount());
        assertEquals(1, analytics.getAcceptedSubmissionCount());
        assertTrue(analytics.getFirstAttemptAccepted());
        assertFalse(analytics.getHintUsed());
        assertEquals(0, analytics.getHintCount());
        assertTrue(analytics.getHintsOpened().isEmpty());
        assertFalse(analytics.getSolutionViewed());
        assertFalse(analytics.getEditorialViewed());
        assertTrue(analytics.getSolved());
    }

    @Test
    @DisplayName("TEST 2: Wrong Answer -> Accepted multi-attempt progression")
    void testWrongAnswerToAcceptedAnalytics() {
        List<SessionEvent> events = List.of(
                SessionEvent.builder().eventType(SessionEventType.CODING_STARTED).timestamp(LocalDateTime.now()).build(),
                SessionEvent.builder().eventType(SessionEventType.SUBMISSION).result("WRONG_ANSWER").submissionId("sub-1").timestamp(LocalDateTime.now()).build(),
                SessionEvent.builder().eventType(SessionEventType.SUBMISSION).result("ACCEPTED").submissionId("sub-2").timestamp(LocalDateTime.now()).build(),
                SessionEvent.builder().eventType(SessionEventType.SOLVED).result("ACCEPTED").timestamp(LocalDateTime.now()).build()
        );

        ProblemSession session = ProblemSession.builder()
                .id(2L)
                .sessionId("sess-2")
                .user(testUser)
                .problem(testProblem)
                .language("java")
                .thinkingDuration(15000L)
                .codingDuration(80000L)
                .attempts(2)
                .solved(true)
                .events(events)
                .build();

        when(userRepository.findByEmail("garnet@example.com")).thenReturn(Optional.of(testUser));
        when(sessionRepository.findBySessionIdAndUserId("sess-2", 1L)).thenReturn(Optional.of(session));

        SessionAnalyticsDTO analytics = analyticsService.getSessionAnalytics("sess-2");

        assertEquals(2, analytics.getAttempts());
        assertEquals(1, analytics.getWrongSubmissionCount());
        assertEquals(1, analytics.getAcceptedSubmissionCount());
        assertFalse(analytics.getFirstAttemptAccepted());
        assertTrue(analytics.getSolved());
    }

    @Test
    @DisplayName("TEST 3: Multiple hints opened preserving names and chronological order")
    void testMultipleHintsAnalytics() {
        List<SessionEvent> events = List.of(
                SessionEvent.builder().eventType(SessionEventType.HINT_OPENED).hintName("Hint 1").timestamp(LocalDateTime.now()).build(),
                SessionEvent.builder().eventType(SessionEventType.HINT_OPENED).hintName("Hint 2").timestamp(LocalDateTime.now().plusSeconds(5)).build(),
                SessionEvent.builder().eventType(SessionEventType.HINT_OPENED).hintName("Hint 1").timestamp(LocalDateTime.now().plusSeconds(10)).build()
        );

        ProblemSession session = ProblemSession.builder()
                .id(3L)
                .sessionId("sess-hints")
                .user(testUser)
                .problem(testProblem)
                .hintOpened(true)
                .hintOpenCount(3)
                .events(events)
                .build();

        when(userRepository.findByEmail("garnet@example.com")).thenReturn(Optional.of(testUser));
        when(sessionRepository.findBySessionIdAndUserId("sess-hints", 1L)).thenReturn(Optional.of(session));

        SessionAnalyticsDTO analytics = analyticsService.getSessionAnalytics("sess-hints");

        assertTrue(analytics.getHintUsed());
        assertEquals(3, analytics.getHintCount());
        assertEquals(List.of("Hint 1", "Hint 2", "Hint 1"), analytics.getHintsOpened());
    }

    @Test
    @DisplayName("TEST 4: Solution viewed detection")
    void testSolutionViewedAnalytics() {
        List<SessionEvent> events = List.of(
                SessionEvent.builder().eventType(SessionEventType.SOLUTION_VIEWED).timestamp(LocalDateTime.now()).build()
        );

        ProblemSession session = ProblemSession.builder()
                .id(4L)
                .sessionId("sess-sol")
                .user(testUser)
                .problem(testProblem)
                .solutionViewed(true)
                .events(events)
                .build();

        when(userRepository.findByEmail("garnet@example.com")).thenReturn(Optional.of(testUser));
        when(sessionRepository.findBySessionIdAndUserId("sess-sol", 1L)).thenReturn(Optional.of(session));

        SessionAnalyticsDTO analytics = analyticsService.getSessionAnalytics("sess-sol");

        assertTrue(analytics.getSolutionViewed());
    }

    @Test
    @DisplayName("TEST 5: Editorial viewed detection")
    void testEditorialViewedAnalytics() {
        List<SessionEvent> events = List.of(
                SessionEvent.builder().eventType(SessionEventType.EDITORIAL_VIEWED).timestamp(LocalDateTime.now()).build()
        );

        ProblemSession session = ProblemSession.builder()
                .id(5L)
                .sessionId("sess-ed")
                .user(testUser)
                .problem(testProblem)
                .editorialViewed(true)
                .events(events)
                .build();

        when(userRepository.findByEmail("garnet@example.com")).thenReturn(Optional.of(testUser));
        when(sessionRepository.findBySessionIdAndUserId("sess-ed", 1L)).thenReturn(Optional.of(session));

        SessionAnalyticsDTO analytics = analyticsService.getSessionAnalytics("sess-ed");

        assertTrue(analytics.getEditorialViewed());
    }

    @Test
    @DisplayName("TEST 6: Tab switching and time away metrics")
    void testTabSwitchingAnalytics() {
        ProblemSession session = ProblemSession.builder()
                .id(6L)
                .sessionId("sess-tabs")
                .user(testUser)
                .problem(testProblem)
                .totalTimeAway(45000L)
                .tabSwitchCount(3)
                .events(new ArrayList<>())
                .build();

        when(userRepository.findByEmail("garnet@example.com")).thenReturn(Optional.of(testUser));
        when(sessionRepository.findBySessionIdAndUserId("sess-tabs", 1L)).thenReturn(Optional.of(session));

        SessionAnalyticsDTO analytics = analyticsService.getSessionAnalytics("sess-tabs");

        assertEquals(45000L, analytics.getTimeAway());
        assertEquals(3, analytics.getTabSwitchCount());
    }

    @Test
    @DisplayName("TEST 7: User isolation - User A cannot access User B's analytics")
    void testUserIsolationAnalytics() {
        when(userRepository.findByEmail("garnet@example.com")).thenReturn(Optional.of(testUser));
        when(sessionRepository.findBySessionIdAndUserId("sess-user-b", 1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> analyticsService.getSessionAnalytics("sess-user-b"));
    }

    @Test
    @DisplayName("TEST 8: Missing session returns 404")
    void testMissingSessionAnalytics() {
        when(userRepository.findByEmail("garnet@example.com")).thenReturn(Optional.of(testUser));
        when(sessionRepository.findBySessionIdAndUserId("non-existent-sess", 1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> analyticsService.getSessionAnalytics("non-existent-sess"));
    }

    @Test
    @DisplayName("TEST 9: Session with no assistance contains false/zero, not fabricated data")
    void testNoAssistanceAnalytics() {
        ProblemSession session = ProblemSession.builder()
                .id(9L)
                .sessionId("sess-clean")
                .user(testUser)
                .problem(testProblem)
                .thinkingDuration(5000L)
                .codingDuration(15000L)
                .totalTimeAway(0L)
                .tabSwitchCount(0)
                .hintOpened(false)
                .hintOpenCount(0)
                .solutionViewed(false)
                .editorialViewed(false)
                .attempts(1)
                .solved(true)
                .events(new ArrayList<>())
                .build();

        when(userRepository.findByEmail("garnet@example.com")).thenReturn(Optional.of(testUser));
        when(sessionRepository.findBySessionIdAndUserId("sess-clean", 1L)).thenReturn(Optional.of(session));

        SessionAnalyticsDTO analytics = analyticsService.getSessionAnalytics("sess-clean");

        assertFalse(analytics.getHintUsed());
        assertEquals(0, analytics.getHintCount());
        assertTrue(analytics.getHintsOpened().isEmpty());
        assertFalse(analytics.getSolutionViewed());
        assertFalse(analytics.getEditorialViewed());
        assertEquals(0L, analytics.getTimeAway());
        assertEquals(0, analytics.getTabSwitchCount());
    }

    @Test
    @DisplayName("TEST 10: Event timeline ordering is preserved")
    void testTimelineOrderingAnalytics() {
        LocalDateTime t0 = LocalDateTime.now();
        List<SessionEvent> events = List.of(
                SessionEvent.builder().eventType(SessionEventType.CODING_STARTED).timestamp(t0).build(),
                SessionEvent.builder().eventType(SessionEventType.HINT_OPENED).hintName("Hint 1").timestamp(t0.plusSeconds(10)).build(),
                SessionEvent.builder().eventType(SessionEventType.SUBMISSION).result("WRONG_ANSWER").submissionId("sub-1").timestamp(t0.plusSeconds(20)).build(),
                SessionEvent.builder().eventType(SessionEventType.HINT_OPENED).hintName("Hint 2").timestamp(t0.plusSeconds(30)).build(),
                SessionEvent.builder().eventType(SessionEventType.SUBMISSION).result("ACCEPTED").submissionId("sub-2").timestamp(t0.plusSeconds(40)).build(),
                SessionEvent.builder().eventType(SessionEventType.SOLVED).result("ACCEPTED").timestamp(t0.plusSeconds(40)).build()
        );

        ProblemSession session = ProblemSession.builder()
                .id(10L)
                .sessionId("sess-timeline")
                .user(testUser)
                .problem(testProblem)
                .solved(true)
                .events(events)
                .build();

        SessionAnalyticsDTO analytics = analyticsService.computeAnalytics(session);

        assertFalse(analytics.getFirstAttemptAccepted());
        assertEquals(1, analytics.getWrongSubmissionCount());
        assertEquals(1, analytics.getAcceptedSubmissionCount());
        assertEquals(List.of("Hint 1", "Hint 2"), analytics.getHintsOpened());
        assertEquals(2, analytics.getHintCount());
        assertTrue(analytics.getSolved());
    }
}
