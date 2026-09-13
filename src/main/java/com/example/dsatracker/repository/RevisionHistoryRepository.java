package com.example.dsatracker.repository;

import com.example.dsatracker.model.RevisionHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface RevisionHistoryRepository extends JpaRepository<RevisionHistory, Long> {

    boolean existsBySourceSessionId(String sourceSessionId);

    Optional<RevisionHistory> findBySourceSessionId(String sourceSessionId);

    List<RevisionHistory> findByRevisionStateIdOrderByReviewedAtDesc(Long revisionStateId);

    @Query("SELECT rh FROM RevisionHistory rh JOIN rh.revisionState rs WHERE rs.user.id = :userId AND rh.reviewedAt >= :since ORDER BY rh.reviewedAt ASC")
    List<RevisionHistory> findByUserIdAndReviewedAtAfter(@Param("userId") Long userId, @Param("since") LocalDateTime since);
}
