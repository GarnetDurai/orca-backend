package com.example.dsatracker.dto;

import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProblemSessionDetailsDTO {

    private String sessionId;
    private ProblemMetadataDTO problem;

    private Long sessionStartedAt;
    private Long firstCodingAt;
    private Long solvedAt;

    private Long thinkingDuration;
    private Long codingDuration;
    private Long totalTimeAway;
    private Integer tabSwitchCount;

    private String language;

    private Boolean hintOpened;
    private Integer hintOpenCount;
    private Long hintOpenedAt;

    private Boolean solutionViewed;
    private Long solutionViewedAt;

    private Boolean editorialViewed;
    private Long editorialViewedAt;

    private Integer attempts;
    private Boolean solved;

    private LocalDateTime createdAt;

    @Builder.Default
    private List<SessionEventDTO> events = new ArrayList<>();
}
