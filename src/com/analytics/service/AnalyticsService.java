package com.analytics.service;

import com.analytics.model.Grade;
import com.analytics.model.Student;
import com.analytics.model.SubjectRecord;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Calculations module: grades, ranking, subject-wise summaries and grade distribution. */
public class AnalyticsService {
    /** Students below this average attendance are flagged for the teacher. */
    public static final double ATTENDANCE_LIMIT = 75.0;

    private final GradingPolicy policy;

    public AnalyticsService(GradingPolicy policy) {
        this.policy = policy;
    }

    public GradingPolicy getPolicy() {
        return policy;
    }

    public Grade gradeOf(Student student) {
        return policy.assignGrade(student.getPercentage());
    }

    public Grade gradeOf(SubjectRecord record) {
        return policy.assignGrade(record.getTotal());
    }

    /**
     * Ranks students by percentage (highest first). Students with equal percentage share a rank
     * and the next rank is skipped (1, 2, 2, 4). Ties are listed alphabetically.
     */
    public List<RankEntry> rankStudents(List<Student> students) {
        List<Student> sorted = new ArrayList<>(students);
        sorted.sort(Comparator.comparingDouble(Student::getPercentage).reversed()
                .thenComparing(Student::getName, String.CASE_INSENSITIVE_ORDER));

        List<RankEntry> result = new ArrayList<>();
        for (int i = 0; i < sorted.size(); i++) {
            Student s = sorted.get(i);
            int rank = i + 1;
            if (i > 0 && Math.abs(s.getPercentage() - sorted.get(i - 1).getPercentage()) < 1e-9) {
                rank = result.get(i - 1).getRank();
            }
            result.add(new RankEntry(rank, s, gradeOf(s)));
        }
        return result;
    }

    /**
     * Builds a 2D marks matrix [student][subject]; Double.NaN marks a subject the student has no record for.
     */
    public double[][] buildMarksMatrix(List<Student> students, List<String> subjects) {
        double[][] matrix = new double[students.size()][subjects.size()];
        for (int i = 0; i < students.size(); i++) {
            for (int j = 0; j < subjects.size(); j++) {
                SubjectRecord r = students.get(i).getRecord(subjects.get(j));
                matrix[i][j] = (r == null) ? Double.NaN : r.getTotal();
            }
        }
        return matrix;
    }

    /** Column-wise statistics over the marks matrix, one SubjectStats per subject. */
    public List<SubjectStats> subjectSummary(List<Student> students, List<String> subjects) {
        double[][] matrix = buildMarksMatrix(students, subjects);
        List<SubjectStats> stats = new ArrayList<>();

        for (int j = 0; j < subjects.size(); j++) {
            double sum = 0;
            double max = Double.NEGATIVE_INFINITY;
            double min = Double.POSITIVE_INFINITY;
            int count = 0;
            int pass = 0;
            String topper = "-";

            for (int i = 0; i < students.size(); i++) {
                double m = matrix[i][j];
                if (Double.isNaN(m)) {
                    continue;
                }
                count++;
                sum += m;
                if (m > max) {
                    max = m;
                    topper = students.get(i).getName();
                }
                min = Math.min(min, m);
                if (policy.assignGrade(m).isPass()) {
                    pass++;
                }
            }
            if (count == 0) {
                stats.add(new SubjectStats(subjects.get(j), 0, 0, 0, 0, "-", 0));
            } else {
                stats.add(new SubjectStats(subjects.get(j), count, sum / count, max, min, topper, pass));
            }
        }
        return stats;
    }

    /** How many students got each overall grade. */
    public Map<Grade, Integer> gradeDistribution(List<Student> students) {
        Map<Grade, Integer> dist = new EnumMap<>(Grade.class);
        for (Grade g : Grade.values()) {
            dist.put(g, 0);
        }
        for (Student s : students) {
            if (s.hasRecords()) {
                dist.merge(gradeOf(s), 1, Integer::sum);
            }
        }
        return dist;
    }

    public double classAverage(List<Student> students) {
        double sum = 0;
        int n = 0;
        for (Student s : students) {
            if (s.hasRecords()) {
                sum += s.getPercentage();
                n++;
            }
        }
        return n == 0 ? 0.0 : sum / n;
    }

    /** Percentage of students (with marks) whose overall grade is a pass. */
    public double passPercentage(List<Student> students) {
        int total = 0;
        int passed = 0;
        for (Student s : students) {
            if (s.hasRecords()) {
                total++;
                if (gradeOf(s).isPass()) {
                    passed++;
                }
            }
        }
        return total == 0 ? 0.0 : passed * 100.0 / total;
    }

    /**
     * Returns why a student needs the teacher's attention (low attendance, failed subjects),
     * or an empty string when everything is fine.
     */
    public String attentionReason(Student student) {
        if (!student.hasRecords()) {
            return "";
        }
        StringBuilder reason = new StringBuilder();
        if (student.getAverageAttendance() < ATTENDANCE_LIMIT) {
            reason.append(String.format(java.util.Locale.US, "Low attendance (%.1f%%)", student.getAverageAttendance()));
        }
        List<String> failed = new ArrayList<>();
        for (SubjectRecord r : student.getRecords()) {
            if (!gradeOf(r).isPass()) {
                failed.add(r.getSubject());
            }
        }
        if (!failed.isEmpty()) {
            if (reason.length() > 0) {
                reason.append("; ");
            }
            reason.append("Failing in ").append(String.join(", ", failed));
        }
        return reason.toString();
    }
}
