package com.edugame.server.network;

import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * VoiceChatServer - Server-side UDP handler for voice chat
 *
 * Features:
 * - Receive audio packets from clients
 * - Broadcast to other clients in same room
 * - Manage voice chat sessions
 * - Handle JOIN/LEAVE voice commands
 */
public class VoiceChatServer {

    // ==================== Constants ====================
    private static final int UDP_PORT = 9999;
    private static final int BUFFER_SIZE = 2048;

    // ==================== Fields ====================
    private DatagramSocket socket;
    private Thread receiveThread;
    private volatile boolean isRunning = false;

    // Map: roomId -> Set of VoiceClient
    private Map<String, Set<VoiceClient>> roomClients = new ConcurrentHashMap<>();

    // Map: userId -> VoiceClient
    private Map<Integer, VoiceClient> activeClients = new ConcurrentHashMap<>();

    // ==================== Constructor ====================

    public VoiceChatServer() {
        System.out.println("🎙️ Initializing VoiceChatServer on port " + UDP_PORT);
    }

    // ==================== Public Methods ====================

    /**
     * Start voice chat server
     */
    public boolean start() {
        try {
            socket = new DatagramSocket(UDP_PORT);
            isRunning = true;

            receiveThread = new Thread(this::receiveLoop, "VoiceChat-Server");
            receiveThread.start();

            System.out.println("✅ VoiceChatServer started on port " + UDP_PORT);
            return true;

        } catch (SocketException e) {
            System.err.println("❌ Failed to start VoiceChatServer: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Stop voice chat server
     */
    public void stop() {
        System.out.println("🛑 Stopping VoiceChatServer...");

        isRunning = false;

        if (receiveThread != null) {
            receiveThread.interrupt();
        }

        if (socket != null && !socket.isClosed()) {
            socket.close();
        }

        roomClients.clear();
        activeClients.clear();

        System.out.println("✅ VoiceChatServer stopped");
    }

    /**
     * Remove client from voice chat (when leaving room)
     */
    public void removeClient(int userId, String roomId) {
        VoiceClient client = activeClients.remove(userId);

        if (client != null && roomId != null) {
            Set<VoiceClient> roomSet = roomClients.get(roomId);
            if (roomSet != null) {
                roomSet.remove(client);

                if (roomSet.isEmpty()) {
                    roomClients.remove(roomId);
                    System.out.println("🗑️ Room " + roomId + " voice chat cleaned up");
                }
            }

            System.out.println("🔇 User " + userId + " removed from voice chat");
        }
    }

    /**
     * Get active voice clients count in a room
     */
    public int getRoomVoiceCount(String roomId) {
        Set<VoiceClient> clients = roomClients.get(roomId);
        return clients != null ? clients.size() : 0;
    }

    // ==================== Private Methods ====================

    /**
     * Main receive loop
     */
    private void receiveLoop() {
        byte[] buffer = new byte[BUFFER_SIZE];

        System.out.println("🎧 Voice receive loop started");

        while (isRunning) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                // Process packet in separate thread to avoid blocking
                processPacket(packet);

            } catch (IOException e) {
                if (isRunning) {
                    System.err.println("❌ Error receiving packet: " + e.getMessage());
                }
            }
        }

        System.out.println("🛑 Voice receive loop stopped");
    }

    /**
     * Process received packet
     */
    private void processPacket(DatagramPacket packet) {
        try {
            byte[] data = packet.getData();
            int length = packet.getLength();

            // Check if this is a control message
            String message = new String(data, 0, Math.min(length, 100));

            if (message.startsWith("JOIN_VOICE:")) {
                handleJoinVoice(message, packet.getAddress(), packet.getPort());
                return;
            }

            if (message.startsWith("LEAVE_VOICE:")) {
                handleLeaveVoice(message);
                return;
            }

            // Otherwise, it's audio data - broadcast to room
            broadcastAudio(data, length, packet.getAddress(), packet.getPort());

        } catch (Exception e) {
            System.err.println("❌ Error processing packet: " + e.getMessage());
        }
    }

    /**
     * Handle JOIN_VOICE command
     * Format: JOIN_VOICE:userId:roomId
     */
    private void handleJoinVoice(String message, InetAddress address, int port) {
        try {
            String[] parts = message.split(":");
            if (parts.length != 3) {
                System.err.println("⚠️ Invalid JOIN_VOICE format: " + message);
                return;
            }

            int userId = Integer.parseInt(parts[1]);
            String roomId = parts[2];

            VoiceClient client = new VoiceClient(userId, roomId, address, port);

            // Add to active clients
            activeClients.put(userId, client);

            // Add to room
            roomClients.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet())
                    .add(client);

            System.out.println("✅ JOIN_VOICE: User " + userId + " joined room " + roomId);
            System.out.println("   Clients in room: " + roomClients.get(roomId).size());

        } catch (Exception e) {
            System.err.println("❌ Error handling JOIN_VOICE: " + e.getMessage());
        }
    }

