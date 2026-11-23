package com.analytics.storage;

import com.analytics.exception.DataStorageException;
import com.analytics.exception.InvalidMarksException;
import com.analytics.model.Student;
import com.analytics.model.SubjectRecord;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MySQL persistence using JDBC. Three tables: students, subjects, marks (see database/schema.sql).
 *
 * Design choice: save() writes the complete data inside ONE transaction (delete everything, insert everything).
 * This is simple and always consistent, and fast enough for a class-sized amount of data.
 */
public class JdbcDataStore implements DataStore {
    private static final String CREATE_STUDENTS =
            "CREATE TABLE IF NOT EXISTS students ("
            + "roll_no VARCHAR(15) PRIMARY KEY, "
            + "name VARCHAR(40) NOT NULL)";

    private static final String CREATE_SUBJECTS =
            "CREATE TABLE IF NOT EXISTS subjects ("
            + "subject_name VARCHAR(30) PRIMARY KEY)";

    private static final String CREATE_MARKS =
            "CREATE TABLE IF NOT EXISTS marks ("
            + "roll_no VARCHAR(15) NOT NULL, "
            + "subject_name VARCHAR(30) NOT NULL, "
            + "assessment DOUBLE NOT NULL, "
            + "assignment DOUBLE NOT NULL, "
            + "attendance DOUBLE NOT NULL, "
            + "PRIMARY KEY (roll_no, subject_name), "
            + "FOREIGN KEY (roll_no) REFERENCES students(roll_no) ON DELETE CASCADE, "
            + "FOREIGN KEY (subject_name) REFERENCES subjects(subject_name) ON DELETE CASCADE)";

    private static final String DELETE_MARKS = "DELETE FROM marks";
    private static final String DELETE_STUDENTS = "DELETE FROM students";
    private static final String DELETE_SUBJECTS = "DELETE FROM subjects";

    private static final String INSERT_SUBJECT = "INSERT INTO subjects (subject_name) VALUES (?)";
    private static final String INSERT_STUDENT = "INSERT INTO students (roll_no, name) VALUES (?, ?)";
    private static final String INSERT_MARKS =
            "INSERT INTO marks (roll_no, subject_name, assessment, assignment, attendance) "
            + "VALUES (?, ?, ?, ?, ?)";

    private static final String SELECT_STUDENTS = "SELECT roll_no, name FROM students ORDER BY roll_no";
    private static final String SELECT_SUBJECTS = "SELECT subject_name FROM subjects ORDER BY subject_name";
    private static final String SELECT_MARKS =
            "SELECT roll_no, subject_name, assessment, assignment, attendance FROM marks ORDER BY subject_name";

    private final String url;
    private final String user;
    private final String password;

    /** Connects once to check the settings and creates the tables if they do not exist yet. */
    public JdbcDataStore(String url, String user, String password) throws DataStorageException {
        this.url = url;
        this.user = user;
        this.password = password;
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new DataStorageException(
                    "MySQL driver not found. Download mysql-connector-j and put the .jar file in the lib folder.", e);
        }
        createTables();
    }

    @Override
    public String getDescription() {
        return "MySQL database";
    }

    private Connection connect() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    private void createTables() throws DataStorageException {
        try (Connection con = connect(); Statement st = con.createStatement()) {
            st.executeUpdate(CREATE_STUDENTS);
            st.executeUpdate(CREATE_SUBJECTS);
            st.executeUpdate(CREATE_MARKS);
        } catch (SQLException e) {
            throw new DataStorageException("Could not connect to MySQL: " + e.getMessage(), e);
        }
    }

    @Override
    public void save(Collection<Student> students, Collection<String> subjects) throws DataStorageException {
        try (Connection con = connect()) {
            con.setAutoCommit(false); // start a transaction: everything is saved or nothing is
            try {
                try (Statement st = con.createStatement()) {
                    st.executeUpdate(DELETE_MARKS);    // marks first, because they refer to students and subjects
                    st.executeUpdate(DELETE_STUDENTS);
                    st.executeUpdate(DELETE_SUBJECTS);
                }
                insertSubjects(con, subjects);
                insertStudents(con, students);
                insertMarks(con, students);
                con.commit();
            } catch (SQLException e) {
                con.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataStorageException("Could not save data to MySQL: " + e.getMessage(), e);
        }
    }

    private void insertSubjects(Connection con, Collection<String> subjects) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(INSERT_SUBJECT)) {
            for (String subject : subjects) {
                ps.setString(1, subject);
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void insertStudents(Connection con, Collection<Student> students) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(INSERT_STUDENT)) {
            for (Student s : students) {
                ps.setString(1, s.getRollNo());
                ps.setString(2, s.getName());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    private void insertMarks(Connection con, Collection<Student> students) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(INSERT_MARKS)) {
            for (Student s : students) {
                for (SubjectRecord r : s.getRecords()) {
                    ps.setString(1, s.getRollNo());
                    ps.setString(2, r.getSubject());
                    ps.setDouble(3, r.getAssessmentMarks());
                    ps.setDouble(4, r.getAssignmentScore());
                    ps.setDouble(5, r.getAttendancePercent());
                    ps.addBatch();
                }
            }
            ps.executeBatch();
        }
    }

    @Override
    public List<Student> loadStudents() throws DataStorageException {
        Map<String, Student> students = new LinkedHashMap<>();
        try (Connection con = connect();
             Statement st = con.createStatement()) {

            try (ResultSet rs = st.executeQuery(SELECT_STUDENTS)) {
                while (rs.next()) {
                    String roll = rs.getString("roll_no");
                    students.put(roll, new Student(roll, rs.getString("name")));
                }
            }
            try (ResultSet rs = st.executeQuery(SELECT_MARKS)) {
                while (rs.next()) {
                    Student s = students.get(rs.getString("roll_no"));
                    if (s == null) {
                        continue;
                    }
                    try {
                        s.putRecord(new SubjectRecord(rs.getString("subject_name"),
                                rs.getDouble("assessment"),
                                rs.getDouble("assignment"),
                                rs.getDouble("attendance")));
                    } catch (InvalidMarksException e) {
                        System.out.println("  [warning] ignored invalid row in marks table: " + e.getMessage());
                    }
                }
            }
        } catch (SQLException e) {
            throw new DataStorageException("Could not read students from MySQL: " + e.getMessage(), e);
        }
        return new ArrayList<>(students.values());
    }

    @Override
    public List<String> loadSubjects() throws DataStorageException {
        List<String> subjects = new ArrayList<>();
        try (Connection con = connect();
             Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(SELECT_SUBJECTS)) {
            while (rs.next()) {
                subjects.add(rs.getString("subject_name"));
            }
        } catch (SQLException e) {
            throw new DataStorageException("Could not read subjects from MySQL: " + e.getMessage(), e);
        }
        return subjects;
    }
}
