package com.edugame.server.network;

import com.edugame.common.Protocol;
import com.edugame.server.database.UserDAO;
import com.edugame.server.database.LeaderboardDAO;
import com.edugame.server.model.User;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.*;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

public class ClientHandler implements Runnable {
    private Socket clientSocket;
    private BufferedReader reader;
    private PrintWriter writer;
    private Gson gson;
    private UserDAO userDAO;
    private LeaderboardDAO leaderboardDAO;
    private User currentUser;
    private boolean running;
    private GameServer server;

    // 🔹 DateTimeFormatter cho log
    private static final DateTimeFormatter LOG_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    public ClientHandler(Socket socket, GameServer server) {
        this.clientSocket = socket;
        this.server = server;
        this.gson = new Gson();
        this.userDAO = new UserDAO();
        this.leaderboardDAO = new LeaderboardDAO();
        this.running = true;

        try {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(socket.getOutputStream(), true);

            logWithTime("✓ New client connected: " + socket.getInetAddress());

        } catch (IOException e) {
            logWithTime("✗ Error initializing client handler: " + e.getMessage());
        }
    }

    // 🔹 Log với timestamp
    private void logWithTime(String message) {
        String timestamp = LocalDateTime.now().format(LOG_TIME_FORMAT);
        System.out.println("[" + timestamp + "] " + message);
    }

    @Override
    public void run() {
        logWithTime("🟢 ClientHandler thread STARTED, ID: " + Thread.currentThread().getId());

        try {
            String message;
            int messageCount = 0;

            while (running && (message = reader.readLine()) != null) {
                messageCount++;
                logWithTime("📨 [Handler-" + Thread.currentThread().getId() + "] Message #" + messageCount);
                handleMessage(message);
            }

            logWithTime("🔴 ClientHandler loop ENDED after " + messageCount + " messages");

        } catch (IOException e) {
            logWithTime("✗ Client disconnected: " + e.getMessage());
        } finally {
            handleLogout();
            disconnect();
        }
    }

    private void handleMessage(String message) {
        try {
            logWithTime("🔵 handleMessage() parsing: " + message.substring(0, Math.min(100, message.length())) + "...");

            JsonObject jsonMessage = gson.fromJson(message, JsonObject.class);
            String type = jsonMessage.get("type").getAsString();

            logWithTime("   📦 Type: " + type + " | User: " + (currentUser != null ? currentUser.getUsername() : "anonymous"));

            switch (type) {
                case Protocol.LOGIN:
                    logWithTime("   → Calling handleLogin()");
                    handleLogin(jsonMessage);
                    break;

                case Protocol.REGISTER:
                    logWithTime("   → Calling handleRegister()");
                    handleRegister(jsonMessage);
                    break;

                case Protocol.GET_LEADERBOARD:
                    logWithTime("   → Calling handleGetLeaderboard()");
                    handleGetLeaderboard(jsonMessage);
                    break;

                case Protocol.LOGOUT:
                    logWithTime("   → Calling handleLogout()");
                    handleLogout();
                    break;

                case Protocol.GLOBAL_CHAT:
                    logWithTime("   → Calling handleGlobalChat()");
                    handleGlobalChat(jsonMessage);
                    break;

                case Protocol.GET_PROFILE:
                    logWithTime("   → Calling handleGetProfile()");
                    handleGetProfile(jsonMessage);
                    break;

                case Protocol.UPDATE_PROFILE:
                    logWithTime("   → Calling handleUpdateProfile()");
                    handleUpdateProfile(jsonMessage);
                    break;

                default:
                    logWithTime("   ❓ Unknown type: " + type);
                    sendError("Unknown message type: " + type);
            }

            logWithTime("   ✅ handleMessage() completed for type: " + type);

        } catch (Exception e) {
            logWithTime("❌ Error handling message: " + e.getMessage());
            e.printStackTrace();
            sendError("Invalid message format");
        }
    }

