package com.edugame.server.game;

import com.edugame.common.Protocol;
import com.edugame.server.model.User;
import com.edugame.server.network.ClientHandler;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

/**
 * Quản lý hệ thống ghép trận tự động
 * - Tìm kiếm đối thủ theo subject và difficulty
 * - Timeout sau 30 giây nếu không tìm thấy
 * - Tự động tạo phòng khi ghép trận thành công
 */
public class MatchmakingManager {

    private static final DateTimeFormatter LOG_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    // Timeout: 30 giây
    private static final long MATCHMAKING_TIMEOUT_MS = 30_000;

    // Hàng đợi tìm kiếm theo subject và difficulty
    private final Map<String, Queue<MatchRequest>> waitingQueues;

    // Map userId -> MatchRequest để hủy nhanh
    private final Map<Integer, MatchRequest> userRequests;

    // Executor để xử lý timeout
    private final ScheduledExecutorService scheduler;

    private final GameRoomManager roomManager;

    public MatchmakingManager(GameRoomManager roomManager) {
        this.waitingQueues = new ConcurrentHashMap<>();
        this.userRequests = new ConcurrentHashMap<>();
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.roomManager = roomManager;

        logWithTime("✅ MatchmakingManager initialized");
    }



    /**
     * Bắt đầu tìm kiếm đối thủ
     */
    public synchronized boolean findMatch(ClientHandler handler, String subject, String difficulty) {
        User user = handler.getCurrentUser();

        if (user == null) {
            logWithTime("❌ User not logged in");
            return false;
        }

        int userId = user.getUserId();

        // Kiểm tra đã trong hàng đợi chưa
        if (userRequests.containsKey(userId)) {
            logWithTime("⚠️ User " + user.getUsername() + " already in queue");
            return false;
        }

        logWithTime("🔍 FIND_MATCH: " + user.getUsername() +
                " | Subject: " + subject + " | Difficulty: " + difficulty);

        // Tạo request
        MatchRequest request = new MatchRequest(handler, user, subject, difficulty);
        userRequests.put(userId, request);

        // Lấy queue tương ứng
        String queueKey = getQueueKey(subject, difficulty);
        Queue<MatchRequest> queue = waitingQueues.computeIfAbsent(
                queueKey, k -> new ConcurrentLinkedQueue<>()
        );

        // Tìm đối thủ trong queue
        MatchRequest opponent = findOpponent(queue, request);

        if (opponent != null) {
            // ✅ Tìm thấy đối thủ ngay lập tức
            logWithTime("✅ Match found immediately!");
            createMatch(request, opponent, subject, difficulty);
            return true;

        } else {
            // ⏳ Thêm vào hàng đợi
            queue.offer(request);
            logWithTime("⏳ Added to queue. Current size: " + queue.size());

            // Lên lịch timeout
            scheduleTimeout(request);
            return true;
        }
    }

