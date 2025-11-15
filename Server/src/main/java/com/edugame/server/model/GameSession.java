package com.edugame.server.model;

import com.edugame.common.Protocol;
import com.edugame.server.database.GameSessionDAO;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

/**
 * GameSession - Quản lý trạng thái của một trận game
 *
 * ✅ ASYNC MODE: Mỗi người chơi có câu hỏi riêng, không cần chờ nhau
 * ✅ Position broadcasting: Tất cả thấy vị trí của nhau real-time
 */
public class GameSession {

    // ==================== GAME INFO ====================
    private final String roomId;
    private final String subject;
    private final String difficulty;
    private final LocalDateTime startTime;
    private final long startTimeMillis;

    private int sessionId;

    public void setSessionId(int id) {
        this.sessionId = id;
    }

    public int getSessionId() {
        return this.sessionId;
    }

    // ==================== QUESTIONS ====================
    private final List<Question> questions;
    private final int questionTimeLimit = Protocol.QUESTION_TIME_LIMIT;

    // ==================== PLAYERS ====================
    private final Map<Integer, PlayerGameState> playerStates; // userId -> state

    // ✅ Mỗi người có câu hỏi riêng
    private final Map<Integer, Integer> playerQuestionIndex; // userId -> current question index
    private final Map<Integer, Long> playerQuestionStartTime; // userId -> question start time
    private final Map<Integer, ScheduledFuture<?>> playerQuestionTimers; // userId -> timeout timer

    private final Set<Integer> disconnectedPlayers;
    private final Set<Integer> finishedPlayers; // Người chơi đã hoàn thành

    // ==================== GAME STATE ====================
    private GameState gameState;
    private ScheduledExecutorService scheduler;
    private long gameStartTime;
    private final long gameDuration = Protocol.GAME_DURATION * 1000L; // 5 minutes

    // ==================== CONSTANTS ====================
    private static final double FINISH_LINE = Protocol.FINISH_LINE;
    private static final double START_POSITION = 0.0;

    public long getStartTimeMillis() {
        return startTimeMillis;
    }

    public enum GameState {
        COUNTDOWN,       // Đếm ngược 10s trước khi bắt đầu
        PLAYING,         // Đang chơi
        FINISHED         // Game kết thúc
    }

    /**
     * Constructor
     */
    public GameSession(String roomId, int sessionId, String subject, String difficulty, long startTimeMillis,
                       List<Question> questions, List<Integer> playerIds) {
        this.roomId = roomId;
        this.subject = subject;
        this.difficulty = difficulty;
        this.sessionId = sessionId;
        this.startTimeMillis = startTimeMillis;


        // ✅ KHÔNG shuffle ở đây - nhận câu hỏi đã shuffle từ GameManager
        this.questions = new ArrayList<>(questions); // Copy để đảm bảo immutable

        this.startTime = LocalDateTime.now();

        this.playerStates = new ConcurrentHashMap<>();
        this.playerQuestionIndex = new ConcurrentHashMap<>();
        this.playerQuestionStartTime = new ConcurrentHashMap<>();
        this.playerQuestionTimers = new ConcurrentHashMap<>();
        this.disconnectedPlayers = ConcurrentHashMap.newKeySet();
        this.finishedPlayers = ConcurrentHashMap.newKeySet();

        // Initialize player states
        for (Integer userId : playerIds) {
            playerStates.put(userId, new PlayerGameState(userId));
            playerQuestionIndex.put(userId, 0); // ✅ TẤT CẢ bắt đầu từ câu 0
        }

        this.gameState = GameState.COUNTDOWN;
        this.scheduler = Executors.newScheduledThreadPool(playerIds.size() + 1);

        System.out.println("✅ [GameSession] Created ASYNC mode for room " + roomId);
        System.out.println("   Subject: " + subject + " | Difficulty: " + difficulty);
        System.out.println("   Players: " + playerIds.size() + " | Questions: " + questions.size());
        System.out.println("   📋 Question order (first 3):");
        for (int i = 0; i < Math.min(3, questions.size()); i++) {
            System.out.println("      Q" + (i+1) + ": ID=" + questions.get(i).getQuestionId());
        }
    }

