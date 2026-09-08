package com.example.dsatracker.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopicAnalyticsDTO {
    private Integer problemsSolved;
    private Long averageSolveTime;
    private Double firstAttemptSuccessRate;
}
