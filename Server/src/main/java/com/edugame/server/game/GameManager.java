package com.edugame.server.game;

import com.edugame.common.Protocol;
import com.edugame.server.database.GameResultDAO;
import com.edugame.server.database.QuestionDAO;
import com.edugame.server.database.UserDAO;
import com.edugame.server.model.GameSession;
import com.edugame.server.model.Question;
import com.edugame.server.model.User;
import com.edugame.server.network.ClientHandler;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GameManager - Quản lý tất cả game sessions
 *
 * Responsibilities:
 * - Tạo và quản lý game sessions
 * - Load questions từ database
 * - Điều phối game flow
 * - Broadcast updates to players
 * - Save game results
 */
public class GameManager {

    private static final DateTimeFormatter LOG_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private static GameManager instance;

    private final Map<String, GameSession> activeSessions; // roomId -> GameSession
    private final QuestionDAO questionDAO;
    private final UserDAO userDAO;
    private final GameResultDAO gameResultDAO;

    private GameManager() {
        this.activeSessions = new ConcurrentHashMap<>();
        this.questionDAO = new QuestionDAO();
        this.userDAO = new UserDAO();
        this.gameResultDAO = new GameResultDAO();
        logWithTime("✅ GameManager initialized");
    }

    public static synchronized GameManager getInstance() {
        if (instance == null) {
            instance = new GameManager();
        }
        return instance;
    }

    // ==================== GAME LIFECYCLE ====================

