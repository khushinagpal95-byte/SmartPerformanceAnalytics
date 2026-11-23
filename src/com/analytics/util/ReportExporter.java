package com.analytics.util;

import com.analytics.model.Grade;
import com.analytics.model.Student;
import com.analytics.model.SubjectRecord;
import com.analytics.service.AnalyticsService;
import com.analytics.service.RankEntry;
import com.analytics.service.SubjectStats;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Report module: builds text reports with StringBuilder and exports them to files. */
public class ReportExporter {
    private static final String LINE = "-".repeat(72);
    private final AnalyticsService analytics;

    public ReportExporter(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    public String buildStudentReport(Student s) {
        StringBuilder sb = new StringBuilder();
        sb.append("STUDENT PERFORMANCE REPORT\n").append(LINE).append('\n');
        sb.append(String.format("Roll No : %s%nName    : %s%n%n", s.getRollNo(), s.getName()));
        sb.append(String.format("%-20s %10s %10s %10s %8s %6s%n",
                "Subject", "Assess/70", "Assign/20", "Attend %", "Total", "Grade"));
        sb.append(LINE).append('\n');
        for (SubjectRecord r : s.getRecords()) {
            sb.append(String.format("%-20s %10.1f %10.1f %10.1f %8.2f %6s%n",
                    r.getSubject(), r.getAssessmentMarks(), r.getAssignmentScore(),
                    r.getAttendancePercent(), r.getTotal(), analytics.gradeOf(r)));
        }
        sb.append(LINE).append('\n');
        if (s.hasRecords()) {
            sb.append(String.format("Total marks : %.2f%n", s.getTotalMarks()));
            sb.append(String.format("Average     : %.2f%n", s.getAverage()));
            sb.append(String.format("Percentage  : %.2f%%%n", s.getPercentage()));
            sb.append(String.format("Attendance  : %.1f%% (average)%n", s.getAverageAttendance()));
            sb.append(String.format("Overall grade: %s%n", analytics.gradeOf(s)));
        } else {
            sb.append("No subject records entered yet.\n");
        }
        return sb.toString();
    }

    public String buildRankList(List<RankEntry> ranks) {
        StringBuilder sb = new StringBuilder();
        sb.append("CLASS RANK LIST\n").append(LINE).append('\n');
        sb.append(String.format("%-5s %-10s %-22s %8s %9s %6s%n",
                "Rank", "Roll No", "Name", "Total", "Percent", "Grade"));
        sb.append(LINE).append('\n');
        for (RankEntry e : ranks) {
            Student s = e.getStudent();
            sb.append(String.format("%-5d %-10s %-22s %8.2f %8.2f%% %6s%n",
                    e.getRank(), s.getRollNo(), s.getName(),
                    s.getTotalMarks(), s.getPercentage(), e.getGrade()));
        }
        return sb.toString();
    }

    /** Rank list as CSV text (opens in Excel). Names cannot contain commas, so no quoting is needed. */
    public String buildRankCsv(List<RankEntry> ranks) {
        StringBuilder sb = new StringBuilder("Rank,RollNo,Name,Total,Percentage,Grade\n");
        for (RankEntry e : ranks) {
            Student s = e.getStudent();
            sb.append(String.format(java.util.Locale.US, "%d,%s,%s,%.2f,%.2f,%s\n",
                    e.getRank(), s.getRollNo(), s.getName(), s.getTotalMarks(), s.getPercentage(), e.getGrade()));
        }
        return sb.toString();
    }

    public String buildSubjectSummary(List<SubjectStats> stats) {
        StringBuilder sb = new StringBuilder();
        sb.append("SUBJECT-WISE SUMMARY\n").append(LINE).append('\n');
        sb.append(String.format("%-20s %5s %8s %8s %8s %8s  %s%n",
                "Subject", "N", "Average", "Highest", "Lowest", "Pass %", "Topper"));
        sb.append(LINE).append('\n');
        for (SubjectStats st : stats) {
            sb.append(String.format("%-20s %5d %8.2f %8.2f %8.2f %7.1f%%  %s%n",
                    st.getSubject(), st.getStudentCount(), st.getAverage(), st.getHighest(),
                    st.getLowest(), st.getPassPercentage(), st.getTopper()));
        }
        return sb.toString();
    }

    public String buildGradeDistribution(Map<Grade, Integer> dist) {
        StringBuilder sb = new StringBuilder();
        sb.append("GRADE DISTRIBUTION\n").append(LINE).append('\n');
        for (Map.Entry<Grade, Integer> e : dist.entrySet()) {
            sb.append(String.format("%-3s | %s %d%n", e.getKey(), "#".repeat(e.getValue()), e.getValue()));
        }
        return sb.toString();
    }

    public String buildClassReport(List<Student> students, List<String> subjects) {
        StringBuilder sb = new StringBuilder();
        sb.append("SMART PERFORMANCE ANALYTICS - CLASS REPORT\n");
        sb.append("Grading policy: ").append(analytics.getPolicy().getName()).append("\n\n");
        sb.append(buildRankList(analytics.rankStudents(students))).append('\n');
        sb.append(buildSubjectSummary(analytics.subjectSummary(students, subjects))).append('\n');
        sb.append(buildGradeDistribution(analytics.gradeDistribution(students))).append('\n');
        sb.append(String.format("Class average: %.2f%%%n", analytics.classAverage(students)));
        return sb.toString();
    }

    public void writeToFile(String content, Path path) throws IOException {
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        try (BufferedWriter w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            w.write(content);
        }
    }
}
