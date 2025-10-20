package com.edugame.server;

import com.edugame.common.Protocol;
import com.edugame.server.network.GameServer;

import java.util.Scanner;

public class ServerMain {
    private static GameServer server;

    public static void main(String[] args) {
        System.out.println("🚀 Starting Math Adventure Server...\n");

        // Create server instance
        server = new GameServer(Protocol.DEFAULT_PORT);

        // Start server in separate thread
        Thread serverThread = new Thread(() -> server.start());
        serverThread.start();

        // Wait for server to initialize
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // Command line interface
        Scanner scanner = new Scanner(System.in);
        boolean running = true;

        System.out.println("\n📝 Server Commands:");
        System.out.println("   'status' - Show server status");
        System.out.println("   'clients' - Show connected clients");
        System.out.println("   'stop' - Stop the server");
        System.out.println("   'help' - Show this help message");
        System.out.println("========================================\n");

        while (running) {
            System.out.print("Server> ");
            String command = scanner.nextLine().trim().toLowerCase();

            switch (command) {
                case "status":
                    showStatus();
                    break;

                case "clients":
                    showClients();
                    break;

                case "stop":
                    System.out.println("\n🛑 Stopping server...");
                    server.stop();
                    running = false;
                    break;

                case "help":
                    showHelp();
                    break;

                case "":
                    // Ignore empty input
                    break;

                default:
                    System.out.println("❌ Unknown command: " + command);
                    System.out.println("   Type 'help' for available commands");
            }
        }

        scanner.close();
        System.exit(0);
    }

    private static void showStatus() {
        System.out.println("\n========================================");
        System.out.println("📊 SERVER STATUS");
        System.out.println("========================================");
        System.out.println("Status: " + (server.isRunning() ? "✓ Running" : "✗ Stopped"));
        System.out.println("Port: " + server.getPort());
        System.out.println("Connected Clients: " + server.getConnectedClientsCount());
        System.out.println("========================================\n");
    }

    private static void showClients() {
        System.out.println("\n========================================");
        System.out.println("👥 CONNECTED CLIENTS");
        System.out.println("========================================");
        int count = server.getConnectedClientsCount();
        System.out.println("Total: " + count + " client(s) connected");
        System.out.println("========================================\n");
    }

    private static void showHelp() {
        System.out.println("\n========================================");
        System.out.println("📝 AVAILABLE COMMANDS");
        System.out.println("========================================");
        System.out.println("status  - Show server status and statistics");
        System.out.println("clients - Show number of connected clients");
        System.out.println("stop    - Stop the server gracefully");
        System.out.println("help    - Show this help message");
        System.out.println("========================================\n");
    }
}