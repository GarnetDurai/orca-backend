package com.example.dsatracker.dto;

import lombok.*;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPerformanceProfileDTO {

    private ProfileOverallDTO overall;

    @Builder.Default
    private Map<String, DifficultyProfileDTO> difficultyPerformance = new HashMap<>();

    @Builder.Default
    private Map<String, TopicProfileDTO> topicPerformance = new HashMap<>();

    private RecentTrendsDTO recentTrends;
}
