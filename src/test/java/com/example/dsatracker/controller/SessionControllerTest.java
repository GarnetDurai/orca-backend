package com.example.dsatracker.controller;

import com.example.dsatracker.dto.*;
import com.example.dsatracker.exception.ResourceNotFoundException;
import com.example.dsatracker.model.SessionEventType;
import com.example.dsatracker.security.JwtAuthenticationFilter;
import com.example.dsatracker.service.SessionAnalyticsService;
import com.example.dsatracker.service.SessionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SessionController.class)
@AutoConfigureMockMvc(addFilters = false)
class SessionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SessionService sessionService;

    @MockitoBean
    private SessionAnalyticsService analyticsService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    @DisplayName("POST /sessions - Success 201 Created")
    void testIngestSessionSuccess() throws Exception {
        ProblemSessionRequestDTO request = ProblemSessionRequestDTO.builder()
                .sessionId("sess-123")
                .problem(ProblemMetadataDTO.builder()
                        .leetcodeId(1)
                        .title("Two Sum")
                        .difficulty("EASY")
                        .url("https://leetcode.com/problems/two-sum/")
                        .build())
                .sessionStartedAt(1725040000000L)
                .thinkingDuration(120000L)
                .codingDuration(580000L)
                .attempts(1)
                .solved(true)
                .events(List.of(
                        SessionEventDTO.builder()
                                .type(SessionEventType.CODING_STARTED)
                                .timestamp(1725040120000L)
                                .build()
                ))
                .build();

        ProblemSessionResponseDTO response = ProblemSessionResponseDTO.builder()
                .sessionId("sess-123")
                .status("SAVED")
                .problemId(10L)
                .leetcodeId(1)
                .solved(true)
                .attempts(1)
                .eventCount(1)
                .createdAt(LocalDateTime.now())
                .build();

        when(sessionService.ingestSession(any(ProblemSessionRequestDTO.class))).thenReturn(response);

        mockMvc.perform(post("/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").value("sess-123"))
                .andExpect(jsonPath("$.status").value("SAVED"))
                .andExpect(jsonPath("$.leetcodeId").value(1))
                .andExpect(jsonPath("$.eventCount").value(1));
    }

    @Test
    @DisplayName("POST /sessions - Validation failure on missing sessionId (400 Bad Request)")
    void testValidationMissingSessionId() throws Exception {
        ProblemSessionRequestDTO request = ProblemSessionRequestDTO.builder()
                .sessionId("") // blank
                .sessionStartedAt(1725040000000L)
                .solved(true)
                .build();

        mockMvc.perform(post("/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /sessions - Validation failure on negative duration (400 Bad Request)")
    void testValidationNegativeDuration() throws Exception {
        ProblemSessionRequestDTO request = ProblemSessionRequestDTO.builder()
                .sessionId("sess-negative")
                .sessionStartedAt(1725040000000L)
                .codingDuration(-500L) // negative
                .solved(true)
                .build();

        mockMvc.perform(post("/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /sessions - Validation failure on negative attempts (400 Bad Request)")
    void testValidationNegativeAttempts() throws Exception {
        ProblemSessionRequestDTO request = ProblemSessionRequestDTO.builder()
                .sessionId("sess-attempts")
                .sessionStartedAt(1725040000000L)
                .attempts(-1) // negative
                .solved(true)
                .build();

        mockMvc.perform(post("/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /sessions - Success 200 OK")
    void testGetUserSessionsSuccess() throws Exception {
        ProblemSessionDetailsDTO session = ProblemSessionDetailsDTO.builder()
                .sessionId("sess-123")
                .problem(ProblemMetadataDTO.builder().leetcodeId(1).title("Two Sum").build())
                .sessionStartedAt(1725040000000L)
                .attempts(1)
                .solved(true)
                .events(List.of(
                        SessionEventDTO.builder().type(SessionEventType.CODING_STARTED).timestamp(1725040120000L).build()
                ))
                .build();

        when(sessionService.getUserSessions()).thenReturn(List.of(session));

        mockMvc.perform(get("/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sessionId").value("sess-123"))
                .andExpect(jsonPath("$[0].problem.leetcodeId").value(1))
                .andExpect(jsonPath("$[0].events[0].type").value("CODING_STARTED"));
    }

    @Test
    @DisplayName("GET /sessions/{sessionId} - Success 200 OK")
    void testGetSessionByIdSuccess() throws Exception {
        ProblemSessionDetailsDTO session = ProblemSessionDetailsDTO.builder()
                .sessionId("sess-123")
                .problem(ProblemMetadataDTO.builder().leetcodeId(1).title("Two Sum").build())
                .sessionStartedAt(1725040000000L)
                .attempts(1)
                .solved(true)
                .build();

        when(sessionService.getSessionById("sess-123")).thenReturn(session);

        mockMvc.perform(get("/sessions/sess-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("sess-123"))
                .andExpect(jsonPath("$.problem.leetcodeId").value(1));
    }

    @Test
    @DisplayName("GET /sessions/{sessionId} - Not Found (404)")
    void testGetSessionByIdNotFound() throws Exception {
        when(sessionService.getSessionById("sess-other-user"))
                .thenThrow(new ResourceNotFoundException("Session not found with ID: sess-other-user"));

        mockMvc.perform(get("/sessions/sess-other-user"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /sessions/{sessionId}/analytics - Success 200 OK")
    void testGetSessionAnalyticsSuccess() throws Exception {
        SessionAnalyticsDTO analytics = SessionAnalyticsDTO.builder()
                .sessionId("sess-123")
                .problem(ProblemMetadataDTO.builder().leetcodeId(1).title("Two Sum").difficulty("EASY").build())
                .language("java")
                .totalActiveTime(20000L)
                .thinkingTime(8000L)
                .codingTime(12000L)
                .timeAway(0L)
                .tabSwitchCount(0)
                .attempts(1)
                .wrongSubmissionCount(0)
                .acceptedSubmissionCount(1)
                .firstAttemptAccepted(true)
                .hintUsed(false)
                .hintCount(0)
                .hintsOpened(List.of())
                .solutionViewed(false)
                .editorialViewed(false)
                .solved(true)
                .build();

        when(analyticsService.getSessionAnalytics("sess-123")).thenReturn(analytics);

        mockMvc.perform(get("/sessions/sess-123/analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("sess-123"))
                .andExpect(jsonPath("$.totalActiveTime").value(20000))
                .andExpect(jsonPath("$.firstAttemptAccepted").value(true))
                .andExpect(jsonPath("$.wrongSubmissionCount").value(0))
                .andExpect(jsonPath("$.acceptedSubmissionCount").value(1));
    }

    @Test
    @DisplayName("GET /sessions/{sessionId}/analytics - Not Found (404)")
    void testGetSessionAnalyticsNotFound() throws Exception {
        when(analyticsService.getSessionAnalytics("sess-other-user"))
                .thenThrow(new ResourceNotFoundException("Session not found with ID: sess-other-user"));

        mockMvc.perform(get("/sessions/sess-other-user/analytics"))
                .andExpect(status().isNotFound());
    }
}
