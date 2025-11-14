package com.edugame.server.network;

import com.edugame.common.Protocol;
import com.edugame.server.database.*;

import com.edugame.server.game.GameManager;
import com.edugame.server.game.GameRoomManager;
import com.edugame.server.game.MatchmakingManager;
import com.edugame.server.model.*;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;

import java.io.*;
import java.net.Socket;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static org.apache.poi.poifs.crypt.CryptoFunctions.hashPassword;

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
    private MatchmakingManager matchmakingManager;
    private QuestionDAO questionDAO;
    private static final GameRoomManager gameRoomManager = GameRoomManager.getInstance();
    private GameManager gameManager = GameManager.getInstance();

    public void setMatchmakingManager(MatchmakingManager matchmakingManager) {
        this.matchmakingManager = matchmakingManager;
    }

    // 🔹 DateTimeFormatter cho log
    private static final DateTimeFormatter LOG_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    public ClientHandler(Socket socket, GameServer server) throws SQLException {
        this.clientSocket = socket;
        this.server = server;
        this.gson = new Gson();
        this.userDAO = new UserDAO();
        this.leaderboardDAO = new LeaderboardDAO();
        this.messageDAO = new MessageDAO();
        this.running = true;
        this.questionDAO = new QuestionDAO();

        try {
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(socket.getOutputStream(), true);

            logWithTime("✓ New client connected: " + socket.getInetAddress());

        } catch (IOException e) {
            logWithTime("✗ Error initializing client handler: " + e.getMessage());
        }
    }

    /**
     * Helper method to get GameServer instance
     */
    private GameServer getGameServer() {
        return GameServer.getInstance();
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

                case Protocol.FIND_MATCH:
                    logWithTime("   → Calling handleFindMatch()");
                    handleFindMatch(jsonMessage);
                    break;

                case Protocol.CANCEL_FIND_MATCH:
                    logWithTime("   → Calling handleCancelFindMatch()");
                    handleCancelFindMatch(jsonMessage);
                    break;

                case Protocol.GLOBAL_CHAT:
                    logWithTime("   → Calling handleGlobalChat()");
                    handleGlobalChat(jsonMessage);
                    break;
                case Protocol.ROOM_CHAT:
                    logWithTime("   → Calling handleRoomChat()");
                    handleRoomChat(jsonMessage);
                    break;
                case Protocol.GAME_CHAT:
                    logWithTime("   → Calling handleGameChat()");
                    handleGameChat(jsonMessage);
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

                case "GET_PROFILE_BY_ID":
                    logWithTime("   → Calling handleGetProfileById()");
                    handleGetProfileById(jsonMessage);
                    break;

                case Protocol.UPDATE_PROFILE:
                    logWithTime("   → Calling handleUpdateProfile()");
                    handleUpdateProfile(jsonMessage);
                    break;
                case Protocol.SEARCH_USERS:
                    logWithTime("   → Calling handleSearchUsers()");
                    handleSearchUsers(jsonMessage);
                    break;
                case Protocol.CHECK_FRIENDSHIP_STATUS:
                    logWithTime("   → Calling handleCheckFriendshipStatus()");
                    handleCheckFriendshipStatus(jsonMessage);
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

                case Protocol.UPDATE_PASSWORD:
                    logWithTime("   → Calling handleUpdatePassword()");
                    handleUpdatePassword(jsonMessage);
                    break;

                case Protocol.GET_STATISTICS:
                    logWithTime("   → Calling handleGetStatistics()");
                    handleGetStatistics(jsonMessage);
                    break;

                case Protocol.GET_TRAINING_QUESTIONS:
                    logWithTime("   → Calling handleGetTrainingQuestions()");
                    handleGetTrainingQuestions(jsonMessage);
                    break;

                case Protocol.CREATE_ROOM:
                    logWithTime("   → Calling handleGetTrainingQuestions()");
                    handleCreateRoom(jsonMessage);
                    break;

                case Protocol.JOIN_ROOM:
                    logWithTime("   → Calling handleJoinRoom()");
                    handleJoinRoom(jsonMessage);
                    break;

                case Protocol.JOIN_ROOM_RESPONSE:
                    logWithTime("   → Calling handleJoinRoom()");
                    handleJoinRoom(jsonMessage);
                    break;

                case Protocol.LEAVE_ROOM:
                    logWithTime("   → Calling handleLeaveRoom()");
                    handleLeaveRoom(jsonMessage);
                    break;
                case Protocol.KICK_PLAYER:
                    logWithTime("   → Calling handleKickPlayer()");
                    handleKickPlayer(jsonMessage);
                    break;

                case Protocol.READY:
                    logWithTime("   → Calling handlePlayerReady()");
                    handlePlayerReady(jsonMessage);
                    break;
                case Protocol.START_GAME:
                    logWithTime("   → Calling handlePlayerReady()");
                    handleStartGame(jsonMessage);
                    break;

                case Protocol.SUBMIT_ANSWER:
                    logWithTime("   → Calling handleSubmitAnswer()");
                    handleSubmitAnswer(jsonMessage);
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

    private void handleGameChat(JsonObject jsonMessage) {
    }


    private void handleGetProfileById(JsonObject jsonMessage) {
        logWithTime("👤 GET_PROFILE_BY_ID request received");

        if (currentUser == null) {
            logWithTime("   ❌ User not logged in");
            sendError("Bạn chưa đăng nhập!");
            return;
        }

        if (!jsonMessage.has("userId")) {
            logWithTime("   ❌ Missing userId parameter");
            sendError("Thiếu thông tin userId!");
            return;
        }

        int targetUserId = jsonMessage.get("userId").getAsInt();
        logWithTime("   👤 Requesting user: " + currentUser.getUsername() + " (ID: " + currentUser.getUserId() + ")");
        logWithTime("   🎯 Target user ID: " + targetUserId);

        User targetUser = userDAO.getUserById(targetUserId);

        Map<String, Object> response = new HashMap<>();
        response.put("type", "GET_PROFILE_BY_ID");  // ✅ THAY ĐỔI: Giữ nguyên tên

        if (targetUser == null) {
            logWithTime("   ❌ Target user not found in database");
            response.put("success", false);
            response.put("message", "Không tìm thấy người dùng!");
            sendMessage(response);
            return;
        }

        logWithTime("   ✅ User loaded from DB:");
        logWithTime("      Name: " + targetUser.getFullName());
        logWithTime("      Username: " + targetUser.getUsername());
        logWithTime("      Avatar: " + (targetUser.getAvatarUrl() != null ?
                targetUser.getAvatarUrl().substring(0, Math.min(50, targetUser.getAvatarUrl().length())) : "null"));
        logWithTime("      Total Score: " + targetUser.getTotalScore());

        Map<String, Object> userData = new HashMap<>();
        userData.put("userId", targetUser.getUserId());
        userData.put("username", targetUser.getUsername());
        userData.put("fullName", targetUser.getFullName());
        userData.put("age", targetUser.getAge());
        userData.put("email", targetUser.getEmail());
        userData.put("avatarUrl", targetUser.getAvatarUrl());
        userData.put("totalScore", targetUser.getTotalScore());
        userData.put("mathScore", targetUser.getMathScore());
        userData.put("englishScore", targetUser.getEnglishScore());
        userData.put("literatureScore", targetUser.getLiteratureScore());
        userData.put("totalGames", targetUser.getTotalGames());
        userData.put("wins", targetUser.getWins());
        userData.put("isOnline", targetUser.isOnline());

        response.put("success", true);
        response.put("user", userData);

        sendMessage(response);
        logWithTime("   ✅ GET_PROFILE_BY_ID response sent successfully");
    }

    private void handleCheckFriendshipStatus(JsonObject jsonMessage) throws SQLException {
        logWithTime("🔍 CHECK_FRIENDSHIP_STATUS request received");

        if (currentUser == null) {
            logWithTime("   ❌ User not logged in");
            sendError("Bạn chưa đăng nhập!");
            return;
        }

        int targetUserId = jsonMessage.get("targetUserId").getAsInt();
        int currentUserId = currentUser.getUserId();

        logWithTime("   👤 Current user: " + currentUser.getUsername() + " (ID: " + currentUserId + ")");
        logWithTime("   🎯 Target user ID: " + targetUserId);

        FriendDAO friendDAO = new FriendDAO();
        String status = friendDAO.getFriendshipStatus(currentUserId, targetUserId);

        logWithTime("   ✅ Friendship status: " + status);

        Map<String, Object> response = new HashMap<>();
        response.put("type", "CHECK_FRIENDSHIP_STATUS");
        response.put("success", true);
        response.put("status", status);
        response.put("targetUserId", targetUserId);

        sendMessage(response);
        logWithTime("   ✅ CHECK_FRIENDSHIP_STATUS response sent");
    }


    private void handleSearchUsers(JsonObject jsonMessage) throws SQLException {
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
    private void handleAddFriend(JsonObject jsonMessage) throws SQLException {
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
    private void handleAcceptFriend(JsonObject jsonMessage) throws SQLException {
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
    private void handleRejectFriend(JsonObject jsonMessage) throws SQLException {
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
    private void handleRemoveFriend(JsonObject jsonMessage) throws SQLException {
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
    private void handleGetFriendsList(JsonObject jsonMessage) throws SQLException {
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
    private void handleGetPendingRequests(JsonObject jsonMessage) throws SQLException {
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

    /**
     * Handler: UPDATE_PASSWORD - Đổi mật khẩu
     */
    private void handleUpdatePassword(JsonObject request) {
//        try {
//            logWithTime("🔐 [UPDATE_PASSWORD] Processing request...");
//
//            if (currentUser == null) {
//                logWithTime("❌ [UPDATE_PASSWORD] User not logged in");
//                sendError("Bạn chưa đăng nhập!");
//                return;
//            }
//
//            String oldPassword = request.get("oldPassword").getAsString();
//            String newPassword = request.get("newPassword").getAsString();
//
//            logWithTime("🔐 [UPDATE_PASSWORD] User: " + currentUser.getUsername());
//
//            // Validate old password
//            String storedHash = userDAO.getPassword(currentUser.getUsername());
//            String inputHash = hashPassword(oldPassword);
//
//            if (storedHash == null || !storedHash.equals(inputHash)) {
//                logWithTime("❌ [UPDATE_PASSWORD] Old password incorrect");
//
//                Map<String, Object> response = new HashMap<>();
//                response.put("type", "UPDATE_PASSWORD");
//                response.put("success", false);
//                response.put("message", "Mật khẩu cũ không đúng!");
//                sendMessage(response);
//                return;
//            }
//
//            // Hash new password before updating
//            String newHashedPassword = hashPassword(newPassword);
//            boolean success = userDAO.updatePassword(currentUser.getUserId(), newHashedPassword);
//
//            Map<String, Object> response = new HashMap<>();
//            response.put("type", "UPDATE_PASSWORD");
//            response.put("success", success);
//
//            if (success) {
//                response.put("message", "Đổi mật khẩu thành công!");
//                logWithTime("✅ [UPDATE_PASSWORD] Password updated successfully");
//            } else {
//                response.put("message", "Đổi mật khẩu thất bại!");
//                logWithTime("❌ [UPDATE_PASSWORD] Failed to update password");
//            }
//
//            sendMessage(response);
//
//        } catch (Exception e) {
//            logWithTime("❌ [UPDATE_PASSWORD] Error: " + e.getMessage());
//            e.printStackTrace();
//            sendError("Lỗi khi đổi mật khẩu!");
//        }
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

    public void sendMessage(Map<String, Object> data) {
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


    // Tìm trận
    /**
     * Handler: FIND_MATCH - Tìm trận đấu
     */
    private void handleFindMatch(JsonObject request) {
        try {
            logWithTime("🔍 [FIND_MATCH] Processing request...");

            if (currentUser == null) {
                logWithTime("❌ [FIND_MATCH] User not logged in");
                sendError("Bạn chưa đăng nhập!");
                return;
            }

            // Parse request
            String subject = request.get("subject").getAsString();
            String difficulty = request.has("difficulty") ?
                    request.get("difficulty").getAsString() :
                    Protocol.MEDIUM;

            logWithTime("🔍 [FIND_MATCH] User: " + currentUser.getUsername() +
                    " | Subject: " + subject +
                    " | Difficulty: " + difficulty);

            // Validate subject
            if (!isValidSubject(subject)) {
                logWithTime("❌ [FIND_MATCH] Invalid subject: " + subject);

                Map<String, Object> response = new HashMap<>();
                response.put("type", Protocol.FIND_MATCH);
                response.put("success", false);
                response.put("message", "Môn học không hợp lệ!");
                sendMessage(response);
                return;
            }

            // Validate difficulty
            if (!isValidDifficulty(difficulty)) {
                logWithTime("❌ [FIND_MATCH] Invalid difficulty: " + difficulty);

                Map<String, Object> response = new HashMap<>();
                response.put("type", Protocol.FIND_MATCH);
                response.put("success", false);
                response.put("message", "Độ khó không hợp lệ!");
                sendMessage(response);
                return;
            }

            // Call MatchmakingManager
            boolean success = matchmakingManager.findMatch(this, subject, difficulty);

            // Send response
            Map<String, Object> response = new HashMap<>();
            response.put("type", Protocol.FIND_MATCH);
            response.put("success", success);

            if (success) {
                response.put("message", "Đang tìm kiếm đối thủ...");
                logWithTime("✅ [FIND_MATCH] User added to matchmaking queue");
            } else {
                response.put("message", "Bạn đã trong hàng đợi tìm kiếm!");
                logWithTime("⚠️ [FIND_MATCH] User already in queue");
            }

            sendMessage(response);

        } catch (Exception e) {
            logWithTime("❌ [FIND_MATCH] Error: " + e.getMessage());
            e.printStackTrace();
            sendError("Lỗi khi tìm trận đấu!");
        }
    }

    /**
     * Handler: CANCEL_FIND_MATCH - Hủy tìm trận
     */
    private void handleCancelFindMatch(JsonObject request) {
        try {
            logWithTime("❌ [CANCEL_FIND_MATCH] Processing request...");

            if (currentUser == null) {
                logWithTime("❌ [CANCEL_FIND_MATCH] User not logged in");
                sendError("Bạn chưa đăng nhập!");
                return;
            }

            logWithTime("❌ [CANCEL_FIND_MATCH] User: " + currentUser.getUsername());

            // Call MatchmakingManager
            boolean success = matchmakingManager.cancelFindMatch(this);

            if (!success) {
                logWithTime("⚠️ [CANCEL_FIND_MATCH] No active search found");

                Map<String, Object> response = new HashMap<>();
                response.put("type", Protocol.CANCEL_FIND_MATCH);
                response.put("success", false);
                response.put("message", "Bạn không có tìm kiếm nào đang hoạt động!");
                sendMessage(response);
            } else {
                logWithTime("✅ [CANCEL_FIND_MATCH] Search cancelled successfully");
            }

        } catch (Exception e) {
            logWithTime("❌ [CANCEL_FIND_MATCH] Error: " + e.getMessage());
            e.printStackTrace();
            sendError("Lỗi khi hủy tìm kiếm!");
        }
    }

    /**
     * Validate subject
     */
    private boolean isValidSubject(String subject) {
        return Protocol.MATH.equals(subject) ||
                Protocol.ENGLISH.equals(subject) ||
                Protocol.LITERATURE.equals(subject);
    }

    /**
     * Validate difficulty
     */
    private boolean isValidDifficulty(String difficulty) {
        return Protocol.EASY.equals(difficulty) ||
                Protocol.MEDIUM.equals(difficulty) ||
                Protocol.HARD.equals(difficulty);
    }




    /**
     * Handler: GET_STATISTICS - Lấy thống kê game
     */
    private void handleGetStatistics(JsonObject request) {
        try {
            logWithTime("📊 [GET_STATISTICS] Processing request...");

            if (currentUser == null) {
                logWithTime("❌ [GET_STATISTICS] User not logged in");
                sendError("Bạn chưa đăng nhập!");
                return;
            }

            logWithTime("📊 [GET_STATISTICS] User: " + currentUser.getUsername());

            // Get fresh data from database
            User user = userDAO.getUserById(currentUser.getUserId());

            if (user == null) {
                logWithTime("❌ [GET_STATISTICS] User not found");
                sendError("Không tìm thấy thông tin người dùng!");
                return;
            }

            // Calculate win rate
            double winRate = 0.0;
            if (user.getTotalGames() > 0) {
                winRate = (double) user.getWins() / user.getTotalGames() * 100.0;
            }

            // Build response
            Map<String, Object> response = new HashMap<>();
            response.put("type", "GET_STATISTICS");
            response.put("success", true);
            response.put("totalGames", user.getTotalGames());
            response.put("wins", user.getWins());
            response.put("losses", user.getTotalGames() - user.getWins());
            response.put("totalScore", user.getTotalScore());
            response.put("mathScore", user.getMathScore());
            response.put("englishScore", user.getEnglishScore());
            response.put("literatureScore", user.getLiteratureScore());
            response.put("winRate", Math.round(winRate * 100.0) / 100.0);

            sendMessage(response);
            logWithTime("✅ [GET_STATISTICS] Statistics sent successfully");

        } catch (Exception e) {
            logWithTime("❌ [GET_STATISTICS] Error: " + e.getMessage());
            e.printStackTrace();
            sendError("Lỗi khi lấy thống kê!");
        }
    }

    /**
     * Handler: GET_TRAINING_QUESTIONS - Lấy câu hỏi luyện tập
     */
    private void handleGetTrainingQuestions(JsonObject request) {
        try {
            logWithTime("📝 [GET_TRAINING_QUESTIONS] Processing request...");

            if (currentUser == null) {
                logWithTime("❌ [GET_TRAINING_QUESTIONS] User not logged in");
                sendError("Bạn chưa đăng nhập!");
                return;
            }

            String subject = request.get("subject").getAsString();
            String difficulty = request.get("difficulty").getAsString();
            int count = request.has("count") ? request.get("count").getAsInt() : 10;

            logWithTime("📝 [GET_TRAINING_QUESTIONS] User: " + currentUser.getUsername() +
                    " | Subject: " + subject +
                    " | Difficulty: " + difficulty +
                    " | Count: " + count);

            // Get questions from database
            List<Question> questions = questionDAO.getRandomQuestions(subject, difficulty, count);

            if (questions == null || questions.isEmpty()) {
                logWithTime("⚠️ [GET_TRAINING_QUESTIONS] No questions found");

                Map<String, Object> response = new HashMap<>();
                response.put("type", "GET_TRAINING_QUESTIONS");
                response.put("success", false);
                response.put("message", "Không tìm thấy câu hỏi phù hợp!");
                sendMessage(response);
                return;
            }

            // Build response
            List<Map<String, Object>> questionList = new ArrayList<>();

            for (Question q : questions) {
                Map<String, Object> qMap = new HashMap<>();
                qMap.put("questionId", q.getQuestionId());
                qMap.put("subject", q.getSubject());
                qMap.put("question", q.getQuestionText());
                qMap.put("optionA", q.getOptionA());
                qMap.put("optionB", q.getOptionB());
                qMap.put("optionC", q.getOptionC());
                qMap.put("optionD", q.getOptionD());
                qMap.put("correctAnswer", q.getCorrectAnswer());
                qMap.put("difficulty", q.getDifficulty());

                questionList.add(qMap);
            }

            Map<String, Object> response = new HashMap<>();
            response.put("type", "GET_TRAINING_QUESTIONS");
            response.put("success", true);
            response.put("questions", questionList);
            response.put("count", questions.size());

            sendMessage(response);
            logWithTime("✅ [GET_TRAINING_QUESTIONS] Sent " + questions.size() + " questions");

        } catch (Exception e) {
            logWithTime("❌ [GET_TRAINING_QUESTIONS] Error: " + e.getMessage());
            e.printStackTrace();
            sendError("Lỗi khi lấy câu hỏi!");
        }
    }
    /**
     * Tạo một số nguyên ngẫu nhiên có đúng 4 chữ số (từ 1000 đến 9999).
     * * @return Số nguyên ngẫu nhiên có 4 chữ số.
     */
    private int generateRandom4DigitId() {
        // Phạm vi: [min, max]
        int min = 1000;
        int max = 9999;

        // Sử dụng Random để tạo số ngẫu nhiên trong phạm vi [min, max]
        // Công thức: random.nextInt((max - min) + 1) + min
        Random random = new Random();
        return random.nextInt((max - min) + 1) + min;
    }

    /**
     * Handler: CREATE_ROOM - Tạo phòng game
     */
    private void handleCreateRoom(JsonObject request) {
        try {
            String subject = request.get("subject").getAsString();
            String difficulty = request.get("difficulty").getAsString();

            if (currentUser == null) {
                sendError("Bạn chưa đăng nhập!");
                return;
            }

            // 1️⃣ Tạo phòng trong DB
            Room newRoom = RoomDAO.createRoom(currentUser.getUserId(), subject, difficulty);
            if (newRoom == null) {
                sendError("Không thể tạo phòng trong database!");
                return;
            }

            // 2️⃣ Tạo phòng trong memory
            String roomId = String.valueOf(newRoom.getRoomId());
            GameRoomManager.GameRoom gameRoom = gameRoomManager
                    .createRoomWithId(roomId, this, newRoom.getRoomName(),
                            subject, difficulty, newRoom.getMaxPlayers());

            if (gameRoom == null) {
                sendError("Không thể tạo phòng trong memory!");
                return;
            }

            // 3️⃣ Chuẩn bị phản hồi
            Map<String, Object> response = new HashMap<>();
            response.put("type", Protocol.CREATE_ROOM);
            response.put("success", true);
            response.put("roomId", roomId);
            response.put("roomName", newRoom.getRoomName());
            response.put("subject", subject);
            response.put("difficulty", difficulty);
            response.put("maxPlayers", newRoom.getMaxPlayers());
            response.put("currentPlayers", 1);

            // 🔑 Thông tin host
            Map<String, Object> hostInfo = new HashMap<>();
            hostInfo.put("userId", currentUser.getUserId());
            hostInfo.put("username", currentUser.getUsername());
            hostInfo.put("fullName", currentUser.getFullName());
            hostInfo.put("avatarUrl", currentUser.getAvatarUrl());
            hostInfo.put("totalScore", currentUser.getTotalScore());
            hostInfo.put("isHost", true);
            hostInfo.put("isReady", false);

            List<Map<String, Object>> players = new ArrayList<>();
            players.add(hostInfo);
            response.put("playersList", players);

            // 4️⃣ Gửi kết quả về client
            sendMessage(response);
            logWithTime("✅ Room created successfully! ID=" + roomId);

        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("type", Protocol.CREATE_ROOM);
            errorResponse.put("success", false);
            errorResponse.put("message", "Lỗi server: " + e.getMessage());
            sendMessage(errorResponse);
        }
    }



//    /**
//     * Handler: JOIN_ROOM - Vào phòng game
//     */
//    private void handleJoinRoom(JsonObject request) {
//        try {
//            logWithTime("🚪 [JOIN_ROOM] Processing request...");
//
//            if (currentUser == null) {
//                sendError("Bạn chưa đăng nhập!");
//                logWithTime("❌ [JOIN_ROOM] User not logged in");
//                return;
//            }
//
//            String roomId = request.get("roomId").getAsString();
//            logWithTime("🚪 [JOIN_ROOM] User: " + currentUser.getUsername() + " | RoomId: " + roomId);
//
//            // 🔎 Thử vào phòng thông qua GameRoomManager
//            boolean success = gameRoomManager.joinRoom(this, roomId);
//
//            Map<String, Object> response = new HashMap<>();
//            response.put("type", "JOIN_ROOM_RESPONSE");
//            response.put("roomId", roomId);
//
//            if (success) {
//                GameRoomManager.GameRoom room = gameRoomManager.getRoom(roomId);
//                response.put("success", true);
//                response.put("message", "Đã tham gia phòng thành công!");
//                response.put("roomName", room.getRoomName());
//                response.put("subject", room.getSubject());
//                response.put("difficulty", room.getDifficulty());
//                response.put("players", room.getPlayerCount());
//
//                // ✅ Gửi thông báo tới các thành viên khác trong phòng
//                for (ClientHandler other : room.getPlayers()) {
//                    if (other != this) {
//                        other.sendMessage(Map.of(
//                                "type", "PLAYER_JOINED",
//                                "username", currentUser.getUsername()
//                        ));
//                    }
//                }
//
//                logWithTime("✅ [JOIN_ROOM] " + currentUser.getUsername() + " joined " + roomId);
//
//            } else {
//                response.put("success", false);
//                response.put("message", "Không thể tham gia phòng. Phòng không tồn tại hoặc đã đầy!");
//                logWithTime("❌ [JOIN_ROOM] Join failed: " + roomId);
//            }
//
//            sendMessage(response);
//
//        } catch (Exception e) {
//            logWithTime("❌ [JOIN_ROOM] Error: " + e.getMessage());
//            e.printStackTrace();
//            sendError("Lỗi khi vào phòng!");
//        }
//    }

    /**
     * Handler: JOIN_ROOM - Vào phòng game
     */
    private void handleJoinRoom(JsonObject request) {
        try {
            logWithTime("🚪 [JOIN_ROOM] ========== START ==========");

            if (currentUser == null) {
                sendError("Bạn chưa đăng nhập!");
                return;
            }

            String roomId = request.get("roomId").getAsString().trim();
            logWithTime("🚪 [JOIN_ROOM] User: " + currentUser.getUsername() +
                    " joining room: " + roomId);

            GameRoomManager.GameRoom room = gameRoomManager.getRoom(roomId);

            if (room == null) {
                logWithTime("❌ [JOIN_ROOM] Room not found: " + roomId);
                Map<String, Object> response = new HashMap<>();
                response.put("type", Protocol.JOIN_ROOM_RESPONSE);
                response.put("success", false);
                response.put("message", "Phòng không tồn tại!");
                response.put("roomId", roomId);
                sendMessage(response);
                return;
            }

            if (room.isFull()) {
                logWithTime("❌ [JOIN_ROOM] Room is full");
                Map<String, Object> response = new HashMap<>();
                response.put("type", Protocol.JOIN_ROOM_RESPONSE);
                response.put("success", false);
                response.put("message", "Phòng đã đầy!");
                response.put("roomId", roomId);
                sendMessage(response);
                return;
            }

            // Join room
            boolean success = gameRoomManager.joinRoom(this, roomId);

            if (!success) {
                Map<String, Object> response = new HashMap<>();
                response.put("type", Protocol.JOIN_ROOM_RESPONSE);
                response.put("success", false);
                response.put("message", "Không thể tham gia phòng!");
                response.put("roomId", roomId);
                sendMessage(response);
                return;
            }

            // ✅ Prepare response with full room data
            Map<String, Object> response = new HashMap<>();
            response.put("type", Protocol.JOIN_ROOM_RESPONSE);
            response.put("success", true);
            response.put("message", "Đã tham gia phòng thành công!");
            response.put("roomId", roomId);
            response.put("roomName", room.getRoomName());
            response.put("subject", room.getSubject());
            response.put("difficulty", room.getDifficulty());
            response.put("maxPlayers", room.getMaxPlayers());

            // ✅ Build players list
            List<Map<String, Object>> playersList = new ArrayList<>();
            for (ClientHandler client : room.getPlayers()) {
                User user = client.getCurrentUser();
                Map<String, Object> playerData = new HashMap<>();
                playerData.put("userId", user.getUserId());
                playerData.put("username", user.getUsername());
                playerData.put("fullName", user.getFullName());
                playerData.put("avatarUrl", user.getAvatarUrl());
                playerData.put("totalScore", user.getTotalScore());
                playerData.put("isHost", user.getUserId() == room.getHost().getUserId());
                playerData.put("isReady", room.isPlayerReady(user.getUserId()));
                playersList.add(playerData);
            }
            response.put("playersList", playersList);

            // Send response to joining player
            sendMessage(response);

            // ✅ Broadcast PLAYER_JOINED to existing players
            Map<String, Object> joinNotification = new HashMap<>();
            joinNotification.put("type", Protocol.PLAYER_JOINED);
            joinNotification.put("userId", currentUser.getUserId());
            joinNotification.put("username", currentUser.getUsername());
            joinNotification.put("fullName", currentUser.getFullName());
            joinNotification.put("avatarUrl", currentUser.getAvatarUrl());
            joinNotification.put("totalScore", currentUser.getTotalScore());

            for (ClientHandler other : room.getPlayers()) {
                if (other != this) {
                    try {
                        other.sendMessage(joinNotification);
                        logWithTime("   📤 Notified: " + other.getCurrentUser().getUsername());
                    } catch (Exception e) {
                        logWithTime("   ⚠️ Failed to notify: " + e.getMessage());
                    }
                }
            }

            logWithTime("✅ [JOIN_ROOM] Player joined successfully");
            logWithTime("🚪 [JOIN_ROOM] ========== END ==========");

        } catch (Exception e) {
            logWithTime("❌ [JOIN_ROOM] Exception: " + e.getMessage());
            e.printStackTrace();
            sendError("Lỗi khi vào phòng: " + e.getMessage());
        }
    }

    /**
     * Handler: KICK_PLAYER - Kick người chơi khỏi phòng (chỉ host)
     */
    private void handleKickPlayer(JsonObject request) {
        try {
            logWithTime("👢 [KICK_PLAYER] ========== START ==========");

            if (currentUser == null) {
                sendError("Bạn chưa đăng nhập!");
                return;
            }

            String roomId = request.get("roomId").getAsString().trim();
            int targetUserId = request.get("targetUserId").getAsInt();

            GameRoomManager.GameRoom room = gameRoomManager.getRoom(roomId);

            if (room == null) {
                logWithTime("❌ [KICK_PLAYER] Room not found");
                sendError("Phòng không tồn tại!");
                return;
            }

            // ✅ Check if requester is host
            if (room.getHost().getUserId() != currentUser.getUserId()) {
                logWithTime("❌ [KICK_PLAYER] Not host");
                sendError("Chỉ chủ phòng mới có thể kick người chơi!");
                return;
            }

            // ✅ Check if target player exists in room
            if (!room.hasPlayer(targetUserId)) {
                logWithTime("❌ [KICK_PLAYER] Target not in room");
                sendError("Người chơi không trong phòng!");
                return;
            }

            // ✅ Cannot kick self
            if (targetUserId == currentUser.getUserId()) {
                logWithTime("❌ [KICK_PLAYER] Cannot kick self");
                sendError("Không thể kick chính mình!");
                return;
            }

            // ✅ Find target ClientHandler
            ClientHandler targetHandler = null;
            User targetUser = null;

            for (ClientHandler client : room.getPlayers()) {
                if (client.getCurrentUser().getUserId() == targetUserId) {
                    targetHandler = client;
                    targetUser = client.getCurrentUser();
                    break;
                }
            }

            if (targetHandler == null || targetUser == null) {
                logWithTime("❌ [KICK_PLAYER] Target handler not found");
                sendError("Không tìm thấy người chơi!");
                return;
            }

            logWithTime("👢 [KICK_PLAYER] Kicking: " + targetUser.getUsername());

            // ✅ Get all players before removing
            List<ClientHandler> allPlayers = new ArrayList<>(room.getPlayers());

            // ✅ Remove player from room
            boolean removed = gameRoomManager.leaveRoom(targetHandler, roomId);

            if (!removed) {
                logWithTime("❌ [KICK_PLAYER] Failed to remove player");
                sendError("Không thể kick người chơi!");
                return;
            }

            // ✅ Prepare kick notification
            Map<String, Object> kickNotification = new HashMap<>();
            kickNotification.put("type", Protocol.KICK_PLAYER);
            kickNotification.put("userId", targetUserId);
            kickNotification.put("username", targetUser.getUsername());
            kickNotification.put("isNewHost", false);

            // ✅ Send notification to all players (including kicked player)
            for (ClientHandler client : allPlayers) {
                try {
                    client.sendMessage(kickNotification);
                    logWithTime("   📤 Notified: " + client.getCurrentUser().getUsername());
                } catch (Exception e) {
                    logWithTime("   ⚠️ Failed to notify: " + e.getMessage());
                }
            }

            logWithTime("✅ [KICK_PLAYER] Player kicked successfully");
            logWithTime("👢 [KICK_PLAYER] ========== END ==========");

        } catch (Exception e) {
            logWithTime("❌ [KICK_PLAYER] Exception: " + e.getMessage());
            e.printStackTrace();
            sendError("Lỗi khi kick người chơi!");
        }
    }
    /**
     * Handler: LEAVE_ROOM - Rời phòng game
     */
    private void handleLeaveRoom(JsonObject request) {
        try {
            logWithTime("🚪 [LEAVE_ROOM] ========== START ==========");

            if (currentUser == null) {
                sendError("Bạn chưa đăng nhập!");
                return;
            }

            String roomId = request.get("roomId").getAsString().trim();
            GameRoomManager.GameRoom room = gameRoomManager.getRoom(roomId);

            if (room == null) {
                logWithTime("⚠️ [LEAVE_ROOM] Room not found");
                Map<String, Object> response = new HashMap<>();
                response.put("type", Protocol.LEAVE_ROOM);
                response.put("success", true);
                response.put("message", "Đã rời phòng");
                sendMessage(response);
                return;
            }

            boolean wasHost = (room.getHost().getUserId() == currentUser.getUserId());
            int leavingUserId = currentUser.getUserId();
            String leavingUsername = currentUser.getUsername();

            logWithTime("   User leaving: " + leavingUsername + " (wasHost=" + wasHost + ")");

            // ✅ Get remaining players BEFORE removing
            List<ClientHandler> remainingPlayers = new ArrayList<>(room.getPlayers());
            remainingPlayers.remove(this);

            logWithTime("   Remaining players: " + remainingPlayers.size());

            // ✅ Remove player from room (this will auto-assign new host if needed)
            boolean success = gameRoomManager.leaveRoom(this, roomId);

            if (!success) {
                logWithTime("❌ [LEAVE_ROOM] Failed to leave room");
                sendError("Không thể rời phòng!");
                return;
            }

            // Send confirmation to leaving player
            Map<String, Object> response = new HashMap<>();
            response.put("type", Protocol.LEAVE_ROOM);
            response.put("success", true);
            response.put("message", "Đã rời phòng");
            sendMessage(response);

            logWithTime("   ✅ Player removed from room");

            // ✅ Broadcast PLAYER_LEFT to remaining players
            if (!remainingPlayers.isEmpty()) {
                Map<String, Object> leftNotification = new HashMap<>();
                leftNotification.put("type", Protocol.PLAYER_LEFT);
                leftNotification.put("userId", leavingUserId);
                leftNotification.put("username", leavingUsername);

                // ✅ If host left, get NEW host from room (already assigned by GameRoom)
                if (wasHost) {
                    // ✅ Get the NEW host that was auto-assigned
                    User newHostUser = room.getHost();

                    leftNotification.put("isNewHost", true);
                    leftNotification.put("newHostId", newHostUser.getUserId());

                    logWithTime("   👑 New host assigned: " + newHostUser.getUsername() +
                            " (userId=" + newHostUser.getUserId() + ")");

                    // ✅ IMPORTANT: Clear ready status của new host
                    room.setPlayerReady(newHostUser.getUserId(), false);

                } else {
                    leftNotification.put("isNewHost", false);
                }

                // Broadcast to all remaining players
                for (ClientHandler player : remainingPlayers) {
                    try {
                        player.sendMessage(leftNotification);
                        logWithTime("   📤 Notified: " + player.getCurrentUser().getUsername());
                    } catch (Exception e) {
                        logWithTime("   ⚠️ Failed to notify: " + e.getMessage());
                    }
                }
            }

            logWithTime("✅ [LEAVE_ROOM] ========== END ==========");

        } catch (Exception e) {
            logWithTime("❌ [LEAVE_ROOM] Exception: " + e.getMessage());
            e.printStackTrace();
            sendError("Lỗi khi rời phòng!");
        }
    }

    /**
     * Handler: PLAYER_READY - Sẵn sàng chơi
     */
    private void handlePlayerReady(JsonObject request) {
        try {
            logWithTime("✅ [PLAYER_READY] Processing request...");

            if (currentUser == null) {
                sendError("Bạn chưa đăng nhập!");
                return;
            }

            boolean isReady = request.get("isReady").getAsBoolean();

            // Find player's room
            GameRoomManager.GameRoom room = gameRoomManager.getRoomByUser(currentUser.getUserId());

            if (room == null) {
                logWithTime("❌ [PLAYER_READY] Player not in any room");
                sendError("Bạn không ở trong phòng nào!");
                return;
            }

            // Update ready status
            room.setPlayerReady(currentUser.getUserId(), isReady);

            logWithTime("✅ [PLAYER_READY] User: " + currentUser.getUsername() +
                    " ready=" + isReady);

            // Send confirmation to player
            Map<String, Object> response = new HashMap<>();
            response.put("type", Protocol.READY);
            response.put("success", true);
            response.put("isReady", isReady);
            response.put("message", isReady ? "Đã sẵn sàng!" : "Đã hủy sẵn sàng!");
            sendMessage(response);

            // ✅ Broadcast to all players in room
            Map<String, Object> readyNotification = new HashMap<>();
            readyNotification.put("type", Protocol.PLAYER_READY);
            readyNotification.put("userId", currentUser.getUserId());
            readyNotification.put("username", currentUser.getUsername());
            readyNotification.put("isReady", isReady);

            for (ClientHandler player : room.getPlayers()) {
                if (player != this) {
                    try {
                        player.sendMessage(readyNotification);
                        logWithTime("   📤 Notified: " + player.getCurrentUser().getUsername());
                    } catch (Exception e) {
                        logWithTime("   ⚠️ Failed to notify: " + e.getMessage());
                    }
                }
            }

            logWithTime("✅ [PLAYER_READY] Ready status broadcasted");

        } catch (Exception e) {
            logWithTime("❌ [PLAYER_READY] Error: " + e.getMessage());
            e.printStackTrace();
            sendError("Lỗi khi gửi trạng thái sẵn sàng!");
        }
    }

    /**
     * Handler: START_GAME - Host bắt đầu game
     */
    private void handleStartGame(JsonObject request) {
        try {
            logWithTime("🎮 [START_GAME] Processing request...");

            if (currentUser == null) {
                sendError("Bạn chưa đăng nhập!");
                return;
            }

            String roomId = request.get("roomId").getAsString().trim();
            GameRoomManager.GameRoom room = gameRoomManager.getRoom(roomId);

            if (room == null) {
                sendError("Phòng không tồn tại!");
                return;
            }

            if (room.getHost().getUserId() != currentUser.getUserId()) {
                sendError("Chỉ chủ phòng mới có thể bắt đầu game!");
                return;
            }

            if (room.getPlayerCount() < 2) {
                sendError("Cần ít nhất 2 người chơi!");
                return;
            }

            if (!room.areAllPlayersReady()) {
                sendError("Chưa tất cả người chơi sẵn sàng!");
                return;
            }

            // ✅ Start game session
            List<ClientHandler> players = room.getPlayers();
            boolean success = gameManager.startGame(
                    roomId,
                    room.getSubject(),
                    room.getDifficulty(),
                    players
            );

            if (!success) {
                sendError("Không thể bắt đầu game!");
                return;
            }

            // ✅ Setup callbacks
            GameSession session = gameManager.getSession(roomId);
            if (session != null) {
                // ✅ FIXED: Send individual questions - tìm đúng handler cho mỗi userId
                session.setQuestionSender((rid, userId, questionIndex) -> {
                    GameServer server = GameServer.getInstance();
                    if (server != null) {
                        for (ClientHandler handler : server.getConnectedClients()) {
                            if (handler.getCurrentUser() != null &&
                                    handler.getCurrentUser().getUserId() == userId) {
                                handler.sendQuestionToPlayerDirect(rid, userId, questionIndex);
                                break;
                            }
                        }
                    }
                });

                // Broadcast positions every second
                session.setPositionBroadcaster((rid) -> {
                    broadcastPositions(rid, players);
                });

                // ✅ NEW: Broadcast when someone answers
                session.setAnswerBroadcaster((rid, userId, isCorrect, timeTaken, position, score, gotNitro) -> {
                    broadcastAnswerResult(rid, userId, isCorrect, timeTaken, position, score, gotNitro, players);
                });

                // ✅ NEW: Broadcast question progress
                session.setProgressBroadcaster((rid, userId, questionIndex) -> {
                    broadcastQuestionProgress(rid, userId, questionIndex, players);
                });

                // Notify when player finishes
                session.setPlayerFinishNotifier((rid, userId, rank) -> {
                    notifyPlayerFinish(rid, userId, rank);
                });

                // Notify game end
                session.setGameEndNotifier((rid, reason) -> {
                    endGameAndSendResults(rid, players, reason);
                });
            }



            // ✅ Broadcast START_GAME to all players
            Map<String, Object> startNotification = new HashMap<>();
            startNotification.put("type", Protocol.START_GAME);
            startNotification.put("success", true);
            startNotification.put("roomId", roomId);
            startNotification.put("subject", room.getSubject());
            startNotification.put("difficulty", room.getDifficulty());
            startNotification.put("message", "Game bắt đầu trong 10 giây!");
            startNotification.put("countdownSeconds", 10);
            startNotification.put("mode", "async"); // ✅ Thông báo chế độ async

            // Add player info
            List<Map<String, Object>> playerInfoList = new ArrayList<>();
            for (ClientHandler player : players) {
                if (player.getCurrentUser() != null) {
                    Map<String, Object> pInfo = new HashMap<>();
                    pInfo.put("userId", player.getCurrentUser().getUserId());
                    pInfo.put("username", player.getCurrentUser().getUsername());
                    pInfo.put("fullName", player.getCurrentUser().getFullName());
                    pInfo.put("avatarUrl", player.getCurrentUser().getAvatarUrl());
                    playerInfoList.add(pInfo);
                }
            }
            startNotification.put("players", playerInfoList);

            // Broadcast to all players
            for (ClientHandler player : players) {
                try {
                    player.sendMessage(startNotification);
                    logWithTime("   📤 Notified: " + player.getCurrentUser().getUsername());
                } catch (Exception e) {
                    logWithTime("   ⚠️ Failed to notify: " + e.getMessage());
                }
            }

            logWithTime("✅ [START_GAME] Game started successfully");

            // ✅ Schedule countdown and start
            new Thread(() -> {
                try {
                    Thread.sleep(10000); // Wait for countdown

                    // Start game (will send first question to all players)
                    gameManager.beginGameAfterCountdown(roomId);

                    logWithTime("✅ [GAME] All players received their first question");

                } catch (InterruptedException e) {
                    logWithTime("❌ [START_GAME] Countdown interrupted");
                }
            }, "GameCountdown-" + roomId).start();

        } catch (Exception e) {
            logWithTime("❌ [START_GAME] Error: " + e.getMessage());
            e.printStackTrace();
            sendError("Lỗi khi bắt đầu game!");
        }
    }


    /**
     * ✅ Gửi câu hỏi trực tiếp cho một player cụ thể
     * Called by GameSession via questionSender callback
     */
    public void sendQuestionToPlayerDirect(String roomId, int userId, int questionIndex) {
        try {
            logWithTime("🎯 [SEND_QUESTION] ========== START ==========");

            // Validate connection
            if (writer == null || writer.checkError()) {
                logWithTime("❌ [SEND_QUESTION] Writer is closed for userId=" + userId);
                return;
            }

            // Validate user
            if (currentUser == null || currentUser.getUserId() != userId) {
                logWithTime("⚠️ [SEND_QUESTION] User mismatch! Expected=" + userId +
                        ", Current=" + (currentUser != null ? currentUser.getUserId() : "null"));
                return;
            }

            logWithTime("🎯 [SEND_QUESTION] Sending Q" + (questionIndex + 1) +
                    " to user: " + currentUser.getUsername());

            // Get question from GameSession
            GameSession session = gameManager.getSession(roomId);
            if (session == null) {
                logWithTime("❌ [SEND_QUESTION] Session not found: " + roomId);
                return;
            }

            Question question = session.getQuestionForPlayer(userId);
            if (question == null) {
                logWithTime("❌ [SEND_QUESTION] No question for player " + userId +
                        " at index " + questionIndex);
                return;
            }

            logWithTime("   Question ID: " + question.getQuestionId());
            logWithTime("   Question Text: " + question.getQuestionText().substring(0,
                    Math.min(50, question.getQuestionText().length())) + "...");

            // ✅ FIX: Build complete question data with proper structure
            Map<String, Object> questionData = new HashMap<>();
            questionData.put("questionId", question.getQuestionId());
            questionData.put("questionText", question.getQuestionText());
            questionData.put("timeLimit", Protocol.QUESTION_TIME_LIMIT);

            // ✅ CRITICAL: Add options array
            List<String> options = Arrays.asList(
                    question.getOptionA(),
                    question.getOptionB(),
                    question.getOptionC(),
                    question.getOptionD()
            );
            questionData.put("options", options);

            // ✅ Build response packet with proper structure
            Map<String, Object> response = new HashMap<>();
            response.put("type", Protocol.GAME_QUESTION);
            response.put("roomId", roomId);
            response.put("questionNumber", questionIndex + 1);
            response.put("totalQuestions", Protocol.QUESTIONS_PER_GAME);
            response.put("question", questionData); // Nested structure
            response.put("timestamp", System.currentTimeMillis());

            // Send
            sendMessage(response);

            logWithTime("✅ [SEND_QUESTION] Successfully sent Q" + (questionIndex + 1) +
                    "/" + Protocol.QUESTIONS_PER_GAME);
            logWithTime("   Options: " + options.size() + " choices");
            logWithTime("🎯 [SEND_QUESTION] ========== END ==========");

        } catch (Exception e) {
            logWithTime("❌ [SEND_QUESTION] Exception: " + e.getMessage());
            e.printStackTrace();
        }
    }


    public void broadcastQuestionProgress(String roomId, int userId, int questionIndex,
                                          List<ClientHandler> players) {
        try {
            Map<String, Object> progressData = new HashMap<>();
            progressData.put("type", "PLAYER_PROGRESS");
            progressData.put("roomId", roomId);
            progressData.put("userId", userId);
            progressData.put("currentQuestion", questionIndex + 1);
            progressData.put("totalQuestions", Protocol.QUESTIONS_PER_GAME);
            progressData.put("timestamp", System.currentTimeMillis());

            // ✅ Broadcast to ALL players EXCEPT the player themselves
            for (ClientHandler player : players) {
                if (player.getCurrentUser() != null &&
                        player.getCurrentUser().getUserId() != userId) {
                    try {
                        player.sendMessage(progressData);
                    } catch (Exception e) {
                        // Ignore disconnected players
                    }
                }
            }

            logWithTime("📢 [PROGRESS] User " + userId + " -> Question " + (questionIndex + 1));

        } catch (Exception e) {
            logWithTime("❌ [BROADCAST_PROGRESS] Error: " + e.getMessage());
        }
    }

    /**
     * ✅ Broadcast vị trí của tất cả người chơi
     * Called every 1 second by GameSession
     */
    public void broadcastPositions(String roomId, List<ClientHandler> players) {
        try {
            GameSession session = GameManager.getInstance().getSession(roomId);
            if (session == null || session.isFinished()) {
                return;
            }

            Map<String, Object> positionData = new HashMap<>();
            positionData.put("type", "GAME_UPDATE");
            positionData.put("roomId", roomId);
            positionData.put("timestamp", System.currentTimeMillis());

            List<Map<String, Object>> positions = new ArrayList<>();

            for (Map.Entry<Integer, GameSession.PlayerGameState> entry :
                    session.getPlayerStates().entrySet()) {

                GameSession.PlayerGameState state = entry.getValue();
                Map<String, Object> pos = new HashMap<>();
                pos.put("userId", state.userId);
                pos.put("position", state.position);
                pos.put("score", state.score);
                pos.put("correctStreak", state.correctStreak);
                pos.put("wrongStreak", state.wrongStreak);
                pos.put("gotNitro", state.gotNitro);
                pos.put("currentQuestion", session.getQuestionIndexForPlayer(state.userId) + 1);
                pos.put("totalQuestions", Protocol.QUESTIONS_PER_GAME);

                // Add last answer info for UI feedback
                pos.put("lastAnswerCorrect", state.lastAnswerCorrect);
                pos.put("lastAnswerTime", state.lastAnswerTime);

                positions.add(pos);
            }
            positionData.put("positions", positions);

            // ✅ Broadcast to ALL players
            for (ClientHandler player : players) {
                if (player.getCurrentUser() == null) continue;

                try {
                    player.sendMessage(positionData);
                } catch (Exception e) {
                    // Ignore disconnected players
                }
            }

            // RESET gotNitro flags after broadcast
            for (GameSession.PlayerGameState state : session.getPlayerStates().values()) {
                state.gotNitro = false;
            }

            logWithTime("📢 [BROADCAST_POSITIONS] Sent to " + players.size() + " players");

        } catch (Exception e) {
            logWithTime("❌ [BROADCAST_POSITIONS] Error: " + e.getMessage());
        }
    }



    /**
     * ✅ Broadcast answer result to all players
     */
    public void broadcastAnswerResult(String roomId, int userId, boolean isCorrect,
                                      long timeTaken, double position, int score,
                                      boolean gotNitro, List<ClientHandler> players) {
        try {
            Map<String, Object> answerData = new HashMap<>();
            answerData.put("type", "PLAYER_ANSWERED");
            answerData.put("roomId", roomId);
            answerData.put("userId", userId);
            answerData.put("isCorrect", isCorrect);
            answerData.put("timeTaken", timeTaken);
            answerData.put("position", position);
            answerData.put("score", score);
            answerData.put("gotNitro", gotNitro);
            answerData.put("timestamp", System.currentTimeMillis());

            // ✅ Broadcast to ALL players (including the answerer)
            for (ClientHandler player : players) {
                if (player.getCurrentUser() != null) {
                    try {
                        player.sendMessage(answerData);
                    } catch (Exception e) {
                        // Ignore disconnected players
                    }
                }
            }

            logWithTime("📢 [ANSWER] Broadcasted: User " + userId + " - " +
                    (isCorrect ? "✅ Correct" : "❌ Wrong") +
                    (gotNitro ? " 🚀 NITRO!" : ""));

        } catch (Exception e) {
            logWithTime("❌ [BROADCAST_ANSWER] Error: " + e.getMessage());
        }
    }

    /**
     * ✅ Thông báo player đã hoàn thành
     */
    public void notifyPlayerFinish(String roomId, int userId, int rank) {
        try {
            // Find the specific player's handler
            for (ClientHandler handler : GameServer.getInstance().getConnectedClients()) {
                if (handler.getCurrentUser() != null &&
                        handler.getCurrentUser().getUserId() == userId) {

                    Map<String, Object> finishData = new HashMap<>();
                    finishData.put("type", "PLAYER_FINISHED");
                    finishData.put("roomId", roomId);
                    finishData.put("userId", userId);
                    finishData.put("rank", rank);
                    finishData.put("message", "You finished! Rank: " + rank);
                    finishData.put("timestamp", System.currentTimeMillis());

                    handler.sendMessage(finishData);

                    logWithTime("🏁 [NOTIFY_FINISH] Player " + userId + " finished, rank " + rank);
                    break;
                }
            }
        } catch (Exception e) {
            logWithTime("❌ [NOTIFY_FINISH] Error: " + e.getMessage());
        }
    }


    /**
     * ✅ Gửi câu hỏi cho một người chơi cụ thể
     */
    public void sendQuestionToPlayer(String roomId, int userId, int questionIndex) {
        try {
            if (currentUser == null || currentUser.getUserId() != userId) {
                logWithTime("⚠️ [QUESTION] User mismatch");
                return;
            }

            GameSession session = GameManager.getInstance().getSession(roomId);
            if (session == null) {
                logWithTime("❌ [QUESTION] Session not found");
                return;
            }

            Question question = session.getQuestionForPlayer(userId);
            if (question == null) {
                logWithTime("⚠️ [QUESTION] No question for player " + userId);
                return;
            }

            // ✅ Log chi tiết
            logWithTime("📤 [QUESTION] Sending to player " + userId + ":");
            logWithTime("   Question Index: " + questionIndex);
            logWithTime("   Question ID: " + question.getQuestionId());
            logWithTime("   Question Text: " + question.getQuestionText().substring(0,
                    Math.min(50, question.getQuestionText().length())) + "...");

            Map<String, Object> questionData = new HashMap<>();
            questionData.put("type", Protocol.GAME_QUESTION);
            questionData.put("roomId", roomId);
            questionData.put("questionNumber", questionIndex + 1);
            questionData.put("totalQuestions", Protocol.QUESTIONS_PER_GAME);
            questionData.put("questionId", question.getQuestionId());
            questionData.put("questionText", question.getQuestionText());
            questionData.put("timeLimit", Protocol.QUESTION_TIME_LIMIT);

            List<String> options = new ArrayList<>();
            options.add(question.getOptionA());
            options.add(question.getOptionB());
            options.add(question.getOptionC());
            options.add(question.getOptionD());
            questionData.put("options", options);

            sendMessage(questionData);
            logWithTime("✅ [QUESTION] Sent successfully");

        } catch (Exception e) {
            logWithTime("❌ [SEND_QUESTION] Error: " + e.getMessage());
            e.printStackTrace();
        }
    }


    /**
     * ✅ Kết thúc game và gửi kết quả cuối cùng
     */
    public void endGameAndSendResults(String roomId, List<ClientHandler> players, String reason) {
        try {
            logWithTime("🏁 [END_GAME] Ending game: " + roomId + " (" + reason + ")");

            GameSession session = GameManager.getInstance().getSession(roomId);
            if (session == null) {
                logWithTime("⚠️ [END_GAME] Session not found");
                return;
            }

            // Get final rankings
            List<Map<String, Object>> rankings = new ArrayList<>();
            List<GameSession.PlayerGameState> playerStates = new ArrayList<>(session.getPlayerStates().values());

            // Sort by position (descending), then by score (descending)
            playerStates.sort((a, b) -> {
                int posCompare = Double.compare(b.position, a.position);
                if (posCompare != 0) return posCompare;
                return Integer.compare(b.score, a.score);
            });

            // Build rankings
            for (int i = 0; i < playerStates.size(); i++) {
                GameSession.PlayerGameState state = playerStates.get(i);

                Map<String, Object> rankData = new HashMap<>();
                rankData.put("rank", i + 1);
                rankData.put("userId", state.userId);
                rankData.put("position", state.position);
                rankData.put("score", state.score);

                // Get player name
                for (ClientHandler handler : players) {
                    if (handler.getCurrentUser() != null &&
                            handler.getCurrentUser().getUserId() == state.userId) {
                        rankData.put("username", handler.getCurrentUser().getUsername());
                        rankData.put("fullName", handler.getCurrentUser().getFullName());
                        break;
                    }
                }

                rankings.add(rankData);
            }

            // Build end game packet
            Map<String, Object> endGameData = new HashMap<>();
            endGameData.put("type", Protocol.GAME_END);
            endGameData.put("roomId", roomId);
            endGameData.put("reason", reason);
            endGameData.put("rankings", rankings);
            endGameData.put("timestamp", System.currentTimeMillis());

            // Broadcast to all players
            for (ClientHandler player : players) {
                if (player.getCurrentUser() != null) {
                    try {
                        player.sendMessage(endGameData);
                        logWithTime("   📤 Sent results to: " + player.getCurrentUser().getUsername());
                    } catch (Exception e) {
                        logWithTime("   ⚠️ Failed to send to: " + e.getMessage());
                    }
                }
            }

            logWithTime("✅ [END_GAME] Results sent to all players");

        } catch (Exception e) {
            logWithTime("❌ [END_GAME] Error: " + e.getMessage());
            e.printStackTrace();
        }
    }


    /**
     * ✅ Handler: GET_GAME_STATE - Player request current game state (reconnect)
     */
    private void handleGetGameState(JsonObject request) {
        try {
            if (currentUser == null) {
                sendError("Bạn chưa đăng nhập!");
                return;
            }

            String roomId = request.has("roomId")
                    ? request.get("roomId").getAsString()
                    : request.get("room_id").getAsString();

            GameSession session = gameManager.getSession(roomId);
            if (session == null) {
                sendError("Game không tồn tại!");
                return;
            }

            int userId = currentUser.getUserId();

            // ✅ Prepare state data
            Map<String, Object> stateData = new HashMap<>();
            stateData.put("type", "GAME_STATE");
            stateData.put("roomId", roomId);
            stateData.put("gameState", session.getGameState().toString());

            // Current question for this player
            int questionIndex = session.getQuestionIndexForPlayer(userId);
            stateData.put("currentQuestion", questionIndex + 1);
            stateData.put("totalQuestions", Protocol.QUESTIONS_PER_GAME);

            // All player positions
            List<Map<String, Object>> positions = new ArrayList<>();
            for (Map.Entry<Integer, GameSession.PlayerGameState> entry :
                    session.getPlayerStates().entrySet()) {

                GameSession.PlayerGameState state = entry.getValue();
                Map<String, Object> pos = new HashMap<>();
                pos.put("userId", state.userId);
                pos.put("position", state.position);
                pos.put("score", state.score);
                pos.put("currentQuestion", session.getQuestionIndexForPlayer(state.userId) + 1);
                positions.add(pos);
            }
            stateData.put("positions", positions);

            sendMessage(stateData);

            logWithTime("✅ [GET_GAME_STATE] Sent state to player " + userId);

        } catch (Exception e) {
            logWithTime("❌ [GET_GAME_STATE] Error: " + e.getMessage());
            sendError("Lỗi khi lấy trạng thái game!");
        }
    }


    /**
     * Handler: SUBMIT_ANSWER - Player gửi câu trả lời
     * ✅ IMPROVED VERSION with proper question flow
     */
    private void handleSubmitAnswer(JsonObject request) {
        try {
            logWithTime("📝 [SUBMIT_ANSWER] ========== START ==========");

            if (currentUser == null) {
                logWithTime("❌ [SUBMIT_ANSWER] User not logged in");
                sendError("Bạn chưa đăng nhập!");
                return;
            }

            String roomId = request.has("roomId")
                    ? request.get("roomId").getAsString()
                    : request.get("room_id").getAsString();
            int answerIndex = request.get("answer").getAsInt();
            String answer = String.valueOf((char)('A' + answerIndex));
            int userId = currentUser.getUserId();

            logWithTime("📝 [SUBMIT_ANSWER] User: " + currentUser.getUsername() +
                    " | Room: " + roomId +
                    " | Answer: " + answer);

            // ✅ Submit to GameManager
            GameSession.AnswerResult result = gameManager.submitAnswer(roomId, userId, answer);

            if (!result.success) {
                logWithTime("❌ [SUBMIT_ANSWER] Failed: " + result.message);
                sendError(result.message);
                return;
            }

            // ✅ Send immediate feedback to THIS player only
            Map<String, Object> feedback = new HashMap<>();
            feedback.put("type", Protocol.ANSWER_RESULT);
            feedback.put("success", true);
            feedback.put("isCorrect", result.message.contains("Correct") ||
                    result.message.contains("finished") ||
                    result.message.contains("reached"));
            feedback.put("timeTaken", result.timeTaken);
            feedback.put("correctStreak", result.correctStreak);
            feedback.put("message", result.message);

            sendMessage(feedback);

            logWithTime("✅ [SUBMIT_ANSWER] Feedback sent: " + result.message);
            logWithTime("   Time: " + result.timeTaken + "ms | Streak: " + result.correctStreak);

            // ✅ NOTE: Next question will be sent automatically by GameSession
            // after 2 seconds delay via questionSender callback

            logWithTime("📝 [SUBMIT_ANSWER] ========== END ==========");

        } catch (Exception e) {
            logWithTime("❌ [SUBMIT_ANSWER] Exception: " + e.getMessage());
            e.printStackTrace();
            sendError("Lỗi khi nộp câu trả lời!");
        }
    }
    /**
     * Kết thúc game và gửi kết quả cuối cùng
     */
    private void endGameAndSendResults(String roomId, List<ClientHandler> players) {
        try {
            logWithTime("🏁 [GAME_END] Ending game: " + roomId);

            GameSession session = gameManager.getSession(roomId);
            if (session == null) return;

            // ✅ End game (will save to database)
            gameManager.endGame(roomId, players);

            // ✅ Prepare final results
            Map<String, Object> endData = new HashMap<>();
            endData.put("type", Protocol.GAME_END);
            endData.put("roomId", roomId);
            endData.put("message", "Game kết thúc!");

            // ✅ Add rankings
            List<Map<String, Object>> rankings = new ArrayList<>();
            List<GameSession.PlayerGameState> sortedStates = new ArrayList<>(
                    session.getPlayerStates().values()
            );
            sortedStates.sort((a, b) -> {
                int posCompare = Double.compare(b.position, a.position);
                if (posCompare != 0) return posCompare;
                return Integer.compare(b.score, a.score);
            });

            for (GameSession.PlayerGameState state : sortedStates) {
                Map<String, Object> ranking = new HashMap<>();
                ranking.put("userId", state.userId);
                ranking.put("rank", state.finalRank);
                ranking.put("position", state.position);
                ranking.put("score", state.score);
                rankings.add(ranking);
            }
            endData.put("rankings", rankings);

            // ✅ Winner info
            if (!sortedStates.isEmpty()) {
                GameSession.PlayerGameState winner = sortedStates.get(0);
                endData.put("winnerId", winner.userId);
                endData.put("winnerScore", winner.score);
            }

            // ✅ Broadcast to all players
            for (ClientHandler player : players) {
                try {
                    player.sendMessage(endData);
                } catch (Exception e) {
                    logWithTime("   ⚠️ Failed to send end data to player");
                }
            }

            logWithTime("✅ [GAME_END] Results sent to all players");

        } catch (Exception e) {
            logWithTime("❌ [GAME_END] Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Handler: ROOM_CHAT - Chat trong phòng
     */
    private void handleRoomChat(JsonObject request) {
        try {
            if (currentUser == null) {
                return;
            }

            String roomId = request.get("roomId").getAsString().trim();
            String message = request.get("message").getAsString();

            GameRoomManager.GameRoom room = gameRoomManager.getRoom(roomId);

            if (room == null) {
                return;
            }

            // Broadcast chat to all players in room
            Map<String, Object> chatMessage = new HashMap<>();
            chatMessage.put("type", Protocol.ROOM_CHAT);
            chatMessage.put("senderId", currentUser.getUserId());
            chatMessage.put("sender", currentUser.getFullName());
            chatMessage.put("username", currentUser.getUsername()); // Add this
            chatMessage.put("message", message);
            chatMessage.put("timestamp", System.currentTimeMillis());

            for (ClientHandler player : room.getPlayers()) {
                try {
                    player.sendMessage(chatMessage);
                } catch (Exception e) {
                    logWithTime("⚠️ Failed to send chat to: " +
                            player.getCurrentUser().getUsername());
                }
            }

            logWithTime("💬 [ROOM_CHAT] " + currentUser.getUsername() + ": " + message);

        } catch (Exception e) {
            logWithTime("❌ [ROOM_CHAT] Error: " + e.getMessage());
        }
    }

}
