package com.analytics.exception;

/** Thrown when adding a student whose roll number already exists. */
public class DuplicateStudentException extends Exception {
    public DuplicateStudentException(String rollNo) {
        super("A student with roll number " + rollNo + " already exists.");
    }
}
