package com.example.dsatracker.mapper;

import com.example.dsatracker.dto.*;
import com.example.dsatracker.model.Problem;
import com.example.dsatracker.model.ProblemSession;
import com.example.dsatracker.model.SessionEvent;
import com.example.dsatracker.model.User;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public class SessionMapper {

    private SessionMapper() {
    }

    public static LocalDateTime toLocalDateTime(Long epochMs) {
        if (epochMs == null) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.systemDefault());
    }

    public static Long toEpochMilli(LocalDateTime ldt) {
        if (ldt == null) {
            return null;
        }
        return ldt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    public static ProblemSession toEntity(
            ProblemSessionRequestDTO request,
            User user,
            Problem problem) {

        ProblemSession session = ProblemSession.builder()
                .sessionId(request.getSessionId())
                .user(user)
                .problem(problem)
                .sessionStartedAt(toLocalDateTime(request.getSessionStartedAt()))
                .firstCodingAt(toLocalDateTime(request.getFirstCodingAt()))
                .solvedAt(toLocalDateTime(request.getSolvedAt()))
                .thinkingDuration(request.getThinkingDuration())
                .codingDuration(request.getCodingDuration())
                .totalTimeAway(request.getTotalTimeAway() != null ? request.getTotalTimeAway() : 0L)
                .tabSwitchCount(request.getTabSwitchCount() != null ? request.getTabSwitchCount() : 0)
                .language(request.getLanguage())
                .hintOpened(Boolean.TRUE.equals(request.getHintOpened()))
                .hintOpenCount(request.getHintOpenCount() != null ? request.getHintOpenCount() : 0)
                .hintOpenedAt(toLocalDateTime(request.getHintOpenedAt()))
                .solutionViewed(Boolean.TRUE.equals(request.getSolutionViewed()))
                .solutionViewedAt(toLocalDateTime(request.getSolutionViewedAt()))
                .editorialViewed(Boolean.TRUE.equals(request.getEditorialViewed()))
                .editorialViewedAt(toLocalDateTime(request.getEditorialViewedAt()))
                .attempts(request.getAttempts() != null ? request.getAttempts() : 0)
                .solved(Boolean.TRUE.equals(request.getSolved()))
                .build();

        if (request.getEvents() != null) {
            for (SessionEventDTO eventDto : request.getEvents()) {
                SessionEvent event = SessionEvent.builder()
                        .eventType(eventDto.getType())
                        .timestamp(toLocalDateTime(eventDto.getTimestamp()))
                        .result(eventDto.getResult())
                        .submissionId(eventDto.getSubmissionId())
                        .hintName(eventDto.getHintName())
                        .build();
                session.addEvent(event);
            }
        }

        return session;
    }

    public static ProblemSessionResponseDTO toResponse(ProblemSession session) {
        return ProblemSessionResponseDTO.builder()
                .sessionId(session.getSessionId())
                .status("SAVED")
                .problemId(session.getProblem().getId())
                .leetcodeId(session.getProblem().getLeetcodeId())
                .solved(session.getSolved())
                .attempts(session.getAttempts())
                .eventCount(session.getEvents() != null ? session.getEvents().size() : 0)
                .createdAt(session.getCreatedAt())
                .build();
    }

    public static ProblemSessionDetailsDTO toDetails(ProblemSession session) {
        ProblemMetadataDTO problemMeta = null;
        if (session.getProblem() != null) {
            problemMeta = ProblemMetadataDTO.builder()
                    .leetcodeId(session.getProblem().getLeetcodeId())
                    .title(session.getProblem().getTitle())
                    .difficulty(session.getProblem().getDifficulty() != null ? session.getProblem().getDifficulty().name() : null)
                    .url(session.getProblem().getUrl())
                    .build();
        }

        List<SessionEventDTO> eventDtos = new ArrayList<>();
        if (session.getEvents() != null) {
            for (SessionEvent event : session.getEvents()) {
                eventDtos.add(SessionEventDTO.builder()
                        .type(event.getEventType())
                        .timestamp(toEpochMilli(event.getTimestamp()))
                        .result(event.getResult())
                        .submissionId(event.getSubmissionId())
                        .hintName(event.getHintName())
                        .build());
            }
        }

        return ProblemSessionDetailsDTO.builder()
                .sessionId(session.getSessionId())
                .problem(problemMeta)
                .sessionStartedAt(toEpochMilli(session.getSessionStartedAt()))
                .firstCodingAt(toEpochMilli(session.getFirstCodingAt()))
                .solvedAt(toEpochMilli(session.getSolvedAt()))
                .thinkingDuration(session.getThinkingDuration())
                .codingDuration(session.getCodingDuration())
                .totalTimeAway(session.getTotalTimeAway())
                .tabSwitchCount(session.getTabSwitchCount())
                .language(session.getLanguage())
                .hintOpened(session.getHintOpened())
                .hintOpenCount(session.getHintOpenCount())
                .hintOpenedAt(toEpochMilli(session.getHintOpenedAt()))
                .solutionViewed(session.getSolutionViewed())
                .solutionViewedAt(toEpochMilli(session.getSolutionViewedAt()))
                .editorialViewed(session.getEditorialViewed())
                .editorialViewedAt(toEpochMilli(session.getEditorialViewedAt()))
                .attempts(session.getAttempts())
                .solved(session.getSolved())
                .createdAt(session.getCreatedAt())
                .events(eventDtos)
                .build();
    }
}
