package com.example.dsatracker.repository;

import com.example.dsatracker.model.ConfidenceState;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConfidenceStateRepository extends JpaRepository<ConfidenceState, Long> {

    Optional<ConfidenceState> findByUserIdAndProblemId(Long userId, Long problemId);

    @EntityGraph(attributePaths = {"problem", "history"})
    Optional<ConfidenceState> findWithHistoryByUserIdAndProblemId(Long userId, Long problemId);

    @EntityGraph(attributePaths = {"problem"})
    List<ConfidenceState> findByUserIdOrderByLastConfidenceUpdateAtDesc(Long userId);

    boolean existsByUserIdAndProblemId(Long userId, Long problemId);
}
