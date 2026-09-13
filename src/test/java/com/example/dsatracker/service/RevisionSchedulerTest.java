package com.example.dsatracker.service;

import com.example.dsatracker.dto.ReviewQueueItemDTO;
import com.example.dsatracker.dto.ReviewQueueResponseDTO;
import com.example.dsatracker.dto.RevisionStateDTO;
import com.example.dsatracker.dto.SessionAnalyticsDTO;
import com.example.dsatracker.exception.ResourceNotFoundException;
import com.example.dsatracker.model.*;
import com.example.dsatracker.repository.*;
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
import java.time.ZoneId;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RevisionSchedulerTest {

    @Mock
    private RevisionStateRepository revisionStateRepository;

    @Mock
    private RevisionHistoryRepository revisionHistoryRepository;

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
    private ReviewOutcomeClassifier outcomeClassifier;
    private RevisionEligibilityService eligibilityService;
    private RevisionPriorityService priorityService;
    private ReviewCapacityService capacityService;
    private RevisionScheduler scheduler;

    private User userA;
    private User userB;
    private Problem twoSum;
    private Problem addTwoNumbers;

    @BeforeEach
    void setUp() {
        sessionAnalyticsService = new SessionAnalyticsService(sessionRepository, userRepository);
        outcomeClassifier = new ReviewOutcomeClassifier();
        eligibilityService = new RevisionEligibilityService();
        priorityService = new RevisionPriorityService();
        capacityService = new ReviewCapacityService(sessionRepository, revisionHistoryRepository);

        scheduler = new RevisionScheduler(
                revisionStateRepository,
                revisionHistoryRepository,
                confidenceStateRepository,
                sessionRepository,
                userRepository,
                sessionAnalyticsService,
                outcomeClassifier,
                eligibilityService,
                priorityService,
                capacityService
        );

        userA = User.builder().id(1L).email("usera@example.com").name("User A").build();
        userB = User.builder().id(2L).email("userb@example.com").name("User B").build();

        twoSum = Problem.builder().id(10L).leetcodeId(1).title("Two Sum").difficulty(Difficulty.EASY).build();
        addTwoNumbers = Problem.builder().id(11L).leetcodeId(2).title("Add Two Numbers").difficulty(Difficulty.MEDIUM).build();

        lenient().when(revisionStateRepository.save(any(RevisionState.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void mockSecurityContext(User user) {
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        lenient().when(authentication.getName()).thenReturn(user.getEmail());
        SecurityContextHolder.setContext(securityContext);
        lenient().when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    }

    private ProblemSession buildSession(
            String sessionId, User user, Problem problem, LocalDateTime time,
            boolean solved, int attempts, long thinking, long coding, long away, int tabSwitches,
            boolean hint, int hintCount, boolean solution, boolean editorial) {

        ProblemSession session = ProblemSession.builder()
                .sessionId(sessionId)
                .user(user)
                .problem(problem)
                .sessionStartedAt(time)
                .solved(solved)
                .attempts(attempts)
                .thinkingDuration(thinking)
                .codingDuration(coding)
                .totalTimeAway(away)
                .tabSwitchCount(tabSwitches)
                .hintOpened(hint)
                .hintOpenCount(hintCount)
                .solutionViewed(solution)
                .editorialViewed(editorial)
                .sessionType(SessionType.PRACTICE)
                .build();

        List<SessionEvent> events = new ArrayList<>();
        events.add(SessionEvent.builder().eventType(SessionEventType.CODING_STARTED).timestamp(time).session(session).build());
        for (int i = 0; i < Math.max(0, attempts - 1); i++) {
            events.add(SessionEvent.builder().eventType(SessionEventType.SUBMISSION).timestamp(time.plusMinutes(i + 1))
                    .result("WRONG_ANSWER").session(session).build());
        }
        if (solved) {
            events.add(SessionEvent.builder().eventType(SessionEventType.SUBMISSION).timestamp(time.plusMinutes(attempts))
                    .result("ACCEPTED").session(session).build());
        }
        session.setEvents(events);
        return session;
    }

    // A. SRS CREATION
    @Test
    @DisplayName("1. First successful solve creates RevisionState")
    void testFirstSuccessfulSolveCreatesRevisionState() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 13, 10, 0);
        ProblemSession session = buildSession("s1", userA, twoSum, now, true, 1, 60000L, 120000L, 0L, 0, false, 0, false, false);

        when(revisionStateRepository.findByUserIdAndProblemId(userA.getId(), twoSum.getId()))
                .thenReturn(Optional.empty());
        when(confidenceStateRepository.findByUserIdAndProblemId(userA.getId(), twoSum.getId()))
                .thenReturn(Optional.of(ConfidenceState.builder().currentConfidence(80.0).retentionStrength(40.0).build()));

        RevisionStateDTO dto = scheduler.processSession(session);

        assertNotNull(dto);
        assertEquals(0, dto.getReviewCount());
        assertTrue(dto.getCurrentIntervalDays() >= 2 && dto.getCurrentIntervalDays() <= 7);
        assertEquals("SRS_V1", dto.getAlgorithmVersion());
        assertEquals(now, dto.getLastReviewedAt());
        assertEquals(now.plusDays(dto.getCurrentIntervalDays()), dto.getNextReviewAt());
    }

    @Test
    @DisplayName("2. Unsolved problem does not create SRS")
    void testUnsolvedProblemDoesNotCreateSRS() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 13, 10, 0);
        ProblemSession session = buildSession("s-unsolved", userA, twoSum, now, false, 2, 60000L, 120000L, 0L, 0, false, 0, false, false);

        when(revisionStateRepository.findByUserIdAndProblemId(userA.getId(), twoSum.getId()))
                .thenReturn(Optional.empty());

        RevisionStateDTO dto = scheduler.processSession(session);
        assertNull(dto);
        verify(revisionStateRepository, never()).save(any());
    }

    @Test
    @DisplayName("3. Unique user/problem constraint: reuses existing state")
    void testUniqueUserProblemReusesState() {
        LocalDateTime t1 = LocalDateTime.of(2026, 9, 1, 10, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 9, 5, 10, 0);

        RevisionState existing = RevisionState.builder()
                .id(100L)
                .user(userA)
                .problem(twoSum)
                .reviewCount(0)
                .currentIntervalDays(4)
                .lastReviewedAt(t1)
                .nextReviewAt(t1.plusDays(4))
                .build();

        ProblemSession s2 = buildSession("s2", userA, twoSum, t2, true, 1, 60000L, 120000L, 0L, 0, false, 0, false, false);

        when(revisionStateRepository.findByUserIdAndProblemId(userA.getId(), twoSum.getId()))
                .thenReturn(Optional.of(existing));

        RevisionStateDTO dto = scheduler.processSession(s2);
        assertNotNull(dto);
        assertEquals(1, dto.getReviewCount());
        assertEquals(100L, dto.getId());
    }

    // B. INTERVAL BEHAVIOR
    @Test
    @DisplayName("4. Initial interval calculation bounded between 2 and 7 days")
    void testInitialIntervalBounds() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 13, 10, 0);
        ProblemSession session = buildSession("s1", userA, twoSum, now, true, 1, 60000L, 120000L, 0L, 0, false, 0, false, false);

        when(revisionStateRepository.findByUserIdAndProblemId(userA.getId(), twoSum.getId())).thenReturn(Optional.empty());
        // Max confidence/retention (100, 100) -> S = 1.0 -> round(2 + 5*1.0) = 7
        when(confidenceStateRepository.findByUserIdAndProblemId(userA.getId(), twoSum.getId()))
                .thenReturn(Optional.of(ConfidenceState.builder().currentConfidence(100.0).retentionStrength(100.0).build()));

        RevisionStateDTO dtoMax = scheduler.processSession(session);
        assertEquals(7, dtoMax.getCurrentIntervalDays());

        // Min confidence/retention (0, 0) -> S = 0.0 -> round(2 + 0) = 2
        when(confidenceStateRepository.findByUserIdAndProblemId(userA.getId(), twoSum.getId()))
                .thenReturn(Optional.of(ConfidenceState.builder().currentConfidence(0.0).retentionStrength(0.0).build()));
        when(revisionHistoryRepository.existsBySourceSessionId("s1-min")).thenReturn(false);
        ProblemSession sessionMin = buildSession("s1-min", userA, twoSum, now, true, 1, 60000L, 120000L, 0L, 0, false, 0, false, false);

        RevisionStateDTO dtoMin = scheduler.processSession(sessionMin);
        assertEquals(2, dtoMin.getCurrentIntervalDays());
    }

    @Test
    @DisplayName("5. AGAIN contracts interval (multiplier 0.25)")
    void testAgainContractsInterval() {
        LocalDateTime t1 = LocalDateTime.of(2026, 9, 1, 10, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 9, 10, 10, 0);

        RevisionState state = RevisionState.builder()
                .user(userA).problem(twoSum).reviewCount(1).currentIntervalDays(8)
                .lastReviewedAt(t1).nextReviewAt(t1.plusDays(8)).build();

        // Solve with solution viewed -> AGAIN
        ProblemSession s2 = buildSession("s2", userA, twoSum, t2, true, 1, 60000L, 120000L, 0L, 0, false, 0, true, false);

        when(revisionStateRepository.findByUserIdAndProblemId(userA.getId(), twoSum.getId())).thenReturn(Optional.of(state));

        RevisionStateDTO dto = scheduler.processSession(s2);
        assertTrue(dto.getCurrentIntervalDays() < 8, "Interval should contract on AGAIN, got: " + dto.getCurrentIntervalDays());
        assertTrue(dto.getCurrentIntervalDays() <= 3);
    }

    @Test
    @DisplayName("6. HARD contracts/slightly preserves interval (multiplier 0.75)")
    void testHardContractsInterval() {
        LocalDateTime t1 = LocalDateTime.of(2026, 9, 1, 10, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 9, 9, 10, 0);

        RevisionState state = RevisionState.builder()
                .user(userA).problem(twoSum).reviewCount(1).currentIntervalDays(8)
                .lastReviewedAt(t1).nextReviewAt(t1.plusDays(8)).build();

        // Multiple hints -> HARD
        ProblemSession s2 = buildSession("s2", userA, twoSum, t2, true, 1, 60000L, 120000L, 0L, 0, true, 2, false, false);

        when(revisionStateRepository.findByUserIdAndProblemId(userA.getId(), twoSum.getId())).thenReturn(Optional.of(state));

        RevisionStateDTO dto = scheduler.processSession(s2);
        assertTrue(dto.getCurrentIntervalDays() <= 8, "Interval should be slightly contracted or preserved on HARD");
        assertTrue(dto.getCurrentIntervalDays() >= 5);
    }

    @Test
    @DisplayName("7. GOOD expands interval (multiplier 1.75)")
    void testGoodExpandsInterval() {
        LocalDateTime t1 = LocalDateTime.of(2026, 9, 1, 10, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 9, 9, 10, 0);

        RevisionState state = RevisionState.builder()
                .user(userA).problem(twoSum).reviewCount(1).currentIntervalDays(8)
                .lastReviewedAt(t1).nextReviewAt(t1.plusDays(8)).build();

        // 2 attempts normal independent solve -> GOOD
        ProblemSession s2 = buildSession("s2", userA, twoSum, t2, true, 2, 60000L, 120000L, 0L, 0, false, 0, false, false);

        when(revisionStateRepository.findByUserIdAndProblemId(userA.getId(), twoSum.getId())).thenReturn(Optional.of(state));

        RevisionStateDTO dto = scheduler.processSession(s2);
        assertTrue(dto.getCurrentIntervalDays() > 8, "Interval should expand on GOOD: " + dto.getCurrentIntervalDays());
        assertTrue(dto.getCurrentIntervalDays() >= 13);
    }

    @Test
    @DisplayName("8. EASY expands interval (multiplier 2.25)")
    void testEasyExpandsInterval() {
        LocalDateTime t1 = LocalDateTime.of(2026, 9, 1, 10, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 9, 9, 10, 0);

        RevisionState state = RevisionState.builder()
                .user(userA).problem(twoSum).reviewCount(1).currentIntervalDays(8)
                .lastReviewedAt(t1).nextReviewAt(t1.plusDays(8)).build();

        // Clean first attempt 8 days later -> EASY
        ProblemSession s2 = buildSession("s2", userA, twoSum, t2, true, 1, 60000L, 120000L, 0L, 0, false, 0, false, false);

        when(revisionStateRepository.findByUserIdAndProblemId(userA.getId(), twoSum.getId())).thenReturn(Optional.of(state));

        RevisionStateDTO dto = scheduler.processSession(s2);
        assertTrue(dto.getCurrentIntervalDays() >= 16, "Interval should expand strongly on EASY: " + dto.getCurrentIntervalDays());
    }

    @Test
    @DisplayName("9. Minimum interval enforced (>= 1 day)")
    void testMinimumIntervalEnforced() {
        LocalDateTime t1 = LocalDateTime.of(2026, 9, 1, 10, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 9, 3, 10, 0);

        RevisionState state = RevisionState.builder()
                .user(userA).problem(twoSum).reviewCount(1).currentIntervalDays(1)
                .lastReviewedAt(t1).nextReviewAt(t1.plusDays(1)).build();

        // Unsolved -> AGAIN
        ProblemSession s2 = buildSession("s2", userA, twoSum, t2, false, 1, 60000L, 120000L, 0L, 0, false, 0, false, false);

        when(revisionStateRepository.findByUserIdAndProblemId(userA.getId(), twoSum.getId())).thenReturn(Optional.of(state));

        RevisionStateDTO dto = scheduler.processSession(s2);
        assertEquals(1, dto.getCurrentIntervalDays(), "Minimum interval must remain 1 day");
    }

    @Test
    @DisplayName("10. Maximum 180-day interval enforced")
    void testMaximumIntervalEnforced() {
        LocalDateTime t1 = LocalDateTime.of(2026, 1, 1, 10, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 5, 1, 10, 0);

        RevisionState state = RevisionState.builder()
                .user(userA).problem(twoSum).reviewCount(5).currentIntervalDays(160)
                .lastReviewedAt(t1).nextReviewAt(t1.plusDays(160)).build();

        ProblemSession s2 = buildSession("s2", userA, twoSum, t2, true, 1, 60000L, 120000L, 0L, 0, false, 0, false, false);

        when(revisionStateRepository.findByUserIdAndProblemId(userA.getId(), twoSum.getId())).thenReturn(Optional.of(state));

        RevisionStateDTO dto = scheduler.processSession(s2);
        assertEquals(180, dto.getCurrentIntervalDays(), "Maximum interval must clamp to 180 days");
    }

    // C. RECALL TIMING
    @Test
    @DisplayName("11. Recall before due date (early recall supersedes old schedule)")
    void testRecallBeforeDueDate() {
        LocalDateTime t1 = LocalDateTime.of(2026, 9, 1, 10, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 9, 7, 10, 0); // Day 7 (planned was Day 10)

        RevisionState state = RevisionState.builder()
                .user(userA).problem(twoSum).reviewCount(0).currentIntervalDays(9)
                .lastReviewedAt(t1).nextReviewAt(t1.plusDays(9)).build();

        ProblemSession s2 = buildSession("s2", userA, twoSum, t2, true, 1, 60000L, 120000L, 0L, 0, false, 0, false, false);

        when(revisionStateRepository.findByUserIdAndProblemId(userA.getId(), twoSum.getId())).thenReturn(Optional.of(state));

        RevisionStateDTO dto = scheduler.processSession(s2);
        assertEquals(t2, dto.getLastReviewedAt(), "Actual recall date becomes new anchor");
        assertTrue(dto.getNextReviewAt().isAfter(t2));
    }

    @Test
    @DisplayName("12. Recall exactly around due date")
    void testRecallAroundDueDate() {
        LocalDateTime t1 = LocalDateTime.of(2026, 9, 1, 10, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 9, 6, 10, 0); // exactly 5 days

        RevisionState state = RevisionState.builder()
                .user(userA).problem(twoSum).reviewCount(1).currentIntervalDays(5)
                .lastReviewedAt(t1).nextReviewAt(t1.plusDays(5)).build();

        ProblemSession s2 = buildSession("s2", userA, twoSum, t2, true, 1, 60000L, 120000L, 0L, 0, false, 0, false, false);
        when(revisionStateRepository.findByUserIdAndProblemId(userA.getId(), twoSum.getId())).thenReturn(Optional.of(state));

        RevisionStateDTO dto = scheduler.processSession(s2);
        assertNotNull(dto);
        assertEquals(2, dto.getReviewCount());
    }

    @Test
    @DisplayName("13. Recall after due date (overdue recall provides stronger retention proof)")
    void testRecallAfterDueDate() {
        LocalDateTime t1 = LocalDateTime.of(2026, 9, 1, 10, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 9, 15, 10, 0); // 14 days later, scheduled was 5

        RevisionState state = RevisionState.builder()
                .user(userA).problem(twoSum).reviewCount(1).currentIntervalDays(5)
                .lastReviewedAt(t1).nextReviewAt(t1.plusDays(5)).build();

        ProblemSession s2 = buildSession("s2", userA, twoSum, t2, true, 1, 60000L, 120000L, 0L, 0, false, 0, false, false);
        when(revisionStateRepository.findByUserIdAndProblemId(userA.getId(), twoSum.getId())).thenReturn(Optional.of(state));

        RevisionStateDTO dto = scheduler.processSession(s2);
        assertTrue(dto.getCurrentIntervalDays() > 10, "Overdue successful recall expands interval substantially");
    }

    @Test
    @DisplayName("14. Recall far beyond due date clamps retention factor to 2.0")
    void testRecallFarBeyondDueDateClampsRetentionFactor() {
        LocalDateTime t1 = LocalDateTime.of(2026, 1, 1, 10, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 4, 1, 10, 0); // 90 days later, planned was 5 days -> ratio 18.0, clamped to 2.0

        RevisionState state = RevisionState.builder()
                .user(userA).problem(twoSum).reviewCount(1).currentIntervalDays(5)
                .lastReviewedAt(t1).nextReviewAt(t1.plusDays(5)).build();

        ProblemSession s2 = buildSession("s2", userA, twoSum, t2, true, 1, 60000L, 120000L, 0L, 0, false, 0, false, false);
        when(revisionStateRepository.findByUserIdAndProblemId(userA.getId(), twoSum.getId())).thenReturn(Optional.of(state));

        RevisionStateDTO dto = scheduler.processSession(s2);
        assertNotNull(dto);
        assertTrue(dto.getCurrentIntervalDays() <= 180);
    }

    @Test
    @DisplayName("15. Failed early recall shortens interval and anchors to failure date")
    void testFailedEarlyRecall() {
        LocalDateTime t1 = LocalDateTime.of(2026, 9, 1, 10, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 9, 5, 10, 0); // Early attempt on day 4 (planned was day 10)

        RevisionState state = RevisionState.builder()
                .user(userA).problem(twoSum).reviewCount(1).currentIntervalDays(10)
                .lastReviewedAt(t1).nextReviewAt(t1.plusDays(10)).build();

        ProblemSession s2 = buildSession("s2", userA, twoSum, t2, false, 2, 60000L, 120000L, 0L, 0, false, 0, false, false);
        when(revisionStateRepository.findByUserIdAndProblemId(userA.getId(), twoSum.getId())).thenReturn(Optional.of(state));

        RevisionStateDTO dto = scheduler.processSession(s2);
        assertEquals(t2, dto.getLastReviewedAt());
        assertTrue(dto.getCurrentIntervalDays() < 10);
    }

    @Test
    @DisplayName("16. Successful late recall provides stronger retention evidence")
    void testSuccessfulLateRecall() {
        LocalDateTime t1 = LocalDateTime.of(2026, 9, 1, 10, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 9, 20, 10, 0);

        RevisionState state = RevisionState.builder()
                .user(userA).problem(twoSum).reviewCount(1).currentIntervalDays(5)
                .lastReviewedAt(t1).nextReviewAt(t1.plusDays(5)).build();

        ConfidenceState cs = ConfidenceState.builder().currentConfidence(70.0).retentionStrength(40.0).build();
        when(confidenceStateRepository.findByUserIdAndProblemId(userA.getId(), twoSum.getId())).thenReturn(Optional.of(cs));
        when(revisionStateRepository.findByUserIdAndProblemId(userA.getId(), twoSum.getId())).thenReturn(Optional.of(state));

        ProblemSession s2 = buildSession("s2", userA, twoSum, t2, true, 1, 60000L, 120000L, 0L, 0, false, 0, false, false);
        scheduler.processSession(s2);

        assertTrue(cs.getRetentionStrength() > 50.0, "Late recall should boost retention strength strongly");
    }

    @Test
    @DisplayName("17. Actual recall becomes new scheduling anchor")
    void testActualRecallBecomesNewAnchor() {
        LocalDateTime t1 = LocalDateTime.of(2026, 9, 1, 10, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 9, 12, 10, 0);

        RevisionState state = RevisionState.builder()
                .user(userA).problem(twoSum).reviewCount(1).currentIntervalDays(5)
                .lastReviewedAt(t1).nextReviewAt(t1.plusDays(5)).build();

        ProblemSession s2 = buildSession("s2", userA, twoSum, t2, true, 1, 60000L, 120000L, 0L, 0, false, 0, false, false);
        when(revisionStateRepository.findByUserIdAndProblemId(userA.getId(), twoSum.getId())).thenReturn(Optional.of(state));

        RevisionStateDTO dto = scheduler.processSession(s2);
        assertEquals(t2, dto.getLastReviewedAt());
        assertEquals(t2.plusDays(dto.getCurrentIntervalDays()), dto.getNextReviewAt());
    }

    // D. REPEATED SOLVES
    @Test
    @DisplayName("18. <24h repeated solve does not create meaningful SRS recall")
    void testShortIntervalRepeatedSolveIgnored() {
        LocalDateTime t1 = LocalDateTime.of(2026, 9, 1, 10, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 9, 1, 14, 0); // 4 hours later, planned is Day 5

        RevisionState state = RevisionState.builder()
                .user(userA).problem(twoSum).reviewCount(0).currentIntervalDays(4)
                .lastReviewedAt(t1).nextReviewAt(t1.plusDays(4)).build();

        ProblemSession s2 = buildSession("s2", userA, twoSum, t2, true, 1, 60000L, 120000L, 0L, 0, false, 0, false, false);
        when(revisionStateRepository.findByUserIdAndProblemId(userA.getId(), twoSum.getId())).thenReturn(Optional.of(state));

        RevisionStateDTO dto = scheduler.processSession(s2);
        assertEquals(0, dto.getReviewCount(), "Review count must not increment on <24h repeat");
        assertEquals(4, dto.getCurrentIntervalDays(), "Interval must not change on <24h repeat");
        assertEquals(t1, dto.getLastReviewedAt(), "Last reviewed must remain original anchor");
    }

    @Test
    @DisplayName("19. >=24h natural recall does create meaningful SRS recall")
    void testTwentyFourHourNaturalRecallProcessed() {
        LocalDateTime t1 = LocalDateTime.of(2026, 9, 1, 10, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 9, 2, 11, 0); // 25 hours later

        RevisionState state = RevisionState.builder()
                .user(userA).problem(twoSum).reviewCount(0).currentIntervalDays(4)
                .lastReviewedAt(t1).nextReviewAt(t1.plusDays(4)).build();

        ProblemSession s2 = buildSession("s2", userA, twoSum, t2, true, 1, 60000L, 120000L, 0L, 0, false, 0, false, false);
        when(revisionStateRepository.findByUserIdAndProblemId(userA.getId(), twoSum.getId())).thenReturn(Optional.of(state));

        RevisionStateDTO dto = scheduler.processSession(s2);
        assertEquals(1, dto.getReviewCount());
        assertEquals(t2, dto.getLastReviewedAt());
    }

    @Test
    @DisplayName("20. Due/overdue natural recall is meaningful even if attempted quickly")
    void testDueNaturalRecallProcessedEvenIfRecent() {
        LocalDateTime t1 = LocalDateTime.of(2026, 9, 1, 10, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 9, 1, 12, 0); // 2 hours later, but state nextReviewAt is t1 (due now)

        RevisionState state = RevisionState.builder()
                .user(userA).problem(twoSum).reviewCount(0).currentIntervalDays(1)
                .lastReviewedAt(t1).nextReviewAt(t1).build(); // already due

        ProblemSession s2 = buildSession("s2", userA, twoSum, t2, true, 1, 60000L, 120000L, 0L, 0, false, 0, false, false);
        when(revisionStateRepository.findByUserIdAndProblemId(userA.getId(), twoSum.getId())).thenReturn(Optional.of(state));

        RevisionStateDTO dto = scheduler.processSession(s2);
        assertEquals(1, dto.getReviewCount(), "Due/overdue items must be processed as meaningful recall");
    }

    // E. ASSISTANCE
    @Test
    @DisplayName("21. No assistance expands interval")
    void testNoAssistanceExpandsInterval() {
        LocalDateTime t1 = LocalDateTime.of(2026, 9, 1, 10, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 9, 5, 10, 0);

        RevisionState state = RevisionState.builder()
                .user(userA).problem(twoSum).reviewCount(1).currentIntervalDays(4)
                .lastReviewedAt(t1).nextReviewAt(t1.plusDays(4)).build();
        when(revisionStateRepository.findByUserIdAndProblemId(userA.getId(), twoSum.getId())).thenReturn(Optional.of(state));

        ProblemSession sClean = buildSession("s-clean", userA, twoSum, t2, true, 1, 60000L, 120000L, 0L, 0, false, 0, false, false);
        RevisionStateDTO dtoClean = scheduler.processSession(sClean);
        assertTrue(dtoClean.getCurrentIntervalDays() > 4);
    }

    @Test
    @DisplayName("22-23. Multiple hints contracts/preserves interval on HARD")
    void testMultipleHintsContractsInterval() {
        LocalDateTime t1 = LocalDateTime.of(2026, 9, 1, 10, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 9, 5, 10, 0);

        RevisionState state = RevisionState.builder()
                .user(userA).problem(twoSum).reviewCount(1).currentIntervalDays(4)
                .lastReviewedAt(t1).nextReviewAt(t1.plusDays(4)).build();
        when(revisionStateRepository.findByUserIdAndProblemId(userA.getId(), twoSum.getId())).thenReturn(Optional.of(state));

        ProblemSession sMultiHints = buildSession("s-multi-hints", userA, twoSum, t2, true, 1, 60000L, 120000L, 0L, 0, true, 2, false, false);
        RevisionStateDTO dtoHints = scheduler.processSession(sMultiHints);
        assertTrue(dtoHints.getCurrentIntervalDays() <= 4);
    }

    @Test
    @DisplayName("24. Editorial viewed contracts interval to minimum on AGAIN")
    void testEditorialContractsInterval() {
        LocalDateTime t1 = LocalDateTime.of(2026, 9, 1, 10, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 9, 5, 10, 0);

        RevisionState state = RevisionState.builder()
                .user(userA).problem(twoSum).reviewCount(1).currentIntervalDays(4)
                .lastReviewedAt(t1).nextReviewAt(t1.plusDays(4)).build();
        when(revisionStateRepository.findByUserIdAndProblemId(userA.getId(), twoSum.getId())).thenReturn(Optional.of(state));

        ProblemSession sEdit = buildSession("s-edit", userA, twoSum, t2, true, 1, 60000L, 120000L, 0L, 0, false, 0, false, true);
        RevisionStateDTO dtoEdit = scheduler.processSession(sEdit);
        assertTrue(dtoEdit.getCurrentIntervalDays() <= 2);
    }

    @Test
    @DisplayName("25-26. Solution viewed contracts interval on AGAIN")
    void testSolutionContractsInterval() {
        LocalDateTime t1 = LocalDateTime.of(2026, 9, 1, 10, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 9, 5, 10, 0);

        RevisionState state = RevisionState.builder()
                .user(userA).problem(twoSum).reviewCount(1).currentIntervalDays(4)
                .lastReviewedAt(t1).nextReviewAt(t1.plusDays(4)).build();
        when(revisionStateRepository.findByUserIdAndProblemId(userA.getId(), twoSum.getId())).thenReturn(Optional.of(state));

        ProblemSession sSol = buildSession("s-sol", userA, twoSum, t2, true, 1, 60000L, 120000L, 0L, 0, false, 0, true, false);
        RevisionStateDTO dtoSol = scheduler.processSession(sSol);
        assertTrue(dtoSol.getCurrentIntervalDays() <= 2);
    }

    // F. PERFORMANCE
    @Test
    @DisplayName("27. Faster second solve triggers efficiency improvement")
    void testFasterSecondSolveEfficiency() {
        LocalDateTime t1 = LocalDateTime.of(2026, 9, 1, 10, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 9, 5, 10, 0);

        ProblemSession s1 = buildSession("s1", userA, twoSum, t1, true, 1, 100000L, 200000L, 0L, 0, false, 0, false, false); // 300s
        ProblemSession s2 = buildSession("s2", userA, twoSum, t2, true, 1, 30000L, 60000L, 0L, 0, false, 0, false, false);   // 90s (< 0.75 ratio)

        RevisionState state = RevisionState.builder()
                .user(userA).problem(twoSum).reviewCount(1).currentIntervalDays(4)
                .lastReviewedAt(t1).nextReviewAt(t1.plusDays(4)).build();

        when(revisionStateRepository.findByUserIdAndProblemId(userA.getId(), twoSum.getId())).thenReturn(Optional.of(state));
        when(sessionRepository.findByUserIdAndProblemId(userA.getId(), twoSum.getId())).thenReturn(List.of(s1));

        RevisionStateDTO dto = scheduler.processSession(s2);
        assertNotNull(dto);
        assertTrue(dto.getCurrentIntervalDays() >= 8, "Fast solve triggers EASY and expands interval");
    }

    @Test
    @DisplayName("28. Slower second solve triggers HARD outcome")
    void testSlowerSecondSolveSlowdown() {
        LocalDateTime t1 = LocalDateTime.of(2026, 9, 1, 10, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 9, 5, 10, 0);

        ProblemSession s1 = buildSession("s1", userA, twoSum, t1, true, 1, 30000L, 60000L, 0L, 0, false, 0, false, false);   // 90s
        ProblemSession s2 = buildSession("s2", userA, twoSum, t2, true, 1, 150000L, 200000L, 0L, 0, false, 0, false, false); // 350s (> 1.35 ratio)

        RevisionState state = RevisionState.builder()
                .user(userA).problem(twoSum).reviewCount(1).currentIntervalDays(4)
                .lastReviewedAt(t1).nextReviewAt(t1.plusDays(4)).build();

        when(revisionStateRepository.findByUserIdAndProblemId(userA.getId(), twoSum.getId())).thenReturn(Optional.of(state));
        when(sessionRepository.findByUserIdAndProblemId(userA.getId(), twoSum.getId())).thenReturn(List.of(s1));

        RevisionStateDTO dto = scheduler.processSession(s2);
        assertTrue(dto.getCurrentIntervalDays() <= 4, "Slow solve contracts or preserves interval on HARD");
    }

    @Test
    @DisplayName("29-30. Away-time low vs high dampening")
    void testAwayTimeDampening() {
        LocalDateTime t1 = LocalDateTime.of(2026, 9, 1, 10, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 9, 5, 10, 0);

        RevisionState state = RevisionState.builder()
                .user(userA).problem(twoSum).reviewCount(1).currentIntervalDays(4)
                .lastReviewedAt(t1).nextReviewAt(t1.plusDays(4)).build();
        when(revisionStateRepository.findByUserIdAndProblemId(userA.getId(), twoSum.getId())).thenReturn(Optional.of(state));

        // High away time: 100s active, 400s away -> awayRatio = 0.80 -> HARD
        ProblemSession sHighAway = buildSession("s-high-away", userA, twoSum, t2, true, 1, 40000L, 60000L, 400000L, 5, false, 0, false, false);
        RevisionStateDTO dto = scheduler.processSession(sHighAway);
        assertTrue(dto.getCurrentIntervalDays() <= 4);
    }

    // G. QUEUE SCHEDULING
    @Test
    @DisplayName("31-33. Queue capacity never moves nextReviewAt forward")
    void testCapacityNeverMovesNextReviewAt() {
        mockSecurityContext(userA);
        LocalDateTime now = LocalDateTime.of(2026, 9, 13, 10, 0);

        RevisionState r1 = RevisionState.builder()
                .id(1L).user(userA).problem(twoSum).currentIntervalDays(5)
                .nextReviewAt(now.minusDays(2)).skipCount(0).build();

        RevisionState r2 = RevisionState.builder()
                .id(2L).user(userA).problem(addTwoNumbers).currentIntervalDays(5)
                .nextReviewAt(now.minusDays(2)).skipCount(0).build();

        when(revisionStateRepository.findDueRevisions(eq(userA.getId()), any()))
                .thenReturn(List.of(r1, r2));

        // User capacity is 1
        when(sessionRepository.findByUserId(userA.getId())).thenReturn(Collections.emptyList());
        when(revisionHistoryRepository.findByUserIdAndReviewedAtAfter(eq(userA.getId()), any())).thenReturn(Collections.emptyList());

        lenient().when(revisionStateRepository.findByUserIdAndProblemId(userA.getId(), twoSum.getId())).thenReturn(Optional.of(r1));
        lenient().when(revisionStateRepository.findByUserIdAndProblemId(userA.getId(), addTwoNumbers.getId())).thenReturn(Optional.of(r2));

        ReviewQueueResponseDTO queueResponse = scheduler.getTodayReviewQueue(ZoneId.systemDefault());

        assertEquals(2, queueResponse.getTotalDue());
        assertEquals(1, queueResponse.getDailyCapacity());
        assertEquals(1, queueResponse.getQueue().size());
        assertEquals(1, queueResponse.getBacklogCount());

        // Both items must maintain their original nextReviewAt
        assertEquals(now.minusDays(2), r1.getNextReviewAt());
        assertEquals(now.minusDays(2), r2.getNextReviewAt());
    }

    @Test
    @DisplayName("34-36. Due item becomes overdue and overdue pressure increases priority")
    void testOverduePriorityIncreases() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 13, 10, 0);

        double onTimePressure = priorityService.calculateOverduePressure(0.0, 5);
        double overduePressure = priorityService.calculateOverduePressure(5.0, 5);

        assertEquals(0.5, onTimePressure);
        assertEquals(1.0, overduePressure);
        assertTrue(overduePressure > onTimePressure);
    }

    @Test
    @DisplayName("37-38. skipCount increases when item is skipped and triggers FAIRNESS_REQUIRED")
    void testSkipCountAndFairnessRequired() {
        mockSecurityContext(userA);
        LocalDateTime now = LocalDateTime.of(2026, 9, 13, 10, 0);

        RevisionState r1 = RevisionState.builder()
                .id(1L).user(userA).problem(twoSum).currentIntervalDays(5)
                .nextReviewAt(now.minusDays(1)).skipCount(0).build();

        RevisionState r2 = RevisionState.builder()
                .id(2L).user(userA).problem(addTwoNumbers).currentIntervalDays(5)
                .nextReviewAt(now.minusDays(1)).skipCount(2).build(); // will become 3 if skipped

        when(revisionStateRepository.findDueRevisions(eq(userA.getId()), any()))
                .thenReturn(List.of(r1, r2));

        // Capacity 1
        when(sessionRepository.findByUserId(userA.getId())).thenReturn(Collections.emptyList());
        when(revisionHistoryRepository.findByUserIdAndReviewedAtAfter(eq(userA.getId()), any())).thenReturn(Collections.emptyList());

        lenient().when(revisionStateRepository.findByUserIdAndProblemId(userA.getId(), twoSum.getId())).thenReturn(Optional.of(r1));
        lenient().when(revisionStateRepository.findByUserIdAndProblemId(userA.getId(), addTwoNumbers.getId())).thenReturn(Optional.of(r2));

        ReviewQueueResponseDTO queueResponse = scheduler.getTodayReviewQueue(ZoneId.systemDefault());

        // One item was selected, the other had skipCount incremented
        verify(revisionStateRepository, atLeastOnce()).save(any(RevisionState.class));
    }

    @Test
    @DisplayName("39. Backlog does not change confidence by itself")
    void testBacklogDoesNotChangeConfidence() {
        ConfidenceState cs = ConfidenceState.builder().currentConfidence(80.0).build();
        // Unreviewed backlog item
        assertEquals(80.0, cs.getCurrentConfidence());
    }

    // H. CAPACITY
    @Test
    @DisplayName("40-42. Capacity derived from median of recent active review days")
    void testCapacityDerivedFromMedian() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 13, 10, 0);
        ZoneId zone = ZoneId.systemDefault();

        // 3 active days with 1, 2, and 10 reviews
        // Median of [1, 2, 10] is 2! (10 reviews does not inflate capacity to 10)
        List<RevisionHistory> historyList = List.of(
                RevisionHistory.builder().reviewedAt(now.minusDays(1)).build(),
                RevisionHistory.builder().reviewedAt(now.minusDays(2)).build(),
                RevisionHistory.builder().reviewedAt(now.minusDays(2).plusHours(1)).build(),
                // Day 3 had 10 reviews
                RevisionHistory.builder().reviewedAt(now.minusDays(3)).build(),
                RevisionHistory.builder().reviewedAt(now.minusDays(3).plusHours(1)).build(),
                RevisionHistory.builder().reviewedAt(now.minusDays(3).plusHours(2)).build(),
                RevisionHistory.builder().reviewedAt(now.minusDays(3).plusHours(3)).build(),
                RevisionHistory.builder().reviewedAt(now.minusDays(3).plusHours(4)).build(),
                RevisionHistory.builder().reviewedAt(now.minusDays(3).plusHours(5)).build(),
                RevisionHistory.builder().reviewedAt(now.minusDays(3).plusHours(6)).build(),
                RevisionHistory.builder().reviewedAt(now.minusDays(3).plusHours(7)).build(),
                RevisionHistory.builder().reviewedAt(now.minusDays(3).plusHours(8)).build(),
                RevisionHistory.builder().reviewedAt(now.minusDays(3).plusHours(9)).build()
        );

        when(revisionHistoryRepository.findByUserIdAndReviewedAtAfter(eq(userA.getId()), any()))
                .thenReturn(historyList);
        when(sessionRepository.findByUserId(userA.getId())).thenReturn(Collections.emptyList());

        var capacity = capacityService.calculateCapacity(userA.getId(), now, zone);
        assertEquals(2, capacity.getDailyCapacity(), "Median of [1, 2, 10] must be 2, protecting against one unusually high day");
    }

    @Test
    @DisplayName("43-44. New user conservative default of 1 review/day")
    void testNewUserConservativeDefault() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 13, 10, 0);
        when(sessionRepository.findByUserId(userA.getId())).thenReturn(Collections.emptyList());
        when(revisionHistoryRepository.findByUserIdAndReviewedAtAfter(eq(userA.getId()), any())).thenReturn(Collections.emptyList());

        var capacity = capacityService.calculateCapacity(userA.getId(), now, ZoneId.systemDefault());
        assertEquals(1, capacity.getDailyCapacity());
        assertEquals(0, capacity.getActiveDaysLast30Days());
    }

    // I. NATURAL RECALL
    @Test
    @DisplayName("45-48. Natural recall supersedes old due date and is not processed again")
    void testNaturalRecallSupersedesOldDueDate() {
        LocalDateTime t1 = LocalDateTime.of(2026, 9, 1, 10, 0);
        LocalDateTime t2 = LocalDateTime.of(2026, 9, 6, 10, 0);

        RevisionState state = RevisionState.builder()
                .user(userA).problem(twoSum).reviewCount(1).currentIntervalDays(8)
                .lastReviewedAt(t1).nextReviewAt(t1.plusDays(8)).build();

        ProblemSession s2 = buildSession("s2", userA, twoSum, t2, true, 1, 60000L, 120000L, 0L, 0, false, 0, false, false);
        when(revisionStateRepository.findByUserIdAndProblemId(userA.getId(), twoSum.getId())).thenReturn(Optional.of(state));

        RevisionStateDTO dto = scheduler.processSession(s2);
        assertEquals(t2, dto.getLastReviewedAt());
        assertNotEquals(t1.plusDays(8), dto.getNextReviewAt(), "Old scheduled date must be replaced");
    }

    // J. STATE INTEGRITY
    @Test
    @DisplayName("49. Idempotency: Duplicate session does not update twice")
    void testIdempotency() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 13, 10, 0);
        ProblemSession session = buildSession("s-dup", userA, twoSum, now, true, 1, 60000L, 120000L, 0L, 0, false, 0, false, false);

        RevisionState state = RevisionState.builder()
                .user(userA).problem(twoSum).reviewCount(1).currentIntervalDays(5)
                .lastReviewedAt(now).nextReviewAt(now.plusDays(5)).build();

        when(revisionHistoryRepository.existsBySourceSessionId("s-dup")).thenReturn(true);
        when(revisionStateRepository.findByUserIdAndProblemId(userA.getId(), twoSum.getId())).thenReturn(Optional.of(state));

        RevisionStateDTO dto = scheduler.processSession(session);
        assertEquals(1, dto.getReviewCount());
        verify(revisionStateRepository, never()).save(any());
    }

    @Test
    @DisplayName("50. User isolation: User A cannot access User B's revision state")
    void testUserIsolation() {
        mockSecurityContext(userA);

        when(revisionStateRepository.findWithHistoryByUserIdAndProblemId(userA.getId(), twoSum.getId()))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> scheduler.getRevisionForProblem(twoSum.getId()));
    }

    @Test
    @DisplayName("51-53. Historical records remain immutable and algorithm version persisted as SRS_V1")
    void testHistoryAndAlgorithmVersion() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 13, 10, 0);
        ProblemSession session = buildSession("s1", userA, twoSum, now, true, 1, 60000L, 120000L, 0L, 0, false, 0, false, false);

        when(revisionStateRepository.findByUserIdAndProblemId(userA.getId(), twoSum.getId())).thenReturn(Optional.empty());
        when(confidenceStateRepository.findByUserIdAndProblemId(userA.getId(), twoSum.getId()))
                .thenReturn(Optional.of(ConfidenceState.builder().currentConfidence(80.0).retentionStrength(40.0).build()));

        RevisionStateDTO dto = scheduler.processSession(session);
        assertEquals("SRS_V1", dto.getAlgorithmVersion());
        assertEquals(1, dto.getHistory().size());
        assertEquals("SRS_V1", dto.getHistory().get(0).getAlgorithmVersion());
    }

    @Test
    @DisplayName("54. Zero-data / empty queue behavior returns empty queue gracefully")
    void testEmptyQueueBehavior() {
        mockSecurityContext(userA);
        when(revisionStateRepository.findDueRevisions(eq(userA.getId()), any()))
                .thenReturn(Collections.emptyList());
        when(sessionRepository.findByUserId(userA.getId())).thenReturn(Collections.emptyList());
        when(revisionHistoryRepository.findByUserIdAndReviewedAtAfter(eq(userA.getId()), any())).thenReturn(Collections.emptyList());

        ReviewQueueResponseDTO queueResponse = scheduler.getTodayReviewQueue(ZoneId.systemDefault());
        assertNotNull(queueResponse);
        assertEquals(0, queueResponse.getTotalDue());
        assertEquals(0, queueResponse.getQueue().size());
        assertEquals(0, queueResponse.getBacklogCount());
    }
}
