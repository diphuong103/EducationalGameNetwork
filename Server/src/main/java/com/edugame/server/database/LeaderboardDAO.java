package com.edugame.server.database;

import com.edugame.server.model.User;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class LeaderboardDAO {

    private Connection getConnection() throws SQLException {
        // Always return a fresh, guaranteed-alive connection
        return DatabaseConnection.getInstance().getConnection();
    }

    /**
     * Get top N users by total score
     */
    public List<User> getTopUsers(int limit) {
        String sql =
                "SELECT user_id, username, full_name, avatar_url, total_score, is_online " +
                        "FROM users " +
                        "ORDER BY total_score DESC " +
                        "LIMIT ?";

        List<User> topUsers = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, limit);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                User user = new User();
                user.setUserId(rs.getInt("user_id"));
                user.setUsername(rs.getString("username"));
                user.setFullName(rs.getString("full_name"));
                user.setAvatarUrl(rs.getString("avatar_url"));
                user.setTotalScore(rs.getInt("total_score"));
                user.setOnline(rs.getBoolean("is_online"));

                topUsers.add(user);
            }

            System.out.println("✓ Loaded top " + topUsers.size() + " users");

        } catch (SQLException e) {
            System.err.println("✗ Error getting top users: " + e.getMessage());
        }

        return topUsers;
    }


    /**
     * Get user rank by userId
     */
    public int getUserRank(int userId) {
        String sql =
                "SELECT COUNT(*) + 1 AS rank " +
                        "FROM users " +
                        "WHERE total_score > (SELECT total_score FROM users WHERE user_id = ?)";

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, userId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getInt("rank");
            }

        } catch (SQLException e) {
            System.err.println("✗ Error getting user rank: " + e.getMessage());
        }

        return -1;
    }


    /**
     * Get leaderboard by subject
     */
    public List<User> getLeaderboardBySubject(String subject, int limit) {
        String scoreColumn;

        switch (subject.toLowerCase()) {
            case "math":       scoreColumn = "math_score"; break;
            case "english":    scoreColumn = "english_score"; break;
            case "literature": scoreColumn = "literature_score"; break;
            default:           scoreColumn = "total_score"; break;
        }

        String sql =
                "SELECT user_id, username, full_name, avatar_url, " +
                        scoreColumn + " AS score, is_online " +
                        "FROM users " +
                        "ORDER BY " + scoreColumn + " DESC " +
                        "LIMIT ?";

        List<User> leaderboard = new ArrayList<>();

        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, limit);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {

                User user = new User();
                user.setUserId(rs.getInt("user_id"));
                user.setUsername(rs.getString("username"));
                user.setFullName(rs.getString("full_name"));
                user.setAvatarUrl(rs.getString("avatar_url"));
                user.setOnline(rs.getBoolean("is_online"));

                int score = rs.getInt("score");

                switch (subject.toLowerCase()) {
                    case "math":       user.setMathScore(score); break;
                    case "english":    user.setEnglishScore(score); break;
                    case "literature": user.setLiteratureScore(score); break;
                    default:           user.setTotalScore(score); break;
                }

                leaderboard.add(user);
            }

        } catch (SQLException e) {
            System.err.println("✗ Error getting leaderboard by subject: " + e.getMessage());
        }

        return leaderboard;
    }
}
