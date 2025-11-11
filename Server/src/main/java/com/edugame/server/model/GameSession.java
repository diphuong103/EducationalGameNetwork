package com.edugame.server.model;

import com.edugame.common.Protocol;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

/**
 * GameSession - Quản lý trạng thái của một trận game
 *
 * Lifecycle:
 * COUNTDOWN (10s) → PLAYING (câu hỏi) → QUESTION_RESULT (2s) → Next question → FINISHED
 */
public class GameSession {

    // ==================== GAME INFO ====================
    private final String roomId;
    private final String subject;
    private final String difficulty;
    private final LocalDateTime startTime;

    // ==================== QUESTIONS ====================
    private final List<Question> questions;
    private int currentQuestionIndex;
    private long questionStartTime;
    private final int questionTimeLimit = Protocol.QUESTION_TIME_LIMIT; // 10s

    // ==================== PLAYERS ====================
    private final Map<Integer, PlayerGameState> playerStates; // userId -> state
    private final Map<Integer, Boolean> playerAnswered; // userId -> has answered this question
    private final Map<Integer, Long> answerTimes; // userId -> timestamp when answered
    private final Set<Integer> disconnectedPlayers;

    // ==================== GAME STATE ====================
    private GameState gameState;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> questionTimer;
    private long gameStartTime;
    private final long gameDuration = Protocol.GAME_DURATION * 1000L; // 5 minutes in ms

    // ==================== CONSTANTS ====================
    private static final double FINISH_LINE = Protocol.FINISH_LINE;
    private static final double START_POSITION = 0.0;

    public enum GameState {
        COUNTDOWN,       // Đếm ngược 10s trước khi bắt đầu
        PLAYING,         // Đang chơi (hiện câu hỏi)
        QUESTION_RESULT, // Hiện kết quả câu hỏi (2s)
        FINISHED         // Game kết thúc
    }

    /**
     * Constructor
     */
    public GameSession(String roomId, String subject, String difficulty, List<Question> questions, List<Integer> playerIds) {
        this.roomId = roomId;
        this.subject = subject;
        this.difficulty = difficulty;
        this.questions = questions;
        this.currentQuestionIndex = 0;
        this.startTime = LocalDateTime.now();

        this.playerStates = new ConcurrentHashMap<>();
        this.playerAnswered = new ConcurrentHashMap<>();
        this.answerTimes = new ConcurrentHashMap<>();
        this.disconnectedPlayers = ConcurrentHashMap.newKeySet();

        // Initialize player states
        for (Integer userId : playerIds) {
            playerStates.put(userId, new PlayerGameState(userId));
        }

        this.gameState = GameState.COUNTDOWN;
        this.scheduler = Executors.newScheduledThreadPool(1);

        System.out.println("✅ [GameSession] Created for room " + roomId);
        System.out.println("   Subject: " + subject + " | Difficulty: " + difficulty);
        System.out.println("   Players: " + playerIds.size() + " | Questions: " + questions.size());
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
        System.out.println("🎮 [GameSession] Game started!");

        // Start first question
        startQuestion();
    }

    /**
     * Bắt đầu câu hỏi mới
     */
    public void startQuestion() {
        if (currentQuestionIndex >= questions.size()) {
            endGame("ALL_QUESTIONS_COMPLETED");
            return;
        }

        gameState = GameState.PLAYING;
        questionStartTime = System.currentTimeMillis();

        // Reset answer tracking
        playerAnswered.clear();
        answerTimes.clear();

        System.out.println("❓ [GameSession] Question " + (currentQuestionIndex + 1) + "/" + questions.size());

        // Set timeout for question (10s)
        questionTimer = scheduler.schedule(() -> {
            System.out.println("⏰ [GameSession] Question timeout!");
            processQuestionResults();
        }, questionTimeLimit, TimeUnit.SECONDS);
    }