    // ==================== GAME FLOW ====================

    /**
     * Bắt đầu countdown 10s
     */
    public void startCountdown() {
        gameState = GameState.COUNTDOWN;
        System.out.println("⏳ [GameSession] Starting countdown...");
    }

    /**
     * Bắt đầu game (sau countdown)
     */
    public void startGame() {
        gameState = GameState.PLAYING;
        gameStartTime = System.currentTimeMillis();
        System.out.println("🎮 [GameSession] Game started in ASYNC mode!");

        // ✅ Gửi câu hỏi đầu tiên cho TẤT CẢ người chơi
        for (Integer userId : playerStates.keySet()) {
            if (!disconnectedPlayers.contains(userId)) {
                startQuestionForPlayer(userId);
            }
        }

        // ✅ Start position broadcasting (mỗi 1s)
        scheduler.scheduleAtFixedRate(() -> {
            if (gameState == GameState.PLAYING && positionBroadcaster != null) {
                positionBroadcaster.broadcastPositions(roomId);
            }
        }, 1, 1, TimeUnit.SECONDS);

        // ✅ Check time limit
        scheduler.schedule(() -> {
            if (gameState == GameState.PLAYING) {
                System.out.println("⏰ [GameSession] Time limit reached!");
                endGame("TIME_UP");
            }
        }, gameDuration, TimeUnit.MILLISECONDS);
    }

    /**
     * ✅ Bắt đầu câu hỏi cho một người chơi cụ thể
     */
    private void startQuestionForPlayer(int userId) {
        Integer currentIndex = playerQuestionIndex.get(userId);

        if (currentIndex == null || currentIndex >= questions.size()) {
            // Người này đã hết câu hỏi
            finishPlayer(userId);
            return;
        }

        // Record start time
        playerQuestionStartTime.put(userId, System.currentTimeMillis());

        System.out.println("❓ [GameSession] Player " + userId +
                " started question " + (currentIndex + 1) + "/" + questions.size());

        // ✅ Set timeout cho người chơi này (10s)
        ScheduledFuture<?> timer = scheduler.schedule(() -> {
            handlePlayerTimeout(userId);
        }, questionTimeLimit, TimeUnit.SECONDS);

        playerQuestionTimers.put(userId, timer);

        // ✅ Notify broadcaster to send question
        if (questionSender != null) {
            questionSender.sendQuestion(roomId, userId, currentIndex);
        }
    }

    /**
     * ✅ Xử lý timeout cho một người chơi
     */
    private void handlePlayerTimeout(int userId) {
        if (finishedPlayers.contains(userId) || disconnectedPlayers.contains(userId)) {
            return;
        }

        System.out.println("⏰ [GameSession] Player " + userId + " timeout!");

        PlayerGameState state = playerStates.get(userId);
        if (state != null) {
            state.totalQuestionsAttempted++;
            state.totalWrongAnswers++;

            // Penalty for timeout
            state.position += Protocol.PENALTY_DISTANCE;
            state.position = Math.max(START_POSITION, state.position);
            state.score += Protocol.POINTS_TIMEOUT;
            state.wrongStreak++;
            state.correctStreak = 0;

            // Wrong streak penalty
            if (state.wrongStreak == 3) {
                state.position -= 30;
                System.out.println("   💥 Player " + userId + " 3 wrong streak penalty!");
            } else if (state.wrongStreak == 5) {
                state.position -= 50;
                System.out.println("   💥💥 Player " + userId + " 5 wrong streak penalty!");
            }

            state.position = Math.max(START_POSITION, state.position);
        }

        // Move to next question
        moveToNextQuestion(userId);
    }


    @FunctionalInterface
    public interface AnswerBroadcaster {
        void broadcastAnswer(String roomId, int userId, boolean isCorrect, long timeTaken,
                             double position, int score, boolean gotNitro);
    }

    private AnswerBroadcaster answerBroadcaster;

    public void setAnswerBroadcaster(AnswerBroadcaster broadcaster) {
        this.answerBroadcaster = broadcaster;
    }


