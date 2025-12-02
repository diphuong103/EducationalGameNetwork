package com.edugame.server.network;

import com.edugame.common.Protocol;
import com.edugame.server.database.DatabaseConnection;
import com.edugame.server.game.GameRoomManager;
import com.edugame.server.game.MatchmakingManager;
import com.edugame.server.model.User;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.edugame.server.network.VoiceChatServer;
import java.util.concurrent.CopyOnWriteArrayList;

public class GameServer {
    private ServerSocket serverSocket;
    private static VoiceChatServer voiceChatServer;
    private List<ClientHandler> connectedClients;
    private boolean running;
    private int port;
    private MatchmakingManager matchmakingManager;
    private final GameRoomManager roomManager;
    private static GameServer instance;

    private Thread monitorThread;
    private volatile boolean isMonitoring = false;

    public GameServer(int port) {
        this.port = port;
        this.connectedClients = new CopyOnWriteArrayList<>(); // Thread-safe
        this.running = false;
        this.roomManager = GameRoomManager.getInstance();
        this.matchmakingManager = new MatchmakingManager(roomManager);
        instance = this;

        System.out.println("✅ GameServer initialized!");
    }

    public static GameServer getInstance() {
        return instance;
    }

    public GameRoomManager getGameRoomManager() {
        return roomManager;
    }

    // getters để các class khác có thể dùng
    public MatchmakingManager getMatchmakingManager() {
        return matchmakingManager;
    }

    public GameRoomManager getRoomManager() {
        return roomManager;
    }

    /**
     * Get VoiceChatServer instance
     */
    public static VoiceChatServer getVoiceChatServer() {
        return voiceChatServer;
    }

