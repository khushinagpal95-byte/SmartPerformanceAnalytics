package com.analytics;

import com.analytics.exception.DataStorageException;
import com.analytics.service.AnalyticsService;
import com.analytics.service.StandardGradingPolicy;
import com.analytics.service.StudentManager;
import com.analytics.storage.CsvDataStore;
import com.analytics.storage.DataStore;
import com.analytics.storage.JdbcDataStore;
import com.analytics.ui.ConsoleMenu;
import com.analytics.util.ReportExporter;
import com.analytics.web.WebServer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * Entry point.
 *   java -cp ... com.analytics.Main            -> starts the web dashboard (default)
 *   java -cp ... com.analytics.Main console    -> starts the console menu
 */
public class Main {
    public static void main(String[] args) {
        boolean consoleMode = args.length > 0 && args[0].equalsIgnoreCase("console");

        Properties config = loadConfig();
        DataStore store = createStore(config);

        AnalyticsService analytics = new AnalyticsService(new StandardGradingPolicy());
        ReportExporter exporter = new ReportExporter(analytics);
        StudentManager manager = new StudentManager(store);
        try {
            manager.loadFromStore();
            System.out.println("Storage: " + store.getDescription() + " - loaded " + manager.size() + " student(s).");
        } catch (DataStorageException e) {
            System.out.println("Could not load saved data: " + e.getMessage());
        }

        if (consoleMode) {
            new ConsoleMenu(manager, analytics, exporter).run();
            return;
        }

        int port = Integer.parseInt(config.getProperty("server.port", "8080").trim());
        try {
            WebServer.start(port, Paths.get("web"), manager, analytics, exporter, store);
            System.out.println("Dashboard is running at  http://localhost:" + port);
            System.out.println("Press Ctrl+C to stop.");
        } catch (IOException e) {
            System.out.println("Could not start the web server on port " + port + ": " + e.getMessage());
            System.out.println("Is another program using this port? Change server.port in config/app.properties.");
        }
    }

    /** Reads config/app.properties. If the file is missing, CSV storage and port 8080 are used. */
    private static Properties loadConfig() {
        Properties config = new Properties();
        Path file = Paths.get("config", "app.properties");
        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                config.load(in);
            } catch (IOException e) {
                System.out.println("Could not read " + file + ": " + e.getMessage());
            }
        }
        return config;
    }

    /** Uses MySQL if it is switched on and reachable, otherwise falls back to CSV files. */
    private static DataStore createStore(Properties config) {
        if (Boolean.parseBoolean(config.getProperty("db.enabled", "false").trim())) {
            try {
                DataStore store = new JdbcDataStore(
                        config.getProperty("db.url", "").trim(),
                        config.getProperty("db.user", "root").trim(),
                        config.getProperty("db.password", ""));
                System.out.println("Connected to MySQL.");
                return store;
            } catch (DataStorageException e) {
                System.out.println("MySQL is not available: " + e.getMessage());
                System.out.println("Using CSV files in the 'data' folder instead.");
            }
        }
        return new CsvDataStore(Paths.get("data"));
    }
}
