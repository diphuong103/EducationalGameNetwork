package com.edugame.server;

import com.edugame.common.Protocol;
import com.edugame.server.network.GameServer;
import com.edugame.server.web.WebServer;  // ✅ IMPORT

import java.util.Scanner;

public class ServerMain {
    private static GameServer gameServer;
    private static WebServer webServer;  // ✅ THÊM

    public static void main(String[] args) {
        System.out.println("🚀 Starting Educational Game Server...\n");

        // ✅ Create both servers
        gameServer = new GameServer(Protocol.DEFAULT_PORT);
        webServer = new WebServer();

        // Start game server in separate thread
        Thread gameServerThread = new Thread(() -> gameServer.start());
        gameServerThread.start();

        // ✅ Start web server
        try {
            webServer.start();
        } catch (Exception e) {
            System.err.println("❌ Failed to start web server: " + e.getMessage());
        }

        // Wait for servers to initialize
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("\n========================================");
        System.out.println("✅ SERVERS RUNNING");
        System.out.println("========================================");
        System.out.println("🎮 Game Server: localhost:" + Protocol.DEFAULT_PORT);
        System.out.println("🌐 Web Server: " + webServer.getUrl());
        System.out.println("🏆 Leaderboard: " + webServer.getUrl() + "/leaderboard");
        System.out.println("📊 Statistics: " + webServer.getUrl() + "/statistics");
        System.out.println("========================================\n");

        // Command line interface
        Scanner scanner = new Scanner(System.in);
        boolean running = true;

        System.out.println("📝 Server Commands:");
        System.out.println("   'status'  - Show server status");
        System.out.println("   'clients' - Show connected clients");
        System.out.println("   'web'     - Show web URLs");  // ✅ NEW
        System.out.println("   'stop'    - Stop all servers");
        System.out.println("   'help'    - Show this help message");
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

                case "web":  // ✅ NEW COMMAND
                    showWebInfo();
                    break;

                case "stop":
                    System.out.println("\n🛑 Stopping servers...");
                    webServer.stop();      // ✅ Stop web server
                    gameServer.stop();     // Stop game server
                    running = false;
                    break;

                case "help":
                    showHelp();
                    break;

                case "":
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
        System.out.println("🎮 Game Server: " + (gameServer.isRunning() ? "✓ Running" : "✗ Stopped"));
        System.out.println("   Port: " + gameServer.getPort());
        System.out.println("   Connected: " + gameServer.getConnectedClientsCount() + " clients");
        System.out.println();
        System.out.println("🌐 Web Server: " + (webServer.isRunning() ? "✓ Running" : "✗ Stopped"));
        System.out.println("   URL: " + webServer.getUrl());
        System.out.println("========================================\n");
    }

    private static void showClients() {
        System.out.println("\n========================================");
        System.out.println("👥 CONNECTED CLIENTS");
        System.out.println("========================================");
        int count = gameServer.getConnectedClientsCount();
        System.out.println("Total: " + count + " client(s) connected");
        System.out.println("========================================\n");
    }

    private static void showWebInfo() {
        System.out.println("\n========================================");
        System.out.println("🌐 WEB SERVER INFORMATION");
        System.out.println("========================================");
        System.out.println("Status: " + (webServer.isRunning() ? "✓ Running" : "✗ Stopped"));
        System.out.println();
        System.out.println("📍 Available Pages:");
        System.out.println("   Home:        " + webServer.getUrl() + "/");
        System.out.println("   Leaderboard: " + webServer.getUrl() + "/leaderboard");
        System.out.println("   Statistics:  " + webServer.getUrl() + "/statistics");
        System.out.println();
        System.out.println("📡 API Endpoints:");
        System.out.println("   Stats JSON:  " + webServer.getUrl() + "/api/stats");
        System.out.println("   Top Players: " + webServer.getUrl() + "/api/leaderboard");
        System.out.println("========================================\n");
    }

    private static void showHelp() {
        System.out.println("\n========================================");
        System.out.println("📝 AVAILABLE COMMANDS");
        System.out.println("========================================");
        System.out.println("status  - Show status of all servers");
        System.out.println("clients - Show connected game clients");
        System.out.println("web     - Show web server URLs");
        System.out.println("stop    - Stop all servers gracefully");
        System.out.println("help    - Show this help message");
        System.out.println("========================================\n");
    }
}