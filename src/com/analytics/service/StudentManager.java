package com.analytics.service;

import com.analytics.exception.DataStorageException;
import com.analytics.exception.DuplicateStudentException;
import com.analytics.exception.InvalidMarksException;
import com.analytics.exception.StudentNotFoundException;
import com.analytics.model.Student;
import com.analytics.model.SubjectRecord;
import com.analytics.storage.DataStore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Keeps all students and subjects in memory and saves them through the DataStore
 * after every change. This is the "data entry" module used by both the console and the web dashboard.
 */
public class StudentManager {
    // TreeMap / TreeSet keep everything sorted (by roll number / by subject name)
    private final Map<String, Student> students = new TreeMap<>();
    private final Set<String> subjects = new TreeSet<>();
    private final DataStore store; // null means "do not save" (used in unit tests)

    public StudentManager() {
        this(null);
    }

    public StudentManager(DataStore store) {
        this.store = store;
    }

    /** Replaces the in-memory data with whatever is saved in the store. */
    public void loadFromStore() throws DataStorageException {
        if (store == null) {
            return;
        }
        students.clear();
        subjects.clear();
        for (Student s : store.loadStudents()) {
            students.put(s.getRollNo(), s);
            subjects.addAll(s.getRecordMap().keySet()); // a subject used in marks always exists
        }
        subjects.addAll(store.loadSubjects());
    }

    private void save() throws DataStorageException {
        if (store != null) {
            store.save(students.values(), subjects);
        }
    }

    // ---------- students ----------

    public void addStudent(Student student) throws DuplicateStudentException, DataStorageException {
        if (students.containsKey(student.getRollNo())) {
            throw new DuplicateStudentException(student.getRollNo());
        }
        students.put(student.getRollNo(), student);
        save();
    }

    public Student getStudent(String rollNo) throws StudentNotFoundException {
        Student s = students.get(rollNo == null ? "" : rollNo.trim().toUpperCase());
        if (s == null) {
            throw new StudentNotFoundException(rollNo);
        }
        return s;
    }

    public void renameStudent(String rollNo, String newName)
            throws StudentNotFoundException, DataStorageException {
        getStudent(rollNo).setName(newName);
        save();
    }

    public void removeStudent(String rollNo) throws StudentNotFoundException, DataStorageException {
        Student s = getStudent(rollNo);
        students.remove(s.getRollNo());
        save();
    }

    public Collection<Student> getAllStudents() {
        return new ArrayList<>(students.values());
    }

    // ---------- subjects ----------

    /** Returns false if the subject already exists. */
    public boolean addSubject(String subject) throws DataStorageException {
        if (!subjects.add(subject)) {
            return false;
        }
        save();
        return true;
    }

    /** Removes the subject and every student's marks in it. Returns false if it did not exist. */
    public boolean removeSubject(String subject) throws DataStorageException {
        if (!subjects.remove(subject)) {
            return false;
        }
        for (Student s : students.values()) {
            s.removeRecord(subject);
        }
        save();
        return true;
    }

    public List<String> getAllSubjects() {
        return new ArrayList<>(subjects);
    }

    /** How many students have marks in this subject. */
    public int countStudentsWithSubject(String subject) {
        int count = 0;
        for (Student s : students.values()) {
            if (s.hasSubject(subject)) {
                count++;
            }
        }
        return count;
    }

    // ---------- marks ----------

    /**
     * Adds or updates a subject record. If the subject is new it is added to the subject list too.
     * The marks are validated first, so nothing changes when they are invalid.
     */
    public SubjectRecord enterMarks(String rollNo, String subject, double assessment,
                                    double assignment, double attendance)
            throws StudentNotFoundException, InvalidMarksException, DataStorageException {
        Student s = getStudent(rollNo);
        SubjectRecord record = s.getRecord(subject);
        if (record != null) {
            record.update(assessment, assignment, attendance);
        } else {
            record = new SubjectRecord(subject, assessment, assignment, attendance);
            s.putRecord(record);
        }
        subjects.add(subject);
        save();
        return record;
    }

    /** Returns false if the student had no marks in that subject. */
    public boolean removeMarks(String rollNo, String subject)
            throws StudentNotFoundException, DataStorageException {
        boolean removed = getStudent(rollNo).removeRecord(subject);
        if (removed) {
            save();
        }
        return removed;
    }

    // ---------- bulk ----------

    /** Replaces everything (used for loading the sample data) and saves only once. */
    public void replaceAllData(List<Student> newStudents, Collection<String> newSubjects)
            throws DataStorageException {
        students.clear();
        subjects.clear();
        for (Student s : newStudents) {
            students.put(s.getRollNo(), s);
        }
        subjects.addAll(newSubjects);
        save();
    }

    public int size() {
        return students.size();
    }

    public boolean isEmpty() {
        return students.isEmpty();
    }
}
