package com.analytics.web;

import com.analytics.service.AnalyticsService;
import com.analytics.service.StudentManager;
import com.analytics.storage.DataStore;
import com.analytics.util.ReportExporter;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Path;

/** Starts the small web server that serves the dashboard and the /api/ address. */
public class WebServer {
    private WebServer() {
    }

    /** Pass port 0 to let the system pick a free port (used by the tests). */
    public static HttpServer start(int port, Path webFolder, StudentManager manager, AnalyticsService analytics,
                                   ReportExporter exporter, DataStore store) throws IOException {
        // listen on this computer only (localhost), nobody else on the network can open the dashboard
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 0);
        int realPort = server.getAddress().getPort();

        server.createContext("/api/", new ApiHandler(manager, analytics, exporter, store, realPort));
        server.createContext("/", new StaticFileHandler(webFolder));

        // no executor = requests are handled one at a time, so two requests can never change the data together
        server.setExecutor(null);
        server.start();
        return server;
    }
}
