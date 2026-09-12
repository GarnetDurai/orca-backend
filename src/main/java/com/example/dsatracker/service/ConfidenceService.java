package com.example.dsatracker.service;

import com.example.dsatracker.dto.ConfidenceHistoryDTO;
import com.example.dsatracker.dto.ConfidenceResponseDTO;
import com.example.dsatracker.dto.SessionAnalyticsDTO;
import com.example.dsatracker.exception.ResourceNotFoundException;
import com.example.dsatracker.model.*;
import com.example.dsatracker.repository.ConfidenceStateRepository;
import com.example.dsatracker.repository.ProblemSessionRepository;
import com.example.dsatracker.repository.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class ConfidenceService {

    public static final String ALGORITHM_VERSION = "V1";

    private final ConfidenceStateRepository confidenceStateRepository;
    private final ProblemSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final SessionAnalyticsService sessionAnalyticsService;

    public ConfidenceService(
            ConfidenceStateRepository confidenceStateRepository,
            ProblemSessionRepository sessionRepository,
            UserRepository userRepository,
            SessionAnalyticsService sessionAnalyticsService) {
        this.confidenceStateRepository = confidenceStateRepository;
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.sessionAnalyticsService = sessionAnalyticsService;
    }

    @Transactional
    public ConfidenceResponseDTO updateConfidenceForSession(ProblemSession session) {
        if (session == null || session.getUser() == null || session.getProblem() == null) {
            throw new IllegalArgumentException("Session, User, and Problem must not be null.");
        }

        User user = session.getUser();
        Problem problem = session.getProblem();

        ConfidenceState state = confidenceStateRepository.findByUserIdAndProblemId(user.getId(), problem.getId())
                .orElseGet(() -> ConfidenceState.builder()
                        .user(user)
                        .problem(problem)
                        .masteryScore(0.0)
                        .independenceScore(0.0)
                        .retentionStrength(0.0)
                        .currentConfidence(0.0)
                        .successfulSolveCount(0)
                        .independentSolveCount(0)
                        .lastConfidenceUpdateAt(session.getSessionStartedAt() != null ? session.getSessionStartedAt() : LocalDateTime.now())
                        .algorithmVersion(ALGORITHM_VERSION)
                        .build());

        SessionAnalyticsDTO analytics = sessionAnalyticsService.computeAnalytics(session);
        applySessionToConfidenceState(state, session, analytics);

        ConfidenceState savedState = confidenceStateRepository.save(state);
        return mapToDTO(savedState, true);
    }

    @Transactional(readOnly = true)
    public ConfidenceResponseDTO getConfidenceForProblem(Long problemId) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));

        ConfidenceState state = confidenceStateRepository.findWithHistoryByUserIdAndProblemId(user.getId(), problemId)
                .orElseThrow(() -> new ResourceNotFoundException("Confidence state not found for problem ID: " + problemId));

        return mapToDTO(state, true);
    }

    @Transactional(readOnly = true)
    public List<ConfidenceResponseDTO> getAllConfidenceForUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));

        List<ConfidenceState> states = confidenceStateRepository.findByUserIdOrderByLastConfidenceUpdateAtDesc(user.getId());
        return states.stream()
                .map(s -> mapToDTO(s, false))
                .toList();
    }

    private void applySessionToConfidenceState(
            ConfidenceState state,
            ProblemSession session,
            SessionAnalyticsDTO analytics) {

        double previousConfidence = state.getCurrentConfidence() != null ? state.getCurrentConfidence() : 0.0;
        LocalDateTime now = session.getSessionStartedAt() != null ? session.getSessionStartedAt() : LocalDateTime.now();

        boolean isSolved = Boolean.TRUE.equals(analytics.getSolved());
        long thinkingTime = analytics.getThinkingTime() != null ? analytics.getThinkingTime() : 0L;
        long codingTime = analytics.getCodingTime() != null ? analytics.getCodingTime() : 0L;
        long activeSolvingTime = thinkingTime + codingTime;
        long timeAway = analytics.getTimeAway() != null ? analytics.getTimeAway() : 0L;

        boolean hintUsed = Boolean.TRUE.equals(analytics.getHintUsed());
        int hintCount = analytics.getHintCount() != null ? analytics.getHintCount() : 0;
        boolean solutionViewed = Boolean.TRUE.equals(analytics.getSolutionViewed());
        boolean editorialViewed = Boolean.TRUE.equals(analytics.getEditorialViewed());
        boolean isIndependent = !hintUsed && !solutionViewed && !editorialViewed;

        int wrongSubmissions = analytics.getWrongSubmissionCount() != null ? analytics.getWrongSubmissionCount() : 0;

        // E. Away-time ratio and reliability weight
        double awayRatio = (activeSolvingTime + timeAway > 0)
                ? ((double) timeAway / (activeSolvingTime + timeAway))
                : 0.0;
        double awayReliabilityWeight = computeAwayReliabilityWeight(awayRatio);

        if (!isSolved) {
            // Incomplete / unsolved session: bounded dampening without destroying confidence
            double newConfidence = Math.max(0.0, roundToTwoDecimals(previousConfidence - 5.0));
            state.setCurrentConfidence(newConfidence);
            state.setLastConfidenceUpdateAt(now);

            ConfidenceHistory historyEntry = ConfidenceHistory.builder()
                    .timestamp(now)
                    .previousConfidence(previousConfidence)
                    .newConfidence(newConfidence)
                    .masteryContribution(0.0)
                    .independenceContribution(0.0)
                    .retentionContribution(0.0)
                    .assistanceEffect("UNSOLVED_SESSION")
                    .awayTimeEffect(roundToTwoDecimals(awayRatio))
                    .algorithmVersion(ALGORITHM_VERSION)
                    .build();
            state.addHistory(historyEntry);
            return;
        }

        // A. Mastery Score
        double sessionMastery = Math.max(50.0, 100.0 - (wrongSubmissions * 15.0));

        // B. Independence Score & Effect
        double sessionIndependence;
        String assistanceEffect;
        if (solutionViewed) {
            sessionIndependence = 15.0;
            assistanceEffect = "SOLUTION_VIEWED";
        } else if (editorialViewed) {
            sessionIndependence = 35.0;
            assistanceEffect = "EDITORIAL_VIEWED";
        } else if (hintUsed) {
            if (hintCount >= 2) {
                sessionIndependence = 60.0;
                assistanceEffect = "MULTIPLE_HINTS_USED";
            } else {
                sessionIndependence = 70.0;
                assistanceEffect = "HINT_USED";
            }
        } else {
            sessionIndependence = 100.0;
            assistanceEffect = "NO_ASSISTANCE";
        }

        // D. Retention Evidence
        double retentionEvidence = computeRetentionEvidence(state.getLastSuccessfulSolveAt(), now);

        // C. Efficiency Modifier
        double efficiencyModifier = computeEfficiencyModifier(session, activeSolvingTime);

        // Raw Session Score
        double rawSessionScore = Math.max(0.0, Math.min(100.0,
                (0.45 * sessionMastery) +
                (0.45 * sessionIndependence) +
                (0.10 * retentionEvidence) +
                efficiencyModifier));

        double independenceFactor = sessionIndependence / 100.0;

        // Confidence calculation
        double newConfidence;
        boolean isFirstSolve = state.getSuccessfulSolveCount() == null || state.getSuccessfulSolveCount() == 0;

        if (isFirstSolve) {
            newConfidence = rawSessionScore * independenceFactor * 0.90 * awayReliabilityWeight;
        } else {
            double sessionTarget = rawSessionScore * (isIndependent ? 1.0 : independenceFactor);
            double alpha = 0.60 * awayReliabilityWeight;
            newConfidence = ((1.0 - alpha) * previousConfidence) + (alpha * sessionTarget);
        }

        newConfidence = Math.max(0.0, Math.min(100.0, roundToTwoDecimals(newConfidence)));

        // Update Retention Strength
        double prevRetention = state.getRetentionStrength() != null ? state.getRetentionStrength() : 0.0;
        double newRetentionStrength = isFirstSolve
                ? (isIndependent ? 40.0 : 15.0)
                : Math.min(100.0, (prevRetention * 0.50) + (retentionEvidence * 0.50 * independenceFactor));
        newRetentionStrength = roundToTwoDecimals(newRetentionStrength);

        // Update Mastery and Independence rolling scores
        double prevMastery = state.getMasteryScore() != null ? state.getMasteryScore() : 0.0;
        double prevIndep = state.getIndependenceScore() != null ? state.getIndependenceScore() : 0.0;
        double updatedMastery = isFirstSolve ? sessionMastery : roundToTwoDecimals((prevMastery * 0.4) + (sessionMastery * 0.6));
        double updatedIndependence = isFirstSolve ? sessionIndependence : roundToTwoDecimals((prevIndep * 0.4) + (sessionIndependence * 0.6));

        // Update State
        state.setMasteryScore(updatedMastery);
        state.setIndependenceScore(updatedIndependence);
        state.setRetentionStrength(newRetentionStrength);
        state.setCurrentConfidence(newConfidence);
        state.setLastSuccessfulSolveAt(now);
        state.setSuccessfulSolveCount((state.getSuccessfulSolveCount() != null ? state.getSuccessfulSolveCount() : 0) + 1);
        if (isIndependent) {
            state.setIndependentSolveCount((state.getIndependentSolveCount() != null ? state.getIndependentSolveCount() : 0) + 1);
        }
        state.setLastConfidenceUpdateAt(now);
        state.setAlgorithmVersion(ALGORITHM_VERSION);

        // History entry
        ConfidenceHistory historyEntry = ConfidenceHistory.builder()
                .timestamp(now)
                .previousConfidence(previousConfidence)
                .newConfidence(newConfidence)
                .masteryContribution(roundToTwoDecimals(sessionMastery))
                .independenceContribution(roundToTwoDecimals(sessionIndependence))
                .retentionContribution(roundToTwoDecimals(retentionEvidence))
                .assistanceEffect(assistanceEffect)
                .awayTimeEffect(roundToTwoDecimals(awayRatio))
                .algorithmVersion(ALGORITHM_VERSION)
                .build();
        state.addHistory(historyEntry);
    }

    private double computeAwayReliabilityWeight(double awayRatio) {
        if (awayRatio <= 0.15) {
            return 1.0;
        } else if (awayRatio <= 0.50) {
            return 1.0 - (0.5 * (awayRatio - 0.15));
        } else {
            return Math.max(0.40, 1.0 - (awayRatio - 0.15));
        }
    }

    private double computeRetentionEvidence(LocalDateTime lastSolve, LocalDateTime currentSolve) {
        if (lastSolve == null) {
            return 30.0; // Baseline initial retention
        }

        double hours = ChronoUnit.MINUTES.between(lastSolve, currentSolve) / 60.0;
        double days = hours / 24.0;

        if (days < 1.0) {
            return 40.0; // Immediate repeat: useful, but weak long-term retention proof
        } else if (days <= 7.0) {
            return 65.0; // Moderate interval
        } else if (days <= 30.0) {
            return 85.0; // Strong retention interval
        } else {
            return 100.0; // Very strong interval
        }
    }

    private double computeEfficiencyModifier(ProblemSession session, long currentActiveTime) {
        if (currentActiveTime <= 0 || session.getUser() == null || session.getProblem() == null) {
            return 0.0;
        }

        // Find previous sessions for the same user and problem
        List<ProblemSession> previousSessions = sessionRepository
                .findByUserIdAndProblemId(session.getUser().getId(), session.getProblem().getId());

        Long previousSolveDuration = null;
        for (ProblemSession s : previousSessions) {
            if (!s.getSessionId().equals(session.getSessionId()) && Boolean.TRUE.equals(s.getSolved())) {
                long prevTime = (s.getThinkingDuration() != null ? s.getThinkingDuration() : 0L) +
                                (s.getCodingDuration() != null ? s.getCodingDuration() : 0L);
                if (prevTime > 0) {
                    previousSolveDuration = prevTime;
                    break;
                }
            }
        }

        if (previousSolveDuration == null || previousSolveDuration <= 0) {
            return 0.0;
        }

        double ratio = (double) currentActiveTime / previousSolveDuration;
        if (ratio < 0.75) {
            return 5.0; // Significantly faster: positive improvement
        } else if (ratio > 1.35) {
            return -5.0; // Significantly slower: bounded negative evidence
        }
        return 0.0;
    }

    private ConfidenceResponseDTO mapToDTO(ConfidenceState state, boolean includeHistory) {
        List<ConfidenceHistoryDTO> historyDTOs = new ArrayList<>();
        if (includeHistory && state.getHistory() != null) {
            for (ConfidenceHistory h : state.getHistory()) {
                historyDTOs.add(ConfidenceHistoryDTO.builder()
                        .id(h.getId())
                        .timestamp(h.getTimestamp())
                        .previousConfidence(h.getPreviousConfidence())
                        .newConfidence(h.getNewConfidence())
                        .masteryContribution(h.getMasteryContribution())
                        .independenceContribution(h.getIndependenceContribution())
                        .retentionContribution(h.getRetentionContribution())
                        .assistanceEffect(h.getAssistanceEffect())
                        .awayTimeEffect(h.getAwayTimeEffect())
                        .algorithmVersion(h.getAlgorithmVersion())
                        .build());
            }
        }

        Problem p = state.getProblem();
        return ConfidenceResponseDTO.builder()
                .problemId(p != null ? p.getId() : null)
                .leetcodeId(p != null ? p.getLeetcodeId() : null)
                .problemTitle(p != null ? p.getTitle() : null)
                .difficulty(p != null && p.getDifficulty() != null ? p.getDifficulty().name() : null)
                .currentConfidence(state.getCurrentConfidence())
                .masteryScore(state.getMasteryScore())
                .independenceScore(state.getIndependenceScore())
                .retentionStrength(state.getRetentionStrength())
                .successfulSolveCount(state.getSuccessfulSolveCount())
                .independentSolveCount(state.getIndependentSolveCount())
                .lastSuccessfulSolveAt(state.getLastSuccessfulSolveAt())
                .lastConfidenceUpdateAt(state.getLastConfidenceUpdateAt())
                .algorithmVersion(state.getAlgorithmVersion())
                .history(historyDTOs)
                .build();
    }

    private double roundToTwoDecimals(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
