package com.example.dsatracker.controller;

import com.example.dsatracker.dto.ConfidenceResponseDTO;
import com.example.dsatracker.dto.HistoricalAnalyticsDTO;
import com.example.dsatracker.dto.TimeWindow;
import com.example.dsatracker.dto.UserPerformanceProfileDTO;
import com.example.dsatracker.service.ConfidenceService;
import com.example.dsatracker.service.HistoricalAnalyticsService;
import com.example.dsatracker.service.UserPerformanceProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/analytics")
public class AnalyticsController {

    private final HistoricalAnalyticsService historicalAnalyticsService;
    private final UserPerformanceProfileService userPerformanceProfileService;
    private final ConfidenceService confidenceService;

    public AnalyticsController(
            HistoricalAnalyticsService historicalAnalyticsService,
            UserPerformanceProfileService userPerformanceProfileService,
            ConfidenceService confidenceService) {
        this.historicalAnalyticsService = historicalAnalyticsService;
        this.userPerformanceProfileService = userPerformanceProfileService;
        this.confidenceService = confidenceService;
    }

    @GetMapping("/historical")
    public ResponseEntity<HistoricalAnalyticsDTO> getHistoricalAnalytics(
            @RequestParam(required = false, defaultValue = "ALL_TIME") TimeWindow timeWindow) {
        HistoricalAnalyticsDTO response = historicalAnalyticsService.getHistoricalAnalytics(timeWindow);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/profile")
    public ResponseEntity<UserPerformanceProfileDTO> getUserProfile() {
        UserPerformanceProfileDTO response = userPerformanceProfileService.getUserProfile();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/confidence/{problemId}")
    public ResponseEntity<ConfidenceResponseDTO> getConfidenceForProblem(
            @PathVariable Long problemId) {
        ConfidenceResponseDTO response = confidenceService.getConfidenceForProblem(problemId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/confidence")
    public ResponseEntity<List<ConfidenceResponseDTO>> getAllConfidence() {
        List<ConfidenceResponseDTO> response = confidenceService.getAllConfidenceForUser();
        return ResponseEntity.ok(response);
    }
}
