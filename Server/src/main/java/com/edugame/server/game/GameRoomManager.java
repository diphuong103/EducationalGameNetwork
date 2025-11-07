package com.edugame.server.game;

import com.edugame.server.model.User;
import com.edugame.server.network.ClientHandler;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Quản lý các phòng chơi game
 */
public class GameRoomManager {

    private static final DateTimeFormatter LOG_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private static GameRoomManager instance;
    private final Map<String, GameRoom> rooms;
    private final AtomicInteger roomIdCounter;

    private GameRoomManager() {
        this.rooms = new ConcurrentHashMap<>();
        this.roomIdCounter = new AtomicInteger(1);
        logWithTime("✅ GameRoomManager initialized");
    }

    public static synchronized GameRoomManager getInstance() {
        if (instance == null) {
            instance = new GameRoomManager();
        }
        return instance;
    }

    /**
     * Tạo phòng mới
     */
    public String createRoom(ClientHandler host, String roomName,
                             String subject, String difficulty, int maxPlayers) {

        User hostUser = host.getCurrentUser();
        if (hostUser == null) {
            logWithTime("❌ Host not logged in");
            return null;
        }

        String roomId = "ROOM_" + roomIdCounter.getAndIncrement();

        GameRoom room = new GameRoom(roomId, hostUser, roomName, subject, difficulty, maxPlayers);
        room.addPlayer(host);

        rooms.put(roomId, room);

        logWithTime("✅ Room created: " + roomId);
        logWithTime("   Host: " + hostUser.getUsername());
        logWithTime("   Name: " + roomName);
        logWithTime("   Subject: " + subject + " | Difficulty: " + difficulty);
        logWithTime("   Max players: " + maxPlayers);

        return roomId;
    }

    /**
     * Tạo phòng mới với ID tùy chỉnh
     */
    public GameRoom createRoomWithId(String roomId, ClientHandler host,
                                     String roomName, String subject,
                                     String difficulty, int maxPlayers) {
        User hostUser = host.getCurrentUser();
        if (hostUser == null) {
            logWithTime("❌ Host not logged in");
            return null;
        }

        if (rooms.containsKey(roomId)) {
            logWithTime("⚠️ Room ID already exists: " + roomId);
            return rooms.get(roomId);
        }

        GameRoom room = new GameRoom(roomId, hostUser, roomName, subject, difficulty, maxPlayers);
        room.addPlayer(host);

        rooms.put(roomId, room);

        logWithTime("✅ Room created with custom ID: " + roomId);
        logWithTime("   Host: " + hostUser.getUsername());

        return room;
    }

    /**
     * Join phòng
     */
    public boolean joinRoom(ClientHandler player, String roomId) {
        GameRoom room = rooms.get(roomId);

        if (room == null) {
            logWithTime("❌ Room not found: " + roomId);
            return false;
        }

        User user = player.getCurrentUser();
        if (user == null) {
            logWithTime("❌ Player not logged in");
            return false;
        }

        if (room.isFull()) {
            logWithTime("❌ Room is full: " + roomId);
            return false;
        }

        boolean success = room.addPlayer(player);

        if (success) {
            logWithTime("✅ Player joined room: " + user.getUsername() + " → " + roomId);
        } else {
            logWithTime("❌ Failed to join room: " + roomId);
        }

        return success;
    }

    /**
     * Leave phòng
     */
    public boolean leaveRoom(ClientHandler player, String roomId) {
        GameRoom room = rooms.get(roomId);

        if (room == null) {
            return false;
        }

        boolean success = room.removePlayer(player);

        // Nếu phòng trống, xóa phòng
        if (room.isEmpty()) {
            rooms.remove(roomId);
            logWithTime("🗑️ Room removed (empty): " + roomId);
        }

        return success;
    }

    /**
     * Lấy thông tin phòng
     */
    public GameRoom getRoom(String roomId) {
        return rooms.get(roomId);
    }

