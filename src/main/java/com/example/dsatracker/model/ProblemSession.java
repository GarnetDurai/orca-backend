package com.example.dsatracker.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "problem_sessions",
        indexes = {
                @Index(name = "idx_problem_sessions_session_id", columnList = "sessionId", unique = true),
                @Index(name = "idx_problem_sessions_user_id", columnList = "user_id"),
                @Index(name = "idx_problem_sessions_problem_id", columnList = "problem_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProblemSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String sessionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "problem_id", nullable = false)
    private Problem problem;

    @Column(nullable = false)
    private LocalDateTime sessionStartedAt;

    private LocalDateTime firstCodingAt;

    private LocalDateTime solvedAt;

    private Long thinkingDuration;

    private Long codingDuration;

    @Column(nullable = false)
    @Builder.Default
    private Long totalTimeAway = 0L;

    @Column(nullable = false)
    @Builder.Default
    private Integer tabSwitchCount = 0;

    @Column(length = 50)
    private String language;

    @Column(nullable = false)
    @Builder.Default
    private Boolean hintOpened = false;

    @Column(nullable = false)
    @Builder.Default
    private Integer hintOpenCount = 0;

    private LocalDateTime hintOpenedAt;

    @Column(nullable = false)
    @Builder.Default
    private Boolean solutionViewed = false;

    private LocalDateTime solutionViewedAt;

    @Column(nullable = false)
    @Builder.Default
    private Boolean editorialViewed = false;

    private LocalDateTime editorialViewedAt;

    @Column(nullable = false)
    @Builder.Default
    private Integer attempts = 0;

    @Column(nullable = false)
    @Builder.Default
    private Boolean solved = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("timestamp ASC, id ASC")
    @Builder.Default
    private List<SessionEvent> events = new ArrayList<>();

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public void addEvent(SessionEvent event) {
        events.add(event);
        event.setSession(this);
    }

    public void removeEvent(SessionEvent event) {
        events.remove(event);
        event.setSession(null);
    }
}
