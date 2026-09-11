package com.example.dsatracker.service;

import com.example.dsatracker.dto.*;
import com.example.dsatracker.model.*;
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
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPerformanceProfileServiceTest {

    @Mock
    private ProblemSessionRepository sessionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    private SessionAnalyticsService sessionAnalyticsService;
    private UserPerformanceProfileService profileService;

    private User testUserA;
    private Problem problemA;
    private Problem problemB;
    private Problem problemC;

    @BeforeEach
    void setUp() {
        sessionAnalyticsService = new SessionAnalyticsService(sessionRepository, userRepository);
        profileService = new UserPerformanceProfileService(
                sessionRepository,
                userRepository,
                sessionAnalyticsService
        );

        testUserA = User.builder()
                .id(1L)
                .name("Alice")
                .email("alice@example.com")
                .password("pass")
                .build();

        Tag arrayTag = Tag.builder().id(1L).name("Array").build();
        Tag hashTableTag = Tag.builder().id(2L).name("Hash Table").build();
        Tag dpTag = Tag.builder().id(3L).name("Dynamic Programming").build();

        problemA = Problem.builder()
                .id(101L)
                .leetcodeId(1)
                .title("Two Sum")
                .difficulty(Difficulty.EASY)
                .url("https://leetcode.com/problems/two-sum/")
                .tags(new HashSet<>(Set.of(arrayTag, hashTableTag)))
                .build();

        problemB = Problem.builder()
                .id(102L)
                .leetcodeId(2)
                .title("Add Two Numbers")
                .difficulty(Difficulty.MEDIUM)
                .url("https://leetcode.com/problems/add-two-numbers/")
                .tags(new HashSet<>(Set.of(arrayTag)))
                .build();

        problemC = Problem.builder()
                .id(103L)
                .leetcodeId(3)
                .title("Longest Palindromic Substring")
                .difficulty(Difficulty.HARD)
                .url("https://leetcode.com/problems/longest-palindromic-substring/")
                .tags(new HashSet<>(Set.of(dpTag)))
                .build();

        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        lenient().when(authentication.getName()).thenReturn("alice@example.com");
        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    @DisplayName("TEST 1: Zero-data profile safely returns zeros and empty collections")
    void testZeroDataProfile() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(testUserA));
        when(sessionRepository.findByUserIdOrderBySessionStartedAtDesc(1L)).thenReturn(Collections.emptyList());

        UserPerformanceProfileDTO profile = profileService.getUserProfile();

        assertNotNull(profile);
        assertNotNull(profile.getOverall());
        assertEquals(0, profile.getOverall().getUniqueProblemsSolved());
        assertEquals(0, profile.getOverall().getTotalSolvedSessions());
        assertEquals(0, profile.getOverall().getTotalSessions());
        assertEquals(0, profile.getOverall().getTotalSubmissions());
        assertEquals(0.0, profile.getOverall().getAverageAttempts());
        assertEquals(0.0, profile.getOverall().getFirstAttemptSuccessRate());
        assertEquals(0L, profile.getOverall().getAverageSolveTime());
        assertEquals(0L, profile.getOverall().getTotalTimeSpent());
        assertTrue(profile.getDifficultyPerformance().isEmpty());
        assertTrue(profile.getTopicPerformance().isEmpty());
        assertNotNull(profile.getRecentTrends());
        assertEquals(0, profile.getRecentTrends().getLast7Days().getCurrentPeriod().getUniqueProblemsSolved());
        assertEquals(0, profile.getRecentTrends().getLast7Days().getDeltaProblemsSolved());
    }

    @Test
    @DisplayName("TEST 2: Unique problem counting vs solved session counting (Repeated Solves)")
    void testUniqueProblemsVsSolvedSessions() {
        // Problem A solved twice, Problem B solved once
        ProblemSession s1 = ProblemSession.builder()
                .id(1L)
                .sessionId("sess-1")
                .user(testUserA)
                .problem(problemA)
                .thinkingDuration(10000L)
                .codingDuration(30000L)
                .attempts(1)
                .solved(true)
                .events(List.of(
                        SessionEvent.builder().eventType(SessionEventType.SUBMISSION).result("ACCEPTED").build()
                ))
                .build();

        ProblemSession s2 = ProblemSession.builder()
                .id(2L)
                .sessionId("sess-2")
                .user(testUserA)
                .problem(problemA) // Problem A solved a second time
                .thinkingDuration(5000L)
                .codingDuration(15000L)
                .attempts(1)
                .solved(true)
                .events(List.of(
                        SessionEvent.builder().eventType(SessionEventType.SUBMISSION).result("ACCEPTED").build()
                ))
                .build();

        ProblemSession s3 = ProblemSession.builder()
                .id(3L)
                .sessionId("sess-3")
                .user(testUserA)
                .problem(problemB) // Problem B solved once
                .thinkingDuration(20000L)
                .codingDuration(60000L)
                .attempts(2)
                .solved(true)
                .events(List.of(
                        SessionEvent.builder().eventType(SessionEventType.SUBMISSION).result("WRONG_ANSWER").build(),
                        SessionEvent.builder().eventType(SessionEventType.SUBMISSION).result("ACCEPTED").build()
                ))
                .build();

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(testUserA));
        when(sessionRepository.findByUserIdOrderBySessionStartedAtDesc(1L)).thenReturn(List.of(s1, s2, s3));

        UserPerformanceProfileDTO profile = profileService.getUserProfile();

        // Semantic Requirement:
        // Problem A solved twice + Problem B solved once:
        // uniqueProblemsSolved = 2
        // totalSolvedSessions = 3
        assertEquals(2, profile.getOverall().getUniqueProblemsSolved());
        assertEquals(3, profile.getOverall().getTotalSolvedSessions());
        assertEquals(3, profile.getOverall().getTotalSessions());
        // Submissions: 1 + 1 + 2 = 4
        assertEquals(4, profile.getOverall().getTotalSubmissions());
        // Attempts: (1 + 1 + 2) / 3 = 1.33
        assertEquals(1.33, profile.getOverall().getAverageAttempts());
        // First attempt success: s1, s2 succeeded on first attempt; s3 did not -> 2/3 = 66.67%
        assertEquals(66.67, profile.getOverall().getFirstAttemptSuccessRate());
    }

    @Test
    @DisplayName("TEST 3: Incomplete / unsolved sessions do not inflate uniqueProblemsSolved")
    void testIncompleteSessions() {
        ProblemSession solvedSession = ProblemSession.builder()
                .id(1L)
                .sessionId("sess-solved")
                .user(testUserA)
                .problem(problemA)
                .thinkingDuration(10000L)
                .codingDuration(30000L)
                .attempts(1)
                .solved(true)
                .events(List.of(
                        SessionEvent.builder().eventType(SessionEventType.SUBMISSION).result("ACCEPTED").build()
                ))
                .build();

        ProblemSession unsolvedSession = ProblemSession.builder()
                .id(2L)
                .sessionId("sess-unsolved")
                .user(testUserA)
                .problem(problemB)
                .thinkingDuration(20000L)
                .codingDuration(40000L)
                .attempts(2)
                .solved(false)
                .events(List.of(
                        SessionEvent.builder().eventType(SessionEventType.SUBMISSION).result("WRONG_ANSWER").build(),
                        SessionEvent.builder().eventType(SessionEventType.SUBMISSION).result("WRONG_ANSWER").build()
                ))
                .build();

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(testUserA));
        when(sessionRepository.findByUserIdOrderBySessionStartedAtDesc(1L)).thenReturn(List.of(solvedSession, unsolvedSession));

        UserPerformanceProfileDTO profile = profileService.getUserProfile();

        assertEquals(1, profile.getOverall().getUniqueProblemsSolved());
        assertEquals(1, profile.getOverall().getTotalSolvedSessions());
        assertEquals(2, profile.getOverall().getTotalSessions());
        assertEquals(3, profile.getOverall().getTotalSubmissions()); // 1 + 2
        assertEquals(100000L, profile.getOverall().getTotalTimeSpent()); // 40000 + 60000
    }

    @Test
    @DisplayName("TEST 4: Difficulty aggregation with assistance metrics")
    void testDifficultyAggregation() {
        ProblemSession easySession = ProblemSession.builder()
                .id(1L)
                .sessionId("sess-easy")
                .user(testUserA)
                .problem(problemA) // Easy
                .thinkingDuration(10000L)
                .codingDuration(30000L)
                .attempts(1)
                .solved(true)
                .hintOpened(true)
                .hintOpenCount(1)
                .solutionViewed(false)
                .editorialViewed(false)
                .events(List.of(
                        SessionEvent.builder().eventType(SessionEventType.SUBMISSION).result("ACCEPTED").build()
                ))
                .build();

        ProblemSession mediumSession = ProblemSession.builder()
                .id(2L)
                .sessionId("sess-med")
                .user(testUserA)
                .problem(problemB) // Medium
                .thinkingDuration(20000L)
                .codingDuration(60000L)
                .attempts(2)
                .solved(true)
                .hintOpened(false)
                .solutionViewed(true)
                .editorialViewed(true)
                .events(List.of(
                        SessionEvent.builder().eventType(SessionEventType.SUBMISSION).result("WRONG_ANSWER").build(),
                        SessionEvent.builder().eventType(SessionEventType.SUBMISSION).result("ACCEPTED").build()
                ))
                .build();

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(testUserA));
        when(sessionRepository.findByUserIdOrderBySessionStartedAtDesc(1L)).thenReturn(List.of(easySession, mediumSession));

        UserPerformanceProfileDTO profile = profileService.getUserProfile();

        DifficultyProfileDTO easy = profile.getDifficultyPerformance().get("EASY");
        assertNotNull(easy);
        assertEquals(1, easy.getUniqueProblemsSolved());
        assertEquals(1, easy.getTotalSolvedSessions());
        assertEquals(1, easy.getSessionCount());
        assertEquals(40000L, easy.getAverageSolveTime());
        assertEquals(1.0, easy.getAverageAttempts());
        assertEquals(100.0, easy.getFirstAttemptSuccessRate());
        assertEquals(100.0, easy.getHintUsageRate());
        assertEquals(0.0, easy.getSolutionUsageRate());
        assertEquals(0.0, easy.getEditorialUsageRate());

        DifficultyProfileDTO med = profile.getDifficultyPerformance().get("MEDIUM");
        assertNotNull(med);
        assertEquals(1, med.getUniqueProblemsSolved());
        assertEquals(1, med.getTotalSolvedSessions());
        assertEquals(1, med.getSessionCount());
        assertEquals(80000L, med.getAverageSolveTime());
        assertEquals(2.0, med.getAverageAttempts());
        assertEquals(0.0, med.getFirstAttemptSuccessRate());
        assertEquals(0.0, med.getHintUsageRate());
        assertEquals(100.0, med.getSolutionUsageRate());
        assertEquals(100.0, med.getEditorialUsageRate());
    }

    @Test
    @DisplayName("TEST 5: Topic aggregation and multi-tag credit without global inflation")
    void testTopicAggregationAndMultiTagCredit() {
        // problemA has ["Array", "Hash Table"]
        // problemB has ["Array"]
        ProblemSession s1 = ProblemSession.builder()
                .id(1L)
                .sessionId("sess-1")
                .user(testUserA)
                .problem(problemA)
                .thinkingDuration(10000L)
                .codingDuration(30000L)
                .attempts(1)
                .solved(true)
                .hintOpened(true)
                .events(List.of(
                        SessionEvent.builder().eventType(SessionEventType.SUBMISSION).result("ACCEPTED").build()
                ))
                .build();

        ProblemSession s2 = ProblemSession.builder()
                .id(2L)
                .sessionId("sess-2")
                .user(testUserA)
                .problem(problemB)
                .thinkingDuration(20000L)
                .codingDuration(40000L)
                .attempts(1)
                .solved(true)
                .hintOpened(false)
                .events(List.of(
                        SessionEvent.builder().eventType(SessionEventType.SUBMISSION).result("ACCEPTED").build()
                ))
                .build();

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(testUserA));
        when(sessionRepository.findByUserIdOrderBySessionStartedAtDesc(1L)).thenReturn(List.of(s1, s2));

        UserPerformanceProfileDTO profile = profileService.getUserProfile();

        // Global unique problems is strictly 2
        assertEquals(2, profile.getOverall().getUniqueProblemsSolved());

        // Array tag has 2 unique problems
        TopicProfileDTO arrayProfile = profile.getTopicPerformance().get("Array");
        assertNotNull(arrayProfile);
        assertEquals(2, arrayProfile.getUniqueProblemsSolved());
        assertEquals(2, arrayProfile.getTotalSolvedSessions());
        assertEquals(50000L, arrayProfile.getAverageSolveTime()); // (40000 + 60000) / 2
        assertEquals(50.0, arrayProfile.getHintUsageRate());      // 1 of 2

        // Hash Table tag has 1 unique problem
        TopicProfileDTO hashProfile = profile.getTopicPerformance().get("Hash Table");
        assertNotNull(hashProfile);
        assertEquals(1, hashProfile.getUniqueProblemsSolved());
        assertEquals(1, hashProfile.getTotalSolvedSessions());
        assertEquals(40000L, hashProfile.getAverageSolveTime());
        assertEquals(100.0, hashProfile.getHintUsageRate());
    }

    @Test
    @DisplayName("TEST 6: Problems without tags do not break topic aggregation")
    void testProblemWithoutTags() {
        Problem untaggedProblem = Problem.builder()
                .id(999L)
                .leetcodeId(999)
                .title("Untagged Problem")
                .difficulty(Difficulty.EASY)
                .tags(null)
                .build();

        ProblemSession session = ProblemSession.builder()
                .id(1L)
                .sessionId("sess-untagged")
                .user(testUserA)
                .problem(untaggedProblem)
                .thinkingDuration(10000L)
                .codingDuration(20000L)
                .attempts(1)
                .solved(true)
                .events(List.of(
                        SessionEvent.builder().eventType(SessionEventType.SUBMISSION).result("ACCEPTED").build()
                ))
                .build();

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(testUserA));
        when(sessionRepository.findByUserIdOrderBySessionStartedAtDesc(1L)).thenReturn(List.of(session));

        UserPerformanceProfileDTO profile = profileService.getUserProfile();

        assertEquals(1, profile.getOverall().getUniqueProblemsSolved());
        assertTrue(profile.getTopicPerformance().isEmpty());
    }

    @Test
    @DisplayName("TEST 7: Recent trend calculations (Current 7 Days vs Preceding 7 Days)")
    void testRecentTrendCalculations() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 11, 12, 0, 0);

        // Day 2 (Current period: [now-7d, now])
        ProblemSession current1 = ProblemSession.builder()
                .id(1L)
                .sessionId("sess-curr-1")
                .user(testUserA)
                .problem(problemA)
                .sessionStartedAt(now.minusDays(2))
                .thinkingDuration(10000L)
                .codingDuration(30000L) // active: 40000
                .attempts(1)
                .solved(true)
                .events(List.of(
                        SessionEvent.builder().eventType(SessionEventType.SUBMISSION).result("ACCEPTED").build()
                ))
                .build();

        // Day 5 (Current period: [now-7d, now])
        ProblemSession current2 = ProblemSession.builder()
                .id(2L)
                .sessionId("sess-curr-2")
                .user(testUserA)
                .problem(problemB)
                .sessionStartedAt(now.minusDays(5))
                .thinkingDuration(20000L)
                .codingDuration(40000L) // active: 60000
                .attempts(1)
                .solved(true)
                .events(List.of(
                        SessionEvent.builder().eventType(SessionEventType.SUBMISSION).result("ACCEPTED").build()
                ))
                .build();

        // Day 10 (Previous period: [now-14d, now-7d))
        ProblemSession previous1 = ProblemSession.builder()
                .id(3L)
                .sessionId("sess-prev-1")
                .user(testUserA)
                .problem(problemC)
                .sessionStartedAt(now.minusDays(10))
                .thinkingDuration(50000L)
                .codingDuration(150000L) // active: 200000
                .attempts(2)
                .solved(true)
                .events(List.of(
                        SessionEvent.builder().eventType(SessionEventType.SUBMISSION).result("WRONG_ANSWER").build(),
                        SessionEvent.builder().eventType(SessionEventType.SUBMISSION).result("ACCEPTED").build()
                ))
                .build();

        UserPerformanceProfileDTO profile = profileService.buildProfile(List.of(current1, current2, previous1), now);

        TrendComparisonDTO last7Days = profile.getRecentTrends().getLast7Days();
        assertNotNull(last7Days);

        // Current period: 2 problems solved, 2 sessions, totalTimeSpent = 100000, avgSolveTime = 50000, firstAttempt = 100.0%
        TrendPeriodMetricsDTO currMetrics = last7Days.getCurrentPeriod();
        assertEquals(2, currMetrics.getUniqueProblemsSolved());
        assertEquals(2, currMetrics.getTotalSolvedSessions());
        assertEquals(100000L, currMetrics.getTotalTimeSpent());
        assertEquals(50000L, currMetrics.getAverageSolveTime());
        assertEquals(100.0, currMetrics.getFirstAttemptSuccessRate());

        // Previous period: 1 problem solved, 1 session, totalTimeSpent = 200000, avgSolveTime = 200000, firstAttempt = 0.0%
        TrendPeriodMetricsDTO prevMetrics = last7Days.getPreviousPeriod();
        assertEquals(1, prevMetrics.getUniqueProblemsSolved());
        assertEquals(1, prevMetrics.getTotalSolvedSessions());
        assertEquals(200000L, prevMetrics.getTotalTimeSpent());
        assertEquals(200000L, prevMetrics.getAverageSolveTime());
        assertEquals(0.0, prevMetrics.getFirstAttemptSuccessRate());

        // Deltas (current - previous)
        assertEquals(1, last7Days.getDeltaProblemsSolved());          // 2 - 1 = +1
        assertEquals(1, last7Days.getDeltaSolvedSessions());          // 2 - 1 = +1
        assertEquals(-100000L, last7Days.getDeltaTimeSpent());        // 100000 - 200000 = -100000
        assertEquals(-150000L, last7Days.getDeltaAverageSolveTime()); // 50000 - 200000 = -150000
        assertEquals(100.0, last7Days.getDeltaFirstAttemptSuccessRate()); // 100.0 - 0.0 = +100.0
    }

    @Test
    @DisplayName("TEST 8: User isolation - queries only authenticated user's sessions")
    void testUserIsolation() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(testUserA));
        when(sessionRepository.findByUserIdOrderBySessionStartedAtDesc(1L)).thenReturn(Collections.emptyList());

        profileService.getUserProfile();

        org.mockito.Mockito.verify(sessionRepository).findByUserIdOrderBySessionStartedAtDesc(1L);
        org.mockito.Mockito.verify(sessionRepository, org.mockito.Mockito.never()).findByUserIdOrderBySessionStartedAtDesc(2L);
    }
}
