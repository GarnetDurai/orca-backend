package com.example.dsatracker.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "confidence_states",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_confidence_user_problem", columnNames = {"user_id", "problem_id"})
        },
        indexes = {
                @Index(name = "idx_confidence_user_id", columnList = "user_id"),
                @Index(name = "idx_confidence_problem_id", columnList = "problem_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfidenceState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;

    @Column(nullable = false)
    private Double masteryScore;

    @Column(nullable = false)
    private Double independenceScore;

    @Column(nullable = false)
    private Double retentionStrength;

    @Column(nullable = false)
    private Double currentConfidence;

    private LocalDateTime lastSuccessfulSolveAt;

    @Column(nullable = false)
    @Builder.Default
    private Integer successfulSolveCount = 0;

    @Column(nullable = false)
    @Builder.Default
    private Integer independentSolveCount = 0;

    @Column(nullable = false)
    private LocalDateTime lastConfidenceUpdateAt;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String algorithmVersion = "V1";

    @OneToMany(mappedBy = "confidenceState", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("timestamp DESC, id DESC")
    @Builder.Default
    private List<ConfidenceHistory> history = new ArrayList<>();

    public void addHistory(ConfidenceHistory entry) {
        history.add(entry);
        entry.setConfidenceState(this);
    }
}
