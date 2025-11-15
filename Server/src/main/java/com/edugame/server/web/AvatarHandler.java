package com.edugame.server.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * ✅ AvatarHandler - Serve avatar images từ resources
 */
public class AvatarHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String filename = path.substring(path.lastIndexOf('/') + 1);

        // Security: Chỉ cho phép file image
        if (!filename.matches(".*\\.(png|jpg|jpeg|gif|webp)$")) {
            exchange.sendResponseHeaders(400, 0);
            exchange.close();
            return;
        }

        try {
            // 1. Thử load từ resources (trong JAR)
            InputStream resourceStream = getClass().getResourceAsStream("/images/avatars/" + filename);

            if (resourceStream != null) {
                byte[] imageBytes = resourceStream.readAllBytes();
                resourceStream.close();

                String contentType = getContentType(filename);
                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.getResponseHeaders().set("Cache-Control", "public, max-age=86400"); // Cache 1 ngày
                exchange.sendResponseHeaders(200, imageBytes.length);

                OutputStream os = exchange.getResponseBody();
                os.write(imageBytes);
                os.close();

                System.out.println("✅ Served avatar from resources: " + filename);
                return;
            }

            // 2. Thử load từ filesystem (nếu có thư mục external)
            Path externalPath = Paths.get("avatars/" + filename);
            if (Files.exists(externalPath)) {
                byte[] imageBytes = Files.readAllBytes(externalPath);

                String contentType = getContentType(filename);
                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.sendResponseHeaders(200, imageBytes.length);

                OutputStream os = exchange.getResponseBody();
                os.write(imageBytes);
                os.close();

                System.out.println("✅ Served avatar from filesystem: " + filename);
                return;
            }

            // 3. Không tìm thấy → 404
            send404(exchange, filename);

        } catch (IOException e) {
            System.err.println("❌ Error serving avatar: " + e.getMessage());
            send404(exchange, filename);
        }
    }

    private String getContentType(String filename) {
        String lower = filename.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        return "application/octet-stream";
    }

    private void send404(HttpExchange exchange, String filename) throws IOException {
        String notFound = "Avatar not found: " + filename;
        exchange.sendResponseHeaders(404, notFound.length());
        OutputStream os = exchange.getResponseBody();
        os.write(notFound.getBytes());
        os.close();

        System.err.println("⚠️ Avatar not found: " + filename);
    }
}