package com.example.dsatracker.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProblemSessionResponseDTO {
    private String sessionId;
    private String status;
    private Long problemId;
    private Integer leetcodeId;
    private Boolean solved;
    private Integer attempts;
    private Integer eventCount;
    private LocalDateTime createdAt;
}