    private void handleUpdateProfile(JsonObject jsonMessage) {
        logWithTime("🔧 UPDATE_PROFILE request received");

        if (currentUser == null) {
            logWithTime("   ❌ User not logged in");
            sendError("Bạn chưa đăng nhập!");
            return;
        }

        int userId = currentUser.getUserId();
        String oldName = currentUser.getFullName();
        String oldAvatar = currentUser.getAvatarUrl();

        String newName = jsonMessage.has("fullName") ? jsonMessage.get("fullName").getAsString() : oldName;
        String newAvatar = jsonMessage.has("avatarUrl") ? jsonMessage.get("avatarUrl").getAsString() : oldAvatar;

        logWithTime("   👤 User: " + currentUser.getUsername() + " (ID: " + userId + ")");
        logWithTime("   📝 Name: \"" + oldName + "\" → \"" + newName + "\"");
        logWithTime("   🖼️ Avatar: \"" + (oldAvatar != null ? oldAvatar.substring(0, Math.min(50, oldAvatar.length())) : "null") + "...\"");
        logWithTime("          → \"" + (newAvatar != null ? newAvatar.substring(0, Math.min(50, newAvatar.length())) : "null") + "...\"");

        boolean success = userDAO.updateUserProfile(userId, newName, newAvatar);

        Map<String, Object> response = new HashMap<>();
        response.put("type", Protocol.UPDATE_PROFILE);
        response.put("success", success);
        response.put("message", success ? "Cập nhật hồ sơ thành công!" : "Không thể cập nhật hồ sơ!");

        if (success) {
            response.put("fullName", newName);
            response.put("avatarUrl", newAvatar);

            // Update current session
            currentUser.setFullName(newName);
            currentUser.setAvatarUrl(newAvatar);

            logWithTime("   ✅ Profile updated successfully");
            logWithTime("      New Name: " + newName);
            logWithTime("      New Avatar: " + (newAvatar != null ? newAvatar.substring(0, Math.min(50, newAvatar.length())) : "null"));
        } else {
            logWithTime("   ❌ Profile update FAILED in database");
        }

        sendMessage(response);
    }

    private void handleGetProfile(JsonObject jsonMessage) {
        logWithTime("🔍 GET_PROFILE request received");

        if (currentUser == null) {
            logWithTime("   ❌ currentUser is NULL!");
            sendError("Bạn chưa đăng nhập!");
            return;
        }

        logWithTime("   👤 User: " + currentUser.getUsername() + " (ID: " + currentUser.getUserId() + ")");

        int userId = currentUser.getUserId();
        User user = userDAO.getUserById(userId);

        if (user == null) {
            logWithTime("   ❌ User not found in database!");
            sendError("Không tìm thấy thông tin người dùng!");
            return;
        }

        logWithTime("   ✅ User loaded from DB:");
        logWithTime("      Name: " + user.getFullName());
        logWithTime("      Avatar: " + (user.getAvatarUrl() != null ? user.getAvatarUrl().substring(0, Math.min(50, user.getAvatarUrl().length())) : "null"));
        logWithTime("      Score: " + user.getTotalScore());
        logWithTime("      Games: " + user.getTotalGames() + " | Wins: " + user.getWins());

        Map<String, Object> response = new HashMap<>();
        response.put("type", Protocol.GET_PROFILE);
        response.put("success", true);
        response.put("userId", user.getUserId());
        response.put("username", user.getUsername());
        response.put("fullName", user.getFullName());
        response.put("email", user.getEmail());
        response.put("avatarUrl", user.getAvatarUrl());
        response.put("totalScore", user.getTotalScore());
        response.put("mathScore", user.getMathScore());
        response.put("englishScore", user.getEnglishScore());
        response.put("literatureScore", user.getLiteratureScore());
        response.put("totalGames", user.getTotalGames());
        response.put("wins", user.getWins());

        sendMessage(response);
        logWithTime("   ✅ GET_PROFILE response sent");
    }