    /**
     * ✅ Xử lý câu trả lời của một người chơi
     */
    public synchronized AnswerResult submitAnswer(int userId, String answer) {
        // Validate
        if (gameState != GameState.PLAYING) {
            return new AnswerResult(false, "Game not in playing state", 0, 0);
        }

        if (finishedPlayers.contains(userId)) {
            return new AnswerResult(false, "You already finished", 0, 0);
        }

        if (disconnectedPlayers.contains(userId)) {
            return new AnswerResult(false, "Player disconnected", 0, 0);
        }

        Integer currentIndex = playerQuestionIndex.get(userId);
        if (currentIndex == null || currentIndex >= questions.size()) {
            return new AnswerResult(false, "No active question", 0, 0);
        }

        // Cancel timeout timer
        ScheduledFuture<?> timer = playerQuestionTimers.remove(userId);
        if (timer != null) {
            timer.cancel(false);
        }

        PlayerGameState state = playerStates.get(userId);
        if (state == null) {
            return new AnswerResult(false, "Player not found", 0, 0);
        }

        // Check correct answer
        Question currentQuestion = questions.get(currentIndex);
        boolean isCorrect = answer.equalsIgnoreCase(currentQuestion.getCorrectAnswer());

        Long startTime = playerQuestionStartTime.get(userId);
        long timeTaken = startTime != null ?
                (System.currentTimeMillis() - startTime) : 0;

        // Update player state
        state.lastAnswer = answer;
        state.lastAnswerCorrect = isCorrect;
        state.lastAnswerTime = timeTaken;

        state.totalQuestionsAttempted++;
        if (isCorrect) {
            state.totalCorrectAnswers++;
        } else {
            state.totalWrongAnswers++;
        }

        // ✅ RESET gotNitro TRƯỚC KHI tính toán
        state.gotNitro = false;

        // Calculate movement and points
        double movement = 0;
        int points = 0;

        if (isCorrect) {
            boolean isFastest = checkIfFastest(userId, timeTaken);

            if (isFastest) {
                movement = Protocol.NITRO_DISTANCE;
                points = Protocol.POINTS_NITRO;
                state.gotNitro = true;
                System.out.println("   🚀 Player " + userId + " NITRO: " + movement);
            } else {
                movement = Protocol.NORMAL_DISTANCE;
                points = Protocol.POINTS_CORRECT;
                System.out.println("   ✅ Player " + userId + " correct: " + movement);
            }

            state.correctStreak++;
            state.wrongStreak = 0;

            if (state.correctStreak == 3) {
                movement += 20;
                points += 20;
                System.out.println("   🔥 Player " + userId + " 3-streak bonus!");
            } else if (state.correctStreak == 5) {
                movement += 50;
                points += 50;
                System.out.println("   🔥🔥 Player " + userId + " 5-streak bonus!");
            }

        }else {
            movement = 0;
            points = Protocol.POINTS_WRONG;
            state.wrongStreak++;
            state.correctStreak = 0;
            System.out.println("   ❌ Player " + userId + " wrong: no movement");

            if (state.wrongStreak == 3) {
                movement = -30;
                System.out.println("   💥 Player " + userId + " 3 wrong streak penalty!");
            } else if (state.wrongStreak == 5) {
                movement = -50;
                System.out.println("   💥💥 Player " + userId + " 5 wrong streak penalty!");
            }
        }

        // Update position
        state.position += movement;
        state.position = Math.max(START_POSITION, state.position);
        state.position = Math.min(FINISH_LINE, state.position);
        state.score += points;

        System.out.println("   📍 Player " + userId + " position: " + state.position +
                " (score: " + state.score + ")");

        // ✅ Broadcast answer result to ALL players immediately
        if (answerBroadcaster != null) {
            answerBroadcaster.broadcastAnswer(roomId, userId, isCorrect, timeTaken,
                    state.position, state.score, state.gotNitro);
        }

        // ✅ Move to next question TRONG playerQuestionIndex
        int nextIndex = currentIndex + 1;
        playerQuestionIndex.put(userId, nextIndex);

        // ✅ Check if player finished all questions
        if (nextIndex >= questions.size()) {
            handlePlayerFinished(userId);
            return new AnswerResult(true,
                    "You finished all questions!",
                    timeTaken,
                    state.correctStreak);
        }

        // Check if reached finish line
        if (state.position >= FINISH_LINE) {
            finishPlayer(userId);
            return new AnswerResult(true,
                    "You reached the finish line! 🏆",
                    timeTaken,
                    state.correctStreak);
        }

        // ✅ Broadcast progress to OTHER players
        if (progressBroadcaster != null) {
            progressBroadcaster.broadcastProgress(roomId, userId, nextIndex);
        }

        // ✅ Schedule next question after 2s delay
        scheduler.schedule(() -> {
            if (gameState == GameState.PLAYING &&
                    !finishedPlayers.contains(userId) &&
                    !disconnectedPlayers.contains(userId)) {
                startQuestionForPlayer(userId);
            }
        }, 2, TimeUnit.SECONDS);

        return new AnswerResult(true,
                isCorrect ? "Correct!" : "Wrong!",
                timeTaken,
                state.correctStreak);
    }

