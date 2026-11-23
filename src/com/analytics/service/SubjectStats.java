package com.analytics.service;

/** Class-level statistics for one subject. */
public class SubjectStats {
    private final String subject;
    private final int studentCount;
    private final double average;
    private final double highest;
    private final double lowest;
    private final String topper;
    private final int passCount;

    public SubjectStats(String subject, int studentCount, double average,
                        double highest, double lowest, String topper, int passCount) {
        this.subject = subject;
        this.studentCount = studentCount;
        this.average = average;
        this.highest = highest;
        this.lowest = lowest;
        this.topper = topper;
        this.passCount = passCount;
    }

    public String getSubject() {
        return subject;
    }

    public int getStudentCount() {
        return studentCount;
    }

    public double getAverage() {
        return average;
    }

    public double getHighest() {
        return highest;
    }

    public double getLowest() {
        return lowest;
    }

    public String getTopper() {
        return topper;
    }

    public int getPassCount() {
        return passCount;
    }

    public double getPassPercentage() {
        return studentCount == 0 ? 0.0 : passCount * 100.0 / studentCount;
    }
}
