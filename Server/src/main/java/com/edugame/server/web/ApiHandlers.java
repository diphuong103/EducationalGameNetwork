package com.edugame.server.web;

import com.edugame.server.database.GameResultDAO;
import com.edugame.server.database.UserDAO;
import com.edugame.server.game.GameManager;
import com.edugame.server.network.GameServer;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;

/**
 * API Handlers - RESTful JSON API endpoints
 */

/**
 * GET /api/stats - Server statistics in JSON
 */
class ApiStatsHandler implements HttpHandler {

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            // Get server stats
            GameServer gameServer = GameServer.getInstance();
            GameManager gameManager = GameManager.getInstance();
            UserDAO userDAO = new UserDAO();

            Map<String, Object> stats = new HashMap<>();
            stats.put("status", "online");
            stats.put("timestamp", System.currentTimeMillis());
            stats.put("connectedPlayers", gameServer != null ?
                    gameServer.getConnectedClients().size() : 0);
            stats.put("activeSessions", gameManager != null ?
                    gameManager.getAllSessions().size() : 0);
            stats.put("totalUsers", userDAO.getTotalUserCount());
            stats.put("totalGames", userDAO.getTotalGamesPlayed());

            // Server info
            Map<String, Object> serverInfo = new HashMap<>();
            serverInfo.put("gamePort", 12345);
            serverInfo.put("webPort", 8080);
            serverInfo.put("javaVersion", System.getProperty("java.version"));
            serverInfo.put("os", System.getProperty("os.name"));
            serverInfo.put("uptimeMs",
                    java.lang.management.ManagementFactory.getRuntimeMXBean().getUptime());
            stats.put("server", serverInfo);

            String json = gson.toJson(stats);
            WebServer.sendResponse(exchange, 200, json, "application/json");

        } catch (SQLException e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Database error");
            error.put("message", e.getMessage());

            String json = gson.toJson(error);
            WebServer.sendResponse(exchange, 500, json, "application/json");
        }
    }
}

/**
 * GET /api/leaderboard?limit=20 - Top players in JSON
 */
class ApiLeaderboardHandler implements HttpHandler {

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            // Parse query parameters
            String query = exchange.getRequestURI().getQuery();
            int limit = 20; // Default

            if (query != null && query.contains("limit=")) {
                try {
                    String limitStr = query.split("limit=")[1].split("&")[0];
                    limit = Integer.parseInt(limitStr);
                    limit = Math.min(limit, 100); // Max 100
                } catch (Exception e) {
                    // Use default
                }
            }

            // Get top players from UserDAO
            UserDAO userDAO = new UserDAO();
            List<UserDAO.PlayerInfo> topPlayers = userDAO.getTopPlayers(limit);

            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("timestamp", System.currentTimeMillis());
            response.put("count", topPlayers.size());

            List<Map<String, Object>> players = new ArrayList<>();
            for (int i = 0; i < topPlayers.size(); i++) {
                UserDAO.PlayerInfo player = topPlayers.get(i);

                Map<String, Object> playerData = new HashMap<>();
                playerData.put("rank", i + 1);
                playerData.put("userId", player.userId);
                playerData.put("username", player.username);
                playerData.put("fullName", player.fullName);
                playerData.put("totalScore", player.totalScore);
                playerData.put("mathScore", player.mathScore);
                playerData.put("englishScore", player.englishScore);
                playerData.put("literatureScore", player.literatureScore);
                playerData.put("gamesPlayed", player.totalGames);
                playerData.put("wins", player.wins);
                playerData.put("winRate", player.getWinRate());

                players.add(playerData);
            }

            response.put("leaderboard", players);

            String json = gson.toJson(response);
            WebServer.sendResponse(exchange, 200, json, "application/json");

        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Failed to load leaderboard");
            error.put("message", e.getMessage());

            String json = gson.toJson(error);
            WebServer.sendResponse(exchange, 500, json, "application/json");
        }
    }
}