package com.analytics.exception;

/** Thrown when a mark, score or attendance value is outside its allowed range. */
public class InvalidMarksException extends Exception {
    public InvalidMarksException(String message) {
        super(message);
    }
}
