package com.analytics.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Serves the dashboard files (index.html, style.css, app.js) from the web folder. */
public class StaticFileHandler implements HttpHandler {
    private final Path webRoot;

    public StaticFileHandler(Path webRoot) {
        this.webRoot = webRoot.toAbsolutePath().normalize();
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/")) {
            path = "/index.html";
        }
        Path file = webRoot.resolve(path.substring(1)).normalize();

        // refuse paths like /../secret.txt that try to leave the web folder
        if (!file.startsWith(webRoot) || !Files.isRegularFile(file)) {
            byte[] msg = "404 - page not found".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(404, msg.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(msg);
            }
            return;
        }
        byte[] data = Files.readAllBytes(file);
        exchange.getResponseHeaders().set("Content-Type", contentType(file.toString()));
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.sendResponseHeaders(200, data.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(data);
        }
    }

    private String contentType(String name) {
        if (name.endsWith(".html")) return "text/html; charset=utf-8";
        if (name.endsWith(".css")) return "text/css; charset=utf-8";
        if (name.endsWith(".js")) return "application/javascript; charset=utf-8";
        if (name.endsWith(".svg")) return "image/svg+xml";
        return "application/octet-stream";
    }
}
