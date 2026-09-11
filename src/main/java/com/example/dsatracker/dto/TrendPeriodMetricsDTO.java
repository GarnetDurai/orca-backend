package com.example.dsatracker.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrendPeriodMetricsDTO {

    private Integer uniqueProblemsSolved;
    private Integer totalSolvedSessions;
    private Integer totalSessions;
    private Long totalTimeSpent;
    private Long averageSolveTime;
    private Double firstAttemptSuccessRate;
}