    /**
     * ✅ Xử lý khi player hoàn thành tất cả câu hỏi hoặc đạt finish line
     * Called from submitAnswer() when player completes all questions or reaches finish line
     */
    private synchronized void handlePlayerFinished(int userId) {
        // ✅ Check if already finished to avoid duplicate processing
        if (finishedPlayers.contains(userId)) {
            System.out.println("⚠️ [handlePlayerFinished] Player " + userId + " already finished");
            return;
        }

        // ✅ Mark as finished
        finishedPlayers.add(userId);

        // ✅ Update player state
        PlayerGameState state = playerStates.get(userId);
        if (state != null) {
            state.finalRank = finishedPlayers.size();

            System.out.println("🏁 [GameSession] Player " + userId +
                    " (" + state.userId + ") FINISHED!");
            System.out.println("   Final Rank: " + state.finalRank);
            System.out.println("   Final Position: " + state.position);
            System.out.println("   Final Score: " + state.score);
            System.out.println("   Questions Completed: " + playerQuestionIndex.get(userId) +
                    "/" + questions.size());
        }

        // ✅ Cancel any pending question timer for this player
        ScheduledFuture<?> timer = playerQuestionTimers.remove(userId);
        if (timer != null && !timer.isDone()) {
            timer.cancel(false);
            System.out.println("   ⏹️ Cancelled pending timer for player " + userId);
        }

        // ✅ Remove question start time
        playerQuestionStartTime.remove(userId);

        // ✅ Notify THIS player they finished
        if (playerFinishNotifier != null) {
            System.out.println("   📤 Sending finish notification to player " + userId);
            playerFinishNotifier.notifyFinish(roomId, userId, finishedPlayers.size());
        }

        // ✅ Check if all active players have finished
        int totalActivePlayers = getActivePlayers().size();
        int finishedCount = finishedPlayers.size();

        System.out.println("   📊 Progress: " + finishedCount + "/" + totalActivePlayers + " players finished");

        if (finishedCount >= totalActivePlayers) {
            System.out.println("🏆 [GameSession] ALL PLAYERS FINISHED!");

            // ✅ Delay 3 seconds before ending game to let players see final results
            scheduler.schedule(() -> {
                if (gameState == GameState.PLAYING) {
                    System.out.println("   🏁 Ending game after delay...");
                    endGame("ALL_FINISHED");
                }
            }, 3, TimeUnit.SECONDS);
        }
    }

    /**
     * ✅ Check if this answer is fastest among all active players
     */
    private boolean checkIfFastest(int userId, long timeTaken) {
        // Check if any other player answered faster in the last 1 second
        long now = System.currentTimeMillis();

        for (Map.Entry<Integer, PlayerGameState> entry : playerStates.entrySet()) {
            int otherId = entry.getKey();
            if (otherId == userId) continue;
            if (disconnectedPlayers.contains(otherId)) continue;
            if (finishedPlayers.contains(otherId)) continue;

            PlayerGameState otherState = entry.getValue();
            if (otherState.lastAnswerTime > 0 &&
                    otherState.lastAnswerCorrect &&
                    otherState.lastAnswerTime < timeTaken) {

                Long otherStartTime = playerQuestionStartTime.get(otherId);
                if (otherStartTime != null && (now - otherStartTime) < 1000) {
                    return false; // Someone was faster
                }
            }
        }

        return true;
    }

