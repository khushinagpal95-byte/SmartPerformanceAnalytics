package com.analytics.web;

import com.analytics.exception.DataStorageException;
import com.analytics.exception.DuplicateStudentException;
import com.analytics.exception.InvalidMarksException;
import com.analytics.exception.StudentNotFoundException;
import com.analytics.model.Grade;
import com.analytics.model.Student;
import com.analytics.model.SubjectRecord;
import com.analytics.service.AnalyticsService;
import com.analytics.service.RankEntry;
import com.analytics.service.StudentManager;
import com.analytics.service.SubjectStats;
import com.analytics.storage.DataStore;
import com.analytics.util.ReportExporter;
import com.analytics.util.SampleData;
import com.analytics.util.Validator;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

import static com.analytics.web.JsonUtil.num;
import static com.analytics.web.JsonUtil.quote;

/**
 * The bridge between the HTML dashboard and the Java code.
 * The browser calls URLs like /api/students; each URL is handled by one method below,
 * which uses the same StudentManager / AnalyticsService as the console version.
 * All answers are JSON (except the report downloads).
 */
public class ApiHandler implements HttpHandler {
    private final StudentManager manager;
    private final AnalyticsService analytics;
    private final ReportExporter exporter;
    private final DataStore store;
    private final int port;

    public ApiHandler(StudentManager manager, AnalyticsService analytics,
                      ReportExporter exporter, DataStore store, int port) {
        this.manager = manager;
        this.analytics = analytics;
        this.exporter = exporter;
        this.store = store;
        this.port = port;
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        try {
            String method = ex.getRequestMethod();
            if (method.equals("POST") && !isAllowedOrigin(ex)) {
                sendError(ex, 403, "Request blocked: it did not come from the dashboard page.");
                return;
            }
            Map<String, String> params = readParams(ex);
            route(ex, method + " " + ex.getRequestURI().getPath(), params);

        } catch (StudentNotFoundException e) {
            sendError(ex, 404, e.getMessage());
        } catch (DuplicateStudentException e) {
            sendError(ex, 409, e.getMessage());
        } catch (InvalidMarksException | IllegalArgumentException e) {
            sendError(ex, 400, e.getMessage());
        } catch (DataStorageException e) {
            sendError(ex, 500, e.getMessage());
        } catch (RuntimeException e) {
            e.printStackTrace();
            sendError(ex, 500, "Unexpected server error: " + e);
        }
    }

    /** Chooses the method that handles this request ("GET /api/students", "POST /api/marks/save", ...). */
    private void route(HttpExchange ex, String route, Map<String, String> p) throws IOException,
            StudentNotFoundException, DuplicateStudentException, InvalidMarksException, DataStorageException {
        switch (route) {
            case "GET /api/dashboard":        sendJson(ex, 200, dashboard()); break;
            case "GET /api/students":         sendJson(ex, 200, studentList()); break;
            case "GET /api/student":          sendJson(ex, 200, studentDetails(p)); break;
            case "POST /api/student/add":     sendJson(ex, 200, addStudent(p)); break;
            case "POST /api/student/rename":  sendJson(ex, 200, renameStudent(p)); break;
            case "POST /api/student/delete":  sendJson(ex, 200, deleteStudent(p)); break;
            case "GET /api/subjects":         sendJson(ex, 200, subjectList()); break;
            case "POST /api/subject/add":     sendJson(ex, 200, addSubject(p)); break;
            case "POST /api/subject/delete":  sendJson(ex, 200, deleteSubject(p)); break;
            case "POST /api/marks/save":      sendJson(ex, 200, saveMarks(p)); break;
            case "POST /api/marks/delete":    sendJson(ex, 200, deleteMarks(p)); break;
            case "GET /api/ranking":          sendJson(ex, 200, ranking()); break;
            case "GET /api/summary":          sendJson(ex, 200, subjectSummary()); break;
            case "POST /api/sample/load":     sendJson(ex, 200, loadSample()); break;
            case "GET /api/report/class":
                sendText(ex, exporter.buildClassReport(studentsWithMarks(), manager.getAllSubjects()),
                        "text/plain", "class_report.txt", p);
                break;
            case "GET /api/report/student":
                Student s = manager.getStudent(p.get("roll"));
                sendText(ex, exporter.buildStudentReport(s), "text/plain", s.getRollNo() + "_report.txt", p);
                break;
            case "GET /api/export/ranking.csv":
                sendText(ex, exporter.buildRankCsv(analytics.rankStudents(studentsWithMarks())),
                        "text/csv", "ranking.csv", p);
                break;
            default:
                sendError(ex, 404, "Unknown address: " + route);
        }
    }

