package com.edugame.server.database;

import com.edugame.server.model.Room;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class RoomDAO {

    private static final DateTimeFormatter LOG_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    public static Room createRoom(int hostId, String subject, String difficulty) {
        String sql = "INSERT INTO rooms " +
                "(room_id, room_name, host_id, subject, difficulty, max_players, current_players, status, is_private, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            int roomId = ThreadLocalRandom.current().nextInt(100000, 1000000);
            String roomName = "Phòng #" + hostId;

            stmt.setInt(1, roomId);          // room_id
            stmt.setString(2, roomName);     // room_name
            stmt.setInt(3, hostId);          // host_id
            stmt.setString(4, subject);      // subject
            stmt.setString(5, difficulty);   // difficulty
            stmt.setInt(6, 4);               // max_players
            stmt.setInt(7, 1);               // current_players
            stmt.setString(8, "waiting");    // status
            stmt.setBoolean(9, false);

            int affected = stmt.executeUpdate();
            logWithTime("✅ Inserted room into database, affected rows: " + affected);

            if (affected > 0) {
                Room room = new Room(
                        String.valueOf(roomId),
                        roomName,
                        hostId,
                        subject,
                        difficulty,
                        4,
                        1,
                        "waiting",
                        false
                );
                logWithTime("✅ Room created: " + roomId);
                return room;
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