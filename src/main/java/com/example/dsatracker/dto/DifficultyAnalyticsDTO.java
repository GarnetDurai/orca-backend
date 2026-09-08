package com.example.dsatracker.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DifficultyAnalyticsDTO {
    private Integer problemsSolved;
    private Long averageSolveTime;
    private Double averageAttempts;
    private Double firstAttemptSuccessRate;
}
