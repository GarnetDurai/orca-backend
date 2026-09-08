package com.example.dsatracker.service;

import com.example.dsatracker.dto.DifficultyAnalyticsDTO;
import com.example.dsatracker.dto.HistoricalAnalyticsDTO;
import com.example.dsatracker.dto.TimeWindow;
import com.example.dsatracker.dto.TopicAnalyticsDTO;
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
class HistoricalAnalyticsServiceTest {

    @Mock
    private ProblemSessionRepository sessionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    private SessionAnalyticsService sessionAnalyticsService;
    private HistoricalAnalyticsService historicalAnalyticsService;

    private User testUserA;
    private User testUserB;
    private Problem twoSum;
    private Problem reverseList;
    private Problem mergeKLists;

    @BeforeEach
    void setUp() {
        sessionAnalyticsService = new SessionAnalyticsService(sessionRepository, userRepository);
        historicalAnalyticsService = new HistoricalAnalyticsService(
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

        testUserB = User.builder()
                .id(2L)
                .name("Bob")
                .email("bob@example.com")
                .password("pass")
                .build();

        Tag arrayTag = Tag.builder().id(1L).name("Array").build();
        Tag hashTableTag = Tag.builder().id(2L).name("Hash Table").build();
        Tag linkedListTag = Tag.builder().id(3L).name("Linked List").build();
        Tag heapTag = Tag.builder().id(4L).name("Heap").build();

        twoSum = Problem.builder()
                .id(10L)
                .leetcodeId(1)
                .title("Two Sum")
                .difficulty(Difficulty.EASY)
                .url("https://leetcode.com/problems/two-sum/")
                .tags(new HashSet<>(Set.of(arrayTag, hashTableTag)))
                .build();

        reverseList = Problem.builder()
                .id(20L)
                .leetcodeId(206)
                .title("Reverse Linked List")
                .difficulty(Difficulty.EASY)
                .url("https://leetcode.com/problems/reverse-linked-list/")
                .tags(new HashSet<>(Set.of(linkedListTag)))
                .build();

        mergeKLists = Problem.builder()
                .id(30L)
                .leetcodeId(23)
                .title("Merge k Sorted Lists")
                .difficulty(Difficulty.HARD)
                .url("https://leetcode.com/problems/merge-k-sorted-lists/")
                .tags(new HashSet<>(Set.of(linkedListTag, heapTag)))
                .build();

        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        lenient().when(authentication.getName()).thenReturn("alice@example.com");
        SecurityContextHolder.setContext(securityContext);
    }

    @Test
    @DisplayName("TEST 1: User with zero sessions safely returns zeros and empty collections")
    void testZeroSessions() {
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(testUserA));
        when(sessionRepository.findByUserIdOrderBySessionStartedAtDesc(1L)).thenReturn(Collections.emptyList());

        HistoricalAnalyticsDTO result = historicalAnalyticsService.getHistoricalAnalytics(TimeWindow.ALL_TIME);

        assertNotNull(result);
        assertEquals(0, result.getTotalProblemsSolved());
        assertEquals(0, result.getTotalSessions());
        assertEquals(0L, result.getAverageSolveTime());
        assertEquals(0L, result.getAverageThinkingTime());
        assertEquals(0L, result.getAverageCodingTime());
        assertEquals(0.0, result.getAverageAttempts());
        assertEquals(0.0, result.getFirstAttemptSuccessRate());
        assertEquals(0, result.getTotalWrongSubmissions());
        assertEquals(0, result.getTotalAcceptedSubmissions());
        assertEquals(0.0, result.getHintUsageRate());
        assertEquals(0.0, result.getSolutionUsageRate());
        assertEquals(0.0, result.getEditorialUsageRate());
        assertEquals(0.0, result.getAverageHintsPerProblem());
        assertEquals(0L, result.getTotalTimeSpent());
        assertTrue(result.getProblemsSolvedByDifficulty().isEmpty());
        assertTrue(result.getDifficultyAnalytics().isEmpty());
        assertTrue(result.getTopicAnalytics().isEmpty());
    }

