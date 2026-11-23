package com.analytics.service;

import com.analytics.model.Grade;

/** Strategy interface: different institutions can plug in different grading rules. */
public interface GradingPolicy {
    Grade assignGrade(double percentage);

    String getName();
}
