package com.example.dsatracker.controller;

import com.example.dsatracker.dto.DifficultyAnalyticsDTO;
import com.example.dsatracker.dto.HistoricalAnalyticsDTO;
import com.example.dsatracker.dto.TimeWindow;
import com.example.dsatracker.dto.TopicAnalyticsDTO;
import com.example.dsatracker.security.JwtAuthenticationFilter;
import com.example.dsatracker.service.HistoricalAnalyticsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AnalyticsController.class)
@AutoConfigureMockMvc(addFilters = false)
class AnalyticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private HistoricalAnalyticsService historicalAnalyticsService;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    @DisplayName("GET /analytics/historical - Defaults to ALL_TIME (200 OK)")
    void testGetHistoricalAnalyticsDefault() throws Exception {
        HistoricalAnalyticsDTO dto = HistoricalAnalyticsDTO.builder()
                .totalProblemsSolved(5)
                .totalSessions(6)
                .averageSolveTime(600000L)
                .averageThinkingTime(120000L)
                .averageCodingTime(480000L)
                .averageAttempts(1.4)
                .firstAttemptSuccessRate(80.0)
                .totalWrongSubmissions(2)
                .totalAcceptedSubmissions(5)
                .hintUsageRate(20.0)
                .solutionUsageRate(0.0)
                .editorialUsageRate(0.0)
                .averageHintsPerProblem(0.2)
                .totalTimeSpent(3600000L)
                .problemsSolvedByDifficulty(Map.of("EASY", 3, "MEDIUM", 2))
                .difficultyAnalytics(Map.of(
                        "EASY", DifficultyAnalyticsDTO.builder()
                                .problemsSolved(3)
                                .averageSolveTime(400000L)
                                .averageAttempts(1.0)
                                .firstAttemptSuccessRate(100.0)
                                .build()
                ))
                .topicAnalytics(Map.of(
                        "Array", TopicAnalyticsDTO.builder()
                                .problemsSolved(3)
                                .averageSolveTime(400000L)
                                .firstAttemptSuccessRate(100.0)
                                .build()
                ))
                .build();

        when(historicalAnalyticsService.getHistoricalAnalytics(eq(TimeWindow.ALL_TIME))).thenReturn(dto);

        mockMvc.perform(get("/analytics/historical")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProblemsSolved").value(5))
                .andExpect(jsonPath("$.totalSessions").value(6))
                .andExpect(jsonPath("$.averageSolveTime").value(600000))
                .andExpect(jsonPath("$.firstAttemptSuccessRate").value(80.0))
                .andExpect(jsonPath("$.difficultyAnalytics.EASY.problemsSolved").value(3))
                .andExpect(jsonPath("$.topicAnalytics.Array.problemsSolved").value(3));
    }

    @Test
    @DisplayName("GET /analytics/historical?timeWindow=LAST_7_DAYS - (200 OK)")
    void testGetHistoricalAnalyticsWithTimeWindow() throws Exception {
        HistoricalAnalyticsDTO dto = HistoricalAnalyticsDTO.builder()
                .totalProblemsSolved(2)
                .totalSessions(2)
                .build();

        when(historicalAnalyticsService.getHistoricalAnalytics(eq(TimeWindow.LAST_7_DAYS))).thenReturn(dto);

        mockMvc.perform(get("/analytics/historical?timeWindow=LAST_7_DAYS")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProblemsSolved").value(2));
    }
}
