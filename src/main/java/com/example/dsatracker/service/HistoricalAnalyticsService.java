package com.example.dsatracker.service;

import com.example.dsatracker.dto.*;
import com.example.dsatracker.exception.ResourceNotFoundException;
import com.example.dsatracker.model.ProblemSession;
import com.example.dsatracker.model.Tag;
import com.example.dsatracker.model.User;
import com.example.dsatracker.repository.ProblemSessionRepository;
import com.example.dsatracker.repository.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class HistoricalAnalyticsService {

    private final ProblemSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final SessionAnalyticsService sessionAnalyticsService;

    public HistoricalAnalyticsService(
            ProblemSessionRepository sessionRepository,
            UserRepository userRepository,
            SessionAnalyticsService sessionAnalyticsService) {
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.sessionAnalyticsService = sessionAnalyticsService;
    }

    @Transactional(readOnly = true)
    public HistoricalAnalyticsDTO getHistoricalAnalytics(TimeWindow timeWindow) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));

        List<ProblemSession> sessions = sessionRepository.findByUserIdOrderBySessionStartedAtDesc(user.getId());

        if (timeWindow != null && timeWindow != TimeWindow.ALL_TIME) {
            LocalDateTime cutoff;
            LocalDateTime now = LocalDateTime.now();
            switch (timeWindow) {
                case LAST_7_DAYS -> cutoff = now.minusDays(7);
                case LAST_30_DAYS -> cutoff = now.minusDays(30);
                case LAST_90_DAYS -> cutoff = now.minusDays(90);
                default -> cutoff = null;
            }
            if (cutoff != null) {
                sessions = sessions.stream()
                        .filter(s -> s.getSessionStartedAt() != null && !s.getSessionStartedAt().isBefore(cutoff))
                        .toList();
            }
        }

        return calculateHistoricalAnalytics(sessions);
    }

    public HistoricalAnalyticsDTO calculateHistoricalAnalytics(List<ProblemSession> sessions) {
        if (sessions == null || sessions.isEmpty()) {
            return HistoricalAnalyticsDTO.builder()
                    .totalProblemsSolved(0)
                    .totalSessions(0)
                    .averageSolveTime(0L)
                    .averageThinkingTime(0L)
                    .averageCodingTime(0L)
                    .averageAttempts(0.0)
                    .firstAttemptSuccessRate(0.0)
                    .totalWrongSubmissions(0)
                    .totalAcceptedSubmissions(0)
                    .hintUsageRate(0.0)
                    .solutionUsageRate(0.0)
                    .editorialUsageRate(0.0)
                    .averageHintsPerProblem(0.0)
                    .totalTimeSpent(0L)
                    .problemsSolvedByDifficulty(new HashMap<>())
                    .difficultyAnalytics(new HashMap<>())
                    .topicAnalytics(new HashMap<>())
                    .build();
        }

        int totalSessions = sessions.size();
        long totalTimeSpentAll = 0L;
        int totalWrongAll = 0;
        int totalAcceptedAll = 0;

        List<SessionWithAnalytics> solvedSessions = new ArrayList<>();

        for (ProblemSession session : sessions) {
            SessionAnalyticsDTO a = sessionAnalyticsService.computeAnalytics(session);
            totalTimeSpentAll += (a.getTotalActiveTime() != null ? a.getTotalActiveTime() : 0L);
            totalWrongAll += (a.getWrongSubmissionCount() != null ? a.getWrongSubmissionCount() : 0);
            totalAcceptedAll += (a.getAcceptedSubmissionCount() != null ? a.getAcceptedSubmissionCount() : 0);

            if (Boolean.TRUE.equals(a.getSolved())) {
                solvedSessions.add(new SessionWithAnalytics(session, a));
            }
        }

        int totalProblemsSolved = solvedSessions.size();
        if (totalProblemsSolved == 0) {
            return HistoricalAnalyticsDTO.builder()
                    .totalProblemsSolved(0)
                    .totalSessions(totalSessions)
                    .averageSolveTime(0L)
                    .averageThinkingTime(0L)
                    .averageCodingTime(0L)
                    .averageAttempts(0.0)
                    .firstAttemptSuccessRate(0.0)
                    .totalWrongSubmissions(totalWrongAll)
                    .totalAcceptedSubmissions(totalAcceptedAll)
                    .hintUsageRate(0.0)
                    .solutionUsageRate(0.0)
                    .editorialUsageRate(0.0)
                    .averageHintsPerProblem(0.0)
                    .totalTimeSpent(totalTimeSpentAll)
                    .problemsSolvedByDifficulty(new HashMap<>())
                    .difficultyAnalytics(new HashMap<>())
                    .topicAnalytics(new HashMap<>())
                    .build();
        }

        long sumSolveTime = 0L;
        long sumThinkingTime = 0L;
        long sumCodingTime = 0L;
        long sumAttempts = 0L;
        int firstAttemptAcceptedCount = 0;
        int hintUsedCount = 0;
        int solutionViewedCount = 0;
        int editorialViewedCount = 0;
        int sumHints = 0;

        Map<String, List<SessionWithAnalytics>> difficultyMap = new LinkedHashMap<>();
        Map<String, List<SessionWithAnalytics>> topicMap = new LinkedHashMap<>();

        for (SessionWithAnalytics swa : solvedSessions) {
            SessionAnalyticsDTO a = swa.analytics;
            ProblemSession s = swa.session;

            long solveTime = a.getTotalActiveTime() != null ? a.getTotalActiveTime() : 0L;
            long thinking = a.getThinkingTime() != null ? a.getThinkingTime() : 0L;
            long coding = a.getCodingTime() != null ? a.getCodingTime() : 0L;
            int attempts = a.getAttempts() != null ? a.getAttempts() : 0;

            sumSolveTime += solveTime;
            sumThinkingTime += thinking;
            sumCodingTime += coding;
            sumAttempts += attempts;

            if (Boolean.TRUE.equals(a.getFirstAttemptAccepted())) {
                firstAttemptAcceptedCount++;
            }
            if (Boolean.TRUE.equals(a.getHintUsed())) {
                hintUsedCount++;
            }
            if (Boolean.TRUE.equals(a.getSolutionViewed())) {
                solutionViewedCount++;
            }
            if (Boolean.TRUE.equals(a.getEditorialViewed())) {
                editorialViewedCount++;
            }
            sumHints += (a.getHintCount() != null ? a.getHintCount() : 0);

            // Group by difficulty
            if (s.getProblem() != null && s.getProblem().getDifficulty() != null) {
                String diff = s.getProblem().getDifficulty().name();
                difficultyMap.computeIfAbsent(diff, k -> new ArrayList<>()).add(swa);
            }

            // Group by topic/tags
            if (s.getProblem() != null && s.getProblem().getTags() != null) {
                for (Tag tag : s.getProblem().getTags()) {
                    if (tag.getName() != null && !tag.getName().isBlank()) {
                        topicMap.computeIfAbsent(tag.getName(), k -> new ArrayList<>()).add(swa);
                    }
                }
            }
        }

        long avgSolveTime = Math.round((double) sumSolveTime / totalProblemsSolved);
        long avgThinkingTime = Math.round((double) sumThinkingTime / totalProblemsSolved);
        long avgCodingTime = Math.round((double) sumCodingTime / totalProblemsSolved);
        double avgAttempts = roundToTwoDecimals((double) sumAttempts / totalProblemsSolved);

        double firstAttemptSuccessRate = roundToTwoDecimals(((double) firstAttemptAcceptedCount / totalProblemsSolved) * 100.0);
        double hintUsageRate = roundToTwoDecimals(((double) hintUsedCount / totalProblemsSolved) * 100.0);
        double solutionUsageRate = roundToTwoDecimals(((double) solutionViewedCount / totalProblemsSolved) * 100.0);
        double editorialUsageRate = roundToTwoDecimals(((double) editorialViewedCount / totalProblemsSolved) * 100.0);
        double avgHintsPerProblem = roundToTwoDecimals((double) sumHints / totalProblemsSolved);

        // Build difficulty analytics
        Map<String, Integer> problemsSolvedByDifficulty = new HashMap<>();
        Map<String, DifficultyAnalyticsDTO> difficultyAnalytics = new HashMap<>();

        for (Map.Entry<String, List<SessionWithAnalytics>> entry : difficultyMap.entrySet()) {
            String diff = entry.getKey();
            List<SessionWithAnalytics> diffSessions = entry.getValue();
            int count = diffSessions.size();
            problemsSolvedByDifficulty.put(diff, count);

            long diffSumSolveTime = 0L;
            long diffSumAttempts = 0L;
            int diffFirstAttemptSuccess = 0;

            for (SessionWithAnalytics dswa : diffSessions) {
                diffSumSolveTime += (dswa.analytics.getTotalActiveTime() != null ? dswa.analytics.getTotalActiveTime() : 0L);
                diffSumAttempts += (dswa.analytics.getAttempts() != null ? dswa.analytics.getAttempts() : 0);
                if (Boolean.TRUE.equals(dswa.analytics.getFirstAttemptAccepted())) {
                    diffFirstAttemptSuccess++;
                }
            }

            long diffAvgSolveTime = Math.round((double) diffSumSolveTime / count);
            double diffAvgAttempts = roundToTwoDecimals((double) diffSumAttempts / count);
            double diffSuccessRate = roundToTwoDecimals(((double) diffFirstAttemptSuccess / count) * 100.0);

            difficultyAnalytics.put(diff, DifficultyAnalyticsDTO.builder()
                    .problemsSolved(count)
                    .averageSolveTime(diffAvgSolveTime)
                    .averageAttempts(diffAvgAttempts)
                    .firstAttemptSuccessRate(diffSuccessRate)
                    .build());
        }

        // Build topic analytics
        Map<String, TopicAnalyticsDTO> topicAnalytics = new HashMap<>();
        for (Map.Entry<String, List<SessionWithAnalytics>> entry : topicMap.entrySet()) {
            String topic = entry.getKey();
            List<SessionWithAnalytics> topicSessions = entry.getValue();
            int count = topicSessions.size();

            long topicSumSolveTime = 0L;
            int topicFirstAttemptSuccess = 0;

            for (SessionWithAnalytics tswa : topicSessions) {
                topicSumSolveTime += (tswa.analytics.getTotalActiveTime() != null ? tswa.analytics.getTotalActiveTime() : 0L);
                if (Boolean.TRUE.equals(tswa.analytics.getFirstAttemptAccepted())) {
                    topicFirstAttemptSuccess++;
                }
            }

            long topicAvgSolveTime = Math.round((double) topicSumSolveTime / count);
            double topicSuccessRate = roundToTwoDecimals(((double) topicFirstAttemptSuccess / count) * 100.0);

            topicAnalytics.put(topic, TopicAnalyticsDTO.builder()
                    .problemsSolved(count)
                    .averageSolveTime(topicAvgSolveTime)
                    .firstAttemptSuccessRate(topicSuccessRate)
                    .build());
        }

        return HistoricalAnalyticsDTO.builder()
                .totalProblemsSolved(totalProblemsSolved)
                .totalSessions(totalSessions)
                .averageSolveTime(avgSolveTime)
                .averageThinkingTime(avgThinkingTime)
                .averageCodingTime(avgCodingTime)
                .averageAttempts(avgAttempts)
                .firstAttemptSuccessRate(firstAttemptSuccessRate)
                .totalWrongSubmissions(totalWrongAll)
                .totalAcceptedSubmissions(totalAcceptedAll)
                .hintUsageRate(hintUsageRate)
                .solutionUsageRate(solutionUsageRate)
                .editorialUsageRate(editorialUsageRate)
                .averageHintsPerProblem(avgHintsPerProblem)
                .totalTimeSpent(totalTimeSpentAll)
                .problemsSolvedByDifficulty(problemsSolvedByDifficulty)
                .difficultyAnalytics(difficultyAnalytics)
                .topicAnalytics(topicAnalytics)
                .build();
    }

    private double roundToTwoDecimals(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static class SessionWithAnalytics {
        final ProblemSession session;
        final SessionAnalyticsDTO analytics;

        SessionWithAnalytics(ProblemSession session, SessionAnalyticsDTO analytics) {
            this.session = session;
            this.analytics = analytics;
        }
    }
}
