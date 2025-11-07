package com.edugame.server.database;

import com.edugame.server.model.User;

import java.sql.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class UserDAO {
    private Connection connection;

    public UserDAO() {
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
            pstmt.setString(4, age);
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
        String sql = "SELECT * FROM users WHERE user_id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, userId);
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
            System.err.println("✗ Error getting user: " + e.getMessage());
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


}