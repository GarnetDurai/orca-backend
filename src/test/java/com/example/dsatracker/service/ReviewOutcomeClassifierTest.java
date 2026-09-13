package com.example.dsatracker.service;

import com.example.dsatracker.dto.SessionAnalyticsDTO;
import com.example.dsatracker.model.ProblemSession;
import com.example.dsatracker.model.ReviewOutcome;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReviewOutcomeClassifierTest {

    private ReviewOutcomeClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new ReviewOutcomeClassifier();
    }

    private ProblemSession buildSession(int attempts, boolean solved) {
        return ProblemSession.builder()
                .attempts(attempts)
                .solved(solved)
                .build();
    }

    private SessionAnalyticsDTO.SessionAnalyticsDTOBuilder baseAnalytics(boolean solved) {
        return SessionAnalyticsDTO.builder()
                .solved(solved)
                .attempts(1)
                .wrongSubmissionCount(0)
                .hintUsed(false)
                .hintCount(0)
                .solutionViewed(false)
                .editorialViewed(false)
                .thinkingTime(60000L)
                .codingTime(120000L)
                .timeAway(0L);
    }

    @Test
    @DisplayName("AGAIN: Unsolved session")
    void testAgainUnsolved() {
        ProblemSession session = buildSession(2, false);
        SessionAnalyticsDTO analytics = baseAnalytics(false).build();

        ReviewOutcome outcome = classifier.classifyOutcome(session, analytics, 5.0, 1.0, 0.0, 1.0);
        assertEquals(ReviewOutcome.AGAIN, outcome);
    }

    @Test
    @DisplayName("AGAIN: Solution viewed")
    void testAgainSolutionViewed() {
        ProblemSession session = buildSession(1, true);
        SessionAnalyticsDTO analytics = baseAnalytics(true).solutionViewed(true).build();

        ReviewOutcome outcome = classifier.classifyOutcome(session, analytics, 5.0, 1.0, 0.0, 1.0);
        assertEquals(ReviewOutcome.AGAIN, outcome);
    }

    @Test
    @DisplayName("AGAIN: Editorial viewed")
    void testAgainEditorialViewed() {
        ProblemSession session = buildSession(1, true);
        SessionAnalyticsDTO analytics = baseAnalytics(true).editorialViewed(true).build();

        ReviewOutcome outcome = classifier.classifyOutcome(session, analytics, 5.0, 1.0, 0.0, 1.0);
        assertEquals(ReviewOutcome.AGAIN, outcome);
    }

    @Test
    @DisplayName("HARD: Multiple hints used")
    void testHardMultipleHints() {
        ProblemSession session = buildSession(1, true);
        SessionAnalyticsDTO analytics = baseAnalytics(true).hintUsed(true).hintCount(2).build();

        ReviewOutcome outcome = classifier.classifyOutcome(session, analytics, 5.0, 1.0, 0.0, 1.0);
        assertEquals(ReviewOutcome.HARD, outcome);
    }

    @Test
    @DisplayName("HARD: Multiple attempts with struggle (attempts >= 3)")
    void testHardMultipleAttempts() {
        ProblemSession session = buildSession(3, true);
        SessionAnalyticsDTO analytics = baseAnalytics(true).attempts(3).wrongSubmissionCount(2).build();

        ReviewOutcome outcome = classifier.classifyOutcome(session, analytics, 5.0, 1.0, 0.0, 1.0);
        assertEquals(ReviewOutcome.HARD, outcome);
    }

    @Test
    @DisplayName("HARD: Significant slowdown relative to previous solve")
    void testHardSlowdown() {
        ProblemSession session = buildSession(1, true);
        SessionAnalyticsDTO analytics = baseAnalytics(true).build();

        ReviewOutcome outcome = classifier.classifyOutcome(session, analytics, 5.0, 1.0, -5.0, 1.0);
        assertEquals(ReviewOutcome.HARD, outcome);
    }

    @Test
    @DisplayName("HARD: Very high away-time reliability reduction")
    void testHardHighAwayTime() {
        ProblemSession session = buildSession(1, true);
        SessionAnalyticsDTO analytics = baseAnalytics(true).timeAway(300000L).build(); // 300s away, 180s active -> awayRatio = 0.625

        ReviewOutcome outcome = classifier.classifyOutcome(session, analytics, 5.0, 1.0, 0.0, 0.50);
        assertEquals(ReviewOutcome.HARD, outcome);
    }

    @Test
    @DisplayName("EASY: Independent first attempt with strong retention (>= 7 days)")
    void testEasyStrongRetention() {
        ProblemSession session = buildSession(1, true);
        SessionAnalyticsDTO analytics = baseAnalytics(true).build();

        ReviewOutcome outcome = classifier.classifyOutcome(session, analytics, 10.0, 1.2, 0.0, 1.0);
        assertEquals(ReviewOutcome.EASY, outcome);
    }

    @Test
    @DisplayName("EASY: Independent first attempt with efficiency improvement (+5)")
    void testEasyEfficiencyImprovement() {
        ProblemSession session = buildSession(1, true);
        SessionAnalyticsDTO analytics = baseAnalytics(true).build();

        ReviewOutcome outcome = classifier.classifyOutcome(session, analytics, 3.0, 0.8, 5.0, 1.0);
        assertEquals(ReviewOutcome.EASY, outcome);
    }

    @Test
    @DisplayName("GOOD: Normal independent performance without EASY triggers")
    void testGoodStandardRecall() {
        ProblemSession session = buildSession(2, true);
        SessionAnalyticsDTO analytics = baseAnalytics(true).attempts(2).wrongSubmissionCount(1).build();

        ReviewOutcome outcome = classifier.classifyOutcome(session, analytics, 3.0, 0.8, 0.0, 1.0);
        assertEquals(ReviewOutcome.GOOD, outcome);
    }

    @Test
    @DisplayName("GOOD: Single hint with clean first attempt")
    void testGoodSingleHint() {
        ProblemSession session = buildSession(1, true);
        SessionAnalyticsDTO analytics = baseAnalytics(true).hintUsed(true).hintCount(1).build();

        ReviewOutcome outcome = classifier.classifyOutcome(session, analytics, 5.0, 1.0, 0.0, 1.0);
        assertEquals(ReviewOutcome.GOOD, outcome);
    }
}
