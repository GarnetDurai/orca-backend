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
public class ConfidenceResponseDTO {

    private Long problemId;
    private Integer leetcodeId;
    private String problemTitle;
    private String difficulty;

    private Double currentConfidence;
    private Double masteryScore;
    private Double independenceScore;
    private Double retentionStrength;

    private Integer successfulSolveCount;
    private Integer independentSolveCount;
    private LocalDateTime lastSuccessfulSolveAt;
    private LocalDateTime lastConfidenceUpdateAt;
    private String algorithmVersion;

    @Builder.Default
    private List<ConfidenceHistoryDTO> history = new ArrayList<>();
}
