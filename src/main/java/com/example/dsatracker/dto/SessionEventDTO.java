package com.example.dsatracker.dto;

import com.example.dsatracker.model.SessionEventType;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionEventDTO {

    @NotNull(message = "Event type is required")
    private SessionEventType type;

    @NotNull(message = "Event timestamp is required")
    private Long timestamp;

    private String result;
    private String submissionId;
    private String hintName;
}
