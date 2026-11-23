package com.analytics.storage;

import com.analytics.exception.DataStorageException;
import com.analytics.model.Student;

import java.util.Collection;
import java.util.List;

/**
 * Persistence interface. CsvDataStore saves to files, JdbcDataStore saves to MySQL.
 * The rest of the program only talks to this interface, so the storage can be swapped.
 */
public interface DataStore {
    /** Saves the complete current data (all students with their marks, and all subjects). */
    void save(Collection<Student> students, Collection<String> subjects) throws DataStorageException;

    List<Student> loadStudents() throws DataStorageException;

    List<String> loadSubjects() throws DataStorageException;

    /** Short text shown to the user, e.g. "MySQL database" or "CSV files". */
    String getDescription();
}