    /**
     * Handle LEAVE_VOICE command
     * Format: LEAVE_VOICE:userId:roomId
     */
    private void handleLeaveVoice(String message) {
        try {
            String[] parts = message.split(":");
            if (parts.length != 3) {
                System.err.println("⚠️ Invalid LEAVE_VOICE format: " + message);
                return;
            }

            int userId = Integer.parseInt(parts[1]);
            String roomId = parts[2];

            removeClient(userId, roomId);

        } catch (Exception e) {
            System.err.println("❌ Error handling LEAVE_VOICE: " + e.getMessage());
        }
    }

    /**
     * Broadcast audio to all clients in room except sender
     */
    private void broadcastAudio(byte[] data, int length, InetAddress senderAddress, int senderPort) {
        try {
            // Parse userId and roomId from packet
            int userId = ((data[0] & 0xFF) << 24) |
                    ((data[1] & 0xFF) << 16) |
                    ((data[2] & 0xFF) << 8) |
                    (data[3] & 0xFF);

            int offset = 4;
            int roomIdLength = ((data[offset++] & 0xFF) << 24) |
                    ((data[offset++] & 0xFF) << 16) |
                    ((data[offset++] & 0xFF) << 8) |
                    (data[offset++] & 0xFF);

            String roomId = new String(data, offset, roomIdLength);

            // Get clients in same room
            Set<VoiceClient> clients = roomClients.get(roomId);

            if (clients == null || clients.isEmpty()) {
                return;
            }

            // Broadcast to all except sender
            DatagramPacket packet = new DatagramPacket(data, length, senderAddress, senderPort);

            int broadcastCount = 0;
            for (VoiceClient client : clients) {
                // Skip sender (echo prevention)
                if (client.userId == userId) {
                    continue;
                }

                try {
                    packet.setAddress(client.address);
                    packet.setPort(client.port);
                    socket.send(packet);
                    broadcastCount++;

                } catch (IOException e) {
                    System.err.println("⚠️ Failed to send to client " + client.userId);
                }
            }

            // Log periodically (every 100 packets)
            if (Math.random() < 0.01) {
                System.out.println("📡 Broadcast audio from user " + userId +
                        " to " + broadcastCount + " clients in room " + roomId);
            }

        } catch (Exception e) {
            System.err.println("❌ Error broadcasting audio: " + e.getMessage());
        }
    }

    // ==================== Inner Classes ====================

    /**
     * Voice client info
     */
    private static class VoiceClient {
        int userId;
        String roomId;
        InetAddress address;
        int port;
        long lastActivity;

        public VoiceClient(int userId, String roomId, InetAddress address, int port) {
            this.userId = userId;
            this.roomId = roomId;
            this.address = address;
            this.port = port;
            this.lastActivity = System.currentTimeMillis();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            VoiceClient that = (VoiceClient) o;
            return userId == that.userId;
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId);
        }
    }
}