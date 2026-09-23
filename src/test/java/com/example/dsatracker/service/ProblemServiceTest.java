package com.example.dsatracker.service;

import com.example.dsatracker.dto.ProblemMetadataDTO;
import com.example.dsatracker.dto.ProblemRequestDTO;
import com.example.dsatracker.dto.ProblemResponseDTO;
import com.example.dsatracker.exception.DuplicateResourceException;
import com.example.dsatracker.exception.ResourceNotFoundException;
import com.example.dsatracker.model.Difficulty;
import com.example.dsatracker.model.Problem;
import com.example.dsatracker.model.Tag;
import com.example.dsatracker.repository.ProblemRepository;
import com.example.dsatracker.repository.TagRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProblemServiceTest {

    @Mock
    private ProblemRepository problemRepository;

    @Mock
    private TagRepository tagRepository;

    @InjectMocks
    private ProblemService problemService;

    private Problem testProblem;

    @BeforeEach
    void setUp() {
        testProblem = Problem.builder()
                .id(1L)
                .leetcodeId(1)
                .title("Two Sum")
                .difficulty(Difficulty.EASY)
                .url("https://leetcode.com/problems/two-sum/")
                .build();
    }

    @Test
    @DisplayName("resolveOrCreateProblem: resolves existing problem by problemId")
    void testResolveByProblemId_Success() {
        when(problemRepository.findById(1L)).thenReturn(Optional.of(testProblem));

        Problem resolved = problemService.resolveOrCreateProblem(1L, null);

        assertNotNull(resolved);
        assertEquals(1L, resolved.getId());
        assertEquals("Two Sum", resolved.getTitle());
        verify(problemRepository).findById(1L);
        verify(problemRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("resolveOrCreateProblem: throws ResourceNotFoundException if problemId not found")
    void testResolveByProblemId_NotFound() {
        when(problemRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () ->
                problemService.resolveOrCreateProblem(999L, null));
    }

    @Test
    @DisplayName("resolveOrCreateProblem: resolves existing problem by leetcodeId without saving")
    void testResolveByLeetcodeId_Existing() {
        ProblemMetadataDTO meta = ProblemMetadataDTO.builder()
                .leetcodeId(1)
                .title("Two Sum")
                .difficulty("EASY")
                .url("https://leetcode.com/problems/two-sum/")
                .build();

        when(problemRepository.findByLeetcodeId(1)).thenReturn(Optional.of(testProblem));

        Problem resolved = problemService.resolveOrCreateProblem(null, meta);

        assertNotNull(resolved);
        assertEquals(1L, resolved.getId());
        assertEquals("Two Sum", resolved.getTitle());
        verify(problemRepository).findByLeetcodeId(1);
        verify(problemRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("resolveOrCreateProblem: auto-creates, saves and flushes new problem if not found")
    void testResolveByLeetcodeId_AutoCreatesNewProblem() {
        ProblemMetadataDTO meta = ProblemMetadataDTO.builder()
                .leetcodeId(787)
                .title("Cheapest Flights Within K Stops")
                .difficulty("MEDIUM")
                .url("https://leetcode.com/problems/cheapest-flights-within-k-stops/")
                .build();

        when(problemRepository.findByLeetcodeId(787)).thenReturn(Optional.empty());
        when(problemRepository.saveAndFlush(any(Problem.class))).thenAnswer(invocation -> {
            Problem p = invocation.getArgument(0);
            p.setId(7L);
            return p;
        });

        Problem resolved = problemService.resolveOrCreateProblem(null, meta);

        assertNotNull(resolved);
        assertEquals(7L, resolved.getId());
        assertEquals(787, resolved.getLeetcodeId());
        assertEquals("Cheapest Flights Within K Stops", resolved.getTitle());
        assertEquals(Difficulty.MEDIUM, resolved.getDifficulty());

        ArgumentCaptor<Problem> captor = ArgumentCaptor.forClass(Problem.class);
        verify(problemRepository).saveAndFlush(captor.capture());
        Problem captured = captor.getValue();
        assertEquals(787, captured.getLeetcodeId());
        assertEquals("Cheapest Flights Within K Stops", captured.getTitle());
    }

    @Test
    @DisplayName("resolveOrCreateProblem: handles invalid difficulty with fallback to MEDIUM")
    void testResolveByLeetcodeId_InvalidDifficultyFallback() {
        ProblemMetadataDTO meta = ProblemMetadataDTO.builder()
                .leetcodeId(999)
                .difficulty("VERY_HARD")
                .build();

        when(problemRepository.findByLeetcodeId(999)).thenReturn(Optional.empty());
        when(problemRepository.saveAndFlush(any(Problem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Problem resolved = problemService.resolveOrCreateProblem(null, meta);

        assertEquals(Difficulty.MEDIUM, resolved.getDifficulty());
        assertEquals("LeetCode Problem #999", resolved.getTitle());
        assertEquals("https://leetcode.com/problems/999", resolved.getUrl());
    }

    @Test
    @DisplayName("resolveOrCreateProblem: handles concurrent creation conflict gracefully")
    void testResolveByLeetcodeId_ConcurrentConflictFallback() {
        ProblemMetadataDTO meta = ProblemMetadataDTO.builder()
                .leetcodeId(787)
                .title("Cheapest Flights Within K Stops")
                .difficulty("MEDIUM")
                .build();

        Problem existingFromConcurrentInsert = Problem.builder()
                .id(7L)
                .leetcodeId(787)
                .title("Cheapest Flights Within K Stops")
                .difficulty(Difficulty.MEDIUM)
                .build();

        // First lookup misses, save throws DataIntegrityViolationException, second lookup finds it
        when(problemRepository.findByLeetcodeId(787))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(existingFromConcurrentInsert));
        when(problemRepository.saveAndFlush(any(Problem.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));

        Problem resolved = problemService.resolveOrCreateProblem(null, meta);

        assertNotNull(resolved);
        assertEquals(7L, resolved.getId());
        assertEquals(787, resolved.getLeetcodeId());
    }

    @Test
    @DisplayName("resolveOrCreateProblem: throws ResourceNotFoundException if no identifier provided")
    void testResolve_NoIdentifierProvided() {
        assertThrows(ResourceNotFoundException.class, () ->
                problemService.resolveOrCreateProblem(null, null));

        ProblemMetadataDTO emptyMeta = ProblemMetadataDTO.builder().build();
        assertThrows(ResourceNotFoundException.class, () ->
                problemService.resolveOrCreateProblem(null, emptyMeta));
    }

    @Test
    @DisplayName("getProblemByLeetcodeId: returns response DTO for existing problem")
    void testGetProblemByLeetcodeId_Success() {
        when(problemRepository.findByLeetcodeId(1)).thenReturn(Optional.of(testProblem));

        ProblemResponseDTO response = problemService.getProblemByLeetcodeId(1);

        assertNotNull(response);
        assertEquals(1, response.getLeetcodeId());
        assertEquals("Two Sum", response.getTitle());
    }

    @Test
    @DisplayName("createProblem: successfully creates problem with tags")
    void testCreateProblem_Success() {
        Tag tag = Tag.builder().id(5L).name("Array").build();
        ProblemRequestDTO request = new ProblemRequestDTO(
                100,
                "Same Tree",
                Difficulty.EASY,
                "https://leetcode.com/problems/same-tree/",
                Set.of(5L)
        );

        when(problemRepository.existsByLeetcodeId(100)).thenReturn(false);
        when(tagRepository.findById(5L)).thenReturn(Optional.of(tag));
        when(problemRepository.save(any(Problem.class))).thenAnswer(invocation -> {
            Problem p = invocation.getArgument(0);
            p.setId(50L);
            return p;
        });

        ProblemResponseDTO response = problemService.createProblem(request);

        assertNotNull(response);
        assertEquals(50L, response.getId());
        assertEquals(100, response.getLeetcodeId());
    }

    @Test
    @DisplayName("createProblem: throws DuplicateResourceException if problem already exists")
    void testCreateProblem_Duplicate() {
        ProblemRequestDTO request = new ProblemRequestDTO(
                1,
                "Two Sum",
                Difficulty.EASY,
                "https://leetcode.com/problems/two-sum/",
                Set.of()
        );

        when(problemRepository.existsByLeetcodeId(1)).thenReturn(true);

        assertThrows(DuplicateResourceException.class, () -> problemService.createProblem(request));
    }
}
