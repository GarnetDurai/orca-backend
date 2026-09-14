package com.example.dsatracker.mapper;

import com.example.dsatracker.dto.ProblemMetadataDTO;
import com.example.dsatracker.dto.ProblemSessionRequestDTO;
import com.example.dsatracker.model.Difficulty;
import com.example.dsatracker.model.Problem;
import com.example.dsatracker.model.ProblemSession;
import com.example.dsatracker.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class SessionMapperTest {

    private final User testUser = User.builder().id(1L).email("test@example.com").build();
    private final Problem testProblem = Problem.builder().id(10L).leetcodeId(1).difficulty(Difficulty.EASY).title("Two Sum").build();

    @Test
    @DisplayName("Normal valid timestamps are accurately mapped")
    void testNormalTimestampMapping() {
        long startedAt = 1725040000000L;
        long solvedAt = 1725040600000L;

        ProblemSessionRequestDTO request = ProblemSessionRequestDTO.builder()
                .sessionId("sess-normal")
                .sessionStartedAt(startedAt)
                .solvedAt(solvedAt)
                .problem(ProblemMetadataDTO.builder().leetcodeId(1).build())
                .build();

        ProblemSession session = SessionMapper.toEntity(request, testUser, testProblem);

        assertNotNull(session.getSessionStartedAt());
        assertNotNull(session.getSolvedAt());
        assertTrue(session.getSolvedAt().isAfter(session.getSessionStartedAt()));
    }

    @Test
    @DisplayName("Far future timestamp (> 5 min skew) is capped to approximately now")
    void testFutureTimestampCapped() {
        // Year 2099 timestamp
        long farFuture = 4091491200000L;

        ProblemSessionRequestDTO request = ProblemSessionRequestDTO.builder()
                .sessionId("sess-future")
                .sessionStartedAt(farFuture)
                .problem(ProblemMetadataDTO.builder().leetcodeId(1).build())
                .build();

        ProblemSession session = SessionMapper.toEntity(request, testUser, testProblem);

        LocalDateTime now = LocalDateTime.now();
        assertNotNull(session.getSessionStartedAt());
        // Should be capped to now (within 1 minute of current time)
        assertTrue(session.getSessionStartedAt().isBefore(now.plusMinutes(1)));
        assertTrue(session.getSessionStartedAt().isAfter(now.minusMinutes(2)));
    }

    @Test
    @DisplayName("Inconsistent firstCodingAt before sessionStartedAt is clamped to sessionStartedAt")
    void testInconsistentCodingBeforeStartClamped() {
        long startedAt = 1725040000000L;
        long codingBeforeStart = startedAt - 60000L; // 1 min before start

        ProblemSessionRequestDTO request = ProblemSessionRequestDTO.builder()
                .sessionId("sess-inconsistent")
                .sessionStartedAt(startedAt)
                .firstCodingAt(codingBeforeStart)
                .problem(ProblemMetadataDTO.builder().leetcodeId(1).build())
                .build();

        ProblemSession session = SessionMapper.toEntity(request, testUser, testProblem);

        assertNotNull(session.getFirstCodingAt());
        assertEquals(session.getSessionStartedAt(), session.getFirstCodingAt());
    }

    @Test
    @DisplayName("Negative or zero timestamps are treated safely without throwing exceptions")
    void testNegativeTimestampHandling() {
        ProblemSessionRequestDTO request = ProblemSessionRequestDTO.builder()
                .sessionId("sess-negative")
                .sessionStartedAt(-500L)
                .firstCodingAt(0L)
                .solvedAt(-1000L)
                .problem(ProblemMetadataDTO.builder().leetcodeId(1).build())
                .build();

        ProblemSession session = SessionMapper.toEntity(request, testUser, testProblem);

        assertNotNull(session.getSessionStartedAt()); // Falls back to now()
        assertNull(session.getFirstCodingAt());
        assertNull(session.getSolvedAt());
    }

    @Test
    @DisplayName("Extreme overflow timestamps do not crash toLocalDateTime")
    void testExtremeOverflowTimestamp() {
        ProblemSessionRequestDTO request = ProblemSessionRequestDTO.builder()
                .sessionId("sess-overflow")
                .sessionStartedAt(Long.MAX_VALUE)
                .problem(ProblemMetadataDTO.builder().leetcodeId(1).build())
                .build();

        assertDoesNotThrow(() -> {
            ProblemSession session = SessionMapper.toEntity(request, testUser, testProblem);
            assertNotNull(session.getSessionStartedAt());
        });
    }
}
