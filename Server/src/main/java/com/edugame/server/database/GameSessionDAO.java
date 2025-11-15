package com.edugame.server.database;

import java.sql.*;

/**
 * GameSessionDAO - Quản lý game_sessions table
 */
public class GameSessionDAO {

    /**
     * Tạo session mới và trả về session_id
     *
     * @param roomId - ID phòng (String như "ROOM_123")
     * @param subject - Môn học
     * @param difficulty - Độ khó
     * @param totalQuestions - Tổng số câu hỏi
     * @return session_id (auto-generated) hoặc -1 nếu lỗi
     */
    public int createSession(String roomId, String subject, String difficulty, int totalQuestions) {
        String query = """
            INSERT INTO game_sessions (
                room_id, subject, difficulty, total_questions, started_at
            ) VALUES (?, ?, ?, ?, NOW())
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {

            // Convert roomId String to int
            int roomIdInt = convertRoomIdToInt(roomId);

            stmt.setInt(1, roomIdInt);
            stmt.setString(2, subject.toLowerCase()); // enum: 'math', 'english', 'literature'
            stmt.setString(3, difficulty.toLowerCase()); // enum: 'easy', 'medium', 'hard'
            stmt.setInt(4, totalQuestions);

            int rows = stmt.executeUpdate();

            if (rows > 0) {
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        int sessionId = rs.getInt(1);
                        System.out.println("✅ [GameSessionDAO] Created session ID: " + sessionId);
                        System.out.println("   Room: " + roomId + " (" + roomIdInt + ")");
                        System.out.println("   Subject: " + subject + " | Difficulty: " + difficulty);
                        System.out.println("   Questions: " + totalQuestions);
                        return sessionId;
                    }
                }
            }

        } catch (SQLException e) {
            System.err.println("❌ [GameSessionDAO] Error creating session:");
            System.err.println("   Room: " + roomId + " | Subject: " + subject);
            System.err.println("   Error: " + e.getMessage());
            e.printStackTrace();
        }

        return -1; // Failed
    }

    /**
     * Cập nhật thời gian kết thúc game
     *
     * @param sessionId - ID của session
     * @return true nếu thành công
     */
    public boolean finishSession(int sessionId) {
        String query = "UPDATE game_sessions SET finished_at = NOW() WHERE session_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, sessionId);
            int rows = stmt.executeUpdate();

            if (rows > 0) {
                System.out.println("✅ [GameSessionDAO] Finished session ID: " + sessionId);
                return true;
            }

        } catch (SQLException e) {
            System.err.println("❌ [GameSessionDAO] Error finishing session: " + e.getMessage());
        }

        return false;
    }

    /**
     * Lấy session_id từ room_id
     *
     * @param roomId - ID phòng (String)
     * @return session_id hoặc -1 nếu không tìm thấy
     */
    public int getSessionIdByRoomId(String roomId) {
        String query = """
            SELECT session_id FROM game_sessions 
            WHERE room_id = ? 
            ORDER BY started_at DESC 
            LIMIT 1
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            int roomIdInt = convertRoomIdToInt(roomId);
            stmt.setInt(1, roomIdInt);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("session_id");
                }
            }

        } catch (SQLException e) {
            System.err.println("❌ [GameSessionDAO] Error getting session ID: " + e.getMessage());
        }

        return -1;
    }

    /**
     * Chuyển đổi roomId từ String sang int
     *
     * @param roomId - String như "ROOM_123" hoặc "123"
     * @return int value
     */
    private int convertRoomIdToInt(String roomId) {
        try {
            // Trường hợp 1: roomId đã là số thuần "123"
            return Integer.parseInt(roomId);
        } catch (NumberFormatException e1) {
            try {
                // Trường hợp 2: roomId có format "ROOM_123" -> lấy phần số
                String numbers = roomId.replaceAll("[^0-9]", "");
                if (!numbers.isEmpty()) {
                    return Integer.parseInt(numbers);
                }
            } catch (NumberFormatException e2) {
                // Ignore
            }

            // Trường hợp 3: Không parse được -> dùng hashCode
            return Math.abs(roomId.hashCode() % 1000000); // Giới hạn trong 6 chữ số
        }
    }

    /**
     * Lấy thông tin session
     */
    public GameSessionInfo getSessionInfo(int sessionId) {
        String query = "SELECT * FROM game_sessions WHERE session_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, sessionId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    GameSessionInfo info = new GameSessionInfo();
                    info.sessionId = rs.getInt("session_id");
                    info.roomId = rs.getInt("room_id");
                    info.subject = rs.getString("subject");
                    info.difficulty = rs.getString("difficulty");
                    info.totalQuestions = rs.getInt("total_questions");
                    info.startedAt = rs.getTimestamp("started_at");
                    info.finishedAt = rs.getTimestamp("finished_at");
                    return info;
                }
            }

        } catch (SQLException e) {
            System.err.println("❌ [GameSessionDAO] Error getting session info: " + e.getMessage());
        }

        return null;
    }

    /**
     * Inner class cho session info
     */
    public static class GameSessionInfo {
        public int sessionId;
        public int roomId;
        public String subject;
        public String difficulty;
        public int totalQuestions;
        public Timestamp startedAt;
        public Timestamp finishedAt;
    }
}