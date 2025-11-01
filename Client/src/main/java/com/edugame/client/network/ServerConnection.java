package com.edugame.client.network;

import com.edugame.client.model.User;
import com.edugame.common.Protocol;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.scene.control.Alert;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * ServerConnection - Handle all client-server communication
 *
 * Supports 4 types of chat:
 * 1. Global Chat - Chat toàn server
 * 2. Private Chat - Chat 1-1 với bạn bè
 * 3. Room Chat - Chat trong phòng chờ
 * 4. Game Chat - Chat trong game (đang phát triển)
 */
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

    // ✅ 4 CHAT CALLBACKS
    private Consumer<JsonObject> globalChatCallback;           // Chat toàn cầu
//    private Consumer<Map<String, Object>> privateChatCallback; // Chat riêng (real-time)
private Map<Integer, Consumer<Map<String, Object>>> privateChatListeners = new ConcurrentHashMap<>();
    private Consumer<JsonObject> roomChatCallback;             // Chat phòng chờ
    private Consumer<JsonObject> gameChatCallback;             // Chat trong game

    private Map<String, Consumer<JsonObject>> pendingRequests = new ConcurrentHashMap<>();

    // Loading states
    private boolean isLoadingFriends = false;
    private boolean isLoadingRequests = false;
    private boolean isLoadingMessages = false;

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
            case "ERROR":
                handleErrorMessage(json);
                break;
                
            // Profile
            case Protocol.GET_PROFILE:
                if (profileCallback != null) {
                    profileCallback.accept(json);
                    profileCallback = null;
                }
                break;

            case Protocol.UPDATE_PROFILE:
                handleUpdateProfileResponse(json);
                break;

            // Leaderboard
            case Protocol.GET_LEADERBOARD:
                if (leaderboardCallback != null) {
                    leaderboardCallback.accept(json);
                    leaderboardCallback = null;
                }
                break;

            // ============================================================
            // CHAT TYPE 1: GLOBAL CHAT - Chat toàn cầu
            // ============================================================
            case Protocol.GLOBAL_CHAT:
            case "GLOBAL_CHAT_MESSAGE":
                if (globalChatCallback != null) {
                    System.out.println("💬 [GLOBAL CHAT] New message received");
                    globalChatCallback.accept(json);
                }
                break;

            // ============================================================
            // CHAT TYPE 2: PRIVATE CHAT - Chat riêng 1-1
            // ============================================================
            case Protocol.NEW_MESSAGE:
                handleNewPrivateMessage(json);
                break;

            case Protocol.GET_MESSAGES:
            case Protocol.SEND_MESSAGE:
            case Protocol.MESSAGE_READ:
                // Handle via pendingRequests
                Consumer<JsonObject> callback = pendingRequests.remove(type);
                if (callback != null) {
                    callback.accept(json);
                }
                break;

            // ============================================================
            // CHAT TYPE 3: ROOM CHAT - Chat trong phòng chờ
            // ============================================================
            case Protocol.ROOM_CHAT:
            case "ROOM_CHAT_MESSAGE":
                if (roomChatCallback != null) {
                    System.out.println("🏠 [ROOM CHAT] New message in room");
                    roomChatCallback.accept(json);
                }
                break;

            // ============================================================
            // CHAT TYPE 4: GAME CHAT - Chat trong game (đang phát triển)
            // ============================================================
            case Protocol.GAME_CHAT:
            case "GAME_CHAT_MESSAGE":
                if (gameChatCallback != null) {
                    System.out.println("🎮 [GAME CHAT] New message in game");
                    gameChatCallback.accept(json);
                } else {
                    System.out.println("⚠️ [GAME CHAT] Tính năng đang phát triển");
                }
                break;

            default:
                // Check pending requests for other types
                Consumer<JsonObject> cb = pendingRequests.remove(type);
                if (cb != null) {
                    cb.accept(json);
                } else {
                    System.out.println("⚠️ No handler for message type: " + type);
                }
                break;
        }
    }



    private void handleErrorMessage(JsonObject json) {
        try {
            String message = json.has("message") ? json.get("message").getAsString() : "Unknown error";
            System.err.println("❌ [SERVER ERROR] " + message);

            // Hiển thị error cho user nếu cần
            Platform.runLater(() -> {
                // Có thể show alert hoặc log
                System.err.println("❌ Server error: " + message);
            });

        } catch (Exception e) {
            System.err.println("❌ Error handling error message: " + e.getMessage());
        }
    }

    /**
     * Handle update profile response
     */
    private void handleUpdateProfileResponse(JsonObject json) {
        boolean success = json.get("success").getAsBoolean();
        String message = json.get("message").getAsString();

        if (success) {
            System.out.println("✅ Hồ sơ được cập nhật trên server!");

            // Cập nhật thông tin user hiện tại
            if (json.has("fullName"))
                currentFullName = json.get("fullName").getAsString();
            if (json.has("avatarUrl"))
                currentAvatarUrl = json.get("avatarUrl").getAsString();

            // Đồng bộ vào currentUser object
            if (currentUser != null) {
                if (json.has("fullName"))
                    currentUser.setFullName(json.get("fullName").getAsString());
                if (json.has("avatarUrl"))
                    currentUser.setAvatarUrl(json.get("avatarUrl").getAsString());
            }

            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Cập nhật thành công");
                alert.setHeaderText(null);
                alert.setContentText(message);
                alert.showAndWait();
            });
        } else {
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Cập nhật thất bại");
                alert.setHeaderText(null);
                alert.setContentText(message);
                alert.showAndWait();
            });
        }
    }

    /**
     * Handle new private message (real-time)
     */
    private void handleNewPrivateMessage(JsonObject json) {
        try {
            int senderId = json.get("senderId").getAsInt();
            String senderName = json.get("senderName").getAsString();
            String content = json.get("content").getAsString();
            int messageId = json.get("messageId").getAsInt();
            String sentAt = json.get("sentAt").getAsString();

            System.out.println("📨 [PRIVATE CHAT] New message from userId=" + senderId + " (" + senderName + ")");

            // ✅ Tạo message object
            Map<String, Object> message = new HashMap<>();
            message.put("messageId", messageId);
            message.put("senderId", senderId);
            message.put("senderName", senderName);
            message.put("content", content);
            message.put("sentAt", sentAt);

            // ✅ Tìm listener tương ứng với senderId (người gửi)
            Consumer<Map<String, Object>> listener = privateChatListeners.get(senderId);

            if (listener != null) {
                System.out.println("✅ [PRIVATE CHAT] Calling listener for friendId=" + senderId);
                listener.accept(message);
            } else {
                System.out.println("⚠️ [PRIVATE CHAT] No listener for friendId=" + senderId + " (chat window not open)");
            }

        } catch (Exception e) {
            System.err.println("❌ Error handling new private message: " + e.getMessage());
            e.printStackTrace();
        }
    }


    /**
     * ✅ Clear tất cả listeners
     */
    public void clearAllPrivateChatListeners() {
        privateChatListeners.clear();
        System.out.println("🗑️ Cleared all private chat listeners");
    }

    // ================================================================
    // AUTHENTICATION
    // ================================================================

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

    public void setCurrentAvatarUrl(String avatarUrl) {
        this.currentAvatarUrl = avatarUrl;
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

    // ================================================================
    // CHAT TYPE 1: GLOBAL CHAT - Chat toàn cầu
    // ================================================================

    /**
     * Set callback for global chat messages
     */
    public void setGlobalChatCallback(Consumer<JsonObject> callback) {
        this.globalChatCallback = callback;
        System.out.println("✅ Global chat callback registered");
    }

    /**
     * Clear global chat callback
     */
    public void clearGlobalChatCallback() {
        this.globalChatCallback = null;
        System.out.println("🗑️ Global chat callback cleared");
    }

    /**
     * Send global chat message
     */
    public void sendGlobalChatMessage(String message) {
        if (!isConnected()) {
            System.err.println("❌ Cannot send global chat - not connected");
            return;
        }

        Map<String, Object> request = new HashMap<>();
        request.put("type", Protocol.GLOBAL_CHAT);
        request.put("username", currentUsername);
        request.put("message", message);
        sendJson(request);

        System.out.println("💬 [GLOBAL CHAT] Message sent: " + message);
    }

    // ================================================================
    // CHAT TYPE 2: PRIVATE CHAT - Chat riêng 1-1
    // ================================================================



    public void addPrivateChatListener(int friendId, Consumer<Map<String, Object>> callback) {
        privateChatListeners.put(friendId, callback);
        System.out.println("✅ Added private chat listener for friendId=" + friendId);
    }
    /**
     * ✅ Remove listener khi đóng chat window
     */
    public void removePrivateChatListener(int friendId) {
        privateChatListeners.remove(friendId);
        System.out.println("🗑️ Removed private chat listener for friendId=" + friendId);
    }

    /**
     * Get chat messages with a friend
     */
    public void getMessages(int friendId, int limit, Consumer<List<Map<String, Object>>> callback) {
        if (!isConnected()) {
            System.err.println("❌ Cannot get messages - not connected");
            callback.accept(new ArrayList<>());
            return;
        }

        if (isLoadingMessages) {
            System.out.println("⏭️ Already loading messages, skipping");
            return;
        }
        isLoadingMessages = true;

        System.out.println("💬 [PRIVATE CHAT] Getting messages from friendId=" + friendId);

        removePendingCallback(Protocol.GET_MESSAGES);
        final boolean[] callbackCalled = new boolean[]{false};

        setPendingCallback(Protocol.GET_MESSAGES, (json) -> {
            try {
                synchronized (callbackCalled) {
                    if (callbackCalled[0]) return;
                    callbackCalled[0] = true;
                }

                removePendingCallback(Protocol.GET_MESSAGES);

                boolean success = json.get("success").getAsBoolean();
                if (!success) {
                    callback.accept(new ArrayList<>());
                    return;
                }

                JsonArray arr = json.getAsJsonArray("messages");
                List<Map<String, Object>> messages = new ArrayList<>();

                for (int i = 0; i < arr.size(); i++) {
                    JsonObject msgObj = arr.get(i).getAsJsonObject();
                    Map<String, Object> message = new HashMap<>();

                    message.put("messageId", msgObj.get("messageId").getAsInt());
                    message.put("senderId", msgObj.get("senderId").getAsInt());
                    message.put("receiverId", msgObj.get("receiverId").getAsInt());
                    message.put("content", msgObj.get("content").getAsString());
                    message.put("sentAt", msgObj.get("sentAt").getAsString());
                    message.put("isRead", msgObj.get("isRead").getAsBoolean());

                    if (msgObj.has("senderName")) {
                        message.put("senderName", msgObj.get("senderName").getAsString());
                    }
                    if (msgObj.has("senderAvatar")) {
                        message.put("senderAvatar", msgObj.get("senderAvatar").getAsString());
                    }

                    messages.add(message);
                }

                System.out.println("✅ [PRIVATE CHAT] Loaded " + messages.size() + " messages");
                callback.accept(messages);

            } catch (Exception e) {
                System.err.println("❌ Error parsing messages: " + e.getMessage());
                e.printStackTrace();
                synchronized (callbackCalled) {
                    if (!callbackCalled[0]) {
                        callbackCalled[0] = true;
                        callback.accept(new ArrayList<>());
                    }
                }
            } finally {
                isLoadingMessages = false;
            }
        });

        Map<String, Object> request = new HashMap<>();
        request.put("type", Protocol.GET_MESSAGES);
        request.put("friendId", friendId);
        request.put("limit", limit);
        sendJson(request);

        // Timeout
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                synchronized (callbackCalled) {
                    if (!callbackCalled[0]) {
                        callbackCalled[0] = true;
                        removePendingCallback(Protocol.GET_MESSAGES);
                        System.err.println("⚠️ Get messages timeout");
                        callback.accept(new ArrayList<>());
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                isLoadingMessages = false;
            }
        }, "GetMessagesTimeout").start();
    }

    /**
     * Send message to friend
     */
    public void sendMessage(int friendId, String content, Consumer<Boolean> callback) {
        if (!isConnected()) {
            callback.accept(false);
            return;
        }

        System.out.println("💬 [PRIVATE CHAT] Sending message to friendId=" + friendId);

        removePendingCallback(Protocol.SEND_MESSAGE);
        final boolean[] callbackCalled = new boolean[]{false};

        setPendingCallback(Protocol.SEND_MESSAGE, (json) -> {
            try {
                synchronized (callbackCalled) {
                    if (callbackCalled[0]) return;
                    callbackCalled[0] = true;
                }

                removePendingCallback(Protocol.SEND_MESSAGE);

                boolean success = json.get("success").getAsBoolean();
                System.out.println("✅ [PRIVATE CHAT] Message " + (success ? "sent" : "failed"));
                callback.accept(success);

            } catch (Exception e) {
                System.err.println("❌ Error: " + e.getMessage());
                synchronized (callbackCalled) {
                    if (!callbackCalled[0]) {
                        callbackCalled[0] = true;
                        callback.accept(false);
                    }
                }
            }
        });

        Map<String, Object> request = new HashMap<>();
        request.put("type", Protocol.SEND_MESSAGE);
        request.put("receiverId", friendId);
        request.put("content", content);
        sendJson(request);

        // Timeout
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                synchronized (callbackCalled) {
                    if (!callbackCalled[0]) {
                        callbackCalled[0] = true;
                        removePendingCallback(Protocol.SEND_MESSAGE);
                        callback.accept(false);
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, "SendMessageTimeout").start();
    }

    /**
     * Mark messages as read
     */
    public void markMessagesAsRead(int friendId) {
        if (!isConnected()) return;

        Map<String, Object> request = new HashMap<>();
        request.put("type", Protocol.MESSAGE_READ);
        request.put("senderId", friendId);
        sendJson(request);

        System.out.println("✅ [PRIVATE CHAT] Marked messages as read from friendId=" + friendId);
    }

    // ================================================================
    // CHAT TYPE 3: ROOM CHAT - Chat trong phòng chờ
    // ================================================================

    /**
     * Set callback for room chat messages
     */
    public void setRoomChatCallback(Consumer<JsonObject> callback) {
        this.roomChatCallback = callback;
        System.out.println("✅ Room chat callback registered");
    }

    /**
     * Clear room chat callback
     */
    public void clearRoomChatCallback() {
        this.roomChatCallback = null;
        System.out.println("🗑️ Room chat callback cleared");
    }

    /**
     * Send room chat message
     */
    public void sendRoomChatMessage(int roomId, String message) {
        if (!isConnected()) {
            System.err.println("❌ Cannot send room chat - not connected");
            return;
        }

        Map<String, Object> request = new HashMap<>();
        request.put("type", Protocol.ROOM_CHAT);
        request.put("roomId", roomId);
        request.put("username", currentUsername);
        request.put("message", message);
        sendJson(request);

        System.out.println("🏠 [ROOM CHAT] Message sent in room " + roomId + ": " + message);
    }

    // ================================================================
    // CHAT TYPE 4: GAME CHAT - Chat trong game (đang phát triển)
    // ================================================================

    /**
     * Set callback for game chat messages
     */
    public void setGameChatCallback(Consumer<JsonObject> callback) {
        this.gameChatCallback = callback;
        System.out.println("✅ Game chat callback registered");
    }

    /**
     * Clear game chat callback
     */
    public void clearGameChatCallback() {
        this.gameChatCallback = null;
        System.out.println("🗑️ Game chat callback cleared");
    }

    /**
     * Send game chat message (đang phát triển)
     */
    public void sendGameChatMessage(int gameId, String message) {
        System.out.println("⚠️ [GAME CHAT] Tính năng đang phát triển");
        System.out.println("🎮 [GAME CHAT] gameId=" + gameId + ", message=" + message);

        // TODO: Implement when game chat is ready
        /*
        if (!isConnected()) {
            System.err.println("❌ Cannot send game chat - not connected");
            return;
        }

        Map<String, Object> request = new HashMap<>();
        request.put("type", Protocol.GAME_CHAT);
        request.put("gameId", gameId);
        request.put("username", currentUsername);
        request.put("message", message);
        sendJson(request);

        System.out.println("🎮 [GAME CHAT] Message sent in game " + gameId + ": " + message);
        */
    }

    // ================================================================
    // UTILITIES
    // ================================================================

    /** Send JSON with error handling */
    public void sendJson(Map<String, Object> data) {
        if (!isConnected()) {
            System.err.println("❌ Cannot send - not connected");
            return;
        }

        if (writer != null && !writer.checkError()) {
            String json = gson.toJson(data);
            System.out.println("📤 Sending: " + data.get("type"));
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

        Map<String, Object> req = new HashMap<>();
        req.put("type", Protocol.GET_PROFILE);
        sendJson(req);

        // Timeout
        new Thread(() -> {
            try {
                Thread.sleep(5000);
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
     * Get leaderboard with callback
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

        Map<String, Object> request = new HashMap<>();
        request.put("type", Protocol.GET_LEADERBOARD);
        request.put("limit", limit);
        request.put("subject", subject);
        sendJson(request);

        // Timeout
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

    // ================================================================
    // FRIENDS MANAGEMENT (GIỮ NGUYÊN CODE CŨ)
    // ================================================================

    /**
     * Tìm kiếm người dùng
     */
    public void searchUsers(String query, Consumer<List<Map<String, Object>>> callback) {
        if (!isConnected()) {
            System.err.println("❌ Cannot search users - not connected");
            callback.accept(new ArrayList<>());
            return;
        }

        System.out.println("🔍 [CLIENT] Searching users: " + query);

        removePendingCallback(Protocol.SEARCH_USERS);
        final boolean[] callbackCalled = new boolean[]{false};

        setPendingCallback(Protocol.SEARCH_USERS, (json) -> {
            System.out.println("🔔 [CLIENT] SEARCH_USERS callback triggered");

            try {
                synchronized (callbackCalled) {
                    if (callbackCalled[0]) {
                        System.err.println("⚠️ [CLIENT] Callback already called, ignoring");
                        return;
                    }
                    callbackCalled[0] = true;
                }

                removePendingCallback(Protocol.SEARCH_USERS);

                boolean success = json.get("success").getAsBoolean();
                if (!success) {
                    String message = json.get("message").getAsString();
                    System.err.println("❌ [CLIENT] Search failed: " + message);

                    Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.WARNING);
                        alert.setTitle("Tìm kiếm");
                        alert.setHeaderText(null);
                        alert.setContentText(message);
                        alert.showAndWait();
                    });

                    callback.accept(new ArrayList<>());
                    return;
                }

                JsonArray arr = json.getAsJsonArray("users");
                List<Map<String, Object>> users = new ArrayList<>();

                for (int i = 0; i < arr.size(); i++) {
                    JsonObject userObj = arr.get(i).getAsJsonObject();
                    Map<String, Object> user = new HashMap<>();

                    user.put("userId", userObj.get("userId").getAsInt());
                    user.put("username", userObj.get("username").getAsString());
                    user.put("fullName", userObj.get("fullName").getAsString());

                    if (userObj.has("email") && !userObj.get("email").isJsonNull()) {
                        user.put("email", userObj.get("email").getAsString());
                    }

                    if (userObj.has("age") && !userObj.get("age").isJsonNull()) {
                        user.put("age", userObj.get("age").getAsString());
                    }

                    if (userObj.has("avatarUrl") && !userObj.get("avatarUrl").isJsonNull()) {
                        user.put("avatarUrl", userObj.get("avatarUrl").getAsString());
                    }

                    user.put("totalScore", userObj.get("totalScore").getAsInt());
                    user.put("isOnline", userObj.get("isOnline").getAsBoolean());

                    if (userObj.has("friendshipStatus")) {
                        user.put("friendshipStatus", userObj.get("friendshipStatus").getAsString());
                    } else {
                        user.put("friendshipStatus", "none");
                    }

                    users.add(user);
                }

                System.out.println("✅ [CLIENT] Found " + users.size() + " users");
                callback.accept(users);

            } catch (Exception e) {
                System.err.println("❌ [CLIENT] Error parsing search results: " + e.getMessage());
                e.printStackTrace();

                synchronized (callbackCalled) {
                    if (!callbackCalled[0]) {
                        callbackCalled[0] = true;
                        callback.accept(new ArrayList<>());
                    }
                }
            }
        });

        Map<String, Object> request = new HashMap<>();
        request.put("type", Protocol.SEARCH_USERS);
        request.put("query", query);
        request.put("limit", 50);
        sendJson(request);

        new Thread(() -> {
            try {
                Thread.sleep(5000);

                synchronized (callbackCalled) {
                    if (!callbackCalled[0]) {
                        callbackCalled[0] = true;
                        removePendingCallback(Protocol.SEARCH_USERS);
                        System.err.println("⚠️ [CLIENT] Search users timeout");
                        callback.accept(new ArrayList<>());
                    } else {
                        System.out.println("✅ [CLIENT] Timeout thread: Search already completed");
                    }
                }

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, "SearchUsersTimeout").start();
    }

    /**
     * Gửi yêu cầu kết bạn
     */
    public void sendFriendRequest(int targetUserId, Consumer<Boolean> callback) {
        if (!isConnected()) {
            System.err.println("❌ Cannot send friend request - not connected");
            callback.accept(false);
            return;
        }

        System.out.println("🤝 [CLIENT] Sending friend request to userId=" + targetUserId);

        removePendingCallback(Protocol.ADD_FRIEND);
        final boolean[] callbackCalled = new boolean[]{false};

        setPendingCallback(Protocol.ADD_FRIEND, (json) -> {
            System.out.println("🔔 [CLIENT] ADD_FRIEND callback triggered");

            try {
                synchronized (callbackCalled) {
                    if (callbackCalled[0]) {
                        System.err.println("⚠️ [CLIENT] Callback already called, ignoring");
                        return;
                    }
                    callbackCalled[0] = true;
                }

                removePendingCallback(Protocol.ADD_FRIEND);

                boolean success = json.get("success").getAsBoolean();
                String message = json.get("message").getAsString();

                System.out.println("📥 [CLIENT] Send friend request result: " + success + ", message: " + message);

                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Kết bạn");
                    alert.setHeaderText(null);
                    alert.setContentText(message);
                    alert.showAndWait();
                });

                callback.accept(success);

            } catch (Exception e) {
                System.err.println("❌ [CLIENT] Error handling ADD_FRIEND response: " + e.getMessage());
                e.printStackTrace();

                synchronized (callbackCalled) {
                    if (!callbackCalled[0]) {
                        callbackCalled[0] = true;
                        callback.accept(false);
                    }
                }
            }
        });

        Map<String, Object> request = new HashMap<>();
        request.put("type", Protocol.ADD_FRIEND);
        request.put("targetUserId", targetUserId);
        sendJson(request);

        new Thread(() -> {
            try {
                Thread.sleep(5000);

                synchronized (callbackCalled) {
                    if (!callbackCalled[0]) {
                        callbackCalled[0] = true;
                        removePendingCallback(Protocol.ADD_FRIEND);
                        System.err.println("⚠️ [CLIENT] Add friend timeout");
                        callback.accept(false);
                    } else {
                        System.out.println("✅ [CLIENT] Timeout thread: Add friend already processed");
                    }
                }

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, "AddFriendTimeout").start();
    }

    /**
     * Chấp nhận lời mời kết bạn
     */
    public void acceptFriendRequest(int friendId, Consumer<Boolean> callback) {
        if (!isConnected()) {
            System.err.println("❌ Cannot accept friend request - not connected");
            callback.accept(false);
            return;
        }

        System.out.println("✅ [CLIENT] Accepting friend request from userId: " + friendId);

        removePendingCallback(Protocol.ACCEPT_FRIEND);
        final boolean[] callbackCalled = new boolean[]{false};

        setPendingCallback(Protocol.ACCEPT_FRIEND, (json) -> {
            System.out.println("🔔 [CLIENT] ACCEPT_FRIEND callback triggered");

            try {
                synchronized (callbackCalled) {
                    if (callbackCalled[0]) {
                        System.err.println("⚠️ [CLIENT] Callback already called, ignoring");
                        return;
                    }
                    callbackCalled[0] = true;
                }

                removePendingCallback(Protocol.ACCEPT_FRIEND);

                boolean success = json.get("success").getAsBoolean();
                System.out.println("📥 [CLIENT] Accept friend result: " + success);

                callback.accept(success);

            } catch (Exception e) {
                System.err.println("❌ [CLIENT] Error parsing accept response: " + e.getMessage());
                e.printStackTrace();

                synchronized (callbackCalled) {
                    if (!callbackCalled[0]) {
                        callbackCalled[0] = true;
                        callback.accept(false);
                    }
                }
            }
        });

        Map<String, Object> request = new HashMap<>();
        request.put("type", Protocol.ACCEPT_FRIEND);
        request.put("friendId", friendId);
        sendJson(request);

        new Thread(() -> {
            try {
                Thread.sleep(5000);

                synchronized (callbackCalled) {
                    if (!callbackCalled[0]) {
                        callbackCalled[0] = true;
                        removePendingCallback(Protocol.ACCEPT_FRIEND);
                        System.err.println("⚠️ [CLIENT] Accept friend request timeout");
                        callback.accept(false);
                    } else {
                        System.out.println("✅ [CLIENT] Timeout thread: Accept already processed");
                    }
                }

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, "AcceptFriendTimeout").start();
    }

    /**
     * Từ chối lời mời kết bạn
     */
    public void rejectFriendRequest(int friendId, Consumer<Boolean> callback) {
        if (!isConnected()) {
            System.err.println("❌ Cannot reject friend request - not connected");
            callback.accept(false);
            return;
        }

        System.out.println("❌ [CLIENT] Rejecting friend request from userId: " + friendId);

        removePendingCallback(Protocol.REJECT_FRIEND);
        final boolean[] callbackCalled = new boolean[]{false};

        setPendingCallback(Protocol.REJECT_FRIEND, (json) -> {
            System.out.println("🔔 [CLIENT] REJECT_FRIEND callback triggered");

            try {
                synchronized (callbackCalled) {
                    if (callbackCalled[0]) {
                        System.err.println("⚠️ [CLIENT] Callback already called, ignoring");
                        return;
                    }
                    callbackCalled[0] = true;
                }

                removePendingCallback(Protocol.REJECT_FRIEND);

                boolean success = json.get("success").getAsBoolean();
                System.out.println("📥 [CLIENT] Reject friend result: " + success);

                callback.accept(success);

            } catch (Exception e) {
                System.err.println("❌ [CLIENT] Error parsing reject response: " + e.getMessage());
                e.printStackTrace();

                synchronized (callbackCalled) {
                    if (!callbackCalled[0]) {
                        callbackCalled[0] = true;
                        callback.accept(false);
                    }
                }
            }
        });

        Map<String, Object> request = new HashMap<>();
        request.put("type", Protocol.REJECT_FRIEND);
        request.put("friendId", friendId);
        sendJson(request);

        new Thread(() -> {
            try {
                Thread.sleep(5000);

                synchronized (callbackCalled) {
                    if (!callbackCalled[0]) {
                        callbackCalled[0] = true;
                        removePendingCallback(Protocol.REJECT_FRIEND);
                        System.err.println("⚠️ [CLIENT] Reject friend request timeout");
                        callback.accept(false);
                    } else {
                        System.out.println("✅ [CLIENT] Timeout thread: Reject already processed");
                    }
                }

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, "RejectFriendTimeout").start();
    }

    /**
     * Xóa bạn bè
     */
    public void removeFriend(int friendId, Consumer<Boolean> callback) {
        if (!isConnected()) {
            System.err.println("❌ Cannot remove friend - not connected");
            callback.accept(false);
            return;
        }

        System.out.println("🗑️ [CLIENT] Removing friend userId=" + friendId);

        removePendingCallback(Protocol.REMOVE_FRIEND);
        final boolean[] callbackCalled = new boolean[]{false};

        setPendingCallback(Protocol.REMOVE_FRIEND, (json) -> {
            System.out.println("🔔 [CLIENT] REMOVE_FRIEND callback triggered");

            try {
                synchronized (callbackCalled) {
                    if (callbackCalled[0]) {
                        System.err.println("⚠️ [CLIENT] Callback already called, ignoring");
                        return;
                    }
                    callbackCalled[0] = true;
                }

                removePendingCallback(Protocol.REMOVE_FRIEND);

                boolean success = json.get("success").getAsBoolean();
                String message = json.get("message").getAsString();

                System.out.println("📥 [CLIENT] Remove friend result: " + success + ", message: " + message);

                Platform.runLater(() -> {
                    Alert alert = new Alert(Alert.AlertType.INFORMATION);
                    alert.setTitle("Xóa bạn bè");
                    alert.setHeaderText(null);
                    alert.setContentText(message);
                    alert.showAndWait();
                });

                callback.accept(success);

            } catch (Exception e) {
                System.err.println("❌ [CLIENT] Error handling REMOVE_FRIEND response: " + e.getMessage());
                e.printStackTrace();

                synchronized (callbackCalled) {
                    if (!callbackCalled[0]) {
                        callbackCalled[0] = true;
                        callback.accept(false);
                    }
                }
            }
        });

        Map<String, Object> request = new HashMap<>();
        request.put("type", Protocol.REMOVE_FRIEND);
        request.put("friendId", friendId);
        sendJson(request);

        new Thread(() -> {
            try {
                Thread.sleep(5000);

                synchronized (callbackCalled) {
                    if (!callbackCalled[0]) {
                        callbackCalled[0] = true;
                        removePendingCallback(Protocol.REMOVE_FRIEND);
                        System.err.println("⚠️ [CLIENT] Remove friend timeout");
                        callback.accept(false);
                    } else {
                        System.out.println("✅ [CLIENT] Timeout thread: Remove friend already processed");
                    }
                }

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, "RemoveFriendTimeout").start();
    }

    /**
     * Lấy danh sách bạn bè
     */
    public void getFriendsList(Consumer<List<Map<String, Object>>> callback) {
        if (!isConnected()) {
            System.err.println("❌ Cannot get friends list - not connected");
            callback.accept(new ArrayList<>());
            return;
        }

        if (isLoadingFriends) {
            System.out.println("⏭️ [CLIENT] Already loading friends, skipping");
            return;
        }
        isLoadingFriends = true;
        System.out.println("🔒 [CLIENT] isLoadingFriends set to TRUE");

        System.out.println("👥 [CLIENT] ========== GET FRIENDS LIST START ==========");

        removePendingCallback(Protocol.GET_FRIENDS_LIST);
        System.out.println("👥 [CLIENT] Removed old callback");

        final boolean[] callbackCalled = new boolean[]{false};
        System.out.println("👥 [CLIENT] Created flag: " + callbackCalled[0]);

        setPendingCallback(Protocol.GET_FRIENDS_LIST, (json) -> {
            System.out.println("🔔🔔🔔 [CLIENT] ===== CALLBACK TRIGGERED ===== 🔔🔔🔔");
            System.out.println("🔔 [CLIENT] JSON: " + json.toString());
            System.out.println("🔔 [CLIENT] Flag before sync: " + callbackCalled[0]);

            try {
                synchronized (callbackCalled) {
                    System.out.println("🔔 [CLIENT] Inside synchronized block");
                    System.out.println("🔔 [CLIENT] Flag value: " + callbackCalled[0]);

                    if (callbackCalled[0]) {
                        System.err.println("⚠️ [CLIENT] Callback already called, RETURNING");
                        return;
                    }

                    callbackCalled[0] = true;
                    System.out.println("✅ [CLIENT] Flag set to TRUE");
                }

                removePendingCallback(Protocol.GET_FRIENDS_LIST);
                System.out.println("✅ [CLIENT] Removed pending callback");

                boolean success = json.get("success").getAsBoolean();
                System.out.println("📥 [CLIENT] Success: " + success);

                if (!success) {
                    System.err.println("❌ [CLIENT] Get friends list failed");
                    callback.accept(new ArrayList<>());
                    return;
                }

                JsonArray arr = json.getAsJsonArray("friends");
                System.out.println("📥 [CLIENT] Friends array size: " + arr.size());

                List<Map<String, Object>> friends = new ArrayList<>();

                for (int i = 0; i < arr.size(); i++) {
                    JsonObject friendObj = arr.get(i).getAsJsonObject();
                    Map<String, Object> friend = new HashMap<>();

                    friend.put("userId", friendObj.get("userId").getAsInt());
                    friend.put("username", friendObj.get("username").getAsString());
                    friend.put("fullName", friendObj.get("fullName").getAsString());

                    if (friendObj.has("avatarUrl") && !friendObj.get("avatarUrl").isJsonNull()) {
                        friend.put("avatarUrl", friendObj.get("avatarUrl").getAsString());
                    }

                    friend.put("totalScore", friendObj.get("totalScore").getAsInt());
                    friend.put("isOnline", friendObj.get("isOnline").getAsBoolean());

                    friends.add(friend);
                    System.out.println("  ✅ [CLIENT] Parsed friend #" + (i+1) + ": " + friend.get("fullName"));
                }

                System.out.println("✅ [CLIENT] Total friends parsed: " + friends.size());
                System.out.println("✅ [CLIENT] Calling callback with " + friends.size() + " friends");

                callback.accept(friends);

                System.out.println("✅ [CLIENT] ===== CALLBACK COMPLETED ===== ✅");

            } catch (Exception e) {
                System.err.println("❌❌❌ [CLIENT] EXCEPTION IN CALLBACK ❌❌❌");
                System.err.println("❌ [CLIENT] Exception: " + e.getMessage());
                e.printStackTrace();

                synchronized (callbackCalled) {
                    System.out.println("❌ [CLIENT] In exception handler, flag: " + callbackCalled[0]);
                    if (!callbackCalled[0]) {
                        callbackCalled[0] = true;
                        System.out.println("❌ [CLIENT] Set flag to true in exception");
                        callback.accept(new ArrayList<>());
                    } else {
                        System.out.println("❌ [CLIENT] Flag already true in exception");
                    }
                }
            } finally {
                isLoadingFriends = false;
                System.out.println("🔓 [CLIENT] isLoadingFriends reset to FALSE (in callback finally)");
            }
        });

        System.out.println("👥 [CLIENT] Callback registered");

        Map<String, Object> request = new HashMap<>();
        request.put("type", Protocol.GET_FRIENDS_LIST);
        sendJson(request);

        System.out.println("👥 [CLIENT] Request sent");

        new Thread(() -> {
            try {
                System.out.println("⏱️ [TIMEOUT] Timeout thread started");
                Thread.sleep(5000);

                System.out.println("⏱️ [TIMEOUT] 5 seconds passed");

                synchronized (callbackCalled) {
                    System.out.println("⏱️ [TIMEOUT] In synchronized block");
                    System.out.println("⏱️ [TIMEOUT] Flag value: " + callbackCalled[0]);

                    if (!callbackCalled[0]) {
                        System.err.println("⚠️⚠️⚠️ [TIMEOUT] FLAG IS FALSE - CALLING TIMEOUT CALLBACK ⚠️⚠️⚠️");
                        callbackCalled[0] = true;
                        removePendingCallback(Protocol.GET_FRIENDS_LIST);
                        System.err.println("⚠️ [CLIENT] Get friends list timeout");
                        callback.accept(new ArrayList<>());
                    } else {
                        System.out.println("✅ [TIMEOUT] Flag is true, skipping timeout");
                    }
                }

            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                isLoadingFriends = false;
                System.out.println("🔓 [CLIENT] isLoadingFriends reset to FALSE (in timeout finally)");
            }
        }, "GetFriendsListTimeout").start();

        System.out.println("👥 [CLIENT] Timeout thread started");
        System.out.println("👥 [CLIENT] ========== GET FRIENDS LIST END ==========");
    }

    /**
     * Lấy danh sách lời mời kết bạn đang chờ
     */
    public void getPendingRequests(Consumer<List<Map<String, Object>>> callback) {
        if (!isConnected()) {
            System.err.println("❌ Cannot get pending requests - not connected");
            callback.accept(new ArrayList<>());
            return;
        }

        if (isLoadingRequests) {
            System.out.println("⏭️ Already loading requests, skipping");
            return;
        }
        isLoadingRequests = true;

        System.out.println("📬 [CLIENT] Getting pending requests...");

        removePendingCallback(Protocol.GET_PENDING_REQUESTS);
        final boolean[] callbackCalled = new boolean[]{false};

        setPendingCallback(Protocol.GET_PENDING_REQUESTS, (json) -> {
            System.out.println("🔔 [CLIENT] GET_PENDING_REQUESTS callback triggered");

            try {
                synchronized (callbackCalled) {
                    if (callbackCalled[0]) {
                        System.err.println("⚠️ [CLIENT] Callback already called, ignoring duplicate");
                        return;
                    }
                    callbackCalled[0] = true;
                }

                removePendingCallback(Protocol.GET_PENDING_REQUESTS);

                boolean success = json.get("success").getAsBoolean();
                if (!success) {
                    System.err.println("❌ [CLIENT] Get pending requests failed");
                    callback.accept(new ArrayList<>());
                    return;
                }

                JsonArray arr = json.getAsJsonArray("requests");
                List<Map<String, Object>> requests = new ArrayList<>();

                for (int i = 0; i < arr.size(); i++) {
                    JsonObject reqObj = arr.get(i).getAsJsonObject();
                    Map<String, Object> request = new HashMap<>();

                    request.put("friendshipId", reqObj.get("friendshipId").getAsInt());
                    request.put("userId", reqObj.get("userId").getAsInt());
                    request.put("username", reqObj.get("username").getAsString());
                    request.put("fullName", reqObj.get("fullName").getAsString());

                    if (reqObj.has("avatarUrl") && !reqObj.get("avatarUrl").isJsonNull()) {
                        request.put("avatarUrl", reqObj.get("avatarUrl").getAsString());
                    }

                    request.put("totalScore", reqObj.get("totalScore").getAsInt());
                    request.put("isOnline", reqObj.get("isOnline").getAsBoolean());
                    request.put("createdAt", reqObj.get("createdAt").getAsString());

                    requests.add(request);
                }

                System.out.println("✅ [CLIENT] Found " + requests.size() + " pending requests");
                callback.accept(requests);

            } catch (Exception e) {
                System.err.println("❌ [CLIENT] Error parsing pending requests: " + e.getMessage());
                e.printStackTrace();

                synchronized (callbackCalled) {
                    if (!callbackCalled[0]) {
                        callbackCalled[0] = true;
                        callback.accept(new ArrayList<>());
                    }
                }
            } finally {
                isLoadingRequests = false;
            }
        });

        Map<String, Object> request = new HashMap<>();
        request.put("type", Protocol.GET_PENDING_REQUESTS);
        sendJson(request);

        new Thread(() -> {
            try {
                Thread.sleep(5000);

                synchronized (callbackCalled) {
                    if (!callbackCalled[0]) {
                        callbackCalled[0] = true;
                        removePendingCallback(Protocol.GET_PENDING_REQUESTS);
                        System.err.println("⚠️ [CLIENT] Get pending requests timeout");
                        callback.accept(new ArrayList<>());
                    } else {
                        System.out.println("✅ [CLIENT] Timeout thread: Callback already called, skipping timeout");
                    }
                }

            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                isLoadingRequests = false;
            }
        }, "GetPendingRequestsTimeout").start();
    }

    // ================================================================
    // DISCONNECT AND CLEANUP
    // ================================================================

    /** Disconnect and cleanup */
    public void disconnect() {
        try {
            isListening = false;

            if (socket != null && !socket.isClosed()) {
                Map<String, Object> req = new HashMap<>();
                req.put("type", "LOGOUT");
                req.put("username", currentUsername);
                sendJson(req);

                Thread.sleep(200);

                socket.close();
            }

            clearSessionData();

            connected = false;
            System.out.println("✅ Disconnected from server");

        } catch (Exception e) {
            System.err.println("❌ Error disconnecting: " + e.getMessage());
        }
    }

    /**
     * Clear all session data
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

        // Clear ALL callbacks
        profileCallback = null;
        leaderboardCallback = null;
        globalChatCallback = null;
        clearAllPrivateChatListeners();
//        privateChatCallback = null;
        roomChatCallback = null;
        gameChatCallback = null;
        pendingRequests.clear();

        System.out.println("🧹 Session data cleared");
    }

    public void logoutAndClearSession() {
        try {
            if (isConnected() && currentUsername != null) {
                Map<String, Object> request = new HashMap<>();
                request.put("type", "LOGOUT");
                request.put("username", currentUsername);
                sendJson(request);
            }

            isListening = false;
            if (listenerThread != null && listenerThread.isAlive()) {
                listenerThread.interrupt();
                listenerThread = null;
            }

            if (socket != null && !socket.isClosed()) {
                socket.close();
            }

            connected = false;
            clearAllPrivateChatListeners();

            // Clear all data
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

    // ================================================================
    // GETTERS
    // ================================================================

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