package com.edugame.server.database;

import com.edugame.server.model.User;

import java.sql.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

public class UserDAO {
    private Connection connection;

    public UserDAO() throws SQLException {
        this.connection = DatabaseConnection.getInstance().getConnection();
    }

    /**
     * Register new user
     */
    public boolean registerUser(String username, String password, String email,
                                String fullName, String age, String avatarUrl) {
        String sql = "INSERT INTO users (username, password, email, full_name, age, avatar_url) VALUES (?, ?, ?, ?, ?, ?)";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            // Hash password
            String hashedPassword = hashPassword(password);

            pstmt.setString(1, username);
            pstmt.setString(2, hashedPassword);
            pstmt.setString(3, email);
            pstmt.setString(4, fullName);
            pstmt.setString(5, age);
            pstmt.setString(6, avatarUrl);

            int rowsAffected = pstmt.executeUpdate();

            if (rowsAffected > 0) {
                System.out.println("✓ User registered: " + username);
                return true;
            }

        } catch (SQLException e) {
            System.err.println("✗ Registration failed: " + e.getMessage());

            // Check for duplicate username or email
            if (e.getErrorCode() == 1062) { // MySQL duplicate entry error code
                System.err.println("✗ Username or email already exists!");
            }
        }

        return false;
    }


    /**
     * Login user - validate credentials
     */
    public User loginUser(String username, String password) {
        String sql = "SELECT * FROM users WHERE username = ? AND password = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            String hashedPassword = hashPassword(password);

            pstmt.setString(1, username);
            pstmt.setString(2, hashedPassword);

            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                // Update last login and online status
                updateLastLogin(rs.getInt("user_id"));

                // Create User object
                User user = new User();
                user.setUserId(rs.getInt("user_id"));
                user.setUsername(rs.getString("username"));
                user.setEmail(rs.getString("email"));
                user.setFullName(rs.getString("full_name"));
                user.setAge(rs.getInt("age"));
                user.setAvatarUrl(rs.getString("avatar_url"));
                user.setTotalScore(rs.getInt("total_score"));
                user.setMathScore(rs.getInt("math_score"));
                user.setEnglishScore(rs.getInt("english_score"));
                user.setLiteratureScore(rs.getInt("literature_score"));
                user.setTotalGames(rs.getInt("total_games"));
                user.setWins(rs.getInt("wins"));
                user.setOnline(true);

                System.out.println("✓ User logged in: " + username);
                return user;
            } else {
                System.err.println("✗ Invalid credentials for: " + username);
            }

        } catch (SQLException e) {
            System.err.println("✗ Login error: " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Check if username exists
     */
    public boolean usernameExists(String username) {
        String sql = "SELECT COUNT(*) FROM users WHERE username = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1) > 0;
            }

        } catch (SQLException e) {
            System.err.println("✗ Error checking username: " + e.getMessage());
        }

        return false;
    }

    /**
     * Check if email exists
     */
    public boolean emailExists(String email) {
        String sql = "SELECT COUNT(*) FROM users WHERE email = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, email);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1) > 0;
            }

        } catch (SQLException e) {
            System.err.println("✗ Error checking email: " + e.getMessage());
        }

        return false;
    }

    /**
     * Update user's last login time
     */
    private void updateLastLogin(int userId) {
        String sql = "UPDATE users SET last_login = NOW(), is_online = 1, status = 'online' WHERE user_id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("✗ Error updating last login: " + e.getMessage());
        }
    }


    /**
     * Update user online status
     */
