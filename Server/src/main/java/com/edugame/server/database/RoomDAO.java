package com.edugame.server.database;

import com.edugame.server.model.Room;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class RoomDAO {

    private static final DateTimeFormatter LOG_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    public static Room createRoom(int hostId, String subject, String difficulty) {
        String sql = "INSERT INTO rooms " +
                "(room_name, host_id, subject, difficulty, max_players, current_players, status, is_private, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            String roomName = "Phòng #" + hostId;

            stmt.setString(1, roomName);
            stmt.setInt(2, hostId);
            stmt.setString(3, subject);
            stmt.setString(4, difficulty);
            stmt.setInt(5, 4); // mặc định 4 người
            stmt.setInt(6, 1); // người tạo phòng
            stmt.setString(7, "waiting");
            stmt.setBoolean(8, false);

            int affected = stmt.executeUpdate();
            logWithTime("✅ Inserted room into database, affected rows: " + affected);

            if (affected > 0) {
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        int roomId = rs.getInt(1);

                        logWithTime("✅ Room created in DB:");
                        logWithTime("   Room ID: " + roomId);
                        logWithTime("   Host ID: " + hostId);
                        logWithTime("   Subject: " + subject);
                        logWithTime("   Difficulty: " + difficulty);

                        // Create and return Room object
                        Room room = new Room(
                                roomId,
                                roomName,
                                hostId,
                                subject,
                                difficulty,
                                4,      // maxPlayers
                                1,      // currentPlayers
                                "waiting",
                                false
                        );

                        return room;
                    } else {
                        logWithTime("❌ No generated keys returned");
                    }
                }
            } else {
                logWithTime("❌ No rows affected");
            }

        } catch (SQLException e) {
            logWithTime("❌ SQL Exception: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            logWithTime("❌ Exception: " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    private static void logWithTime(String message) {
        String timestamp = LocalDateTime.now().format(LOG_TIME_FORMAT);
        System.out.println("[" + timestamp + "] [RoomDAO] " + message);
    }
}