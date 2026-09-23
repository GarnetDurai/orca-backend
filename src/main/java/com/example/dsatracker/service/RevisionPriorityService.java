package com.example.dsatracker.service;

import com.example.dsatracker.dto.ReviewQueueItemDTO;
import com.example.dsatracker.model.ConfidenceState;
import com.example.dsatracker.model.Problem;
import com.example.dsatracker.model.RevisionState;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class RevisionPriorityService {

    public double calculateSchedulingStrength(double confidence, double retentionStrength) {
        double c = Math.max(0.0, Math.min(100.0, confidence));
        double r = Math.max(0.0, Math.min(100.0, retentionStrength));
        return (0.70 * (c / 100.0)) + (0.30 * (r / 100.0));
    }

    public double calculateOverdueDays(LocalDateTime nextReviewAt, LocalDateTime now) {
        if (nextReviewAt == null || now == null || now.isBefore(nextReviewAt)) {
            return 0.0;
        }
        double minutes = Duration.between(nextReviewAt, now).toMinutes();
        return roundToTwoDecimals(minutes / 1440.0);
    }

    public double calculateOverduePressure(double overdueDays, int previousIntervalDays) {
        int interval = Math.max(1, previousIntervalDays);
        double pressure = 0.5 + (0.5 * (overdueDays / interval));
        return roundToTwoDecimals(Math.max(0.5, Math.min(1.0, pressure)));
    }

    public double calculateMemoryRisk(double schedulingStrength) {
        return roundToTwoDecimals(Math.max(0.0, Math.min(1.0, 1.0 - schedulingStrength)));
    }

    public double calculateFairnessScore(int skipCount) {
        return roundToTwoDecimals(Math.max(0.0, Math.min(1.0, (double) skipCount / 3.0)));
    }

    public double calculatePriority(double overduePressure, double memoryRisk, double fairnessScore) {
        double priority = (0.55 * overduePressure) + (0.30 * memoryRisk) + (0.15 * fairnessScore);
        return roundToTwoDecimals(Math.max(0.0, Math.min(1.0, priority)));
    }

    public ReviewQueueItemDTO buildQueueItem(RevisionState state, ConfidenceState confidenceState, LocalDateTime now) {
        double confidence = confidenceState != null && confidenceState.getCurrentConfidence() != null
                ? confidenceState.getCurrentConfidence()
                : 0.0;
        double retention = confidenceState != null && confidenceState.getRetentionStrength() != null
                ? confidenceState.getRetentionStrength()
                : 0.0;

        int interval = state.getCurrentIntervalDays() != null ? state.getCurrentIntervalDays() : 1;
        int skipCount = state.getSkipCount() != null ? state.getSkipCount() : 0;
        double overdueDays = calculateOverdueDays(state.getNextReviewAt(), now);
        double overduePressure = calculateOverduePressure(overdueDays, interval);
        double schedulingStrength = calculateSchedulingStrength(confidence, retention);
        double memoryRisk = calculateMemoryRisk(schedulingStrength);
        double fairnessScore = calculateFairnessScore(skipCount);
        double priority = calculatePriority(overduePressure, memoryRisk, fairnessScore);
        boolean fairnessRequired = skipCount >= 3;

        Problem p = state.getProblem();
        return ReviewQueueItemDTO.builder()
                .problemId(p != null ? p.getId() : null)
                .leetcodeId(p != null ? p.getLeetcodeId() : null)
                .problemTitle(p != null ? p.getTitle() : null)
                .difficulty(p != null && p.getDifficulty() != null ? p.getDifficulty().name() : null)
                .lastReviewedAt(state.getLastReviewedAt())
                .nextReviewAt(state.getNextReviewAt())
                .currentIntervalDays(state.getCurrentIntervalDays())
                .reviewCount(state.getReviewCount())
                .skipCount(skipCount)
                .currentConfidence(confidence)
                .retentionStrength(retention)
                .overdueDays(overdueDays)
                .overduePressure(overduePressure)
                .memoryRisk(memoryRisk)
                .fairnessScore(fairnessScore)
                .priority(priority)
                .fairnessRequired(fairnessRequired)
                .build();
    }

    public List<ReviewQueueItemDTO> rankQueue(List<ReviewQueueItemDTO> items) {
        List<ReviewQueueItemDTO> fairnessRequiredList = new ArrayList<>();
        List<ReviewQueueItemDTO> standardList = new ArrayList<>();

        for (ReviewQueueItemDTO item : items) {
            if (Boolean.TRUE.equals(item.getFairnessRequired())) {
                fairnessRequiredList.add(item);
            } else {
                standardList.add(item);
            }
        }

        // 1. Fairness-required items ordered by:
        //    a. Oldest nextReviewAt (ASC)
        //    b. Highest skipCount (DESC)
        //    c. Lowest currentConfidence (ASC)
        fairnessRequiredList.sort(
                Comparator.comparing(ReviewQueueItemDTO::getNextReviewAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ReviewQueueItemDTO::getSkipCount, Comparator.reverseOrder())
                        .thenComparing(ReviewQueueItemDTO::getCurrentConfidence)
        );

        // 2. Standard items ordered by:
        //    a. Priority (DESC)
        //    b. Oldest nextReviewAt (ASC)
        standardList.sort(
                Comparator.comparing(ReviewQueueItemDTO::getPriority, Comparator.reverseOrder())
                        .thenComparing(ReviewQueueItemDTO::getNextReviewAt, Comparator.nullsLast(Comparator.naturalOrder()))
        );

        List<ReviewQueueItemDTO> result = new ArrayList<>(fairnessRequiredList);
        result.addAll(standardList);

        for (int i = 0; i < result.size(); i++) {
            result.get(i).setQueuePosition(i + 1);
        }

        return result;
    }

    private double roundToTwoDecimals(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