    /**
     * Lên lịch timeout (30 giây)
     */
    private void scheduleTimeout(MatchRequest request) {
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            handleTimeout(request);
        }, MATCHMAKING_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        request.timeoutFuture = future;
        logWithTime("⏱️ Scheduled timeout for: " + request.user.getUsername());
    }

    /**
     * Xử lý timeout - Không tìm thấy đối thủ sau 30 giây
     */
    private synchronized void handleTimeout(MatchRequest request) {
        // Kiểm tra request còn trong hệ thống không
        if (!userRequests.containsKey(request.user.getUserId())) {
            logWithTime("⏰ Timeout ignored (already processed): " + request.user.getUsername());
            return; // Đã được xử lý (tìm thấy match hoặc đã cancel)
        }

        logWithTime("⏰ TIMEOUT: " + request.user.getUsername() +
                " | Subject: " + request.subject +
                " | Difficulty: " + request.difficulty);

        // Xóa khỏi hệ thống
        userRequests.remove(request.user.getUserId());

        String queueKey = getQueueKey(request.subject, request.difficulty);
        Queue<MatchRequest> queue = waitingQueues.get(queueKey);

        if (queue != null) {
            boolean removed = queue.remove(request);
            logWithTime("   Queue removal: " + (removed ? "SUCCESS" : "FAILED"));
        }

        // Gửi thông báo timeout
        Map<String, Object> response = new HashMap<>();
        response.put("type", Protocol.MATCH_FOUND);
        response.put("success", false);
        response.put("timeout", true);
        response.put("message", "Không tìm thấy đối thủ phù hợp trong 30 giây. Vui lòng thử lại!");

        request.handler.sendMessage(response);
        logWithTime("   ✅ Timeout notification sent to: " + request.user.getUsername());
    }


    /**
     * Hủy tìm kiếm
     */
    public synchronized boolean cancelFindMatch(ClientHandler handler) {
        User user = handler.getCurrentUser();

        if (user == null) {
            return false;
        }

        int userId = user.getUserId();
        MatchRequest request = userRequests.remove(userId);

        if (request == null) {
            logWithTime("⚠️ No active search for user: " + user.getUsername());
            return false;
        }

        // Hủy timeout
        if (request.timeoutFuture != null) {
            request.timeoutFuture.cancel(false);
        }

        // Xóa khỏi queue
        String queueKey = getQueueKey(request.subject, request.difficulty);
        Queue<MatchRequest> queue = waitingQueues.get(queueKey);

        if (queue != null) {
            queue.remove(request);
            logWithTime("✅ Removed from queue: " + user.getUsername());
        }

        // Gửi phản hồi
        Map<String, Object> response = new HashMap<>();
        response.put("type", Protocol.CANCEL_FIND_MATCH);
        response.put("success", true);
        response.put("message", "Đã hủy tìm kiếm");
        handler.sendMessage(response);

        logWithTime("✅ CANCEL_FIND_MATCH: " + user.getUsername());
        return true;
    }

    /**
     * Tìm đối thủ phù hợp trong queue
     */
    private MatchRequest findOpponent(Queue<MatchRequest> queue, MatchRequest newRequest) {
        Iterator<MatchRequest> iterator = queue.iterator();

        while (iterator.hasNext()) {
            MatchRequest candidate = iterator.next();

            // Bỏ qua request đã expired hoặc của chính mình
            if (candidate.isExpired() ||
                    candidate.user.getUserId() == newRequest.user.getUserId()) {
                iterator.remove();
                userRequests.remove(candidate.user.getUserId());
                continue;
            }

            // Kiểm tra độ chênh lệch score (±200 điểm)
            int scoreDiff = Math.abs(candidate.user.getTotalScore() -
                    newRequest.user.getTotalScore());

            if (scoreDiff <= 200) {
                // ✅ Tìm thấy đối thủ phù hợp
                iterator.remove();
                userRequests.remove(candidate.user.getUserId());

                // Hủy timeout của đối thủ
                if (candidate.timeoutFuture != null) {
                    candidate.timeoutFuture.cancel(false);
                }

                return candidate;
            }
        }

        return null;
    }

    /**
     * Tạo trận đấu khi tìm thấy 2 người
     */
    private void createMatch(MatchRequest req1, MatchRequest req2,
                             String subject, String difficulty) {

        logWithTime("🎮 Creating match:");
        logWithTime("   Player 1: " + req1.user.getUsername() +
                " (Score: " + req1.user.getTotalScore() + ")");
        logWithTime("   Player 2: " + req2.user.getUsername() +
                " (Score: " + req2.user.getTotalScore() + ")");
        logWithTime("   Subject: " + subject + " | Difficulty: " + difficulty);

        // Xóa khỏi userRequests
        userRequests.remove(req1.user.getUserId());
        userRequests.remove(req2.user.getUserId());

        try {
            // Tạo phòng mới
            String roomId = roomManager.createRoom(
                    req1.handler,
                    "Match: " + req1.user.getUsername() + " vs " + req2.user.getUsername(),
                    subject,
                    difficulty,
                    2  // Max 2 players
            );

            if (roomId == null) {
                logWithTime("❌ Failed to create room");
                sendMatchFailure(req1.handler, "Không thể tạo phòng");
                sendMatchFailure(req2.handler, "Không thể tạo phòng");
                return;
            }

            logWithTime("✅ Room created: " + roomId);

            // Player 2 join room
            boolean joined = roomManager.joinRoom(req2.handler, roomId);

            if (!joined) {
                logWithTime("❌ Player 2 failed to join");
                sendMatchFailure(req1.handler, "Đối thủ không thể vào phòng");
                sendMatchFailure(req2.handler, "Không thể vào phòng");
                return;
            }

            logWithTime("✅ Player 2 joined room");

            // Gửi thông báo MATCH_FOUND cho cả 2
            sendMatchFound(req1.handler, roomId, req2.user);
            sendMatchFound(req2.handler, roomId, req1.user);

            logWithTime("✅ Match created successfully!");

        } catch (Exception e) {
            logWithTime("❌ Error creating match: " + e.getMessage());
            e.printStackTrace();
            sendMatchFailure(req1.handler, "Lỗi tạo trận đấu");
            sendMatchFailure(req2.handler, "Lỗi tạo trận đấu");
        }
    }

    /**
     * Gửi thông báo tìm thấy trận đấu
     */
    private void sendMatchFound(ClientHandler handler, String roomId, User opponent) {
        Map<String, Object> response = new HashMap<>();
        response.put("type", Protocol.MATCH_FOUND);
        response.put("success", true);
        response.put("room_id", roomId);

        Map<String, Object> opponentData = new HashMap<>();
        opponentData.put("userId", opponent.getUserId());
        opponentData.put("username", opponent.getUsername());
        opponentData.put("fullName", opponent.getFullName());
        opponentData.put("avatarUrl", opponent.getAvatarUrl());
        opponentData.put("totalScore", opponent.getTotalScore());

        response.put("opponent", opponentData);
        response.put("message", "Đã tìm thấy đối thủ!");

        handler.sendMessage(response);
    }

    /**
     * Gửi thông báo thất bại khi tìm trận
     */
    private void sendMatchFailure(ClientHandler handler, String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("type", Protocol.MATCH_FAILED);
        response.put("success", false);
        response.put("message", message);
        handler.sendMessage(response);
    }


    /**
     * Lên lịch timeout
     */
