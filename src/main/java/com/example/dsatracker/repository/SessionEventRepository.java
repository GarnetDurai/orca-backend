package com.example.dsatracker.repository;

import com.example.dsatracker.model.SessionEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SessionEventRepository extends JpaRepository<SessionEvent, Long> {
    List<SessionEvent> findBySessionIdOrderByTimestampAsc(Long sessionId);
}
