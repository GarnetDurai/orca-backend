package com.example.dsatracker.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopicProfileDTO {

    private String topic;
    private Integer uniqueProblemsSolved;
    private Integer totalSolvedSessions;
    private Integer sessionCount;
    private Long averageSolveTime;
    private Double averageAttempts;
    private Double firstAttemptSuccessRate;
    private Double hintUsageRate;
    private Double solutionUsageRate;
    private Double editorialUsageRate;
}
