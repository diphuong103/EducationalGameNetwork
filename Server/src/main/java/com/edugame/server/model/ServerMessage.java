package com.edugame.server.model;

import java.sql.Timestamp;

public class ServerMessage {
    private int messageId;
    private String messageType; // broadcast, group, private
    private String senderType;  // system, admin
    private String senderName;
    private String content;
    private Timestamp sentAt;
    private Timestamp expiresAt;
    private boolean isImportant;

    // For recipients
    private boolean isRead;
    private Timestamp readAt;

    // Constructors
    public ServerMessage() {}

    public ServerMessage(String messageType, String senderName, String content) {
        this.messageType = messageType;
        this.senderName = senderName;
        this.content = content;
        this.senderType = "admin";
        this.isImportant = false;
    }

    // Getters and Setters
    public int getMessageId() {
        return messageId;
    }

    public void setMessageId(int messageId) {
        this.messageId = messageId;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public String getSenderType() {
        return senderType;
    }

    public void setSenderType(String senderType) {
        this.senderType = senderType;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Timestamp getSentAt() {
        return sentAt;
    }

    public void setSentAt(Timestamp sentAt) {
        this.sentAt = sentAt;
    }

    public Timestamp getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Timestamp expiresAt) {
        this.expiresAt = expiresAt;
    }

    public boolean isImportant() {
        return isImportant;
    }

    public void setImportant(boolean important) {
        isImportant = important;
    }

    public boolean isRead() {
        return isRead;
    }

    public void setRead(boolean read) {
        isRead = read;
    }

    public Timestamp getReadAt() {
        return readAt;
    }

    public void setReadAt(Timestamp readAt) {
        this.readAt = readAt;
    }

    @Override
    public String toString() {
        return "ServerMessage{" +
                "messageId=" + messageId +
                ", type='" + messageType + '\'' +
                ", sender='" + senderName + '\'' +
                ", content='" + content + '\'' +
                ", sentAt=" + sentAt +
                '}';
    }
}