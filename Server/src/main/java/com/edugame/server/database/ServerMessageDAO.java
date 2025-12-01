package com.edugame.server.database;

import com.edugame.server.model.ServerMessage;
import java.sql.*;
import java.util.*;

public class ServerMessageDAO {

    /**
     * Gửi broadcast message (tất cả users)
     */
    public ServerMessage sendBroadcast(String senderName, String content, boolean isImportant) {
        String query = """
            INSERT INTO server_messages 
            (message_type, sender_type, sender_name, message_content, is_important, sent_at)
            VALUES ('broadcast', 'admin', ?, ?, ?, NOW())
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, senderName);
            stmt.setString(2, content);
            stmt.setBoolean(3, isImportant);

            int affected = stmt.executeUpdate();

            if (affected > 0) {
                ResultSet rs = stmt.getGeneratedKeys();
                if (rs.next()) {
                    int messageId = rs.getInt(1);
                    return getMessageById(messageId);
                }
            }

        } catch (SQLException e) {
            System.err.println("Error sending broadcast: " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Gửi group message (nhiều users)
     */
    public ServerMessage sendGroupMessage(String senderName, String content,
                                          List<Integer> userIds, boolean isImportant) {
        String insertMsg = """
            INSERT INTO server_messages 
            (message_type, sender_type, sender_name, message_content, is_important, sent_at)
            VALUES ('group', 'admin', ?, ?, ?, NOW())
        """;

        String insertRecipient = """
            INSERT INTO server_message_recipients (message_id, user_id, is_read)
            VALUES (?, ?, FALSE)
        """;

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);

            int messageId;

            // Insert message
            try (PreparedStatement stmt = conn.prepareStatement(insertMsg, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, senderName);
                stmt.setString(2, content);
                stmt.setBoolean(3, isImportant);
                stmt.executeUpdate();

                ResultSet rs = stmt.getGeneratedKeys();
                if (!rs.next()) {
                    conn.rollback();
                    return null;
                }
                messageId = rs.getInt(1);
            }

            // Insert recipients
            try (PreparedStatement stmt = conn.prepareStatement(insertRecipient)) {
                for (int userId : userIds) {
                    stmt.setInt(1, messageId);
                    stmt.setInt(2, userId);
                    stmt.addBatch();
                }
                stmt.executeBatch();
            }

            conn.commit();
            return getMessageById(messageId);

        } catch (SQLException e) {
            System.err.println("Error sending group message: " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Gửi private message (1 user)
     */
    public ServerMessage sendPrivateMessage(String senderName, String content,
                                            int userId, boolean isImportant) {
        return sendGroupMessage(senderName, content, Arrays.asList(userId), isImportant);
    }

    /**
     * Lấy tất cả tin nhắn broadcast
     */
    public List<ServerMessage> getBroadcastMessages(int limit) {
        String query = """
            SELECT * FROM server_messages
            WHERE message_type = 'broadcast'
            ORDER BY sent_at DESC
            LIMIT ?
        """;

        List<ServerMessage> messages = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, limit);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                messages.add(mapResultSetToMessage(rs));
            }

        } catch (SQLException e) {
            System.err.println("Error getting broadcast messages: " + e.getMessage());
            e.printStackTrace();
        }

        Collections.reverse(messages);
        return messages;
    }

    /**
     * Lấy tin nhắn cho một user cụ thể (broadcast + private + group)
     */
    public List<ServerMessage> getMessagesForUser(int userId, int limit) {
        String query = """
            SELECT m.*, r.is_read, r.read_at
            FROM server_messages m
            LEFT JOIN server_message_recipients r 
                ON m.message_id = r.message_id AND r.user_id = ?
            WHERE m.message_type = 'broadcast'
               OR (m.message_type IN ('group', 'private') AND r.user_id = ?)
            ORDER BY m.sent_at DESC
            LIMIT ?
        """;

        List<ServerMessage> messages = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, userId);
            stmt.setInt(2, userId);
            stmt.setInt(3, limit);

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                ServerMessage msg = mapResultSetToMessage(rs);

                // Set read status for non-broadcast messages
                if (!rs.getString("message_type").equals("broadcast")) {
                    msg.setRead(rs.getBoolean("is_read"));
                    msg.setReadAt(rs.getTimestamp("read_at"));
                }

                messages.add(msg);
            }

        } catch (SQLException e) {
            System.err.println("Error getting user messages: " + e.getMessage());
            e.printStackTrace();
        }

        Collections.reverse(messages);
        return messages;
    }

    /**
     * Đánh dấu tin nhắn đã đọc
     */
    public boolean markAsRead(int messageId, int userId) {
        String query = """
            UPDATE server_message_recipients
            SET is_read = TRUE, read_at = NOW()
            WHERE message_id = ? AND user_id = ? AND is_read = FALSE
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, messageId);
            stmt.setInt(2, userId);

