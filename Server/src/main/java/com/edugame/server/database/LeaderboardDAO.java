package com.edugame.server.database;

import com.edugame.server.model.User;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class LeaderboardDAO {
    private Connection connection;

    public LeaderboardDAO() {
        this.connection = DatabaseConnection.getInstance().getConnection();
    }

    /**
     * Get top N users by total score
     */
    public List<User> getTopUsers(int limit) {
        String sql = "SELECT user_id, username, full_name, avatar_url, total_score, is_online " +
                "FROM users " +
                "ORDER BY total_score DESC " +
                "LIMIT ?";

        List<User> topUsers = new ArrayList<>();

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
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
            e.printStackTrace();
        }

        return topUsers;
    }

    /**
     * Get user rank by userId
     */
    public int getUserRank(int userId) {
        String sql = "SELECT COUNT(*) + 1 as rank " +
                "FROM users " +
                "WHERE total_score > (SELECT total_score FROM users WHERE user_id = ?)";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
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
            case "math":
                scoreColumn = "math_score";
                break;
            case "english":
                scoreColumn = "english_score";
                break;
            case "science":
                scoreColumn = "science_score";
                break;
            default:
                scoreColumn = "total_score";
        }

        String sql = "SELECT user_id, username, full_name, avatar_url, " + scoreColumn + " as score, is_online " +
                "FROM users " +
                "ORDER BY " + scoreColumn + " DESC " +
                "LIMIT ?";

        List<User> leaderboard = new ArrayList<>();

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, limit);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                User user = new User();
                user.setUserId(rs.getInt("user_id"));
                user.setUsername(rs.getString("username"));
                user.setFullName(rs.getString("full_name"));
                user.setAvatarUrl(rs.getString("avatar_url"));
                user.setTotalScore(rs.getInt("score"));
                user.setOnline(rs.getBoolean("is_online"));

                leaderboard.add(user);
            }

        } catch (SQLException e) {
            System.err.println("✗ Error getting leaderboard: " + e.getMessage());
        }

        return leaderboard;
    }
}