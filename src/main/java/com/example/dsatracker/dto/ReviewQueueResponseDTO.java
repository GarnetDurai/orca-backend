package com.example.dsatracker.dto;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewQueueResponseDTO {

    @Builder.Default
    private List<ReviewQueueItemDTO> queue = new ArrayList<>();

    private Integer totalDue;
    private Integer dailyCapacity;
    private Integer backlogCount;
    private Integer fairnessRequiredCount;

    private ReviewCapacityDTO capacityDetails;
}
