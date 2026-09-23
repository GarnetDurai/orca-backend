package com.example.dsatracker.service;

import com.example.dsatracker.dto.ReviewCapacityDTO;
import com.example.dsatracker.model.ReviewOutcome;
import com.example.dsatracker.model.RevisionHistory;
import com.example.dsatracker.repository.ProblemSessionRepository;
import com.example.dsatracker.repository.RevisionHistoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewCapacityServiceTest {

    @Mock
    private ProblemSessionRepository sessionRepository;

    @Mock
    private RevisionHistoryRepository revisionHistoryRepository;

    private ReviewCapacityService capacityService;

    private final Long userId = 1L;

    @BeforeEach
    void setUp() {
        capacityService = new ReviewCapacityService(sessionRepository, revisionHistoryRepository);
    }

    @Test
    @DisplayName("Initial SRS enrollment (previousIntervalDays == 0) does not count as completed review")
    void testInitialSrsEnrollmentDoesNotCountAsCompletedReview() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 23, 10, 0);
        ZoneId zone = ZoneId.systemDefault();

        // 1 initial enrollment today (previousIntervalDays = 0)
        List<RevisionHistory> historyList = List.of(
                RevisionHistory.builder()
                        .reviewedAt(now)
                        .previousIntervalDays(0)
                        .newIntervalDays(6)
                        .outcome(ReviewOutcome.GOOD)
                        .build()
        );

        when(revisionHistoryRepository.findByUserIdAndReviewedAtAfter(eq(userId), any()))
                .thenReturn(historyList);
        when(sessionRepository.findByUserId(userId)).thenReturn(Collections.emptyList());

        ReviewCapacityDTO capacity = capacityService.calculateCapacity(userId, now, zone);
        assertEquals(0, capacity.getReviewsCompletedToday(), "Initial SRS enrollment baseline must NOT be counted as a completed review");
    }

    @Test
    @DisplayName("Null previousIntervalDays does not count as completed review (strict null semantics)")
    void testNullPreviousIntervalDoesNotCountAsCompletedReview() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 23, 10, 0);
        ZoneId zone = ZoneId.systemDefault();

        List<RevisionHistory> historyList = List.of(
                RevisionHistory.builder()
                        .reviewedAt(now)
                        .previousIntervalDays(null)
                        .newIntervalDays(6)
                        .outcome(ReviewOutcome.GOOD)
                        .build()
        );

        when(revisionHistoryRepository.findByUserIdAndReviewedAtAfter(eq(userId), any()))
                .thenReturn(historyList);
        when(sessionRepository.findByUserId(userId)).thenReturn(Collections.emptyList());

        ReviewCapacityDTO capacity = capacityService.calculateCapacity(userId, now, zone);
        assertEquals(0, capacity.getReviewsCompletedToday(), "Null previousIntervalDays must NOT be counted as a completed review");
    }

    @Test
    @DisplayName("Negative previousIntervalDays does not count as completed review")
    void testNegativePreviousIntervalDoesNotCountAsCompletedReview() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 23, 10, 0);
        ZoneId zone = ZoneId.systemDefault();

        List<RevisionHistory> historyList = List.of(
                RevisionHistory.builder()
                        .reviewedAt(now)
                        .previousIntervalDays(-1)
                        .newIntervalDays(6)
                        .outcome(ReviewOutcome.GOOD)
                        .build()
        );

        when(revisionHistoryRepository.findByUserIdAndReviewedAtAfter(eq(userId), any()))
                .thenReturn(historyList);
        when(sessionRepository.findByUserId(userId)).thenReturn(Collections.emptyList());

        ReviewCapacityDTO capacity = capacityService.calculateCapacity(userId, now, zone);
        assertEquals(0, capacity.getReviewsCompletedToday(), "Negative previousIntervalDays must NOT be counted as a completed review");
    }

    @Test
    @DisplayName("Genuine review (previousIntervalDays > 0) counts as completed review")
    void testGenuineReviewCountsAsCompletedReview() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 23, 10, 0);
        ZoneId zone = ZoneId.systemDefault();

        // 1 initial enrollment today + 1 genuine review today
        List<RevisionHistory> historyList = List.of(
                RevisionHistory.builder()
                        .reviewedAt(now.minusHours(2))
                        .previousIntervalDays(0)
                        .newIntervalDays(6)
                        .outcome(ReviewOutcome.GOOD)
                        .build(),
                RevisionHistory.builder()
                        .reviewedAt(now)
                        .previousIntervalDays(2)
                        .newIntervalDays(4)
                        .outcome(ReviewOutcome.GOOD)
                        .build()
        );

        when(revisionHistoryRepository.findByUserIdAndReviewedAtAfter(eq(userId), any()))
                .thenReturn(historyList);
        when(sessionRepository.findByUserId(userId)).thenReturn(Collections.emptyList());

        ReviewCapacityDTO capacity = capacityService.calculateCapacity(userId, now, zone);
        assertEquals(1, capacity.getReviewsCompletedToday(), "Only genuine review must be counted in completedReviewsToday");
    }

    @Test
    @DisplayName("Capacity derived from median of recent active review days with genuine reviews")
    void testCapacityDerivedFromMedian() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 13, 10, 0);
        ZoneId zone = ZoneId.systemDefault();

        // 3 active days with 1, 2, and 10 reviews
        List<RevisionHistory> historyList = List.of(
                RevisionHistory.builder().reviewedAt(now.minusDays(1)).previousIntervalDays(1).build(),
                RevisionHistory.builder().reviewedAt(now.minusDays(2)).previousIntervalDays(1).build(),
                RevisionHistory.builder().reviewedAt(now.minusDays(2).plusHours(1)).previousIntervalDays(1).build(),
                RevisionHistory.builder().reviewedAt(now.minusDays(3)).previousIntervalDays(1).build(),
                RevisionHistory.builder().reviewedAt(now.minusDays(3).plusHours(1)).previousIntervalDays(1).build(),
                RevisionHistory.builder().reviewedAt(now.minusDays(3).plusHours(2)).previousIntervalDays(1).build(),
                RevisionHistory.builder().reviewedAt(now.minusDays(3).plusHours(3)).previousIntervalDays(1).build(),
                RevisionHistory.builder().reviewedAt(now.minusDays(3).plusHours(4)).previousIntervalDays(1).build(),
                RevisionHistory.builder().reviewedAt(now.minusDays(3).plusHours(5)).previousIntervalDays(1).build(),
                RevisionHistory.builder().reviewedAt(now.minusDays(3).plusHours(6)).previousIntervalDays(1).build(),
                RevisionHistory.builder().reviewedAt(now.minusDays(3).plusHours(7)).previousIntervalDays(1).build(),
                RevisionHistory.builder().reviewedAt(now.minusDays(3).plusHours(8)).previousIntervalDays(1).build(),
                RevisionHistory.builder().reviewedAt(now.minusDays(3).plusHours(9)).previousIntervalDays(1).build()
        );

        when(revisionHistoryRepository.findByUserIdAndReviewedAtAfter(eq(userId), any()))
                .thenReturn(historyList);
        when(sessionRepository.findByUserId(userId)).thenReturn(Collections.emptyList());

        ReviewCapacityDTO capacity = capacityService.calculateCapacity(userId, now, zone);
        assertEquals(2, capacity.getDailyCapacity(), "Median of [1, 2, 10] must be 2");
    }

    @Test
    @DisplayName("New user conservative default of 1 review/day")
    void testNewUserConservativeDefault() {
        LocalDateTime now = LocalDateTime.of(2026, 9, 13, 10, 0);
        when(sessionRepository.findByUserId(userId)).thenReturn(Collections.emptyList());
        when(revisionHistoryRepository.findByUserIdAndReviewedAtAfter(eq(userId), any())).thenReturn(Collections.emptyList());

        ReviewCapacityDTO capacity = capacityService.calculateCapacity(userId, now, ZoneId.systemDefault());
        assertEquals(1, capacity.getDailyCapacity());
        assertEquals(0, capacity.getActiveDaysLast30Days());
    }

    @Test
    @DisplayName("Null userId returns default capacity")
    void testNullUserIdReturnsDefault() {
        ReviewCapacityDTO capacity = capacityService.calculateCapacity(null, LocalDateTime.now(), ZoneId.systemDefault());
        assertEquals(1, capacity.getDailyCapacity());
        assertEquals(0, capacity.getReviewsCompletedToday());
    }
}
