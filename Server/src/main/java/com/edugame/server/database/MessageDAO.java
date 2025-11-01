package com.edugame.server.database;

import com.edugame.server.model.Message;

import java.sql.*;
import java.util.*;

public class MessageDAO {

    /**
     * Lấy lịch sử tin nhắn giữa 2 user
     */
    public List<Message> getMessages(int userId1, int userId2, int limit) {
        List<Message> messages = new ArrayList<>();

        String query = """
            SELECT m.*, 
                   sender.username as sender_username,
                   sender.full_name as sender_name,
                   sender.avatar_url as sender_avatar
            FROM private_messages m
            INNER JOIN users sender ON m.sender_id = sender.user_id
            WHERE (m.sender_id = ? AND m.receiver_id = ?)
               OR (m.sender_id = ? AND m.receiver_id = ?)
            ORDER BY m.sent_at DESC
            LIMIT ?
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, userId1);
            stmt.setInt(2, userId2);
            stmt.setInt(3, userId2);
            stmt.setInt(4, userId1);
            stmt.setInt(5, limit);

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                Message msg = new Message();
                msg.setMessageId(rs.getInt("message_id"));
                msg.setSenderId(rs.getInt("sender_id"));
                msg.setReceiverId(rs.getInt("receiver_id"));
                msg.setContent(rs.getString("message_content"));
                msg.setSentAt(rs.getTimestamp("sent_at"));
                msg.setRead(rs.getBoolean("is_read"));
                msg.setReadAt(rs.getTimestamp("read_at"));
                msg.setSenderUsername(rs.getString("sender_username"));
                msg.setSenderName(rs.getString("sender_name"));
                msg.setSenderAvatar(rs.getString("sender_avatar"));

                messages.add(msg);
            }

            // Reverse để hiển thị từ cũ đến mới
            Collections.reverse(messages);

        } catch (SQLException e) {
            System.err.println("Error getting messages: " + e.getMessage());
            e.printStackTrace();
        }

        return messages;
    }

    /**
     * Gửi tin nhắn mới
     */
    public Message sendMessage(int senderId, int receiverId, String content) {
        String query = """
            INSERT INTO private_messages 
            (sender_id, receiver_id, message_content, sent_at, is_read)
            VALUES (?, ?, ?, NOW(), FALSE)
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setInt(1, senderId);
            stmt.setInt(2, receiverId);
            stmt.setString(3, content);

            int affected = stmt.executeUpdate();

            if (affected > 0) {
                ResultSet rs = stmt.getGeneratedKeys();
                if (rs.next()) {
                    int messageId = rs.getInt(1);

                    // Update conversation
                    updateConversation(senderId, receiverId, messageId);

                    // Return full message object
                    return getMessageById(messageId);
                }
            }

        } catch (SQLException e) {
            System.err.println("Error sending message: " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Đánh dấu tin nhắn đã đọc
     */
    public boolean markAsRead(int messageId, int userId) {
        String query = """
            UPDATE private_messages
            SET is_read = TRUE, read_at = NOW()
            WHERE message_id = ? 
              AND receiver_id = ?
              AND is_read = FALSE
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
     * Đánh dấu tất cả tin nhắn từ user X đã đọc
     */
    public int markAllAsRead(int receiverId, int senderId) {
        String query = """
            UPDATE private_messages
            SET is_read = TRUE, read_at = NOW()
            WHERE receiver_id = ? 
              AND sender_id = ?
              AND is_read = FALSE
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, receiverId);
            stmt.setInt(2, senderId);

            return stmt.executeUpdate();

        } catch (SQLException e) {
            System.err.println("Error marking all messages as read: " + e.getMessage());
            e.printStackTrace();
        }

        return 0;
    }

    /**
     * Đếm tin nhắn chưa đọc từ một user
     */
    public int getUnreadCount(int receiverId, int senderId) {
        String query = """
            SELECT COUNT(*) as count
            FROM private_messages
            WHERE receiver_id = ? 
              AND sender_id = ?
              AND is_read = FALSE
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, receiverId);
            stmt.setInt(2, senderId);

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
     * Lấy tổng số tin nhắn chưa đọc
     */
    public int getTotalUnreadCount(int userId) {
        String query = """
            SELECT COUNT(*) as count
            FROM private_messages
            WHERE receiver_id = ? AND is_read = FALSE
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, userId);

            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("count");
            }

        } catch (SQLException e) {
            System.err.println("Error getting total unread count: " + e.getMessage());
            e.printStackTrace();
        }

        return 0;
    }

    /**
     * Lấy message theo ID
     */
    private Message getMessageById(int messageId) {
        String query = """
            SELECT m.*, 
                   sender.username as sender_username,
                   sender.full_name as sender_name,
                   sender.avatar_url as sender_avatar
            FROM private_messages m
            INNER JOIN users sender ON m.sender_id = sender.user_id
            WHERE m.message_id = ?
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, messageId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                Message msg = new Message();
                msg.setMessageId(rs.getInt("message_id"));
                msg.setSenderId(rs.getInt("sender_id"));
                msg.setReceiverId(rs.getInt("receiver_id"));
                msg.setContent(rs.getString("message_content"));
                msg.setSentAt(rs.getTimestamp("sent_at"));
                msg.setRead(rs.getBoolean("is_read"));
                msg.setReadAt(rs.getTimestamp("read_at"));
                msg.setSenderUsername(rs.getString("sender_username"));
                msg.setSenderName(rs.getString("sender_name"));
                msg.setSenderAvatar(rs.getString("sender_avatar"));
                return msg;
            }

        } catch (SQLException e) {
            System.err.println("Error getting message by ID: " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Update conversation (last message, unread count)
     */
    private void updateConversation(int senderId, int receiverId, int messageId) {
        String selectQuery = """
        SELECT conversation_id, user1_id, user2_id
        FROM conversations
        WHERE (user1_id = ? AND user2_id = ?)
           OR (user1_id = ? AND user2_id = ?)
    """;

        String insertQuery = """
        INSERT INTO conversations (user1_id, user2_id, last_message_id, last_message_at)
        VALUES (?, ?, ?, NOW())
    """;

        String updateQuery = """
        UPDATE conversations
        SET last_message_id = ?, last_message_at = NOW(),
            user1_unread_count = CASE WHEN user1_id = ? THEN user1_unread_count ELSE user1_unread_count + 1 END,
            user2_unread_count = CASE WHEN user2_id = ? THEN user2_unread_count ELSE user2_unread_count + 1 END
        WHERE conversation_id = ?
    """;

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);

            int conversationId = -1;

            try (PreparedStatement stmt = conn.prepareStatement(selectQuery)) {
                stmt.setInt(1, senderId);
                stmt.setInt(2, receiverId);
                stmt.setInt(3, receiverId);
                stmt.setInt(4, senderId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    conversationId = rs.getInt("conversation_id");
                }
            }

            if (conversationId == -1) {
                // Chưa có hội thoại → tạo mới
                try (PreparedStatement stmt = conn.prepareStatement(insertQuery)) {
                    stmt.setInt(1, senderId);
                    stmt.setInt(2, receiverId);
                    stmt.setInt(3, messageId);
                    stmt.executeUpdate();
                }
            } else {
                // Cập nhật hội thoại hiện có
                try (PreparedStatement stmt = conn.prepareStatement(updateQuery)) {
                    stmt.setInt(1, messageId);
                    stmt.setInt(2, senderId);
                    stmt.setInt(3, receiverId);
                    stmt.setInt(4, conversationId);
                    stmt.executeUpdate();
                }
            }

            conn.commit();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

}