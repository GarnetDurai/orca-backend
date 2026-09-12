package com.example.dsatracker.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfidenceHistoryDTO {

    private Long id;
    private LocalDateTime timestamp;
    private Double previousConfidence;
    private Double newConfidence;
    private Double masteryContribution;
    private Double independenceContribution;
    private Double retentionContribution;
    private String assistanceEffect;
    private Double awayTimeEffect;
    private String algorithmVersion;
}
