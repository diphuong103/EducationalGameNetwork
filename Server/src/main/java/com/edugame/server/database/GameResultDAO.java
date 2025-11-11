package com.edugame.server.database;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * GameResultDAO - Lưu và truy vấn kết quả game
 *
 * Schema của bạn:
 * game_results (
 *     result_id INT AI PK,
 *     session_id INT,
 *     user_id INT,
 *     score INT,
 *     correct_answers INT,
 *     wrong_answers INT,
 *     time_taken INT,
 *     rank_position INT,
 *     created_at TIMESTAMP
 * )
 */
public class GameResultDAO {

    /**
     * Lưu kết quả game - phù hợp với schema hiện tại
     */
    public boolean saveGameResult(int userId, String roomId, String subject,
                                  String difficulty, int score, int rank,
                                  boolean isWinner, int finalPosition,
                                  int questionsAnswered) {

        // Tính correct_answers và wrong_answers từ questionsAnswered
        // (Giả sử mỗi câu đúng = +60 điểm, mỗi câu sai = 0 điểm)
        int correctAnswers = score / 60; // Ước lượng số câu đúng
        int wrongAnswers = questionsAnswered - correctAnswers;

        String query = """
            INSERT INTO game_results (
                session_id, user_id, score, correct_answers, 
                wrong_answers, time_taken, rank_position, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, NOW())
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            // session_id: chuyển roomId thành integer (lấy phần số)
            int sessionId;
            try {
                sessionId = Integer.parseInt(roomId.replaceAll("[^0-9]", ""));
            } catch (NumberFormatException e) {
                sessionId = roomId.hashCode(); // Fallback
            }

            stmt.setInt(1, sessionId);
            stmt.setInt(2, userId);
            stmt.setInt(3, score);
            stmt.setInt(4, correctAnswers);
            stmt.setInt(5, wrongAnswers);
            stmt.setInt(6, 0); // time_taken - có thể tính sau
            stmt.setInt(7, rank);

            int rows = stmt.executeUpdate();

            if (rows > 0) {
                System.out.println("✅ [GameResultDAO] Saved result for user " + userId);
                System.out.println("   Score: " + score + " | Rank: " + rank +
                        " | Correct: " + correctAnswers + "/" + questionsAnswered);
                return true;
            }

        } catch (SQLException e) {
            System.err.println("❌ [GameResultDAO] Error saving result: " + e.getMessage());
            e.printStackTrace();
            // Don't throw - game should continue even if save fails
        }

        return false;
    }

    /**
     * Lưu kết quả game - phiên bản đầy đủ với thời gian
     */
    public boolean saveGameResultFull(int sessionId, int userId, int score,
                                      int correctAnswers, int wrongAnswers,
                                      int timeTaken, int rankPosition) {
        String query = """
            INSERT INTO game_results (
                session_id, user_id, score, correct_answers, 
                wrong_answers, time_taken, rank_position, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, NOW())
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setInt(1, sessionId);
            stmt.setInt(2, userId);
            stmt.setInt(3, score);
            stmt.setInt(4, correctAnswers);
            stmt.setInt(5, wrongAnswers);
            stmt.setInt(6, timeTaken);
            stmt.setInt(7, rankPosition);

            int rows = stmt.executeUpdate();

            if (rows > 0) {
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        int resultId = rs.getInt(1);
                        System.out.println("✅ [GameResultDAO] Saved result ID: " + resultId);
                    }
                }
                return true;
            }

        } catch (SQLException e) {
            System.err.println("❌ [GameResultDAO] Error saving result: " + e.getMessage());
        }

        return false;
    }

    /**
     * Lấy lịch sử game của user
     */
    public List<GameResult> getUserGameHistory(int userId, int limit) {
        List<GameResult> results = new ArrayList<>();

        String query = """
            SELECT * FROM game_results 
            WHERE user_id = ? 
            ORDER BY created_at DESC 
            LIMIT ?
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, userId);
            stmt.setInt(2, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    GameResult result = new GameResult();
                    result.resultId = rs.getInt("result_id");
                    result.sessionId = rs.getInt("session_id");
                    result.userId = rs.getInt("user_id");
                    result.score = rs.getInt("score");
                    result.correctAnswers = rs.getInt("correct_answers");
                    result.wrongAnswers = rs.getInt("wrong_answers");
                    result.timeTaken = rs.getInt("time_taken");
                    result.rankPosition = rs.getInt("rank_position");
                    result.createdAt = rs.getTimestamp("created_at");

                    results.add(result);
                }
            }

            System.out.println("✅ [GameResultDAO] Loaded " + results.size() + " results for user " + userId);

        } catch (SQLException e) {
            System.err.println("❌ [GameResultDAO] Error getting history: " + e.getMessage());
        }

        return results;
    }

    /**
     * Lấy thống kê của user
     */
    public GameStats getUserStats(int userId) {
        String query = """
            SELECT 
                COUNT(*) as total_games,
                SUM(CASE WHEN rank_position = 1 THEN 1 ELSE 0 END) as wins,
                SUM(CASE WHEN rank_position > 1 THEN 1 ELSE 0 END) as losses,
                AVG(rank_position) as avg_rank,
                SUM(score) as total_score,
                MAX(score) as best_score,
                SUM(correct_answers) as total_correct,
                SUM(wrong_answers) as total_wrong
            FROM game_results 
            WHERE user_id = ?
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, userId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    GameStats stats = new GameStats();
                    stats.totalGames = rs.getInt("total_games");
                    stats.wins = rs.getInt("wins");
                    stats.losses = rs.getInt("losses");
                    stats.avgRank = rs.getDouble("avg_rank");
                    stats.totalScore = rs.getInt("total_score");
                    stats.bestScore = rs.getInt("best_score");
                    stats.totalCorrect = rs.getInt("total_correct");
                    stats.totalWrong = rs.getInt("total_wrong");
                    stats.winRate = stats.totalGames > 0 ?
                            (double)stats.wins / stats.totalGames * 100 : 0;

                    System.out.println("✅ [GameResultDAO] Loaded stats for user " + userId);
                    return stats;
                }
            }

        } catch (SQLException e) {
            System.err.println("❌ [GameResultDAO] Error getting stats: " + e.getMessage());
        }

        return new GameStats();
    }

    /**
     * Lấy top players theo điểm
     */
    public List<TopPlayer> getTopPlayers(int limit) {
        List<TopPlayer> topPlayers = new ArrayList<>();

        String query = """
            SELECT 
                u.user_id,
                u.username,
                u.full_name,
                u.avatar_url,
                SUM(gr.score) as total_score,
                COUNT(*) as games_played,
                SUM(CASE WHEN gr.rank_position = 1 THEN 1 ELSE 0 END) as wins
            FROM game_results gr
            JOIN users u ON gr.user_id = u.user_id
            GROUP BY u.user_id
            ORDER BY total_score DESC
            LIMIT ?
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    TopPlayer player = new TopPlayer();
                    player.userId = rs.getInt("user_id");
                    player.username = rs.getString("username");
                    player.fullName = rs.getString("full_name");
                    player.avatarUrl = rs.getString("avatar_url");
                    player.totalScore = rs.getInt("total_score");
                    player.gamesPlayed = rs.getInt("games_played");
                    player.wins = rs.getInt("wins");

                    topPlayers.add(player);
                }
            }

        } catch (SQLException e) {
            System.err.println("❌ [GameResultDAO] Error getting top players: " + e.getMessage());
        }

        return topPlayers;
    }

    // ==================== INNER CLASSES ====================

    /**
     * Game result model - khớp với schema
     */
    public static class GameResult {
        public int resultId;
        public int sessionId;
        public int userId;
        public int score;
        public int correctAnswers;
        public int wrongAnswers;
        public int timeTaken;
        public int rankPosition;
        public Timestamp createdAt;
    }

    /**
     * Game statistics
     */
    public static class GameStats {
        public int totalGames = 0;
        public int wins = 0;
        public int losses = 0;
        public double avgRank = 0;
        public int totalScore = 0;
        public int bestScore = 0;
        public int totalCorrect = 0;
        public int totalWrong = 0;
        public double winRate = 0;
    }

    /**
     * Top player model
     */
    public static class TopPlayer {
        public int userId;
        public String username;
        public String fullName;
        public String avatarUrl;
        public int totalScore;
        public int gamesPlayed;
        public int wins;
    }
}