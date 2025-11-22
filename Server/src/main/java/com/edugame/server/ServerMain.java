package com.edugame.server;

import com.edugame.common.Protocol;
import com.edugame.server.network.GameServer;
import com.edugame.server.web.WebServer;
import com.edugame.server.web.CloudflareTunnel;  // ✅ NEW IMPORT

import java.util.Scanner;

public class ServerMain {
    private static GameServer gameServer;
    private static WebServer webServer;
    private static CloudflareTunnel cloudflareTunnel;  // ✅ NEW

    public static void main(String[] args) {
        System.out.println("🚀 Starting Educational Game Server...\n");

        // ✅ Create all servers
        gameServer = new GameServer(Protocol.DEFAULT_PORT);
        webServer = new WebServer();
        cloudflareTunnel = new CloudflareTunnel();  // ✅ NEW

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
        System.out.println("   'status'    - Show server status");
        System.out.println("   'clients'   - Show connected clients");
        System.out.println("   'web'       - Show web URLs");
        System.out.println("   'tunnel'    - Start Cloudflare Tunnel");  // ✅ NEW
        System.out.println("   'public'    - Show public URL");          // ✅ NEW
        System.out.println("   'stop'      - Stop all servers");
        System.out.println("   'help'      - Show this help message");
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

                case "web":
                    showWebInfo();
                    break;

                case "tunnel":  // ✅ NEW COMMAND
                    startTunnel();
                    break;

                case "public":  // ✅ NEW COMMAND
                    showPublicUrl();
                    break;

                case "stop":
                    System.out.println("\n🛑 Stopping servers...");
                    cloudflareTunnel.stop();  // ✅ Stop tunnel first
                    webServer.stop();
                    gameServer.stop();
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
        System.out.println();
        System.out.println("☁️ Cloudflare Tunnel: " + (cloudflareTunnel.isRunning() ? "✓ Running" : "✗ Stopped"));
        if (cloudflareTunnel.isRunning() && cloudflareTunnel.getPublicUrl() != null) {
            System.out.println("   Public URL: " + cloudflareTunnel.getPublicUrl());
        }
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
        System.out.println("📍 Local URLs:");
        System.out.println("   Home:        " + webServer.getUrl() + "/");
        System.out.println("   Leaderboard: " + webServer.getUrl() + "/leaderboard");
        System.out.println("   Statistics:  " + webServer.getUrl() + "/statistics");
        System.out.println();
        System.out.println("📡 API Endpoints:");
        System.out.println("   Stats JSON:  " + webServer.getUrl() + "/api/stats");
        System.out.println("   Top Players: " + webServer.getUrl() + "/api/leaderboard");

        // ✅ Show public URL if tunnel is running
        if (cloudflareTunnel.isRunning() && cloudflareTunnel.getPublicUrl() != null) {
            System.out.println();
            System.out.println("🌍 Public URLs (via Cloudflare):");
            String publicUrl = cloudflareTunnel.getPublicUrl();
            System.out.println("   Home:        " + publicUrl + "/");
            System.out.println("   Leaderboard: " + publicUrl + "/leaderboard");
            System.out.println("   Statistics:  " + publicUrl + "/statistics");
        }
        System.out.println("========================================\n");
    }

    // ✅ NEW METHOD
    private static void startTunnel() {
        System.out.println("\n========================================");
        System.out.println("☁️ STARTING CLOUDFLARE TUNNEL");
        System.out.println("========================================");

        if (cloudflareTunnel.isRunning()) {
            System.out.println("⚠️ Tunnel already running!");
            System.out.println("   Public URL: " + cloudflareTunnel.getPublicUrl());
            System.out.println("========================================\n");
            return;
        }

        if (!webServer.isRunning()) {
            System.out.println("❌ Web server is not running!");
            System.out.println("   Please start web server first.");
            System.out.println("========================================\n");
            return;
        }

        boolean success = cloudflareTunnel.start(8080);

        if (success && cloudflareTunnel.getPublicUrl() != null) {
            System.out.println();
            System.out.println("✅ Tunnel established successfully!");
            System.out.println("🌍 Share this URL with anyone:");
            System.out.println("   " + cloudflareTunnel.getPublicUrl());
            System.out.println();
            System.out.println("🔒 Features:");
            System.out.println("   ✓ HTTPS encryption by Cloudflare");
            System.out.println("   ✓ No port forwarding needed");
            System.out.println("   ✓ Accessible from anywhere");
            System.out.println("   ✓ DDoS protection included");
        }
        System.out.println("========================================\n");
    }

    // ✅ NEW METHOD
    private static void showPublicUrl() {
        System.out.println("\n========================================");
        System.out.println("🌍 PUBLIC URL");
        System.out.println("========================================");

        if (!cloudflareTunnel.isRunning()) {
            System.out.println("❌ Cloudflare Tunnel is not running!");
            System.out.println("   Type 'tunnel' to start it.");
        } else if (cloudflareTunnel.getPublicUrl() == null) {
            System.out.println("⏳ Tunnel is starting...");
            System.out.println("   Please wait for URL generation.");
        } else {
            String publicUrl = cloudflareTunnel.getPublicUrl();
            System.out.println("✅ Your server is publicly accessible at:");
            System.out.println();
            System.out.println("   " + publicUrl);
            System.out.println();
            System.out.println("📱 Pages:");
            System.out.println("   🏠 Home:        " + publicUrl + "/");
            System.out.println("   🏆 Leaderboard: " + publicUrl + "/leaderboard");
            System.out.println("   📊 Statistics:  " + publicUrl + "/statistics");
            System.out.println();
            System.out.println("💡 Tip: Share this URL with your friends!");
        }
        System.out.println("========================================\n");
    }

    private static void showHelp() {
        System.out.println("\n========================================");
        System.out.println("📝 AVAILABLE COMMANDS");
        System.out.println("========================================");
        System.out.println("status  - Show status of all servers");
        System.out.println("clients - Show connected game clients");
        System.out.println("web     - Show web server URLs (local + public)");
        System.out.println("tunnel  - Start Cloudflare Tunnel for HTTPS");  // ✅ UPDATED
        System.out.println("public  - Show public URL (if tunnel running)"); // ✅ NEW
        System.out.println("stop    - Stop all servers gracefully");
        System.out.println("help    - Show this help message");

        System.out.println("========================================\n");
    }
    //winget install --id Cloudflare.cloudflared
    //cloudflared tunnel --protocol http2 --url http://localhost:8080
}