//    public void updateOnlineStatus(int userId, boolean isOnline) {
//        String sql = "UPDATE users SET is_online = ?, status = ? WHERE user_id = ?";
//
//        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
//            pstmt.setBoolean(1, isOnline);
//            pstmt.setString(2, isOnline ? "online" : "offline");
//            pstmt.setInt(3, userId);
//            pstmt.executeUpdate();
//
//            System.out.println("✓ User " + userId + " status: " + (isOnline ? "online" : "offline"));
//
//        } catch (SQLException e) {
//            System.err.println("✗ Error updating online status: " + e.getMessage());
//        }
//    }

    public void updateOnlineStatus(int userId, boolean isOnline) {
        String sql = "UPDATE users SET is_online = ?, status = ? WHERE user_id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            // Ép kiểu rõ ràng để tránh MySQL hiểu sai
            pstmt.setInt(1, isOnline ? 1 : 0);
            pstmt.setString(2, isOnline ? "online" : "offline");
            pstmt.setInt(3, userId);

            int rows = pstmt.executeUpdate();

            if (!connection.getAutoCommit()) {
                connection.commit();
            }

            System.out.println("✅ Updated user_id=" + userId +
                    " → " + (isOnline ? "online" : "offline") +
                    " (" + rows + " rows)");

        } catch (SQLException e) {
            System.err.println("❌ Error updating online status: " + e.getMessage());
        }
    }


    /**
     * Get user by ID
     */
    public User getUserById(int userId) {
        String query = "SELECT * FROM users WHERE user_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, userId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    User user = new User();
                    user.setUserId(rs.getInt("user_id"));
                    user.setUsername(rs.getString("username"));
                    user.setFullName(rs.getString("full_name"));
                    user.setEmail(rs.getString("email"));
                    user.setAvatarUrl(rs.getString("avatar_url"));
                    user.setTotalScore(rs.getInt("total_score"));
                    user.setMathScore(rs.getInt("math_score"));
                    user.setEnglishScore(rs.getInt("english_score"));
                    user.setLiteratureScore(rs.getInt("literature_score"));
                    user.setTotalGames(rs.getInt("total_games"));
                    user.setWins(rs.getInt("wins"));
                    user.setCreatedAt(rs.getTimestamp("created_at"));
                    return user;
                }
            }

        } catch (SQLException e) {
            System.err.println("❌ [UserDAO] Error getting user: " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }


    public boolean updateUserProfile(int userId, String newName, String newAvatar) {
        String sql = "UPDATE users SET " +
                "full_name = COALESCE(?, full_name), " +
                "avatar_url = COALESCE(?, avatar_url) " +
                "WHERE user_id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, newName);
            pstmt.setString(2, newAvatar);
            pstmt.setInt(3, userId);

            int rows = pstmt.executeUpdate();
            System.out.println("✅ Updated profile for user_id=" + userId + " (" + rows + " rows)");
            return rows > 0;
        } catch (SQLException e) {
            System.err.println("❌ Error updating profile: " + e.getMessage());
            return false;
        }
    }

    /**
     * Hash password using SHA-256
     */
    private String hashPassword(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(password.getBytes());

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            return hexString.toString();

        } catch (NoSuchAlgorithmException e) {
            System.err.println("✗ Error hashing password: " + e.getMessage());
            return password; // Fallback (not recommended for production)
        }
    }

    /**
     * Lấy thông tin người dùng theo username
     */
    public User getUserByUsername(String username) {
        String sql = "SELECT * FROM users WHERE username = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                User user = new User();
                user.setUserId(rs.getInt("user_id"));
                user.setUsername(rs.getString("username"));
                user.setEmail(rs.getString("email"));
                user.setFullName(rs.getString("full_name"));
                user.setAge(rs.getInt("age"));
                user.setAvatarUrl(rs.getString("avatar_url"));
                user.setTotalScore(rs.getInt("total_score"));
                user.setMathScore(rs.getInt("math_score"));
                user.setEnglishScore(rs.getInt("english_score"));
                user.setLiteratureScore(rs.getInt("literature_score"));
                user.setTotalGames(rs.getInt("total_games"));
                user.setWins(rs.getInt("wins"));
                user.setOnline(rs.getBoolean("is_online"));

                return user;
            }

        } catch (SQLException e) {
            System.err.println("❌ Error getting user by username: " + e.getMessage());
        }

        return null;
    }

    /**
     * Lấy mật khẩu đã mã hóa của người dùng (dùng cho xác thực, đổi mật khẩu)
     */
    public String getPassword(String username) {
        String sql = "SELECT password FROM users WHERE username = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getString("password");
            }

        } catch (SQLException e) {
            System.err.println("❌ Error getting password: " + e.getMessage());
        }

        return null;
    }

    /**
     * Cập nhật mật khẩu mới cho người dùng
     */
    public boolean updatePassword(int userId, String newPassword) {
        String sql = "UPDATE users SET password = ? WHERE user_id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            String hashedPassword = hashPassword(newPassword);
            pstmt.setString(1, hashedPassword);
            pstmt.setInt(2, userId);

            int rows = pstmt.executeUpdate();

            if (rows > 0) {
                System.out.println("✅ Password updated for user_id=" + userId);
                return true;
            } else {
                System.out.println("⚠️ No user found with id=" + userId);
            }

        } catch (SQLException e) {
            System.err.println("❌ Error updating password: " + e.getMessage());
        }

        return false;
    }

    public boolean updateUserStats(User user) {
        String sql = """
        UPDATE users 
        SET total_games = ?, 
            wins = ?, 
            total_score = ?, 
            math_score = ?, 
            english_score = ?, 
            literature_score = ?, 
            last_login = NOW()
        WHERE user_id = ?
    """;

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, user.getTotalGames());
            stmt.setInt(2, user.getWins());
            stmt.setInt(3, user.getTotalScore());
            stmt.setInt(4, user.getMathScore());
            stmt.setInt(5, user.getEnglishScore());
            stmt.setInt(6, user.getLiteratureScore());
            stmt.setInt(7, user.getUserId());

            int rows = stmt.executeUpdate();
            return rows > 0;

        } catch (Exception e) {
            System.err.println("❌ Lỗi khi cập nhật thống kê người chơi: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    /**
     * Cập nhật tổng điểm của user (cộng dồn)
     */
    public boolean updateTotalScore(int userId, int scoreToAdd) {
        String query = """
            UPDATE users 
            SET total_score = total_score + ?
            WHERE user_id = ?
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, scoreToAdd);
            stmt.setInt(2, userId);

            int rows = stmt.executeUpdate();

            if (rows > 0) {
                System.out.println("✅ [UserDAO] Updated total score for user " + userId + " (+" + scoreToAdd + ")");
                return true;
            }

        } catch (SQLException e) {
            System.err.println("❌ [UserDAO] Error updating total score: " + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }


    /**
     * Cập nhật thống kê win/loss
     */
    public boolean updateGameStats(int userId, boolean isWinner) {
        String query = """
            UPDATE users 
            SET total_games = total_games + 1,
                wins = wins + ?
            WHERE user_id = ?
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, isWinner ? 1 : 0);
            stmt.setInt(2, userId);

            int rows = stmt.executeUpdate();

            if (rows > 0) {
                System.out.println("✅ [UserDAO] Updated game stats for user " + userId +
                        " (" + (isWinner ? "WIN" : "LOSS") + ")");
                return true;
            }

        } catch (SQLException e) {
            System.err.println("❌ [UserDAO] Error updating game stats: " + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }


    /**
     * Cập nhật điểm theo môn học
     */
    public boolean updateSubjectScore(int userId, String subject, int scoreToAdd) {
        String columnName;
        switch (subject.toLowerCase()) {
            case "math":
            case "toán":
                columnName = "math_score";
                break;
            case "english":
            case "tiếng anh":
                columnName = "english_score";
                break;
            case "literature":
            case "văn":
                columnName = "literature_score";
                break;
            default:
                System.out.println("⚠️ [UserDAO] Unknown subject: " + subject);
                return false;
        }

        String query = "UPDATE users SET " + columnName + " = " + columnName + " + ? WHERE user_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, scoreToAdd);
            stmt.setInt(2, userId);

            int rows = stmt.executeUpdate();

            if (rows > 0) {
                System.out.println("✅ [UserDAO] Updated " + subject + " score for user " + userId + " (+" + scoreToAdd + ")");
                return true;
            }

        } catch (SQLException e) {
            System.err.println("❌ [UserDAO] Error updating subject score: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }


    /**
     * Get total number of registered users
     */
    public int getTotalUserCount() {
        String query = "SELECT COUNT(*) as total FROM users";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                return rs.getInt("total");
            }

        } catch (SQLException e) {
            System.err.println("❌ [UserDAO] Error getting user count: " + e.getMessage());
        }

        return 0;
    }

    /**
     * Get total number of games played (from users table)
     */
    public int getTotalGamesPlayed() {
        String query = "SELECT SUM(total_games) as total FROM users";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                return rs.getInt("total");
            }

        } catch (SQLException e) {
            System.err.println("❌ [UserDAO] Error getting games count: " + e.getMessage());
        }

        return 0;
    }
    /**
     * Lấy TẤT CẢ người chơi cho leaderboard
     * Sắp xếp theo điểm cao nhất
     * Bao gồm cả người chưa chơi game nào (total_games = 0)
     */
    public List<PlayerInfo> getAllPlayersForLeaderboard() {
        System.out.println("🔍 [DEBUG UserDAO] Starting getAllPlayersForLeaderboard()...");

        List<PlayerInfo> players = new ArrayList<>();

        String query = """
        SELECT user_id, username, full_name, email, age, avatar_url,
               total_score, math_score, english_score, literature_score,
               total_games, wins, created_at
        FROM users 
        ORDER BY total_score DESC, wins DESC, username ASC
    """;

        try {
            System.out.println("🔍 [DEBUG UserDAO] Connection status: " +
                    (connection != null && !connection.isClosed() ? "OK" : "CLOSED"));

            if (connection == null || connection.isClosed()) {
                System.err.println("❌ [DEBUG UserDAO] Connection is NULL or closed!");
                System.err.println("   Attempting to reconnect...");
                connection = DatabaseConnection.getInstance().getConnection();
            }

            try (PreparedStatement stmt = connection.prepareStatement(query);
                 ResultSet rs = stmt.executeQuery()) {

                System.out.println("✅ [DEBUG UserDAO] Query executed successfully");

                int count = 0;
                while (rs.next()) {
                    PlayerInfo player = new PlayerInfo();
                    player.userId = rs.getInt("user_id");
                    player.username = rs.getString("username");
                    player.fullName = rs.getString("full_name");
                    player.email = rs.getString("email");
                    player.age = rs.getInt("age");
                    player.avatarUrl = rs.getString("avatar_url");
                    player.totalScore = rs.getInt("total_score");
                    player.mathScore = rs.getInt("math_score");
                    player.englishScore = rs.getInt("english_score");
                    player.literatureScore = rs.getInt("literature_score");
                    player.totalGames = rs.getInt("total_games");
                    player.wins = rs.getInt("wins");
                    player.createdAt = rs.getTimestamp("created_at");

                    players.add(player);
                    count++;

                    if (count <= 3) {
                        System.out.println("   [" + count + "] " + player.username +
                                " - Score: " + player.totalScore);
                    }
                }

                System.out.println("✅ [DEBUG UserDAO] Loaded " + players.size() + " players from database");

            }

        } catch (SQLException e) {
            System.err.println("❌ [DEBUG UserDAO] SQL Error: " + e.getMessage());
            System.err.println("   Error Code: " + e.getErrorCode());
            System.err.println("   SQL State: " + e.getSQLState());
            e.printStackTrace();
        }

        return players;
    }

    /**
     * Lấy top N người chơi
     */
    public java.util.List<PlayerInfo> getTopPlayers(int limit) {
        java.util.List<PlayerInfo> topPlayers = new java.util.ArrayList<>();

        String query = "SELECT " +
                "user_id, username, full_name, email, age, avatar_url, " +
                "total_score, math_score, english_score, literature_score, " +
                "total_games, wins, created_at " +
                "FROM users " +
                "ORDER BY total_score DESC, wins DESC, total_games DESC " +
                "LIMIT ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    PlayerInfo player = new PlayerInfo();
                    player.userId = rs.getInt("user_id");
                    player.username = rs.getString("username");
                    player.fullName = rs.getString("full_name");
                    player.email = rs.getString("email");
                    player.age = rs.getInt("age");
                    player.avatarUrl = rs.getString("avatar_url");
                    player.totalScore = rs.getInt("total_score");
                    player.mathScore = rs.getInt("math_score");
                    player.englishScore = rs.getInt("english_score");
                    player.literatureScore = rs.getInt("literature_score");
                    player.totalGames = rs.getInt("total_games");
                    player.wins = rs.getInt("wins");
                    player.createdAt = rs.getTimestamp("created_at");

                    topPlayers.add(player);
                }
            }

            System.out.println("✅ [UserDAO] Loaded top " + topPlayers.size() + " players");

        } catch (SQLException e) {
            System.err.println("❌ [UserDAO] Error getting top players: " + e.getMessage());
            e.printStackTrace();
        }

        return topPlayers;
    }

    /**
     * Inner class cho Player Info
     */
    public static class PlayerInfo {
        public int userId;
        public String username;
        public String fullName;
        public String email;
        public int age;
        public String avatarUrl;
        public int totalScore;
        public int mathScore;
        public int englishScore;
        public int literatureScore;
        public int totalGames;
        public int wins;
        public java.sql.Timestamp createdAt;

        // Helper method
        public double getWinRate() {
            if (totalGames == 0) return 0;
            return (wins * 100.0) / totalGames;
        }
    }
    /**
     * Lấy thông tin chi tiết của một player theo ID
     * Trả về PlayerInfo object với đầy đủ thông tin
     */
    public PlayerInfo getPlayerInfoById(int userId) {
        String query = """
        SELECT user_id, username, full_name, email, age, avatar_url,
               total_score, math_score, english_score, literature_score,
               total_games, wins, created_at
        FROM users 
        WHERE user_id = ?
    """;
        //                                               ^^^^^^^^^^^ THÊM AVATAR_URL VÀO ĐÂY

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, userId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    PlayerInfo player = new PlayerInfo();
                    player.userId = rs.getInt("user_id");
                    player.username = rs.getString("username");
                    player.fullName = rs.getString("full_name");
                    player.email = rs.getString("email");
                    player.age = rs.getInt("age");
                    player.avatarUrl = rs.getString("avatar_url");
                    player.totalScore = rs.getInt("total_score");
                    player.mathScore = rs.getInt("math_score");
                    player.englishScore = rs.getInt("english_score");
                    player.literatureScore = rs.getInt("literature_score");
                    player.totalGames = rs.getInt("total_games");
                    player.wins = rs.getInt("wins");
                    player.createdAt = rs.getTimestamp("created_at");

                    System.out.println("✅ [UserDAO] Loaded player: " + player.username);
                    System.out.println("   Avatar URL: " + player.avatarUrl);

                    return player;
                }
            }

        } catch (SQLException e) {
            System.err.println("❌ [UserDAO] Error getting player info: " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    public boolean updateUser(int userId, String username, String fullName, String email) {
        String sql = "UPDATE users SET username = ?, full_name = ?, email = ? WHERE user_id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, fullName);
            pstmt.setString(3, email);
            pstmt.setInt(4, userId);

            int rows = pstmt.executeUpdate();

            if (rows > 0) {
                System.out.println("✅ User updated successfully: " + username);
                return true;
            }

        } catch (SQLException e) {
            System.err.println("❌ Error updating user: " + e.getMessage());

            // Check for duplicate username or email
            if (e.getErrorCode() == 1062) {
                System.err.println("❌ Username or email already exists!");
            }
        }

        return false;
    }

    /**
     * Xóa user theo ID
     */
    public boolean deleteUser(int userId) {
        // Kiểm tra xem user có đang online không
        String checkSql = "SELECT is_online FROM users WHERE user_id = ?";

        try (PreparedStatement checkStmt = connection.prepareStatement(checkSql)) {
            checkStmt.setInt(1, userId);
            ResultSet rs = checkStmt.executeQuery();

            if (rs.next() && rs.getBoolean("is_online")) {
                System.err.println("⚠️ Cannot delete online user!");
                return false;
            }

        } catch (SQLException e) {
            System.err.println("❌ Error checking user status: " + e.getMessage());
            return false;
        }

        // Xóa user
        String deleteSql = "DELETE FROM users WHERE user_id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(deleteSql)) {
            pstmt.setInt(1, userId);

            int rows = pstmt.executeUpdate();

            if (rows > 0) {
                System.out.println("✅ User deleted successfully (ID: " + userId + ")");
                return true;
            } else {
                System.err.println("⚠️ User not found (ID: " + userId + ")");
            }

        } catch (SQLException e) {
            System.err.println("❌ Error deleting user: " + e.getMessage());

            // Check for foreign key constraint
            if (e.getErrorCode() == 1451) {
                System.err.println("❌ Cannot delete user with existing game records!");
            }
        }

        return false;
    }

    /**
     * Kiểm tra username có tồn tại không (ngoại trừ user hiện tại - dùng cho Edit)
     */
    public boolean usernameExistsExcept(String username, int exceptUserId) {
        String sql = "SELECT COUNT(*) FROM users WHERE username = ? AND user_id != ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setInt(2, exceptUserId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1) > 0;
            }

        } catch (SQLException e) {
            System.err.println("❌ Error checking username: " + e.getMessage());
        }

        return false;
    }

    /**
     * Kiểm tra email có tồn tại không (ngoại trừ user hiện tại - dùng cho Edit)
     */
    public boolean emailExistsExcept(String email, int exceptUserId) {
        String sql = "SELECT COUNT(*) FROM users WHERE email = ? AND user_id != ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, email);
            pstmt.setInt(2, exceptUserId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getInt(1) > 0;
            }

        } catch (SQLException e) {
            System.err.println("❌ Error checking email: " + e.getMessage());
        }

        return false;
    }

    /**
     * Reset password về mặc định
     */
    public boolean resetPassword(int userId, String newPassword) {
        return updatePassword(userId, newPassword);
    }

    /**
     * Đếm số user online
     */
    public int getOnlineUserCount() {
        String sql = "SELECT COUNT(*) FROM users WHERE is_online = 1";

        try (PreparedStatement pstmt = connection.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            if (rs.next()) {
                return rs.getInt(1);
            }

        } catch (SQLException e) {
            System.err.println("❌ Error counting online users: " + e.getMessage());
        }

        return 0;
    }

    /**
     * Lấy danh sách user online
     */
    public List<PlayerInfo> getOnlineUsers() {
        List<PlayerInfo> players = new ArrayList<>();

        String query = """
        SELECT user_id, username, full_name, email, age, avatar_url,
               total_score, math_score, english_score, literature_score,
               total_games, wins, created_at
        FROM users 
        WHERE is_online = 1
        ORDER BY username ASC
    """;

        try (PreparedStatement stmt = connection.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                PlayerInfo player = new PlayerInfo();
                player.userId = rs.getInt("user_id");
                player.username = rs.getString("username");
                player.fullName = rs.getString("full_name");
                player.email = rs.getString("email");
                player.age = rs.getInt("age");
                player.avatarUrl = rs.getString("avatar_url");
                player.totalScore = rs.getInt("total_score");
                player.mathScore = rs.getInt("math_score");
                player.englishScore = rs.getInt("english_score");
                player.literatureScore = rs.getInt("literature_score");
                player.totalGames = rs.getInt("total_games");
                player.wins = rs.getInt("wins");
                player.createdAt = rs.getTimestamp("created_at");

                players.add(player);
            }

        } catch (SQLException e) {
            System.err.println("❌ Error getting online users: " + e.getMessage());
        }

        return players;
    }
}