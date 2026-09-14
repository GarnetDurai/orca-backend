package com.example.dsatracker.repository;

import com.example.dsatracker.model.DashboardAuthCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface DashboardAuthCodeRepository extends JpaRepository<DashboardAuthCode, Long> {

    Optional<DashboardAuthCode> findByCodeHash(String codeHash);

    @Query("SELECT c FROM DashboardAuthCode c JOIN FETCH c.user WHERE c.codeHash = :codeHash")
    Optional<DashboardAuthCode> findByCodeHashWithUser(@Param("codeHash") String codeHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE DashboardAuthCode c SET c.usedAt = :usedAt WHERE c.codeHash = :codeHash AND c.usedAt IS NULL AND c.expiresAt > :now")
    int consumeCodeIfValid(
            @Param("codeHash") String codeHash,
            @Param("usedAt") LocalDateTime usedAt,
            @Param("now") LocalDateTime now
    );
}
