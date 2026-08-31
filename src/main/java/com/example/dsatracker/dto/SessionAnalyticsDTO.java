package com.example.dsatracker.dto;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionAnalyticsDTO {

    private String sessionId;
    private ProblemMetadataDTO problem;
    private String language;

    // Time Metrics (in milliseconds)
    private Long totalActiveTime;
    private Long thinkingTime;
    private Long codingTime;
    private Long timeAway;
    private Integer tabSwitchCount;

    // Submission Metrics
    private Integer attempts;
    private Integer wrongSubmissionCount;
    private Integer acceptedSubmissionCount;
    private Boolean firstAttemptAccepted;

    // Assistance Metrics
    private Boolean hintUsed;
    private Integer hintCount;
    @Builder.Default
    private List<String> hintsOpened = new ArrayList<>();

    private Boolean solutionViewed;
    private Boolean editorialViewed;

    // Outcome
    private Boolean solved;
}
