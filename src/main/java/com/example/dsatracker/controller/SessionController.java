package com.example.dsatracker.controller;

import com.example.dsatracker.dto.ProblemSessionDetailsDTO;
import com.example.dsatracker.dto.ProblemSessionRequestDTO;
import com.example.dsatracker.dto.ProblemSessionResponseDTO;
import com.example.dsatracker.dto.SessionAnalyticsDTO;
import com.example.dsatracker.service.SessionAnalyticsService;
import com.example.dsatracker.service.SessionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/sessions")
public class SessionController {

    private final SessionService sessionService;
    private final SessionAnalyticsService analyticsService;

    public SessionController(
            SessionService sessionService,
            SessionAnalyticsService analyticsService) {
        this.sessionService = sessionService;
        this.analyticsService = analyticsService;
    }

    @PostMapping
    public ResponseEntity<ProblemSessionResponseDTO> ingestSession(
            @Valid @RequestBody ProblemSessionRequestDTO request) {
        ProblemSessionResponseDTO response = sessionService.ingestSession(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @GetMapping
    public ResponseEntity<List<ProblemSessionDetailsDTO>> getUserSessions() {
        List<ProblemSessionDetailsDTO> response = sessionService.getUserSessions();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<ProblemSessionDetailsDTO> getSessionById(
            @PathVariable String sessionId) {
        ProblemSessionDetailsDTO response = sessionService.getSessionById(sessionId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{sessionId}/analytics")
    public ResponseEntity<SessionAnalyticsDTO> getSessionAnalytics(
            @PathVariable String sessionId) {
        SessionAnalyticsDTO response = analyticsService.getSessionAnalytics(sessionId);
        return ResponseEntity.ok(response);
    }
}
