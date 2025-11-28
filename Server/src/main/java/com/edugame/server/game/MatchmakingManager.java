package com.edugame.server.game;

import com.edugame.common.Protocol;
import com.edugame.server.database.QuestionDAO;
import com.edugame.server.database.RoomDAO;
import com.edugame.server.model.Question;
import com.edugame.server.model.Room;
import com.edugame.server.model.User;
import com.edugame.server.network.ClientHandler;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * ✅ FIXED: Ghép trận + Gửi câu hỏi
 * - Tìm đối thủ phù hợp
 * - Tạo bộ câu hỏi chung cho cả 2
 * - Gửi START_GAME kèm câu hỏi
 */
public class MatchmakingManager {

    private static final DateTimeFormatter LOG_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private static final long MATCHMAKING_TIMEOUT_MS = 30_000;

    private final Map<String, Queue<MatchRequest>> waitingQueues;
    private final Map<Integer, MatchRequest> userRequests;
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
     * ✅ Tìm kiếm đối thủ
     */
    public synchronized boolean findMatch(ClientHandler handler, String subject,
                                          String difficulty, int countPlayer) {
        User user = handler.getCurrentUser();

        if (user == null) {
            logWithTime("❌ User not logged in");
            return false;
        }

        int userId = user.getUserId();

        if (userRequests.containsKey(userId)) {
            MatchRequest existingRequest = userRequests.get(userId);
            logWithTime("⚠️ User " + user.getUsername() + " already in queue");
            logWithTime("   Existing queue: " + existingRequest.subject +
                    "_" + existingRequest.difficulty);
            return false;
        }

        logWithTime("🔍 FIND_MATCH: " + user.getUsername() +
                " | Subject: " + subject +
                " | Difficulty: " + difficulty +
                " | CountPlayer: " + countPlayer);

        MatchRequest newRequest = new MatchRequest(handler, user, subject, difficulty, countPlayer);

        String queueKey = getQueueKey(subject, difficulty);
        Queue<MatchRequest> queue = waitingQueues.computeIfAbsent(
                queueKey, k -> new ConcurrentLinkedQueue<>()
        );

        logWithTime("📊 Current queue size for " + queueKey + ": " + queue.size());

        MatchRequest opponent = findOpponent(queue, newRequest);

        if (opponent != null) {
            logWithTime("✅ MATCH FOUND IMMEDIATELY!");
            logWithTime("   Player 1: " + newRequest.user.getUsername());
            logWithTime("   Player 2: " + opponent.user.getUsername());

            userRequests.put(userId, newRequest);

            createMatch(newRequest, opponent, subject, difficulty, countPlayer);
            return true;

        } else {
            queue.offer(newRequest);
            userRequests.put(userId, newRequest);

            logWithTime("⏳ Added to queue: " + user.getUsername());
            logWithTime("   New queue size: " + queue.size());

            scheduleTimeout(newRequest);
            return true;
        }
    }

    /**
     * ✅ Tìm đối thủ trong queue
     */
    private MatchRequest findOpponent(Queue<MatchRequest> queue, MatchRequest newRequest) {
        Iterator<MatchRequest> iterator = queue.iterator();

        logWithTime("🔍 Searching for opponent in queue...");
        logWithTime("   Queue size: " + queue.size());

        // ✅ Lấy điểm môn học của người chơi mới
        int newPlayerScore = getSubjectScore(newRequest.user, newRequest.subject);
        logWithTime("   New player: " + newRequest.user.getUsername() +
                " (Total: " + newRequest.user.getTotalScore() +
                ", " + newRequest.subject + ": " + newPlayerScore + ")");

        while (iterator.hasNext()) {
            MatchRequest candidate = iterator.next();

            if (candidate.isExpired()) {
                logWithTime("   ⏰ Removing expired: " + candidate.user.getUsername());
                iterator.remove();
                userRequests.remove(candidate.user.getUserId());
                continue;
            }

            if (candidate.user.getUserId() == newRequest.user.getUserId()) {
                logWithTime("   ⚠️ Same user, skip");
                continue;
            }

            if (candidate.countPlayer != newRequest.countPlayer) {
                logWithTime("   ⚠️ Different countPlayer: " +
                        candidate.countPlayer + " vs " + newRequest.countPlayer);
                continue;
            }

            // ✅ So sánh điểm môn học cụ thể thay vì tổng điểm
            int candidateScore = getSubjectScore(candidate.user, candidate.subject);
            int scoreDiff = Math.abs(candidateScore - newPlayerScore);

            logWithTime("   🎯 Checking: " + candidate.user.getUsername() +
                    " (Total: " + candidate.user.getTotalScore() +
                    ", " + candidate.subject + ": " + candidateScore + ")");
            logWithTime("      Score difference (" + newRequest.subject + "): " + scoreDiff);

            if (scoreDiff <= 200) {
                logWithTime("   ✅ OPPONENT FOUND!");

                iterator.remove();
                userRequests.remove(candidate.user.getUserId());

                if (candidate.timeoutFuture != null) {
                    candidate.timeoutFuture.cancel(false);
                    logWithTime("   ⏱️ Cancelled timeout for: " + candidate.user.getUsername());
                }

                return candidate;
            } else {
                logWithTime("      ❌ Score difference too large (max: 200)");
            }
        }

        logWithTime("   ❌ No suitable opponent found");
        return null;
    }

