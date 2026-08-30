package com.example.dsatracker.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "session_events",
        indexes = {
                @Index(name = "idx_session_events_session_id", columnList = "session_id"),
                @Index(name = "idx_session_events_event_type", columnList = "eventType")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private ProblemSession session;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private SessionEventType eventType;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column(length = 50)
    private String result;

    @Column(length = 64)
    private String submissionId;

    @Column(length = 100)
    private String hintName;
}
