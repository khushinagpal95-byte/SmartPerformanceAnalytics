import com.analytics.exception.DuplicateStudentException;
import com.analytics.exception.InvalidMarksException;
import com.analytics.exception.StudentNotFoundException;
import com.analytics.model.Grade;
import com.analytics.model.Student;
import com.analytics.model.SubjectRecord;
import com.analytics.service.AnalyticsService;
import com.analytics.service.RankEntry;
import com.analytics.service.StandardGradingPolicy;
import com.analytics.service.StudentManager;
import com.analytics.service.SubjectStats;
import com.analytics.storage.CsvDataStore;
import com.analytics.util.Validator;

import java.nio.file.Files;
import java.nio.file.Path;

import java.util.ArrayList;
import java.util.List;

/** Plain-Java tests (no framework needed). Run with run_tests.sh. */
public class AnalyticsTests {
    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) throws Exception {
        testSubjectTotal();
        testInvalidMarksRejected();
        testGradeBoundaries();
        testStudentAverage();
        testRankingWithTies();
        testSubjectSummary();
        testDuplicateAndNotFound();
        testValidator();
        testSubjectManagement();
        testRemoveMarksAndRename();
        testAttentionReason();
        testCsvRoundTrip();
        System.out.println("\nPassed: " + passed + "  Failed: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    static void testSubjectTotal() throws Exception {
        SubjectRecord r = new SubjectRecord("Java", 60, 18, 90);
        check("total = 60 + 18 + 9", Math.abs(r.getTotal() - 87.0) < 1e-9);
    }

    static void testInvalidMarksRejected() {
        boolean thrown = false;
        try {
            new SubjectRecord("Java", 75, 10, 90);
        } catch (InvalidMarksException e) {
            thrown = true;
        }
        check("assessment above 70 rejected", thrown);
    }

    static void testGradeBoundaries() {
        check("90 -> O", Grade.fromPercentage(90) == Grade.O);
        check("89.99 -> A+", Grade.fromPercentage(89.99) == Grade.A_PLUS);
        check("40 -> C", Grade.fromPercentage(40) == Grade.C);
        check("39.9 -> F", Grade.fromPercentage(39.9) == Grade.F);
    }

    static void testStudentAverage() throws Exception {
        Student s = new Student("T1", "Test One");
        s.putRecord(new SubjectRecord("A", 60, 20, 100)); // 90
        s.putRecord(new SubjectRecord("B", 40, 10, 50));  // 55
        check("total = 145", Math.abs(s.getTotalMarks() - 145.0) < 1e-9);
        check("average = 72.5", Math.abs(s.getAverage() - 72.5) < 1e-9);
    }

    static void testRankingWithTies() throws Exception {
        Student a = new Student("R1", "Alice");
        Student b = new Student("R2", "Bob");
        Student c = new Student("R3", "Carl");
        a.putRecord(new SubjectRecord("X", 70, 20, 100)); // 100
        b.putRecord(new SubjectRecord("X", 50, 10, 50));  // 65
        c.putRecord(new SubjectRecord("X", 50, 10, 50));  // 65
        List<Student> list = new ArrayList<>(List.of(c, b, a));
        List<RankEntry> ranks = new AnalyticsService(new StandardGradingPolicy()).rankStudents(list);
        check("first is Alice rank 1", ranks.get(0).getStudent() == a && ranks.get(0).getRank() == 1);
        check("Bob and Carl share rank 2", ranks.get(1).getRank() == 2 && ranks.get(2).getRank() == 2);
    }

    static void testSubjectSummary() throws Exception {
        Student a = new Student("S1", "Ann");
        Student b = new Student("S2", "Ben");
        a.putRecord(new SubjectRecord("Math", 60, 20, 100)); // 90
        b.putRecord(new SubjectRecord("Math", 20, 5, 50));   // 30 -> F
        List<Student> list = List.of(a, b);
        List<SubjectStats> st = new AnalyticsService(new StandardGradingPolicy())
                .subjectSummary(list, List.of("Math"));
        check("average 60", Math.abs(st.get(0).getAverage() - 60.0) < 1e-9);
        check("topper is Ann", st.get(0).getTopper().equals("Ann"));
        check("pass percentage 50", Math.abs(st.get(0).getPassPercentage() - 50.0) < 1e-9);
    }

    static void testDuplicateAndNotFound() throws Exception {
        StudentManager m = new StudentManager();
        m.addStudent(new Student("D1", "Dee"));
        boolean dup = false;
        try {
            m.addStudent(new Student("D1", "Dee Again"));
        } catch (DuplicateStudentException e) {
            dup = true;
        }
        boolean nf = false;
        try {
            m.getStudent("NOPE");
        } catch (StudentNotFoundException e) {
            nf = true;
        }
        check("duplicate roll rejected", dup);
        check("unknown roll -> StudentNotFoundException", nf);
    }

    static void testValidator() throws Exception {
        check("valid roll", Validator.isValidRollNo("CU2501"));
        check("invalid roll with space", !Validator.isValidRollNo("CU 25"));
        check("name with comma rejected", !Validator.isValidName("Doe, John"));
        check("subject normalised", Validator.normalizeSubject("data   structures").equals("Data Structures"));
        boolean bad = false;
        try {
            Validator.parseInRange("abc", "Marks", 0, 70);
        } catch (InvalidMarksException e) {
            bad = true;
        }
        check("non-numeric rejected", bad);
    }

    static void testSubjectManagement() throws Exception {
        StudentManager m = new StudentManager();
        m.addStudent(new Student("M1", "Mia"));
        check("new subject added", m.addSubject("Java"));
        check("duplicate subject refused", !m.addSubject("Java"));
        m.enterMarks("M1", "Physics", 50, 10, 80);
        check("entering marks registers the subject", m.getAllSubjects().contains("Physics"));
        check("students counted per subject", m.countStudentsWithSubject("Physics") == 1);
        m.removeSubject("Physics");
        check("removing a subject removes the marks", !m.getStudent("M1").hasSubject("Physics"));
    }

    static void testRemoveMarksAndRename() throws Exception {
        StudentManager m = new StudentManager();
        m.addStudent(new Student("N1", "Nina"));
        m.enterMarks("N1", "Java", 50, 10, 80);
        check("marks removed", m.removeMarks("N1", "Java"));
        check("removing again returns false", !m.removeMarks("N1", "Java"));
        m.renameStudent("N1", "Nina Rao");
        check("student renamed", m.getStudent("n1").getName().equals("Nina Rao"));
    }

    static void testAttentionReason() throws Exception {
        AnalyticsService svc = new AnalyticsService(new StandardGradingPolicy());
        Student good = new Student("G1", "Good");
        good.putRecord(new SubjectRecord("A", 60, 18, 90));
        Student weak = new Student("W1", "Weak");
        weak.putRecord(new SubjectRecord("A", 20, 5, 60));
        check("no reason for a good student", svc.attentionReason(good).isEmpty());
        String reason = svc.attentionReason(weak);
        check("low attendance flagged", reason.contains("Low attendance"));
        check("failed subject flagged", reason.contains("Failing in A"));
        check("pass percentage 50", Math.abs(svc.passPercentage(List.of(good, weak)) - 50.0) < 1e-9);
    }

    static void testCsvRoundTrip() throws Exception {
        Path dir = Files.createTempDirectory("analytics-test");
        StudentManager first = new StudentManager(new CsvDataStore(dir));
        first.addStudent(new Student("C1", "Cara"));
        first.addSubject("Chemistry");
        first.enterMarks("C1", "Java", 55, 15, 85);
        StudentManager second = new StudentManager(new CsvDataStore(dir));
        second.loadFromStore();
        check("student reloaded from CSV", second.size() == 1);
        check("marks reloaded", Math.abs(second.getStudent("C1").getRecord("Java").getTotal() - 78.5) < 1e-9);
        check("subjects reloaded", second.getAllSubjects().equals(List.of("Chemistry", "Java")));
    }

    static void check(String name, boolean ok) {
        if (ok) {
            passed++;
            System.out.println("  PASS  " + name);
        } else {
            failed++;
            System.out.println("  FAIL  " + name);
        }
    }
}
