package com.example.dsatracker.controller;

import com.example.dsatracker.dto.*;
import com.example.dsatracker.security.JwtAuthenticationFilter;
import com.example.dsatracker.service.ConfidenceService;
import com.example.dsatracker.service.HistoricalAnalyticsService;
import com.example.dsatracker.service.UserPerformanceProfileService;
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
    private UserPerformanceProfileService userPerformanceProfileService;

    @MockitoBean
    private ConfidenceService confidenceService;

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

    @Test
    @DisplayName("GET /analytics/profile - Returns UserPerformanceProfileDTO (200 OK)")
    void testGetUserProfileSuccess() throws Exception {
        ProfileOverallDTO overall = ProfileOverallDTO.builder()
                .uniqueProblemsSolved(10)
                .totalSolvedSessions(12)
                .totalSessions(15)
                .totalSubmissions(25)
                .averageAttempts(1.5)
                .firstAttemptSuccessRate(75.0)
                .averageSolveTime(600000L)
                .averageThinkingTime(120000L)
                .averageCodingTime(480000L)
                .totalTimeSpent(7200000L)
                .hintUsageRate(20.0)
                .solutionUsageRate(10.0)
                .editorialUsageRate(5.0)
                .build();

        DifficultyProfileDTO easyDiff = DifficultyProfileDTO.builder()
                .uniqueProblemsSolved(5)
                .totalSolvedSessions(6)
                .sessionCount(7)
                .averageSolveTime(300000L)
                .averageAttempts(1.2)
                .firstAttemptSuccessRate(83.33)
                .hintUsageRate(0.0)
                .solutionUsageRate(0.0)
                .editorialUsageRate(0.0)
                .build();

        TopicProfileDTO arrayTopic = TopicProfileDTO.builder()
                .topic("Array")
                .uniqueProblemsSolved(4)
                .totalSolvedSessions(5)
                .sessionCount(6)
                .averageSolveTime(400000L)
                .averageAttempts(1.3)
                .firstAttemptSuccessRate(80.0)
                .hintUsageRate(20.0)
                .solutionUsageRate(0.0)
                .editorialUsageRate(0.0)
                .build();

        RecentTrendsDTO trends = RecentTrendsDTO.builder()
                .last7Days(TrendComparisonDTO.builder()
                        .timeWindow("LAST_7_DAYS")
                        .deltaProblemsSolved(2)
                        .currentPeriod(TrendPeriodMetricsDTO.builder().uniqueProblemsSolved(3).build())
                        .previousPeriod(TrendPeriodMetricsDTO.builder().uniqueProblemsSolved(1).build())
                        .build())
                .last30Days(TrendComparisonDTO.builder()
                        .timeWindow("LAST_30_DAYS")
                        .deltaProblemsSolved(5)
                        .currentPeriod(TrendPeriodMetricsDTO.builder().uniqueProblemsSolved(8).build())
                        .previousPeriod(TrendPeriodMetricsDTO.builder().uniqueProblemsSolved(3).build())
                        .build())
                .build();

        UserPerformanceProfileDTO profileDTO = UserPerformanceProfileDTO.builder()
                .overall(overall)
                .difficultyPerformance(Map.of("EASY", easyDiff))
                .topicPerformance(Map.of("Array", arrayTopic))
                .recentTrends(trends)
                .build();

        when(userPerformanceProfileService.getUserProfile()).thenReturn(profileDTO);

        mockMvc.perform(get("/analytics/profile")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overall.uniqueProblemsSolved").value(10))
                .andExpect(jsonPath("$.overall.totalSolvedSessions").value(12))
                .andExpect(jsonPath("$.overall.totalSessions").value(15))
                .andExpect(jsonPath("$.overall.totalSubmissions").value(25))
                .andExpect(jsonPath("$.difficultyPerformance.EASY.uniqueProblemsSolved").value(5))
                .andExpect(jsonPath("$.topicPerformance.Array.uniqueProblemsSolved").value(4))
                .andExpect(jsonPath("$.recentTrends.last7Days.deltaProblemsSolved").value(2));
    }

    @Test
    @DisplayName("GET /analytics/confidence/{problemId} - Returns ConfidenceResponseDTO (200 OK)")
    void testGetConfidenceForProblemSuccess() throws Exception {
        ConfidenceResponseDTO dto = ConfidenceResponseDTO.builder()
                .problemId(1L)
                .leetcodeId(1)
                .problemTitle("Two Sum")
                .difficulty("EASY")
                .currentConfidence(85.5)
                .masteryScore(100.0)
                .independenceScore(100.0)
                .retentionStrength(40.0)
                .successfulSolveCount(1)
                .independentSolveCount(1)
                .algorithmVersion("V1")
                .history(java.util.List.of(
                        ConfidenceHistoryDTO.builder()
                                .id(101L)
                                .previousConfidence(0.0)
                                .newConfidence(85.5)
                                .masteryContribution(100.0)
                                .independenceContribution(100.0)
                                .retentionContribution(30.0)
                                .assistanceEffect("NO_ASSISTANCE")
                                .awayTimeEffect(0.0)
                                .algorithmVersion("V1")
                                .build()
                ))
                .build();

        when(confidenceService.getConfidenceForProblem(eq(1L))).thenReturn(dto);

        mockMvc.perform(get("/analytics/confidence/1")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.problemId").value(1))
                .andExpect(jsonPath("$.problemTitle").value("Two Sum"))
                .andExpect(jsonPath("$.currentConfidence").value(85.5))
                .andExpect(jsonPath("$.masteryScore").value(100.0))
                .andExpect(jsonPath("$.independenceScore").value(100.0))
                .andExpect(jsonPath("$.algorithmVersion").value("V1"))
                .andExpect(jsonPath("$.history[0].assistanceEffect").value("NO_ASSISTANCE"));
    }

    @Test
    @DisplayName("GET /analytics/confidence - Returns List<ConfidenceResponseDTO> (200 OK)")
    void testGetAllConfidenceSuccess() throws Exception {
        ConfidenceResponseDTO dto1 = ConfidenceResponseDTO.builder()
                .problemId(1L)
                .leetcodeId(1)
                .problemTitle("Two Sum")
                .difficulty("EASY")
                .currentConfidence(85.5)
                .masteryScore(100.0)
                .independenceScore(100.0)
                .retentionStrength(40.0)
                .successfulSolveCount(1)
                .independentSolveCount(1)
                .algorithmVersion("V1")
                .build();

        ConfidenceResponseDTO dto2 = ConfidenceResponseDTO.builder()
                .problemId(2L)
                .leetcodeId(2)
                .problemTitle("Add Two Numbers")
                .difficulty("MEDIUM")
                .currentConfidence(55.0)
                .masteryScore(85.0)
                .independenceScore(70.0)
                .retentionStrength(15.0)
                .successfulSolveCount(1)
                .independentSolveCount(0)
                .algorithmVersion("V1")
                .build();

        when(confidenceService.getAllConfidenceForUser()).thenReturn(java.util.List.of(dto1, dto2));

        mockMvc.perform(get("/analytics/confidence")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].problemId").value(1))
                .andExpect(jsonPath("$[0].currentConfidence").value(85.5))
                .andExpect(jsonPath("$[1].problemId").value(2))
                .andExpect(jsonPath("$[1].currentConfidence").value(55.0));
    }
}
