package com.analytics.model;

import com.analytics.exception.InvalidMarksException;

/**
 * One student's performance in one subject.
 * Total out of 100 = assessment (max 70) + assignment (max 20) + attendance marks (max 10).
 */
public class SubjectRecord {
    public static final double MAX_ASSESSMENT = 70.0;
    public static final double MAX_ASSIGNMENT = 20.0;
    public static final double MAX_ATTENDANCE_MARKS = 10.0;
    public static final double MAX_TOTAL = 100.0;

    private final String subject;
    private double assessmentMarks;
    private double assignmentScore;
    private double attendancePercent;

    public SubjectRecord(String subject, double assessmentMarks,
                         double assignmentScore, double attendancePercent) throws InvalidMarksException {
        this.subject = subject;
        update(assessmentMarks, assignmentScore, attendancePercent);
    }

    /** Validates all three values before changing anything. */
    public void update(double assessmentMarks, double assignmentScore,
                       double attendancePercent) throws InvalidMarksException {
        checkRange("Assessment marks", assessmentMarks, MAX_ASSESSMENT);
        checkRange("Assignment score", assignmentScore, MAX_ASSIGNMENT);
        checkRange("Attendance percentage", attendancePercent, 100.0);
        this.assessmentMarks = assessmentMarks;
        this.assignmentScore = assignmentScore;
        this.attendancePercent = attendancePercent;
    }

    private static void checkRange(String field, double value, double max) throws InvalidMarksException {
        if (Double.isNaN(value) || value < 0 || value > max) {
            throw new InvalidMarksException(field + " must be between 0 and " + (int) max + " (got " + value + ")");
        }
    }

    public String getSubject() {
        return subject;
    }

    public double getAssessmentMarks() {
        return assessmentMarks;
    }

    public double getAssignmentScore() {
        return assignmentScore;
    }

    public double getAttendancePercent() {
        return attendancePercent;
    }

    /** Attendance contributes up to 10 marks (10% of attendance percentage). */
    public double getAttendanceMarks() {
        return attendancePercent / 100.0 * MAX_ATTENDANCE_MARKS;
    }

    public double getTotal() {
        return assessmentMarks + assignmentScore + getAttendanceMarks();
    }
}