            return stmt.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println("Error marking message as read: " + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Đánh dấu tất cả tin nhắn server đã đọc
     */
    public int markAllAsRead(int userId) {
        String query = """
            UPDATE server_message_recipients
            SET is_read = TRUE, read_at = NOW()
            WHERE user_id = ? AND is_read = FALSE
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, userId);
            return stmt.executeUpdate();

        } catch (SQLException e) {
            System.err.println("Error marking all as read: " + e.getMessage());
            e.printStackTrace();
        }

        return 0;
    }

    /**
     * Đếm tin nhắn chưa đọc
     */
    public int getUnreadCount(int userId) {
        String query = """
            SELECT COUNT(*) as count
            FROM server_message_recipients
            WHERE user_id = ? AND is_read = FALSE
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, userId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return rs.getInt("count");
            }

        } catch (SQLException e) {
            System.err.println("Error getting unread count: " + e.getMessage());
            e.printStackTrace();
        }

        return 0;
    }

    /**
     * Xóa tin nhắn
     */
    public boolean deleteMessage(int messageId) {
        String query = "DELETE FROM server_messages WHERE message_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, messageId);
            return stmt.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println("Error deleting message: " + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Lấy danh sách users đã nhận tin nhắn
     */
    public List<Integer> getRecipients(int messageId) {
        String query = """
            SELECT user_id FROM server_message_recipients
            WHERE message_id = ?
        """;

        List<Integer> recipients = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, messageId);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                recipients.add(rs.getInt("user_id"));
            }

        } catch (SQLException e) {
            System.err.println("Error getting recipients: " + e.getMessage());
            e.printStackTrace();
        }

        return recipients;
    }

    /**
     * Helper: Get message by ID
     */
    private ServerMessage getMessageById(int messageId) {
        String query = "SELECT * FROM server_messages WHERE message_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, messageId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return mapResultSetToMessage(rs);
            }

        } catch (SQLException e) {
            System.err.println("Error getting message by ID: " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Helper: Map ResultSet to ServerMessage
     */
    private ServerMessage mapResultSetToMessage(ResultSet rs) throws SQLException {
        ServerMessage msg = new ServerMessage();
        msg.setMessageId(rs.getInt("message_id"));
        msg.setMessageType(rs.getString("message_type"));
        msg.setSenderType(rs.getString("sender_type"));
        msg.setSenderName(rs.getString("sender_name"));
        msg.setContent(rs.getString("message_content"));
        msg.setSentAt(rs.getTimestamp("sent_at"));
        msg.setExpiresAt(rs.getTimestamp("expires_at"));
        msg.setImportant(rs.getBoolean("is_important"));
        return msg;
    }

    /**
     * Lấy X tin nhắn gần nhất (broadcast + group + private)
     */
    public List<ServerMessage> getRecentMessages(int limit) {
        String query = """
        SELECT m.*
        FROM server_messages m
        ORDER BY m.sent_at DESC
        LIMIT ?
    """;

        List<ServerMessage> messages = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, limit);
            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                messages.add(mapResultSetToMessage(rs));
            }

        } catch (SQLException e) {
            System.err.println("Error getting recent messages: " + e.getMessage());
            e.printStackTrace();
        }

        // Đảo ngược để hiển thị từ cũ → mới
        Collections.reverse(messages);
        return messages;
    }

}