    /**
     * Xử lý câu trả lời của player
     */
    public synchronized AnswerResult submitAnswer(int userId, String answer) {
        // Validate
        if (gameState != GameState.PLAYING) {
            return new AnswerResult(false, "Game not in playing state", 0, 0);
        }

        if (playerAnswered.containsKey(userId)) {
            return new AnswerResult(false, "Already answered", 0, 0);
        }

        if (disconnectedPlayers.contains(userId)) {
            return new AnswerResult(false, "Player disconnected", 0, 0);
        }

        // Record answer
        long answerTime = System.currentTimeMillis();
        playerAnswered.put(userId, true);
        answerTimes.put(userId, answerTime);

        PlayerGameState state = playerStates.get(userId);
        if (state == null) {
            return new AnswerResult(false, "Player not found", 0, 0);
        }

        // Check correct answer
        Question currentQuestion = questions.get(currentQuestionIndex);
        boolean isCorrect = answer.equalsIgnoreCase(currentQuestion.getCorrectAnswer());

        long timeTaken = answerTime - questionStartTime;

        // Update player state
        state.lastAnswer = answer;
        state.lastAnswerCorrect = isCorrect;
        state.lastAnswerTime = timeTaken;

        // Update streak
        if (isCorrect) {
            state.correctStreak++;
            state.wrongStreak = 0;
        } else {
            state.wrongStreak++;
            state.correctStreak = 0;
        }

        System.out.println("📝 [GameSession] Player " + userId + " answered: " + answer +
                " (" + (isCorrect ? "✅ CORRECT" : "❌ WRONG") + ") in " + timeTaken + "ms");

        // Check if all players answered
        if (playerAnswered.size() == getActivePlayers().size()) {
            System.out.println("✅ [GameSession] All players answered!");
            if (questionTimer != null) {
                questionTimer.cancel(false);
            }
            processQuestionResults();
        }

        return new AnswerResult(true, isCorrect ? "Correct!" : "Wrong!", timeTaken, state.correctStreak);
    }

    /**
     * Xử lý kết quả câu hỏi và cập nhật vị trí
     * ✅ ADD: Callback to notify server to broadcast results
     */
    private ResultBroadcaster resultBroadcaster;

    public void setResultBroadcaster(ResultBroadcaster broadcaster) {
        this.resultBroadcaster = broadcaster;
    }

    @FunctionalInterface
    public interface ResultBroadcaster {
        void broadcastResults(String roomId);
    }

