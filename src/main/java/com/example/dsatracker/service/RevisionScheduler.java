package com.example.dsatracker.service;

import com.example.dsatracker.dto.*;
import com.example.dsatracker.exception.ResourceNotFoundException;
import com.example.dsatracker.model.*;
import com.example.dsatracker.repository.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class RevisionScheduler {

    public static final String ALGORITHM_VERSION = "SRS_V1";

    private final RevisionStateRepository revisionStateRepository;
    private final RevisionHistoryRepository revisionHistoryRepository;
    private final ConfidenceStateRepository confidenceStateRepository;
    private final ProblemSessionRepository sessionRepository;
    private final UserRepository userRepository;
    private final SessionAnalyticsService sessionAnalyticsService;
    private final ReviewOutcomeClassifier outcomeClassifier;
    private final RevisionEligibilityService eligibilityService;
    private final RevisionPriorityService priorityService;
    private final ReviewCapacityService capacityService;

    public RevisionScheduler(
            RevisionStateRepository revisionStateRepository,
            RevisionHistoryRepository revisionHistoryRepository,
            ConfidenceStateRepository confidenceStateRepository,
            ProblemSessionRepository sessionRepository,
            UserRepository userRepository,
            SessionAnalyticsService sessionAnalyticsService,
            ReviewOutcomeClassifier outcomeClassifier,
            RevisionEligibilityService eligibilityService,
            RevisionPriorityService priorityService,
            ReviewCapacityService capacityService) {
        this.revisionStateRepository = revisionStateRepository;
        this.revisionHistoryRepository = revisionHistoryRepository;
        this.confidenceStateRepository = confidenceStateRepository;
        this.sessionRepository = sessionRepository;
        this.userRepository = userRepository;
        this.sessionAnalyticsService = sessionAnalyticsService;
        this.outcomeClassifier = outcomeClassifier;
        this.eligibilityService = eligibilityService;
        this.priorityService = priorityService;
        this.capacityService = capacityService;
    }

    @Transactional
    public RevisionStateDTO processSession(ProblemSession session) {
        if (session == null || session.getUser() == null || session.getProblem() == null) {
            return null;
        }

        String sessionId = session.getSessionId();

        // 1. Idempotency Check: if this session was already processed into RevisionHistory, do not update again
        if (revisionHistoryRepository.existsBySourceSessionId(sessionId)) {
            return revisionStateRepository.findByUserIdAndProblemId(session.getUser().getId(), session.getProblem().getId())
                    .map(s -> mapToDTO(s, true))
                    .orElse(null);
        }

        User user = session.getUser();
        Problem problem = session.getProblem();
        LocalDateTime sessionTime = session.getSessionStartedAt() != null
                ? session.getSessionStartedAt()
                : LocalDateTime.now();

        Optional<RevisionState> existingOpt = revisionStateRepository.findByUserIdAndProblemId(user.getId(), problem.getId());

        // 2. Initial Solve: Problem enters SRS for the first time
        if (existingOpt.isEmpty()) {
            if (!eligibilityService.isEligibleForInitialSRS(session, false)) {
                return null; // Unsolved problems do not enter SRS
            }

            ConfidenceState confidenceState = confidenceStateRepository.findByUserIdAndProblemId(user.getId(), problem.getId())
                    .orElse(null);

            double confidence = confidenceState != null && confidenceState.getCurrentConfidence() != null
                    ? confidenceState.getCurrentConfidence()
                    : 0.0;
            double retention = confidenceState != null && confidenceState.getRetentionStrength() != null
                    ? confidenceState.getRetentionStrength()
                    : 0.0;

            // Initial review interval calculation: S = 0.70 * (C/100) + 0.30 * (R/100)
            double schedulingStrength = priorityService.calculateSchedulingStrength(confidence, retention);
            int initialIntervalDays = (int) Math.max(2, Math.min(7, Math.round(2.0 + (5.0 * schedulingStrength))));
            LocalDateTime nextReviewAt = sessionTime.plusDays(initialIntervalDays);

            RevisionState newState = RevisionState.builder()
                    .user(user)
                    .problem(problem)
                    .reviewCount(0)
                    .currentIntervalDays(initialIntervalDays)
                    .lastReviewedAt(sessionTime)
                    .nextReviewAt(nextReviewAt)
                    .skipCount(0)
                    .algorithmVersion(ALGORITHM_VERSION)
                    .createdAt(sessionTime)
                    .updatedAt(sessionTime)
                    .build();

            RevisionHistory historyEntry = RevisionHistory.builder()
                    .sourceSessionId(sessionId)
                    .reviewedAt(sessionTime)
                    .outcome(ReviewOutcome.GOOD)
                    .previousIntervalDays(0)
                    .newIntervalDays(initialIntervalDays)
                    .previousConfidence(0.0)
                    .newConfidence(confidence)
                    .previousRetentionStrength(0.0)
                    .newRetentionStrength(retention)
                    .previousNextReviewAt(null)
                    .newNextReviewAt(nextReviewAt)
                    .actualRecallIntervalDays(0.0)
                    .plannedIntervalDays(0.0)
                    .priorityAtSelection(0.0)
                    .algorithmVersion(ALGORITHM_VERSION)
                    .build();

            newState.addHistory(historyEntry);
            RevisionState saved = revisionStateRepository.save(newState);
            return mapToDTO(saved, true);
        }

        // 3. Revisit to an existing SRS problem
        RevisionState state = existingOpt.get();

        // Check if meaningful recall: >= 24h OR due/overdue
        boolean isMeaningful = eligibilityService.isMeaningfulRecall(session, state);
        if (!isMeaningful) {
            // Immediate repeated solves (< 24h and not due) do not advance SRS interval or increment reviewCount
            return mapToDTO(state, true);
        }

        // Meaningful recall processing
        LocalDateTime prevSolveTime = state.getLastReviewedAt() != null ? state.getLastReviewedAt() : sessionTime.minusDays(state.getCurrentIntervalDays());
        LocalDateTime prevNextReviewAt = state.getNextReviewAt();
        int prevIntervalDays = state.getCurrentIntervalDays() != null ? state.getCurrentIntervalDays() : 1;

        double actualRecallIntervalDays = Math.max(0.0, (double) Duration.between(prevSolveTime, sessionTime).toMinutes() / 1440.0);
        double plannedIntervalDays = Math.max(0.0, (double) Duration.between(prevSolveTime, prevNextReviewAt).toMinutes() / 1440.0);

        double retentionRatio = actualRecallIntervalDays / Math.max(plannedIntervalDays, 1.0);
        double retentionFactor = Math.max(0.5, Math.min(2.0, retentionRatio));

        SessionAnalyticsDTO analytics = sessionAnalyticsService.computeAnalytics(session);
        double efficiencyModifier = computeEfficiencyModifier(session);
        double awayReliabilityWeight = computeAwayReliabilityWeight(analytics);

        ReviewOutcome outcome = outcomeClassifier.classifyOutcome(
                session, analytics, actualRecallIntervalDays, retentionRatio, efficiencyModifier, awayReliabilityWeight);

        // Update Retention Strength & Confidence in ConfidenceState
        ConfidenceState confidenceState = confidenceStateRepository.findByUserIdAndProblemId(user.getId(), problem.getId())
                .orElse(null);

        double prevConfidence = confidenceState != null && confidenceState.getCurrentConfidence() != null
                ? confidenceState.getCurrentConfidence()
                : 0.0;
        double prevRetention = confidenceState != null && confidenceState.getRetentionStrength() != null
                ? confidenceState.getRetentionStrength()
                : 0.0;

        // Retention Strength: 50 * retentionFactor * recallQuality
        double recallQuality = outcome.getQualityFactor();
        double retentionEvidence = 50.0 * retentionFactor * recallQuality;
        double newRetentionStrength = roundToTwoDecimals(Math.max(0.0, Math.min(100.0, (0.40 * prevRetention) + (0.60 * retentionEvidence))));

        // Revision Confidence
        int wrongSubmissions = analytics.getWrongSubmissionCount() != null ? analytics.getWrongSubmissionCount() : 0;
        double sessionMastery = Boolean.TRUE.equals(analytics.getSolved())
                ? Math.max(50.0, 100.0 - (wrongSubmissions * 15.0))
                : 20.0;

        double sessionIndependence;
        String assistanceEffect;
        if (Boolean.TRUE.equals(analytics.getSolutionViewed())) {
            sessionIndependence = 15.0;
            assistanceEffect = "SOLUTION_VIEWED";
        } else if (Boolean.TRUE.equals(analytics.getEditorialViewed())) {
            sessionIndependence = 35.0;
            assistanceEffect = "EDITORIAL_VIEWED";
        } else if (Boolean.TRUE.equals(analytics.getHintUsed())) {
            int hintCount = analytics.getHintCount() != null ? analytics.getHintCount() : 0;
            sessionIndependence = hintCount >= 2 ? 60.0 : 70.0;
            assistanceEffect = hintCount >= 2 ? "MULTIPLE_HINTS_USED" : "HINT_USED";
        } else {
            sessionIndependence = 100.0;
            assistanceEffect = "NO_ASSISTANCE";
        }

        double revisionEvidence = (0.40 * sessionMastery) + (0.35 * sessionIndependence) + (0.25 * newRetentionStrength) + efficiencyModifier;
        revisionEvidence = Math.max(0.0, Math.min(100.0, revisionEvidence));

        double alpha = 0.65 * awayReliabilityWeight;
        double newConfidence = roundToTwoDecimals(Math.max(0.0, Math.min(100.0, ((1.0 - alpha) * prevConfidence) + (alpha * revisionEvidence))));

        if (confidenceState != null) {
            confidenceState.setCurrentConfidence(newConfidence);
            confidenceState.setRetentionStrength(newRetentionStrength);
            confidenceState.setLastConfidenceUpdateAt(sessionTime);
            if (Boolean.TRUE.equals(analytics.getSolved())) {
                confidenceState.setLastSuccessfulSolveAt(sessionTime);
            }

            ConfidenceHistory confHistory = ConfidenceHistory.builder()
                    .timestamp(sessionTime)
                    .previousConfidence(prevConfidence)
                    .newConfidence(newConfidence)
                    .masteryContribution(roundToTwoDecimals(sessionMastery))
                    .independenceContribution(roundToTwoDecimals(sessionIndependence))
                    .retentionContribution(roundToTwoDecimals(retentionEvidence))
                    .assistanceEffect("SRS_" + outcome.name() + "_" + assistanceEffect)
                    .awayTimeEffect(roundToTwoDecimals(computeAwayRatio(analytics)))
                    .algorithmVersion(ALGORITHM_VERSION)
                    .build();
            confidenceState.addHistory(confHistory);
            confidenceStateRepository.save(confidenceState);
        }

        // Subsequent Interval Calculation
        double outcomeMultiplier = outcome.getOutcomeMultiplier();
        double schedulingStrength = priorityService.calculateSchedulingStrength(newConfidence, newRetentionStrength);
        double Fs = 0.75 + (0.50 * schedulingStrength);
        double G = actualRecallIntervalDays / Math.max(prevIntervalDays, 1.0);
        double Fg = Math.max(0.75, Math.min(1.50, G));

        int newIntervalDays = (int) Math.max(1, Math.min(180, Math.round(prevIntervalDays * outcomeMultiplier * Fs * Fg)));
        LocalDateTime newNextReviewAt = sessionTime.plusDays(newIntervalDays);

        // Update RevisionState
        state.setCurrentIntervalDays(newIntervalDays);
        state.setLastReviewedAt(sessionTime);
        state.setNextReviewAt(newNextReviewAt);
        state.setReviewCount(state.getReviewCount() != null ? state.getReviewCount() + 1 : 1);
        state.setSkipCount(0); // Reset skip count on review
        state.setAlgorithmVersion(ALGORITHM_VERSION);
        state.setUpdatedAt(sessionTime);

        // Record RevisionHistory
        RevisionHistory historyEntry = RevisionHistory.builder()
                .sourceSessionId(sessionId)
                .reviewedAt(sessionTime)
                .outcome(outcome)
                .previousIntervalDays(prevIntervalDays)
                .newIntervalDays(newIntervalDays)
                .previousConfidence(prevConfidence)
                .newConfidence(newConfidence)
                .previousRetentionStrength(prevRetention)
                .newRetentionStrength(newRetentionStrength)
                .previousNextReviewAt(prevNextReviewAt)
                .newNextReviewAt(newNextReviewAt)
                .actualRecallIntervalDays(roundToTwoDecimals(actualRecallIntervalDays))
                .plannedIntervalDays(roundToTwoDecimals(plannedIntervalDays))
                .priorityAtSelection(0.0)
                .algorithmVersion(ALGORITHM_VERSION)
                .build();

        state.addHistory(historyEntry);
        RevisionState saved = revisionStateRepository.save(state);
        return mapToDTO(saved, true);
    }

    @Transactional(readOnly = true)
    public List<RevisionStateDTO> getAllRevisionsForUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));

        LocalDateTime now = LocalDateTime.now();
        return revisionStateRepository.findByUserIdOrderByNextReviewAtAsc(user.getId()).stream()
                .map(s -> mapToDTO(s, false, now))
                .toList();
    }

    @Transactional(readOnly = true)
    public RevisionStateDTO getRevisionForProblem(Long problemId) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));

        RevisionState state = revisionStateRepository.findWithHistoryByUserIdAndProblemId(user.getId(), problemId)
                .orElseThrow(() -> new ResourceNotFoundException("Revision state not found for problem ID: " + problemId));

        return mapToDTO(state, true, LocalDateTime.now());
    }

    @Transactional
    public ReviewQueueResponseDTO getTodayReviewQueue(ZoneId zoneId) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));

        LocalDateTime now = LocalDateTime.now();
        ZoneId zone = zoneId != null ? zoneId : ZoneId.systemDefault();

        // 1. Fetch all due revisions for this user
        List<RevisionState> dueStates = revisionStateRepository.findDueRevisions(user.getId(), now);

        // 2. Build Queue Items
        List<ReviewQueueItemDTO> queueItems = new ArrayList<>();
        int fairnessRequiredCount = 0;

        for (RevisionState rs : dueStates) {
            ConfidenceState cs = confidenceStateRepository.findByUserIdAndProblemId(user.getId(), rs.getProblem().getId()).orElse(null);
            ReviewQueueItemDTO item = priorityService.buildQueueItem(rs, cs, now);
            queueItems.add(item);
            if (Boolean.TRUE.equals(item.getFairnessRequired())) {
                fairnessRequiredCount++;
            }
        }

        // 3. Rank Queue
        List<ReviewQueueItemDTO> ranked = priorityService.rankQueue(queueItems);

        // 4. Calculate Capacity
        ReviewCapacityDTO capacityDto = capacityService.calculateCapacity(user.getId(), now, zone);
        int capacity = capacityDto.getDailyCapacity();

        // 5. Select items up to capacity
        int totalDue = ranked.size();
        int selectedCount = Math.min(capacity, totalDue);
        List<ReviewQueueItemDTO> selectedQueue = ranked.subList(0, selectedCount);

        // 6. Manage Skip Count for unselected eligible items: skipCount++
        if (totalDue > capacity) {
            for (int i = selectedCount; i < totalDue; i++) {
                ReviewQueueItemDTO skippedItem = ranked.get(i);
                revisionStateRepository.findByUserIdAndProblemId(user.getId(), skippedItem.getProblemId())
                        .ifPresent(s -> {
                            s.setSkipCount((s.getSkipCount() != null ? s.getSkipCount() : 0) + 1);
                            revisionStateRepository.save(s);
                        });
            }
        }

        int backlogCount = Math.max(0, totalDue - capacity);

        return ReviewQueueResponseDTO.builder()
                .queue(selectedQueue)
                .totalDue(totalDue)
                .dailyCapacity(capacity)
                .backlogCount(backlogCount)
                .fairnessRequiredCount(fairnessRequiredCount)
                .capacityDetails(capacityDto)
                .build();
    }

    private double computeEfficiencyModifier(ProblemSession session) {
        long currentActiveTime = (session.getThinkingDuration() != null ? session.getThinkingDuration() : 0L) +
                                (session.getCodingDuration() != null ? session.getCodingDuration() : 0L);

        if (currentActiveTime <= 0 || session.getUser() == null || session.getProblem() == null) {
            return 0.0;
        }

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
            return 5.0;
        } else if (ratio > 1.35) {
            return -5.0;
        }
        return 0.0;
    }

    private double computeAwayRatio(SessionAnalyticsDTO analytics) {
        long thinking = analytics.getThinkingTime() != null ? analytics.getThinkingTime() : 0L;
        long coding = analytics.getCodingTime() != null ? analytics.getCodingTime() : 0L;
        long active = thinking + coding;
        long away = analytics.getTimeAway() != null ? analytics.getTimeAway() : 0L;
        return (active + away > 0) ? ((double) away / (active + away)) : 0.0;
    }

    private double computeAwayReliabilityWeight(SessionAnalyticsDTO analytics) {
        double awayRatio = computeAwayRatio(analytics);
        if (awayRatio <= 0.15) {
            return 1.0;
        } else if (awayRatio <= 0.50) {
            return 1.0 - (0.5 * (awayRatio - 0.15));
        } else {
            return Math.max(0.40, 1.0 - (awayRatio - 0.15));
        }
    }

    private RevisionStateDTO mapToDTO(RevisionState state, boolean includeHistory) {
        return mapToDTO(state, includeHistory, LocalDateTime.now());
    }

    private RevisionStateDTO mapToDTO(RevisionState state, boolean includeHistory, LocalDateTime now) {
        List<RevisionHistoryDTO> historyDTOs = new ArrayList<>();
        if (includeHistory && state.getHistory() != null) {
            for (RevisionHistory h : state.getHistory()) {
                historyDTOs.add(RevisionHistoryDTO.builder()
                        .id(h.getId())
                        .sourceSessionId(h.getSourceSessionId())
                        .reviewedAt(h.getReviewedAt())
                        .outcome(h.getOutcome() != null ? h.getOutcome().name() : null)
                        .previousIntervalDays(h.getPreviousIntervalDays())
                        .newIntervalDays(h.getNewIntervalDays())
                        .previousConfidence(h.getPreviousConfidence())
                        .newConfidence(h.getNewConfidence())
                        .previousRetentionStrength(h.getPreviousRetentionStrength())
                        .newRetentionStrength(h.getNewRetentionStrength())
                        .previousNextReviewAt(h.getPreviousNextReviewAt())
                        .newNextReviewAt(h.getNewNextReviewAt())
                        .actualRecallIntervalDays(h.getActualRecallIntervalDays())
                        .plannedIntervalDays(h.getPlannedIntervalDays())
                        .priorityAtSelection(h.getPriorityAtSelection())
                        .algorithmVersion(h.getAlgorithmVersion())
                        .build());
            }
        }

        Problem p = state.getProblem();
        double overdueDays = priorityService.calculateOverdueDays(state.getNextReviewAt(), now);
        boolean isOverdue = overdueDays > 0.0;

        return RevisionStateDTO.builder()
                .id(state.getId())
                .problemId(p != null ? p.getId() : null)
                .leetcodeId(p != null ? p.getLeetcodeId() : null)
                .problemTitle(p != null ? p.getTitle() : null)
                .difficulty(p != null && p.getDifficulty() != null ? p.getDifficulty().name() : null)
                .reviewCount(state.getReviewCount())
                .currentIntervalDays(state.getCurrentIntervalDays())
                .lastReviewedAt(state.getLastReviewedAt())
                .nextReviewAt(state.getNextReviewAt())
                .skipCount(state.getSkipCount())
                .isOverdue(isOverdue)
                .overdueDays(overdueDays)
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
