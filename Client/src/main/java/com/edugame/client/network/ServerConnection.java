package com.edugame.client.network;

import com.edugame.client.controller.RoomController;
import com.edugame.client.model.User;
import com.edugame.client.util.SceneManager;
import com.edugame.common.Protocol;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import javafx.application.Platform;
import javafx.scene.control.Alert;

import java.io.*;
import java.lang.reflect.Type;
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
    private Consumer<Map<String, Object>> serverMessageCallback;
    private boolean isLoadingServerMessages = false;


    private User currentUser;

    //Heartbeat fields
    private String sessionToken;
    private Thread heartbeatThread;
    private volatile boolean isHeartbeatRunning = false;
    private long lastHeartbeatTime = 0;
    private int missedHeartbeats = 0;
    private static final int MAX_MISSED_HEARTBEATS = 3;

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
    private Map<String, Consumer<JsonObject>> messageHandlers = new ConcurrentHashMap<>();
    private String selectedSubject;
    private String selectedDifficulty;
    private int selectedCountPlayer;
    private Consumer<Map<String, Object>> playerJoinedCallback;
    private JoinRoomCallback joinRoomCallback;

    private String currentRoomId;
    private JsonObject opponentInfo;

    private Consumer<Map<String, Object>> playerLeftCallback;
    private Consumer<Map<String, Object>> playerReadyCallback;
    private Consumer<Map<String, Object>> kickPlayerCallback;
    private Consumer<Map<String, Object>> questionResultCallback;

    private Consumer<Map<String, Object>> playerAnsweredCallback;
    private Consumer<Map<String, Object>> playerProgressCallback;
    /**
     * Set callback for new server messages (real-time)
     */
    public void setServerMessageCallback(Consumer<Map<String, Object>> callback) {
        this.serverMessageCallback = callback;
        System.out.println("✅ Server message callback registered");
    }

    /**
     * Clear server message callback
     */
    public void clearServerMessageCallback() {
        this.serverMessageCallback = null;
        System.out.println("🗑️ Server message callback cleared");
    }

    public void setQuestionResultCallback(Consumer<Map<String, Object>> callback) {
        this.questionResultCallback = callback;
    }

    public void setOpponentInfo(JsonObject opponent) {
        this.opponentInfo = opponent;
    }

    public JsonObject getOpponentInfo() {
        return this.opponentInfo;
    }

    @FunctionalInterface
    public interface JoinRoomCallback {
        void onResult(boolean success, String message, Map<String, Object> roomData);
    }

    public void setJoinRoomCallback(JoinRoomCallback callback) {
        this.joinRoomCallback = callback;
    }

    public void setPlayerJoinedCallback(Consumer<Map<String, Object>> callback) {
        this.playerJoinedCallback = callback;
    }

    private Consumer<JsonObject> matchFoundCallback;
    private Consumer<JsonObject> findMatchResponseCallback;

    public void setCurrentRoomId(String roomId) {
        this.currentRoomId = roomId;
        System.out.println("📌 Current room set: " + roomId);
    }

    public String getCurrentRoomId() {
        return currentRoomId;
    }


    public void setPlayerLeftCallback(Consumer<Map<String, Object>> callback) {
        this.playerLeftCallback = callback;
    }

    public void setPlayerReadyCallback(Consumer<Map<String, Object>> callback) {
        this.playerReadyCallback = callback;
    }

    public void clearPlayerJoinedCallback() {
        this.playerJoinedCallback = null;
    }

    public void clearPlayerLeftCallback() {
        this.playerLeftCallback = null;
    }

    public void clearPlayerReadyCallback() {
        this.playerReadyCallback = null;
    }

    /**
     * Set callback cho KICK_PLAYER
     */
    public void setKickPlayerCallback(Consumer<Map<String, Object>> callback) {
        this.kickPlayerCallback = callback;
    }

    public void clearKickPlayerCallback() {
        this.kickPlayerCallback = null;
    }


    /**
     * Set callback khi có người chơi trả lời
     */
    public void setPlayerAnsweredCallback(Consumer<Map<String, Object>> callback) {
        this.playerAnsweredCallback = callback;
    }

    /**
     * Set callback khi có người chơi chuyển câu hỏi
     */
    public void setPlayerProgressCallback(Consumer<Map<String, Object>> callback) {
        this.playerProgressCallback = callback;
    }


    public void clearVoiceStatusCallback() {
        this.voiceStatusCallback = null;
        System.out.println("🗑️ Voice status callback cleared");
    }
    private Map<String, Consumer<User>> profileByIdCallbacks = new HashMap<>();

    private Map<String, Consumer<JsonObject>> pendingRequests = new ConcurrentHashMap<>();


    // ============================================
