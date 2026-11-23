import com.analytics.service.AnalyticsService;
import com.analytics.service.StandardGradingPolicy;
import com.analytics.service.StudentManager;
import com.analytics.storage.CsvDataStore;
import com.analytics.util.ReportExporter;
import com.analytics.web.WebServer;
import com.sun.net.httpserver.HttpServer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Tests the whole application together: web server + API + calculations + CSV storage.
 * It starts the real server on a free port and talks to it over HTTP, like the dashboard does.
 * Run from the project root (it needs the web folder): see run_tests.sh / run_tests.bat
 */
public class IntegrationTest {
    private static final HttpClient client = HttpClient.newHttpClient();
    private static int passed = 0;
    private static int failed = 0;
    private static String base;

    public static void main(String[] args) throws Exception {
        Path dataFolder = Files.createTempDirectory("analytics-integration");

        HttpServer server = startServer(dataFolder);
        System.out.println("Server started at " + base + " (temporary data folder)");

        // ---- dashboard files are served ----
        check("index.html served", get("/").body().contains("Teacher Dashboard"));
        check("style.css served", get("/style.css").statusCode() == 200);
        check("app.js served", get("/app.js").statusCode() == 200);
        check("unknown file gives 404", get("/missing.html").statusCode() == 404);
        check("path traversal blocked", get("/%2e%2e/README.md").statusCode() == 404);

        // ---- empty state ----
        check("dashboard starts empty", get("/api/dashboard").body().contains("\"students\":0"));
        check("report refused when no marks", get("/api/report/class").statusCode() == 400);

        // ---- registration ----
        check("add student", post("/api/student/add", "roll=cu1&name=Asha+Rao").statusCode() == 200);
        check("add second student", post("/api/student/add", "roll=cu2&name=Bhavya+Singh").statusCode() == 200);
        check("add third student", post("/api/student/add", "roll=cu3&name=Chirag+Mehta").statusCode() == 200);
        check("duplicate roll -> 409", post("/api/student/add", "roll=CU1&name=Someone").statusCode() == 409);
        check("invalid name -> 400", post("/api/student/add", "roll=CU9&name=B4d").statusCode() == 400);
        check("invalid roll -> 400", post("/api/student/add", "roll=a&name=Valid+Name").statusCode() == 400);

        // ---- subjects ----
        check("add subject", post("/api/subject/add", "name=java").body().contains("Subject Java added"));
        check("add second subject", post("/api/subject/add", "name=mathematics").statusCode() == 200);
        check("duplicate subject -> 400", post("/api/subject/add", "name=JAVA").statusCode() == 400);

        // ---- marks and automatic calculations ----
        HttpResponse<String> m1 = post("/api/marks/save", "roll=CU1&subject=Java&assessment=60&assignment=18&attendance=90");
        check("marks total = 60 + 18 + 9 = 87", m1.body().contains("\"total\":87.00"));
        check("grade for 87 is A+", m1.body().contains("\"grade\":\"A+\""));
        post("/api/marks/save", "roll=CU1&subject=Mathematics&assessment=50&assignment=15&attendance=80");  // 73
        post("/api/marks/save", "roll=CU2&subject=Java&assessment=70&assignment=20&attendance=100");        // 100
        post("/api/marks/save", "roll=CU2&subject=Mathematics&assessment=70&assignment=20&attendance=100"); // 100
        post("/api/marks/save", "roll=CU3&subject=Java&assessment=20&assignment=5&attendance=60");          // 31
        check("marks above maximum -> 400", post("/api/marks/save", "roll=CU1&subject=Java&assessment=71&assignment=1&attendance=1").statusCode() == 400);
        check("non-numeric marks -> 400", post("/api/marks/save", "roll=CU1&subject=Java&assessment=abc&assignment=1&attendance=1").statusCode() == 400);
        check("marks for unknown student -> 404", post("/api/marks/save", "roll=NOPE&subject=Java&assessment=1&assignment=1&attendance=1").statusCode() == 404);
        check("rejected marks did not change anything", get("/api/student?roll=CU1").body().contains("\"assessment\":60.00"));

        String student = get("/api/student?roll=CU1").body();
        check("student total = 87 + 73 = 160", student.contains("\"total\":160.00"));
        check("student percentage = 80", student.contains("\"percentage\":80.00"));
        check("student overall grade A+", student.contains("\"grade\":\"A+\",\"rank\":2"));

        // ---- ranking, summary, dashboard ----
        String ranking = get("/api/ranking").body();
        check("ranking: CU2 is rank 1", ranking.startsWith("[{\"rank\":1,\"rollNo\":\"CU2\""));
        check("ranking: CU1 is rank 2", ranking.contains("{\"rank\":2,\"rollNo\":\"CU1\""));
        check("ranking: CU3 is rank 3", ranking.contains("{\"rank\":3,\"rollNo\":\"CU3\""));
        String summary = get("/api/summary").body();
        check("Java average = (87 + 100 + 31) / 3 = 72.67", summary.contains("\"subject\":\"Java\",\"students\":3,\"average\":72.67"));
        check("Mathematics topper is Bhavya", summary.contains("\"subject\":\"Mathematics\"") && summary.contains("\"topper\":\"Bhavya Singh\""));
        String dash = get("/api/dashboard").body();
        check("dashboard: 3 students, 2 subjects", dash.contains("\"students\":3,\"subjects\":2"));
        check("dashboard: Chirag flagged (low attendance, failing)", dash.contains("Chirag Mehta") && dash.contains("Failing in Java"));
        check("dashboard: pass percentage 66.67", dash.contains("\"passPercentage\":66.67"));

        // ---- reports ----
        check("class report text", get("/api/report/class").body().contains("CLASS RANK LIST"));
        check("student report text", get("/api/report/student?roll=CU1").body().contains("Asha Rao"));
        HttpResponse<String> csv = get("/api/export/ranking.csv?download=1");
        check("CSV has header and rows", csv.body().startsWith("Rank,RollNo,Name,Total,Percentage,Grade") && csv.body().contains("1,CU2,Bhavya Singh,200.00,100.00,O"));
        check("CSV download header", csv.headers().firstValue("Content-Disposition").orElse("").contains("ranking.csv"));

        // ---- edit and delete ----
        check("rename student", post("/api/student/rename", "roll=CU3&name=Chirag+M+Mehta").statusCode() == 200);
        check("remove one marks entry", post("/api/marks/delete", "roll=CU3&subject=Java").statusCode() == 200);
        check("removing it twice -> 400", post("/api/marks/delete", "roll=CU3&subject=Java").statusCode() == 400);
        check("ranking now has 2 students", countOccurrences(get("/api/ranking").body(), "\"rank\"") == 2);

        // ---- security: requests from another website are refused ----
        HttpResponse<String> evil = client.send(HttpRequest.newBuilder(URI.create(base + "/api/student/add"))
                .header("Origin", "http://evil.example")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("roll=EVIL&name=Evil+Person")).build(),
                HttpResponse.BodyHandlers.ofString());
        check("request from another website blocked (403)", evil.statusCode() == 403);

