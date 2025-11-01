package com.edugame.server.network;

import com.edugame.common.Protocol;
import com.edugame.server.database.FriendDAO;
import com.edugame.server.database.UserDAO;
import com.edugame.server.database.LeaderboardDAO;
import com.edugame.server.database.MessageDAO;

import com.edugame.server.model.Friend;
import com.edugame.server.model.User;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.*;
import java.net.Socket;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClientHandler implements Runnable {
    private Socket clientSocket;
    private BufferedReader reader;
    private PrintWriter writer;
    private Gson gson;
    private UserDAO userDAO;
    private LeaderboardDAO leaderboardDAO;
    private MessageDAO messageDAO;
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
        this.messageDAO = new MessageDAO();
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

                case Protocol.GLOBAL_CHAT:
                    logWithTime("   → Calling handleGlobalChat()");
                    handleGlobalChat(jsonMessage);
                    break;

                case Protocol.GET_MESSAGES:
                    logWithTime("   → Calling handleGetMessages()");
                    handleGetMessages(jsonMessage);
                    break;

                case Protocol.SEND_MESSAGE:
                    logWithTime("   → Calling handleSendMessage()");
                    handleSendMessage(jsonMessage);
                    break;

                case Protocol.MESSAGE_READ:
                    logWithTime("   → Calling handleMarkAsRead()");
                    handleMarkAsRead(jsonMessage);
                    break;

                case Protocol.GET_PROFILE:
                    logWithTime("   → Calling handleGetProfile()");
                    handleGetProfile(jsonMessage);
                    break;

                case Protocol.UPDATE_PROFILE:
                    logWithTime("   → Calling handleUpdateProfile()");
                    handleUpdateProfile(jsonMessage);
                    break;
                case Protocol.SEARCH_USERS:
                    logWithTime("   → Calling handleSearchUsers()");
                    handleSearchUsers(jsonMessage);
                    break;
                case Protocol.ADD_FRIEND:
                    logWithTime("   → Calling handleAddFriend()");
                    handleAddFriend(jsonMessage);
                    break;

                case Protocol.ACCEPT_FRIEND:
                    logWithTime("   → Calling handleAcceptFriend()");
                    handleAcceptFriend(jsonMessage);
                    break;

                case Protocol.REJECT_FRIEND:
                    logWithTime("   → Calling handleRejectFriend()");
                    handleRejectFriend(jsonMessage);
                    break;

                case Protocol.REMOVE_FRIEND:
                    logWithTime("   → Calling handleRemoveFriend()");
                    handleRemoveFriend(jsonMessage);
                    break;

                case Protocol.GET_FRIENDS_LIST:
                    logWithTime("   → Calling handleGetFriendsList()");
                    handleGetFriendsList(jsonMessage);
                    break;

                case Protocol.GET_PENDING_REQUESTS:
                    logWithTime("   → Calling handleGetPendingRequests()");
                    handleGetPendingRequests(jsonMessage);
                    break;

                case Protocol.LOGOUT:
                    logWithTime("   → Calling handleLogout()");
                    handleLogout();
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


    private void handleSearchUsers(JsonObject jsonMessage) {
        logWithTime("🔍 SEARCH_USERS request received");

        if (currentUser == null) {
            logWithTime("   ❌ User not logged in");
            sendError("Bạn chưa đăng nhập!");
            return;
        }

        String searchQuery = jsonMessage.has("query") ? jsonMessage.get("query").getAsString() : "";
        int limit = jsonMessage.has("limit") ? jsonMessage.get("limit").getAsInt() : 20;

        logWithTime("   🔎 Query: \"" + searchQuery + "\" | Limit: " + limit);
        logWithTime("   👤 Searching for user: " + currentUser.getUsername());

        if (searchQuery.trim().isEmpty()) {
            logWithTime("   ❌ Empty search query");
            Map<String, Object> response = new HashMap<>();
            response.put("type", Protocol.SEARCH_USERS);
            response.put("success", false);
            response.put("message", "Vui lòng nhập tên để tìm kiếm!");
            sendMessage(response);
            return;
        }

        FriendDAO friendDAO = new FriendDAO();
        java.util.List<User> users = friendDAO.searchUsers(searchQuery, currentUser.getUserId(), limit);

        logWithTime("   ✅ Found " + users.size() + " users");

        Map<String, Object> response = new HashMap<>();
        response.put("type", Protocol.SEARCH_USERS);
        response.put("success", true);
        response.put("query", searchQuery);

        java.util.List<Map<String, Object>> usersData = new java.util.ArrayList<>();

        for (User user : users) {
            Map<String, Object> userData = new HashMap<>();
            userData.put("userId", user.getUserId());
            userData.put("username", user.getUsername());
            userData.put("fullName", user.getFullName());
            userData.put("email", user.getEmail());
            userData.put("age", user.getAge());
            userData.put("avatarUrl", user.getAvatarUrl());
            userData.put("totalScore", user.getTotalScore());
            userData.put("mathScore", user.getMathScore());
            userData.put("englishScore", user.getEnglishScore());
            userData.put("literatureScore", user.getLiteratureScore());
            userData.put("totalGames", user.getTotalGames());
            userData.put("wins", user.getWins());
            userData.put("isOnline", user.isOnline());

            // Lấy trạng thái bạn bè
            String friendshipStatus = friendDAO.getFriendshipStatus(currentUser.getUserId(), user.getUserId());
            userData.put("friendshipStatus", friendshipStatus);

            usersData.add(userData);
        }

        response.put("users", usersData);
        sendMessage(response);

        logWithTime("   ✅ Search results sent");
    }

    /**
     * Gửi lời mời kết bạn
     */
    private void handleAddFriend(JsonObject jsonMessage) {
        logWithTime("🤝 ADD_FRIEND request received");

        if (currentUser == null) {
            logWithTime("   ❌ User not logged in");
            sendError("Bạn chưa đăng nhập!");
            return;
        }

        int targetUserId = jsonMessage.get("targetUserId").getAsInt();
        int currentUserId = currentUser.getUserId();

        logWithTime("   👤 From: " + currentUser.getUsername() + " (ID: " + currentUserId + ")");
        logWithTime("   👤 To: User ID " + targetUserId);

        // Kiểm tra không thể kết bạn với chính mình
        if (currentUserId == targetUserId) {
            logWithTime("   ❌ Cannot add yourself as friend");
            Map<String, Object> response = new HashMap<>();
            response.put("type", Protocol.ADD_FRIEND);
            response.put("success", false);
            response.put("message", "Bạn không thể kết bạn với chính mình!");
            sendMessage(response);
            return;
        }

        FriendDAO friendDAO = new FriendDAO();

        // Kiểm tra đã là bạn chưa
        if (friendDAO.isFriend(currentUserId, targetUserId)) {
            logWithTime("   ⚠️ Already friends");
            Map<String, Object> response = new HashMap<>();
            response.put("type", Protocol.ADD_FRIEND);
            response.put("success", false);
            response.put("message", "Các bạn đã là bạn bè rồi!");
            sendMessage(response);
            return;
        }

        // Kiểm tra đã có lời mời chưa
        if (friendDAO.hasPendingRequest(currentUserId, targetUserId)) {
            logWithTime("   ⚠️ Pending request already exists");
            Map<String, Object> response = new HashMap<>();
            response.put("type", Protocol.ADD_FRIEND);
            response.put("success", false);
            response.put("message", "Đã có lời mời kết bạn đang chờ xử lý!");
            sendMessage(response);
            return;
        }

        // Gửi lời mời
        boolean success = friendDAO.sendFriendRequest(currentUserId, targetUserId);

        Map<String, Object> response = new HashMap<>();
        response.put("type", Protocol.ADD_FRIEND);
        response.put("success", success);
        response.put("message", success ? "Đã gửi lời mời kết bạn!" : "Không thể gửi lời mời kết bạn!");
        response.put("targetUserId", targetUserId);

        sendMessage(response);

        if (success) {
            logWithTime("   ✅ Friend request sent successfully");
        } else {
            logWithTime("   ❌ Failed to send friend request");
        }
    }

    /**
     * Chấp nhận lời mời kết bạn
     */
    private void handleAcceptFriend(JsonObject jsonMessage) {
        logWithTime("✅ ACCEPT_FRIEND request received");

        if (currentUser == null) {
            logWithTime("   ❌ User not logged in");
            sendError("Bạn chưa đăng nhập!");
            return;
        }

        int friendId = jsonMessage.get("friendId").getAsInt();
        int currentUserId = currentUser.getUserId();

        logWithTime("   👤 User: " + currentUser.getUsername() + " (ID: " + currentUserId + ")");
        logWithTime("   👤 Accepting request from: User ID " + friendId);

        FriendDAO friendDAO = new FriendDAO();
        boolean success = friendDAO.acceptFriendRequest(currentUserId, friendId);

        Map<String, Object> response = new HashMap<>();
        response.put("type", Protocol.ACCEPT_FRIEND);
        response.put("success", success);
        response.put("message", success ? "Đã chấp nhận lời mời kết bạn!" : "Không thể chấp nhận lời mời!");
        response.put("friendId", friendId);

        sendMessage(response);

        if (success) {
            logWithTime("   ✅ Friend request accepted");
        } else {
            logWithTime("   ❌ Failed to accept friend request");
        }
    }

    /**
     * Từ chối lời mời kết bạn
     */
    private void handleRejectFriend(JsonObject jsonMessage) {
        logWithTime("❌ REJECT_FRIEND request received");

        if (currentUser == null) {
            logWithTime("   ❌ User not logged in");
            sendError("Bạn chưa đăng nhập!");
            return;
        }

        int friendId = jsonMessage.get("friendId").getAsInt();
        int currentUserId = currentUser.getUserId();

        logWithTime("   👤 User: " + currentUser.getUsername() + " (ID: " + currentUserId + ")");
        logWithTime("   👤 Rejecting request from: User ID " + friendId);

        FriendDAO friendDAO = new FriendDAO();
        boolean success = friendDAO.rejectFriendRequest(currentUserId, friendId);

        Map<String, Object> response = new HashMap<>();
        response.put("type", Protocol.REJECT_FRIEND);
        response.put("success", success);
        response.put("message", success ? "Đã từ chối lời mời kết bạn!" : "Không thể từ chối lời mời!");
        response.put("friendId", friendId);

        sendMessage(response);

        if (success) {
            logWithTime("   ✅ Friend request rejected");
        } else {
            logWithTime("   ❌ Failed to reject friend request");
        }
    }

    /**
     * Xóa bạn bè
     */
    private void handleRemoveFriend(JsonObject jsonMessage) {
        logWithTime("🗑️ REMOVE_FRIEND request received");

        if (currentUser == null) {
            logWithTime("   ❌ User not logged in");
            sendError("Bạn chưa đăng nhập!");
            return;
        }

        int friendId = jsonMessage.get("friendId").getAsInt();
        int currentUserId = currentUser.getUserId();

        logWithTime("   👤 User: " + currentUser.getUsername() + " (ID: " + currentUserId + ")");
        logWithTime("   👤 Removing friend: User ID " + friendId);

        FriendDAO friendDAO = new FriendDAO();
        boolean success = friendDAO.removeFriend(currentUserId, friendId);

        Map<String, Object> response = new HashMap<>();
        response.put("type", Protocol.REMOVE_FRIEND);
        response.put("success", success);
        response.put("message", success ? "Đã xóa bạn bè!" : "Không thể xóa bạn bè!");
        response.put("friendId", friendId);

        sendMessage(response);

        if (success) {
            logWithTime("   ✅ Friend removed successfully");
        } else {
            logWithTime("   ❌ Failed to remove friend");
        }
    }

    /**
     * Lấy danh sách bạn bè
     */
    private void handleGetFriendsList(JsonObject jsonMessage) {
        logWithTime("📋 GET_FRIENDS_LIST request received");

        if (currentUser == null) {
            logWithTime("   ❌ User not logged in");
            sendError("Bạn chưa đăng nhập!");
            return;
        }

        int currentUserId = currentUser.getUserId();
        logWithTime("   👤 User: " + currentUser.getUsername() + " (ID: " + currentUserId + ")");

        FriendDAO friendDAO = new FriendDAO();
        List<Friend> friends = friendDAO.getFriendsList(currentUserId);

        logWithTime("   ✅ Found " + friends.size() + " friends");

        Map<String, Object> response = new HashMap<>();
        response.put("type", Protocol.GET_FRIENDS_LIST);
        response.put("success", true);

        List<Map<String, Object>> friendsData = new ArrayList<>();

        for (Friend friend : friends) {
            Map<String, Object> friendData = new HashMap<>();
            friendData.put("friendshipId", friend.getFriendshipId());
            friendData.put("userId", friend.getUserId() == currentUserId ? friend.getFriendId() : friend.getUserId());
            friendData.put("username", friend.getUsername());
            friendData.put("fullName", friend.getFullName());
            friendData.put("avatarUrl", friend.getAvatarUrl());
            friendData.put("totalScore", friend.getTotalScore());
            friendData.put("isOnline", friend.isOnline());
            friendData.put("createdAt", friend.getCreatedAt().toString());

            friendsData.add(friendData);
        }

        response.put("friends", friendsData);
        response.put("count", friends.size());

        sendMessage(response);
        logWithTime("   ✅ Friends list sent");
    }

    /**
     * Lấy danh sách lời mời kết bạn đang chờ
     */
    private void handleGetPendingRequests(JsonObject jsonMessage) {
        logWithTime("📬 GET_PENDING_REQUESTS request received");

        if (currentUser == null) {
            logWithTime("   ❌ User not logged in");
            sendError("Bạn chưa đăng nhập!");
            return;
        }

        int currentUserId = currentUser.getUserId();
        logWithTime("   👤 User: " + currentUser.getUsername() + " (ID: " + currentUserId + ")");

        FriendDAO friendDAO = new FriendDAO();
        List<Friend> requests = friendDAO.getPendingRequests(currentUserId);

        logWithTime("   ✅ Found " + requests.size() + " pending requests");

        Map<String, Object> response = new HashMap<>();
        response.put("type", Protocol.GET_PENDING_REQUESTS);
        response.put("success", true);

        List<Map<String, Object>> requestsData = new ArrayList<>();

        for (Friend request : requests) {
            Map<String, Object> requestData = new HashMap<>();
            requestData.put("friendshipId", request.getFriendshipId());
            requestData.put("userId", request.getUserId());
            requestData.put("username", request.getUsername());
            requestData.put("fullName", request.getFullName());
            requestData.put("avatarUrl", request.getAvatarUrl());
            requestData.put("totalScore", request.getTotalScore());
            requestData.put("isOnline", request.isOnline());
            requestData.put("createdAt", request.getCreatedAt().toString());

            requestsData.add(requestData);
        }

        response.put("requests", requestsData);
        response.put("count", requests.size());

        sendMessage(response);
        logWithTime("   ✅ Pending requests sent");
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
        response.put("age", user.getAge());
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
            response.put("age", user.getAge());
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

    /// ////////////Chat /////////////
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

    /**
     * Handle GET_MESSAGES - Lấy lịch sử chat
     */
    private void handleGetMessages(JsonObject jsonMessage) {
        logWithTime("💬 GET_MESSAGES request received");

        if (currentUser == null) {
            logWithTime("   ❌ User not logged in");
            sendError("Bạn chưa đăng nhập!");
            return;
        }

        int friendId = jsonMessage.get("friendId").getAsInt();
        int limit = jsonMessage.get("limit").getAsInt();
        int currentUserId = currentUser.getUserId();

        logWithTime("   👤 User: " + currentUser.getUsername() + " (ID: " + currentUserId + ")");
        logWithTime("   👤 Getting messages with: User ID " + friendId);
        logWithTime("   📊 Limit: " + limit);

        List<com.edugame.server.model.Message> messages = messageDAO.getMessages(currentUserId, friendId, limit);

        logWithTime("   ✅ Found " + messages.size() + " messages");

        Map<String, Object> response = new HashMap<>();
        response.put("type", Protocol.GET_MESSAGES);
        response.put("success", true);

        List<Map<String, Object>> messagesData = new ArrayList<>();

        for (com.edugame.server.model.Message msg : messages) {
            Map<String, Object> msgData = new HashMap<>();
            msgData.put("messageId", msg.getMessageId());
            msgData.put("senderId", msg.getSenderId());
            msgData.put("receiverId", msg.getReceiverId());
            msgData.put("content", msg.getContent());
            msgData.put("sentAt", msg.getSentAt().toString());
            msgData.put("isRead", msg.isRead());

            if (msg.getSenderUsername() != null) {
                msgData.put("senderName", msg.getSenderName());
                msgData.put("senderAvatar", msg.getSenderAvatar());
            }

            messagesData.add(msgData);
        }

        response.put("messages", messagesData);
        sendMessage(response);

        logWithTime("   ✅ Messages sent");
    }

    /**
     * Handle SEND_MESSAGE - Gửi tin nhắn
     */
    private void handleSendMessage(JsonObject jsonMessage) {
        logWithTime("💬 SEND_MESSAGE request received");

        if (currentUser == null) {
            logWithTime("   ❌ User not logged in");
            sendError("Bạn chưa đăng nhập!");
            return;
        }

        int receiverId = jsonMessage.get("receiverId").getAsInt();
        String content = jsonMessage.get("content").getAsString();
        int senderId = currentUser.getUserId();

        logWithTime("   👤 From: " + currentUser.getUsername() + " (ID: " + senderId + ")");
        logWithTime("   👤 To: User ID " + receiverId);
        logWithTime("   💬 Content: " + content.substring(0, Math.min(50, content.length())));

        // Validate
        if (content.trim().isEmpty()) {
            logWithTime("   ❌ Empty message");
            Map<String, Object> response = new HashMap<>();
            response.put("type", Protocol.SEND_MESSAGE);
            response.put("success", false);
            response.put("message", "Tin nhắn không được để trống!");
            sendMessage(response);
            return;
        }

        if (content.length() > 1000) {
            logWithTime("   ❌ Message too long");
            Map<String, Object> response = new HashMap<>();
            response.put("type", Protocol.SEND_MESSAGE);
            response.put("success", false);
            response.put("message", "Tin nhắn quá dài (tối đa 1000 ký tự)!");
            sendMessage(response);
            return;
        }

        // ✅ 1. Save to database
        com.edugame.server.model.Message savedMessage = messageDAO.sendMessage(senderId, receiverId, content);

        if (savedMessage != null) {
            logWithTime("   ✅ Message saved to database (ID=" + savedMessage.getMessageId() + ")");

            // ✅ 2. Send success response to SENDER
            Map<String, Object> response = new HashMap<>();
            response.put("type", Protocol.SEND_MESSAGE);
            response.put("success", true);
            response.put("messageId", savedMessage.getMessageId());
            response.put("sentAt", savedMessage.getSentAt().toString());
            sendMessage(response);
            logWithTime("   ✅ SUCCESS response sent to SENDER");

            // ✅ 3. Get sender info
            User senderUser = userDAO.getUserById(senderId);
            String senderName = (senderUser != null) ? senderUser.getFullName() : currentUser.getFullName();

            logWithTime("   📝 Sender name: " + senderName);

            // ✅ 4. Create NEW_MESSAGE notification
            Map<String, Object> newMessageNotification = new HashMap<>();
            newMessageNotification.put("type", Protocol.NEW_MESSAGE);
            newMessageNotification.put("messageId", savedMessage.getMessageId());
            newMessageNotification.put("senderId", senderId);
            newMessageNotification.put("senderName", senderName);
            newMessageNotification.put("content", content);
            newMessageNotification.put("sentAt", savedMessage.getSentAt().toString());

            logWithTime("   📤 Attempting to send NEW_MESSAGE to receiverId=" + receiverId);

            // ✅ 5. Send to receiver by USER_ID (QUAN TRỌNG - KHÔNG dùng String.valueOf)
            boolean sentToReceiver = server.sendToUserId(receiverId, newMessageNotification);

            if (sentToReceiver) {
                logWithTime("   ✅✅✅ NEW_MESSAGE sent successfully to receiverId=" + receiverId);
            } else {
                logWithTime("   ⚠️⚠️⚠️ RECEIVER OFFLINE or NOT FOUND (userId=" + receiverId + ")");
            }

        } else {
            logWithTime("   ❌ Failed to save message");
            Map<String, Object> response = new HashMap<>();
            response.put("type", Protocol.SEND_MESSAGE);
            response.put("success", false);
            response.put("message", "Không thể gửi tin nhắn!");
            sendMessage(response);
        }
    }

    /**
     * Handle MESSAGE_READ - Đánh dấu đã đọc
     */
    private void handleMarkAsRead(JsonObject jsonMessage) {
        logWithTime("✓ MESSAGE_READ request received");

        if (currentUser == null) {
            logWithTime("   ❌ User not logged in");
            return;
        }

        int senderId = jsonMessage.get("senderId").getAsInt();
        int currentUserId = currentUser.getUserId();

        logWithTime("   👤 User: " + currentUser.getUsername() + " (ID: " + currentUserId + ")");
        logWithTime("   📨 Marking messages from: User ID " + senderId);

        int updatedCount = messageDAO.markAllAsRead(currentUserId, senderId);

        logWithTime("   ✅ Marked " + updatedCount + " messages as read");

        Map<String, Object> response = new HashMap<>();
        response.put("type", Protocol.MESSAGE_READ);
        response.put("success", true);
        response.put("updatedCount", updatedCount);
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
