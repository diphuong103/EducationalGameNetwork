package com.edugame.server.network;

import com.edugame.common.Protocol;
import com.edugame.server.database.DatabaseConnection;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class GameServer {
    private ServerSocket serverSocket;
    private List<ClientHandler> connectedClients;
    private boolean running;
    private int port;

    public GameServer(int port) {
        this.port = port;
        this.connectedClients = new ArrayList<>();
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

                    // Create new client handler
                    ClientHandler clientHandler = new ClientHandler(clientSocket);
                    connectedClients.add(clientHandler);

                    // Start client handler in new thread
                    Thread clientThread = new Thread(clientHandler);
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
        }
    }

    /**
     * Stop the server
     */
    public void stop() {
        try {
            running = false;

            // Disconnect all clients
            for (ClientHandler client : connectedClients) {
                // Client handler will clean up on its own
            }
            connectedClients.clear();

            // Close server socket
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }

            // Close database connection
            DatabaseConnection.getInstance().closeConnection();

            System.out.println("\n========================================");
            System.out.println("✓ Server stopped");
            System.out.println("========================================");

        } catch (IOException e) {
            System.err.println("✗ Error stopping server: " + e.getMessage());
        }
    }

    /**
     * Get number of connected clients
     */
    public int getConnectedClientsCount() {
        // Remove disconnected clients
        connectedClients.removeIf(client -> !client.isRunning());
        return connectedClients.size();
    }

    /**
     * Broadcast message to all connected clients
     */
    public void broadcastMessage(String message) {
        connectedClients.removeIf(client -> !client.isRunning());

        for (ClientHandler client : connectedClients) {
            // Send message to client
            // This will be implemented when we add chat functionality
        }
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return port;
    }
}