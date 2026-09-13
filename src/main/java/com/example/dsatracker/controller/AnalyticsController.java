package com.example.dsatracker.controller;

import com.example.dsatracker.dto.*;
import com.example.dsatracker.service.ConfidenceService;
import com.example.dsatracker.service.HistoricalAnalyticsService;
import com.example.dsatracker.service.RevisionScheduler;
import com.example.dsatracker.service.UserPerformanceProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneId;
import java.util.List;

@RestController
@RequestMapping("/analytics")
public class AnalyticsController {

    private final HistoricalAnalyticsService historicalAnalyticsService;
    private final UserPerformanceProfileService userPerformanceProfileService;
    private final ConfidenceService confidenceService;
    private final RevisionScheduler revisionScheduler;

    public AnalyticsController(
            HistoricalAnalyticsService historicalAnalyticsService,
            UserPerformanceProfileService userPerformanceProfileService,
            ConfidenceService confidenceService,
            RevisionScheduler revisionScheduler) {
        this.historicalAnalyticsService = historicalAnalyticsService;
        this.userPerformanceProfileService = userPerformanceProfileService;
        this.confidenceService = confidenceService;
        this.revisionScheduler = revisionScheduler;
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

    @GetMapping("/revisions")
    public ResponseEntity<List<RevisionStateDTO>> getAllRevisions() {
        List<RevisionStateDTO> response = revisionScheduler.getAllRevisionsForUser();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/revisions/today")
    public ResponseEntity<ReviewQueueResponseDTO> getTodayReviewQueue(
            @RequestParam(required = false) String timeZone) {
        ZoneId zone = ZoneId.systemDefault();
        if (timeZone != null && !timeZone.isBlank()) {
            try {
                zone = ZoneId.of(timeZone.trim());
            } catch (Exception ignored) {}
        }
        ReviewQueueResponseDTO response = revisionScheduler.getTodayReviewQueue(zone);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/revisions/{problemId}")
    public ResponseEntity<RevisionStateDTO> getRevisionForProblem(
            @PathVariable Long problemId) {
        RevisionStateDTO response = revisionScheduler.getRevisionForProblem(problemId);
        return ResponseEntity.ok(response);
    }
}