    // ================= dashboard / lists =================

    private String dashboard() {
        List<Student> withMarks = studentsWithMarks();
        List<RankEntry> ranks = analytics.rankStudents(withMarks);

        StringJoiner top = new StringJoiner(",", "[", "]");
        for (int i = 0; i < Math.min(3, ranks.size()); i++) {
            RankEntry e = ranks.get(i);
            top.add("{\"rank\":" + e.getRank() + ",\"name\":" + quote(e.getStudent().getName())
                    + ",\"percentage\":" + num(e.getStudent().getPercentage())
                    + ",\"grade\":" + quote(e.getGrade().getLabel()) + "}");
        }

        StringJoiner grades = new StringJoiner(",", "[", "]");
        for (Map.Entry<Grade, Integer> e : analytics.gradeDistribution(withMarks).entrySet()) {
            grades.add("{\"grade\":" + quote(e.getKey().getLabel()) + ",\"count\":" + e.getValue() + "}");
        }

        StringJoiner subjectAverages = new StringJoiner(",", "[", "]");
        for (SubjectStats st : analytics.subjectSummary(withMarks, manager.getAllSubjects())) {
            subjectAverages.add("{\"subject\":" + quote(st.getSubject()) + ",\"average\":" + num(st.getAverage())
                    + ",\"students\":" + st.getStudentCount() + "}");
        }

        StringJoiner attention = new StringJoiner(",", "[", "]");
        for (Student s : manager.getAllStudents()) {
            String reason = analytics.attentionReason(s);
            if (!reason.isEmpty()) {
                attention.add("{\"rollNo\":" + quote(s.getRollNo()) + ",\"name\":" + quote(s.getName())
                        + ",\"reason\":" + quote(reason) + "}");
            }
        }

        return "{\"students\":" + manager.size()
                + ",\"subjects\":" + manager.getAllSubjects().size()
                + ",\"studentsWithMarks\":" + withMarks.size()
                + ",\"classAverage\":" + num(analytics.classAverage(withMarks))
                + ",\"passPercentage\":" + num(analytics.passPercentage(withMarks))
                + ",\"attendanceLimit\":" + num(AnalyticsService.ATTENDANCE_LIMIT)
                + ",\"storage\":" + quote(store.getDescription())
                + ",\"top\":" + top
                + ",\"gradeDistribution\":" + grades
                + ",\"subjectAverages\":" + subjectAverages
                + ",\"attention\":" + attention + "}";
    }

    private String studentList() {
        Map<String, Integer> ranks = rankByRoll();
        StringJoiner list = new StringJoiner(",", "[", "]");
        for (Student s : manager.getAllStudents()) {
            list.add(studentJson(s, ranks.getOrDefault(s.getRollNo(), 0), false));
        }
        return list.toString();
    }

    private String studentDetails(Map<String, String> p) throws StudentNotFoundException {
        Student s = manager.getStudent(p.get("roll"));
        return studentJson(s, rankByRoll().getOrDefault(s.getRollNo(), 0), true);
    }

    private String subjectList() {
        StringJoiner list = new StringJoiner(",", "[", "]");
        for (String subject : manager.getAllSubjects()) {
            list.add("{\"name\":" + quote(subject)
                    + ",\"students\":" + manager.countStudentsWithSubject(subject) + "}");
        }
        return list.toString();
    }

    private String ranking() {
        StringJoiner list = new StringJoiner(",", "[", "]");
        for (RankEntry e : analytics.rankStudents(studentsWithMarks())) {
            Student s = e.getStudent();
            list.add("{\"rank\":" + e.getRank()
                    + ",\"rollNo\":" + quote(s.getRollNo())
                    + ",\"name\":" + quote(s.getName())
                    + ",\"total\":" + num(s.getTotalMarks())
                    + ",\"percentage\":" + num(s.getPercentage())
                    + ",\"grade\":" + quote(e.getGrade().getLabel()) + "}");
        }
        return list.toString();
    }

