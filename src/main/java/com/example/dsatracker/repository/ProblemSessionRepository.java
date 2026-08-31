package com.example.dsatracker.repository;

import com.example.dsatracker.model.ProblemSession;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProblemSessionRepository extends JpaRepository<ProblemSession, Long> {
    Optional<ProblemSession> findBySessionId(String sessionId);
    boolean existsBySessionId(String sessionId);
    List<ProblemSession> findByUserId(Long userId);
    List<ProblemSession> findByProblemId(Long problemId);
    List<ProblemSession> findByUserIdAndProblemId(Long userId, Long problemId);

    @EntityGraph(attributePaths = {"problem", "events"})
    List<ProblemSession> findByUserIdOrderBySessionStartedAtDesc(Long userId);

    @EntityGraph(attributePaths = {"problem", "events"})
    Optional<ProblemSession> findBySessionIdAndUserId(String sessionId, Long userId);
}