    private synchronized void processQuestionResults() {
        if (gameState != GameState.PLAYING) return;

        gameState = GameState.QUESTION_RESULT;

        Question currentQuestion = questions.get(currentQuestionIndex);

        // Find fastest correct answer
        long fastestTime = Long.MAX_VALUE;
        Integer fastestPlayer = null;

        for (Map.Entry<Integer, PlayerGameState> entry : playerStates.entrySet()) {
            int userId = entry.getKey();
            PlayerGameState state = entry.getValue();

            if (playerAnswered.containsKey(userId) && state.lastAnswerCorrect) {
                long time = answerTimes.getOrDefault(userId, Long.MAX_VALUE);
                if (time < fastestTime) {
                    fastestTime = time;
                    fastestPlayer = userId;
                }
            }
        }

        // Calculate new positions
        for (Map.Entry<Integer, PlayerGameState> entry : playerStates.entrySet()) {
            int userId = entry.getKey();
            PlayerGameState state = entry.getValue();

            if (disconnectedPlayers.contains(userId)) {
                continue;
            }

            double movement = 0;
            int points = 0;
            boolean gotNitro = false;

            if (!playerAnswered.containsKey(userId)) {
                // Timeout - penalty
                movement = Protocol.PENALTY_DISTANCE;
                points = Protocol.POINTS_TIMEOUT;
                System.out.println("   ⏰ Player " + userId + " timeout: " + movement);

            } else if (state.lastAnswerCorrect) {
                // Correct answer
                if (userId == fastestPlayer) {
                    // NITRO BOOST
                    movement = Protocol.NITRO_DISTANCE;
                    points = Protocol.POINTS_NITRO;
                    gotNitro = true;
                    System.out.println("   🚀 Player " + userId + " NITRO: " + movement);
                } else {
                    // Normal correct
                    movement = Protocol.NORMAL_DISTANCE;
                    points = Protocol.POINTS_CORRECT;
                    System.out.println("   ✅ Player " + userId + " correct: " + movement);
                }

                // Bonus for streaks
                if (state.correctStreak == 3) {
                    movement += 20; // Bonus for 3 streak
                    points += 20;
                    System.out.println("   🔥 Player " + userId + " 3-streak bonus!");
                } else if (state.correctStreak == 5) {
                    movement += 50; // Bigger bonus for 5 streak
                    points += 50;
                    System.out.println("   🔥🔥 Player " + userId + " 5-streak bonus!");
                }

            } else {
                // Wrong answer - no movement
                movement = 0;
                points = Protocol.POINTS_WRONG;
                System.out.println("   ❌ Player " + userId + " wrong: no movement");

                // Penalty for wrong streaks
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
            state.position = Math.max(START_POSITION, state.position); // Don't go below start
            state.position = Math.min(FINISH_LINE, state.position); // Don't exceed finish
            state.score += points;
            state.gotNitro = gotNitro;

            System.out.println("   📍 Player " + userId + " position: " + state.position + " (score: " + state.score + ")");
        }

        // Check for winners
        checkWinners();

        // Move to next question after 2s
        currentQuestionIndex++;

        if (gameState != GameState.FINISHED) {
            scheduler.schedule(() -> {
                startQuestion();
            }, 2, TimeUnit.SECONDS);
        }
    }

    /**
     * Kiểm tra người thắng
     */
    private void checkWinners() {
        List<Integer> winners = new ArrayList<>();

        for (Map.Entry<Integer, PlayerGameState> entry : playerStates.entrySet()) {
            if (entry.getValue().position >= FINISH_LINE) {
                winners.add(entry.getKey());
            }
        }

        if (!winners.isEmpty()) {
            System.out.println("🏆 [GameSession] Winners found: " + winners);
            endGame("FINISH_LINE");
        }

        // Check time limit
        long elapsed = System.currentTimeMillis() - gameStartTime;
        if (elapsed >= gameDuration) {
            System.out.println("⏰ [GameSession] Time limit reached!");
            endGame("TIME_UP");
        }
    }

    /**
     * Kết thúc game
     */
    public void endGame(String reason) {
        gameState = GameState.FINISHED;

        if (questionTimer != null) {
            questionTimer.cancel(false);
        }

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
            state.finalRank = i + 1;
            System.out.println("   " + (i + 1) + ". Player " + state.userId +
                    " - Position: " + state.position + " - Score: " + state.score);
        }
    }

    /**
     * Player disconnect
     */
    public void playerDisconnected(int userId) {
        disconnectedPlayers.add(userId);
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
        System.out.println("🧹 [GameSession] Cleaned up");
    }

    // ==================== GETTERS ====================

    public String getRoomId() { return roomId; }
    public String getSubject() { return subject; }
    public String getDifficulty() { return difficulty; }
    public GameState getGameState() { return gameState; }
    public int getCurrentQuestionIndex() { return currentQuestionIndex; }
    public Question getCurrentQuestion() {
        if (currentQuestionIndex < questions.size()) {
            return questions.get(currentQuestionIndex);
        }
        return null;
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

    // ==================== INNER CLASSES ====================

    /**
     * Trạng thái của một player trong game
     */
    public static class PlayerGameState {
        public int userId;
        public double position;
        public int score;
        public int correctStreak;
        public int wrongStreak;
        public String lastAnswer;
        public boolean lastAnswerCorrect;
        public long lastAnswerTime;
        public boolean gotNitro;
        public int finalRank;

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

}