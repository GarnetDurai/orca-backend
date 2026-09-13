package com.example.dsatracker.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewQueueItemDTO {

    private Long problemId;
    private Integer leetcodeId;
    private String problemTitle;
    private String difficulty;

    private LocalDateTime nextReviewAt;
    private Integer currentIntervalDays;
    private Integer reviewCount;
    private Integer skipCount;

    private Double currentConfidence;
    private Double retentionStrength;

    private Double overdueDays;
    private Double overduePressure;
    private Double memoryRisk;
    private Double fairnessScore;
    private Double priority;
    private Boolean fairnessRequired;
    private Integer queuePosition;
}
