package com.example.dsatracker.service;

import com.example.dsatracker.dto.*;
import com.example.dsatracker.exception.ResourceNotFoundException;
import com.example.dsatracker.model.Problem;
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
public class UserPerformanceProfileService {

    private final ProblemSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final SessionAnalyticsService sessionAnalyticsService;

    public UserPerformanceProfileService(
            ProblemSessionRepository sessionRepository,
            UserRepository userRepository,
            SessionAnalyticsService sessionAnalyticsService) {
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.sessionAnalyticsService = sessionAnalyticsService;
    }

    @Transactional(readOnly = true)
    public UserPerformanceProfileDTO getUserProfile() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));

        List<ProblemSession> sessions = sessionRepository.findByUserIdOrderBySessionStartedAtDesc(user.getId());
        return buildProfile(sessions, LocalDateTime.now());
    }

    public UserPerformanceProfileDTO buildProfile(List<ProblemSession> sessions) {
        return buildProfile(sessions, LocalDateTime.now());
    }

    public UserPerformanceProfileDTO buildProfile(List<ProblemSession> sessions, LocalDateTime now) {
        if (sessions == null || sessions.isEmpty()) {
            return buildEmptyProfile(now);
        }

        List<SessionWithAnalytics> allSessions = new ArrayList<>();
        List<SessionWithAnalytics> solvedSessions = new ArrayList<>();
        Set<Long> uniqueSolvedProblemIds = new HashSet<>();

        long totalTimeSpentAll = 0L;
        int totalWrongSubmissionsAll = 0;
        int totalAcceptedSubmissionsAll = 0;

        for (ProblemSession session : sessions) {
            SessionAnalyticsDTO a = sessionAnalyticsService.computeAnalytics(session);
            SessionWithAnalytics swa = new SessionWithAnalytics(session, a);
            allSessions.add(swa);

            totalTimeSpentAll += (a.getTotalActiveTime() != null ? a.getTotalActiveTime() : 0L);
            totalWrongSubmissionsAll += (a.getWrongSubmissionCount() != null ? a.getWrongSubmissionCount() : 0);
            totalAcceptedSubmissionsAll += (a.getAcceptedSubmissionCount() != null ? a.getAcceptedSubmissionCount() : 0);

            if (Boolean.TRUE.equals(a.getSolved())) {
                solvedSessions.add(swa);
                if (session.getProblem() != null && session.getProblem().getId() != null) {
                    uniqueSolvedProblemIds.add(session.getProblem().getId());
                }
            }
        }

        int totalSessions = allSessions.size();
        int totalSolvedSessions = solvedSessions.size();
        int uniqueProblemsSolved = uniqueSolvedProblemIds.size();
        int totalSubmissions = totalWrongSubmissionsAll + totalAcceptedSubmissionsAll;

        // Overall averages & rates
        long sumSolveTime = 0L;
        long sumThinkingTime = 0L;
        long sumCodingTime = 0L;
        long sumAttempts = 0L;
        int firstAttemptAcceptedCount = 0;
        int hintUsedCount = 0;
        int solutionViewedCount = 0;
        int editorialViewedCount = 0;

        for (SessionWithAnalytics swa : solvedSessions) {
            SessionAnalyticsDTO a = swa.analytics;
            sumSolveTime += (a.getTotalActiveTime() != null ? a.getTotalActiveTime() : 0L);
            sumThinkingTime += (a.getThinkingTime() != null ? a.getThinkingTime() : 0L);
            sumCodingTime += (a.getCodingTime() != null ? a.getCodingTime() : 0L);
            sumAttempts += (a.getAttempts() != null ? a.getAttempts() : 0);

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
        }

        long avgSolveTime = totalSolvedSessions > 0 ? Math.round((double) sumSolveTime / totalSolvedSessions) : 0L;
        long avgThinkingTime = totalSolvedSessions > 0 ? Math.round((double) sumThinkingTime / totalSolvedSessions) : 0L;
        long avgCodingTime = totalSolvedSessions > 0 ? Math.round((double) sumCodingTime / totalSolvedSessions) : 0L;
        double avgAttempts = totalSolvedSessions > 0 ? roundToTwoDecimals((double) sumAttempts / totalSolvedSessions) : 0.0;

        double firstAttemptSuccessRate = totalSolvedSessions > 0
                ? roundToTwoDecimals(((double) firstAttemptAcceptedCount / totalSolvedSessions) * 100.0)
                : 0.0;
        double hintUsageRate = totalSolvedSessions > 0
                ? roundToTwoDecimals(((double) hintUsedCount / totalSolvedSessions) * 100.0)
                : 0.0;
        double solutionUsageRate = totalSolvedSessions > 0
                ? roundToTwoDecimals(((double) solutionViewedCount / totalSolvedSessions) * 100.0)
                : 0.0;
        double editorialUsageRate = totalSolvedSessions > 0
                ? roundToTwoDecimals(((double) editorialViewedCount / totalSolvedSessions) * 100.0)
                : 0.0;

        ProfileOverallDTO overall = ProfileOverallDTO.builder()
                .uniqueProblemsSolved(uniqueProblemsSolved)
                .totalSolvedSessions(totalSolvedSessions)
                .totalSessions(totalSessions)
                .totalSubmissions(totalSubmissions)
                .averageAttempts(avgAttempts)
                .firstAttemptSuccessRate(firstAttemptSuccessRate)
                .averageSolveTime(avgSolveTime)
                .averageThinkingTime(avgThinkingTime)
                .averageCodingTime(avgCodingTime)
                .totalTimeSpent(totalTimeSpentAll)
                .hintUsageRate(hintUsageRate)
                .solutionUsageRate(solutionUsageRate)
                .editorialUsageRate(editorialUsageRate)
                .build();

        // Difficulty Performance
        Map<String, List<SessionWithAnalytics>> allByDiff = new LinkedHashMap<>();
        Map<String, List<SessionWithAnalytics>> solvedByDiff = new LinkedHashMap<>();

        for (SessionWithAnalytics swa : allSessions) {
            Problem p = swa.session.getProblem();
            if (p != null && p.getDifficulty() != null) {
                String diffName = p.getDifficulty().name();
                allByDiff.computeIfAbsent(diffName, k -> new ArrayList<>()).add(swa);
                if (Boolean.TRUE.equals(swa.analytics.getSolved())) {
                    solvedByDiff.computeIfAbsent(diffName, k -> new ArrayList<>()).add(swa);
                }
            }
        }

        Map<String, DifficultyProfileDTO> difficultyPerformance = new LinkedHashMap<>();
        for (Map.Entry<String, List<SessionWithAnalytics>> entry : allByDiff.entrySet()) {
            String diffName = entry.getKey();
            List<SessionWithAnalytics> diffAll = entry.getValue();
            List<SessionWithAnalytics> diffSolved = solvedByDiff.getOrDefault(diffName, Collections.emptyList());

            int diffSessionCount = diffAll.size();
            int diffSolvedCount = diffSolved.size();
            Set<Long> diffUniqueProblems = new HashSet<>();

            long diffSumSolveTime = 0L;
            long diffSumAttempts = 0L;
            int diffFirstAttemptSuccess = 0;
            int diffHintUsed = 0;
            int diffSolutionViewed = 0;
            int diffEditorialViewed = 0;

            for (SessionWithAnalytics dswa : diffSolved) {
                if (dswa.session.getProblem() != null && dswa.session.getProblem().getId() != null) {
                    diffUniqueProblems.add(dswa.session.getProblem().getId());
                }
                diffSumSolveTime += (dswa.analytics.getTotalActiveTime() != null ? dswa.analytics.getTotalActiveTime() : 0L);
                diffSumAttempts += (dswa.analytics.getAttempts() != null ? dswa.analytics.getAttempts() : 0);
                if (Boolean.TRUE.equals(dswa.analytics.getFirstAttemptAccepted())) {
                    diffFirstAttemptSuccess++;
                }
                if (Boolean.TRUE.equals(dswa.analytics.getHintUsed())) {
                    diffHintUsed++;
                }
                if (Boolean.TRUE.equals(dswa.analytics.getSolutionViewed())) {
                    diffSolutionViewed++;
                }
                if (Boolean.TRUE.equals(dswa.analytics.getEditorialViewed())) {
                    diffEditorialViewed++;
                }
            }

            long diffAvgSolveTime = diffSolvedCount > 0 ? Math.round((double) diffSumSolveTime / diffSolvedCount) : 0L;
            double diffAvgAttempts = diffSolvedCount > 0 ? roundToTwoDecimals((double) diffSumAttempts / diffSolvedCount) : 0.0;
            double diffSuccessRate = diffSolvedCount > 0 ? roundToTwoDecimals(((double) diffFirstAttemptSuccess / diffSolvedCount) * 100.0) : 0.0;
            double diffHintRate = diffSolvedCount > 0 ? roundToTwoDecimals(((double) diffHintUsed / diffSolvedCount) * 100.0) : 0.0;
            double diffSolutionRate = diffSolvedCount > 0 ? roundToTwoDecimals(((double) diffSolutionViewed / diffSolvedCount) * 100.0) : 0.0;
            double diffEditorialRate = diffSolvedCount > 0 ? roundToTwoDecimals(((double) diffEditorialViewed / diffSolvedCount) * 100.0) : 0.0;

            difficultyPerformance.put(diffName, DifficultyProfileDTO.builder()
                    .uniqueProblemsSolved(diffUniqueProblems.size())
                    .totalSolvedSessions(diffSolvedCount)
                    .sessionCount(diffSessionCount)
                    .averageSolveTime(diffAvgSolveTime)
                    .averageAttempts(diffAvgAttempts)
                    .firstAttemptSuccessRate(diffSuccessRate)
                    .hintUsageRate(diffHintRate)
                    .solutionUsageRate(diffSolutionRate)
                    .editorialUsageRate(diffEditorialRate)
                    .build());
        }

        // Topic Performance
        Map<String, List<SessionWithAnalytics>> allByTopic = new LinkedHashMap<>();
        Map<String, List<SessionWithAnalytics>> solvedByTopic = new LinkedHashMap<>();

        for (SessionWithAnalytics swa : allSessions) {
            Problem p = swa.session.getProblem();
            if (p != null && p.getTags() != null) {
                for (Tag tag : p.getTags()) {
                    if (tag.getName() != null && !tag.getName().isBlank()) {
                        String tagName = tag.getName();
                        allByTopic.computeIfAbsent(tagName, k -> new ArrayList<>()).add(swa);
                        if (Boolean.TRUE.equals(swa.analytics.getSolved())) {
                            solvedByTopic.computeIfAbsent(tagName, k -> new ArrayList<>()).add(swa);
                        }
                    }
                }
            }
        }

        Map<String, TopicProfileDTO> topicPerformance = new LinkedHashMap<>();
        for (Map.Entry<String, List<SessionWithAnalytics>> entry : allByTopic.entrySet()) {
            String tagName = entry.getKey();
            List<SessionWithAnalytics> tagAll = entry.getValue();
            List<SessionWithAnalytics> tagSolved = solvedByTopic.getOrDefault(tagName, Collections.emptyList());

            int tagSessionCount = tagAll.size();
            int tagSolvedCount = tagSolved.size();
            Set<Long> tagUniqueProblems = new HashSet<>();

            long tagSumSolveTime = 0L;
            long tagSumAttempts = 0L;
            int tagFirstAttemptSuccess = 0;
            int tagHintUsed = 0;
            int tagSolutionViewed = 0;
            int tagEditorialViewed = 0;

            for (SessionWithAnalytics tswa : tagSolved) {
                if (tswa.session.getProblem() != null && tswa.session.getProblem().getId() != null) {
                    tagUniqueProblems.add(tswa.session.getProblem().getId());
                }
                tagSumSolveTime += (tswa.analytics.getTotalActiveTime() != null ? tswa.analytics.getTotalActiveTime() : 0L);
                tagSumAttempts += (tswa.analytics.getAttempts() != null ? tswa.analytics.getAttempts() : 0);
                if (Boolean.TRUE.equals(tswa.analytics.getFirstAttemptAccepted())) {
                    tagFirstAttemptSuccess++;
                }
                if (Boolean.TRUE.equals(tswa.analytics.getHintUsed())) {
                    tagHintUsed++;
                }
                if (Boolean.TRUE.equals(tswa.analytics.getSolutionViewed())) {
                    tagSolutionViewed++;
                }
                if (Boolean.TRUE.equals(tswa.analytics.getEditorialViewed())) {
                    tagEditorialViewed++;
                }
            }

            long tagAvgSolveTime = tagSolvedCount > 0 ? Math.round((double) tagSumSolveTime / tagSolvedCount) : 0L;
            double tagAvgAttempts = tagSolvedCount > 0 ? roundToTwoDecimals((double) tagSumAttempts / tagSolvedCount) : 0.0;
            double tagSuccessRate = tagSolvedCount > 0 ? roundToTwoDecimals(((double) tagFirstAttemptSuccess / tagSolvedCount) * 100.0) : 0.0;
            double tagHintRate = tagSolvedCount > 0 ? roundToTwoDecimals(((double) tagHintUsed / tagSolvedCount) * 100.0) : 0.0;
            double tagSolutionRate = tagSolvedCount > 0 ? roundToTwoDecimals(((double) tagSolutionViewed / tagSolvedCount) * 100.0) : 0.0;
            double tagEditorialRate = tagSolvedCount > 0 ? roundToTwoDecimals(((double) tagEditorialViewed / tagSolvedCount) * 100.0) : 0.0;

            topicPerformance.put(tagName, TopicProfileDTO.builder()
                    .topic(tagName)
                    .uniqueProblemsSolved(tagUniqueProblems.size())
                    .totalSolvedSessions(tagSolvedCount)
                    .sessionCount(tagSessionCount)
                    .averageSolveTime(tagAvgSolveTime)
                    .averageAttempts(tagAvgAttempts)
                    .firstAttemptSuccessRate(tagSuccessRate)
                    .hintUsageRate(tagHintRate)
                    .solutionUsageRate(tagSolutionRate)
                    .editorialUsageRate(tagEditorialRate)
                    .build());
        }

        // Recent Trends (LAST_7_DAYS vs PRECEDING_7_DAYS, LAST_30_DAYS vs PRECEDING_30_DAYS)
        TrendComparisonDTO trend7Days = computeTrendComparison(allSessions, "LAST_7_DAYS", 7, now);
        TrendComparisonDTO trend30Days = computeTrendComparison(allSessions, "LAST_30_DAYS", 30, now);

        RecentTrendsDTO recentTrends = RecentTrendsDTO.builder()
                .last7Days(trend7Days)
                .last30Days(trend30Days)
                .build();

        return UserPerformanceProfileDTO.builder()
                .overall(overall)
                .difficultyPerformance(difficultyPerformance)
                .topicPerformance(topicPerformance)
                .recentTrends(recentTrends)
                .build();
    }

    private TrendComparisonDTO computeTrendComparison(
            List<SessionWithAnalytics> allSessions,
            String windowName,
            int days,
            LocalDateTime now) {

        LocalDateTime currentEnd = now;
        LocalDateTime currentStart = now.minusDays(days);
        LocalDateTime previousEnd = currentStart;
        LocalDateTime previousStart = now.minusDays(days * 2L);

        List<SessionWithAnalytics> currentSessions = new ArrayList<>();
        List<SessionWithAnalytics> previousSessions = new ArrayList<>();

        for (SessionWithAnalytics swa : allSessions) {
            LocalDateTime start = swa.session.getSessionStartedAt();
            if (start != null) {
                if (!start.isBefore(currentStart) && !start.isAfter(currentEnd)) {
                    currentSessions.add(swa);
                } else if (!start.isBefore(previousStart) && start.isBefore(previousEnd)) {
                    previousSessions.add(swa);
                }
            }
        }

        TrendPeriodMetricsDTO currentMetrics = computePeriodMetrics(currentSessions);
        TrendPeriodMetricsDTO previousMetrics = computePeriodMetrics(previousSessions);

        int deltaProblemsSolved = currentMetrics.getUniqueProblemsSolved() - previousMetrics.getUniqueProblemsSolved();
        int deltaSolvedSessions = currentMetrics.getTotalSolvedSessions() - previousMetrics.getTotalSolvedSessions();
        long deltaTimeSpent = currentMetrics.getTotalTimeSpent() - previousMetrics.getTotalTimeSpent();
        long deltaAvgSolveTime = currentMetrics.getAverageSolveTime() - previousMetrics.getAverageSolveTime();
        double deltaSuccessRate = roundToTwoDecimals(currentMetrics.getFirstAttemptSuccessRate() - previousMetrics.getFirstAttemptSuccessRate());

        return TrendComparisonDTO.builder()
                .timeWindow(windowName)
                .currentPeriodStart(currentStart)
                .currentPeriodEnd(currentEnd)
                .previousPeriodStart(previousStart)
                .previousPeriodEnd(previousEnd)
                .currentPeriod(currentMetrics)
                .previousPeriod(previousMetrics)
                .deltaProblemsSolved(deltaProblemsSolved)
                .deltaSolvedSessions(deltaSolvedSessions)
                .deltaTimeSpent(deltaTimeSpent)
                .deltaAverageSolveTime(deltaAvgSolveTime)
                .deltaFirstAttemptSuccessRate(deltaSuccessRate)
                .build();
    }

    private TrendPeriodMetricsDTO computePeriodMetrics(List<SessionWithAnalytics> periodSessions) {
        if (periodSessions == null || periodSessions.isEmpty()) {
            return TrendPeriodMetricsDTO.builder()
                    .uniqueProblemsSolved(0)
                    .totalSolvedSessions(0)
                    .totalSessions(0)
                    .totalTimeSpent(0L)
                    .averageSolveTime(0L)
                    .firstAttemptSuccessRate(0.0)
                    .build();
        }

        int totalSessions = periodSessions.size();
        long totalTimeSpent = 0L;
        List<SessionWithAnalytics> solvedSessions = new ArrayList<>();
        Set<Long> uniqueProblems = new HashSet<>();

        for (SessionWithAnalytics swa : periodSessions) {
            totalTimeSpent += (swa.analytics.getTotalActiveTime() != null ? swa.analytics.getTotalActiveTime() : 0L);
            if (Boolean.TRUE.equals(swa.analytics.getSolved())) {
                solvedSessions.add(swa);
                if (swa.session.getProblem() != null && swa.session.getProblem().getId() != null) {
                    uniqueProblems.add(swa.session.getProblem().getId());
                }
            }
        }

        int totalSolvedSessions = solvedSessions.size();
        long sumSolveTime = 0L;
        int firstAttemptSuccess = 0;

        for (SessionWithAnalytics sswa : solvedSessions) {
            sumSolveTime += (sswa.analytics.getTotalActiveTime() != null ? sswa.analytics.getTotalActiveTime() : 0L);
            if (Boolean.TRUE.equals(sswa.analytics.getFirstAttemptAccepted())) {
                firstAttemptSuccess++;
            }
        }

        long avgSolveTime = totalSolvedSessions > 0 ? Math.round((double) sumSolveTime / totalSolvedSessions) : 0L;
        double successRate = totalSolvedSessions > 0
                ? roundToTwoDecimals(((double) firstAttemptSuccess / totalSolvedSessions) * 100.0)
                : 0.0;

        return TrendPeriodMetricsDTO.builder()
                .uniqueProblemsSolved(uniqueProblems.size())
                .totalSolvedSessions(totalSolvedSessions)
                .totalSessions(totalSessions)
                .totalTimeSpent(totalTimeSpent)
                .averageSolveTime(avgSolveTime)
                .firstAttemptSuccessRate(successRate)
                .build();
    }

    private UserPerformanceProfileDTO buildEmptyProfile(LocalDateTime now) {
        ProfileOverallDTO overall = ProfileOverallDTO.builder()
                .uniqueProblemsSolved(0)
                .totalSolvedSessions(0)
                .totalSessions(0)
                .totalSubmissions(0)
                .averageAttempts(0.0)
                .firstAttemptSuccessRate(0.0)
                .averageSolveTime(0L)
                .averageThinkingTime(0L)
                .averageCodingTime(0L)
                .totalTimeSpent(0L)
                .hintUsageRate(0.0)
                .solutionUsageRate(0.0)
                .editorialUsageRate(0.0)
                .build();

        TrendComparisonDTO empty7 = computeTrendComparison(Collections.emptyList(), "LAST_7_DAYS", 7, now);
        TrendComparisonDTO empty30 = computeTrendComparison(Collections.emptyList(), "LAST_30_DAYS", 30, now);

        RecentTrendsDTO trends = RecentTrendsDTO.builder()
                .last7Days(empty7)
                .last30Days(empty30)
                .build();

        return UserPerformanceProfileDTO.builder()
                .overall(overall)
                .difficultyPerformance(new HashMap<>())
                .topicPerformance(new HashMap<>())
                .recentTrends(trends)
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