    @FunctionalInterface
    public interface ProgressBroadcaster {
        void broadcastProgress(String roomId, int userId, int questionIndex);
    }

    private ProgressBroadcaster progressBroadcaster;

    public void setProgressBroadcaster(ProgressBroadcaster broadcaster) {
        this.progressBroadcaster = broadcaster;
    }

    /**
     * ✅ Move player to next question
     */
    @Deprecated
    private void moveToNextQuestion(int userId) {
        System.out.println("⚠️ [moveToNextQuestion] This method is deprecated");
    }

    /**
     * ✅ Mark player as finished
     */
    private synchronized void finishPlayer(int userId) {
        if (finishedPlayers.contains(userId)) return;

        finishedPlayers.add(userId);

        PlayerGameState state = playerStates.get(userId);
        if (state != null) {
            state.finalRank = finishedPlayers.size();
        }

        System.out.println("🏁 [GameSession] Player " + userId + " finished! Rank: " + finishedPlayers.size());

        // Cancel any pending timer
        ScheduledFuture<?> timer = playerQuestionTimers.remove(userId);
        if (timer != null) {
            timer.cancel(false);
        }

        // ✅ Notify player they finished
        if (playerFinishNotifier != null) {
            playerFinishNotifier.notifyFinish(roomId, userId, finishedPlayers.size());
        }

        // ✅ Check if all players finished
        int activePlayers = getActivePlayers().size();
        if (finishedPlayers.size() >= activePlayers) {
            System.out.println("🏆 [GameSession] All players finished!");
            endGame("ALL_FINISHED");
        }
    }

    /**
     * Kết thúc game
     */
    public void endGame(String reason) {
        if (gameState == GameState.FINISHED) return;

        gameState = GameState.FINISHED;

        // Cancel all timers
        for (ScheduledFuture<?> timer : playerQuestionTimers.values()) {
            if (timer != null) {
                timer.cancel(false);
            }
        }
        playerQuestionTimers.clear();

        System.out.println("🏁 [GameSession] Game ended! Reason: " + reason);

        // Calculate final rankings
        List<PlayerGameState> rankings = new ArrayList<>(playerStates.values());
        rankings.sort((a, b) -> {
            // Sort by position (descending), then by score (descending)
            int posCompare = Double.compare(b.position, a.position);
            if (posCompare != 0) return posCompare;
            return Integer.compare(b.score, a.score);
        });

        System.out.println("📊 [GameSession] Final Rankings:");
        for (int i = 0; i < rankings.size(); i++) {
            PlayerGameState state = rankings.get(i);
            if (state.finalRank == 0) {
                state.finalRank = i + 1;
            }
            System.out.println("   " + state.finalRank + ". Player " + state.userId +
                    " - Position: " + state.position + " - Score: " + state.score);
        }

        // Notify end game
        if (gameEndNotifier != null) {
            gameEndNotifier.notifyGameEnd(roomId, reason);
        }
    }

    /**
     * Player disconnect
     */
    public void playerDisconnected(int userId) {
        disconnectedPlayers.add(userId);

        // Cancel timer
        ScheduledFuture<?> timer = playerQuestionTimers.remove(userId);
        if (timer != null) {
            timer.cancel(false);
        }

        System.out.println("💔 [GameSession] Player " + userId + " disconnected");

        // Check if all players disconnected
        if (disconnectedPlayers.size() == playerStates.size()) {
            endGame("ALL_DISCONNECTED");
        }
    }

