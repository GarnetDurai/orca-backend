package com.example.dsatracker.repository;

import com.example.dsatracker.model.RevisionState;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RevisionStateRepository extends JpaRepository<RevisionState, Long> {

    Optional<RevisionState> findByUserIdAndProblemId(Long userId, Long problemId);

    @EntityGraph(attributePaths = {"history", "problem"})
    @Query("SELECT r FROM RevisionState r WHERE r.user.id = :userId AND r.problem.id = :problemId")
    Optional<RevisionState> findWithHistoryByUserIdAndProblemId(@Param("userId") Long userId, @Param("problemId") Long problemId);

    @EntityGraph(attributePaths = {"problem"})
    List<RevisionState> findByUserIdOrderByNextReviewAtAsc(Long userId);

    @Query("SELECT r FROM RevisionState r JOIN FETCH r.problem WHERE r.user.id = :userId AND r.nextReviewAt <= :cutoff ORDER BY r.nextReviewAt ASC")
    List<RevisionState> findDueRevisions(@Param("userId") Long userId, @Param("cutoff") LocalDateTime cutoff);

    long countByUserIdAndNextReviewAtLessThanEqual(Long userId, LocalDateTime cutoff);
}