    private void handleLogin(JsonObject jsonMessage) {
        String username = jsonMessage.get("username").getAsString();
        String password = jsonMessage.get("password").getAsString();

        logWithTime("🔐 LOGIN attempt: " + username);

        User user = userDAO.loginUser(username, password);

        if (user != null) {
            currentUser = user;

            logWithTime("   ✅ Login successful");
            logWithTime("      User: " + user.getUsername() + " | Name: " + user.getFullName());
            logWithTime("      Avatar: " + (user.getAvatarUrl() != null ? user.getAvatarUrl().substring(0, Math.min(50, user.getAvatarUrl().length())) : "null"));

            Map<String, Object> response = new HashMap<>();
            response.put("type", Protocol.LOGIN);
            response.put("success", true);
            response.put("userId", user.getUserId());
            response.put("username", user.getUsername());
            response.put("fullName", user.getFullName());
            response.put("email", user.getEmail());
            response.put("avatarUrl", user.getAvatarUrl());
            response.put("totalScore", user.getTotalScore());
            response.put("mathScore", user.getMathScore());
            response.put("englishScore", user.getEnglishScore());
            response.put("literatureScore", user.getLiteratureScore());
            response.put("totalGames", user.getTotalGames());
            response.put("wins", user.getWins());
            response.put("message", "Đăng nhập thành công!");

            sendMessage(response);

        } else {
            logWithTime("   ❌ Login failed: Invalid credentials");

            Map<String, Object> response = new HashMap<>();
            response.put("type", Protocol.LOGIN);
            response.put("success", false);
            response.put("message", "Tên đăng nhập hoặc mật khẩu không đúng!");

            sendMessage(response);
        }
    }

    private void handleRegister(JsonObject jsonMessage) {
        String username = jsonMessage.get("username").getAsString();
        String password = jsonMessage.get("password").getAsString();
        String email = jsonMessage.get("email").getAsString();
        String fullName = jsonMessage.get("fullName").getAsString();
        String age = jsonMessage.get("age").getAsString();
        String avatar = jsonMessage.get("avatar").getAsString();

        logWithTime("📝 REGISTER attempt: " + username);
        logWithTime("   Name: " + fullName + " | Avatar: " + avatar);

        if (userDAO.usernameExists(username)) {
            logWithTime("   ❌ Username already exists");
            Map<String, Object> response = new HashMap<>();
            response.put("type", Protocol.REGISTER);
            response.put("success", false);
            response.put("message", "Tên đăng nhập đã tồn tại!");
            sendMessage(response);
            return;
        }

        if (!email.isEmpty() && userDAO.emailExists(email)) {
            logWithTime("   ❌ Email already exists");
            Map<String, Object> response = new HashMap<>();
            response.put("type", Protocol.REGISTER);
            response.put("success", false);
            response.put("message", "Email đã được sử dụng!");
            sendMessage(response);
            return;
        }

        boolean success = userDAO.registerUser(username, password, email, fullName, age, avatar);

        Map<String, Object> response = new HashMap<>();
        response.put("type", Protocol.REGISTER);
        response.put("success", success);
        response.put("message", success ? "Đăng ký thành công!" : "Đăng ký thất bại!");

        sendMessage(response);

        if (success) {
            logWithTime("   ✅ Registration successful");
        } else {
            logWithTime("   ❌ Registration failed");
        }
    }

