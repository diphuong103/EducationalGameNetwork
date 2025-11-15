package com.edugame.server.database;

import java.sql.*;
import java.util.List;

/**
 * GameResultDAO - Lưu kết quả game vào game_results table
 */
public class GameResultDAO {

    /**
     * ✅ Lưu kết quả game với session_id CHÍNH XÁC từ game_sessions
     *
     * @param sessionId - ID từ game_sessions table (auto-generated)
     * @param userId - ID người chơi
     * @param score - Điểm số
     * @param correctAnswers - Số câu đúng thực tế
     * @param wrongAnswers - Số câu sai thực tế
     * @param timeTaken - Thời gian hoàn thành (giây)
     * @param rankPosition - Thứ hạng cuối cùng
     * @return true nếu lưu thành công
     */
    public boolean saveGameResult(int sessionId, int userId, int score,
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
                        System.out.println("   Session: " + sessionId + " | User: " + userId);
                        System.out.println("   Score: " + score + " | Rank: " + rankPosition);
                        System.out.println("   Correct: " + correctAnswers + " | Wrong: " + wrongAnswers);
                        System.out.println("   Time: " + timeTaken + "s");
                    }
                }
                return true;
            }

        } catch (SQLException e) {
            System.err.println("❌ [GameResultDAO] Error saving result:");
            System.err.println("   Session: " + sessionId + " | User: " + userId);
            System.err.println("   Error: " + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Lấy lịch sử game của user
     */
    public java.util.List<GameResult> getUserGameHistory(int userId, int limit) {
        java.util.List<GameResult> results = new java.util.ArrayList<>();

        String query = """
            SELECT gr.*, gs.subject, gs.difficulty, gs.started_at
            FROM game_results gr
            JOIN game_sessions gs ON gr.session_id = gs.session_id
            WHERE gr.user_id = ? 
            ORDER BY gr.created_at DESC 
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

                    // Extra info from game_sessions
                    result.subject = rs.getString("subject");
                    result.difficulty = rs.getString("difficulty");
                    result.gameStartedAt = rs.getTimestamp("started_at");

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
                SUM(wrong_answers) as total_wrong,
                AVG(time_taken) as avg_time
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
                    stats.avgTime = rs.getInt("avg_time");
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

    public java.util.List<TopPlayer> getAllPlayers() {
        java.util.List<TopPlayer> allPlayers = new java.util.ArrayList<>();

        String query = """
        SELECT 
            u.user_id,
            u.username,
            u.full_name,
            u.total_score,
            u.total_games,
            u.wins
        FROM users u
        WHERE u.total_games > 0
        ORDER BY u.total_score DESC, u.wins DESC, u.username ASC
    """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    TopPlayer player = new TopPlayer();
                    player.userId = rs.getInt("user_id");
                    player.username = rs.getString("username");
                    player.fullName = rs.getString("full_name");
                    player.totalScore = rs.getInt("total_score");
                    player.gamesPlayed = rs.getInt("total_games");
                    player.wins = rs.getInt("wins");

                    allPlayers.add(player);
                }
            }

            System.out.println("✅ [GameResultDAO] Loaded all " + allPlayers.size() + " players");

        } catch (SQLException e) {
            System.err.println("❌ [GameResultDAO] Error getting all players: " + e.getMessage());
            e.printStackTrace();
        }

        return allPlayers;
    }

    // ==================== INNER CLASSES ====================

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

        // Extra from JOIN
        public String subject;
        public String difficulty;
        public Timestamp gameStartedAt;
    }

    public static class GameStats {
        public int totalGames = 0;
        public int wins = 0;
        public int losses = 0;
        public double avgRank = 0;
        public int totalScore = 0;
        public int bestScore = 0;
        public int totalCorrect = 0;
        public int totalWrong = 0;
        public int avgTime = 0;
        public double winRate = 0;
    }

    /**
     * Lấy top players (leaderboard)
     */
    public java.util.List<TopPlayer> getTopPlayers(int limit) {
        java.util.List<TopPlayer> topPlayers = new java.util.ArrayList<>();

        String query = """
        SELECT 
            u.user_id,
            u.username,
            u.full_name,
            u.total_score,
            u.total_games,
            u.wins
        FROM users u
        WHERE u.total_games > 0
        ORDER BY u.total_score DESC, u.wins DESC
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
                    player.totalScore = rs.getInt("total_score");
                    player.gamesPlayed = rs.getInt("total_games");
                    player.wins = rs.getInt("wins");

                    topPlayers.add(player);
                }
            }

            System.out.println("✅ [GameResultDAO] Loaded top " + topPlayers.size() + " players");

        } catch (SQLException e) {
            System.err.println("❌ [GameResultDAO] Error getting top players: " + e.getMessage());
            e.printStackTrace();
        }

        return topPlayers;
    }

    /**
     * Inner class cho Top Player data
     */
    public static class TopPlayer {
        public int userId;
        public String username;
        public String fullName;
        public int totalScore;
        public int gamesPlayed;
        public int wins;
    }

}