package com.example.dsatracker.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "revision_history",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_revision_history_source_session_id", columnNames = {"sourceSessionId"})
        },
        indexes = {
                @Index(name = "idx_revision_history_state_id", columnList = "revision_state_id"),
                @Index(name = "idx_revision_history_source_session_id", columnList = "sourceSessionId")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RevisionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "revision_state_id", nullable = false)
    private RevisionState revisionState;

    @Column(nullable = false, unique = true, length = 64)
    private String sourceSessionId;

    @Column(nullable = false)
    private LocalDateTime reviewedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReviewOutcome outcome;

    private Integer previousIntervalDays;

    private Integer newIntervalDays;

    private Double previousConfidence;

    private Double newConfidence;

    private Double previousRetentionStrength;

    private Double newRetentionStrength;

    private LocalDateTime previousNextReviewAt;

    private LocalDateTime newNextReviewAt;

    private Double actualRecallIntervalDays;

    private Double plannedIntervalDays;

    private Double priorityAtSelection;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String algorithmVersion = "SRS_V1";
}