    /**
     * Cleanup
     */
    public void cleanup() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }
        playerQuestionTimers.clear();
        System.out.println("🧹 [GameSession] Cleaned up");
    }

    // ==================== CALLBACK INTERFACES ====================

    @FunctionalInterface
    public interface QuestionSender {
        void sendQuestion(String roomId, int userId, int questionIndex);
    }

    @FunctionalInterface
    public interface PositionBroadcaster {
        void broadcastPositions(String roomId);
    }

    @FunctionalInterface
    public interface PlayerFinishNotifier {
        void notifyFinish(String roomId, int userId, int rank);
    }

    @FunctionalInterface
    public interface GameEndNotifier {
        void notifyGameEnd(String roomId, String reason);
    }

    private QuestionSender questionSender;
    private PositionBroadcaster positionBroadcaster;
    private PlayerFinishNotifier playerFinishNotifier;
    private GameEndNotifier gameEndNotifier;

    public void setQuestionSender(QuestionSender sender) {
        this.questionSender = sender;
    }

    public void setPositionBroadcaster(PositionBroadcaster broadcaster) {
        this.positionBroadcaster = broadcaster;
    }

    public void setPlayerFinishNotifier(PlayerFinishNotifier notifier) {
        this.playerFinishNotifier = notifier;
    }

    public void setGameEndNotifier(GameEndNotifier notifier) {
        this.gameEndNotifier = notifier;
    }

    // ==================== GETTERS ====================

    public String getRoomId() { return roomId; }
    public String getSubject() { return subject; }
    public String getDifficulty() { return difficulty; }
    public GameState getGameState() { return gameState; }

    public Question getQuestionForPlayer(int userId) {
        Integer index = playerQuestionIndex.get(userId);

        if (index == null) {
            System.out.println("⚠️ [getQuestion] Player " + userId + " has null index");
            return null;
        }

        if (index < 0 || index >= questions.size()) {
            System.out.println("⚠️ [getQuestion] Player " + userId + " index out of bounds: " + index);
            return null;
        }

        Question q = questions.get(index);

        // ✅ Log để debug
        System.out.println("📖 [getQuestion] Player " + userId +
                " -> Q" + (index + 1) + " (ID: " +
                (q != null ? q.getQuestionId() : "null") + ")");

        return q;
    }

    public int getQuestionIndexForPlayer(int userId) {
        return playerQuestionIndex.getOrDefault(userId, 0);
    }

    public Map<Integer, PlayerGameState> getPlayerStates() { return playerStates; }
    public PlayerGameState getPlayerState(int userId) { return playerStates.get(userId); }

    public List<Integer> getActivePlayers() {
        List<Integer> active = new ArrayList<>();
        for (Integer userId : playerStates.keySet()) {
            if (!disconnectedPlayers.contains(userId)) {
                active.add(userId);
            }
        }
        return active;
    }

    public boolean isFinished() { return gameState == GameState.FINISHED; }
    public boolean hasPlayer(int userId) { return playerStates.containsKey(userId); }

    // ==================== INNER CLASSES ====================

    /**
     * Trạng thái của một player trong game
     */
    public static class PlayerGameState {
        public int userId;
        public double position;
        public int score;
        public int correctAnswers;
        public int wrongAnswers;
        public int correctStreak;
        public int wrongStreak;
        public String lastAnswer;
        public boolean lastAnswerCorrect;
        public long lastAnswerTime;
        public boolean gotNitro;
        public int finalRank;

        public int totalCorrectAnswers = 0;
        public int totalWrongAnswers = 0;
        public int totalQuestionsAttempted = 0;

        public PlayerGameState(int userId) {
            this.userId = userId;
            this.position = START_POSITION;
            this.score = 0;
            this.correctStreak = 0;
            this.wrongStreak = 0;
            this.gotNitro = false;
            this.finalRank = 0;
        }
    }

    /**
     * Kết quả trả lời
     */
    public static class AnswerResult {
        public boolean success;
        public String message;
        public long timeTaken;
        public int correctStreak;

        public AnswerResult(boolean success, String message, long timeTaken, int correctStreak) {
            this.success = success;
            this.message = message;
            this.timeTaken = timeTaken;
            this.correctStreak = correctStreak;
        }
    }

    /**
     * Get current question index for tracking
     */
    public int getCurrentQuestionIndex() {
        // Return the maximum question index any player has reached
        int maxIndex = 0;
        for (Integer index : playerQuestionIndex.values()) {
            if (index != null && index > maxIndex) {
                maxIndex = index;
            }
        }
        return maxIndex;
    }
}