package com.edugame.server.model;

public class Room {
    private String roomId;
    private String roomName;
    private int hostId;
    private String subject;
    private String difficulty;
    private int maxPlayers;
    private int currentPlayers;
    private String status;
    private boolean isPrivate;

    public Room(String roomId, String roomName, int hostId, String subject,
                String difficulty, int maxPlayers, int currentPlayers,
                String status, boolean isPrivate) {
        this.roomId = roomId;
        this.roomName = roomName;
        this.hostId = hostId;
        this.subject = subject;
        this.difficulty = difficulty;
        this.maxPlayers = maxPlayers;
        this.currentPlayers = currentPlayers;
        this.status = status;
        this.isPrivate = isPrivate;
    }

    // getters
    public String getRoomId() { return roomId; }
    public String getRoomName() { return roomName; }
    public int getHostId() { return hostId; }
    public String getSubject() { return subject; }
    public String getDifficulty() { return difficulty; }
    public int getMaxPlayers() { return maxPlayers; }
    public int getCurrentPlayers() { return currentPlayers; }
    public String getStatus() { return status; }
    public boolean isPrivate() { return isPrivate; }
}