        // ---- data survives a restart ----
        server.stop(0);
        server = startServer(dataFolder);
        String after = get("/api/students").body();
        check("after restart: students still there", after.contains("Asha Rao") && after.contains("Bhavya Singh") && after.contains("Chirag M Mehta"));
        check("after restart: marks still there", get("/api/student?roll=CU1").body().contains("\"total\":160.00"));
        check("after restart: subjects still there", get("/api/subjects").body().contains("Mathematics"));
        check("after restart: removed marks stay removed", get("/api/student?roll=CU3").body().contains("\"subjectCount\":0"));
        check("blocked student was never saved", !after.contains("EVIL"));

        // ---- subject deletion removes marks everywhere ----
        check("delete subject", post("/api/subject/delete", "name=Mathematics").statusCode() == 200);
        check("subject gone from student", !get("/api/student?roll=CU1").body().contains("Mathematics"));
        check("sample data loads", post("/api/sample/load", "").body().contains("6 students"));
        check("sample ranking has Diya first", get("/api/ranking").body().startsWith("[{\"rank\":1,\"rollNo\":\"S102\""));

        server.stop(0);
        System.out.println("\nPassed: " + passed + "  Failed: " + failed);
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static HttpServer startServer(Path dataFolder) throws Exception {
        AnalyticsService analytics = new AnalyticsService(new StandardGradingPolicy());
        CsvDataStore store = new CsvDataStore(dataFolder);
        StudentManager manager = new StudentManager(store);
        manager.loadFromStore();
        HttpServer server = WebServer.start(0, Paths.get("web"), manager, analytics, new ReportExporter(analytics), store);
        base = "http://localhost:" + server.getAddress().getPort();
        return server;
    }

    private static HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static HttpResponse<String> post(String path, String formBody) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(base + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formBody)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static int countOccurrences(String text, String part) {
        int count = 0;
        for (int i = text.indexOf(part); i >= 0; i = text.indexOf(part, i + 1)) {
            count++;
        }
        return count;
    }

    private static void check(String name, boolean ok) {
        if (ok) {
            passed++;
            System.out.println("  PASS  " + name);
        } else {
            failed++;
            System.out.println("  FAIL  " + name);
        }
    }
}
