package com.example.dsatracker.service;

import com.example.dsatracker.dto.ProblemMetadataDTO;
import com.example.dsatracker.dto.SessionAnalyticsDTO;
import com.example.dsatracker.exception.ResourceNotFoundException;
import com.example.dsatracker.model.ProblemSession;
import com.example.dsatracker.model.SessionEvent;
import com.example.dsatracker.model.SessionEventType;
import com.example.dsatracker.model.User;
import com.example.dsatracker.repository.ProblemSessionRepository;
import com.example.dsatracker.repository.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class SessionAnalyticsService {

    private final ProblemSessionRepository sessionRepository;
    private final UserRepository userRepository;

    public SessionAnalyticsService(
            ProblemSessionRepository sessionRepository,
            UserRepository userRepository) {
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public SessionAnalyticsDTO getSessionAnalytics(String sessionId) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));

        ProblemSession session = sessionRepository.findBySessionIdAndUserId(sessionId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Session not found with ID: " + sessionId));

        return computeAnalytics(session);
    }

    public SessionAnalyticsDTO computeAnalytics(ProblemSession session) {
        // Problem Metadata
        ProblemMetadataDTO problemMeta = null;
        if (session.getProblem() != null) {
            problemMeta = ProblemMetadataDTO.builder()
                    .leetcodeId(session.getProblem().getLeetcodeId())
                    .title(session.getProblem().getTitle())
                    .difficulty(session.getProblem().getDifficulty() != null ? session.getProblem().getDifficulty().name() : null)
                    .url(session.getProblem().getUrl())
                    .build();
        }

        // Time Metrics
        long thinking = session.getThinkingDuration() != null ? session.getThinkingDuration() : 0L;
        long coding = session.getCodingDuration() != null ? session.getCodingDuration() : 0L;
        long totalActive = thinking + coding;
        long timeAway = session.getTotalTimeAway() != null ? session.getTotalTimeAway() : 0L;
        int tabSwitches = session.getTabSwitchCount() != null ? session.getTabSwitchCount() : 0;

        // Process Event Timeline
        int wrongSubmissions = 0;
        int acceptedSubmissions = 0;
        Boolean firstAttemptAccepted = null;
        List<String> hintsOpened = new ArrayList<>();
        boolean eventSolutionViewed = false;
        boolean eventEditorialViewed = false;

        List<SessionEvent> events = session.getEvents();
        if (events != null && !events.isEmpty()) {
            boolean firstSubmissionEncountered = false;

            for (SessionEvent event : events) {
                if (event.getEventType() == SessionEventType.SUBMISSION) {
                    String res = event.getResult();
                    boolean isAccepted = "ACCEPTED".equalsIgnoreCase(res);

                    if (isAccepted) {
                        acceptedSubmissions++;
                    } else {
                        wrongSubmissions++;
                    }

                    if (!firstSubmissionEncountered) {
                        firstAttemptAccepted = isAccepted;
                        firstSubmissionEncountered = true;
                    }
                } else if (event.getEventType() == SessionEventType.HINT_OPENED) {
                    if (event.getHintName() != null && !event.getHintName().isBlank()) {
                        hintsOpened.add(event.getHintName());
                    }
                } else if (event.getEventType() == SessionEventType.SOLUTION_VIEWED) {
                    eventSolutionViewed = true;
                } else if (event.getEventType() == SessionEventType.EDITORIAL_VIEWED) {
                    eventEditorialViewed = true;
                }
            }
        }

        // Fallback / consistency with session flags
        int attempts = session.getAttempts() != null && session.getAttempts() > 0
                ? session.getAttempts()
                : (wrongSubmissions + acceptedSubmissions);

        if (firstAttemptAccepted == null) {
            firstAttemptAccepted = (attempts == 1 && (Boolean.TRUE.equals(session.getSolved()) || acceptedSubmissions > 0));
        }

        int hintCount = !hintsOpened.isEmpty()
                ? hintsOpened.size()
                : (session.getHintOpenCount() != null ? session.getHintOpenCount() : 0);

        boolean hintUsed = Boolean.TRUE.equals(session.getHintOpened()) || hintCount > 0;
        boolean solutionViewed = Boolean.TRUE.equals(session.getSolutionViewed()) || eventSolutionViewed;
        boolean editorialViewed = Boolean.TRUE.equals(session.getEditorialViewed()) || eventEditorialViewed;
        boolean isSolved = Boolean.TRUE.equals(session.getSolved()) || acceptedSubmissions > 0;

        return SessionAnalyticsDTO.builder()
                .sessionId(session.getSessionId())
                .problem(problemMeta)
                .language(session.getLanguage())
                .totalActiveTime(totalActive)
                .thinkingTime(thinking)
                .codingTime(coding)
                .timeAway(timeAway)
                .tabSwitchCount(tabSwitches)
                .attempts(attempts)
                .wrongSubmissionCount(wrongSubmissions)
                .acceptedSubmissionCount(acceptedSubmissions)
                .firstAttemptAccepted(firstAttemptAccepted)
                .hintUsed(hintUsed)
                .hintCount(hintCount)
                .hintsOpened(hintsOpened)
                .solutionViewed(solutionViewed)
                .editorialViewed(editorialViewed)
                .solved(isSolved)
                .build();
    }
}
