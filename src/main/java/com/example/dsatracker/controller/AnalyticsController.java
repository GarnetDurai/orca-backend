package com.example.dsatracker.controller;

import com.example.dsatracker.dto.HistoricalAnalyticsDTO;
import com.example.dsatracker.dto.TimeWindow;
import com.example.dsatracker.dto.UserPerformanceProfileDTO;
import com.example.dsatracker.service.HistoricalAnalyticsService;
import com.example.dsatracker.service.UserPerformanceProfileService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/analytics")
public class AnalyticsController {

    private final HistoricalAnalyticsService historicalAnalyticsService;
    private final UserPerformanceProfileService userPerformanceProfileService;

    public AnalyticsController(
            HistoricalAnalyticsService historicalAnalyticsService,
            UserPerformanceProfileService userPerformanceProfileService) {
        this.historicalAnalyticsService = historicalAnalyticsService;
        this.userPerformanceProfileService = userPerformanceProfileService;
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
}
