package com.analytics.util;

import com.analytics.exception.InvalidMarksException;
import com.analytics.model.Student;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Demo data so the tool can be shown without typing everything in. */
public final class SampleData {
    private SampleData() {
    }

    private static final String[] SUBJECTS = {"Java", "Mathematics", "Physics", "Data Structures"};

    // roll number, name, then {assessment, assignment, attendance} for each subject above
    private static final Object[][] DATA = {
        {"S101", "Aarav Sharma",  new double[][]{{62, 18, 92}, {58, 17, 88}, {55, 15, 85}, {64, 19, 95}}},
        {"S102", "Diya Verma",    new double[][]{{68, 20, 98}, {66, 19, 96}, {63, 18, 94}, {67, 20, 99}}},
        {"S103", "Kabir Singh",   new double[][]{{45, 14, 76}, {40, 12, 72}, {38, 13, 70}, {47, 15, 80}}},
        {"S104", "Meera Nair",    new double[][]{{55, 16, 84}, {60, 17, 90}, {52, 15, 82}, {58, 16, 86}}},
        {"S105", "Rohan Gupta",   new double[][]{{30, 10, 60}, {28, 9, 58}, {25, 8, 55}, {33, 11, 62}}},
        {"S106", "Sneha Kapoor",  new double[][]{{62, 18, 92}, {58, 17, 88}, {55, 15, 85}, {64, 19, 95}}},
    };

    public static List<String> getSubjects() {
        return new ArrayList<>(Arrays.asList(SUBJECTS));
    }

    /** Builds the sample students (nothing is saved here). */
    public static List<Student> createStudents() throws InvalidMarksException {
        List<Student> list = new ArrayList<>();
        for (Object[] row : DATA) {
            Student s = new Student((String) row[0], (String) row[1]);
            double[][] marks = (double[][]) row[2];
            for (int i = 0; i < SUBJECTS.length; i++) {
                s.putRecord(new com.analytics.model.SubjectRecord(
                        SUBJECTS[i], marks[i][0], marks[i][1], marks[i][2]));
            }
            list.add(s);
        }
        return list;
    }
}
