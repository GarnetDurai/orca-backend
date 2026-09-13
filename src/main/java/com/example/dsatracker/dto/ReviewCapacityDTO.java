package com.example.dsatracker.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewCapacityDTO {

    private Integer dailyCapacity;
    private Integer activeDaysLast30Days;
    private Double medianDailyReviews;
    private Double newProblemsPerActiveDay;
    private Double reviewProblemsPerActiveDay;
}
