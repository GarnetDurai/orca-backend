package com.example.dsatracker.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProfileOverallDTO {

    private Integer uniqueProblemsSolved;
    private Integer totalSolvedSessions;
    private Integer totalSessions;
    private Integer totalSubmissions;
    private Double averageAttempts;
    private Double firstAttemptSuccessRate;
    private Long averageSolveTime;
    private Long averageThinkingTime;
    private Long averageCodingTime;
    private Long totalTimeSpent;
    private Double hintUsageRate;
    private Double solutionUsageRate;
    private Double editorialUsageRate;
}
