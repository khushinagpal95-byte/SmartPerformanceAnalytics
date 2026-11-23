package com.analytics.storage;

import com.analytics.exception.DataStorageException;
import com.analytics.exception.InvalidMarksException;
import com.analytics.model.Student;
import com.analytics.model.SubjectRecord;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * File persistence (used when MySQL is not available). Two files inside the data folder:
 *   students.csv : rollNo,name,subject,assessment,assignment,attendance   (one line per student-subject)
 *   subjects.csv : one subject name per line
 */
public class CsvDataStore implements DataStore {
    private static final String STUDENT_HEADER = "rollNo,name,subject,assessment,assignment,attendance";
    private static final String SUBJECT_HEADER = "subject";

    private final Path studentFile;
    private final Path subjectFile;

    public CsvDataStore(Path folder) {
        this.studentFile = folder.resolve("students.csv");
        this.subjectFile = folder.resolve("subjects.csv");
    }

    @Override
    public String getDescription() {
        return "CSV files (" + studentFile.getParent() + ")";
    }

    @Override
    public void save(Collection<Student> students, Collection<String> subjects) throws DataStorageException {
        try {
            Files.createDirectories(studentFile.getParent());
            writeStudents(students);
            writeSubjects(subjects);
        } catch (IOException e) {
            throw new DataStorageException("Could not save data to " + studentFile.getParent(), e);
        }
    }

    private void writeStudents(Collection<Student> students) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(studentFile, StandardCharsets.UTF_8)) {
            w.write(STUDENT_HEADER);
            w.newLine();
            for (Student s : students) {
                if (!s.hasRecords()) {
                    w.write(s.getRollNo() + "," + s.getName() + ",,,,");
                    w.newLine();
                    continue;
                }
                for (SubjectRecord r : s.getRecords()) {
                    w.write(String.join(",",
                            s.getRollNo(), s.getName(), r.getSubject(),
                            String.valueOf(r.getAssessmentMarks()),
                            String.valueOf(r.getAssignmentScore()),
                            String.valueOf(r.getAttendancePercent())));
                    w.newLine();
                }
            }
        }
    }

    private void writeSubjects(Collection<String> subjects) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(subjectFile, StandardCharsets.UTF_8)) {
            w.write(SUBJECT_HEADER);
            w.newLine();
            for (String subject : subjects) {
                w.write(subject);
                w.newLine();
            }
        }
    }

    @Override
    public List<Student> loadStudents() throws DataStorageException {
        if (!Files.exists(studentFile)) {
            return new ArrayList<>(); // first run: nothing saved yet
        }
        Map<String, Student> loaded = new LinkedHashMap<>();
        try (BufferedReader r = Files.newBufferedReader(studentFile, StandardCharsets.UTF_8)) {
            String line = r.readLine(); // skip header
            int lineNo = 1;
            while ((line = r.readLine()) != null) {
                lineNo++;
                if (line.trim().isEmpty()) {
                    continue;
                }
                String[] p = line.split(",", -1);
                if (p.length < 6) {
                    System.out.println("  [warning] skipped malformed line " + lineNo);
                    continue;
                }
                try {
                    Student s = loaded.computeIfAbsent(p[0], k -> new Student(p[0], p[1]));
                    if (!p[2].isEmpty()) {
                        s.putRecord(new SubjectRecord(p[2],
                                Double.parseDouble(p[3]),
                                Double.parseDouble(p[4]),
                                Double.parseDouble(p[5])));
                    }
                } catch (NumberFormatException | InvalidMarksException e) {
                    System.out.println("  [warning] skipped line " + lineNo + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            throw new DataStorageException("Could not read data from " + studentFile, e);
        }
        return new ArrayList<>(loaded.values());
    }

    @Override
    public List<String> loadSubjects() throws DataStorageException {
        List<String> subjects = new ArrayList<>();
        if (!Files.exists(subjectFile)) {
            return subjects;
        }
        try (BufferedReader r = Files.newBufferedReader(subjectFile, StandardCharsets.UTF_8)) {
            String line = r.readLine(); // skip header
            while ((line = r.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    subjects.add(line.trim());
                }
            }
        } catch (IOException e) {
            throw new DataStorageException("Could not read data from " + subjectFile, e);
        }
        return subjects;
    }
}
