package com.analytics.service;

import com.analytics.model.Grade;

/** Default 10-point scale defined in the Grade enum (O, A+, A, B+, B, C, F). */
public class StandardGradingPolicy implements GradingPolicy {
    @Override
    public Grade assignGrade(double percentage) {
        return Grade.fromPercentage(percentage);
    }

    @Override
    public String getName() {
        return "Standard 10-point scale";
    }
}
