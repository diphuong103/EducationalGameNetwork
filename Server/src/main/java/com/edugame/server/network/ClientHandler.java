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
        try {
            String message;
            while (running && (message = reader.readLine()) != null) {
                handleMessage(message);
            }
        } catch (IOException e) {
            System.err.println("✗ Client disconnected: " + e.getMessage());
        } finally {
            disconnect();
        }
    }

    private void handleMessage(String message) {
        try {
            JsonObject jsonMessage = gson.fromJson(message, JsonObject.class);
            String type = jsonMessage.get("type").getAsString();

            System.out.println("📨 Received: " + type + " from " +
                    (currentUser != null ? currentUser.getUsername() : "anonymous"));

            switch (type) {
                case Protocol.LOGIN:
                    handleLogin(jsonMessage);
                    break;

                case Protocol.REGISTER:
                    handleRegister(jsonMessage);
                    break;

                case Protocol.GET_LEADERBOARD:
                    handleGetLeaderboard(jsonMessage);
                    break;

                case Protocol.LOGOUT:
                    handleLogout();
                    break;
                case Protocol.GLOBAL_CHAT:
                    handleGlobalChat(jsonMessage);
                    break;

                default:
                    sendError("Unknown message type: " + type);
            }

        } catch (Exception e) {
            System.err.println("✗ Error handling message: " + e.getMessage());
            sendError("Invalid message format");
        }
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
            response.put("scienceScore", user.getScienceScore());
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
        if (currentUser != null) {
            userDAO.updateOnlineStatus(currentUser.getUserId(), false);
            System.out.println("✓ User logged out: " + currentUser.getUsername());
            currentUser = null;
        }

        running = false;
    }

    private void handleGetLeaderboard(JsonObject jsonMessage) {
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
                writer.println(json);
                writer.flush();
                System.out.println("  ✉️ Sent message type: " + data.get("type")); // Debug
            } else {
                System.err.println("✗ Writer is null or has error");
            }
        } catch (Exception e) {
            System.err.println("✗ Error sending message: " + e.getMessage());
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