package com.example.dsatracker.service;

import com.example.dsatracker.dto.ConfidenceHistoryDTO;
import com.example.dsatracker.dto.ConfidenceResponseDTO;
import com.example.dsatracker.exception.ResourceNotFoundException;
import com.example.dsatracker.model.*;
import com.example.dsatracker.repository.ConfidenceStateRepository;
import com.example.dsatracker.repository.ProblemSessionRepository;
import com.example.dsatracker.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConfidenceServiceTest {

    @Mock
    private ConfidenceStateRepository confidenceStateRepository;

    @Mock
    private ProblemSessionRepository sessionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    private SessionAnalyticsService sessionAnalyticsService;
    private ConfidenceService confidenceService;

    private User userA;
    private User userB;
    private Problem twoSum;

    @BeforeEach
    void setUp() {
        sessionAnalyticsService = new SessionAnalyticsService(sessionRepository, userRepository);
        confidenceService = new ConfidenceService(
                confidenceStateRepository,
                sessionRepository,
                userRepository,
                sessionAnalyticsService
        );

        userA = User.builder()
                .id(1L)
                .email("usera@example.com")
                .name("User A")
                .build();

        userB = User.builder()
                .id(2L)
                .email("userb@example.com")
                .name("User B")
                .build();

        twoSum = Problem.builder()
                .id(10L)
                .leetcodeId(1)
                .title("Two Sum")
                .difficulty(Difficulty.EASY)
                .build();

        // Default repo behavior for saving state: return the state passed in
        lenient().when(confidenceStateRepository.save(any(ConfidenceState.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void mockSecurityContext(User user) {
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        lenient().when(authentication.getName()).thenReturn(user.getEmail());
        SecurityContextHolder.setContext(securityContext);
        lenient().when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    }

    private ProblemSession createSession(
            String sessionId,
            User user,
            Problem problem,
            LocalDateTime startTime,
            boolean solved,
            long thinkingDuration,
            long codingDuration,
            long timeAway,
            int tabSwitches,
            boolean hintOpened,
            int hintCount,
            boolean solutionViewed,
            boolean editorialViewed,
            int attempts,
            List<SessionEvent> events
    ) {
        ProblemSession session = ProblemSession.builder()
                .sessionId(sessionId)
                .user(user)
                .problem(problem)
                .sessionStartedAt(startTime)
                .solved(solved)
                .thinkingDuration(thinkingDuration)
                .codingDuration(codingDuration)
                .totalTimeAway(timeAway)
                .tabSwitchCount(tabSwitches)
                .hintOpened(hintOpened)
                .hintOpenCount(hintCount)
                .solutionViewed(solutionViewed)
                .editorialViewed(editorialViewed)
                .attempts(attempts)
                .events(events != null ? new ArrayList<>(events) : new ArrayList<>())
                .build();

        if (events != null) {
            for (SessionEvent e : events) {
                e.setSession(session);
            }
        }
        return session;
    }

    private List<SessionEvent> createEvents(int wrongAttempts, boolean accepted) {
        List<SessionEvent> events = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        events.add(SessionEvent.builder().eventType(SessionEventType.CODING_STARTED).timestamp(now).build());
        for (int i = 0; i < wrongAttempts; i++) {
            events.add(SessionEvent.builder().eventType(SessionEventType.SUBMISSION)
                    .timestamp(now.plusMinutes(i + 1))
                    .result("WRONG_ANSWER").build());
        }
        if (accepted) {
            events.add(SessionEvent.builder().eventType(SessionEventType.SUBMISSION)
                    .timestamp(now.plusMinutes(wrongAttempts + 1))
                    .result("ACCEPTED").build());
        }
        return events;
    }

    // 1. First independent accepted solve
    @Test
    @DisplayName("1. First independent accepted solve -> Strong positive confidence (~80-85)")
    void testFirstIndependentAcceptedSolve() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 12, 10, 0);
        ProblemSession session = createSession(
                "s1", userA, twoSum, now, true,
                120000L, 300000L, 0L, 0,
                false, 0, false, false, 1,
                createEvents(0, true)
        );

        when(confidenceStateRepository.findByUserIdAndProblemId(userA.getId(), twoSum.getId()))
                .thenReturn(Optional.empty());

        ConfidenceResponseDTO response = confidenceService.updateConfidenceForSession(session);

        assertNotNull(response);
        assertTrue(response.getCurrentConfidence() >= 80.0 && response.getCurrentConfidence() <= 86.0,
                "Expected confidence in ~80-85 range, got: " + response.getCurrentConfidence());
        assertEquals(100.0, response.getMasteryScore());
        assertEquals(100.0, response.getIndependenceScore());
        assertEquals(1, response.getSuccessfulSolveCount());
        assertEquals(1, response.getIndependentSolveCount());
        assertEquals("V1", response.getAlgorithmVersion());
        assertEquals(1, response.getHistory().size());
        assertEquals("NO_ASSISTANCE", response.getHistory().get(0).getAssistanceEffect());
    }

    // 2. First solve with hint
    @Test
    @DisplayName("2. First solve with hint -> Noticeably weaker evidence (~50-55)")
    void testFirstSolveWithHint() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 12, 10, 0);
        List<SessionEvent> events = createEvents(0, true);
        events.add(SessionEvent.builder().eventType(SessionEventType.HINT_OPENED).timestamp(now).hintName("Hint 1").build());

        ProblemSession session = createSession(
                "s-hint", userA, twoSum, now, true,
                120000L, 300000L, 0L, 0,
                true, 1, false, false, 1,
                events
        );

        when(confidenceStateRepository.findByUserIdAndProblemId(userA.getId(), twoSum.getId()))
                .thenReturn(Optional.empty());

        ConfidenceResponseDTO response = confidenceService.updateConfidenceForSession(session);

        assertTrue(response.getCurrentConfidence() >= 48.0 && response.getCurrentConfidence() <= 55.0,
                "Expected confidence in ~50-55 range, got: " + response.getCurrentConfidence());
        assertEquals(100.0, response.getMasteryScore());
        assertEquals(70.0, response.getIndependenceScore());
        assertEquals(0, response.getIndependentSolveCount());
        assertEquals("HINT_USED", response.getHistory().get(0).getAssistanceEffect());
    }

    // 3. First solve with editorial
    @Test
    @DisplayName("3. First solve with editorial -> Substantially weaker evidence (~20-25)")
    void testFirstSolveWithEditorial() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 12, 10, 0);
        List<SessionEvent> events = createEvents(0, true);
        events.add(SessionEvent.builder().eventType(SessionEventType.EDITORIAL_VIEWED).timestamp(now).build());

        ProblemSession session = createSession(
                "s-edit", userA, twoSum, now, true,
                120000L, 300000L, 0L, 0,
                false, 0, false, true, 1,
                events
        );

        when(confidenceStateRepository.findByUserIdAndProblemId(userA.getId(), twoSum.getId()))
                .thenReturn(Optional.empty());

        ConfidenceResponseDTO response = confidenceService.updateConfidenceForSession(session);

        assertTrue(response.getCurrentConfidence() >= 18.0 && response.getCurrentConfidence() <= 25.0,
                "Expected confidence in ~20-25 range, got: " + response.getCurrentConfidence());
        assertEquals(35.0, response.getIndependenceScore());
        assertEquals(0, response.getIndependentSolveCount());
        assertEquals("EDITORIAL_VIEWED", response.getHistory().get(0).getAssistanceEffect());
    }

    // 4. First solve with solution
    @Test
    @DisplayName("4. First solve with solution -> Very weak independent-mastery evidence (~5-12)")
    void testFirstSolveWithSolution() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 12, 10, 0);
        List<SessionEvent> events = createEvents(0, true);
        events.add(SessionEvent.builder().eventType(SessionEventType.SOLUTION_VIEWED).timestamp(now).build());

        ProblemSession session = createSession(
                "s-sol", userA, twoSum, now, true,
                120000L, 300000L, 0L, 0,
                false, 0, true, false, 1,
                events
        );

        when(confidenceStateRepository.findByUserIdAndProblemId(userA.getId(), twoSum.getId()))
                .thenReturn(Optional.empty());

        ConfidenceResponseDTO response = confidenceService.updateConfidenceForSession(session);

        assertTrue(response.getCurrentConfidence() >= 5.0 && response.getCurrentConfidence() <= 12.0,
                "Expected confidence in ~5-12 range, got: " + response.getCurrentConfidence());
        assertEquals(15.0, response.getIndependenceScore());
        assertEquals(0, response.getIndependentSolveCount());
        assertEquals("SOLUTION_VIEWED", response.getHistory().get(0).getAssistanceEffect());
    }

    // 5. Multiple wrong attempts then accepted
    @Test
    @DisplayName("5. Multiple wrong attempts then accepted -> Positive mastery evidence, lower than clean success (~60-70)")
    void testMultipleWrongAttemptsThenAccepted() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 12, 10, 0);
        ProblemSession session = createSession(
                "s-wrong", userA, twoSum, now, true,
                120000L, 300000L, 0L, 0,
                false, 0, false, false, 4,
                createEvents(3, true)
        );

        when(confidenceStateRepository.findByUserIdAndProblemId(userA.getId(), twoSum.getId()))
                .thenReturn(Optional.empty());

        ConfidenceResponseDTO response = confidenceService.updateConfidenceForSession(session);

        assertTrue(response.getCurrentConfidence() >= 60.0 && response.getCurrentConfidence() <= 70.0,
                "Expected confidence in ~60-70 range, got: " + response.getCurrentConfidence());
        assertEquals(55.0, response.getMasteryScore());
        assertEquals(100.0, response.getIndependenceScore());
    }

    // 6. Repeated successful solve
    @Test
    @DisplayName("6. Repeated successful solve shortly after -> Increments confidence (~88-92)")
    void testRepeatedSuccessfulSolve() {
        LocalDateTime t1 = LocalDateTime.of(2026, 9, 12, 10, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 9, 12, 12, 0);

        ConfidenceState existingState = ConfidenceState.builder()
                .user(userA)
                .problem(twoSum)
                .masteryScore(100.0)
                .independenceScore(100.0)
                .retentionStrength(40.0)
                .currentConfidence(83.7)
                .lastSuccessfulSolveAt(t1)
                .successfulSolveCount(1)
                .independentSolveCount(1)
                .lastConfidenceUpdateAt(t1)
                .algorithmVersion("V1")
                .build();

        ProblemSession s2 = createSession(
                "s2", userA, twoSum, t2, true,
                100000L, 300000L, 0L, 0,
                false, 0, false, false, 1,
                createEvents(0, true)
        );

        when(confidenceStateRepository.findByUserIdAndProblemId(userA.getId(), twoSum.getId()))
                .thenReturn(Optional.of(existingState));

        ConfidenceResponseDTO response = confidenceService.updateConfidenceForSession(s2);

        assertTrue(response.getCurrentConfidence() > 83.7, "Repeated solve should increase confidence");
        assertTrue(response.getCurrentConfidence() >= 88.0 && response.getCurrentConfidence() <= 93.0);
        assertEquals(2, response.getSuccessfulSolveCount());
        assertEquals(2, response.getIndependentSolveCount());
    }

    // 7. Repeated solve after long interval
    @Test
    @DisplayName("7. Repeated solve after long interval -> Strong retention evidence (retention contribution ~85)")
    void testRepeatedSolveAfterLongInterval() {
        LocalDateTime t1 = LocalDateTime.of(2026, 8, 1, 10, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 8, 20, 10, 0); // 19 days later

        ConfidenceState existingState = ConfidenceState.builder()
                .user(userA)
                .problem(twoSum)
                .masteryScore(100.0)
                .independenceScore(100.0)
                .retentionStrength(40.0)
                .currentConfidence(80.0)
                .lastSuccessfulSolveAt(t1)
                .successfulSolveCount(1)
                .independentSolveCount(1)
                .lastConfidenceUpdateAt(t1)
                .algorithmVersion("V1")
                .build();

        ProblemSession s2 = createSession(
                "s2-long", userA, twoSum, t2, true,
                100000L, 300000L, 0L, 0,
                false, 0, false, false, 1,
                createEvents(0, true)
        );

        when(confidenceStateRepository.findByUserIdAndProblemId(userA.getId(), twoSum.getId()))
                .thenReturn(Optional.of(existingState));

        ConfidenceResponseDTO response = confidenceService.updateConfidenceForSession(s2);

        assertEquals(85.0, response.getHistory().get(0).getRetentionContribution());
        assertTrue(response.getRetentionStrength() > 40.0);
    }

    // 8. Faster second solve
    @Test
    @DisplayName("8. Faster second solve -> Positive efficiency modifier (+5)")
    void testFasterSecondSolve() {
        LocalDateTime t1 = LocalDateTime.of(2026, 9, 10, 10, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 9, 12, 10, 0);

        ProblemSession s1 = createSession(
                "s1", userA, twoSum, t1, true,
                300000L, 600000L, 0L, 0, // 900s total
                false, 0, false, false, 1,
                createEvents(0, true)
        );

        ProblemSession s2 = createSession(
                "s2-fast", userA, twoSum, t2, true,
                100000L, 200000L, 0L, 0, // 300s total (ratio 300/900 = 0.33 < 0.75)
                false, 0, false, false, 1,
                createEvents(0, true)
        );

        ConfidenceState existingState = ConfidenceState.builder()
                .user(userA)
                .problem(twoSum)
                .masteryScore(100.0)
                .independenceScore(100.0)
                .retentionStrength(40.0)
                .currentConfidence(80.0)
                .lastSuccessfulSolveAt(t1)
                .successfulSolveCount(1)
                .independentSolveCount(1)
                .lastConfidenceUpdateAt(t1)
                .build();

        when(confidenceStateRepository.findByUserIdAndProblemId(userA.getId(), twoSum.getId()))
                .thenReturn(Optional.of(existingState));
        when(sessionRepository.findByUserIdAndProblemId(userA.getId(), twoSum.getId()))
                .thenReturn(List.of(s1));

        ConfidenceResponseDTO response = confidenceService.updateConfidenceForSession(s2);

        assertTrue(response.getCurrentConfidence() > 88.0);
    }

    // 9. Slower second solve
    @Test
    @DisplayName("9. Slower second solve -> Bounded negative efficiency modifier (-5)")
    void testSlowerSecondSolve() {
        LocalDateTime t1 = LocalDateTime.of(2026, 9, 10, 10, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 9, 12, 10, 0);

        ProblemSession s1 = createSession(
                "s1", userA, twoSum, t1, true,
                100000L, 200000L, 0L, 0, // 300s total
                false, 0, false, false, 1,
                createEvents(0, true)
        );

        ProblemSession s2 = createSession(
                "s2-slow", userA, twoSum, t2, true,
                300000L, 600000L, 0L, 0, // 900s total (ratio 900/300 = 3.0 > 1.35)
                false, 0, false, false, 1,
                createEvents(0, true)
        );

        ConfidenceState existingState = ConfidenceState.builder()
                .user(userA)
                .problem(twoSum)
                .masteryScore(100.0)
                .independenceScore(100.0)
                .retentionStrength(40.0)
                .currentConfidence(80.0)
                .lastSuccessfulSolveAt(t1)
                .successfulSolveCount(1)
                .independentSolveCount(1)
                .lastConfidenceUpdateAt(t1)
                .build();

        when(confidenceStateRepository.findByUserIdAndProblemId(userA.getId(), twoSum.getId()))
                .thenReturn(Optional.of(existingState));
        when(sessionRepository.findByUserIdAndProblemId(userA.getId(), twoSum.getId()))
                .thenReturn(List.of(s1));

        ConfidenceResponseDTO response = confidenceService.updateConfidenceForSession(s2);

        assertTrue(response.getCurrentConfidence() < 90.0);
    }

    // 10. Large away-time ratio
    @Test
    @DisplayName("10. Large away-time ratio -> Dampens reliability weight, does not destroy confidence")
    void testLargeAwayTimeRatio() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 12, 10, 0);
        // Active = 100s, away = 400s -> awayRatio = 0.80 (> 0.50)
        ProblemSession session = createSession(
                "s-away", userA, twoSum, now, true,
                40000L, 60000L, 400000L, 3,
                false, 0, false, false, 1,
                createEvents(0, true)
        );

        when(confidenceStateRepository.findByUserIdAndProblemId(userA.getId(), twoSum.getId()))
                .thenReturn(Optional.empty());

        ConfidenceResponseDTO response = confidenceService.updateConfidenceForSession(session);

        // Weight should be clamped to 0.40, confidence is ~33-35 instead of 83.7
        assertTrue(response.getCurrentConfidence() >= 30.0 && response.getCurrentConfidence() <= 40.0,
                "Confidence should be dampened due to large away ratio, got: " + response.getCurrentConfidence());
        assertEquals(0.8, response.getHistory().get(0).getAwayTimeEffect());
    }

    // 11. Small away-time ratio
    @Test
    @DisplayName("11. Small away-time ratio -> Full weight (1.0), normal strong confidence")
    void testSmallAwayTimeRatio() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 12, 10, 0);
        // Active = 200s, away = 10s -> awayRatio = 10/210 = 0.047 (<= 0.15)
        ProblemSession session = createSession(
                "s-small-away", userA, twoSum, now, true,
                80000L, 120000L, 10000L, 1,
                false, 0, false, false, 1,
                createEvents(0, true)
        );

        when(confidenceStateRepository.findByUserIdAndProblemId(userA.getId(), twoSum.getId()))
                .thenReturn(Optional.empty());

        ConfidenceResponseDTO response = confidenceService.updateConfidenceForSession(session);

        assertTrue(response.getCurrentConfidence() >= 80.0 && response.getCurrentConfidence() <= 86.0);
    }

    // 12. Large tab-switch count but low away duration
    @Test
    @DisplayName("12. Large tab-switch count with low away duration -> Not penalized by switch count")
    void testLargeTabSwitchesLowAwayDuration() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 12, 10, 0);
        // 25 tab switches, but total away time is only 5s out of 300s active
        ProblemSession session = createSession(
                "s-switches", userA, twoSum, now, true,
                100000L, 200000L, 5000L, 25,
                false, 0, false, false, 1,
                createEvents(0, true)
        );

        when(confidenceStateRepository.findByUserIdAndProblemId(userA.getId(), twoSum.getId()))
                .thenReturn(Optional.empty());

        ConfidenceResponseDTO response = confidenceService.updateConfidenceForSession(session);

        assertTrue(response.getCurrentConfidence() >= 80.0 && response.getCurrentConfidence() <= 86.0,
                "Tab switches alone must not penalize confidence: " + response.getCurrentConfidence());
    }

    // 13. Earlier solution-assisted solve followed by independent solve
    @Test
    @DisplayName("13. Solution-assisted solve followed by independent solve -> Confidence recovers strongly")
    void testSolutionFollowedByIndependentSolve() {
        LocalDateTime t1 = LocalDateTime.of(2026, 9, 10, 10, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 9, 12, 10, 0);

        // First solve was with solution: confidence is low (~7.4)
        ConfidenceState existingState = ConfidenceState.builder()
                .user(userA)
                .problem(twoSum)
                .masteryScore(100.0)
                .independenceScore(15.0)
                .retentionStrength(15.0)
                .currentConfidence(7.4)
                .lastSuccessfulSolveAt(t1)
                .successfulSolveCount(1)
                .independentSolveCount(0)
                .lastConfidenceUpdateAt(t1)
                .algorithmVersion("V1")
                .build();

        ProblemSession s2 = createSession(
                "s2-indep", userA, twoSum, t2, true,
                120000L, 300000L, 0L, 0,
                false, 0, false, false, 1,
                createEvents(0, true)
        );

        when(confidenceStateRepository.findByUserIdAndProblemId(userA.getId(), twoSum.getId()))
                .thenReturn(Optional.of(existingState));

        ConfidenceResponseDTO response = confidenceService.updateConfidenceForSession(s2);

        assertTrue(response.getCurrentConfidence() >= 55.0 && response.getCurrentConfidence() <= 65.0,
                "Confidence should recover strongly (expected ~55-65), got: " + response.getCurrentConfidence());
        assertEquals(1, response.getIndependentSolveCount());
        assertEquals(2, response.getSuccessfulSolveCount());
    }

    // 14. Confidence never goes below 0
    @Test
    @DisplayName("14. Confidence never goes below 0")
    void testConfidenceNeverGoesBelowZero() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 12, 10, 0);
        ConfidenceState existingState = ConfidenceState.builder()
                .user(userA)
                .problem(twoSum)
                .masteryScore(20.0)
                .independenceScore(0.0)
                .retentionStrength(0.0)
                .currentConfidence(2.0)
                .lastConfidenceUpdateAt(now.minusDays(1))
                .algorithmVersion("V1")
                .build();

        // Unsolved session reduces confidence by 5.0
        ProblemSession unsolved = createSession(
                "s-unsolved", userA, twoSum, now, false,
                60000L, 60000L, 0L, 0,
                false, 0, false, false, 1,
                createEvents(1, false)
        );

        when(confidenceStateRepository.findByUserIdAndProblemId(userA.getId(), twoSum.getId()))
                .thenReturn(Optional.of(existingState));

        ConfidenceResponseDTO response = confidenceService.updateConfidenceForSession(unsolved);

        assertEquals(0.0, response.getCurrentConfidence(), "Confidence must clamp to at least 0.0");
    }

    // 15. Confidence never exceeds 100
    @Test
    @DisplayName("15. Confidence never exceeds 100")
    void testConfidenceNeverExceeds100() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 12, 10, 0);
        ConfidenceState existingState = ConfidenceState.builder()
                .user(userA)
                .problem(twoSum)
                .masteryScore(100.0)
                .independenceScore(100.0)
                .retentionStrength(100.0)
                .currentConfidence(99.0)
                .lastSuccessfulSolveAt(now.minusDays(10))
                .successfulSolveCount(10)
                .independentSolveCount(10)
                .lastConfidenceUpdateAt(now.minusDays(10))
                .algorithmVersion("V1")
                .build();

        ProblemSession perfectSolve = createSession(
                "s-perfect", userA, twoSum, now, true,
                60000L, 120000L, 0L, 0,
                false, 0, false, false, 1,
                createEvents(0, true)
        );

        when(confidenceStateRepository.findByUserIdAndProblemId(userA.getId(), twoSum.getId()))
                .thenReturn(Optional.of(existingState));

        ConfidenceResponseDTO response = confidenceService.updateConfidenceForSession(perfectSolve);

        assertTrue(response.getCurrentConfidence() <= 100.0, "Confidence must not exceed 100.0");
    }

    // 16. Confidence state uniqueness per user/problem
    @Test
    @DisplayName("16. Confidence state uniqueness: updates existing state rather than duplicating")
    void testConfidenceStateUniqueness() {
        LocalDateTime t1 = LocalDateTime.of(2026, 9, 12, 10, 0);
        ConfidenceState existingState = ConfidenceState.builder()
                .id(999L)
                .user(userA)
                .problem(twoSum)
                .masteryScore(80.0)
                .independenceScore(80.0)
                .retentionStrength(30.0)
                .currentConfidence(70.0)
                .successfulSolveCount(1)
                .independentSolveCount(1)
                .lastConfidenceUpdateAt(t1)
                .algorithmVersion("V1")
                .build();

        when(confidenceStateRepository.findByUserIdAndProblemId(userA.getId(), twoSum.getId()))
                .thenReturn(Optional.of(existingState));

        ProblemSession s2 = createSession(
                "s2", userA, twoSum, t1.plusHours(1), true,
                120000L, 240000L, 0L, 0,
                false, 0, false, false, 1,
                createEvents(0, true)
        );

        ConfidenceResponseDTO response = confidenceService.updateConfidenceForSession(s2);

        verify(confidenceStateRepository, times(1)).save(existingState);
        assertEquals(2, existingState.getSuccessfulSolveCount());
    }

    // 17. User isolation
    @Test
    @DisplayName("17. User isolation: User A cannot access User B's confidence state")
    void testUserIsolation() {
        mockSecurityContext(userA);

        when(confidenceStateRepository.findWithHistoryByUserIdAndProblemId(userA.getId(), twoSum.getId()))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                confidenceService.getConfidenceForProblem(twoSum.getId()));
    }

    // 18. No historical sessions
    @Test
    @DisplayName("18. No historical sessions -> Handles gracefully without NPE")
    void testNoHistoricalSessions() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 12, 10, 0);
        ProblemSession session = createSession(
                "s1", userA, twoSum, now, true,
                120000L, 300000L, 0L, 0,
                false, 0, false, false, 1,
                createEvents(0, true)
        );

        when(confidenceStateRepository.findByUserIdAndProblemId(userA.getId(), twoSum.getId()))
                .thenReturn(Optional.empty());
        when(sessionRepository.findByUserIdAndProblemId(userA.getId(), twoSum.getId()))
                .thenReturn(Collections.emptyList());

        assertDoesNotThrow(() -> confidenceService.updateConfidenceForSession(session));
    }

    // 19. Incomplete/unsolved session
    @Test
    @DisplayName("19. Incomplete/unsolved session -> Bounded dampening, does not wipe out confidence")
    void testIncompleteUnsolvedSession() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 12, 10, 0);
        ConfidenceState existingState = ConfidenceState.builder()
                .user(userA)
                .problem(twoSum)
                .masteryScore(100.0)
                .independenceScore(100.0)
                .retentionStrength(40.0)
                .currentConfidence(80.0)
                .successfulSolveCount(1)
                .independentSolveCount(1)
                .lastConfidenceUpdateAt(now.minusDays(1))
                .algorithmVersion("V1")
                .build();

        ProblemSession unsolved = createSession(
                "s-unsolved", userA, twoSum, now, false,
                300000L, 300000L, 0L, 0,
                false, 0, false, false, 2,
                createEvents(2, false)
        );

        when(confidenceStateRepository.findByUserIdAndProblemId(userA.getId(), twoSum.getId()))
                .thenReturn(Optional.of(existingState));

        ConfidenceResponseDTO response = confidenceService.updateConfidenceForSession(unsolved);

        assertEquals(75.0, response.getCurrentConfidence());
        assertEquals(1, response.getHistory().size());
        assertEquals("UNSOLVED_SESSION", response.getHistory().get(0).getAssistanceEffect());
    }

    // 20. Algorithm version persistence
    @Test
    @DisplayName("20. Algorithm version persistence -> Always 'V1'")
    void testAlgorithmVersionPersistence() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 12, 10, 0);
        ProblemSession session = createSession(
                "s1", userA, twoSum, now, true,
                120000L, 300000L, 0L, 0,
                false, 0, false, false, 1,
                createEvents(0, true)
        );

        when(confidenceStateRepository.findByUserIdAndProblemId(userA.getId(), twoSum.getId()))
                .thenReturn(Optional.empty());

        ConfidenceResponseDTO response = confidenceService.updateConfidenceForSession(session);

        assertEquals("V1", response.getAlgorithmVersion());
        assertEquals("V1", response.getHistory().get(0).getAlgorithmVersion());
    }

    // 21. Confidence history correctness
    @Test
    @DisplayName("21. Confidence history correctness -> Records all contributions and previous/new confidence")
    void testConfidenceHistoryCorrectness() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 12, 10, 0);
        ProblemSession session = createSession(
                "s1", userA, twoSum, now, true,
                100000L, 200000L, 0L, 0,
                false, 0, false, false, 1,
                createEvents(0, true)
        );

        when(confidenceStateRepository.findByUserIdAndProblemId(userA.getId(), twoSum.getId()))
                .thenReturn(Optional.empty());

        ConfidenceResponseDTO response = confidenceService.updateConfidenceForSession(session);

        List<ConfidenceHistoryDTO> history = response.getHistory();
        assertNotNull(history);
        assertEquals(1, history.size());

        ConfidenceHistoryDTO entry = history.get(0);
        assertEquals(0.0, entry.getPreviousConfidence());
        assertEquals(response.getCurrentConfidence(), entry.getNewConfidence());
        assertEquals(100.0, entry.getMasteryContribution());
        assertEquals(100.0, entry.getIndependenceContribution());
        assertEquals(30.0, entry.getRetentionContribution());
        assertEquals("NO_ASSISTANCE", entry.getAssistanceEffect());
        assertEquals(0.0, entry.getAwayTimeEffect());
        assertEquals("V1", entry.getAlgorithmVersion());
    }
}
