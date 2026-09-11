package com.example.dsatracker.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrendComparisonDTO {

    private String timeWindow;
    private LocalDateTime currentPeriodStart;
    private LocalDateTime currentPeriodEnd;
    private LocalDateTime previousPeriodStart;
    private LocalDateTime previousPeriodEnd;
    private TrendPeriodMetricsDTO currentPeriod;
    private TrendPeriodMetricsDTO previousPeriod;
    private Integer deltaProblemsSolved;
    private Integer deltaSolvedSessions;
    private Long deltaTimeSpent;
    private Long deltaAverageSolveTime;
    private Double deltaFirstAttemptSuccessRate;
}
