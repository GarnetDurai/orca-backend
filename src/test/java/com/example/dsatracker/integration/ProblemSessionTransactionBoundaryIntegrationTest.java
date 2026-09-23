package com.example.dsatracker.integration;

import com.example.dsatracker.dto.ProblemMetadataDTO;
import com.example.dsatracker.dto.ProblemSessionRequestDTO;
import com.example.dsatracker.dto.ProblemSessionResponseDTO;
import com.example.dsatracker.model.*;
import com.example.dsatracker.repository.*;
import com.example.dsatracker.service.SessionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class ProblemSessionTransactionBoundaryIntegrationTest {

    @Autowired
    private SessionService sessionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProblemRepository problemRepository;

    @Autowired
    private ProblemSessionRepository sessionRepository;

    @Autowired
    private ConfidenceStateRepository confidenceStateRepository;

    @Autowired
    private RevisionStateRepository revisionStateRepository;

    private User testUser;
    private String uniqueEmail;

    @BeforeEach
    void setUp() {
        uniqueEmail = "integration_test_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
        testUser = userRepository.save(User.builder()
                .name("Integration User")
                .email(uniqueEmail)
                .password("encoded_pass")
                .createdAt(LocalDateTime.now())
                .build());

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(uniqueEmail, null, java.util.Collections.emptyList())
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Transaction Boundary Fix: Ingesting a new problem successfully persists ConfidenceState and RevisionState via REQUIRES_NEW")
    void testNewProblemIngestionPersistsConfidenceAndRevisionStates() {
        // Generate a unique leetcodeId unlikely to collide with any real problems
        int uniqueLeetcodeId = 980000 + (int) (Math.random() * 10000);
        String uniqueSessionId = "sess-int-" + UUID.randomUUID();

        ProblemSessionRequestDTO request = ProblemSessionRequestDTO.builder()
                .sessionId(uniqueSessionId)
                .problem(ProblemMetadataDTO.builder()
                        .leetcodeId(uniqueLeetcodeId)
                        .title("Integration Test Problem " + uniqueLeetcodeId)
                        .difficulty("MEDIUM")
                        .url("https://leetcode.com/problems/integration-test-" + uniqueLeetcodeId)
                        .build())
                .sessionStartedAt(System.currentTimeMillis() - 600000)
                .firstCodingAt(System.currentTimeMillis() - 500000)
                .solvedAt(System.currentTimeMillis())
                .thinkingDuration(100000L)
                .codingDuration(500000L)
                .totalTimeAway(0L)
                .tabSwitchCount(0)
                .language("java")
                .attempts(1)
                .solved(true)
                .build();

        // Ensure problem did not exist prior to ingestion
        assertTrue(problemRepository.findByLeetcodeId(uniqueLeetcodeId).isEmpty(),
                "Problem should not exist before test execution");

        // Execute session ingestion
        ProblemSessionResponseDTO response = sessionService.ingestSession(request);

        assertNotNull(response);
        assertEquals(uniqueSessionId, response.getSessionId());
        assertEquals("SAVED", response.getStatus());

        // 1. Verify Problem row was committed and has an ID
        Optional<Problem> problemOpt = problemRepository.findByLeetcodeId(uniqueLeetcodeId);
        assertTrue(problemOpt.isPresent(), "Problem must be created and committed");
        Problem problem = problemOpt.get();
        assertEquals(uniqueLeetcodeId, problem.getLeetcodeId());

        // 2. Verify ProblemSession was persisted
        Optional<ProblemSession> sessionOpt = sessionRepository.findBySessionIdAndUserId(uniqueSessionId, testUser.getId());
        assertTrue(sessionOpt.isPresent(), "ProblemSession must be persisted");

        // 3. Verify ConfidenceState was committed in REQUIRES_NEW without FK violation
        Optional<ConfidenceState> confidenceOpt = confidenceStateRepository.findByUserIdAndProblemId(testUser.getId(), problem.getId());
        assertTrue(confidenceOpt.isPresent(),
                "ConfidenceState MUST be present for newly created problem; REQUIRES_NEW transaction must not fail foreign key check");
        ConfidenceState confidenceState = confidenceOpt.get();
        assertNotNull(confidenceState.getCurrentConfidence());
        assertTrue(confidenceState.getCurrentConfidence() > 0.0);

        // 4. Verify RevisionState was committed in REQUIRES_NEW without FK violation
        Optional<RevisionState> revisionOpt = revisionStateRepository.findByUserIdAndProblemId(testUser.getId(), problem.getId());
        assertTrue(revisionOpt.isPresent(),
                "RevisionState MUST be present for newly created problem; REQUIRES_NEW transaction must not fail foreign key check");
        RevisionState revisionState = revisionOpt.get();
        assertNotNull(revisionState.getNextReviewAt());
        assertTrue(revisionState.getCurrentIntervalDays() >= 2);
    }
}