//    private void scheduleTimeout(MatchRequest request) {
//        ScheduledFuture<?> future = scheduler.schedule(() -> {
//            handleTimeout(request);
//        }, MATCHMAKING_TIMEOUT_MS, TimeUnit.MILLISECONDS);
//
//        request.timeoutFuture = future;
//    }

//    /**
//     * Xử lý timeout
//     */
//    private synchronized void handleTimeout(MatchRequest request) {
//        // Kiểm tra request còn trong hệ thống không
//        if (!userRequests.containsKey(request.user.getUserId())) {
//            return; // Đã được xử lý (tìm thấy match hoặc đã cancel)
//        }
//
//        logWithTime("⏰ TIMEOUT: " + request.user.getUsername());
//
//        // Xóa khỏi hệ thống
//        userRequests.remove(request.user.getUserId());
//
//        String queueKey = getQueueKey(request.subject, request.difficulty);
//        Queue<MatchRequest> queue = waitingQueues.get(queueKey);
//
//        if (queue != null) {
//            queue.remove(request);
//        }
//
//        // Gửi thông báo timeout
//        Map<String, Object> response = new HashMap<>();
//        response.put("type", Protocol.MATCH_FOUND);
//        response.put("success", false);
//        response.put("timeout", true);
//        response.put("message", "Không tìm thấy đối thủ. Vui lòng thử lại!");
//
//        request.handler.sendMessage(response);
//    }

    /**
     * Tạo key cho queue
     */
    private String getQueueKey(String subject, String difficulty) {
        return subject + "_" + difficulty;
    }

    /**
     * Dọn dẹp các request đã expired
     */
    public synchronized void cleanupExpiredRequests() {
        int removed = 0;

        for (Queue<MatchRequest> queue : waitingQueues.values()) {
            Iterator<MatchRequest> iterator = queue.iterator();

            while (iterator.hasNext()) {
                MatchRequest request = iterator.next();

                if (request.isExpired()) {
                    iterator.remove();
                    userRequests.remove(request.user.getUserId());
                    removed++;
                }
            }
        }

        if (removed > 0) {
            logWithTime("🧹 Cleaned up " + removed + " expired requests");
        }
    }

    /**
     * Lấy thống kê hàng đợi
     */
    public Map<String, Integer> getQueueStats() {
        Map<String, Integer> stats = new HashMap<>();

        for (Map.Entry<String, Queue<MatchRequest>> entry : waitingQueues.entrySet()) {
            stats.put(entry.getKey(), entry.getValue().size());
        }

        return stats;
    }

    /**
     * Shutdown
     */
    public void shutdown() {
        logWithTime("🛑 Shutting down MatchmakingManager...");

        // Hủy tất cả timeout
        for (MatchRequest request : userRequests.values()) {
            if (request.timeoutFuture != null) {
                request.timeoutFuture.cancel(false);
            }
        }

        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
        }

        logWithTime("✅ MatchmakingManager shut down");
    }

    private void logWithTime(String message) {
        String timestamp = LocalDateTime.now().format(LOG_TIME_FORMAT);
        System.out.println("[" + timestamp + "] [Matchmaking] " + message);
    }

    /**
     * Class đại diện cho một request tìm kiếm
     */
    private static class MatchRequest {
        final ClientHandler handler;
        final User user;
        final String subject;
        final String difficulty;
        final long timestamp;
        ScheduledFuture<?> timeoutFuture;

        MatchRequest(ClientHandler handler, User user, String subject, String difficulty) {
            this.handler = handler;
            this.user = user;
            this.subject = subject;
            this.difficulty = difficulty;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > MATCHMAKING_TIMEOUT_MS;
        }
    }
}