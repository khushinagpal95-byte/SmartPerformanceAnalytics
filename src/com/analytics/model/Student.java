package com.analytics.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A student and all of his/her subject records. */
public class Student extends Person implements Reportable {
    private static int studentCount = 0; // static: how many Student objects were created

    private final Map<String, SubjectRecord> records = new LinkedHashMap<>();

    public Student(String rollNo, String name) {
        super(rollNo, name);
        studentCount++;
    }

    public static int getStudentCount() {
        return studentCount;
    }

    public String getRollNo() {
        return getId();
    }

    @Override
    public String getRole() {
        return "Student";
    }

    public void putRecord(SubjectRecord record) {
        records.put(record.getSubject(), record);
    }

    /** Returns true if a record for this subject existed and was removed. */
    public boolean removeRecord(String subject) {
        return records.remove(subject) != null;
    }

    public SubjectRecord getRecord(String subject) {
        return records.get(subject);
    }

    public boolean hasSubject(String subject) {
        return records.containsKey(subject);
    }

    public List<SubjectRecord> getRecords() {
        return Collections.unmodifiableList(new ArrayList<>(records.values()));
    }

    public Map<String, SubjectRecord> getRecordMap() {
        return Collections.unmodifiableMap(records);
    }

    public boolean hasRecords() {
        return !records.isEmpty();
    }

    /** Sum of the subject totals. */
    public double getTotalMarks() {
        double sum = 0;
        for (SubjectRecord r : records.values()) {
            sum += r.getTotal();
        }
        return sum;
    }

    /** Average of the subject totals (each out of 100), which is also the overall percentage. */
    public double getAverage() {
        return records.isEmpty() ? 0.0 : getTotalMarks() / records.size();
    }

    public double getPercentage() {
        return getAverage();
    }

    public double getAverageAttendance() {
        if (records.isEmpty()) {
            return 0.0;
        }
        double sum = 0;
        for (SubjectRecord r : records.values()) {
            sum += r.getAttendancePercent();
        }
        return sum / records.size();
    }

    @Override
    public String toReportLine() {
        return String.format("%-10s %-22s %8.2f %8.2f%%", getRollNo(), getName(), getTotalMarks(), getPercentage());
    }
}
