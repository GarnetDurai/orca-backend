package com.example.dsatracker.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "confidence_history",
        indexes = {
                @Index(name = "idx_confidence_history_state_id", columnList = "confidence_state_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfidenceHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "confidence_state_id", nullable = false)
    private ConfidenceState confidenceState;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(nullable = false)
    private Double previousConfidence;

    @Column(nullable = false)
    private Double newConfidence;

    @Column(nullable = false)
    private Double masteryContribution;

    @Column(nullable = false)
    private Double independenceContribution;

    @Column(nullable = false)
    private Double retentionContribution;

    @Column(nullable = false, length = 50)
    private String assistanceEffect;

    @Column(nullable = false)
    private Double awayTimeEffect;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String algorithmVersion = "V1";
}