    private String subjectSummary() {
        StringJoiner list = new StringJoiner(",", "[", "]");
        for (SubjectStats st : analytics.subjectSummary(studentsWithMarks(), manager.getAllSubjects())) {
            list.add("{\"subject\":" + quote(st.getSubject())
                    + ",\"students\":" + st.getStudentCount()
                    + ",\"average\":" + num(st.getAverage())
                    + ",\"highest\":" + num(st.getHighest())
                    + ",\"lowest\":" + num(st.getLowest())
                    + ",\"passPercentage\":" + num(st.getPassPercentage())
                    + ",\"topper\":" + quote(st.getTopper()) + "}");
        }
        return list.toString();
    }

    // ================= changes (POST) =================

    private String addStudent(Map<String, String> p) throws DuplicateStudentException, DataStorageException {
        String roll = required(p, "roll").toUpperCase();
        String name = required(p, "name");
        if (!Validator.isValidRollNo(roll)) {
            throw new IllegalArgumentException("Roll number must be 3-15 letters or digits.");
        }
        if (!Validator.isValidName(name)) {
            throw new IllegalArgumentException("Name must be 2-40 characters: letters, spaces, . ' - only.");
        }
        manager.addStudent(new Student(roll, name));
        return message("Student " + name + " added.");
    }

    private String renameStudent(Map<String, String> p) throws StudentNotFoundException, DataStorageException {
        String name = required(p, "name");
        if (!Validator.isValidName(name)) {
            throw new IllegalArgumentException("Name must be 2-40 characters: letters, spaces, . ' - only.");
        }
        manager.renameStudent(required(p, "roll"), name);
        return message("Student renamed to " + name + ".");
    }

    private String deleteStudent(Map<String, String> p) throws StudentNotFoundException, DataStorageException {
        manager.removeStudent(required(p, "roll"));
        return message("Student removed.");
    }

    private String addSubject(Map<String, String> p) throws DataStorageException {
        String subject = cleanSubject(required(p, "name"));
        if (!manager.addSubject(subject)) {
            throw new IllegalArgumentException("Subject " + subject + " already exists.");
        }
        return message("Subject " + subject + " added.");
    }

    private String deleteSubject(Map<String, String> p) throws DataStorageException {
        String subject = cleanSubject(required(p, "name"));
        if (!manager.removeSubject(subject)) {
            throw new IllegalArgumentException("Subject " + subject + " does not exist.");
        }
        return message("Subject " + subject + " and its marks were removed.");
    }

    private String saveMarks(Map<String, String> p)
            throws StudentNotFoundException, InvalidMarksException, DataStorageException {
        String subject = cleanSubject(required(p, "subject"));
        double assessment = Validator.parseInRange(required(p, "assessment"), "Assessment marks", 0, 70);
        double assignment = Validator.parseInRange(required(p, "assignment"), "Assignment score", 0, 20);
        double attendance = Validator.parseInRange(required(p, "attendance"), "Attendance", 0, 100);

        SubjectRecord r = manager.enterMarks(required(p, "roll"), subject, assessment, assignment, attendance);
        return "{\"message\":" + quote("Saved " + subject + " marks.")
                + ",\"total\":" + num(r.getTotal())
                + ",\"grade\":" + quote(analytics.gradeOf(r).getLabel()) + "}";
    }

    private String deleteMarks(Map<String, String> p) throws StudentNotFoundException, DataStorageException {
        String subject = cleanSubject(required(p, "subject"));
        if (!manager.removeMarks(required(p, "roll"), subject)) {
            throw new IllegalArgumentException("That student has no marks in " + subject + ".");
        }
        return message("Marks in " + subject + " removed.");
    }

    private String loadSample() throws InvalidMarksException, DataStorageException {
        manager.replaceAllData(SampleData.createStudents(), SampleData.getSubjects());
        return message("Sample data loaded (" + manager.size() + " students).");
    }

    // ================= helpers =================

    private List<Student> studentsWithMarks() {
        List<Student> list = new ArrayList<>();
        for (Student s : manager.getAllStudents()) {
            if (s.hasRecords()) {
                list.add(s);
            }
        }
        return list;
    }

