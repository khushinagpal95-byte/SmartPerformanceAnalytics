package com.analytics.exception;

/** Wraps low-level I/O problems so the UI layer deals with one exception type. */
public class DataStorageException extends Exception {
    public DataStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
