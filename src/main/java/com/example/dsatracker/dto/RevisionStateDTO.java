package com.example.dsatracker.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RevisionStateDTO {

    private Long id;
    private Long problemId;
    private Integer leetcodeId;
    private String problemTitle;
    private String difficulty;

    private Integer reviewCount;
    private Integer currentIntervalDays;
    private LocalDateTime lastReviewedAt;
    private LocalDateTime nextReviewAt;
    private Integer skipCount;

    private Boolean isOverdue;
    private Double overdueDays;

    private String algorithmVersion;

    @Builder.Default
    private List<RevisionHistoryDTO> history = new ArrayList<>();
}