// GAME CALLBACKS
// ============================================
    private Consumer<Map<String, Object>> gameStartCallback;
    private Consumer<Map<String, Object>> gameQuestionCallback;
    private Consumer<Map<String, Object>> answerResultCallback;
    private Consumer<Map<String, Object>> gameUpdateCallback;
    private Consumer<Map<String, Object>> positionUpdateCallback;
    private Consumer<Map<String, Object>> gameEndCallback;
    private Consumer<Map<String, Object>> nitroBoostCallback;
    private Consumer<JsonObject> voiceStatusCallback;
    /**
     * Set callback khi game bắt đầu
     */
    public void setGameStartCallback(Consumer<Map<String, Object>> callback) {
        this.gameStartCallback = callback;
    }

    /**
     * Set callback khi nhận câu hỏi mới
     */
    public void setGameQuestionCallback(Consumer<Map<String, Object>> callback) {
        this.gameQuestionCallback = callback;
    }

    /**
     * Set callback khi nhận kết quả đáp án
     */
    public void setAnswerResultCallback(Consumer<Map<String, Object>> callback) {
        this.answerResultCallback = callback;
    }

    /**
     * Set callback khi game update
     */
    public void setGameUpdateCallback(Consumer<Map<String, Object>> callback) {
        this.gameUpdateCallback = callback;
    }

    /**
     * Set callback khi cập nhật vị trí
     */
    public void setPositionUpdateCallback(Consumer<Map<String, Object>> callback) {
        this.positionUpdateCallback = callback;
    }

    /**
     * Set callback khi game kết thúc
     */
    public void setGameEndCallback(Consumer<Map<String, Object>> callback) {
        this.gameEndCallback = callback;
    }

    public void setVoiceStatusCallback(Consumer<JsonObject> callback) {
        this.voiceStatusCallback = callback;
    }
    /**
     * Set callback khi có nitro boost
     */
    public void setNitroBoostCallback(Consumer<Map<String, Object>> callback) {
        this.nitroBoostCallback = callback;
    }
    /**
     * Clear tất cả game callbacks
     */
    public void clearGameCallbacks() {
        gameStartCallback = null;
        gameQuestionCallback = null;
        answerResultCallback = null;
        gameUpdateCallback = null;
        positionUpdateCallback = null;
        questionResultCallback = null;
        gameEndCallback = null;
        nitroBoostCallback = null;
        playerAnsweredCallback = null;
        playerProgressCallback = null;
    }

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

            socket.setKeepAlive(true);
            socket.setTcpNoDelay(true); // Disable Nagle's algorithm
            socket.setSoTimeout(0);

            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(socket.getOutputStream(), true);
            connected = true;

            System.out.println("✅ Connected to server: " + host + ":" + port);
            System.out.println("   Keep-Alive: ENABLED");
            System.out.println("   TCP No Delay: ENABLED");
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
                            if (!Protocol.HEARTBEAT_ACK.equals(type)) {
                                System.out.println("📨 Received: " + type);
                            }
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
        // ✅ Convert JsonObject sang Map một lần
        Gson gson = new Gson();
        Type mapType = new TypeToken<Map<String, Object>>() {}.getType();
        Map<String, Object> data = gson.fromJson(json, mapType);

        switch (type) {
            case "ERROR":
                handleErrorMessage(json);
                break;

            case "PONG":
                // Heartbeat response - do nothing, just keep alive
                // System.out.println("💓 PONG received");
                break;

            case Protocol.HEARTBEAT_ACK:
                handleHeartbeatAck();
                break;

            case Protocol.GET_PROFILE:
                if (profileCallback != null) {
                    profileCallback.accept(json);
                    profileCallback = null;
                }
                break;

            case Protocol.GET_PROFILE_BY_ID:
                handleProfileByIdResponse(json);
                break;

            case Protocol.UPDATE_PROFILE:
                handleUpdateProfileResponse(json);
                break;

            case Protocol.GET_LEADERBOARD:
                if (leaderboardCallback != null) {
                    leaderboardCallback.accept(json);
                    leaderboardCallback = null;
                }
                break;

            case Protocol.JOIN_ROOM_RESPONSE:
                System.out.println("🚪 [CLIENT] Received JOIN_ROOM_RESPONSE");
                handleJoinRoomResponse(json);
                break;

            case Protocol.PLAYER_JOINED:
                System.out.println("🆕 [CLIENT] Received PLAYER_JOINED");
                if (playerJoinedCallback != null) {
                    playerJoinedCallback.accept(data);
                }
                break;
            case Protocol.VOICE_STATUS_UPDATE:
                System.out.println("🎤 [CLIENT] Received VOICE_STATUS_UPDATE");
                handleVoiceStatusUpdate(json);
                break;

            case Protocol.GET_VOICE_STATUS_RESPONSE:
                System.out.println("🎤 [CLIENT] Received GET_VOICE_STATUS_RESPONSE");
                handleVoiceStatusResponse(json);
                break;
            case Protocol.PLAYER_LEFT:
                System.out.println("👋 [CLIENT] Received PLAYER_LEFT");
                if (playerLeftCallback != null) {
                    playerLeftCallback.accept(data);
                }
                break;

            case Protocol.KICK_PLAYER:
                System.out.println("👢 [CLIENT] Received KICK_PLAYER");
                if (kickPlayerCallback != null) {
                    kickPlayerCallback.accept(data);
                }
                break;

            case Protocol.PLAYER_READY:
                System.out.println("✅ [CLIENT] Received PLAYER_READY");
                if (playerReadyCallback != null) {
                    playerReadyCallback.accept(data);
                }
                break;

//            case Protocol.ROOM_CHAT:
//                if (roomChatCallback != null) {
//                    roomChatCallback.accept(data);
//                }
//                break;

            // ==================== MATCHMAKING ====================
            case Protocol.FIND_MATCH:
                handleFindMatchResponse(json);
                break;

            case Protocol.MATCH_FOUND:
                handleMatchFoundResponse(json);
                break;

            case Protocol.CANCEL_FIND_MATCH:
                handleCancelFindMatchResponse(json);
                break;

            case Protocol.MATCH_FAILED:
                handleMatchFailedResponse(json);
                break;

            // GLOBAL CHAT
            case Protocol.GLOBAL_CHAT:
            case "GLOBAL_CHAT_MESSAGE":
                if (globalChatCallback != null) {
                    System.out.println("💬 [GLOBAL CHAT] New message received");
                    globalChatCallback.accept(json);
                }
                break;

            // PRIVATE CHAT
            case Protocol.NEW_MESSAGE:
                handleNewPrivateMessage(json);
                break;
            case Protocol.NEW_SERVER_MESSAGE:
                handleNewServerMessage(json);
                break;

            case Protocol.GET_SERVER_MESSAGES:
            case Protocol.MARK_SERVER_MESSAGE_READ:
            case Protocol.GET_ONLINE_USERS:
            case Protocol.GET_MESSAGES:
            case Protocol.SEND_MESSAGE:
            case Protocol.MESSAGE_READ: {
                Consumer<JsonObject> callback = pendingRequests.remove(type);
                if (callback != null) {
                    callback.accept(json);
                }
                break;
            }


            // ROOM CHAT
            case Protocol.ROOM_CHAT:
            case "ROOM_CHAT_MESSAGE":
                if (roomChatCallback != null) {
                    System.out.println("🏠 [ROOM CHAT] New message in room");
                    roomChatCallback.accept(json);
                }
                break;

            // GAME CHAT
            case Protocol.GAME_CHAT:
            case "GAME_CHAT_MESSAGE":
                if (gameChatCallback != null) {
                    System.out.println("🎮 [GAME CHAT] New message in game");
                    gameChatCallback.accept(json);
                } else {
                    System.out.println("⚠️ [GAME CHAT] Tính năng đang phát triển");
                }
                break;

            case Protocol.START_GAME:
                System.out.println("🎮 [CLIENT] Game starting!");
                if (gameStartCallback != null) {
                    gameStartCallback.accept(data);
                }
                break;

            case Protocol.GAME_QUESTION:
                System.out.println("❓ [CLIENT] Received new question");
                if (gameQuestionCallback != null) {
                    gameQuestionCallback.accept(data);
                }
                break;

            case Protocol.ANSWER_RESULT:
                System.out.println("✅ [CLIENT] Received answer result");
                if (answerResultCallback != null) {
                    answerResultCallback.accept(data);
                }
                break;
            case Protocol.QUESTION_RESULT:
                if (questionResultCallback != null) {
                    questionResultCallback.accept(data);
                }
                break;

            case Protocol.PLAYER_ANSWERED:
                System.out.println("📢 [CLIENT] Another player answered");
                if (playerAnsweredCallback != null) {
                    playerAnsweredCallback.accept(data);
                }
                break;

            case Protocol.PLAYER_PROGRESS:
                System.out.println("📢 [CLIENT] Player progress update");
                if (playerProgressCallback != null) {
                    playerProgressCallback.accept(data);
                }
                break;


            case Protocol.GAME_UPDATE:
                System.out.println("🔄 [CLIENT] Received game state update");
                if (gameUpdateCallback != null) {
                    gameUpdateCallback.accept(data);
                }
                break;

            case Protocol.PLAYER_POSITION_UPDATE:
                System.out.println("🏎️ [CLIENT] Received position update");
                if (positionUpdateCallback != null) {
                    positionUpdateCallback.accept(data);
                }
                break;

            case Protocol.GAME_END:
                System.out.println("🏁 [CLIENT] Game ended!");
                if (gameEndCallback != null) {
                    gameEndCallback.accept(data);
                }
                break;

            case Protocol.NITRO_BOOST:
                System.out.println("🚀 [CLIENT] Player used nitro boost!");
                if (nitroBoostCallback != null) {
                    nitroBoostCallback.accept(data);
                }
                break;

            default:
                Consumer<JsonObject> cb = pendingRequests.remove(type);
                if (cb != null) {
                    cb.accept(json);
                } else {
                    System.out.println("⚠️ No handler for message type: " + type);
                }
                break;
        }

        // Check dynamic handlers
        Consumer<JsonObject> dynamicHandler = messageHandlers.get(type);
        if (dynamicHandler != null) {
            System.out.println("🎯 Found dynamic handler for: " + type);
            dynamicHandler.accept(json);
        }
    }

    /**
     * Handle new server message (real-time notification)
     */
    private void handleNewServerMessage(JsonObject json) {
        try {
            int messageId = json.get("messageId").getAsInt();
            String messageType = json.get("messageType").getAsString();
            String senderName = json.get("senderName").getAsString();
            String content = json.get("content").getAsString();
            String sentAt = json.get("sentAt").getAsString();
            boolean isImportant = json.has("isImportant") && json.get("isImportant").getAsBoolean();

            System.out.println("📨 [SERVER MESSAGE] New message received");
            System.out.println("   Type: " + messageType);
            System.out.println("   From: " + senderName);
            System.out.println("   Content: " + content.substring(0, Math.min(50, content.length())));

            // Convert to Map
            Map<String, Object> message = new HashMap<>();
            message.put("messageId", messageId);
            message.put("messageType", messageType);
            message.put("senderName", senderName);
            message.put("content", content);
            message.put("sentAt", sentAt);
            message.put("isImportant", isImportant);

            // Trigger callback
            if (serverMessageCallback != null) {
                System.out.println("✅ [SERVER MESSAGE] Calling callback");
                serverMessageCallback.accept(message);
            } else {
                System.out.println("⚠️ [SERVER MESSAGE] No callback registered");
            }

        } catch (Exception e) {
            System.err.println("❌ Error handling new server message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Get server messages
     */
    public void getServerMessages(int limit, Consumer<List<Map<String, Object>>> callback) {
        if (!isConnected()) {
            System.err.println("❌ Cannot get server messages - not connected");
            callback.accept(new ArrayList<>());
            return;
        }

        if (isLoadingServerMessages) {
            System.out.println("⏭️ Already loading server messages, skipping");
            return;
        }
        isLoadingServerMessages = true;

        System.out.println("📨 [SERVER MESSAGES] Getting messages (limit=" + limit + ")");

        removePendingCallback(Protocol.GET_SERVER_MESSAGES);
        final boolean[] callbackCalled = new boolean[]{false};

        setPendingCallback(Protocol.GET_SERVER_MESSAGES, (json) -> {
            try {
                synchronized (callbackCalled) {
                    if (callbackCalled[0]) return;
                    callbackCalled[0] = true;
                }

                removePendingCallback(Protocol.GET_SERVER_MESSAGES);

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
                    message.put("messageType", msgObj.get("messageType").getAsString());
                    message.put("senderName", msgObj.get("senderName").getAsString());
                    message.put("content", msgObj.get("content").getAsString());
                    message.put("sentAt", msgObj.get("sentAt").getAsString());
                    message.put("isImportant", msgObj.get("isImportant").getAsBoolean());

                    if (msgObj.has("isRead")) {
                        message.put("isRead", msgObj.get("isRead").getAsBoolean());
                    }
                    if (msgObj.has("readAt")) {
                        message.put("readAt", msgObj.get("readAt").getAsString());
                    }

                    messages.add(message);
                }

                System.out.println("✅ [SERVER MESSAGES] Loaded " + messages.size() + " messages");
                callback.accept(messages);

            } catch (Exception e) {
                System.err.println("❌ Error parsing server messages: " + e.getMessage());
                e.printStackTrace();
                synchronized (callbackCalled) {
                    if (!callbackCalled[0]) {
                        callbackCalled[0] = true;
                        callback.accept(new ArrayList<>());
                    }
                }
            } finally {
                isLoadingServerMessages = false;
            }
        });

        Map<String, Object> request = new HashMap<>();
        request.put("type", Protocol.GET_SERVER_MESSAGES);
        request.put("limit", limit);
        sendJson(request);

        // Timeout
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                synchronized (callbackCalled) {
                    if (!callbackCalled[0]) {
                        callbackCalled[0] = true;
                        removePendingCallback(Protocol.GET_SERVER_MESSAGES);
                        System.err.println("⚠️ Get server messages timeout");
                        callback.accept(new ArrayList<>());
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                isLoadingServerMessages = false;
            }
        }, "GetServerMessagesTimeout").start();
    }

    /**
     * Mark server message as read
     */
    public void markServerMessageAsRead(int messageId) {
        if (!isConnected()) return;

        Map<String, Object> request = new HashMap<>();
        request.put("type", Protocol.MARK_SERVER_MESSAGE_READ);
        request.put("messageId", messageId);
        sendJson(request);

        System.out.println("✅ [SERVER MESSAGES] Marked message " + messageId + " as read");
    }

    /**
     * Mark all server messages as read
     */
    public void markAllServerMessagesAsRead() {
        if (!isConnected()) return;

        Map<String, Object> request = new HashMap<>();
        request.put("type", Protocol.MARK_SERVER_MESSAGE_READ);
        sendJson(request);

        System.out.println("✅ [SERVER MESSAGES] Marked all messages as read");
    }

    /**
     * Get online users
     */
    public void getOnlineUsers(Consumer<List<Map<String, Object>>> callback) {
        if (!isConnected()) {
            callback.accept(new ArrayList<>());
            return;
        }

        System.out.println("👥 [ONLINE USERS] Requesting online users");

        removePendingCallback(Protocol.GET_ONLINE_USERS);
        final boolean[] callbackCalled = new boolean[]{false};

        setPendingCallback(Protocol.GET_ONLINE_USERS, (json) -> {
            try {
                synchronized (callbackCalled) {
                    if (callbackCalled[0]) return;
                    callbackCalled[0] = true;
                }

                removePendingCallback(Protocol.GET_ONLINE_USERS);

                boolean success = json.get("success").getAsBoolean();
                if (!success) {
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
                    user.put("avatarUrl", userObj.get("avatarUrl").getAsString());
                    user.put("totalScore", userObj.get("totalScore").getAsInt());
                    user.put("isOnline", true);

                    users.add(user);
                }

                System.out.println("✅ [ONLINE USERS] Found " + users.size() + " online users");
                callback.accept(users);

            } catch (Exception e) {
                System.err.println("❌ Error parsing online users: " + e.getMessage());
                synchronized (callbackCalled) {
                    if (!callbackCalled[0]) {
                        callbackCalled[0] = true;
                        callback.accept(new ArrayList<>());
                    }
                }
            }
        });

        Map<String, Object> request = new HashMap<>();
        request.put("type", Protocol.GET_ONLINE_USERS);
        sendJson(request);

        // Timeout
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                synchronized (callbackCalled) {
                    if (!callbackCalled[0]) {
                        callbackCalled[0] = true;
                        removePendingCallback(Protocol.GET_ONLINE_USERS);
                        callback.accept(new ArrayList<>());
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, "GetOnlineUsersTimeout").start();
    }


    /**
     * Xử lý response từ FIND_MATCH
     */
    private void handleFindMatchResponse(JsonObject json) {
        System.out.println("🔍 [CLIENT] FIND_MATCH response received");

        boolean success = json.has("success") && json.get("success").getAsBoolean();
        String message = json.has("message") ? json.get("message").getAsString() : "";

        if (!success) {
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Lỗi");
                alert.setHeaderText("Không thể tìm trận");
                alert.setContentText(message);
                alert.showAndWait();
            });
        } else {
            System.out.println("✅ " + message);
        }

        // Gọi callback nếu có
        if (findMatchResponseCallback != null) {
            Platform.runLater(() -> findMatchResponseCallback.accept(json));
        }
    }

    /**
     * Xử lý response từ MATCH_FOUND
     */
    private void handleMatchFoundResponse(JsonObject json) {
        System.out.println("🎮 [CLIENT] MATCH_FOUND response received");
        System.out.println("📦 Raw JSON: " + json.toString());

        Platform.runLater(() -> {
            try {
                boolean success = json.has("success") && json.get("success").getAsBoolean();

                if (!success) {
                    String message = json.has("message") ? json.get("message").getAsString() : "Không tìm thấy đối thủ";
                    System.err.println("❌ MATCH_FOUND failed: " + message);

                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Lỗi");
                    alert.setHeaderText("Ghép trận thất bại");
                    alert.setContentText(message);
                    alert.showAndWait();
                    return;
                }

                // ✅ Parse với null checks
                String roomId = json.has("roomId") && !json.get("roomId").isJsonNull()
                        ? json.get("roomId").getAsString()
                        : "unknown";

                String subject = json.has("subject") && !json.get("subject").isJsonNull()
                        ? json.get("subject").getAsString()
                        : "unknown";

                String difficulty = json.has("difficulty") && !json.get("difficulty").isJsonNull()
                        ? json.get("difficulty").getAsString()
                        : "medium";

                System.out.println("✅ Match found!");
                System.out.println("   Room: " + roomId);
                System.out.println("   Subject: " + subject);
                System.out.println("   Difficulty: " + difficulty);

                // ✅ Parse opponent with null checks
                JsonObject opponent = json.has("opponent") && !json.get("opponent").isJsonNull()
                        ? json.getAsJsonObject("opponent")
                        : new JsonObject();

                String opponentUsername = opponent.has("username") && !opponent.get("username").isJsonNull()
                        ? opponent.get("username").getAsString()
                        : "Unknown";

                String opponentFullName = opponent.has("fullName") && !opponent.get("fullName").isJsonNull()
                        ? opponent.get("fullName").getAsString()
                        : opponentUsername;

                int opponentScore = opponent.has("totalScore") && !opponent.get("totalScore").isJsonNull()
                        ? opponent.get("totalScore").getAsInt()
                        : 0;

                System.out.println("   Opponent: " + opponentUsername + " (" + opponentFullName + ")");
                System.out.println("   Score: " + opponentScore);

                // ✅ Lưu room ID
                setCurrentRoomId(roomId);

                // ✅ Dừng timer nếu có
                // TODO: Add your timer stop code here if needed

                // ✅ Hiển thị thông báo
                System.out.println("🎉 Đã tìm thấy đối thủ!");
                System.out.println("   Đối thủ: " + opponentFullName);
                System.out.println("   Điểm: " + opponentScore);
                System.out.println("   Môn: " + subject + " (" + difficulty + ")");
                System.out.println("   ⏳ Đợi START_GAME từ server...");

                // ✅ Update UI if you have UI elements
                // TODO: Update your UI labels here

            } catch (Exception e) {
                System.err.println("❌ Error in handleMatchFound: " + e.getMessage());
                e.printStackTrace();

                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Lỗi");
                alert.setHeaderText("Lỗi xử lý kết quả");
                alert.setContentText("Không thể xử lý kết quả ghép trận: " + e.getMessage());
                alert.showAndWait();
            }
        });
    }

    /**
     * Xử lý response từ CANCEL_FIND_MATCH
     */
    private void handleCancelFindMatchResponse(JsonObject json) {
        System.out.println("❌ [CLIENT] CANCEL_FIND_MATCH response received");

        boolean success = json.has("success") && json.get("success").getAsBoolean();
        String message = json.has("message") ? json.get("message").getAsString() : "";

        if (success) {
            System.out.println("✅ " + message);
        } else {
            System.out.println("⚠️ " + message);
        }
    }

    /**
     * Xử lý response từ MATCH_FAILED
     */
    private void handleMatchFailedResponse(JsonObject json) {
        System.out.println("❌ [CLIENT] MATCH_FAILED response received");

        String message = json.has("message") ? json.get("message").getAsString() : "Không thể tạo trận đấu";

        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Lỗi");
            alert.setHeaderText("Ghép trận thất bại");
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
    private void handleHeartbeatAck() {
        lastHeartbeatTime = System.currentTimeMillis();
        missedHeartbeats = 0;
        // System.out.println("💓 Heartbeat ACK received");
    }

    // ============================================
// GAME METHODS
// ============================================

    /**
     * Gửi đáp án cho câu hỏi hiện tại
     * @param roomId ID phòng
     * @param answerIndex Index đáp án (0-3 cho A-D)
     */
    public void submitAnswer(String roomId, int answerIndex) {
        Map<String, Object> request = new HashMap<>();
        request.put("type", Protocol.SUBMIT_ANSWER);
        request.put("room_id", roomId);
        request.put("answer", answerIndex);
        request.put("username", currentUsername);
        request.put("timestamp", System.currentTimeMillis());

        sendRequest(request);
        System.out.println("📤 [GAME] Submitted answer: " + (char)('A' + answerIndex));
    }

    /**
     * Request game state (nếu bị disconnect)
     */
    public void requestGameState(String roomId) {
        Map<String, Object> request = new HashMap<>();
        request.put("type", Protocol.GET_GAME_STATE);
        request.put("room_id", roomId);
        request.put("username", currentUsername);

        sendRequest(request);
        System.out.println("📤 [GAME] Requesting game state");
    }

    /**
     * Leave game
     */
    public void leaveGame(String roomId) {
        Map<String, Object> request = new HashMap<>();
        request.put("type", Protocol.LEAVE_GAME);
        request.put("room_id", roomId);
        request.put("username", currentUsername);

        sendRequest(request);
        System.out.println("📤 [GAME] Leaving game");
    }

    /**
     * Gửi chat message trong game
     */
    public void sendGameChatMessage(String roomId, String message) {
        Map<String, Object> request = new HashMap<>();
        request.put("type", Protocol.GAME_CHAT);
        request.put("room_id", roomId);
        request.put("username", currentUsername);
        request.put("message", message);
        request.put("timestamp", System.currentTimeMillis());

        sendRequest(request);
        System.out.println("💬 [GAME CHAT] Sent: " + message);
    }

    /**
     * Set callback cho game chat
     */
    public void setGameChatCallback(Consumer<JsonObject> callback) {
        this.gameChatCallback = callback;
    }

    /**
     * Ready for next question (optional - nếu muốn player phải confirm)
     */
    public void readyForNextQuestion(String roomId) {
        Map<String, Object> request = new HashMap<>();
        request.put("type", Protocol.READY_NEXT_QUESTION);
        request.put("room_id", roomId);
        request.put("username", currentUsername);

        sendRequest(request);
        System.out.println("✅ [GAME] Ready for next question");
    }


///** Tìm trận **//
    /**
     * Lưu môn học được chọn vào session
     */
    public void setSelectedSubject(String subject) {
        this.selectedSubject = subject;
        System.out.println("📚 Selected subject: " + subject);
    }

    public void setSelectedDifficulty(String difficulty) {
        this.selectedDifficulty = difficulty;
        System.out.println("📚 Selected subject: " + difficulty);
    }

    public void setSelectedcountPlayer(int countPlayer) {
        this.selectedCountPlayer  = countPlayer;
        System.out.println("📚 Selected subject: " + countPlayer);
    }

    /**
     * Lấy môn học đang được chọn
     */
    public String getSelectedSubject() {
        return selectedSubject;
    }
    /**
     * Lấy môn học đang được chọn
     */
    public String getSelectedDifficulty() {
        return selectedDifficulty;
    }


    public void setSelectedCountPlayer(int count) {
        this.selectedCountPlayer = count;
    }
    public int getSelectedCountPlayer() {
        return selectedCountPlayer;
    }






    /**
     * Đăng ký handler động cho một loại message
     * @param messageType Loại message (vd: "MATCH_FOUND", "GAME_START")
     * @param handler Callback xử lý message
     */
    public void registerHandler(String messageType, Consumer<JsonObject> handler) {
        messageHandlers.put(messageType, handler);
        System.out.println("✅ Registered handler for: " + messageType);
    }


    /**
     * Hủy đăng ký handler
     * @param messageType Loại message cần hủy
     */
    public void unregisterHandler(String messageType) {
        messageHandlers.remove(messageType);
        System.out.println("🗑️ Unregistered handler for: " + messageType);
    }

    /**
     * Clear tất cả handlers
     */
    public void clearAllHandlers() {
        messageHandlers.clear();
        System.out.println("🗑️ Cleared all message handlers");
    }

    /**
     * Tìm trận đấu theo môn học và độ khó
     * @param subject Môn học (MATH, ENGLISH, LITERATURE)
     * @param difficulty Độ khó (EASY, MEDIUM, HARD)
     */
    public void findMatch(String subject, String difficulty, int countPlayer) {
        this.selectedSubject = subject;
        this.selectedDifficulty = difficulty;
        this.selectedCountPlayer = countPlayer;

        JsonObject request = new JsonObject();
        request.addProperty("type", Protocol.FIND_MATCH);
        request.addProperty("subject", subject);
        request.addProperty("difficulty", difficulty);
        request.addProperty("countPlayer", countPlayer);

        sendMessage(request.toString());
        System.out.println("🔍 Sent FIND_MATCH: " + subject + "/" + difficulty + "/" + countPlayer);
    }

    /**
     * Hủy tìm kiếm trận đấu
     */
    public void cancelFindMatch() {
        if (!isConnected()) {
            System.err.println("❌ Cannot cancel find match - not connected");
            return;
        }

        System.out.println("❌ Canceling matchmaking...");

        JsonObject request = new JsonObject();
        request.addProperty("type", Protocol.CANCEL_FIND_MATCH);

        sendMessage(request.toString());
        System.out.println("❌ Sent CANCEL_FIND_MATCH");
    }

    /**
     * Gửi message đơn giản (dùng cho các request không có callback phức tạp)
     * @param message JSON string hoặc raw message
     */
    public void sendMessage(String message) {
        if (!isConnected()) {
            System.err.println("❌ Cannot send message - not connected");
            return;
        }

        if (writer != null && !writer.checkError()) {
            writer.println(message);
            writer.flush();
            System.out.println("📤 Sent: " + message);
        }
    }

    /**
     * Gửi request đến server (JsonObject hoặc Map<String, Object>)
     */
    private void sendRequest(Object request) {
        if (!isConnected()) {
            System.err.println("❌ Cannot send request - not connected");
            return;
        }

        try {
            String jsonString;

            if (request instanceof JsonObject json) {
                jsonString = json.toString();
            } else if (request instanceof Map<?, ?> map) {
                // Chuyển Map sang JSON string
                jsonString = new Gson().toJson(map);
            } else {
                throw new IllegalArgumentException("Unsupported request type: " + request.getClass());
            }

            // Gửi trực tiếp bằng sendMessage
            sendMessage(jsonString);

        } catch (Exception e) {
            System.err.println("❌ Failed to send request: " + e.getMessage());
            e.printStackTrace();
        }
    }


    // ============== TRAINING MODE METHODS ==============/

    /**
     * Bắt đầu chế độ luyện tập
     * @param subject Môn học
     * @param difficulty Độ khó
     */
    public void startTrainingMode(String subject, String difficulty) {
        if (!isConnected()) {
            System.err.println("❌ Cannot start training - not connected");
            return;
        }

        System.out.println("🎓 Starting training mode: " + subject + " (" + difficulty + ")");

        Map<String, Object> request = new HashMap<>();
        request.put("type", Protocol.TRAINING_MODE);
        request.put("subject", subject);
        request.put("difficulty", difficulty);
        sendJson(request);
    }

    /**
     * Lấy câu hỏi luyện tập
     * @param subject Môn học
     * @param difficulty Độ khó
     * @param count Số lượng câu hỏi
     * @param callback Callback nhận kết quả
     */
    public void getTrainingQuestions(String subject, String difficulty, int count,
                                     Consumer<List<Map<String, Object>>> callback) {
        if (!isConnected()) {
            System.err.println("❌ Cannot get training questions - not connected");
            callback.accept(new ArrayList<>());
            return;
        }

        System.out.println("📝 Getting training questions: " + subject + " x" + count);

        removePendingCallback("GET_TRAINING_QUESTIONS");
        final boolean[] callbackCalled = new boolean[]{false};

        setPendingCallback("GET_TRAINING_QUESTIONS", (json) -> {
            try {
                synchronized (callbackCalled) {
                    if (callbackCalled[0]) return;
                    callbackCalled[0] = true;
                }

                removePendingCallback("GET_TRAINING_QUESTIONS");

                boolean success = json.get("success").getAsBoolean();
                if (!success) {
                    callback.accept(new ArrayList<>());
                    return;
                }

                JsonArray arr = json.getAsJsonArray("questions");
                List<Map<String, Object>> questions = new ArrayList<>();

                for (int i = 0; i < arr.size(); i++) {
                    JsonObject qObj = arr.get(i).getAsJsonObject();
                    Map<String, Object> question = new HashMap<>();

                    question.put("questionId", qObj.get("questionId").getAsInt());
                    question.put("subject", qObj.get("subject").getAsString());
                    question.put("question", qObj.get("question").getAsString());
                    question.put("optionA", qObj.get("optionA").getAsString());
                    question.put("optionB", qObj.get("optionB").getAsString());
                    question.put("optionC", qObj.get("optionC").getAsString());
                    question.put("optionD", qObj.get("optionD").getAsString());
                    question.put("correctAnswer", qObj.get("correctAnswer").getAsString());
//                    qMap.put("correctAnswer", q.getCorrectAnswer());
                    question.put("difficulty", qObj.get("difficulty").getAsString());

                    questions.add(question);
                }

                System.out.println("✅ Loaded " + questions.size() + " training questions");
                callback.accept(questions);

            } catch (Exception e) {
                System.err.println("❌ Error parsing training questions: " + e.getMessage());
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
        request.put("type", "GET_TRAINING_QUESTIONS");
        request.put("subject", subject);
        request.put("difficulty", difficulty);
        request.put("count", count);
        sendJson(request);

        // Timeout
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                synchronized (callbackCalled) {
                    if (!callbackCalled[0]) {
                        callbackCalled[0] = true;
                        removePendingCallback("GET_TRAINING_QUESTIONS");
                        System.err.println("⚠️ Get training questions timeout");
                        callback.accept(new ArrayList<>());
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, "GetTrainingQuestionsTimeout").start();
    }

    /**
     * Cập nhật password
     * @param oldPassword Mật khẩu cũ
     * @param newPassword Mật khẩu mới
     * @param callback Callback kết quả
     */
    public void updatePassword(String oldPassword, String newPassword, Consumer<Boolean> callback) {
        if (!isConnected()) {
            System.err.println("❌ Cannot update password - not connected");
            callback.accept(false);
            return;
        }

        System.out.println("🔐 Updating password...");

        removePendingCallback("UPDATE_PASSWORD");
        final boolean[] callbackCalled = new boolean[]{false};

        setPendingCallback("UPDATE_PASSWORD", (json) -> {
            try {
                synchronized (callbackCalled) {
                    if (callbackCalled[0]) return;
                    callbackCalled[0] = true;
                }

                removePendingCallback("UPDATE_PASSWORD");

                boolean success = json.get("success").getAsBoolean();
                String message = json.get("message").getAsString();

                System.out.println("📥 Update password result: " + success + " - " + message);

                Platform.runLater(() -> {
                    Alert alert = new Alert(success ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR);
                    alert.setTitle("Đổi mật khẩu");
                    alert.setHeaderText(null);
                    alert.setContentText(message);
                    alert.showAndWait();
                });

                callback.accept(success);

            } catch (Exception e) {
                System.err.println("❌ Error handling password update: " + e.getMessage());
                synchronized (callbackCalled) {
                    if (!callbackCalled[0]) {
                        callbackCalled[0] = true;
                        callback.accept(false);
                    }
                }
            }
        });

        Map<String, Object> request = new HashMap<>();
        request.put("type", "UPDATE_PASSWORD");
        request.put("oldPassword", oldPassword);
        request.put("newPassword", newPassword);
        sendJson(request);

        // Timeout
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                synchronized (callbackCalled) {
                    if (!callbackCalled[0]) {
                        callbackCalled[0] = true;
                        removePendingCallback("UPDATE_PASSWORD");
                        System.err.println("⚠️ Update password timeout");
                        callback.accept(false);
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, "UpdatePasswordTimeout").start();
    }

    /**
     * Tạo phòng game mới
     * @param subject Môn học
     * @param difficulty Độ khó
     * @param callback Callback nhận Map<String, Object> chứa toàn bộ room data
     */
    /**
     * Gửi yêu cầu tạo phòng
     */
    public void createRoom(String subject, String difficulty,
                           Consumer<Map<String, Object>> callback) {
        try {
            JsonObject request = new JsonObject();
            request.addProperty("type", Protocol.CREATE_ROOM);
            request.addProperty("subject", subject);
            request.addProperty("difficulty", difficulty);

            sendRequest(request);

            // Register one-time callback
            pendingRequests.put(Protocol.CREATE_ROOM, json -> {
                Gson gson = new Gson();
                Type type = new TypeToken<Map<String, Object>>() {}.getType();
                Map<String, Object> data = gson.fromJson(json, type);
                callback.accept(data);
            });

            System.out.println("📤 CREATE_ROOM request sent");

        } catch (Exception e) {
            System.err.println("❌ Failed to send CREATE_ROOM: " + e.getMessage());
            e.printStackTrace();
        }
    }



    /**
     * Join game room
     */
    public void joinGameRoom(String roomId) {
        if (!isConnected()) {
            System.err.println("❌ Cannot join game room - not connected");
            if (joinRoomCallback != null) {
                joinRoomCallback.onResult(false, "Không kết nối với server", null);
            }
            return;
        }

        System.out.println("🚪 Joining game room: " + roomId);

        Map<String, Object> request = new HashMap<>();
        request.put("type", "JOIN_ROOM");
        request.put("roomId", roomId);
        sendJson(request);
    }

    /**
     * Gửi yêu cầu tham gia phòng
     */
    public void joinRoom(String roomId) {
        try {
            JsonObject request = new JsonObject();
            request.addProperty("type", Protocol.JOIN_ROOM);
            request.addProperty("roomId", roomId);

            sendRequest(request);
            System.out.println("📤 JOIN_ROOM request sent: " + roomId);

        } catch (Exception e) {
            System.err.println("❌ Failed to send JOIN_ROOM: " + e.getMessage());
            e.printStackTrace();
        }
    }


    /**
     * Leave game room
     */
    public void leaveGameRoom(String roomId) {
        try {
            JsonObject request = new JsonObject();
            request.addProperty("type", Protocol.LEAVE_ROOM);
            request.addProperty("roomId", roomId);

            sendRequest(request);
            System.out.println("📤 LEAVE_ROOM request sent");

        } catch (Exception e) {
            System.err.println("❌ Failed to send LEAVE_ROOM: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Gửi trạng thái sẵn sàng
     */
    public void sendReady(boolean isReady) {
        try {
            JsonObject request = new JsonObject();
            request.addProperty("type", Protocol.READY);
            request.addProperty("isReady", isReady);

            sendRequest(request);
            System.out.println("📤 READY request sent: " + isReady);

        } catch (Exception e) {
            System.err.println("❌ Failed to send READY: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Bắt đầu game (chỉ host)
     */
    public void sendStartGame(String roomId) {
        if (!isConnected()) {
            System.err.println("❌ Cannot start game - not connected");
            return;
        }

        Map<String, Object> request = new HashMap<>();
        request.put("type", Protocol.START_GAME);
        request.put("roomId", roomId);

        sendRequest(request);
        System.out.println("📤 [GAME] Start game request sent");
    }
    /**
     * voice trong phòng
     */

    private void handleVoiceStatusUpdate(JsonObject json) {
        try {
            System.out.println("=".repeat(50));
            System.out.println("🎤 VOICE STATUS UPDATE HANDLER");
            System.out.println("   Raw JSON: " + json);
            System.out.println("=".repeat(50));

            // ✅ Check if it's a single user update (realtime)
            if (json.has("userId") && json.has("isActive")) {
                int userId = json.get("userId").getAsInt();
                boolean isActive = json.get("isActive").getAsBoolean();
                String roomId = json.has("roomId") ? json.get("roomId").getAsString() : "";

                System.out.println("📢 Single user update:");
                System.out.println("   Room: " + roomId);
                System.out.println("   User: " + userId);
                System.out.println("   Active: " + isActive);

                // ✅ Trigger callback with full JSON
                if (voiceStatusCallback != null) {
                    Platform.runLater(() -> voiceStatusCallback.accept(json));
                } else {
                    System.out.println("⚠️ No voice status callback registered");
                }
                return;
            }

            // ✅ Check if it's a batch update (full status)
            if (json.has("voiceStatus")) {
                JsonObject statusObj = json.getAsJsonObject("voiceStatus");

                // ✅ NULL safety: Check if voiceStatus is null or empty
                if (statusObj == null || statusObj.size() == 0) {
                    System.out.println("ℹ️ Voice status is empty (no active users)");

                    // Still trigger callback to clear all indicators
                    if (voiceStatusCallback != null) {
                        Platform.runLater(() -> voiceStatusCallback.accept(json));
                    }
                    return;
                }

                System.out.println("📋 Batch update:");
                for (String key : statusObj.keySet()) {
                    try {
                        int userId = Integer.parseInt(key);
                        boolean isActive = statusObj.get(key).getAsBoolean();
                        System.out.println("   User " + userId + ": " + (isActive ? "🎤 Active" : "🔇 Inactive"));
                    } catch (NumberFormatException e) {
                        System.err.println("⚠️ Invalid userId key: " + key);
                    }
                }

                // ✅ Trigger callback
                if (voiceStatusCallback != null) {
                    Platform.runLater(() -> voiceStatusCallback.accept(json));
                }
            }

        } catch (Exception e) {
            System.err.println("❌ Error handling voice status update: " + e.getMessage());
            e.printStackTrace();
        }
    }


    /**
     * Request voice status for room
     */
    public void requestVoiceStatus(String roomId) {
        try {
            JsonObject request = new JsonObject();
            request.addProperty("type", Protocol.GET_VOICE_STATUS);
            request.addProperty("roomId", roomId);

            sendRequest(request);

        } catch (Exception e) {
            System.err.println("❌ Error requesting voice status: " + e.getMessage());
        }
    }

    /**
     * Send voice status change
     */
    public void sendVoiceStatusChange(String roomId, int userId, boolean isActive) {
        try {
            JsonObject message = new JsonObject();
            message.addProperty("type", Protocol.VOICE_STATUS_CHANGE);
            message.addProperty("roomId", roomId);
            message.addProperty("userId", userId);
            message.addProperty("isActive", isActive);
            message.addProperty("timestamp", System.currentTimeMillis());

            sendRequest(message);

            System.out.println("=".repeat(50));
            System.out.println("📤 VOICE STATUS CHANGE SENT");
            System.out.println("   Room: " + roomId);
            System.out.println("   User: " + userId);
            System.out.println("   Active: " + isActive);
            System.out.println("=".repeat(50));

        } catch (Exception e) {
            System.err.println("❌ Error sending voice status: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Enhanced voice status response handler
     */
    private void handleVoiceStatusResponse(JsonObject json) {
        try {
            System.out.println("=".repeat(50));
            System.out.println("🎤 VOICE STATUS RESPONSE HANDLER");
            System.out.println("   Raw JSON: " + json);
            System.out.println("=".repeat(50));

            boolean success = json.has("success") && json.get("success").getAsBoolean();

            if (!success) {
                System.err.println("⚠️ Voice status request failed");
                return;
            }

            String roomId = json.has("roomId") ? json.get("roomId").getAsString() : "";
            System.out.println("   Room: " + roomId);

            // ✅ NULL safety: Check voiceStatus exists and is not null
            if (!json.has("voiceStatus")) {
                System.out.println("⚠️ voiceStatus field missing");
                return;
            }

            JsonObject voiceStatusObj = json.getAsJsonObject("voiceStatus");

            // ✅ NULL safety: Check if voiceStatus is null or empty
            if (voiceStatusObj == null) {
                System.out.println("⚠️ voiceStatus is null");
                return;
            }

            if (voiceStatusObj.size() == 0) {
                System.out.println("ℹ️ No active voice users in room");

                // ✅ Still trigger callback to clear all indicators
                if (voiceStatusCallback != null) {
                    Platform.runLater(() -> voiceStatusCallback.accept(json));
                }
                return;
            }

            // ✅ Parse voice status
            System.out.println("📋 Active voice users:");
            for (String key : voiceStatusObj.keySet()) {
                try {
                    int userId = Integer.parseInt(key);
                    boolean isActive = voiceStatusObj.get(key).getAsBoolean();
                    System.out.println("   User " + userId + ": " + (isActive ? "🎤 Active" : "🔇 Inactive"));
                } catch (NumberFormatException e) {
                    System.err.println("⚠️ Invalid userId key: " + key);
                }
            }

            // ✅ Trigger callback
            if (voiceStatusCallback != null) {
                Platform.runLater(() -> voiceStatusCallback.accept(json));
            } else {
                System.out.println("⚠️ No voice status callback registered");
            }

        } catch (Exception e) {
            System.err.println("❌ Error handling voice status response: " + e.getMessage());
            e.printStackTrace();
        }
    }


    /**
     * Gửi tin nhắn chat trong phòng
     */
    public void sendRoomChat(String roomId, String message) {
        try {
            JsonObject request = new JsonObject();
            request.addProperty("type", Protocol.ROOM_CHAT);
            request.addProperty("roomId", roomId);
            request.addProperty("message", message);

//            sendRequest(request);
            System.out.println("📤 ROOM_CHAT sent");

        } catch (Exception e) {
            System.err.println("❌ Failed to send ROOM_CHAT: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Mời bạn vào phòng
     */
    public void inviteToRoom(int friendUserId, String roomId) {
        try {
            JsonObject request = new JsonObject();
            request.addProperty("type", Protocol.INVITE_TO_ROOM);
            request.addProperty("friendUserId", friendUserId);
            request.addProperty("roomId", roomId);

            sendRequest(request);
            System.out.println("📤 INVITE_TO_ROOM sent to user: " + friendUserId);

        } catch (Exception e) {
            System.err.println("❌ Failed to send INVITE_TO_ROOM: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Submit answer in game
     * @param questionId ID câu hỏi
     * @param answer Đáp án (A, B, C, D)
     * @param timeSpent Thời gian làm bài (ms)
     */
    public void submitAnswer(int questionId, String answer, long timeSpent) {
        if (!isConnected()) {
            System.err.println("❌ Cannot submit answer - not connected");
            return;
        }

        System.out.println("📝 Submitting answer: Q" + questionId + " = " + answer);

        Map<String, Object> request = new HashMap<>();
        request.put("type", "SUBMIT_ANSWER");
        request.put("questionId", questionId);
        request.put("answer", answer);
        request.put("timeSpent", timeSpent);
        sendJson(request);
    }


// ============== STATISTICS METHODS ==============

    /**
     * Lấy thống kê game của user
     * @param callback Callback nhận kết quả
     */
    public void getGameStatistics(Consumer<Map<String, Object>> callback) {
        if (!isConnected()) {
            System.err.println("❌ Cannot get statistics - not connected");
            callback.accept(new HashMap<>());
            return;
        }

        System.out.println("📊 Getting game statistics...");

        removePendingCallback("GET_STATISTICS");
        final boolean[] callbackCalled = new boolean[]{false};

        setPendingCallback("GET_STATISTICS", (json) -> {
            try {
                synchronized (callbackCalled) {
                    if (callbackCalled[0]) return;
                    callbackCalled[0] = true;
                }

                removePendingCallback("GET_STATISTICS");

                boolean success = json.get("success").getAsBoolean();
                if (!success) {
                    callback.accept(new HashMap<>());
                    return;
                }

                Map<String, Object> stats = new HashMap<>();
                stats.put("totalGames", json.get("totalGames").getAsInt());
                stats.put("wins", json.get("wins").getAsInt());
                stats.put("losses", json.get("losses").getAsInt());
                stats.put("totalScore", json.get("totalScore").getAsInt());
                stats.put("mathScore", json.get("mathScore").getAsInt());
                stats.put("englishScore", json.get("englishScore").getAsInt());
                stats.put("literatureScore", json.get("literatureScore").getAsInt());
                stats.put("winRate", json.get("winRate").getAsDouble());

                System.out.println("✅ Statistics loaded");
                callback.accept(stats);

            } catch (Exception e) {
                System.err.println("❌ Error parsing statistics: " + e.getMessage());
                synchronized (callbackCalled) {
                    if (!callbackCalled[0]) {
                        callbackCalled[0] = true;
                        callback.accept(new HashMap<>());
                    }
                }
            }
        });

        Map<String, Object> request = new HashMap<>();
        request.put("type", "GET_STATISTICS");
        sendJson(request);

        // Timeout
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                synchronized (callbackCalled) {
                    if (!callbackCalled[0]) {
                        callbackCalled[0] = true;
                        removePendingCallback("GET_STATISTICS");
                        System.err.println("⚠️ Get statistics timeout");
                        callback.accept(new HashMap<>());
                    }
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, "GetStatisticsTimeout").start();
    }



    /**
     * Xử lý khi có người chơi bị kick khỏi phòng
     * @param json dữ liệu JSON từ server (chứa userId, username, isKickedByHost, newHostId, v.v.)
     */
    /**
     * Gửi yêu cầu kick player
     */
    public void kickPlayerFromRoom(String roomId, int targetUserId) {
        try {
            JsonObject request = new JsonObject();
            request.addProperty("type", Protocol.KICK_PLAYER);
            request.addProperty("roomId", roomId);
            request.addProperty("targetUserId", targetUserId);

            sendRequest(request);
            System.out.println("📤 KICK_PLAYER request sent: targetUserId=" + targetUserId);

        } catch (Exception e) {
            System.err.println("❌ Failed to send KICK_PLAYER: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Xử lý phản hồi khi tham gia phòng
     */
    private void handleJoinRoomResponse(JsonObject data) {
        boolean success = data.has("success") && data.get("success").getAsBoolean();
        String message = data.has("message") ? data.get("message").getAsString() : "Không có phản hồi";
        String roomId = data.has("roomId") ? data.get("roomId").getAsString() : "unknown";

        System.out.println("📨 [CLIENT] JOIN_ROOM_RESPONSE: success=" + success + ", roomId=" + roomId);

        if (success) {
            Gson gson = new Gson();
            Type type = new TypeToken<Map<String, Object>>() {}.getType();
            Map<String, Object> roomData = gson.fromJson(data, type);

            System.out.println("✅ Join room thành công: " + roomId);

            if (joinRoomCallback != null) {
                joinRoomCallback.onResult(true, message, roomData);
                joinRoomCallback = null;
            }

        } else {
            System.err.println("❌ Join room thất bại: " + message);

            if (joinRoomCallback != null) {
                joinRoomCallback.onResult(false, message, null);
                joinRoomCallback = null;
            }
        }
    }


    private void handleProfileByIdResponse(JsonObject json) {
        try {
            System.out.println("👤 [CLIENT] GET_PROFILE_BY_ID_RESPONSE received");

            boolean success = json.get("success").getAsBoolean();

            if (!success) {
                String message = json.has("message") ? json.get("message").getAsString() : "Failed to get profile";
                System.err.println("❌ [CLIENT] Get profile by ID failed: " + message);

                // Trigger tất cả callbacks với null
                for (Consumer<User> callback : profileByIdCallbacks.values()) {
                    callback.accept(null);
                }
                profileByIdCallbacks.clear();
                return;
            }

            // Parse user data
            JsonObject userData = json.getAsJsonObject("user");
            if (userData != null) {
                User user = gson.fromJson(userData, User.class);
                System.out.println("✅ [CLIENT] Profile loaded: " + user.getFullName() + " (ID=" + user.getUserId() + ")");

                // Trigger tất cả callbacks
                for (Consumer<User> callback : profileByIdCallbacks.values()) {
                    callback.accept(user);
                }
                profileByIdCallbacks.clear();
            } else {
                System.err.println("❌ [CLIENT] User data is null in response");
                for (Consumer<User> callback : profileByIdCallbacks.values()) {
                    callback.accept(null);
                }
                profileByIdCallbacks.clear();
            }

        } catch (Exception e) {
            System.err.println("❌ [CLIENT] Error handling profile by ID response: " + e.getMessage());
            e.printStackTrace();

            // Trigger callbacks với null khi có lỗi
            for (Consumer<User> callback : profileByIdCallbacks.values()) {
                callback.accept(null);
            }
            profileByIdCallbacks.clear();
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

                // ✅ SAVE SESSION TOKEN
                if (jsonResponse.has("sessionToken")) {
                    sessionToken = jsonResponse.get("sessionToken").getAsString();
                    System.out.println("✅ Session token received: " + sessionToken.substring(0, 8) + "...");
                }

                // ✅ Start listener SAU KHI login thành công
                startListener();
                startHeartbeat();

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

    /**
     * ✅ Start heartbeat thread
     */
    private void startHeartbeat() {
        synchronized (this) {
            if (isHeartbeatRunning && heartbeatThread != null && heartbeatThread.isAlive()) {
                System.out.println("⚠️ Heartbeat already running");
                return;
            }

            System.out.println("💓 Starting heartbeat (interval: " + Protocol.HEARTBEAT_INTERVAL + "ms)...");
            isHeartbeatRunning = true;
            lastHeartbeatTime = System.currentTimeMillis();
            missedHeartbeats = 0;

            heartbeatThread = new Thread(() -> {
                System.out.println("💓 Heartbeat thread STARTED");

                while (isHeartbeatRunning && isConnected()) {
                    try {
                        Thread.sleep(Protocol.HEARTBEAT_INTERVAL);

                        if (!isConnected()) {
                            System.out.println("⚠️ Heartbeat: Connection lost");
                            break;
                        }

                        // ✅ Send HEARTBEAT ping
                        sendHeartbeat();

                        // ✅ Check if server is still alive
                        long timeSinceLastHeartbeat = System.currentTimeMillis() - lastHeartbeatTime;

                        if (timeSinceLastHeartbeat > Protocol.HEARTBEAT_TIMEOUT) {
                            missedHeartbeats++;
                            System.err.println("⚠️ Missed heartbeat #" + missedHeartbeats +
                                    " (last: " + timeSinceLastHeartbeat + "ms ago)");

                            if (missedHeartbeats >= MAX_MISSED_HEARTBEATS) {
                                System.err.println("❌ Server not responding - connection lost!");
                                handleConnectionLost();
                                break;
                            }
                        }

                    } catch (InterruptedException e) {
                        if (isHeartbeatRunning) {
                            System.err.println("⚠️ Heartbeat interrupted");
                        }
                        break;
                    } catch (Exception e) {
                        System.err.println("❌ Heartbeat error: " + e.getMessage());
                        break;
                    }
                }

                isHeartbeatRunning = false;
                System.out.println("💓 Heartbeat thread STOPPED");

            }, "Heartbeat");

            heartbeatThread.setDaemon(true);
            heartbeatThread.start();

            System.out.println("✅ Heartbeat started");
        }
    }
    /**
     * ✅ Send HEARTBEAT ping to server
     */
    private void sendHeartbeat() {
        try {
            Map<String, Object> heartbeat = new HashMap<>();
            heartbeat.put("type", Protocol.HEARTBEAT);
            heartbeat.put("timestamp", System.currentTimeMillis());

            // Send directly without logging
            if (writer != null && !writer.checkError()) {
                String json = gson.toJson(heartbeat);
                writer.println(json);
                writer.flush();
                // System.out.println("💓 Heartbeat sent");
            }

        } catch (Exception e) {
            System.err.println("❌ Failed to send heartbeat: " + e.getMessage());
        }
    }

    /**
     * ✅ Handle connection lost
     */
    private void handleConnectionLost() {
        System.err.println("🔴 CONNECTION LOST!");

        // Stop everything
        isListening = false;
        stopHeartbeat();
        connected = false;

        // Notify user on JavaFX thread
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Mất kết nối");
            alert.setHeaderText("Kết nối tới server bị gián đoạn");
            alert.setContentText("Vui lòng kiểm tra kết nối mạng và đăng nhập lại!");
            alert.showAndWait();

            // Return to login screen
            try {
                SceneManager.getInstance().switchScene("Login.fxml");
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }


    /**
     * Stop heartbeat
     */
    private void stopHeartbeat() {
        isHeartbeatRunning = false;
        if (heartbeatThread != null && heartbeatThread.isAlive()) {
            heartbeatThread.interrupt();
            try {
                heartbeatThread.join(1000); // Wait max 1 second
            } catch (InterruptedException e) {
                // Ignore
            }
            heartbeatThread = null;
        }
        System.out.println("🛑 Heartbeat stopped");
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

//    /**
//     * Set callback for game chat messages
//     */
//    public void setGameChatCallback(Consumer<JsonObject> callback) {
//        this.gameChatCallback = callback;
//        System.out.println("✅ Game chat callback registered");
//    }

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

        String type = (String) data.get("type");
        if (sessionToken != null &&
                !"LOGIN".equals(type) &&
                !"REGISTER".equals(type) &&
                !"PING".equals(type)) {
            data.put("sessionToken", sessionToken);
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
     * Lấy thông tin profile của user khác theo ID
     */
    public void getProfileById(int userId, Consumer<User> callback) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("type", "GET_PROFILE_BY_ID");
            request.put("userId", userId);

            sendJson(request);

            // Lưu callback với key unique
            String callbackKey = "profile_" + userId + "_" + System.currentTimeMillis();
            profileByIdCallbacks.put(callbackKey, callback);

            System.out.println("📤 Sent GET_PROFILE_BY_ID request for userId: " + userId);

        } catch (Exception e) {
            System.err.println("❌ Error sending profile request: " + e.getMessage());
            callback.accept(null);
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

    public void checkFriendshipStatus(int targetUserId, Consumer<String> callback) {
        if (!isConnected()) {
            System.err.println("❌ Cannot check friendship status - not connected");
            callback.accept("none");
            return;
        }

        System.out.println("🔍 [CLIENT] Checking friendship status with userId: " + targetUserId);

        removePendingCallback("CHECK_FRIENDSHIP_STATUS");
        final boolean[] callbackCalled = new boolean[]{false};
        setPendingCallback("CHECK_FRIENDSHIP_STATUS", (json) -> {
            System.out.println("🔔 [CLIENT] CHECK_FRIENDSHIP_STATUS callback triggered");

            try {
                synchronized (callbackCalled) {
                    if (callbackCalled[0]) {
                        System.err.println("⚠️ [CLIENT] Callback already called, ignoring");
                        return;
                    }
                    callbackCalled[0] = true;
                }

                removePendingCallback("CHECK_FRIENDSHIP_STATUS");

                boolean success = json.get("success").getAsBoolean();
                if (!success) {
                    System.err.println("❌ [CLIENT] Check friendship status failed");
                    callback.accept("none");
                    return;
                }

                String status = json.get("status").getAsString();
                System.out.println("✅ [CLIENT] Friendship status: " + status);
                callback.accept(status);

            } catch (Exception e) {
                System.err.println("❌ [CLIENT] Error parsing friendship status: " + e.getMessage());
                e.printStackTrace();

                synchronized (callbackCalled) {
                    if (!callbackCalled[0]) {
                        callbackCalled[0] = true;
                        callback.accept("none");
                    }
                }
            }
        });

        Map<String, Object> request = new HashMap<>();
        request.put("type", "CHECK_FRIENDSHIP_STATUS");
        request.put("targetUserId", targetUserId);
        sendJson(request);

        // Timeout
        new Thread(() -> {
            try {
                Thread.sleep(5000);

                synchronized (callbackCalled) {
                    if (!callbackCalled[0]) {
                        callbackCalled[0] = true;
                        removePendingCallback("CHECK_FRIENDSHIP_STATUS");
                        System.err.println("⚠️ [CLIENT] Check friendship status timeout");
                        callback.accept("none");
                    } else {
                        System.out.println("✅ [CLIENT] Timeout thread: Check already completed");
                    }
                }

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }, "CheckFriendshipStatusTimeout").start();
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
            stopHeartbeat();

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

        sessionToken = null;

        selectedSubject = null;
        selectedDifficulty = null;
        clearAllHandlers();

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

            stopHeartbeat();

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

            sessionToken = null;

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