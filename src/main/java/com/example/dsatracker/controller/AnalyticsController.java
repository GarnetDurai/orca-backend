package com.example.dsatracker.controller;

import com.example.dsatracker.dto.HistoricalAnalyticsDTO;
import com.example.dsatracker.dto.TimeWindow;
import com.example.dsatracker.service.HistoricalAnalyticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/analytics")
public class AnalyticsController {

    private final HistoricalAnalyticsService historicalAnalyticsService;

    public AnalyticsController(HistoricalAnalyticsService historicalAnalyticsService) {
        this.historicalAnalyticsService = historicalAnalyticsService;
    }

    @GetMapping("/historical")
    public ResponseEntity<HistoricalAnalyticsDTO> getHistoricalAnalytics(
            @RequestParam(required = false, defaultValue = "ALL_TIME") TimeWindow timeWindow) {
        HistoricalAnalyticsDTO response = historicalAnalyticsService.getHistoricalAnalytics(timeWindow);
        return ResponseEntity.ok(response);
    }
}
