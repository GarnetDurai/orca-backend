package com.example.dsatracker.service;

import com.example.dsatracker.dto.ProblemMetadataDTO;
import com.example.dsatracker.dto.ProblemRequestDTO;
import com.example.dsatracker.dto.ProblemResponseDTO;
import com.example.dsatracker.exception.DuplicateResourceException;
import com.example.dsatracker.exception.ResourceNotFoundException;
import com.example.dsatracker.mapper.ProblemMapper;
import com.example.dsatracker.model.Difficulty;
import com.example.dsatracker.model.Problem;
import com.example.dsatracker.model.Tag;
import com.example.dsatracker.repository.ProblemRepository;
import com.example.dsatracker.repository.TagRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

@Service
public class ProblemService {

    private static final Logger log = LoggerFactory.getLogger(ProblemService.class);

    private final ProblemRepository problemRepository;
    private final TagRepository tagRepository;

    public ProblemService(
            ProblemRepository problemRepository,
            TagRepository tagRepository) {
        this.problemRepository = problemRepository;
        this.tagRepository = tagRepository;
    }

    public ProblemResponseDTO getProblemByLeetcodeId(Integer leetcodeId) {
        Problem problem = problemRepository
                .findByLeetcodeId(leetcodeId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Problem not found"));
        return ProblemMapper.toResponse(problem);
    }

    public ProblemResponseDTO createProblem(ProblemRequestDTO request) {
        if (problemRepository.existsByLeetcodeId(request.getLeetcodeId())) {
            throw new DuplicateResourceException("Problem already exists.");
        }

        Set<Tag> tags = new HashSet<>();
        for (Long tagId : request.getTagIds()) {
            Tag tag = tagRepository.findById(tagId)
                    .orElseThrow(() ->
                            new ResourceNotFoundException("Tag with ID " + tagId + " not found."));
            tags.add(tag);
        }

        Problem problem = ProblemMapper.toEntity(request, tags);
        Problem savedProblem = problemRepository.save(problem);
        return ProblemMapper.toResponse(savedProblem);
    }

    /**
     * Resolves an existing problem or creates, persists, and immediately commits a new Problem
     * in its own isolated transaction (REQUIRES_NEW).
     *
     * Committing the Problem row immediately guarantees that downstream transactions
     * (such as ConfidenceService and RevisionScheduler running in REQUIRES_NEW) can
     * satisfy foreign-key constraints in PostgreSQL without waiting for the outer session
     * ingestion transaction to commit.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Problem resolveOrCreateProblem(Long problemId, ProblemMetadataDTO meta) {
        if (problemId != null) {
            return problemRepository.findById(problemId)
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Problem not found with ID: " + problemId));
        }

        if (meta != null && meta.getLeetcodeId() != null) {
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

                        try {
                            Problem saved = problemRepository.saveAndFlush(newProblem);
                            log.info("Auto-created and committed new Problem [id={}, leetcodeId={}, title='{}'] in isolated transaction",
                                    saved.getId(), saved.getLeetcodeId(), saved.getTitle());
                            return saved;
                        } catch (DataIntegrityViolationException e) {
                            log.warn("Concurrent creation conflict for leetcodeId {}. Falling back to lookup.", meta.getLeetcodeId());
                            return problemRepository.findByLeetcodeId(meta.getLeetcodeId())
                                    .orElseThrow(() -> e);
                        }
                    });
        }

        throw new ResourceNotFoundException("Problem identifier is required (either problemId or problem.leetcodeId).");
    }
}