    /**
     * ✅ Helper: Lấy điểm số theo môn học
     */
    private int getSubjectScore(User user, String subject) {
        switch (subject.toLowerCase()) {
            case "math":
                return user.getMathScore();
            case "english":
                return user.getEnglishScore();
            case "literature":
                return user.getLiteratureScore();
            case "total":
            default:
                return user.getTotalScore();
        }
    }

    /**
     * Lên lịch timeout
     */
    private void scheduleTimeout(MatchRequest request) {
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            handleTimeout(request);
        }, MATCHMAKING_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        request.timeoutFuture = future;
        logWithTime("⏱️ Scheduled timeout (30s) for: " + request.user.getUsername());
    }

    /**
     * ✅ Xử lý timeout
     */
    private synchronized void handleTimeout(MatchRequest request) {
        if (!userRequests.containsKey(request.user.getUserId())) {
            logWithTime("⏰ Timeout ignored (already matched): " +
                    request.user.getUsername());
            return;
        }

        logWithTime("⏰ TIMEOUT: " + request.user.getUsername() +
                " | Subject: " + request.subject +
                " | Difficulty: " + request.difficulty);

        userRequests.remove(request.user.getUserId());

        String queueKey = getQueueKey(request.subject, request.difficulty);
        Queue<MatchRequest> queue = waitingQueues.get(queueKey);

        if (queue != null) {
            boolean removed = queue.remove(request);
            logWithTime("   Queue removal: " + (removed ? "SUCCESS" : "FAILED"));
            logWithTime("   Queue size after removal: " + queue.size());
        }

        Map<String, Object> response = new HashMap<>();
        response.put("type", Protocol.MATCH_FOUND);
        response.put("success", false);
        response.put("timeout", true);
        response.put("message", "Không tìm thấy đối thủ phù hợp trong 30 giây. Vui lòng thử lại!");

        request.handler.sendMessage(response);
        logWithTime("   ✅ Timeout notification sent to: " + request.user.getUsername());
    }

    /**
     * ✅ Hủy tìm kiếm
     */
    public synchronized boolean cancelFindMatch(ClientHandler handler) {
        User user = handler.getCurrentUser();

        if (user == null) {
            logWithTime("❌ Cancel failed: User not logged in");
            return false;
        }

        int userId = user.getUserId();
        MatchRequest request = userRequests.remove(userId);

        if (request == null) {
            logWithTime("⚠️ No active search for user: " + user.getUsername());
            return false;
        }

        if (request.timeoutFuture != null) {
            request.timeoutFuture.cancel(false);
            logWithTime("⏱️ Cancelled timeout for: " + user.getUsername());
        }

        String queueKey = getQueueKey(request.subject, request.difficulty);
        Queue<MatchRequest> queue = waitingQueues.get(queueKey);

        if (queue != null) {
            boolean removed = queue.remove(request);
            logWithTime("✅ Removed from queue: " + user.getUsername() +
                    " (success: " + removed + ")");
            logWithTime("   Queue size after removal: " + queue.size());
        }

        Map<String, Object> response = new HashMap<>();
        response.put("type", Protocol.CANCEL_FIND_MATCH);
        response.put("success", true);
        response.put("message", "Đã hủy tìm kiếm");
        handler.sendMessage(response);

        logWithTime("✅ CANCEL_FIND_MATCH: " + user.getUsername());
        return true;
    }

    /**
     * ✅ FIXED: Tạo trận đấu + Gửi câu hỏi
     */
    private void createMatch(MatchRequest req1, MatchRequest req2,
                             String subject, String difficulty, int countPlayer) {

        logWithTime("🎮 Creating match:");
        logWithTime("   Player 1: " + req1.user.getUsername() +
                " (Score: " + req1.user.getTotalScore() + ")");
        logWithTime("   Player 2: " + req2.user.getUsername() +
                " (Score: " + req2.user.getTotalScore() + ")");
        logWithTime("   Subject: " + subject);
        logWithTime("   Difficulty: " + difficulty);
        logWithTime("   CountPlayer: " + countPlayer);

        userRequests.remove(req1.user.getUserId());
        userRequests.remove(req2.user.getUserId());

        try {
            // ✅ 1. TẠO BỘ CÂU HỎI NGAY TỪ ĐẦU
            logWithTime("📝 Generating questions...");
            QuestionDAO questionDAO = new QuestionDAO();
            List<Question> questions = questionDAO.getRandomQuestions(
                    subject,
                    difficulty,
                    Protocol.QUESTIONS_PER_GAME
            );

            if (questions == null || questions.size() < Protocol.QUESTIONS_PER_GAME) {
                logWithTime("❌ Not enough questions! Found: " +
                        (questions != null ? questions.size() : 0));
                sendMatchFailure(req1.handler, "Không đủ câu hỏi cho trận đấu");
                sendMatchFailure(req2.handler, "Không đủ câu hỏi cho trận đấu");
                return;
            }

            logWithTime("✅ Generated " + questions.size() + " questions");

            // 2. Tạo room trong DATABASE
            RoomDAO roomDAO = new RoomDAO();
            Room dbRoom = roomDAO.createRoom(
                    req1.user.getUserId(),
                    subject,
                    difficulty
            );

            if (dbRoom == null) {
                logWithTime("❌ Failed to create room in database");
                sendMatchFailure(req1.handler, "Không thể tạo phòng");
                sendMatchFailure(req2.handler, "Không thể tạo phòng");
                return;
            }

            String roomId = String.valueOf(dbRoom.getRoomId());
            logWithTime("🏠 Room created in DB: " + roomId);

            // 3. Tạo GameRoom
            GameRoomManager.GameRoom room = roomManager.createRoomWithId(
                    roomId,
                    req1.handler,
                    "Match: " + req1.user.getUsername() + " vs " + req2.user.getUsername(),
                    subject,
                    difficulty,
                    countPlayer
            );

            if (room == null) {
                logWithTime("❌ Failed to create GameRoom");
                sendMatchFailure(req1.handler, "Không thể tạo phòng");
                sendMatchFailure(req2.handler, "Không thể tạo phòng");
                return;
            }

            logWithTime("✅ GameRoom created: " + roomId);

            // 4. Player 2 JOIN
            boolean joined = roomManager.joinRoom(req2.handler, roomId);

            if (!joined) {
                logWithTime("❌ Player 2 failed to join");
                sendMatchFailure(req1.handler, "Đối thủ không thể vào phòng");
                sendMatchFailure(req2.handler, "Không thể vào phòng");
                return;
            }

            logWithTime("✅ Player 2 joined room");

            // 5. Set ready
            room.setPlayerReady(req1.user.getUserId(), true);
            room.setPlayerReady(req2.user.getUserId(), true);
            logWithTime("✅ All players set to ready");

            // 6. Gửi MATCH_FOUND cho cả 2
            sendMatchFoundNotification(req1.handler, roomId, subject, difficulty, req2.user);
            sendMatchFoundNotification(req2.handler, roomId, subject, difficulty, req1.user);
            logWithTime("✅ MATCH_FOUND sent to both players");

            // ✅ 7. LƯU CÂU HỎI VÀO GAME
            GameManager gameManager = GameManager.getInstance();

            // ✅ 8. LƯU THÔNG TIN USER TRƯỚC KHI SCHEDULED TASK
            final User user1 = req1.user; // ✅ LƯU LẠI ĐỂ TRÁNH NULL
            final User user2 = req2.user; // ✅ LƯU LẠI ĐỂ TRÁNH NULL
            final String finalRoomId = roomId;
            final List<Question> finalQuestions = questions;

            // 9. Đợi 3 giây rồi START GAME + GỬI CÂU HỎI
            // ✅ FIXED: Đợi countdown (10s) + delay (2s) trước khi gửi câu hỏi đầu tiên
            scheduler.schedule(() -> {
                try {
                    // ✅ Lọc disconnected players
                    List<ClientHandler> allPlayers = room.getPlayers();
                    List<ClientHandler> connectedPlayers = allPlayers.stream()
                            .filter(h -> h != null && h.getCurrentUser() != null)
                            .collect(Collectors.toList());

                    if (connectedPlayers.isEmpty()) {
                        logWithTime("❌ All players disconnected before game start");
                        return;
                    }

                    // ✅ Start game với questions
                    boolean gameStarted = gameManager.startGameWithQuestions(
                            finalRoomId,
                            subject,
                            difficulty,
                            connectedPlayers,
                            finalQuestions
                    );

                    if (!gameStarted) {
                        logWithTime("❌ Failed to start game");
                        for (ClientHandler h : connectedPlayers) {
                            sendMatchFailure(h, "Không thể bắt đầu game");
                        }
                        return;
                    }

                    logWithTime("✅ Game started - countdown begins");

                    // ✅ GỬI START_GAME notification (với countdown 10s)
                    for (ClientHandler handler : connectedPlayers) {
                        if (handler != null && handler.getCurrentUser() != null) {
                            User opponent = null;
                            int currentUserId = handler.getCurrentUser().getUserId();

                            if (currentUserId == user1.getUserId()) {
                                opponent = user2;
                            } else if (currentUserId == user2.getUserId()) {
                                opponent = user1;
                            }

                            if (opponent != null) {
                                sendGameStartWithQuestions(
                                        handler,
                                        finalRoomId,
                                        subject,
                                        difficulty,
                                        opponent,
                                        connectedPlayers,
                                        finalQuestions
                                );
                            }
                        }
                    }

                    // ✅ CHỜ 10 GIÂY (countdown) + 2 GIÂY (buffer) trước khi chuyển PLAYING
                    scheduler.schedule(() -> {
                        try {
                            logWithTime("⏰ Countdown finished - starting game NOW!");

                            // ✅ CHUYỂN GAME SANG PLAYING và gửi câu hỏi đầu tiên
                            gameManager.beginGameAfterCountdown(finalRoomId);

                            logWithTime("✅ Game is now in PLAYING state - ready for answers!");

                        } catch (Exception e) {
                            logWithTime("❌ Error starting gameplay: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }, 12, TimeUnit.SECONDS); // 10s countdown + 2s buffer

                } catch (Exception e) {
                    logWithTime("❌ Error in game start sequence: " + e.getMessage());
                    e.printStackTrace();
                }
            }, 3, TimeUnit.SECONDS); // Initial 3s delay after MATCH_FOUND
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
//            scheduler.schedule(() -> {
//                try {
//                    // ✅ LỌC BỎ DISCONNECTED PLAYERS
//                    List<ClientHandler> allPlayers = room.getPlayers();
//                    List<ClientHandler> connectedPlayers = allPlayers.stream()
//                            .filter(h -> h != null && h.getCurrentUser() != null)
//                            .collect(Collectors.toList());
//
//                    if (connectedPlayers.isEmpty()) {
//                        logWithTime("❌ All players disconnected before game start");
//                        return;
//                    }
//
//                    if (connectedPlayers.size() < 2) {
//                        logWithTime("⚠️ Only " + connectedPlayers.size() + " player(s) remain");
//                        // Vẫn tiếp tục để player còn lại có thể chơi
//                    }
//
//                    // Start game với câu hỏi đã tạo
//                    boolean gameStarted = gameManager.startGameWithQuestions(
//                            finalRoomId,
//                            subject,
//                            difficulty,
//                                connectedPlayers, // ✅ CHỈ GỬI CHO CONNECTED PLAYERS
//                            finalQuestions
//                    );
//
//                    if (!gameStarted) {
//                        logWithTime("❌ Failed to start game");
//                        // Gửi lỗi cho players còn lại
//                        for (ClientHandler h : connectedPlayers) {
//                            sendMatchFailure(h, "Không thể bắt đầu game");
//                        }
//                        return;
//                    }
//
//                    logWithTime("✅ Game started automatically");
//
//                    // ✅ GỬI START_GAME CHỈ CHO CONNECTED PLAYERS
//                    for (ClientHandler handler : connectedPlayers) {
//                        if (handler != null && handler.getCurrentUser() != null) {
//                            // Tìm opponent (dùng User đã lưu thay vì getCurrentUser)
//                            User opponent = null;
//                            int currentUserId = handler.getCurrentUser().getUserId();
//
//                            if (currentUserId == user1.getUserId()) {
//                                opponent = user2;
//                            } else if (currentUserId == user2.getUserId()) {
//                                opponent = user1;
//                            }
//
//                            if (opponent != null) {
//                                sendGameStartWithQuestions(
//                                        handler,
//                                        finalRoomId,
//                                        subject,
//                                        difficulty,
//                                        opponent,
//                                        connectedPlayers, // ✅ CHỈ GỬI CONNECTED PLAYERS
//                                        finalQuestions
//                                );
//                            } else {
//                                logWithTime("⚠️ Could not find opponent for user " + currentUserId);
//                            }
//                        }
//                    }
//
//                    logWithTime("✅ Match created and game started with questions!");
//
//                } catch (Exception e) {
//                    logWithTime("❌ Error starting game: " + e.getMessage());
//                    e.printStackTrace();
//                }
//            }, 3, TimeUnit.SECONDS);
//
//        } catch (Exception e) {
//            logWithTime("❌ Error creating match: " + e.getMessage());
//            e.printStackTrace();
//            sendMatchFailure(req1.handler, "Lỗi tạo trận đấu");
//            sendMatchFailure(req2.handler, "Lỗi tạo trận đấu");
//        }
//    }

    /**
     * Gửi MATCH_FOUND notification
     */
    private void sendMatchFoundNotification(ClientHandler handler, String roomId,
                                            String subject, String difficulty, User opponent) {
        try {
            Map<String, Object> response = new HashMap<>();
            response.put("type", Protocol.MATCH_FOUND);
            response.put("success", true);
            response.put("roomId", roomId);
            response.put("subject", subject);
            response.put("difficulty", difficulty);
            response.put("message", "Đã tìm thấy đối thủ! Game sẽ bắt đầu sau 3 giây...");

            Map<String, Object> opponentData = new HashMap<>();
            opponentData.put("userId", opponent.getUserId());
            opponentData.put("username", opponent.getUsername());
            opponentData.put("fullName", opponent.getFullName());
            opponentData.put("avatarUrl", opponent.getAvatarUrl() != null ? opponent.getAvatarUrl() : "");
            opponentData.put("totalScore", opponent.getTotalScore());
            response.put("opponent", opponentData);

            handler.sendMessage(response);

            logWithTime("📤 MATCH_FOUND sent to: " + handler.getCurrentUser().getUsername());

        } catch (Exception e) {
            logWithTime("❌ Error sending MATCH_FOUND: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * ✅ NEW: Gửi START_GAME + CÂU HỎI
     */

    private void sendGameStartWithQuestions(ClientHandler handler, String roomId, String subject,
                                            String difficulty, User opponent,
                                            List<ClientHandler> allPlayers,
                                            List<Question> questions) {
        try {
            // ✅ KIỂM TRA NULL TRƯỚC KHI GỬI
            if (handler == null || handler.getCurrentUser() == null) {
                logWithTime("⚠️ Skipping disconnected handler");
                return;
            }

            User currentUser = handler.getCurrentUser(); // Lưu lại để tránh gọi nhiều lần

            Map<String, Object> response = new HashMap<>();
            response.put("type", Protocol.START_GAME);
            response.put("success", true);
            response.put("roomId", roomId);
            response.put("subject", subject);
            response.put("difficulty", difficulty);
            response.put("totalQuestions", questions.size());
            response.put("message", "Game bắt đầu!");
            response.put("countdownSeconds", 10);
            response.put("mode", "async");

            // Opponent info
            Map<String, Object> opponentData = new HashMap<>();
            opponentData.put("userId", opponent.getUserId());
            opponentData.put("username", opponent.getUsername());
            opponentData.put("fullName", opponent.getFullName());
            opponentData.put("avatarUrl", opponent.getAvatarUrl());
            opponentData.put("total", opponent.getTotalScore());
            opponentData.put("score_math", opponent.getMathScore());
            opponentData.put("score_english", opponent.getEnglishScore());
            opponentData.put("score_literature", opponent.getLiteratureScore());

            response.put("opponent", opponentData);

            // ✅ All players info - CHỈ THÊM PLAYERS ĐANG ONLINE
            List<Map<String, Object>> playerInfoList = new ArrayList<>();
            for (ClientHandler player : allPlayers) {
                if (player != null && player.getCurrentUser() != null) {
                    Map<String, Object> pInfo = new HashMap<>();
                    User pUser = player.getCurrentUser();
                    pInfo.put("userId", pUser.getUserId());
                    pInfo.put("username", pUser.getUsername());
                    pInfo.put("fullName", pUser.getFullName());
                    pInfo.put("avatarUrl", pUser.getAvatarUrl());
                    pInfo.put("totalScore", pUser.getTotalScore());
                    playerInfoList.add(pInfo);
                } else {
                    logWithTime("⚠️ Skipping disconnected player in players list");
                }
            }
            response.put("players", playerInfoList);

            // ✅ THÊM CÂU HỎI VÀO RESPONSE
            List<Map<String, Object>> questionList = new ArrayList<>();
            for (int i = 0; i < questions.size(); i++) {
                Question q = questions.get(i);
                Map<String, Object> qData = new HashMap<>();
                qData.put("questionId", q.getQuestionId());
                qData.put("questionNumber", i + 1);
                qData.put("questionText", q.getQuestionText());
                qData.put("optionA", q.getOptionA());
                qData.put("optionB", q.getOptionB());
                qData.put("optionC", q.getOptionC());
                qData.put("optionD", q.getOptionD());

                questionList.add(qData);
            }
            response.put("questions", questionList);

            // ✅ GỬI MESSAGE - WRAP TRONG TRY-CATCH
            try {
                handler.sendMessage(response);
                logWithTime("📤 START_GAME sent to: " + currentUser.getUsername());
                logWithTime("   📝 Included " + questions.size() + " questions");
            } catch (Exception sendEx) {
                logWithTime("❌ Failed to send to " + currentUser.getUsername() + ": " + sendEx.getMessage());
            }

        } catch (Exception e) {
            logWithTime("❌ Error in sendGameStartWithQuestions: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Gửi thông báo thất bại
     */
    private void sendMatchFailure(ClientHandler handler, String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("type", Protocol.MATCH_FOUND);
        response.put("success", false);
        response.put("message", message);
        handler.sendMessage(response);
    }

    private String getQueueKey(String subject, String difficulty) {
        return subject + "_" + difficulty;
    }

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

    public Map<String, Integer> getQueueStats() {
        Map<String, Integer> stats = new HashMap<>();
        for (Map.Entry<String, Queue<MatchRequest>> entry : waitingQueues.entrySet()) {
            stats.put(entry.getKey(), entry.getValue().size());
        }
        return stats;
    }

    public void shutdown() {
        logWithTime("🛑 Shutting down MatchmakingManager...");

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

    private static class MatchRequest {
        final ClientHandler handler;
        final User user;
        final String subject;
        final String difficulty;
        final int countPlayer;
        final long timestamp;
        ScheduledFuture<?> timeoutFuture;

        MatchRequest(ClientHandler handler, User user, String subject,
                     String difficulty, int countPlayer) {
            this.handler = handler;
            this.user = user;
            this.subject = subject;
            this.difficulty = difficulty;
            this.countPlayer = countPlayer;
            this.timestamp = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - timestamp > MATCHMAKING_TIMEOUT_MS;
        }
    }
}