    /**
     * Lấy danh sách phòng
     */
    public List<GameRoom> getAllRooms() {
        return new ArrayList<>(rooms.values());
    }

    /**
     * Lấy phòng của user
     */
    public GameRoom getRoomByUser(int userId) {
        for (GameRoom room : rooms.values()) {
            if (room.hasPlayer(userId)) {
                return room;
            }
        }
        return null;
    }

    private void logWithTime(String message) {
        String timestamp = LocalDateTime.now().format(LOG_TIME_FORMAT);
        System.out.println("[" + timestamp + "] [RoomManager] " + message);
    }

    /**
     * Class đại diện cho một phòng chơi
     */
    public static class GameRoom {
        private final String roomId;
        private User host;
        private final String roomName;
        private final String subject;
        private final String difficulty;
        private final int maxPlayers;
        private final List<ClientHandler> players;
        private final Map<Integer, Boolean> playerReadyStatus; // userId -> isReady
        private final LocalDateTime createdAt;

        public GameRoom(String roomId, User host, String roomName,
                        String subject, String difficulty, int maxPlayers) {
            this.roomId = roomId;
            this.host = host;
            this.roomName = roomName;
            this.subject = subject;
            this.difficulty = difficulty;
            this.maxPlayers = maxPlayers;
            this.players = Collections.synchronizedList(new ArrayList<>());
            this.playerReadyStatus = new ConcurrentHashMap<>();
            this.createdAt = LocalDateTime.now();
        }

        public boolean addPlayer(ClientHandler player) {
            synchronized (players) {
                if (players.size() >= maxPlayers) {
                    return false;
                }

                boolean added = players.add(player);
                if (added && player.getCurrentUser() != null) {
                    // Initialize ready status as false
                    playerReadyStatus.put(player.getCurrentUser().getUserId(), false);
                }
                return added;
            }
        }

        public boolean removePlayer(ClientHandler player) {
            synchronized (players) {
                boolean removed = players.remove(player);

                if (removed && player.getCurrentUser() != null) {
                    int userId = player.getCurrentUser().getUserId();
                    playerReadyStatus.remove(userId);

                    // If host left, assign new host
                    if (host.getUserId() == userId && !players.isEmpty()) {
                        host = players.get(0).getCurrentUser();
                        System.out.println("👑 New host assigned: " + host.getUsername());
                    }
                }

                return removed;
            }
        }

        public boolean hasPlayer(int userId) {
            synchronized (players) {
                return players.stream()
                        .anyMatch(p -> p.getCurrentUser() != null &&
                                p.getCurrentUser().getUserId() == userId);
            }
        }

        public void setPlayerReady(int userId, boolean isReady) {
            playerReadyStatus.put(userId, isReady);
        }

        public boolean isPlayerReady(int userId) {
            return playerReadyStatus.getOrDefault(userId, false);
        }

        public boolean areAllPlayersReady() {
            synchronized (players) {
                for (ClientHandler player : players) {
                    User user = player.getCurrentUser();
                    if (user == null) continue;

                    // Skip host - host doesn't need to ready
                    if (user.getUserId() == host.getUserId()) {
                        continue;
                    }

                    // Check if player is ready
                    if (!isPlayerReady(user.getUserId())) {
                        return false;
                    }
                }
                return true;
            }
        }

        public boolean isFull() {
            return players.size() >= maxPlayers;
        }

        public boolean isEmpty() {
            return players.isEmpty();
        }

        public int getPlayerCount() {
            return players.size();
        }

        public List<ClientHandler> getPlayers() {
            synchronized (players) {
                return new ArrayList<>(players);
            }
        }

        // Getters
        public String getRoomId() { return roomId; }
        public User getHost() { return host; }
        public String getRoomName() { return roomName; }
        public String getSubject() { return subject; }
        public String getDifficulty() { return difficulty; }
        public int getMaxPlayers() { return maxPlayers; }
        public LocalDateTime getCreatedAt() { return createdAt; }
    }
}