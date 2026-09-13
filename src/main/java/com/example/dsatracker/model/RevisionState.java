package com.example.dsatracker.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "revision_states",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_revision_user_problem", columnNames = {"user_id", "problem_id"})
        },
        indexes = {
                @Index(name = "idx_revision_user_id", columnList = "user_id"),
                @Index(name = "idx_revision_problem_id", columnList = "problem_id"),
                @Index(name = "idx_revision_next_review_at", columnList = "nextReviewAt")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RevisionState {

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
    @Builder.Default
    private Integer reviewCount = 0;

    @Column(nullable = false)
    private Integer currentIntervalDays;

    private LocalDateTime lastReviewedAt;

    @Column(nullable = false)
    private LocalDateTime nextReviewAt;

    @Column(nullable = false)
    @Builder.Default
    private Integer skipCount = 0;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String algorithmVersion = "SRS_V1";

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "revisionState", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("reviewedAt DESC, id DESC")
    @Builder.Default
    private List<RevisionHistory> history = new ArrayList<>();

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void addHistory(RevisionHistory entry) {
        history.add(entry);
        entry.setRevisionState(this);
    }
}
