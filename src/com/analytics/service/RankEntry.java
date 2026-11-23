package com.analytics.service;

import com.analytics.model.Grade;
import com.analytics.model.Student;

/** One row of the rank list. */
public class RankEntry {
    private final int rank;
    private final Student student;
    private final Grade grade;

    public RankEntry(int rank, Student student, Grade grade) {
        this.rank = rank;
        this.student = student;
        this.grade = grade;
    }

    public int getRank() {
        return rank;
    }

    public Student getStudent() {
        return student;
    }

    public Grade getGrade() {
        return grade;
    }
}
