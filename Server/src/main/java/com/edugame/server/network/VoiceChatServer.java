package com.edugame.server.network;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * VoiceChatServer - UDP Server for voice chat
 *
 * Features:
 * - Receive audio packets from clients
 * - Broadcast to other clients in same room
 * - Track active voice clients per room
 * - Handle JOIN_VOICE and LEAVE_VOICE messages
 */
public class VoiceChatServer {

    private static final int UDP_PORT = 9999;
    private static final int BUFFER_SIZE = 2048;

    private DatagramSocket socket;
    private Thread receiveThread;
    private AtomicBoolean running = new AtomicBoolean(false);

    // Track clients: roomId -> List<ClientInfo>
    private Map<String, List<ClientInfo>> roomClients = new ConcurrentHashMap<>();

    // Track individual clients: userId -> ClientInfo
    private Map<Integer, ClientInfo> activeClients = new ConcurrentHashMap<>();

    /**
     * Client information for UDP communication
     */
    private static class ClientInfo {
        int userId;
        String roomId;
        InetAddress address;
        int port;
        long lastActivity;

        ClientInfo(int userId, String roomId, InetAddress address, int port) {
            this.userId = userId;
            this.roomId = roomId;
            this.address = address;
            this.port = port;
            this.lastActivity = System.currentTimeMillis();
        }

        void updateActivity() {
            this.lastActivity = System.currentTimeMillis();
        }

        boolean isActive() {
            return System.currentTimeMillis() - lastActivity < 30000; // 30s timeout
        }
    }

