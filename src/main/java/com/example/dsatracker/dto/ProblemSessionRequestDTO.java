package com.example.dsatracker.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProblemSessionRequestDTO {

    @NotBlank(message = "Session ID is required")
    private String sessionId;

    private Long problemId;

    @Valid
    private ProblemMetadataDTO problem;

    @NotNull(message = "Session started timestamp is required")
    private Long sessionStartedAt;

    private Long firstCodingAt;

    private Long solvedAt;

    @Min(value = 0, message = "Thinking duration cannot be negative")
    private Long thinkingDuration;

    @Min(value = 0, message = "Coding duration cannot be negative")
    private Long codingDuration;

    @Min(value = 0, message = "Total time away cannot be negative")
    private Long totalTimeAway;

    @Min(value = 0, message = "Tab switch count cannot be negative")
    private Integer tabSwitchCount;

    private String language;

    private Boolean hintOpened;

    @Min(value = 0, message = "Hint open count cannot be negative")
    private Integer hintOpenCount;

    private Long hintOpenedAt;

    private Boolean solutionViewed;

    private Long solutionViewedAt;

    private Boolean editorialViewed;

    private Long editorialViewedAt;

    @Min(value = 0, message = "Attempts cannot be negative")
    private Integer attempts;

    @NotNull(message = "Solved state is required")
    private Boolean solved;

    @Valid
    @Builder.Default
    private List<SessionEventDTO> events = new ArrayList<>();
}
