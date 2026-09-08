package com.example.dsatracker.dto;

import lombok.*;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HistoricalAnalyticsDTO {

    private Integer totalProblemsSolved;
    private Integer totalSessions;

    private Long averageSolveTime;
    private Long averageThinkingTime;
    private Long averageCodingTime;
    private Double averageAttempts;

    private Double firstAttemptSuccessRate;

    private Integer totalWrongSubmissions;
    private Integer totalAcceptedSubmissions;

    private Double hintUsageRate;
    private Double solutionUsageRate;
    private Double editorialUsageRate;

    private Double averageHintsPerProblem;

    private Long totalTimeSpent;

    @Builder.Default
    private Map<String, Integer> problemsSolvedByDifficulty = new HashMap<>();

    @Builder.Default
    private Map<String, DifficultyAnalyticsDTO> difficultyAnalytics = new HashMap<>();

    @Builder.Default
    private Map<String, TopicAnalyticsDTO> topicAnalytics = new HashMap<>();
}
