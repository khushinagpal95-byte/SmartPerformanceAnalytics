package com.analytics.ui;

import com.analytics.exception.DataStorageException;
import com.analytics.exception.DuplicateStudentException;
import com.analytics.exception.InvalidMarksException;
import com.analytics.exception.StudentNotFoundException;
import com.analytics.model.Student;
import com.analytics.service.AnalyticsService;
import com.analytics.service.StudentManager;
import com.analytics.util.ReportExporter;
import com.analytics.util.SampleData;
import com.analytics.util.Validator;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * Console user interface (start the program with the argument "console").
 * Saving is done automatically by StudentManager, so this class only handles input and output.
 */
public class ConsoleMenu {
    private final Scanner in = new Scanner(System.in);
    private final StudentManager manager;
    private final AnalyticsService analytics;
    private final ReportExporter exporter;

    public ConsoleMenu(StudentManager manager, AnalyticsService analytics, ReportExporter exporter) {
        this.manager = manager;
        this.analytics = analytics;
        this.exporter = exporter;
    }

    public void run() {
        System.out.println("==========================================");
        System.out.println("   SMART PERFORMANCE ANALYTICS TOOL");
        System.out.println("==========================================");
        if (!manager.isEmpty()) {
            System.out.println("Loaded " + manager.size() + " student(s) from saved data.");
        }

        boolean running = true;
        while (running) {
            printMenu();
            String choice = prompt("Choose an option");
            try {
                switch (choice) {
                    case "1": addStudent(); break;
                    case "2": enterMarks(); break;
                    case "3": viewStudentReport(); break;
                    case "4": showRanking(); break;
                    case "5": showSubjectSummary(); break;
                    case "6": showGradeDistribution(); break;
                    case "7": exportReports(); break;
                    case "8": removeStudent(); break;
                    case "9": loadSample(); break;
                    case "10": addSubject(); break;
                    case "0": running = false; break;
                    default: System.out.println("Invalid option. Please enter a number from the menu.");
                }
            } catch (StudentNotFoundException | DuplicateStudentException | InvalidMarksException e) {
                System.out.println("  ! " + e.getMessage());
            } catch (IOException e) {
                System.out.println("  ! File error: " + e.getMessage());
            } catch (DataStorageException e) {
                System.out.println("  ! Storage error: " + e.getMessage());
            } finally {
                System.out.println();
            }
        }
        System.out.println("Goodbye! (all changes are already saved)");
    }

    private void printMenu() {
        System.out.println("1. Add student");
        System.out.println("2. Enter / update subject marks");
        System.out.println("3. View student report");
        System.out.println("4. Class ranking");
        System.out.println("5. Subject-wise summary");
        System.out.println("6. Grade distribution");
        System.out.println("7. Export reports to files");
        System.out.println("8. Remove student");
        System.out.println("9. Load sample data");
        System.out.println("10. Add subject");
        System.out.println("0. Exit");
    }

    private void addStudent() throws DuplicateStudentException, DataStorageException {
        String roll = prompt("Roll number (3-15 letters/digits)").toUpperCase();
        if (!Validator.isValidRollNo(roll)) {
            System.out.println("  ! Invalid roll number.");
            return;
        }
        String name = prompt("Student name");
        if (!Validator.isValidName(name)) {
            System.out.println("  ! Invalid name (letters, spaces, . ' - only).");
            return;
        }
        manager.addStudent(new Student(roll, name.trim()));
        System.out.println("  Student added.");
    }

    private void addSubject() throws DataStorageException {
        String subject = prompt("Subject name");
        if (!Validator.isValidSubject(subject)) {
            System.out.println("  ! Invalid subject name.");
            return;
        }
        subject = Validator.normalizeSubject(subject);
        System.out.println(manager.addSubject(subject) ? "  Subject added." : "  Subject already exists.");
    }

    private void enterMarks() throws StudentNotFoundException, InvalidMarksException, DataStorageException {
        Student s = manager.getStudent(prompt("Roll number"));
        System.out.println("  Student: " + s.getName());
        String subject = prompt("Subject name");
        if (!Validator.isValidSubject(subject)) {
            System.out.println("  ! Invalid subject name.");
            return;
        }
        subject = Validator.normalizeSubject(subject);
        double assessment = Validator.parseInRange(prompt("Assessment marks (0-70)"), "Assessment marks", 0, 70);
        double assignment = Validator.parseInRange(prompt("Assignment score (0-20)"), "Assignment score", 0, 20);
        double attendance = Validator.parseInRange(prompt("Attendance % (0-100)"), "Attendance", 0, 100);

        manager.enterMarks(s.getRollNo(), subject, assessment, assignment, attendance);
        System.out.println("  Saved " + subject + " record for " + s.getName() + ".");
    }

    private void viewStudentReport() throws StudentNotFoundException {
        Student s = manager.getStudent(prompt("Roll number"));
        System.out.println();
        System.out.print(exporter.buildStudentReport(s));
    }

    private void showRanking() {
        if (requireData()) {
            System.out.println();
            System.out.print(exporter.buildRankList(analytics.rankStudents(withMarks())));
        }
    }

    private void showSubjectSummary() {
        if (requireData()) {
            System.out.println();
            System.out.print(exporter.buildSubjectSummary(
                    analytics.subjectSummary(withMarks(), manager.getAllSubjects())));
        }
    }

    private void showGradeDistribution() {
        if (requireData()) {
            System.out.println();
            System.out.print(exporter.buildGradeDistribution(analytics.gradeDistribution(withMarks())));
        }
    }

    private void exportReports() throws IOException {
        if (!requireData()) {
            return;
        }
        Path dir = Paths.get("reports");
        List<Student> students = withMarks();
        exporter.writeToFile(exporter.buildClassReport(students, manager.getAllSubjects()),
                dir.resolve("class_report.txt"));
        for (Student s : students) {
            exporter.writeToFile(exporter.buildStudentReport(s), dir.resolve(s.getRollNo() + "_report.txt"));
        }
        System.out.println("  Exported class report and " + students.size()
                + " student report(s) to the 'reports' folder.");
    }

    private void removeStudent() throws StudentNotFoundException, DataStorageException {
        String roll = prompt("Roll number to remove");
        String confirm = prompt("Type YES to confirm removal of " + roll.toUpperCase());
        if (confirm.equals("YES")) {
            manager.removeStudent(roll);
            System.out.println("  Student removed.");
        } else {
            System.out.println("  Cancelled.");
        }
    }

    private void loadSample() throws InvalidMarksException, DataStorageException {
        if (!manager.isEmpty()) {
            String c = prompt("This replaces current data. Type YES to continue");
            if (!c.equals("YES")) {
                System.out.println("  Cancelled.");
                return;
            }
        }
        manager.replaceAllData(SampleData.createStudents(), SampleData.getSubjects());
        System.out.println("  Loaded " + manager.size() + " sample students.");
    }

    /** Only students that have at least one subject record take part in analytics. */
    private List<Student> withMarks() {
        List<Student> list = new ArrayList<>();
        for (Student s : manager.getAllStudents()) {
            if (s.hasRecords()) {
                list.add(s);
            }
        }
        return list;
    }

    private boolean requireData() {
        if (withMarks().isEmpty()) {
            System.out.println("  No marks entered yet. Add students and marks first (or load sample data).");
            return false;
        }
        return true;
    }

    private String prompt(String label) {
        System.out.print(label + ": ");
        return in.hasNextLine() ? in.nextLine().trim() : "0";
    }
}
