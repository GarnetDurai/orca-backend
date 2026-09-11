package com.example.dsatracker.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecentTrendsDTO {

    private TrendComparisonDTO last7Days;
    private TrendComparisonDTO last30Days;
}
