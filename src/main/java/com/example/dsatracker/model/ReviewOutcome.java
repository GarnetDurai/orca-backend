package com.example.dsatracker.model;

import lombok.Getter;

@Getter
public enum ReviewOutcome {
    AGAIN(0.20, 0.25),
    HARD(0.55, 0.75),
    GOOD(0.80, 1.75),
    EASY(1.00, 2.25);

    private final double qualityFactor;
    private final double outcomeMultiplier;

    ReviewOutcome(double qualityFactor, double outcomeMultiplier) {
        this.qualityFactor = qualityFactor;
        this.outcomeMultiplier = outcomeMultiplier;
    }
}
