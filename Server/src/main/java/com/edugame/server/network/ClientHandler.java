package com.edugame.server.network;

import com.edugame.common.Protocol;
import com.edugame.server.database.UserDAO;
import com.edugame.server.database.LeaderboardDAO;
import com.edugame.server.model.User;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.*;
import java.net.Socket;
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

            System.out.println("✓ New client connected: " + socket.getInetAddress());

        } catch (IOException e) {
            System.err.println("✗ Error initializing client handler: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        System.out.println("🟢 ClientHandler thread STARTED, ID: " + Thread.currentThread().getId());

        try {
            String message;
            int messageCount = 0;

            while (running && (message = reader.readLine()) != null) {
                messageCount++;
                System.out.println("📨 [Handler-" + Thread.currentThread().getId() + "] Message #" + messageCount);
                handleMessage(message);
            }

            System.out.println("🔴 ClientHandler loop ENDED after " + messageCount + " messages");

        } catch (IOException e) {
            System.err.println("✗ Client disconnected: " + e.getMessage());
        } finally {
            handleLogout();
            disconnect();
        }
    }

    private void handleMessage(String message) {
        try {
            System.out.println("🔵 handleMessage() parsing: " + message.substring(0, Math.min(100, message.length())) + "...");

            JsonObject jsonMessage = gson.fromJson(message, JsonObject.class);
            String type = jsonMessage.get("type").getAsString();

            System.out.println("   📦 Type parsed: " + type);
            System.out.println("   👤 Current user: " + (currentUser != null ? currentUser.getUsername() : "null"));

            switch (type) {
                case Protocol.LOGIN:
                    System.out.println("   → Calling handleLogin()");
                    handleLogin(jsonMessage);
                    break;

                case Protocol.REGISTER:
                    System.out.println("   → Calling handleRegister()");
                    handleRegister(jsonMessage);
                    break;

                case Protocol.GET_LEADERBOARD:
                    System.out.println("   → Calling handleGetLeaderboard()");
                    handleGetLeaderboard(jsonMessage);
                    break;

                case Protocol.LOGOUT:
                    System.out.println("   → Calling handleLogout()");
                    handleLogout();
                    break;

                case Protocol.GLOBAL_CHAT:
                    System.out.println("   → Calling handleGlobalChat()");
                    handleGlobalChat(jsonMessage);
                    break;

                case Protocol.GET_PROFILE:
                    System.out.println("   → Calling handleGetProfile()");
                    handleGetProfile(jsonMessage);
                    break;

                default:
                    System.out.println("   ❓ Unknown type: " + type);
                    sendError("Unknown message type: " + type);
            }

            System.out.println("   ✅ handleMessage() completed for type: " + type);

        } catch (Exception e) {
            System.err.println("❌ Error handling message: " + e.getMessage());
            e.printStackTrace();
            sendError("Invalid message format");
        }
    }
    private void handleGetProfile(JsonObject jsonMessage) {
        System.out.println("🔵 handleGetProfile() CALLED");

        if (currentUser == null) {
            System.err.println("  ❌ currentUser is NULL!");
            sendError("Bạn chưa đăng nhập!");
            return;
        }

        System.out.println("  ✅ currentUser exists: " + currentUser.getUsername());
        int userId = currentUser.getUserId();

        System.out.println("  🔍 Getting user from database, userId=" + userId);
        User user = userDAO.getUserById(userId);

        if (user == null) {
            System.err.println("  ❌ User not found in database!");
            sendError("Không tìm thấy thông tin người dùng!");
            return;
        }

        System.out.println("  ✅ User loaded from DB: " + user.getUsername());

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

        System.out.println("  📦 Response prepared with " + response.size() + " fields");
        System.out.println("  📤 Sending profile JSON: " + gson.toJson(response));

        sendMessage(response);

        System.out.println("  ✅ handleGetProfile() COMPLETED");
    }


    private void handleLogin(JsonObject jsonMessage) {
        String username = jsonMessage.get("username").getAsString();
        String password = jsonMessage.get("password").getAsString();

        // Validate credentials
        User user = userDAO.loginUser(username, password);

        if (user != null) {
            currentUser = user;

            // Send success response
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

            System.out.println("✓ Login successful: " + username);

        } else {
            // Send failure response
            Map<String, Object> response = new HashMap<>();
            response.put("type", Protocol.LOGIN);
            response.put("success", false);
            response.put("message", "Tên đăng nhập hoặc mật khẩu không đúng!");

            sendMessage(response);

            System.out.println("✗ Login failed: " + username);
        }
    }

    private void handleRegister(JsonObject jsonMessage) {
        String username = jsonMessage.get("username").getAsString();
        String password = jsonMessage.get("password").getAsString();
        String email = jsonMessage.get("email").getAsString();
        String fullName = jsonMessage.get("fullName").getAsString();
        String age = jsonMessage.get("age").getAsString();
        String avatar = jsonMessage.get("avatar").getAsString();

        // Check if username already exists
        if (userDAO.usernameExists(username)) {
            Map<String, Object> response = new HashMap<>();
            response.put("type", Protocol.REGISTER);
            response.put("success", false);
            response.put("message", "Tên đăng nhập đã tồn tại!");

            sendMessage(response);
            System.out.println("✗ Registration failed: Username exists - " + username);
            return;
        }

        // Check if email already exists (if provided)
        if (!email.isEmpty() && userDAO.emailExists(email)) {
            Map<String, Object> response = new HashMap<>();
            response.put("type", Protocol.REGISTER);
            response.put("success", false);
            response.put("message", "Email đã được sử dụng!");

            sendMessage(response);
            System.out.println("✗ Registration failed: Email exists - " + email);
            return;
        }

        // Register user
        boolean success = userDAO.registerUser(username, password, email, fullName, age, avatar);

        Map<String, Object> response = new HashMap<>();
        response.put("type", Protocol.REGISTER);
        response.put("success", success);
        response.put("message", success ? "Đăng ký thành công!" : "Đăng ký thất bại!");

        sendMessage(response);

        if (success) {
            System.out.println("✓ Registration successful: " + username);
        } else {
            System.out.println("✗ Registration failed: " + username);
        }
    }

    private void handleLogout() {
        try {
            if (currentUser != null) {
                userDAO.updateOnlineStatus(currentUser.getUserId(), false);
                System.out.println("✓ User logged out: " + currentUser.getUsername());
                currentUser = null;
            }

            running = false;

            // 🔒 Đóng socket và streams (rất quan trọng)
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
                System.out.println("🔒 Server socket closed for client");
            }


        } catch (Exception e) {
            System.err.println("❌ Error during logout: " + e.getMessage());
        }
    }


    private void handleGetLeaderboard(JsonObject jsonMessage) {
        try {
            String subject = jsonMessage.has("subject") ? jsonMessage.get("subject").getAsString() : "total";
            int limit = jsonMessage.has("limit") ? jsonMessage.get("limit").getAsInt() : 50;

            System.out.println("📊 Getting leaderboard - Subject: " + subject + ", Limit: " + limit);

            // Lấy danh sách từ DB
            java.util.List<User> leaderboard = leaderboardDAO.getLeaderboardBySubject(subject, limit);

            // Kiểm tra danh sách rỗng
            if (leaderboard == null || leaderboard.isEmpty()) {
                System.err.println("⚠️ No users found in leaderboard for subject: " + subject);
            }

            // Chuẩn bị phản hồi JSON
            Map<String, Object> response = new HashMap<>();
            response.put("type", Protocol.GET_LEADERBOARD);
            response.put("success", true);
            response.put("subject", subject);

            java.util.List<Map<String, Object>> usersData = new java.util.ArrayList<>();

            int index = 0;
            for (User user : leaderboard) {
                index++;
                Map<String, Object> u = new HashMap<>();
                u.put("userId", user.getUserId());
                u.put("username", user.getUsername());
                u.put("fullName", user.getFullName());
                u.put("avatarUrl", user.getAvatarUrl());
                u.put("isOnline", user.isOnline());

                // ✅ Lấy điểm đúng theo môn
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

                // 🔍 Log chi tiết từng user
                System.out.printf("   #%d %s | math=%d, eng=%d, lit=%d, total=%d, isOnline=%s%n",
                        index, user.getUsername(),
                        user.getMathScore(), user.getEnglishScore(),
                        user.getLiteratureScore(), user.getTotalScore(),
                        user.isOnline());
            }

            response.put("leaderboard", usersData);
            sendMessage(response);

            System.out.println("✅ Sent leaderboard (" + subject + ", top " + usersData.size() + ")");
        } catch (Exception e) {
            System.err.println("❌ Error handling leaderboard: " + e.getMessage());
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

        // Validate message
        if (message.trim().isEmpty()) {
            return;
        }

        if (message.length() > 500) {
            sendError("Tin nhắn quá dài! (tối đa 500 ký tự)");
            return;
        }

        // Broadcast to all clients except sender
        Map<String, Object> chatMessage = new HashMap<>();
        chatMessage.put("type", "GLOBAL_CHAT");
        chatMessage.put("username", username);
        chatMessage.put("message", message);

        server.broadcastMessage(chatMessage, this);

        System.out.println("💬 Global chat [" + username + "]: " + message);
    }


    void sendMessage(Map<String, Object> data) {
        try {
            if (writer != null && !writer.checkError()) {
                String json = gson.toJson(data);

                System.out.println("  📤 sendMessage() called:");
                System.out.println("     Type: " + data.get("type"));
                System.out.println("     JSON length: " + json.length());
                System.out.println("     First 200 chars: " + json.substring(0, Math.min(200, json.length())));

                writer.println(json);
                writer.flush();

                if (writer.checkError()) {
                    System.err.println("  ❌ Writer has error after flush!");
                } else {
                    System.out.println("  ✅ Message flushed successfully");
                }

                if (clientSocket.isClosed() || !clientSocket.isConnected()) {
                    System.err.println("  ⚠️ Socket is closed or disconnected!");
                } else {
                    System.out.println("  ✅ Socket still alive");
                }

            } else {
                System.err.println("  ❌ Writer is null or has error before sending");
            }
        } catch (Exception e) {
            System.err.println("  ❌ Error in sendMessage: " + e.getMessage());
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
            // Update user status to offline
            if (currentUser != null) {
                userDAO.updateOnlineStatus(currentUser.getUserId(), false);
            }

            running = false;

            if (reader != null) reader.close();
            if (writer != null) writer.close();
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
            }

            System.out.println("✓ Client disconnected: " +
                    (currentUser != null ? currentUser.getUsername() : "anonymous"));

        } catch (IOException e) {
            System.err.println("✗ Error disconnecting client: " + e.getMessage());
        }
    }

    public User getCurrentUser() {
        return currentUser;
    }

    public boolean isRunning() {
        return running;
    }
}