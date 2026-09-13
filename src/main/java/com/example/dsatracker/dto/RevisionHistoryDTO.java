package com.example.dsatracker.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RevisionHistoryDTO {

    private Long id;
    private String sourceSessionId;
    private LocalDateTime reviewedAt;
    private String outcome;
    private Integer previousIntervalDays;
    private Integer newIntervalDays;
    private Double previousConfidence;
    private Double newConfidence;
    private Double previousRetentionStrength;
    private Double newRetentionStrength;
    private LocalDateTime previousNextReviewAt;
    private LocalDateTime newNextReviewAt;
    private Double actualRecallIntervalDays;
    private Double plannedIntervalDays;
    private Double priorityAtSelection;
    private String algorithmVersion;
}
