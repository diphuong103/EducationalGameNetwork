package com.edugame.server.game;

import com.edugame.common.Protocol;
import com.edugame.server.database.GameResultDAO;
import com.edugame.server.database.GameSessionDAO;
import com.edugame.server.database.QuestionDAO;
import com.edugame.server.database.UserDAO;
import com.edugame.server.model.GameSession;
import com.edugame.server.model.Question;
import com.edugame.server.model.User;
import com.edugame.server.network.ClientHandler;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ✅ FIXED: GameManager with startGameWithQuestions() for matchmaking
 */
public class GameManager {

    private static final DateTimeFormatter LOG_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private static GameManager instance;

    private final Map<String, GameSession> activeSessions;
    private final QuestionDAO questionDAO;
    private final UserDAO userDAO;
    private final GameResultDAO gameResultDAO;
    private final GameSessionDAO gameSessionDAO;

    private GameManager() throws SQLException {
        this.activeSessions = new ConcurrentHashMap<>();
        this.questionDAO = new QuestionDAO();
        this.userDAO = new UserDAO();
        this.gameResultDAO = new GameResultDAO();
        this.gameSessionDAO = new GameSessionDAO();
        logWithTime("✅ GameManager initialized");
    }

    public static synchronized GameManager getInstance() throws SQLException {
        if (instance == null) {
            instance = new GameManager();
        }
        return instance;
    }