    /**
     * Start the server
     */
    public void start() {
        try {
            // Test database connection first
            DatabaseConnection dbConnection = DatabaseConnection.getInstance();
            if (!dbConnection.testConnection()) {
                System.err.println("✗ Failed to connect to database! Server cannot start.");
                return;
            }

            serverSocket = new ServerSocket(port);
            serverSocket.setReuseAddress(true);
            running = true;

            System.out.println("========================================");
            System.out.println("🎮 MATH ADVENTURE SERVER");
            System.out.println("========================================");
            System.out.println("✓ Server started on port: " + port + " (TCP)");
            System.out.println("✓ Database connected");

            GameRoomManager gameRoomManager = new GameRoomManager();

            matchmakingManager = new MatchmakingManager(GameRoomManager.getInstance());


            // Start Voice Chat UDP Server
            System.out.println("🎙️ Starting Voice Chat Server...");
            voiceChatServer = new VoiceChatServer();
            boolean voiceStarted = voiceChatServer.start();

            if (voiceStarted) {
                System.out.println("✅ Voice Chat Server started successfully");
            } else {
                System.err.println("⚠️ Voice Chat Server failed to start (continuing without voice)");
            }

            System.out.println("========================================");
            System.out.println("🎮 MATH ADVENTURE SERVER");
            System.out.println("========================================");
            System.out.println("✓ Server started on port: " + port);
            System.out.println("✓ Database connected");
            if (voiceStarted) {
                System.out.println("✓ Voice Chat enabled");
            }
            System.out.println("✓ Waiting for clients...");
            System.out.println("========================================\n");

            // Accept client connections
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();

                    // Create new client handler với reference đến server và voiceChatServer
                    ClientHandler clientHandler = new ClientHandler(clientSocket, this, matchmakingManager);
                    connectedClients.add(clientHandler);

                    // Start client handler in new thread
                    Thread clientThread = new Thread(clientHandler);
                    clientThread.setName("Client-" + connectedClients.size());
                    clientThread.start();

                    System.out.println("📊 Active connections: " + connectedClients.size());

                } catch (IOException e) {
                    if (running) {
                        System.err.println("✗ Error accepting client: " + e.getMessage());
                    }
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }

        } catch (IOException e) {
            System.err.println("✗ Could not start server on port " + port);
            e.printStackTrace();
        } finally {
            stop();
        }
    }

    /**
     * Stop the server
     */
    public void stop() {
        try {
            System.out.println("\n🛑 ========== SERVER SHUTDOWN INITIATED ==========");

            // 1. Set running flag to false FIRST
            running = false;

            // 2. Stop monitoring thread
            if (isMonitoring) {
                System.out.println("🛑 Stopping connection monitor...");
                stopConnectionMonitor();
            }

            // 3. Disconnect all clients with timeout
            System.out.println("🛑 Disconnecting " + connectedClients.size() + " clients...");

            List<ClientHandler> clientsCopy = new ArrayList<>(connectedClients);
            int disconnected = 0;

            for (ClientHandler client : clientsCopy) {
                try {
                    if (client.isRunning()) {
                        // Force disconnect
                        client.disconnect();
                        disconnected++;

                        // Small delay to allow graceful shutdown
                        Thread.sleep(50);
                    }
                } catch (Exception e) {
                    System.err.println("⚠️ Error disconnecting client: " + e.getMessage());
                }
            }

            System.out.println("✓ Disconnected " + disconnected + " clients");

            // 4. Clear client list
            connectedClients.clear();

            // 5. Stop Voice Chat Server
            if (voiceChatServer != null) {
                System.out.println("🛑 Stopping Voice Chat Server...");
                try {
                    voiceChatServer.stop();
                    System.out.println("✓ Voice Chat Server stopped");
                } catch (Exception e) {
                    System.err.println("⚠️ Error stopping voice chat: " + e.getMessage());
                }
            }

            // 6. Close server socket
            if (serverSocket != null && !serverSocket.isClosed()) {
                System.out.println("🛑 Closing server socket...");
                try {
                    serverSocket.close();
                    System.out.println("✓ Server socket closed");
                } catch (IOException e) {
                    System.err.println("⚠️ Error closing socket: " + e.getMessage());
                }
            }

            // 7. Give threads time to clean up
            Thread.sleep(500);

            System.out.println("========================================");
            System.out.println("✅ SERVER SHUTDOWN COMPLETE");
            System.out.println("========================================\n");

        } catch (Exception e) {
            System.err.println("❌ Error during server shutdown: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * ✅ Add this helper method to check if server is truly stopped
     */
    public boolean isStopped() {
        return !running &&
                (serverSocket == null || serverSocket.isClosed()) &&
                connectedClients.isEmpty();
    }
    // ========== BROADCAST FUNCTIONS ==========

    /**
     * Broadcast message to all clients EXCEPT sender
     * Used for chat messages
     */
    public void broadcastMessage(Map<String, Object> message, ClientHandler sender) {
        // Remove disconnected clients first
        connectedClients.removeIf(client -> !client.isRunning());

        int sentCount = 0;
        for (ClientHandler client : connectedClients) {
            if (client != sender && client.isRunning() && client.getCurrentUser() != null) {
                client.sendMessage(message);
                sentCount++;
                System.out.println("  → Sent to: " + client.getCurrentUser().getUsername());
            }
        }

        System.out.println("📢 Broadcast sent to " + sentCount + " clients");
    }

    /**
     * Broadcast system message to all clients EXCEPT sender
     * Used for join/leave notifications
     */
    public void broadcastSystemMessage(String message, ClientHandler sender) {
        Map<String, Object> systemMessage = new HashMap<>();
        systemMessage.put("type", "SYSTEM_MESSAGE");
        systemMessage.put("message", message);

        connectedClients.removeIf(client -> !client.isRunning());

        for (ClientHandler client : connectedClients) {
            if (client != sender && client.isRunning() && client.getCurrentUser() != null) {
                client.sendMessage(systemMessage);
            }
        }

        System.out.println("📣 System: " + message);
    }

    /**
     * Broadcast to ALL clients INCLUDING sender
     * Used for announcements
     */
    public void broadcastToAll(Map<String, Object> message) {
        connectedClients.removeIf(client -> !client.isRunning());

        for (ClientHandler client : connectedClients) {
            if (client.isRunning() && client.getCurrentUser() != null) {
                client.sendMessage(message);
            }
        }
    }

    /**
     * Get list of connected client handlers (for internal use)
     */
    public List<ClientHandler> getConnectedClients() {
        connectedClients.removeIf(client -> !client.isRunning());
        return new ArrayList<>(connectedClients);
    }

    /**
     * Broadcast simple string message to all clients
     * Legacy method for compatibility
     */
    public void broadcastMessage(String message) {
        Map<String, Object> data = new HashMap<>();
        data.put("type", "BROADCAST");
        data.put("message", message);
        broadcastToAll(data);
    }

    /**
     * Send message to specific user by username
     */
    public void sendToUser(String username, Map<String, Object> message) {
        connectedClients.removeIf(client -> !client.isRunning());

        for (ClientHandler client : connectedClients) {
            if (client.getCurrentUser() != null &&
                    client.getCurrentUser().getUsername().equals(username)) {
                client.sendMessage(message);
                break;
            }
        }
    }

    /**
     * Remove disconnected client from list
     */
    public void removeClient(ClientHandler client) {
        connectedClients.remove(client);
        System.out.println("🔌 Client removed. Active connections: " + connectedClients.size());
    }

    // ========== GETTERS ==========

    /**
     * Get number of connected clients
     */
    public int getConnectedClientsCount() {
        // Remove disconnected clients
        connectedClients.removeIf(client -> !client.isRunning());
        return connectedClients.size();
    }

    /**
     * Get number of logged-in users (authenticated)
     */
    public int getLoggedInUsersCount() {
        connectedClients.removeIf(client -> !client.isRunning());

        int count = 0;
        for (ClientHandler client : connectedClients) {
            if (client.getCurrentUser() != null) {
                count++;
            }
        }
        return count;
    }

    /**
     * Get list of online usernames
     */
    public List<String> getOnlineUsernames() {
        List<String> usernames = new ArrayList<>();
        connectedClients.removeIf(client -> !client.isRunning());

        for (ClientHandler client : connectedClients) {
            if (client.getCurrentUser() != null) {
                usernames.add(client.getCurrentUser().getUsername());
            }
        }
        return usernames;
    }

    /**
     * Check if user is online
     */
    public boolean isUserOnline(String username) {
        connectedClients.removeIf(client -> !client.isRunning());

        for (ClientHandler client : connectedClients) {
            if (client.getCurrentUser() != null &&
                    client.getCurrentUser().getUsername().equals(username)) {
                return true;
            }
        }
        return false;
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return port;
    }



    // ========== MAIN METHOD ==========

    /**
     * Main method to start the server
     */
    public static void main(String[] args) {
        // Default port
        int port = 8888;

        // Parse command line arguments
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number. Using default: 8888");
            }
        }

        // Create and start server
        GameServer server = new GameServer(port);

        // Add shutdown hook for graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n🛑 Shutdown signal received...");
            server.stop();
        }));

        // Start server (blocking call)
        server.start();
    }

    public boolean sendToUserId(int userId, Map<String, Object> message) {
        System.out.println("[SERVER] 🔍 Searching for userId=" + userId);

        synchronized (connectedClients) {
            // Debug: Show total connected clients
            System.out.println("[SERVER] 📊 Total connected clients: " + connectedClients.size());

            // Loop through all connected clients
            for (ClientHandler handler : connectedClients) {
                // Check if handler is valid and has logged-in user
                if (handler.isRunning() && handler.getCurrentUser() != null) {
                    User user = handler.getCurrentUser();
                    int currentHandlerUserId = user.getUserId();
                    String currentHandlerUsername = user.getUsername();

                    // Debug: Show each handler we're checking
                    System.out.println("[SERVER] 🔎 Checking handler: userId=" + currentHandlerUserId +
                            ", username=" + currentHandlerUsername);

                    // Found the matching user!
                    if (currentHandlerUserId == userId) {
                        System.out.println("[SERVER] ✅✅✅ FOUND MATCHING USER! Sending message...");
                        handler.sendMessage(message);
                        System.out.println("[SERVER] ✅ Message sent to userId=" + userId +
                                " (username=" + currentHandlerUsername + ")");
                        return true;
                    }
                }
            }
        }

        // User not found or offline
        System.out.println("[SERVER] ⚠️⚠️⚠️ User NOT FOUND or OFFLINE (userId=" + userId + ")");
        return false;
    }
    /**
     * ✅ Start connection monitoring thread
     */
    private void startConnectionMonitor() {
        isMonitoring = true;

        monitorThread = new Thread(() -> {
            System.out.println("🔍 Connection monitor STARTED");

            while (isMonitoring) {
                try {
                    Thread.sleep(30000); // Check every 30 seconds

                    List<ClientHandler> deadClients = new ArrayList<>();

                    // Check all connected clients
                    for (ClientHandler client : connectedClients) {
                        if (!client.isClientAlive()) {
                            System.err.println("⚠️ Dead client detected: " +
                                    (client.getCurrentUser() != null ?
                                            client.getCurrentUser().getUsername() : "unknown"));
                            deadClients.add(client);
                        }
                    }

                    // Remove dead clients
                    for (ClientHandler client : deadClients) {
                        connectedClients.remove(client);
                        // Force disconnect
                        try {
                            client.disconnect();
                        } catch (Exception e) {
                            // Ignore
                        }
                    }

                    if (deadClients.size() > 0) {
                        System.out.println("🧹 Cleaned up " + deadClients.size() + " dead clients");
                    }

                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    System.err.println("❌ Monitor error: " + e.getMessage());
                }
            }

            System.out.println("🔍 Connection monitor STOPPED");

        }, "ConnectionMonitor");

        monitorThread.setDaemon(true);
        monitorThread.start();
    }

    /**
     * Stop monitoring
     */
    private void stopConnectionMonitor() {
        isMonitoring = false;
        if (monitorThread != null && monitorThread.isAlive()) {
            monitorThread.interrupt();
        }
    }

}