    @Test
    @DisplayName("TEST 2: One solved session - basic metrics check")
    void testOneSolvedSession() {
        List<SessionEvent> events = List.of(
                SessionEvent.builder().eventType(SessionEventType.CODING_STARTED).timestamp(LocalDateTime.now()).build(),
                SessionEvent.builder().eventType(SessionEventType.SUBMISSION).result("ACCEPTED").submissionId("sub-1").timestamp(LocalDateTime.now()).build(),
                SessionEvent.builder().eventType(SessionEventType.SOLVED).result("ACCEPTED").timestamp(LocalDateTime.now()).build()
        );

        ProblemSession s1 = ProblemSession.builder()
                .id(101L)
                .sessionId("sess-1")
                .user(testUserA)
                .problem(twoSum)
                .sessionStartedAt(LocalDateTime.now().minusDays(1))
                .thinkingDuration(120000L)
                .codingDuration(480000L)
                .attempts(1)
                .solved(true)
                .events(events)
                .build();

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(testUserA));
        when(sessionRepository.findByUserIdOrderBySessionStartedAtDesc(1L)).thenReturn(List.of(s1));

        HistoricalAnalyticsDTO result = historicalAnalyticsService.getHistoricalAnalytics(TimeWindow.ALL_TIME);

        assertEquals(1, result.getTotalProblemsSolved());
        assertEquals(1, result.getTotalSessions());
        assertEquals(600000L, result.getAverageSolveTime());
        assertEquals(120000L, result.getAverageThinkingTime());
        assertEquals(480000L, result.getAverageCodingTime());
        assertEquals(1.0, result.getAverageAttempts());
        assertEquals(100.0, result.getFirstAttemptSuccessRate());
        assertEquals(0, result.getTotalWrongSubmissions());
        assertEquals(1, result.getTotalAcceptedSubmissions());
        assertEquals(0.0, result.getHintUsageRate());
        assertEquals(0.0, result.getSolutionUsageRate());
        assertEquals(0.0, result.getEditorialUsageRate());
        assertEquals(0.0, result.getAverageHintsPerProblem());
        assertEquals(600000L, result.getTotalTimeSpent());
        assertEquals(1, result.getProblemsSolvedByDifficulty().get("EASY"));
    }

    @Test
    @DisplayName("TEST 3: Multiple solved sessions - averages and totals")
    void testMultipleSolvedSessions() {
        ProblemSession s1 = ProblemSession.builder()
                .id(101L)
                .sessionId("sess-1")
                .user(testUserA)
                .problem(twoSum)
                .sessionStartedAt(LocalDateTime.now().minusDays(2))
                .thinkingDuration(100000L)
                .codingDuration(400000L) // active: 500000
                .attempts(1)
                .solved(true)
                .events(List.of(
                        SessionEvent.builder().eventType(SessionEventType.SUBMISSION).result("ACCEPTED").timestamp(LocalDateTime.now()).build()
                ))
                .build();

        ProblemSession s2 = ProblemSession.builder()
                .id(102L)
                .sessionId("sess-2")
                .user(testUserA)
                .problem(reverseList)
                .sessionStartedAt(LocalDateTime.now().minusDays(1))
                .thinkingDuration(200000L)
                .codingDuration(500000L) // active: 700000
                .attempts(2)
                .solved(true)
                .events(List.of(
                        SessionEvent.builder().eventType(SessionEventType.SUBMISSION).result("WRONG_ANSWER").timestamp(LocalDateTime.now()).build(),
                        SessionEvent.builder().eventType(SessionEventType.SUBMISSION).result("ACCEPTED").timestamp(LocalDateTime.now()).build()
                ))
                .build();

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(testUserA));
        when(sessionRepository.findByUserIdOrderBySessionStartedAtDesc(1L)).thenReturn(List.of(s1, s2));

        HistoricalAnalyticsDTO result = historicalAnalyticsService.getHistoricalAnalytics(TimeWindow.ALL_TIME);

        assertEquals(2, result.getTotalProblemsSolved());
        assertEquals(2, result.getTotalSessions());
        // (500000 + 700000) / 2 = 600000
        assertEquals(600000L, result.getAverageSolveTime());
        // (100000 + 200000) / 2 = 150000
        assertEquals(150000L, result.getAverageThinkingTime());
        // (400000 + 500000) / 2 = 450000
        assertEquals(450000L, result.getAverageCodingTime());
        // (1 + 2) / 2 = 1.5
        assertEquals(1.5, result.getAverageAttempts());
        // 1 of 2 first attempt accepted = 50.0%
        assertEquals(50.0, result.getFirstAttemptSuccessRate());
        assertEquals(1, result.getTotalWrongSubmissions());
        assertEquals(2, result.getTotalAcceptedSubmissions());
        assertEquals(1200000L, result.getTotalTimeSpent());
    }

    @Test
    @DisplayName("TEST 4: Wrong Answer -> Accepted sessions metrics verification")
    void testWrongAnswerToAcceptedSessions() {
        ProblemSession s1 = ProblemSession.builder()
                .id(101L)
                .sessionId("sess-1")
                .user(testUserA)
                .problem(twoSum)
                .attempts(3)
                .solved(true)
                .events(List.of(
                        SessionEvent.builder().eventType(SessionEventType.SUBMISSION).result("WRONG_ANSWER").timestamp(LocalDateTime.now()).build(),
                        SessionEvent.builder().eventType(SessionEventType.SUBMISSION).result("WRONG_ANSWER").timestamp(LocalDateTime.now()).build(),
                        SessionEvent.builder().eventType(SessionEventType.SUBMISSION).result("ACCEPTED").timestamp(LocalDateTime.now()).build()
                ))
                .build();

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(testUserA));
        when(sessionRepository.findByUserIdOrderBySessionStartedAtDesc(1L)).thenReturn(List.of(s1));

        HistoricalAnalyticsDTO result = historicalAnalyticsService.getHistoricalAnalytics(TimeWindow.ALL_TIME);

        assertEquals(1, result.getTotalProblemsSolved());
        assertEquals(2, result.getTotalWrongSubmissions());
        assertEquals(1, result.getTotalAcceptedSubmissions());
        assertEquals(3.0, result.getAverageAttempts());
        assertEquals(0.0, result.getFirstAttemptSuccessRate());
    }

    @Test
    @DisplayName("TEST 5: Hint usage rate and averageHintsPerProblem across 0, 1, 2 hints")
    void testHintUsageRates() {
        ProblemSession s1 = ProblemSession.builder()
                .id(101L)
                .sessionId("sess-1")
                .user(testUserA)
                .problem(twoSum)
                .hintOpened(false)
                .hintOpenCount(0)
                .solved(true)
                .events(List.of())
                .build();

        ProblemSession s2 = ProblemSession.builder()
                .id(102L)
                .sessionId("sess-2")
                .user(testUserA)
                .problem(reverseList)
                .hintOpened(true)
                .hintOpenCount(1)
                .solved(true)
                .events(List.of(
                        SessionEvent.builder().eventType(SessionEventType.HINT_OPENED).hintName("Hint 1").timestamp(LocalDateTime.now()).build()
                ))
                .build();

        ProblemSession s3 = ProblemSession.builder()
                .id(103L)
                .sessionId("sess-3")
                .user(testUserA)
                .problem(mergeKLists)
                .hintOpened(true)
                .hintOpenCount(2)
                .solved(true)
                .events(List.of(
                        SessionEvent.builder().eventType(SessionEventType.HINT_OPENED).hintName("Hint 1").timestamp(LocalDateTime.now()).build(),
                        SessionEvent.builder().eventType(SessionEventType.HINT_OPENED).hintName("Hint 2").timestamp(LocalDateTime.now()).build()
                ))
                .build();

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(testUserA));
        when(sessionRepository.findByUserIdOrderBySessionStartedAtDesc(1L)).thenReturn(List.of(s1, s2, s3));

        HistoricalAnalyticsDTO result = historicalAnalyticsService.getHistoricalAnalytics(TimeWindow.ALL_TIME);

        assertEquals(3, result.getTotalProblemsSolved());
        // 2 of 3 used hints: 2/3 * 100 = 66.67%
        assertEquals(66.67, result.getHintUsageRate());
        // total hints: 0 + 1 + 2 = 3. avg: 3 / 3 = 1.0
        assertEquals(1.0, result.getAverageHintsPerProblem());
    }

    @Test
    @DisplayName("TEST 6: Solution usage rate calculation")
    void testSolutionUsageRate() {
        ProblemSession s1 = ProblemSession.builder()
                .id(101L)
                .sessionId("sess-1")
                .user(testUserA)
                .problem(twoSum)
                .solutionViewed(true)
                .solved(true)
                .events(List.of(
                        SessionEvent.builder().eventType(SessionEventType.SOLUTION_VIEWED).timestamp(LocalDateTime.now()).build()
                ))
                .build();

        ProblemSession s2 = ProblemSession.builder()
                .id(102L)
                .sessionId("sess-2")
                .user(testUserA)
                .problem(reverseList)
                .solutionViewed(false)
                .solved(true)
                .events(List.of())
                .build();

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(testUserA));
        when(sessionRepository.findByUserIdOrderBySessionStartedAtDesc(1L)).thenReturn(List.of(s1, s2));

        HistoricalAnalyticsDTO result = historicalAnalyticsService.getHistoricalAnalytics(TimeWindow.ALL_TIME);

        // 1 of 2 viewed solution = 50.0%
        assertEquals(50.0, result.getSolutionUsageRate());
    }

    @Test
    @DisplayName("TEST 7: Editorial usage rate calculation")
    void testEditorialUsageRate() {
        ProblemSession s1 = ProblemSession.builder()
                .id(101L)
                .sessionId("sess-1")
                .user(testUserA)
                .problem(twoSum)
                .editorialViewed(true)
                .solved(true)
                .events(List.of(
                        SessionEvent.builder().eventType(SessionEventType.EDITORIAL_VIEWED).timestamp(LocalDateTime.now()).build()
                ))
                .build();

        ProblemSession s2 = ProblemSession.builder()
                .id(102L)
                .sessionId("sess-2")
                .user(testUserA)
                .problem(reverseList)
                .editorialViewed(false)
                .solved(true)
                .events(List.of())
                .build();

        ProblemSession s3 = ProblemSession.builder()
                .id(103L)
                .sessionId("sess-3")
                .user(testUserA)
                .problem(mergeKLists)
                .editorialViewed(false)
                .solved(true)
                .events(List.of())
                .build();

        ProblemSession s4 = ProblemSession.builder()
                .id(104L)
                .sessionId("sess-4")
                .user(testUserA)
                .problem(twoSum)
                .editorialViewed(true)
                .solved(true)
                .events(List.of(
                        SessionEvent.builder().eventType(SessionEventType.EDITORIAL_VIEWED).timestamp(LocalDateTime.now()).build()
                ))
                .build();

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(testUserA));
        when(sessionRepository.findByUserIdOrderBySessionStartedAtDesc(1L)).thenReturn(List.of(s1, s2, s3, s4));

        HistoricalAnalyticsDTO result = historicalAnalyticsService.getHistoricalAnalytics(TimeWindow.ALL_TIME);

        // 2 of 4 viewed editorial = 50.0%
        assertEquals(50.0, result.getEditorialUsageRate());
    }

    @Test
    @DisplayName("TEST 8: Difficulty aggregation separates statistics for Easy, Medium, Hard")
    void testDifficultyAggregation() {
        Problem mediumProblem = Problem.builder()
                .id(40L)
                .leetcodeId(3)
                .title("Longest Substring")
                .difficulty(Difficulty.MEDIUM)
                .build();

        ProblemSession easySession = ProblemSession.builder()
                .id(101L)
                .sessionId("sess-easy")
                .user(testUserA)
                .problem(twoSum)
                .thinkingDuration(10000L)
                .codingDuration(30000L) // active: 40000
                .attempts(1)
                .solved(true)
                .events(List.of(
                        SessionEvent.builder().eventType(SessionEventType.SUBMISSION).result("ACCEPTED").timestamp(LocalDateTime.now()).build()
                ))
                .build();

        ProblemSession mediumSession = ProblemSession.builder()
                .id(102L)
                .sessionId("sess-medium")
                .user(testUserA)
                .problem(mediumProblem)
                .thinkingDuration(20000L)
                .codingDuration(60000L) // active: 80000
                .attempts(2)
                .solved(true)
                .events(List.of(
                        SessionEvent.builder().eventType(SessionEventType.SUBMISSION).result("WRONG_ANSWER").timestamp(LocalDateTime.now()).build(),
                        SessionEvent.builder().eventType(SessionEventType.SUBMISSION).result("ACCEPTED").timestamp(LocalDateTime.now()).build()
                ))
                .build();

        ProblemSession hardSession = ProblemSession.builder()
                .id(103L)
                .sessionId("sess-hard")
                .user(testUserA)
                .problem(mergeKLists)
                .thinkingDuration(50000L)
                .codingDuration(150000L) // active: 200000
                .attempts(3)
                .solved(true)
                .events(List.of(
                        SessionEvent.builder().eventType(SessionEventType.SUBMISSION).result("WRONG_ANSWER").timestamp(LocalDateTime.now()).build(),
                        SessionEvent.builder().eventType(SessionEventType.SUBMISSION).result("WRONG_ANSWER").timestamp(LocalDateTime.now()).build(),
                        SessionEvent.builder().eventType(SessionEventType.SUBMISSION).result("ACCEPTED").timestamp(LocalDateTime.now()).build()
                ))
                .build();

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(testUserA));
        when(sessionRepository.findByUserIdOrderBySessionStartedAtDesc(1L)).thenReturn(List.of(easySession, mediumSession, hardSession));

        HistoricalAnalyticsDTO result = historicalAnalyticsService.getHistoricalAnalytics(TimeWindow.ALL_TIME);

        assertEquals(3, result.getTotalProblemsSolved());
        assertEquals(1, result.getProblemsSolvedByDifficulty().get("EASY"));
        assertEquals(1, result.getProblemsSolvedByDifficulty().get("MEDIUM"));
        assertEquals(1, result.getProblemsSolvedByDifficulty().get("HARD"));

        DifficultyAnalyticsDTO easyStats = result.getDifficultyAnalytics().get("EASY");
        assertEquals(1, easyStats.getProblemsSolved());
        assertEquals(40000L, easyStats.getAverageSolveTime());
        assertEquals(1.0, easyStats.getAverageAttempts());
        assertEquals(100.0, easyStats.getFirstAttemptSuccessRate());

        DifficultyAnalyticsDTO mediumStats = result.getDifficultyAnalytics().get("MEDIUM");
        assertEquals(1, mediumStats.getProblemsSolved());
        assertEquals(80000L, mediumStats.getAverageSolveTime());
        assertEquals(2.0, mediumStats.getAverageAttempts());
        assertEquals(0.0, mediumStats.getFirstAttemptSuccessRate());

        DifficultyAnalyticsDTO hardStats = result.getDifficultyAnalytics().get("HARD");
        assertEquals(1, hardStats.getProblemsSolved());
        assertEquals(200000L, hardStats.getAverageSolveTime());
        assertEquals(3.0, hardStats.getAverageAttempts());
        assertEquals(0.0, hardStats.getFirstAttemptSuccessRate());
    }

    @Test
    @DisplayName("TEST 9: Multiple topics - problem belongs to multiple topics without inflating global count")
    void testMultipleTopicsAggregation() {
        // twoSum has tags: ["Array", "Hash Table"]
        Problem arrayOnlyProblem = Problem.builder()
                .id(50L)
                .leetcodeId(53)
                .title("Maximum Subarray")
                .difficulty(Difficulty.MEDIUM)
                .tags(new HashSet<>(Set.of(Tag.builder().name("Array").build())))
                .build();

        ProblemSession s1 = ProblemSession.builder()
                .id(101L)
                .sessionId("sess-1")
                .user(testUserA)
                .problem(twoSum) // Array, Hash Table
                .thinkingDuration(10000L)
                .codingDuration(30000L) // 40000
                .attempts(1)
                .solved(true)
                .events(List.of(
                        SessionEvent.builder().eventType(SessionEventType.SUBMISSION).result("ACCEPTED").timestamp(LocalDateTime.now()).build()
                ))
                .build();

        ProblemSession s2 = ProblemSession.builder()
                .id(102L)
                .sessionId("sess-2")
                .user(testUserA)
                .problem(arrayOnlyProblem) // Array
                .thinkingDuration(20000L)
                .codingDuration(40000L) // 60000
                .attempts(2)
                .solved(true)
                .events(List.of(
                        SessionEvent.builder().eventType(SessionEventType.SUBMISSION).result("WRONG_ANSWER").timestamp(LocalDateTime.now()).build(),
                        SessionEvent.builder().eventType(SessionEventType.SUBMISSION).result("ACCEPTED").timestamp(LocalDateTime.now()).build()
                ))
                .build();

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(testUserA));
        when(sessionRepository.findByUserIdOrderBySessionStartedAtDesc(1L)).thenReturn(List.of(s1, s2));

        HistoricalAnalyticsDTO result = historicalAnalyticsService.getHistoricalAnalytics(TimeWindow.ALL_TIME);

        // Global count is strictly 2
        assertEquals(2, result.getTotalProblemsSolved());

        // Topic: Array has 2 problems
        TopicAnalyticsDTO arrayTopic = result.getTopicAnalytics().get("Array");
        assertNotNull(arrayTopic);
        assertEquals(2, arrayTopic.getProblemsSolved());
        // avg solve time: (40000 + 60000) / 2 = 50000
        assertEquals(50000L, arrayTopic.getAverageSolveTime());
        // 1 of 2 first attempt: 50.0%
        assertEquals(50.0, arrayTopic.getFirstAttemptSuccessRate());

        // Topic: Hash Table has 1 problem
        TopicAnalyticsDTO hashTopic = result.getTopicAnalytics().get("Hash Table");
        assertNotNull(hashTopic);
        assertEquals(1, hashTopic.getProblemsSolved());
        assertEquals(40000L, hashTopic.getAverageSolveTime());
        assertEquals(100.0, hashTopic.getFirstAttemptSuccessRate());
    }

    @Test
    @DisplayName("TEST 10: Multiple sessions for same problem - each completed session is counted in history")
    void testMultipleSessionsForSameProblem() {
        ProblemSession s1 = ProblemSession.builder()
                .id(101L)
                .sessionId("sess-1-ts")
                .user(testUserA)
                .problem(twoSum)
                .attempts(2)
                .solved(true)
                .events(List.of())
                .build();

        ProblemSession s2 = ProblemSession.builder()
                .id(102L)
                .sessionId("sess-2-ts")
                .user(testUserA)
                .problem(twoSum)
                .attempts(1)
                .solved(true)
                .events(List.of(
                        SessionEvent.builder().eventType(SessionEventType.SUBMISSION).result("ACCEPTED").timestamp(LocalDateTime.now()).build()
                ))
                .build();

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(testUserA));
        when(sessionRepository.findByUserIdOrderBySessionStartedAtDesc(1L)).thenReturn(List.of(s1, s2));

        HistoricalAnalyticsDTO result = historicalAnalyticsService.getHistoricalAnalytics(TimeWindow.ALL_TIME);

        assertEquals(2, result.getTotalProblemsSolved());
        assertEquals(2, result.getTotalSessions());
    }

    @Test
    @DisplayName("TEST 11: User isolation - User A's query derives only User A's sessions")
    void testUserIsolation() {
        ProblemSession aliceSession = ProblemSession.builder()
                .id(101L)
                .sessionId("alice-sess")
                .user(testUserA)
                .problem(twoSum)
                .solved(true)
                .events(List.of())
                .build();

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(testUserA));
        when(sessionRepository.findByUserIdOrderBySessionStartedAtDesc(1L)).thenReturn(List.of(aliceSession));

        HistoricalAnalyticsDTO result = historicalAnalyticsService.getHistoricalAnalytics(TimeWindow.ALL_TIME);

        assertEquals(1, result.getTotalProblemsSolved());
        // Verify we only queried sessionRepository with User A's ID (1L)
        org.mockito.Mockito.verify(sessionRepository).findByUserIdOrderBySessionStartedAtDesc(1L);
        org.mockito.Mockito.verify(sessionRepository, org.mockito.Mockito.never()).findByUserIdOrderBySessionStartedAtDesc(2L);
    }

    @Test
    @DisplayName("TEST 12: Null optional values do not crash analytics computation")
    void testNullOptionalValues() {
        ProblemSession nullFieldsSession = ProblemSession.builder()
                .id(105L)
                .sessionId("null-sess")
                .user(testUserA)
                .problem(twoSum)
                .sessionStartedAt(null)
                .thinkingDuration(null)
                .codingDuration(null)
                .totalTimeAway(0L)
                .tabSwitchCount(0)
                .language(null)
                .hintOpened(false)
                .hintOpenCount(null)
                .solutionViewed(null)
                .editorialViewed(null)
                .attempts(null)
                .solved(true)
                .events(null)
                .build();

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(testUserA));
        when(sessionRepository.findByUserIdOrderBySessionStartedAtDesc(1L)).thenReturn(List.of(nullFieldsSession));

        HistoricalAnalyticsDTO result = historicalAnalyticsService.getHistoricalAnalytics(TimeWindow.ALL_TIME);

        assertNotNull(result);
        assertEquals(1, result.getTotalProblemsSolved());
        assertEquals(0L, result.getAverageSolveTime());
        assertEquals(0L, result.getAverageThinkingTime());
        assertEquals(0L, result.getAverageCodingTime());
    }

    @Test
    @DisplayName("TEST 13: Time window filtering (LAST_7_DAYS)")
    void testTimeWindowFiltering() {
        ProblemSession recent = ProblemSession.builder()
                .id(101L)
                .sessionId("recent-sess")
                .user(testUserA)
                .problem(twoSum)
                .sessionStartedAt(LocalDateTime.now().minusDays(2))
                .solved(true)
                .events(List.of())
                .build();

        ProblemSession old = ProblemSession.builder()
                .id(102L)
                .sessionId("old-sess")
                .user(testUserA)
                .problem(reverseList)
                .sessionStartedAt(LocalDateTime.now().minusDays(15))
                .solved(true)
                .events(List.of())
                .build();

        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(testUserA));
        when(sessionRepository.findByUserIdOrderBySessionStartedAtDesc(1L)).thenReturn(List.of(recent, old));

        HistoricalAnalyticsDTO allTime = historicalAnalyticsService.getHistoricalAnalytics(TimeWindow.ALL_TIME);
        assertEquals(2, allTime.getTotalProblemsSolved());

        HistoricalAnalyticsDTO last7Days = historicalAnalyticsService.getHistoricalAnalytics(TimeWindow.LAST_7_DAYS);
        assertEquals(1, last7Days.getTotalProblemsSolved());
    }
}
