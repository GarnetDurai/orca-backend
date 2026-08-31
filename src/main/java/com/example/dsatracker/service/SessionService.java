package com.example.dsatracker.service;

import com.example.dsatracker.dto.ProblemMetadataDTO;
import com.example.dsatracker.dto.ProblemSessionDetailsDTO;
import com.example.dsatracker.dto.ProblemSessionRequestDTO;
import com.example.dsatracker.dto.ProblemSessionResponseDTO;
import com.example.dsatracker.exception.DuplicateResourceException;
import com.example.dsatracker.exception.ResourceNotFoundException;
import com.example.dsatracker.mapper.SessionMapper;
import com.example.dsatracker.model.Difficulty;
import com.example.dsatracker.model.Problem;
import com.example.dsatracker.model.ProblemSession;
import com.example.dsatracker.model.User;
import com.example.dsatracker.repository.ProblemRepository;
import com.example.dsatracker.repository.ProblemSessionRepository;
import com.example.dsatracker.repository.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SessionService {

    private final ProblemSessionRepository sessionRepository;
    private final ProblemRepository problemRepository;
    private final UserRepository userRepository;

    public SessionService(
            ProblemSessionRepository sessionRepository,
            ProblemRepository problemRepository,
            UserRepository userRepository) {
        this.sessionRepository = sessionRepository;
        this.problemRepository = problemRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public ProblemSessionResponseDTO ingestSession(ProblemSessionRequestDTO request) {
        // 1. Duplicate protection on sessionId
        if (sessionRepository.existsBySessionId(request.getSessionId())) {
            throw new DuplicateResourceException(
                    "Session with ID '" + request.getSessionId() + "' already exists.");
        }

        // 2. Resolve authenticated user from SecurityContext
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));

        // 3. Resolve problem
        Problem problem = resolveProblem(request);

        // 4. Map request to entities (attaching all SessionEvent records)
        ProblemSession session = SessionMapper.toEntity(request, user, problem);

        // 5. Transactional save (cascades to all SessionEvent records)
        ProblemSession savedSession = sessionRepository.save(session);

        return SessionMapper.toResponse(savedSession);
    }

    @Transactional(readOnly = true)
    public List<ProblemSessionDetailsDTO> getUserSessions() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));

        List<ProblemSession> sessions = sessionRepository.findByUserIdOrderBySessionStartedAtDesc(user.getId());
        return sessions.stream()
                .map(SessionMapper::toDetails)
                .toList();
    }

    @Transactional(readOnly = true)
    public ProblemSessionDetailsDTO getSessionById(String sessionId) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Authenticated user not found."));

        ProblemSession session = sessionRepository.findBySessionIdAndUserId(sessionId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Session not found with ID: " + sessionId));

        return SessionMapper.toDetails(session);
    }

    private Problem resolveProblem(ProblemSessionRequestDTO request) {
        if (request.getProblemId() != null) {
            return problemRepository.findById(request.getProblemId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Problem not found with ID: " + request.getProblemId()));
        }

        if (request.getProblem() != null && request.getProblem().getLeetcodeId() != null) {
            ProblemMetadataDTO meta = request.getProblem();
            return problemRepository.findByLeetcodeId(meta.getLeetcodeId())
                    .orElseGet(() -> {
                        Difficulty difficulty = Difficulty.MEDIUM;
                        if (meta.getDifficulty() != null) {
                            try {
                                difficulty = Difficulty.valueOf(meta.getDifficulty().toUpperCase());
                            } catch (IllegalArgumentException ignored) {
                                difficulty = Difficulty.MEDIUM;
                            }
                        }

                        String title = (meta.getTitle() != null && !meta.getTitle().isBlank())
                                ? meta.getTitle()
                                : "LeetCode Problem #" + meta.getLeetcodeId();

                        String url = (meta.getUrl() != null && !meta.getUrl().isBlank())
                                ? meta.getUrl()
                                : "https://leetcode.com/problems/" + (meta.getSlug() != null ? meta.getSlug() : meta.getLeetcodeId());

                        Problem newProblem = Problem.builder()
                                .leetcodeId(meta.getLeetcodeId())
                                .title(title)
                                .difficulty(difficulty)
                                .url(url)
                                .build();

                        return problemRepository.save(newProblem);
                    });
        }

        throw new ResourceNotFoundException("Problem identifier is required (either problemId or problem.leetcodeId).");
    }
}
