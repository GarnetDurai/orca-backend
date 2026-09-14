package com.example.dsatracker.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExchangeCodeRequestDTO {

    @NotBlank(message = "Authorization code must not be blank")
    private String code;
}
