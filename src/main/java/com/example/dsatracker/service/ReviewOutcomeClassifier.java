package com.example.dsatracker.service;

import com.example.dsatracker.dto.SessionAnalyticsDTO;
import com.example.dsatracker.model.ProblemSession;
import com.example.dsatracker.model.ReviewOutcome;
import org.springframework.stereotype.Service;

@Service
public class ReviewOutcomeClassifier {

    public ReviewOutcome classifyOutcome(
            ProblemSession session,
            SessionAnalyticsDTO analytics,
            double actualRecallIntervalDays,
            double retentionRatio,
            double efficiencyModifier,
            double awayReliabilityWeight) {

        boolean isSolved = Boolean.TRUE.equals(analytics.getSolved());
        boolean solutionViewed = Boolean.TRUE.equals(analytics.getSolutionViewed());
        boolean editorialViewed = Boolean.TRUE.equals(analytics.getEditorialViewed());
        boolean hintUsed = Boolean.TRUE.equals(analytics.getHintUsed());
        int hintCount = analytics.getHintCount() != null ? analytics.getHintCount() : 0;
        int attempts = analytics.getAttempts() != null ? analytics.getAttempts() : (session.getAttempts() != null ? session.getAttempts() : 1);
        int wrongSubmissions = analytics.getWrongSubmissionCount() != null ? analytics.getWrongSubmissionCount() : 0;

        long thinkingTime = analytics.getThinkingTime() != null ? analytics.getThinkingTime() : 0L;
        long codingTime = analytics.getCodingTime() != null ? analytics.getCodingTime() : 0L;
        long activeSolvingTime = thinkingTime + codingTime;
        long timeAway = analytics.getTimeAway() != null ? analytics.getTimeAway() : 0L;
        double awayRatio = (activeSolvingTime + timeAway > 0)
                ? ((double) timeAway / (activeSolvingTime + timeAway))
                : 0.0;

        // 1. AGAIN precedence: Unsolved, or solution/editorial required
        if (!isSolved || solutionViewed || editorialViewed) {
            return ReviewOutcome.AGAIN;
        }

        // 2. HARD precedence: Substantial struggle, multiple hints, multiple attempts, significant slowdown, or very high away dampening
        if (hintCount >= 2 || attempts >= 3 || wrongSubmissions >= 2 || efficiencyModifier <= -5.0 || awayReliabilityWeight <= 0.65) {
            return ReviewOutcome.HARD;
        }

        // 3. EASY precedence: Independent recall, first attempt, low away ratio, strong retention or efficiency improvement
        boolean isIndependent = !hintUsed && !solutionViewed && !editorialViewed;
        boolean isFirstAttempt = attempts <= 1 && wrongSubmissions == 0;
        boolean isLowAway = awayRatio <= 0.15;
        boolean hasStrongRetentionOrEfficiency = (actualRecallIntervalDays >= 7.0 || retentionRatio >= 1.0) || (efficiencyModifier >= 5.0);

        if (isIndependent && isFirstAttempt && isLowAway && hasStrongRetentionOrEfficiency) {
            return ReviewOutcome.EASY;
        }

        // 4. GOOD: Default for normal successful recall
        return ReviewOutcome.GOOD;
    }
}
