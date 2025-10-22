package com.edugame.server.network;

import com.edugame.common.Protocol;
import com.edugame.server.database.DatabaseConnection;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class GameServer {
    private ServerSocket serverSocket;
    private List<ClientHandler> connectedClients;
    private boolean running;
    private int port;

    public GameServer(int port) {
        this.port = port;
        this.connectedClients = new CopyOnWriteArrayList<>(); // Thread-safe
        this.running = false;
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
            running = true;

            System.out.println("========================================");
            System.out.println("🎮 MATH ADVENTURE SERVER");
            System.out.println("========================================");
            System.out.println("✓ Server started on port: " + port);
            System.out.println("✓ Database connected");
            System.out.println("✓ Waiting for clients...");
            System.out.println("========================================\n");

            // Accept client connections
            while (running) {
                try {
                    Socket clientSocket = serverSocket.accept();

                    // Create new client handler với reference đến server
                    ClientHandler clientHandler = new ClientHandler(clientSocket, this);
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
            running = false;

            // Disconnect all clients
            System.out.println("\n🛑 Disconnecting all clients...");
            for (ClientHandler client : connectedClients) {
                // Client handler will clean up on its own
                if (client.isRunning()) {
                    // Optionally send shutdown notification
                }
            }
            connectedClients.clear();

            // Close server socket
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }

            // Close database connection
            DatabaseConnection.getInstance().closeConnection();

            System.out.println("========================================");
            System.out.println("✓ Server stopped");
            System.out.println("========================================");

        } catch (IOException e) {
            System.err.println("✗ Error stopping server: " + e.getMessage());
        }
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
}