    /**
     * Start voice chat server
     *
     * @return
     */
    public boolean start() {
        if (running.get()) {
            System.out.println("⚠️ Voice chat server already running");
            return false;
        }

        try {
            socket = new DatagramSocket(UDP_PORT);
            socket.setReuseAddress(true);
            running.set(true);

            System.out.println("=".repeat(60));
            System.out.println("🎤 VOICE CHAT SERVER STARTED");
            System.out.println("   Port: " + UDP_PORT);
            System.out.println("   Buffer Size: " + BUFFER_SIZE + " bytes");
            System.out.println("=".repeat(60));

            // Start receive thread
            receiveThread = new Thread(this::receiveLoop, "VoiceChat-Server");
            receiveThread.start();

            // Start cleanup thread
            Thread cleanupThread = new Thread(this::cleanupLoop, "VoiceChat-Cleanup");
            cleanupThread.setDaemon(true);
            cleanupThread.start();
            return true;
        } catch (SocketException e) {
            System.err.println("❌ Failed to start voice chat server: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Stop voice chat server
     */
    public void stop() {
        System.out.println("🛑 Stopping voice chat server...");

        running.set(false);

        if (socket != null && !socket.isClosed()) {
            socket.close();
        }

        if (receiveThread != null) {
            receiveThread.interrupt();
        }

        roomClients.clear();
        activeClients.clear();

        System.out.println("✅ Voice chat server stopped");
    }

    /**
     * Main receive loop
     */
    private void receiveLoop() {
        byte[] buffer = new byte[BUFFER_SIZE];

        while (running.get()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);

                // Process packet in separate thread to avoid blocking
                byte[] data = Arrays.copyOf(packet.getData(), packet.getLength());
                InetAddress address = packet.getAddress();
                int port = packet.getPort();

                processPacket(data, address, port);

            } catch (IOException e) {
                if (running.get()) {
                    System.err.println("❌ Error receiving packet: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Process received packet
     */
    private void processPacket(byte[] data, InetAddress address, int port) {
        try {
            // Check if this is a control message (JOIN_VOICE or LEAVE_VOICE)
            String message = new String(data, 0, Math.min(50, data.length));

            if (message.startsWith("JOIN_VOICE:")) {
                handleJoinVoice(message, address, port);
                return;
            }

            if (message.startsWith("LEAVE_VOICE:")) {
                handleLeaveVoice(message);
                return;
            }

            // Otherwise, it's audio data - forward to room members
            handleAudioPacket(data, address, port);

        } catch (Exception e) {
            System.err.println("❌ Error processing packet: " + e.getMessage());
        }
    }

    /**
     * Handle JOIN_VOICE message
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

            ClientInfo client = new ClientInfo(userId, roomId, address, port);

            // Add to active clients
            activeClients.put(userId, client);

            // Add to room
            roomClients.computeIfAbsent(roomId, k -> new ArrayList<>()).add(client);

            System.out.println("✅ User " + userId + " joined voice chat in room " + roomId);
            System.out.println("   Address: " + address + ":" + port);
            System.out.println("   Room members: " + roomClients.get(roomId).size());

            // Send confirmation packet
            sendConfirmation(client, "VOICE_JOINED");

        } catch (Exception e) {
            System.err.println("❌ Error handling JOIN_VOICE: " + e.getMessage());
        }
    }

    /**
     * Handle LEAVE_VOICE message
     * Format: LEAVE_VOICE:userId:roomId
     */
    private void handleLeaveVoice(String message) {
        try {
            String[] parts = message.split(":");
            if (parts.length != 3) return;

            int userId = Integer.parseInt(parts[1]);
            String roomId = parts[2];

            // Remove from active clients
            ClientInfo client = activeClients.remove(userId);

            if (client != null) {
                // Remove from room
                List<ClientInfo> roomMembers = roomClients.get(roomId);
                if (roomMembers != null) {
                    roomMembers.removeIf(c -> c.userId == userId);

                    if (roomMembers.isEmpty()) {
                        roomClients.remove(roomId);
                    }
                }

                System.out.println("✅ User " + userId + " left voice chat from room " + roomId);

                // Send confirmation
                sendConfirmation(client, "VOICE_LEFT");
            }

        } catch (Exception e) {
            System.err.println("❌ Error handling LEAVE_VOICE: " + e.getMessage());
        }
    }

    /**
     * Handle audio packet - broadcast to room members
     */
    private void handleAudioPacket(byte[] data, InetAddress senderAddress, int senderPort) {
        try {
            // Parse packet metadata
            if (data.length < 12) return; // Minimum: userId(4) + roomIdLen(4) + roomId(1+) + audio

            int userId = ((data[0] & 0xFF) << 24) |
                    ((data[1] & 0xFF) << 16) |
                    ((data[2] & 0xFF) << 8) |
                    (data[3] & 0xFF);

            int roomIdLength = ((data[4] & 0xFF) << 24) |
                    ((data[5] & 0xFF) << 16) |
                    ((data[6] & 0xFF) << 8) |
                    (data[7] & 0xFF);

            if (data.length < 8 + roomIdLength) return;

            String roomId = new String(data, 8, roomIdLength);

            // Update client activity
            ClientInfo sender = activeClients.get(userId);
            if (sender != null) {
                sender.updateActivity();
            }

            // Get room members
            List<ClientInfo> roomMembers = roomClients.get(roomId);
            if (roomMembers == null || roomMembers.isEmpty()) {
                return;
            }

            // Broadcast to all members except sender
            int sent = 0;
            for (ClientInfo member : roomMembers) {
                if (member.userId != userId && member.isActive()) {
                    try {
                        DatagramPacket packet = new DatagramPacket(
                                data,
                                data.length,
                                member.address,
                                member.port
                        );
                        socket.send(packet);
                        sent++;
                    } catch (IOException e) {
                        System.err.println("⚠️ Failed to send to user " + member.userId);
                    }
                }
            }

            // Optional: Log every 100 packets to avoid spam
            if (Math.random() < 0.01) {
                System.out.println("📡 Audio packet from user " + userId +
                        " in room " + roomId +
                        " → sent to " + sent + " members");
            }

        } catch (Exception e) {
            System.err.println("❌ Error handling audio packet: " + e.getMessage());
        }
    }

    /**
     * Send confirmation message to client
     */
    private void sendConfirmation(ClientInfo client, String message) {
        try {
            byte[] data = message.getBytes();
            DatagramPacket packet = new DatagramPacket(
                    data,
                    data.length,
                    client.address,
                    client.port
            );
            socket.send(packet);
        } catch (IOException e) {
            System.err.println("⚠️ Failed to send confirmation: " + e.getMessage());
        }
    }

    /**
     * Cleanup inactive clients
     */
    private void cleanupLoop() {
        while (running.get()) {
            try {
                Thread.sleep(10000); // Check every 10 seconds

                // Remove inactive clients
                List<Integer> toRemove = new ArrayList<>();

                for (Map.Entry<Integer, ClientInfo> entry : activeClients.entrySet()) {
                    if (!entry.getValue().isActive()) {
                        toRemove.add(entry.getKey());
                    }
                }

                for (Integer userId : toRemove) {
                    ClientInfo client = activeClients.remove(userId);
                    if (client != null) {
                        // Remove from room
                        List<ClientInfo> roomMembers = roomClients.get(client.roomId);
                        if (roomMembers != null) {
                            roomMembers.removeIf(c -> c.userId == userId);
                            if (roomMembers.isEmpty()) {
                                roomClients.remove(client.roomId);
                            }
                        }

                        System.out.println("🧹 Cleaned up inactive user " + userId);
                    }
                }

            } catch (InterruptedException e) {
                break;
            }
        }
    }

    /**
     * Get active clients count in room
     */
    public int getRoomVoiceCount(String roomId) {
        List<ClientInfo> members = roomClients.get(roomId);
        return members != null ? members.size() : 0;
    }

    /**
     * Get all active rooms
     */
    public Set<String> getActiveRooms() {
        return new HashSet<>(roomClients.keySet());
    }

    /**
     * Get voice status for all users in room
     * Returns Map<userId, isActive>
     */
    public Map<Integer, Boolean> getRoomVoiceStatus(String roomId) {
        Map<Integer, Boolean> status = new HashMap<>();

        List<ClientInfo> members = roomClients.get(roomId);
        if (members != null) {
            for (ClientInfo client : members) {
                // Check if client is still active (within 30s timeout)
                if (client.isActive()) {
                    status.put(client.userId, true);
                }
            }
        }

        System.out.println("📊 Voice status for room " + roomId + ": " + status.size() + " active");
        return status;
    }
    /**
     * Remove client from voice chat
     * Called when player leaves room or disconnects
     */
    public void removeClient(int userId, String roomId) {
        try {
            System.out.println("🔇 Removing voice client: userId=" + userId + ", room=" + roomId);

            // Remove from active clients
            ClientInfo client = activeClients.remove(userId);

            // Remove from room
            List<ClientInfo> roomMembers = roomClients.get(roomId);
            if (roomMembers != null) {
                roomMembers.removeIf(c -> c.userId == userId);

                // Clean up empty room
                if (roomMembers.isEmpty()) {
                    roomClients.remove(roomId);
                    System.out.println("   🧹 Cleaned up empty voice room: " + roomId);
                }
            }

            System.out.println("   ✅ Voice client removed");

        } catch (Exception e) {
            System.err.println("❌ Error removing voice client: " + e.getMessage());
        }
    }

    /**
     * Check if user is in voice chat
     */
    public boolean isUserInVoiceChat(int userId) {
        ClientInfo client = activeClients.get(userId);
        return client != null && client.isActive();
    }

    /**
     * Check if user is in voice chat in specific room
     */
    public boolean isUserInVoiceChatInRoom(int userId, String roomId) {
        List<ClientInfo> members = roomClients.get(roomId);
        if (members == null) return false;

        return members.stream()
                .anyMatch(c -> c.userId == userId && c.isActive());
    }
    /**
     * Check if running
     */
    public boolean isRunning() {
        return running.get();
    }
}