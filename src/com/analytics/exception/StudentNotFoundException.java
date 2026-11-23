package com.analytics.exception;

/** Thrown when a roll number does not match any stored student. */
public class StudentNotFoundException extends Exception {
    private final String rollNo;

    public StudentNotFoundException(String rollNo) {
        super("No student found with roll number: " + rollNo);
        this.rollNo = rollNo;
    }

    public String getRollNo() {
        return rollNo;
    }
}