    /**
     * Tạo game session mới và bắt đầu countdown
     */
    public boolean startGame(String roomId, String subject, String difficulty,
                             List<ClientHandler> players) {
        try {
            logWithTime("🎮 [GameManager] Starting game for room: " + roomId);

            // Validate
            if (players.size() < 2) {
                logWithTime("❌ Not enough players: " + players.size());
                return false;
            }

            if (activeSessions.containsKey(roomId)) {
                logWithTime("⚠️ Game already active for room: " + roomId);
                return false;
            }

            // Load questions
            List<Question> questions = loadQuestions(subject, difficulty);
            if (questions.isEmpty()) {
                logWithTime("❌ No questions found for " + subject + "/" + difficulty);
                return false;
            }

            // Get player IDs
            List<Integer> playerIds = new ArrayList<>();
            for (ClientHandler handler : players) {
                User user = handler.getCurrentUser();
                if (user != null) {
                    playerIds.add(user.getUserId());
                }
            }

            // Create game session
            GameSession session = new GameSession(roomId, subject, difficulty, questions, playerIds);
            activeSessions.put(roomId, session);

            logWithTime("✅ [GameManager] Game session created successfully");
            logWithTime("   Players: " + playerIds);
            logWithTime("   Questions loaded: " + questions.size());

            // Start countdown
            session.startCountdown();

            return true;

        } catch (Exception e) {
            logWithTime("❌ [GameManager] Error starting game: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Sau countdown, bắt đầu game thật
     */
    public void beginGameAfterCountdown(String roomId) {
        GameSession session = activeSessions.get(roomId);
        if (session != null) {
            session.startGame();
            logWithTime("🏁 [GameManager] Game started after countdown: " + roomId);
        }
    }

    /**
     * Submit answer
     */
    public GameSession.AnswerResult submitAnswer(String roomId, int userId, String answer) {
        GameSession session = activeSessions.get(roomId);

        if (session == null) {
            return new GameSession.AnswerResult(false, "Game not found", 0, 0);
        }

        return session.submitAnswer(userId, answer);
    }

    /**
     * Player disconnected
     */
    public void handlePlayerDisconnect(String roomId, int userId) {
        GameSession session = activeSessions.get(roomId);
        if (session != null) {
            session.playerDisconnected(userId);
            logWithTime("💔 [GameManager] Player " + userId + " disconnected from game");
        }
    }

    /**
     * End game và save results
     */
    public void endGame(String roomId, List<ClientHandler> players) {
        GameSession session = activeSessions.get(roomId);
        if (session == null) return;

        try {
            logWithTime("🏁 [GameManager] Ending game: " + roomId);

            // Get final results
            Map<Integer, GameSession.PlayerGameState> playerStates = session.getPlayerStates();

            // Save to database
            saveGameResults(roomId, session, players);

            // Update player statistics
            updatePlayerStats(playerStates);

            // Cleanup
            session.cleanup();
            activeSessions.remove(roomId);

            logWithTime("✅ [GameManager] Game ended and results saved");

        } catch (Exception e) {
            logWithTime("❌ [GameManager] Error ending game: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ==================== DATABASE OPERATIONS ====================

    /**
     * Load questions từ database
     */
    private List<Question> loadQuestions(String subject, String difficulty) {
        try {
            logWithTime("📚 [GameManager] Loading questions...");
            logWithTime("   Subject: " + subject + " | Difficulty: " + difficulty);

            List<Question> questions = questionDAO.getRandomQuestions(
                    subject,
                    difficulty,
                    Protocol.QUESTIONS_PER_GAME
            );

            if (questions.isEmpty()) {
                logWithTime("⚠️ No questions found! Loading from any difficulty...");
                // Fallback: load from any difficulty
                questions = questionDAO.getRandomQuestions(subject, null, Protocol.QUESTIONS_PER_GAME);
            }

            logWithTime("✅ Loaded " + questions.size() + " questions");
            return questions;

        } catch (Exception e) {
            logWithTime("❌ Error loading questions: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    /**
     * Save game results to database
     */
    private void saveGameResults(String roomId, GameSession session, List<ClientHandler> players) {
        try {
            logWithTime("💾 [GameManager] Saving game results...");

            Map<Integer, GameSession.PlayerGameState> playerStates = session.getPlayerStates();

            for (ClientHandler handler : players) {
                User user = handler.getCurrentUser();
                if (user == null) continue;

                int userId = user.getUserId();
                GameSession.PlayerGameState state = playerStates.get(userId);
                if (state == null) continue;

                // Determine win/loss
                boolean isWinner = (state.finalRank == 1);

                // Save to game_results table (if exists)
                try {
                    gameResultDAO.saveGameResult(
                            userId,
                            roomId,
                            session.getSubject(),
                            session.getDifficulty(),
                            state.score,
                            state.finalRank,
                            isWinner,
                            (int)state.position,
                            session.getCurrentQuestionIndex()
                    );
                } catch (Exception e) {
                    logWithTime("⚠️ Could not save to game_results: " + e.getMessage());
                }

                logWithTime("   ✅ Saved results for user " + userId +
                        " | Rank: " + state.finalRank +
                        " | Score: " + state.score);
            }

        } catch (Exception e) {
            logWithTime("❌ Error saving game results: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Update player statistics
     */
    private void updatePlayerStats(Map<Integer, GameSession.PlayerGameState> playerStates) {
        try {
            logWithTime("📊 [GameManager] Updating player statistics...");

            for (GameSession.PlayerGameState state : playerStates.values()) {
                try {
                    // Update total score
                    userDAO.updateTotalScore(state.userId, state.score);

                    // Update win/loss stats
                    boolean isWinner = (state.finalRank == 1);
                    userDAO.updateGameStats(state.userId, isWinner);

                    logWithTime("   ✅ Updated user " + state.userId +
                            " | Score: +" + state.score +
                            " | " + (isWinner ? "WIN" : "LOSS"));

                } catch (Exception e) {
                    logWithTime("   ⚠️ Could not update user " + state.userId + ": " + e.getMessage());
                }
            }

        } catch (Exception e) {
            logWithTime("❌ Error updating stats: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ==================== GETTERS ====================

    public GameSession getSession(String roomId) {
        return activeSessions.get(roomId);
    }

    public boolean hasActiveSession(String roomId) {
        return activeSessions.containsKey(roomId);
    }

    public Map<String, GameSession> getAllSessions() {
        return new HashMap<>(activeSessions);
    }

    // ==================== HELPERS ====================

    private void logWithTime(String message) {
        String timestamp = LocalDateTime.now().format(LOG_TIME_FORMAT);
        System.out.println("[" + timestamp + "] [GameManager] " + message);
    }
}