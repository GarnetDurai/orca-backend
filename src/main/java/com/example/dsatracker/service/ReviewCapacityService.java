package com.example.dsatracker.service;

import com.example.dsatracker.dto.ReviewCapacityDTO;
import com.example.dsatracker.model.ProblemSession;
import com.example.dsatracker.model.RevisionHistory;
import com.example.dsatracker.repository.ProblemSessionRepository;
import com.example.dsatracker.repository.RevisionHistoryRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
public class ReviewCapacityService {

    private final ProblemSessionRepository sessionRepository;
    private final RevisionHistoryRepository revisionHistoryRepository;

    public ReviewCapacityService(
            ProblemSessionRepository sessionRepository,
            RevisionHistoryRepository revisionHistoryRepository) {
        this.sessionRepository = sessionRepository;
        this.revisionHistoryRepository = revisionHistoryRepository;
    }

    public ReviewCapacityDTO calculateCapacity(Long userId, LocalDateTime now, ZoneId zoneId) {
        if (userId == null) {
            return defaultCapacity();
        }

        ZoneId zone = zoneId != null ? zoneId : ZoneId.systemDefault();
        LocalDateTime thirtyDaysAgo = now.minusDays(30);

        List<ProblemSession> recentSessions = sessionRepository.findByUserId(userId).stream()
                .filter(s -> s.getSessionStartedAt() != null && !s.getSessionStartedAt().isBefore(thirtyDaysAgo))
                .toList();

        List<RevisionHistory> recentReviews = revisionHistoryRepository.findByUserIdAndReviewedAtAfter(userId, thirtyDaysAgo);

        // Group sessions by local date to determine active days
        Set<LocalDate> activeDays = new TreeSet<>();
        Set<Long> problemsSeenBeforeThirtyDays = new HashSet<>();

        // Identify problems solved before the 30-day window
        for (ProblemSession s : sessionRepository.findByUserId(userId)) {
            if (s.getSessionStartedAt() != null && s.getSessionStartedAt().isBefore(thirtyDaysAgo) && Boolean.TRUE.equals(s.getSolved())) {
                if (s.getProblem() != null) {
                    problemsSeenBeforeThirtyDays.add(s.getProblem().getId());
                }
            }
        }

        Map<LocalDate, Integer> reviewsPerDay = new HashMap<>();
        Map<LocalDate, Set<Long>> newProblemsPerDay = new HashMap<>();

        for (ProblemSession s : recentSessions) {
            LocalDate date = s.getSessionStartedAt().atZone(zone).toLocalDate();
            activeDays.add(date);

            if (Boolean.TRUE.equals(s.getSolved()) && s.getProblem() != null) {
                Long pid = s.getProblem().getId();
                if (!problemsSeenBeforeThirtyDays.contains(pid)) {
                    newProblemsPerDay.computeIfAbsent(date, k -> new HashSet<>()).add(pid);
                }
            }
        }

        for (RevisionHistory r : recentReviews) {
            LocalDate date = r.getReviewedAt().atZone(zone).toLocalDate();
            activeDays.add(date);
            reviewsPerDay.put(date, reviewsPerDay.getOrDefault(date, 0) + 1);
        }

        if (activeDays.isEmpty()) {
            return defaultCapacity();
        }

        List<Integer> dailyCounts = new ArrayList<>();
        int totalReviews = 0;
        int totalNew = 0;

        for (LocalDate day : activeDays) {
            int rCount = reviewsPerDay.getOrDefault(day, 0);
            dailyCounts.add(rCount);
            totalReviews += rCount;

            int nCount = newProblemsPerDay.getOrDefault(day, Collections.emptySet()).size();
            totalNew += nCount;
        }

        Collections.sort(dailyCounts);
        double median = calculateMedian(dailyCounts);
        int capacity = Math.max(1, (int) Math.round(median));

        double newPerDay = roundToTwoDecimals((double) totalNew / activeDays.size());
        double reviewPerDay = roundToTwoDecimals((double) totalReviews / activeDays.size());
        LocalDate today = now.atZone(zone).toLocalDate();
        int completedReviewsToday = reviewsPerDay.getOrDefault(today, 0);
        int newProblemsSolvedToday = newProblemsPerDay.getOrDefault(today, Collections.emptySet()).size();

        return ReviewCapacityDTO.builder()
                .dailyCapacity(capacity)
                .activeDaysLast30Days(activeDays.size())
                .medianDailyReviews(roundToTwoDecimals(median))
                .newProblemsPerActiveDay(newPerDay)
                .reviewProblemsPerActiveDay(reviewPerDay)
                .reviewsCompletedToday(completedReviewsToday)
                .newProblemsSolvedToday(newProblemsSolvedToday)
                .build();
    }

    private double calculateMedian(List<Integer> sortedList) {
        if (sortedList == null || sortedList.isEmpty()) {
            return 0.0;
        }
        int size = sortedList.size();
        if (size % 2 == 1) {
            return sortedList.get(size / 2);
        } else {
            return (sortedList.get((size / 2) - 1) + sortedList.get(size / 2)) / 2.0;
        }
    }

    private ReviewCapacityDTO defaultCapacity() {
        return ReviewCapacityDTO.builder()
                .dailyCapacity(1)
                .activeDaysLast30Days(0)
                .medianDailyReviews(0.0)
                .newProblemsPerActiveDay(0.0)
                .reviewProblemsPerActiveDay(0.0)
                .reviewsCompletedToday(0)
                .newProblemsSolvedToday(0)
                .build();
    }

    private double roundToTwoDecimals(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
