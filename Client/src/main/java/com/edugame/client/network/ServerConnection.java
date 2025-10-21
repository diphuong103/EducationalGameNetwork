package com.edugame.client.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.*;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class ServerConnection {
    private static ServerConnection instance;
    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private Gson gson;
    private boolean connected;

    // User session data
    private String currentUsername;
    private int currentUserId;
    private String currentFullName;
    private String currentEmail;
    private String currentAvatarUrl;
    private int totalScore;
    private int mathScore;
    private int englishScore;
    private int scienceScore;
    private int totalGames;
    private int wins;
    private int currentLevel;

    private ServerConnection() {
        gson = new Gson();
        connected = false;
    }

    public static synchronized ServerConnection getInstance() {
        if (instance == null) {
            instance = new ServerConnection();
        }
        return instance;
    }

    /** Connect to server */
    public boolean connect(String host, int port) {
        try {
            socket = new Socket(host, port);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(socket.getOutputStream(), true);
            connected = true;

            System.out.println("Connected to server: " + host + ":" + port);
            return true;
        } catch (IOException e) {
            System.err.println("Failed to connect to server: " + e.getMessage());
            connected = false;
            return false;
        }
    }

    /** Login to server */
    public boolean login(String username, String password) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("type", "LOGIN");
            request.put("username", username);
            request.put("password", password);

            writer.println(gson.toJson(request));

            String response = reader.readLine();
            JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);

            boolean success = jsonResponse.get("success").getAsBoolean();
            if (success) {
                currentUsername = username;
                currentUserId = jsonResponse.get("userId").getAsInt();
                currentFullName = jsonResponse.get("fullName").getAsString();
                currentEmail = jsonResponse.get("email").getAsString();
                currentAvatarUrl = jsonResponse.get("avatarUrl").getAsString();
                totalScore = jsonResponse.get("totalScore").getAsInt();
                mathScore = jsonResponse.get("mathScore").getAsInt();
                englishScore = jsonResponse.get("englishScore").getAsInt();
                scienceScore = jsonResponse.get("scienceScore").getAsInt();
                totalGames = jsonResponse.get("totalGames").getAsInt();
                wins = jsonResponse.get("wins").getAsInt();
                currentLevel = calculateLevel(totalScore);

                System.out.println("Login successful: " + username);
            } else {
                System.out.println("Login failed: " + jsonResponse.get("message").getAsString());
            }

            return success;
        } catch (IOException e) {
            System.err.println("Login error: " + e.getMessage());
            return false;
        }
    }

    /** Calculate level */
    private int calculateLevel(int score) {
        return (score / 200) + 1;
    }

    /** Register new user */
    public boolean register(String username, String password, String email,
                            String fullName, String age, String avatar) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("type", "REGISTER");
            request.put("username", username);
            request.put("password", password);
            request.put("email", email.isEmpty() ? username + "@mathadventure.com" : email);
            request.put("fullName", fullName);
            request.put("age", age);
            request.put("avatar", avatar);

            writer.println(gson.toJson(request));

            String response = reader.readLine();
            JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);

            boolean success = jsonResponse.get("success").getAsBoolean();
            if (success) {
                System.out.println("Registration successful: " + username);
            } else {
                System.out.println("Registration failed: " + jsonResponse.get("message").getAsString());
            }

            return success;
        } catch (IOException e) {
            System.err.println("Registration error: " + e.getMessage());
            return false;
        }
    }

    /** Send message */
    public void sendMessage(String message) {
        if (writer != null) {
            writer.println(message);
        }
    }

    /** Send JSON */
    public void sendJson(Map<String, Object> data) {
        sendMessage(gson.toJson(data));
    }

    /** Receive message */
    public String receiveMessage() throws IOException {
        if (reader != null) {
            return reader.readLine();
        }
        return null;
    }

    /** Get leaderboard */
    public java.util.List<java.util.Map<String, Object>> getLeaderboard(int limit) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("type", "GET_LEADERBOARD");
            request.put("limit", limit);
            request.put("subject", "total");

            writer.println(gson.toJson(request));

            String response = reader.readLine();
            JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);

            boolean success = jsonResponse.get("success").getAsBoolean();
            if (success) {
                com.google.gson.JsonArray arr = jsonResponse.getAsJsonArray("leaderboard");
                java.util.List<java.util.Map<String, Object>> leaderboard = new java.util.ArrayList<>();

                for (int i = 0; i < arr.size(); i++) {
                    com.google.gson.JsonObject user = arr.get(i).getAsJsonObject();
                    Map<String, Object> map = new HashMap<>();
                    map.put("userId", user.get("userId").getAsInt());
                    map.put("username", user.get("username").getAsString());
                    map.put("fullName", user.get("fullName").getAsString());
                    map.put("totalScore", user.get("totalScore").getAsInt());
                    map.put("isOnline", user.get("isOnline").getAsBoolean());
                    leaderboard.add(map);
                }

                System.out.println("✓ Leaderboard loaded: " + leaderboard.size());
                return leaderboard;
            }

        } catch (IOException e) {
            System.err.println("✗ Error getting leaderboard: " + e.getMessage());
        }
        return new java.util.ArrayList<>();
    }

    /** Disconnect */
    public void disconnect() {
        try {
            if (socket != null && !socket.isClosed()) {
                Map<String, Object> req = new HashMap<>();
                req.put("type", "LOGOUT");
                req.put("username", currentUsername);
                sendJson(req);
                socket.close();
                connected = false;
                System.out.println("Disconnected from server");
            }
        } catch (IOException e) {
            System.err.println("Error disconnecting: " + e.getMessage());
        }
    }

    // Getters
    public boolean isConnected() { return connected && socket != null && !socket.isClosed(); }
    public String getCurrentUsername() { return currentUsername; }
    public int getCurrentUserId() { return currentUserId; }
    public String getCurrentFullName() { return currentFullName; }
    public String getCurrentEmail() { return currentEmail; }
    public String getCurrentAvatarUrl() { return currentAvatarUrl; }
    public int getTotalScore() { return totalScore; }
    public int getMathScore() { return mathScore; }
    public int getEnglishScore() { return englishScore; }
    public int getScienceScore() { return scienceScore; }
    public int getTotalGames() { return totalGames; }
    public int getWins() { return wins; }
    public int getCurrentLevel() { return currentLevel; }
}
