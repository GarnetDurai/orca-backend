package com.example.dsatracker.service;

import com.example.dsatracker.model.ProblemSession;
import com.example.dsatracker.model.RevisionState;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
public class RevisionEligibilityService {

    /**
     * A problem is eligible to enter SRS for the first time ONLY if the session was successfully solved
     * and no RevisionState already exists for (user, problem).
     */
    public boolean isEligibleForInitialSRS(ProblemSession session, boolean stateExists) {
        if (stateExists) {
            return false;
        }
        return Boolean.TRUE.equals(session.getSolved());
    }

    /**
     * A revisit to an existing SRS problem is a meaningful recall event when:
     * - at least 24 hours (1440 minutes) have elapsed since the previous review/solve
     * OR
     * - the problem is already due or overdue (now >= nextReviewAt).
     */
    public boolean isMeaningfulRecall(ProblemSession session, RevisionState state) {
        if (state == null) {
            return false;
        }

        LocalDateTime now = session.getSessionStartedAt() != null
                ? session.getSessionStartedAt()
                : LocalDateTime.now();

        // If the problem is due or overdue, it is always a meaningful recall
        if (state.getNextReviewAt() != null && !now.isBefore(state.getNextReviewAt())) {
            return true;
        }

        // If at least 24 hours have elapsed since the last review
        if (state.getLastReviewedAt() != null) {
            long minutes = Duration.between(state.getLastReviewedAt(), now).toMinutes();
            return minutes >= 1440;
        }

        return true;
    }
}