    private Map<String, Integer> rankByRoll() {
        Map<String, Integer> map = new HashMap<>();
        for (RankEntry e : analytics.rankStudents(studentsWithMarks())) {
            map.put(e.getStudent().getRollNo(), e.getRank());
        }
        return map;
    }

    private String studentJson(Student s, int rank, boolean withRecords) {
        String json = "{\"rollNo\":" + quote(s.getRollNo())
                + ",\"name\":" + quote(s.getName())
                + ",\"subjectCount\":" + s.getRecords().size()
                + ",\"total\":" + num(s.getTotalMarks())
                + ",\"average\":" + num(s.getAverage())
                + ",\"percentage\":" + num(s.getPercentage())
                + ",\"attendance\":" + num(s.getAverageAttendance())
                + ",\"grade\":" + quote(s.hasRecords() ? analytics.gradeOf(s).getLabel() : "-")
                + ",\"rank\":" + rank
                + ",\"attention\":" + quote(analytics.attentionReason(s));
        if (withRecords) {
            StringJoiner records = new StringJoiner(",", "[", "]");
            for (SubjectRecord r : s.getRecords()) {
                records.add("{\"subject\":" + quote(r.getSubject())
                        + ",\"assessment\":" + num(r.getAssessmentMarks())
                        + ",\"assignment\":" + num(r.getAssignmentScore())
                        + ",\"attendance\":" + num(r.getAttendancePercent())
                        + ",\"attendanceMarks\":" + num(r.getAttendanceMarks())
                        + ",\"total\":" + num(r.getTotal())
                        + ",\"grade\":" + quote(analytics.gradeOf(r).getLabel()) + "}");
            }
            json += ",\"records\":" + records;
        }
        return json + "}";
    }

    private String cleanSubject(String subject) {
        if (!Validator.isValidSubject(subject)) {
            throw new IllegalArgumentException("Subject name must be 2-30 characters (letters, digits, space, & + -).");
        }
        return Validator.normalizeSubject(subject);
    }

    private String required(Map<String, String> p, String key) {
        String value = p.get(key);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Please fill in: " + key);
        }
        return value.trim();
    }

    private String message(String text) {
        return "{\"message\":" + quote(text) + "}";
    }

    /**
     * Only accept POST requests that come from our own dashboard page. Without this check another website
     * open in the same browser could secretly send requests to http://localhost.
     */
    private boolean isAllowedOrigin(HttpExchange ex) {
        String origin = ex.getRequestHeaders().getFirst("Origin");
        return origin == null
                || origin.equals("http://localhost:" + port)
                || origin.equals("http://127.0.0.1:" + port);
    }

    /** Reads values from the URL (?roll=S101) and, for POST, from the form body (roll=S101&name=Asha). */
    private Map<String, String> readParams(HttpExchange ex) throws IOException {
        Map<String, String> params = new HashMap<>();
        addParams(params, ex.getRequestURI().getRawQuery());
        if (ex.getRequestMethod().equals("POST")) {
            addParams(params, new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        }
        return params;
    }

    private void addParams(Map<String, String> params, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        for (String pair : text.split("&")) {
            int eq = pair.indexOf('=');
            String key = eq < 0 ? pair : pair.substring(0, eq);
            String value = eq < 0 ? "" : pair.substring(eq + 1);
            params.put(URLDecoder.decode(key, StandardCharsets.UTF_8),
                    URLDecoder.decode(value, StandardCharsets.UTF_8));
        }
    }

    private void sendJson(HttpExchange ex, int status, String json) throws IOException {
        send(ex, status, "application/json; charset=utf-8", json);
    }

    private void sendError(HttpExchange ex, int status, String text) throws IOException {
        sendJson(ex, status, "{\"error\":" + quote(text) + "}");
    }

    /** Sends plain text / CSV. With ?download=1 the browser saves it as a file instead of showing it. */
    private void sendText(HttpExchange ex, String body, String type, String fileName, Map<String, String> p)
            throws IOException {
        if (studentsWithMarks().isEmpty()) {
            sendError(ex, 400, "No marks entered yet, so there is nothing to report.");
            return;
        }
        if ("1".equals(p.get("download"))) {
            ex.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + fileName + "\"");
        }
        send(ex, 200, type + "; charset=utf-8", body);
    }

    private void send(HttpExchange ex, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }
}
