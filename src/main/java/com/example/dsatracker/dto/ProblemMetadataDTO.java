package com.example.dsatracker.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProblemMetadataDTO {

    @NotNull(message = "LeetCode ID is required")
    @Positive(message = "LeetCode ID must be positive")
    private Integer leetcodeId;

    private String title;
    private String difficulty;
    private String url;
    private String slug;
}
