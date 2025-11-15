package com.edugame.server.web;

import com.edugame.server.database.UserDAO;
import com.edugame.server.game.GameManager;
import com.edugame.server.network.GameServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.sql.SQLException;

/**
 * StatisticsHandler - Display server statistics
 */
public class StatisticsHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            // Get server statistics
            GameServer gameServer = GameServer.getInstance();
            GameManager gameManager = GameManager.getInstance();

            int connectedClients = gameServer != null ? gameServer.getConnectedClients().size() : 0;
            int activeSessions = gameManager != null ? gameManager.getAllSessions().size() : 0;

            // Get database stats
            UserDAO userDAO = new UserDAO();
            int totalUsers = userDAO.getTotalUserCount();
            int totalGames = userDAO.getTotalGamesPlayed();

            String html = """
                <!DOCTYPE html>
                <html lang="vi">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>📊 Statistics - Educational Game Server</title>
                    <style>
                        * { margin: 0; padding: 0; box-sizing: border-box; }
                        body {
                            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                            background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
                            min-height: 100vh;
                            padding: 40px 20px;
                        }
                        .container {
                            max-width: 1200px;
                            margin: 0 auto;
                        }
                        .back-button {
                            display: inline-block;
                            background: rgba(255,255,255,0.2);
                            color: white;
                            padding: 10px 20px;
                            border-radius: 25px;
                            text-decoration: none;
                            margin-bottom: 20px;
                            transition: background 0.3s;
                        }
                        .back-button:hover {
                            background: rgba(255,255,255,0.3);
                        }
                        .header {
                            text-align: center;
                            color: white;
                            margin-bottom: 40px;
                        }
                        h1 {
                            font-size: 3em;
                            margin-bottom: 10px;
                            text-shadow: 2px 2px 4px rgba(0,0,0,0.3);
                        }
                        .subtitle {
                            font-size: 1.2em;
                            opacity: 0.9;
                        }
                        .stats-grid {
                            display: grid;
                            grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
                            gap: 25px;
                            margin-bottom: 30px;
                        }
                        .stat-card {
                            background: white;
                            border-radius: 20px;
                            padding: 30px;
                            box-shadow: 0 10px 30px rgba(0,0,0,0.2);
                            transition: transform 0.3s, box-shadow 0.3s;
                        }
                        .stat-card:hover {
                            transform: translateY(-5px);
                            box-shadow: 0 15px 40px rgba(0,0,0,0.3);
                        }
                        .stat-icon {
                            font-size: 3em;
                            margin-bottom: 15px;
                        }
                        .stat-value {
                            font-size: 2.5em;
                            font-weight: bold;
                            color: #667eea;
                            margin-bottom: 10px;
                        }
                        .stat-label {
                            color: #666;
                            font-size: 1.1em;
                            font-weight: 500;
                        }
                        .stat-sublabel {
                            color: #999;
                            font-size: 0.9em;
                            margin-top: 5px;
                        }
                        .status-indicator {
                            display: inline-block;
                            width: 10px;
                            height: 10px;
                            border-radius: 50%;
                            background: #4ade80;
                            margin-right: 8px;
                            animation: pulse 2s infinite;
                        }
                        @keyframes pulse {
                            0%, 100% { opacity: 1; }
                            50% { opacity: 0.5; }
                        }
                        .info-section {
                            background: white;
                            border-radius: 20px;
                            padding: 30px;
                            box-shadow: 0 10px 30px rgba(0,0,0,0.2);
                        }
                        .info-section h2 {
                            color: #667eea;
                            margin-bottom: 20px;
                            font-size: 1.5em;
                        }
                        .info-grid {
                            display: grid;
                            grid-template-columns: repeat(auto-fit, minmax(250px, 1fr));
                            gap: 20px;
                        }
                        .info-item {
                            display: flex;
                            justify-content: space-between;
                            padding: 15px;
                            background: #f8f9fa;
                            border-radius: 10px;
                        }
                        .info-label {
                            color: #666;
                            font-weight: 500;
                        }
                        .info-value {
                            color: #333;
                            font-weight: bold;
                        }
                        .refresh-info {
                            text-align: center;
                            color: white;
                            margin-top: 30px;
                            opacity: 0.8;
                        }
                    </style>
                    <script>
                        // Auto refresh every 5 seconds
                        setTimeout(() => location.reload(), 5000);
                    </script>
                </head>
                <body>
                    <div class="container">
                        <a href="/" class="back-button">← Back to Home</a>
                        
                        <div class="header">
                            <h1>📊 Server Statistics</h1>
                            <p class="subtitle">Real-time monitoring dashboard</p>
                        </div>
                        
                        <div class="stats-grid">
                            <div class="stat-card">
                                <div class="stat-icon">👥</div>
                                <div class="stat-value">""" + connectedClients + """
                                </div>
                                <div class="stat-label">
                                    <span class="status-indicator"></span>
                                    Connected Players
                                </div>
                                <div class="stat-sublabel">Currently online</div>
                            </div>
                            
                            <div class="stat-card">
                                <div class="stat-icon">🎮</div>
                                <div class="stat-value">""" + activeSessions + """
                                </div>
                                <div class="stat-label">Active Games</div>
                                <div class="stat-sublabel">In progress now</div>
                            </div>
                            
                            <div class="stat-card">
                                <div class="stat-icon">👤</div>
                                <div class="stat-value">""" + totalUsers + """
                                </div>
                                <div class="stat-label">Total Users</div>
                                <div class="stat-sublabel">Registered accounts</div>
                            </div>
                            
                            <div class="stat-card">
                                <div class="stat-icon">🏆</div>
                                <div class="stat-value">""" + totalGames + """
                                </div>
                                <div class="stat-label">Games Played</div>
                                <div class="stat-sublabel">All time</div>
                            </div>
                        </div>
                        
                        <div class="info-section">
                            <h2>Server Information</h2>
                            <div class="info-grid">
                                <div class="info-item">
                                    <span class="info-label">Server Status</span>
                                    <span class="info-value">🟢 Running</span>
                                </div>
                                <div class="info-item">
                                    <span class="info-label">Game Port</span>
                                    <span class="info-value">12345</span>
                                </div>
                                <div class="info-item">
                                    <span class="info-label">Web Port</span>
                                    <span class="info-value">8080</span>
                                </div>
                                <div class="info-item">
                                    <span class="info-label">Uptime</span>
                                    <span class="info-value">""" + getUptime() + """
                                    </span>
                                </div>
                                <div class="info-item">
                                    <span class="info-label">Java Version</span>
                                    <span class="info-value">""" + System.getProperty("java.version") + """
                                    </span>
                                </div>
                                <div class="info-item">
                                    <span class="info-label">OS</span>
                                    <span class="info-value">""" + System.getProperty("os.name") + """
                                    </span>
                                </div>
                            </div>
                        </div>
                        
                        <div class="refresh-info">
                            🔄 Auto-refreshing every 5 seconds | Last updated: """ +
                    java.time.LocalDateTime.now().format(
                            java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")) + """
                        </div>
                    </div>
                </body>
                </html>
                """;

            WebServer.sendResponse(exchange, 200, html, "text/html");

        } catch (SQLException e) {
            e.printStackTrace();
            String error = "<html><body><h1>Error loading statistics</h1><p>" +
                    e.getMessage() + "</p></body></html>";
            WebServer.sendResponse(exchange, 500, error, "text/html");
        }
    }

    private String getUptime() {
        long uptimeMillis = java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime();
        long seconds = uptimeMillis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return days + "d " + (hours % 24) + "h";
        } else if (hours > 0) {
            return hours + "h " + (minutes % 60) + "m";
        } else if (minutes > 0) {
            return minutes + "m " + (seconds % 60) + "s";
        } else {
            return seconds + "s";
        }
    }
}