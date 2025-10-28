package com.edugame.client.network;

import com.edugame.client.model.User;
import com.edugame.common.Protocol;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class ServerConnection {
    private static ServerConnection instance;
    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private Gson gson;
    private volatile boolean connected;

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

    private User currentUser;

    // Listener management
    private Thread listenerThread;
    private volatile boolean isListening = false;

    // Callback storage for different message types
    private Consumer<JsonObject> leaderboardCallback;
    private Consumer<JsonObject> profileCallback;
    private Consumer<JsonObject> chatCallback;
    private Map<String, Consumer<JsonObject>> pendingRequests = new ConcurrentHashMap<>();

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

            System.out.println("✅ Connected to server: " + host + ":" + port);
            return true;
        } catch (IOException e) {
            System.err.println("❌ Failed to connect to server: " + e.getMessage());
            connected = false;
            return false;
        }
    }

    /**
     * Start listener thread - chỉ gọi SAU KHI login thành công
     */
    public void startListener() {
        synchronized (this) {
            if (isListening && listenerThread != null && listenerThread.isAlive()) {
                System.out.println("⚠️ Listener already running");
                return;
            }

            System.out.println("🚀 Starting listener thread...");
            isListening = true;

            listenerThread = new Thread(() -> {
                System.out.println("🎧 Listener thread STARTED");

                try {
                    String line;
                    while (isListening && isConnected() && (line = reader.readLine()) != null) {

                        try {
                            JsonObject json = gson.fromJson(line, JsonObject.class);
                            String type = json.has("type") ? json.get("type").getAsString() : "UNKNOWN";

                            System.out.println("📨 Received: " + type);

                            // Route message to appropriate handler
                            handleIncomingMessage(type, json);

                        } catch (Exception e) {
                            System.err.println("❌ Error parsing message: " + e.getMessage());
                        }
                    }

                } catch (IOException e) {
                    if (isListening) {
                        System.err.println("❌ Listener IOException: " + e.getMessage());
                    }
                } finally {
                    isListening = false;
                    System.out.println("🛑 Listener thread STOPPED");
                }
            }, "ServerListener");

            listenerThread.setDaemon(true);
            listenerThread.start();

            // Wait for thread to actually start
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            System.out.println("✅ Listener thread ready");
        }
    }

    /**
     * Route incoming messages to appropriate callbacks
     */
    private void handleIncomingMessage(String type, JsonObject json) {
        switch (type) {
            case Protocol.GET_PROFILE:
                if (profileCallback != null) {
                    profileCallback.accept(json);
                    profileCallback = null; // One-time callback
                }
                break;

            case Protocol.GET_LEADERBOARD:
                if (leaderboardCallback != null) {
                    leaderboardCallback.accept(json);
                    leaderboardCallback = null; // One-time callback
                }
                break;

            case Protocol.GLOBAL_CHAT:
            case "SYSTEM_MESSAGE":
                if (chatCallback != null) {
                    chatCallback.accept(json);
                }
                break;

            default:
                // Check pending requests for other types
                Consumer<JsonObject> callback = pendingRequests.remove(type);
                if (callback != null) {
                    callback.accept(json);
                }
                break;
        }
    }

    /** Login to server */
    public boolean login(String username, String password) {
        try {
            clearSessionData();

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
                scienceScore = jsonResponse.get("literatureScore").getAsInt();
                totalGames = jsonResponse.get("totalGames").getAsInt();
                wins = jsonResponse.get("wins").getAsInt();
                currentLevel = calculateLevel(totalScore);

                // ✅ Start listener SAU KHI login thành công
                startListener();

                System.out.println("✅ Login successful: " + username);
            } else {
                System.out.println("❌ Login failed: " + jsonResponse.get("message").getAsString());
            }

            return success;

        } catch (IOException e) {
            System.err.println("❌ Login error: " + e.getMessage());
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
                System.out.println("✅ Registration successful: " + username);
            } else {
                System.out.println("❌ Registration failed: " + jsonResponse.get("message").getAsString());
            }

            return success;
        } catch (IOException e) {
            System.err.println("❌ Registration error: " + e.getMessage());
            return false;
        }
    }

    /** Send JSON with error handling */
    public void sendJson(Map<String, Object> data) {
        if (!isConnected()) {
            System.err.println("❌ Cannot send - not connected");
            return;
        }

        if (writer != null && !writer.checkError()) {
            String json = gson.toJson(data);
            System.out.println("📤 Sending: " + data.get("type") + " (subject: " + data.get("subject") + ")");
            writer.println(json);
            writer.flush();

            if (writer.checkError()) {
                System.err.println("❌ Writer error after flush!");
            }
        } else {
            System.err.println("❌ Writer is null or has error");
        }
    }

    /**
     * Get profile with callback
     */
    public void getProfile(Consumer<User> callback) {
        if (!isConnected()) {
            System.err.println("❌ Cannot get profile - not connected");
            callback.accept(null);
            return;
        }

        System.out.println("📝 Getting profile...");

        // Register callback BEFORE sending request
        profileCallback = (json) -> {
            try {
                System.out.println("🔄 Profile callback executing");
                User user = gson.fromJson(json, User.class);
                currentUser = user;
                System.out.println("✅ Profile loaded: " + user.getFullName());
                callback.accept(user);
            } catch (Exception e) {
                System.err.println("❌ Error parsing profile: " + e.getMessage());
                e.printStackTrace();
                callback.accept(null);
            }
        };

        // Send request
        Map<String, Object> req = new HashMap<>();
        req.put("type", Protocol.GET_PROFILE);
        sendJson(req);

        // Timeout handler
        new Thread(() -> {
            try {
                Thread.sleep(5000); // 5 second timeout
                if (profileCallback != null) {
                    System.err.println("⚠️ Profile request timeout");
                    profileCallback = null;
                    callback.accept(null);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, "ProfileTimeout").start();
    }

    /**
     * Get leaderboard with callback (mặc định lấy tổng điểm)
     */
    public void getLeaderboard(int limit, Consumer<List<Map<String, Object>>> callback) {
        getLeaderboardBySubject("total", limit, callback);
    }

    /**
     * Get leaderboard by subject with callback
     */
    public void getLeaderboardBySubject(String subject, int limit, Consumer<List<Map<String, Object>>> callback) {
        if (!isConnected()) {
            System.err.println("❌ Cannot get leaderboard - not connected");
            callback.accept(new ArrayList<>());
            return;
        }

        System.out.println("📊 Getting leaderboard for subject: " + subject);

        // Register callback BEFORE sending request
        leaderboardCallback = (json) -> {
            try {
                System.out.println("🔄 Leaderboard callback executing for: " + subject);

                boolean success = json.get("success").getAsBoolean();
                if (!success) {
                    System.err.println("❌ Leaderboard request failed");
                    callback.accept(new ArrayList<>());
                    return;
                }

                JsonArray arr = json.getAsJsonArray("leaderboard");
                List<Map<String, Object>> leaderboard = new ArrayList<>();

                for (int i = 0; i < arr.size(); i++) {
                    JsonObject user = arr.get(i).getAsJsonObject();
                    Map<String, Object> map = new HashMap<>();
                    map.put("userId", user.get("userId").getAsInt());
                    map.put("username", user.get("username").getAsString());
                    map.put("fullName", user.get("fullName").getAsString());

                    int score = 0;
                    if (user.has("score") && !user.get("score").isJsonNull()) {
                        score = user.get("score").getAsInt();
                    } else if (user.has("totalScore") && !user.get("totalScore").isJsonNull()) {
                        score = user.get("totalScore").getAsInt();
                    }
                    map.put("totalScore", score);

                    map.put("isOnline", user.get("isOnline").getAsBoolean());

                    if (user.has("avatarUrl") && !user.get("avatarUrl").isJsonNull()) {
                        map.put("avatarUrl", user.get("avatarUrl").getAsString());
                    }
                    leaderboard.add(map);
                }

                System.out.println("✅ Leaderboard loaded: " + leaderboard.size() + " users (" + subject + ")");
                callback.accept(leaderboard);

            } catch (Exception e) {
                System.err.println("❌ Error parsing leaderboard: " + e.getMessage());
                e.printStackTrace();
                callback.accept(new ArrayList<>());
            }
        };

        // Send request with subject parameter
        Map<String, Object> request = new HashMap<>();
        request.put("type", Protocol.GET_LEADERBOARD);
        request.put("limit", limit);
        request.put("subject", subject);
        sendJson(request);

        // Timeout handler
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                if (leaderboardCallback != null) {
                    System.err.println("⚠️ Leaderboard request timeout for: " + subject);
                    leaderboardCallback = null;
                    callback.accept(new ArrayList<>());
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, "LeaderboardTimeout-" + subject).start();
    }

    /**
     * Register chat message callback
     */
    public void setChatCallback(Consumer<JsonObject> callback) {
        this.chatCallback = callback;
    }

    /**
     * Clear chat callback
     */
    public void clearChatCallback() {
        this.chatCallback = null;
    }

    /**
     * Register a one-time callback for any message type
     */
    public void setPendingCallback(String messageType, Consumer<JsonObject> callback) {
        pendingRequests.put(messageType, callback);
        System.out.println("✅ Registered callback for: " + messageType);
    }

    /**
     * Remove a pending callback
     */
    public void removePendingCallback(String messageType) {
        pendingRequests.remove(messageType);
        System.out.println("🗑️ Removed callback for: " + messageType);
    }

    /** Disconnect and cleanup */
    public void disconnect() {
        try {
            // Stop listener first
            isListening = false;

            if (socket != null && !socket.isClosed()) {
                Map<String, Object> req = new HashMap<>();
                req.put("type", "LOGOUT");
                req.put("username", currentUsername);
                sendJson(req);

                // Wait a bit for logout message to send
                Thread.sleep(200);

                socket.close();
            }

            // Clear all session data
            clearSessionData();

            connected = false;
            System.out.println("✅ Disconnected from server");

        } catch (Exception e) {
            System.err.println("❌ Error disconnecting: " + e.getMessage());
        }
    }

    /**
     * Clear all session data - IMPORTANT for clean logout
     */
    private void clearSessionData() {
        currentUsername = null;
        currentUserId = 0;
        currentFullName = null;
        currentEmail = null;
        currentAvatarUrl = null;
        totalScore = 0;
        mathScore = 0;
        englishScore = 0;
        scienceScore = 0;
        totalGames = 0;
        wins = 0;
        currentLevel = 0;
        currentUser = null;

        // Clear callbacks
        profileCallback = null;
        leaderboardCallback = null;
        chatCallback = null;
        pendingRequests.clear();

        System.out.println("🧹 Session data cleared");
    }

    public void logoutAndClearSession() {
        try {
            // Gửi logout message cho server
            if (isConnected() && currentUsername != null) {
                Map<String, Object> request = new HashMap<>();
                request.put("type", "LOGOUT");
                request.put("username", currentUsername);
                sendJson(request);
            }

            // Dừng listener
            isListening = false;
            if (listenerThread != null && listenerThread.isAlive()) {
                listenerThread.interrupt();
                listenerThread = null;
            }

            // Ngắt socket
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }

            connected = false;

            // 🧹 Xóa toàn bộ dữ liệu user hiện tại
            currentUserId = 0;
            currentUsername = null;
            currentFullName = null;
            currentEmail = null;
            currentAvatarUrl = null;
            totalScore = 0;
            mathScore = 0;
            englishScore = 0;
            scienceScore = 0;
            totalGames = 0;
            wins = 0;
            currentLevel = 0;

            System.out.println("🧹 Client session cleared completely");

        } catch (Exception e) {
            System.err.println("❌ Error during logout: " + e.getMessage());
        }
    }


    // Getters
    public boolean isConnected() {
        return connected && socket != null && !socket.isClosed();
    }

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
    public User getCurrentUser() { return currentUser; }
}