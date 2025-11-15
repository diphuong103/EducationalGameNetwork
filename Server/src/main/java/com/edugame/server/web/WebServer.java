package com.edugame.server.web;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;

/**
 * WebServer - Simple HTTP Server for web interface
 *
 * Features:
 * - Leaderboard page
 * - Statistics page
 * - API endpoints
 *
 * Demonstrates:
 * - HTTP protocol understanding
 * - Web server implementation
 * - Request/Response handling
 */
public class WebServer {

    private static final int WEB_PORT = 8080;
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private HttpServer server;
    private boolean running;

    public WebServer() {
        this.running = false;
    }

    /**
     * Start the HTTP web server
     */
    public void start() throws IOException {
        // Create HTTP server
        server = HttpServer.create(new InetSocketAddress(WEB_PORT), 0);

        // Register endpoints
        server.createContext("/", new HomeHandler());
        server.createContext("/leaderboard", new LeaderboardHandler());
        server.createContext("/statistics", new StatisticsHandler());
        server.createContext("/profile", new ProfileHandler());
        server.createContext("/api/stats", new ApiStatsHandler());
        server.createContext("/api/leaderboard", new ApiLeaderboardHandler());


        server.createContext("/avatars", new AvatarHandler());

        // Set executor (thread pool)
        server.setExecutor(Executors.newFixedThreadPool(10));

        // Start server
        server.start();
        running = true;

        log("✅ Web Server started on port " + WEB_PORT);
        log("📱 Access at: http://localhost:" + WEB_PORT);
        log("📊 Leaderboard: http://localhost:" + WEB_PORT + "/leaderboard");
        log("📈 Statistics: http://localhost:" + WEB_PORT + "/statistics");
    }

    /**
     * Stop the web server
     */
    public void stop() {
        if (server != null && running) {
            server.stop(0);
            running = false;
            log("🛑 Web Server stopped");
        }
    }

    /**
     * Check if server is running
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Get web server URL
     */
    public String getUrl() {
        return "http://localhost:" + WEB_PORT;
    }

    // ==================== HANDLERS ====================

    /**
     * Home page handler
     */
    static class HomeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String html = """
                <!DOCTYPE html>
                <html lang="vi">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Educational Game Server</title>
                    <style>
                        * { margin: 0; padding: 0; box-sizing: border-box; }
                        body {
                            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                            min-height: 100vh;
                            display: flex;
                            justify-content: center;
                            align-items: center;
                            padding: 20px;
                        }
                        .container {
                            background: white;
                            border-radius: 20px;
                            box-shadow: 0 20px 60px rgba(0,0,0,0.3);
                            padding: 50px;
                            max-width: 600px;
                            text-align: center;
                        }
                        h1 {
                            color: #667eea;
                            font-size: 2.5em;
                            margin-bottom: 10px;
                        }
                        .subtitle {
                            color: #666;
                            font-size: 1.1em;
                            margin-bottom: 40px;
                        }
                        .status {
                            display: inline-block;
                            background: #4ade80;
                            color: white;
                            padding: 8px 20px;
                            border-radius: 20px;
                            font-weight: bold;
                            margin-bottom: 30px;
                        }
                        .links {
                            display: flex;
                            flex-direction: column;
                            gap: 15px;
                        }
                        .link-button {
                            display: block;
                            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                            color: white;
                            padding: 15px 30px;
                            border-radius: 10px;
                            text-decoration: none;
                            font-size: 1.1em;
                            font-weight: bold;
                            transition: transform 0.2s, box-shadow 0.2s;
                        }
                        .link-button:hover {
                            transform: translateY(-2px);
                            box-shadow: 0 10px 25px rgba(102, 126, 234, 0.4);
                        }
                        .info {
                            margin-top: 40px;
                            padding: 20px;
                            background: #f8f9fa;
                            border-radius: 10px;
                            text-align: left;
                        }
                        .info h3 {
                            color: #667eea;
                            margin-bottom: 15px;
                        }
                        .info-item {
                            display: flex;
                            justify-content: space-between;
                            padding: 10px 0;
                            border-bottom: 1px solid #e0e0e0;
                        }
                        .info-item:last-child {
                            border-bottom: none;
                        }
                        .info-label {
                            color: #666;
                            font-weight: 500;
                        }
                        .info-value {
                            color: #333;
                            font-weight: bold;
                        }
                    </style>
                </head>
                <body>
                    <div class="container">
                        <h1>🎮 Educational Game Server</h1>
                        <p class="subtitle">Multiplayer Quiz Racing Game</p>
                        
                        <div class="status">🟢 Server Online</div>
                        
                        <div class="links">
                            <a href="/leaderboard" class="link-button">
                                🏆 View Leaderboard
                            </a>
                            <a href="/statistics" class="link-button">
                                📊 Server Statistics
                            </a>
                            <a href="/api/stats" class="link-button">
                                📡 API Stats (JSON)
                            </a>
                        </div>
                        
                        <div class="info">
                            <h3>Server Information</h3>
                            <div class="info-item">
                                <span class="info-label">Game Port:</span>
                                <span class="info-value">12345</span>
                            </div>
                            <div class="info-item">
                                <span class="info-label">Web Port:</span>
                                <span class="info-value">8080</span>
                            </div>
                            <div class="info-item">
                                <span class="info-label">Status:</span>
                                <span class="info-value">Running</span>
                            </div>
                            <div class="info-item">
                                <span class="info-label">Server Time:</span>
                                <span class="info-value">""" + LocalDateTime.now().format(TIME_FORMAT) + """
                                </span>
                            </div>
                        </div>
                    </div>
                </body>
                </html>
                """;

            sendResponse(exchange, 200, html, "text/html");
        }
    }

    // ==================== UTILITY METHODS ====================

    /**
     * Send HTTP response
     */
    static void sendResponse(HttpExchange exchange, int statusCode,
                             String response, String contentType) throws IOException {
        byte[] bytes = response.getBytes("UTF-8");

        exchange.getResponseHeaders().set("Content-Type", contentType + "; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(statusCode, bytes.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }

        log("📤 " + exchange.getRequestMethod() + " " +
                exchange.getRequestURI() + " → " + statusCode);
    }

    /**
     * Logging with timestamp
     */
    private static void log(String message) {
        String timestamp = LocalDateTime.now().format(TIME_FORMAT);
        System.out.println("[" + timestamp + "] [WebServer] " + message);
    }
}