    /**
     * ✅ ORIGINAL: Start game (load questions from DB)
     */
    public boolean startGame(String roomId, String subject, String difficulty,
                             List<ClientHandler> players) {
        try {
            logWithTime("🎮 [GameManager] Starting game for room: " + roomId);

            if (players.size() < 2) {
                logWithTime("❌ Not enough players: " + players.size());
                return false;
            }

            if (activeSessions.containsKey(roomId)) {
                logWithTime("⚠️ Game already active for room: " + roomId);
                return false;
            }

            // Create database session
            int sessionId = gameSessionDAO.createSession(
                    roomId,
                    subject,
                    difficulty,
                    Protocol.QUESTIONS_PER_GAME
            );

            if (sessionId <= 0) {
                logWithTime("❌ Failed to create database session");
                return false;
            }

            logWithTime("✅ Database session created: ID=" + sessionId);

            // Load questions from DB
            List<Question> questions = loadQuestions(subject, difficulty);
            if (questions.isEmpty()) {
                logWithTime("❌ No questions found for " + subject + "/" + difficulty);
                return false;
            }

            Collections.shuffle(questions);
            logWithTime("🎲 Questions shuffled: " + questions.size());

            // Use shared method
            return createGameSession(roomId, sessionId, subject, difficulty,
                    questions, players);

        } catch (Exception e) {
            logWithTime("❌ [GameManager] Error starting game: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * ✅ NEW: Start game with pre-loaded questions (for matchmaking)
     */
    public boolean startGameWithQuestions(String roomId, String subject,
                                          String difficulty,
                                          List<ClientHandler> players,
                                          List<Question> questions) {
        try {
            logWithTime("🎮 [GameManager] Starting game WITH QUESTIONS for room: " + roomId);

            if (players.size() < 2) {
                logWithTime("❌ Not enough players: " + players.size());
                return false;
            }

            if (activeSessions.containsKey(roomId)) {
                logWithTime("⚠️ Game already active for room: " + roomId);
                return false;
            }

            if (questions == null || questions.size() < Protocol.QUESTIONS_PER_GAME) {
                logWithTime("❌ Invalid questions: " +
                        (questions != null ? questions.size() : "null"));
                return false;
            }

            // Create database session
            int sessionId = gameSessionDAO.createSession(
                    roomId,
                    subject,
                    difficulty,
                    Protocol.QUESTIONS_PER_GAME
            );

            if (sessionId <= 0) {
                logWithTime("❌ Failed to create database session");
                return false;
            }

            logWithTime("✅ Database session created: ID=" + sessionId);
            logWithTime("✅ Using pre-loaded questions: " + questions.size());

            // Use shared method
            return createGameSession(roomId, sessionId, subject, difficulty,
                    questions, players);

        } catch (Exception e) {
            logWithTime("❌ [GameManager] Error starting game with questions: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * ✅ SHARED: Create GameSession (used by both methods)
     */
    private boolean createGameSession(String roomId, int sessionId, String subject,
                                      String difficulty, List<Question> questions,
                                      List<ClientHandler> players) {
        try {
            // Get player IDs
            List<Integer> playerIds = new ArrayList<>();
            for (ClientHandler handler : players) {
                User user = handler.getCurrentUser();
                if (user != null) {
                    playerIds.add(user.getUserId());
                }
            }

            // Create GameSession
            GameSession session = new GameSession(
                    roomId,
                    sessionId,
                    subject,
                    difficulty,
                    System.currentTimeMillis(),
                    questions,
                    playerIds
            );

            // Setup callbacks
            setupSessionCallbacks(session, players);

            activeSessions.put(roomId, session);

            logWithTime("✅ [GameManager] Game session created successfully");
            logWithTime("   Session ID: " + sessionId);
            logWithTime("   Room ID: " + roomId);
            logWithTime("   Players: " + playerIds);
            logWithTime("   Questions: " + questions.size());

            // Start countdown
            session.startCountdown();

            return true;

        } catch (Exception e) {
            logWithTime("❌ [GameManager] Error creating game session: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * ✅ SHARED: Setup all session callbacks
     */
    private void setupSessionCallbacks(GameSession session, List<ClientHandler> players) {
        String roomId = session.getRoomId();

        // Question sender
        session.setQuestionSender((rid, userId, questionIndex) -> {
            for (ClientHandler handler : players) {
                if (handler.getCurrentUser() != null &&
                        handler.getCurrentUser().getUserId() == userId) {
                    handler.sendQuestionToPlayerDirect(rid, userId, questionIndex);
                    break;
                }
            }
        });

        // Position broadcaster
        session.setPositionBroadcaster((rid) -> {
            if (!players.isEmpty()) {
                players.get(0).broadcastPositions(rid, players);
            }
        });

        // Answer broadcaster
        session.setAnswerBroadcaster((rid, userId, isCorrect, timeTaken, position, score, gotNitro) -> {
            for (ClientHandler handler : players) {
                if (handler.getCurrentUser() != null) {
                    handler.broadcastAnswerResult(rid, userId, isCorrect, timeTaken,
                            position, score, gotNitro, players);
                    break;
                }
            }
        });

        // Progress broadcaster
        session.setProgressBroadcaster((rid, userId, questionIndex) -> {
            for (ClientHandler handler : players) {
                if (handler.getCurrentUser() != null) {
                    handler.broadcastQuestionProgress(rid, userId, questionIndex, players);
                    break;
                }
            }
        });

        // Player finish notifier
        session.setPlayerFinishNotifier((rid, userId, rank) -> {
            for (ClientHandler handler : players) {
                if (handler.getCurrentUser() != null &&
                        handler.getCurrentUser().getUserId() == userId) {
                    handler.notifyPlayerFinish(rid, userId, rank);
                    break;
                }
            }
        });

        // Game end notifier
        session.setGameEndNotifier((rid, reason) -> {
            logWithTime("🏁 [GameEndNotifier] Game ending: " + reason);

            GameSession endingSession = activeSessions.get(rid);
            if (endingSession != null) {
                saveAndBroadcastResults(rid, endingSession, players, reason);
            }
        });
    }

    /**
     * Lấy danh sách tất cả các session đang hoạt động
     */
    public Collection<GameSession> getAllSessions() {
        return activeSessions.values();
    }

    /**
     * ✅ Save results and broadcast in ONE method
     */
    private void saveAndBroadcastResults(String roomId, GameSession session,
                                         List<ClientHandler> players, String reason) {
        try {
            logWithTime("💾 [SaveAndBroadcast] Processing for room: " + roomId);

            int sessionId = session.getSessionId();

            if (sessionId <= 0) {
                logWithTime("❌ Invalid session ID: " + sessionId);
                return;
            }

            logWithTime("   Session ID: " + sessionId);

            long startTime = session.getStartTimeMillis();
            long endTime = System.currentTimeMillis();
            int timeTaken = (int)((endTime - startTime) / 1000);

            Map<Integer, GameSession.PlayerGameState> playerStates = session.getPlayerStates();

            List<GameSession.PlayerGameState> sortedStates = new ArrayList<>(playerStates.values());
            sortedStates.sort((a, b) -> {
                int posCompare = Double.compare(b.position, a.position);
                if (posCompare != 0) return posCompare;
                return Integer.compare(b.score, a.score);
            });

            List<Map<String, Object>> rankings = new ArrayList<>();

            for (int i = 0; i < sortedStates.size(); i++) {
                GameSession.PlayerGameState state = sortedStates.get(i);
                int rank = i + 1;
                state.finalRank = rank;

                String username = "";
                String fullName = "";
                for (ClientHandler handler : players) {
                    if (handler.getCurrentUser() != null &&
                            handler.getCurrentUser().getUserId() == state.userId) {
                        username = handler.getCurrentUser().getUsername();
                        fullName = handler.getCurrentUser().getFullName();
                        break;
                    }
                }

                Map<String, Object> rankData = new HashMap<>();
                rankData.put("rank", rank);
                rankData.put("userId", state.userId);
                rankData.put("username", username);
                rankData.put("fullName", fullName);
                rankData.put("position", state.position);
                rankData.put("score", state.score);
                rankData.put("correctAnswers", state.totalCorrectAnswers);
                rankData.put("wrongAnswers", state.totalWrongAnswers);
                rankData.put("totalQuestions", state.totalQuestionsAttempted);

                rankings.add(rankData);

                try {
                    boolean saved = gameResultDAO.saveGameResult(
                            sessionId,
                            state.userId,
                            state.score,
                            state.totalCorrectAnswers,
                            state.totalWrongAnswers,
                            timeTaken,
                            rank
                    );

                    if (saved) {
                        logWithTime("   💾 ✅ User " + state.userId + " saved (Rank " + rank + ")");
                    }
                } catch (Exception e) {
                    logWithTime("   💾 ❌ Failed to save user " + state.userId + ": " + e.getMessage());
                }

                try {
                    userDAO.updateTotalScore(state.userId, state.score);
                    userDAO.updateSubjectScore(state.userId, session.getSubject(), state.score);
                    userDAO.updateGameStats(state.userId, rank == 1);

                    logWithTime("   📊 ✅ Stats updated for user " + state.userId);
                } catch (Exception e) {
                    logWithTime("   📊 ⚠️ Could not update stats: " + e.getMessage());
                }
            }

            try {
                gameSessionDAO.finishSession(sessionId);
                logWithTime("   🏁 Session " + sessionId + " marked as finished");
            } catch (Exception e) {
                logWithTime("   ⚠️ Could not mark session finished: " + e.getMessage());
            }

            Map<String, Object> endGameData = new HashMap<>();
            endGameData.put("type", Protocol.GAME_END);
            endGameData.put("roomId", roomId);
            endGameData.put("sessionId", sessionId);
            endGameData.put("reason", reason);
            endGameData.put("rankings", rankings);
            endGameData.put("subject", session.getSubject());
            endGameData.put("difficulty", session.getDifficulty());
            endGameData.put("totalTime", timeTaken);
            endGameData.put("timestamp", System.currentTimeMillis());

            int sentCount = 0;
            for (ClientHandler player : players) {
                if (player.getCurrentUser() != null) {
                    try {
                        player.sendMessage(endGameData);
                        sentCount++;
                        logWithTime("   📤 Sent to: " + player.getCurrentUser().getUsername());
                    } catch (Exception e) {
                        logWithTime("   ⚠️ Failed to send: " + e.getMessage());
                    }
                }
            }

            logWithTime("✅ [SaveAndBroadcast] Complete!");
            logWithTime("   Results saved: " + sortedStates.size());
            logWithTime("   Notifications sent: " + sentCount + "/" + players.size());

        } catch (Exception e) {
            logWithTime("❌ [SaveAndBroadcast] Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Cleanup session after results are saved
     */
    public void endGame(String roomId, List<ClientHandler> players) {
        GameSession session = activeSessions.get(roomId);
        if (session == null) return;

        try {
            logWithTime("🧹 [GameManager] Cleaning up session: " + roomId);

            session.cleanup();
            activeSessions.remove(roomId);

            logWithTime("✅ [GameManager] Session cleaned up");

        } catch (Exception e) {
            logWithTime("❌ [GameManager] Error cleaning up: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void beginGameAfterCountdown(String roomId) {
        GameSession session = activeSessions.get(roomId);
        if (session != null) {
            session.startGame();
            logWithTime("🏁 Game started after countdown: " + roomId);
        }
    }

    public GameSession.AnswerResult submitAnswer(String roomId, int userId, String answer) {
        GameSession session = activeSessions.get(roomId);
        if (session == null) {
            return new GameSession.AnswerResult(false, "Game not found", 0, 0);
        }
        return session.submitAnswer(userId, answer);
    }

    public void handlePlayerDisconnect(String roomId, int userId) {
        GameSession session = activeSessions.get(roomId);
        if (session != null) {
            session.playerDisconnected(userId);
            logWithTime("💔 Player " + userId + " disconnected");
        }
    }

    private List<Question> loadQuestions(String subject, String difficulty) {
        try {
            logWithTime("📚 Loading questions: " + subject + "/" + difficulty);

            List<Question> questions = questionDAO.getRandomQuestions(
                    subject,
                    difficulty,
                    Protocol.QUESTIONS_PER_GAME
            );

            if (questions.isEmpty()) {
                logWithTime("⚠️ No questions found, trying any difficulty...");
                questions = questionDAO.getRandomQuestions(subject, null, Protocol.QUESTIONS_PER_GAME);
            }

            logWithTime("✅ Loaded " + questions.size() + " questions");
            return questions;

        } catch (Exception e) {
            logWithTime("❌ Error loading questions: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public GameSession getSession(String roomId) {
        return activeSessions.get(roomId);
    }

    public boolean hasActiveSession(String roomId) {
        return activeSessions.containsKey(roomId);
    }

    private void logWithTime(String message) {
        String timestamp = LocalDateTime.now().format(LOG_TIME_FORMAT);
        System.out.println("[" + timestamp + "] [GameManager] " + message);
    }
}