    private void handleLogout() {
        try {
            if (currentUser != null) {
                logWithTime("🚪 LOGOUT: " + currentUser.getUsername());
                userDAO.updateOnlineStatus(currentUser.getUserId(), false);
                currentUser = null;
            }

            running = false;

            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
                logWithTime("   🔒 Socket closed");
            }

        } catch (Exception e) {
            logWithTime("   ❌ Error during logout: " + e.getMessage());
        }
    }

    private void handleGetLeaderboard(JsonObject jsonMessage) {
        try {
            String subject = jsonMessage.has("subject") ? jsonMessage.get("subject").getAsString() : "total";
            int limit = jsonMessage.has("limit") ? jsonMessage.get("limit").getAsInt() : 50;

            logWithTime("📊 GET_LEADERBOARD: subject=" + subject + ", limit=" + limit);

            java.util.List<User> leaderboard = leaderboardDAO.getLeaderboardBySubject(subject, limit);

            if (leaderboard == null || leaderboard.isEmpty()) {
                logWithTime("   ⚠️ No users found");
            } else {
                logWithTime("   ✅ Found " + leaderboard.size() + " users");
            }

            Map<String, Object> response = new HashMap<>();
            response.put("type", Protocol.GET_LEADERBOARD);
            response.put("success", true);
            response.put("subject", subject);

            java.util.List<Map<String, Object>> usersData = new java.util.ArrayList<>();

            for (User user : leaderboard) {
                Map<String, Object> u = new HashMap<>();
                u.put("userId", user.getUserId());
                u.put("username", user.getUsername());
                u.put("fullName", user.getFullName());
                u.put("avatarUrl", user.getAvatarUrl());
                u.put("isOnline", user.isOnline());

                int score;
                switch (subject.toLowerCase()) {
                    case "math":
                        score = user.getMathScore();
                        break;
                    case "english":
                        score = user.getEnglishScore();
                        break;
                    case "literature":
                        score = user.getLiteratureScore();
                        break;
                    default:
                        score = user.getTotalScore();
                        break;
                }

                u.put("totalScore", score);
                usersData.add(u);
            }

            response.put("leaderboard", usersData);
            sendMessage(response);

            logWithTime("   ✅ Leaderboard sent");
        } catch (Exception e) {
            logWithTime("   ❌ Error: " + e.getMessage());
            e.printStackTrace();
            sendError("Không thể tải bảng xếp hạng");
        }
    }

    private void handleGlobalChat(JsonObject jsonMessage) {
        if (currentUser == null) {
            sendError("Bạn phải đăng nhập để gửi tin nhắn!");
            return;
        }

        String message = jsonMessage.get("message").getAsString();
        String username = currentUser.getUsername();

        if (message.trim().isEmpty()) {
            return;
        }

        if (message.length() > 500) {
            sendError("Tin nhắn quá dài! (tối đa 500 ký tự)");
            return;
        }

        Map<String, Object> chatMessage = new HashMap<>();
        chatMessage.put("type", "GLOBAL_CHAT");
        chatMessage.put("username", username);
        chatMessage.put("message", message);

        server.broadcastMessage(chatMessage, this);

        logWithTime("💬 CHAT [" + username + "]: " + message.substring(0, Math.min(50, message.length())));
    }

    void sendMessage(Map<String, Object> data) {
        try {
            if (writer != null && !writer.checkError()) {
                String json = gson.toJson(data);
                writer.println(json);
                writer.flush();

                if (!writer.checkError()) {
                    logWithTime("   📤 Response sent: type=" + data.get("type") + ", size=" + json.length() + " bytes");
                } else {
                    logWithTime("   ❌ Writer error after flush");
                }

            } else {
                logWithTime("   ❌ Writer unavailable");
            }
        } catch (Exception e) {
            logWithTime("   ❌ sendMessage error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void sendError(String errorMessage) {
        Map<String, Object> response = new HashMap<>();
        response.put("type", Protocol.ERROR);
        response.put("success", false);
        response.put("message", errorMessage);
        sendMessage(response);
    }

    private void disconnect() {
        try {
            if (currentUser != null) {
                userDAO.updateOnlineStatus(currentUser.getUserId(), false);
            }

            running = false;

            if (reader != null) reader.close();
            if (writer != null) writer.close();
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }

            logWithTime("✓ Client disconnected: " +
                    (currentUser != null ? currentUser.getUsername() : "anonymous"));

        } catch (IOException e) {
            logWithTime("✗ Error disconnecting: " + e.getMessage());
        }
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public boolean isRunning() {
        return running;
    }
}
