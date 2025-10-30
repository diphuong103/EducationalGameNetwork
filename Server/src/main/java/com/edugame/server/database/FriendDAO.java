package com.edugame.server.database;

import com.edugame.server.model.Friend;
import com.edugame.server.model.User;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class FriendDAO {
    private Connection connection;

    public FriendDAO() {
        this.connection = DatabaseConnection.getConnection();
    }

    /**
     * Tìm kiếm người dùng theo tên (fullName hoặc username)
     */
    public List<User> searchUsers(String searchQuery, int currentUserId, int limit) {
        List<User> users = new ArrayList<>();

        String sql = "SELECT user_id, username, full_name, email, age, avatar_url, " +
                "total_score, math_score, english_score, literature_score, " +
                "total_games, wins, is_online " +
                "FROM users " +
                "WHERE user_id != ? AND " +
                "(LOWER(full_name) LIKE LOWER(?) OR LOWER(username) LIKE LOWER(?)) " +
                "ORDER BY total_score DESC " +
                "LIMIT ?";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, currentUserId);
            stmt.setString(2, "%" + searchQuery + "%");
            stmt.setString(3, "%" + searchQuery + "%");
            stmt.setInt(4, limit);

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                User user = new User();
                user.setUserId(rs.getInt("user_id"));
                user.setUsername(rs.getString("username"));
                user.setFullName(rs.getString("full_name"));
                user.setEmail(rs.getString("email"));
                user.setAge(rs.getInt("age"));
                user.setAvatarUrl(rs.getString("avatar_url"));
                user.setTotalScore(rs.getInt("total_score"));
                user.setMathScore(rs.getInt("math_score"));
                user.setEnglishScore(rs.getInt("english_score"));
                user.setLiteratureScore(rs.getInt("literature_score"));
                user.setTotalGames(rs.getInt("total_games"));
                user.setWins(rs.getInt("wins"));
                user.setOnline(rs.getBoolean("is_online"));

                users.add(user);
            }

            System.out.println("✅ Found " + users.size() + " users matching: " + searchQuery);

        } catch (SQLException e) {
            System.err.println("❌ Error searching users: " + e.getMessage());
            e.printStackTrace();
        }

        return users;
    }

    /**
     * Gửi lời mời kết bạn
     */
    public boolean sendFriendRequest(int userId, int friendId) {
        // Kiểm tra xem đã có quan hệ nào chưa
        if (hasPendingRequest(userId, friendId)) {
            System.err.println("⚠️ Already has pending request between " + userId + " and " + friendId);
            return false;
        }

        if (isFriend(userId, friendId)) {
            System.err.println("⚠️ Already friends: " + userId + " and " + friendId);
            return false;
        }

        String sql = "INSERT INTO friends (user_id, friend_id, status, created_at) VALUES (?, ?, 'pending', NOW())";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setInt(2, friendId);

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                System.out.println("✅ Friend request sent from " + userId + " to " + friendId);
                return true;
            }
        } catch (SQLException e) {
            System.err.println("❌ Error sending friend request: " + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Chấp nhận lời mời kết bạn
     */
    public boolean acceptFriendRequest(int userId, int friendId) {
        // userId: người nhận lời mời (người chấp nhận)
        // friendId: người gửi lời mời

        String sql = "UPDATE friends SET status = 'accepted', updated_at = NOW() " +
                "WHERE user_id = ? AND friend_id = ? AND status = 'pending'";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, friendId); // người gửi lời mời
            stmt.setInt(2, userId);   // người nhận (hiện tại)

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                System.out.println("✅ Friend request accepted: " + friendId + " -> " + userId);
                return true;
            } else {
                System.err.println("⚠️ No pending request found from " + friendId + " to " + userId);
            }
        } catch (SQLException e) {
            System.err.println("❌ Error accepting friend request: " + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Từ chối lời mời kết bạn
     */
    public boolean rejectFriendRequest(int userId, int friendId) {
        String sql = "UPDATE friends SET status = 'rejected', updated_at = NOW() " +
                "WHERE user_id = ? AND friend_id = ? AND status = 'pending'";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, friendId);
            stmt.setInt(2, userId);

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                System.out.println("✅ Friend request rejected: " + friendId + " -> " + userId);
                return true;
            }
        } catch (SQLException e) {
            System.err.println("❌ Error rejecting friend request: " + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Xóa bạn bè
     */
    public boolean removeFriend(int userId, int friendId) {
        String sql = "DELETE FROM friends WHERE " +
                "((user_id = ? AND friend_id = ?) OR (user_id = ? AND friend_id = ?)) " +
                "AND status = 'accepted'";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setInt(2, friendId);
            stmt.setInt(3, friendId);
            stmt.setInt(4, userId);

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                System.out.println("✅ Friendship removed between " + userId + " and " + friendId);
                return true;
            }
        } catch (SQLException e) {
            System.err.println("❌ Error removing friend: " + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Lấy danh sách bạn bè
     */
    public List<Friend> getFriendsList(int userId) {
        List<Friend> friends = new ArrayList<>();

        String sql = "SELECT f.friendship_id, f.user_id, f.friend_id, f.status, f.created_at, " +
                "u.username, u.full_name, u.avatar_url, u.total_score, u.is_online " +
                "FROM friends f " +
                "JOIN users u ON (u.user_id = CASE WHEN f.user_id = ? THEN f.friend_id ELSE f.user_id END) " +
                "WHERE (f.user_id = ? OR f.friend_id = ?) AND f.status = 'accepted' " +
                "ORDER BY u.is_online DESC, u.full_name ASC";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setInt(2, userId);
            stmt.setInt(3, userId);

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                Friend friend = new Friend();
                friend.setFriendshipId(rs.getInt("friendship_id"));
                friend.setUserId(rs.getInt("user_id"));
                friend.setFriendId(rs.getInt("friend_id"));
                friend.setStatus(rs.getString("status"));
                friend.setCreatedAt(rs.getTimestamp("created_at"));
                friend.setUsername(rs.getString("username"));
                friend.setFullName(rs.getString("full_name"));
                friend.setAvatarUrl(rs.getString("avatar_url"));
                friend.setTotalScore(rs.getInt("total_score"));
                friend.setOnline(rs.getBoolean("is_online"));

                friends.add(friend);
            }

            System.out.println("✅ Loaded " + friends.size() + " friends for user " + userId);

        } catch (SQLException e) {
            System.err.println("❌ Error loading friends list: " + e.getMessage());
            e.printStackTrace();
        }

        return friends;
    }

    /**
     * Lấy danh sách lời mời kết bạn đang chờ (người khác gửi cho mình)
     */
    public List<Friend> getPendingRequests(int userId) {
        List<Friend> requests = new ArrayList<>();

        String sql = "SELECT f.friendship_id, f.user_id, f.friend_id, f.status, f.created_at, " +
                "u.username, u.full_name, u.avatar_url, u.total_score, u.is_online " +
                "FROM friends f " +
                "JOIN users u ON u.user_id = f.user_id " +
                "WHERE f.friend_id = ? AND f.status = 'pending' " +
                "ORDER BY f.created_at DESC";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, userId);

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                Friend friend = new Friend();
                friend.setFriendshipId(rs.getInt("friendship_id"));
                friend.setUserId(rs.getInt("user_id"));
                friend.setFriendId(rs.getInt("friend_id"));
                friend.setStatus(rs.getString("status"));
                friend.setCreatedAt(rs.getTimestamp("created_at"));
                friend.setUsername(rs.getString("username"));
                friend.setFullName(rs.getString("full_name"));
                friend.setAvatarUrl(rs.getString("avatar_url"));
                friend.setTotalScore(rs.getInt("total_score"));
                friend.setOnline(rs.getBoolean("is_online"));

                requests.add(friend);
            }

            System.out.println("✅ Found " + requests.size() + " pending requests for user " + userId);

        } catch (SQLException e) {
            System.err.println("❌ Error loading pending requests: " + e.getMessage());
            e.printStackTrace();
        }

        return requests;
    }

    /**
     * Kiểm tra xem hai người đã là bạn bè chưa
     */
    public boolean isFriend(int userId, int friendId) {
        String sql = "SELECT COUNT(*) FROM friends " +
                "WHERE ((user_id = ? AND friend_id = ?) OR (user_id = ? AND friend_id = ?)) " +
                "AND status = 'accepted'";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setInt(2, friendId);
            stmt.setInt(3, friendId);
            stmt.setInt(4, userId);

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            System.err.println("❌ Error checking friendship: " + e.getMessage());
        }

        return false;
    }

    /**
     * Kiểm tra xem đã có lời mời kết bạn đang chờ chưa
     */
    public boolean hasPendingRequest(int userId, int friendId) {
        String sql = "SELECT COUNT(*) FROM friends " +
                "WHERE ((user_id = ? AND friend_id = ?) OR (user_id = ? AND friend_id = ?)) " +
                "AND status = 'pending'";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setInt(2, friendId);
            stmt.setInt(3, friendId);
            stmt.setInt(4, userId);

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            System.err.println("❌ Error checking pending request: " + e.getMessage());
        }

        return false;
    }

    /**
     * Lấy trạng thái mối quan hệ giữa hai người
     * Trả về: "none", "friend", "pending_sent", "pending_received"
     */
    public String getFriendshipStatus(int userId, int friendId) {
        // Kiểm tra xem đã là bạn chưa
        if (isFriend(userId, friendId)) {
            return "friend";
        }

        // Kiểm tra lời mời đã gửi
        String sql = "SELECT user_id, friend_id FROM friends " +
                "WHERE ((user_id = ? AND friend_id = ?) OR (user_id = ? AND friend_id = ?)) " +
                "AND status = 'pending'";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setInt(2, friendId);
            stmt.setInt(3, friendId);
            stmt.setInt(4, userId);

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                int requesterId = rs.getInt("user_id");
                if (requesterId == userId) {
                    return "pending_sent"; // Bạn đã gửi lời mời
                } else {
                    return "pending_received"; // Bạn nhận được lời mời
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ Error checking friendship status: " + e.getMessage());
        }

        return "none";
    }

    /**
     * Đếm số bạn bè
     */
    public int countFriends(int userId) {
        String sql = "SELECT COUNT(*) FROM friends " +
                "WHERE (user_id = ? OR friend_id = ?) AND status = 'accepted'";

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, userId);
            stmt.setInt(2, userId);

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("❌ Error counting friends: " + e.getMessage());
        }

        return 0;
    }
}