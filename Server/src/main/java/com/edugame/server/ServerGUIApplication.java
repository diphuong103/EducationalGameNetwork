package com.edugame.server;

import com.edugame.server.network.GameServer;
import com.edugame.server.controller.ServerViewController;
import com.edugame.server.web.CloudflareTunnel;
import com.edugame.server.web.WebServer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Objects;

/**
 * JavaFX Application entry point for Server Management GUI
 */
public class ServerGUIApplication extends Application {

    private GameServer gameServer;
    private WebServer webServer;
    private CloudflareTunnel cloudflareTunnel;
    private ServerViewController controller;
    private Stage primaryStage;

    @Override
    public void init() throws Exception {
        super.init();
        System.out.println("========================================");
        System.out.println("🎮 MATH ADVENTURE SERVER - GUI MODE");
        System.out.println("========================================\n");

        // Initialize supporting servers
        initializeServers();
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        this.primaryStage = primaryStage;

        try {
            System.out.println("📂 Loading FXML...");

            FXMLLoader loader = new FXMLLoader();
            loader.setLocation(getClass().getResource("/fxml/ServerView.fxml"));

            if (loader.getLocation() == null) {
                throw new RuntimeException("❌ FXML file not found: /fxml/ServerView.fxml");
            }

            Parent root = loader.load();
            System.out.println("✅ FXML loaded successfully");

            // Get controller
            controller = loader.getController();
            if (controller == null) {
                throw new RuntimeException("❌ Controller not initialized");
            }
            System.out.println("✅ Controller initialized");

            // ✅ CRITICAL FIX: Create GameServer instance if not exists
            if (gameServer == null) {
                gameServer = new GameServer(8888);
                System.out.println("✅ GameServer instance created");
            }

            // ✅ Pass GameServer reference to controller
            controller.setGameServer(gameServer);
            System.out.println("✅ GameServer reference set in controller");

            // Load CSS (optional, with error handling)
            try {
                String css = getClass().getResource("/css/server-style.css").toExternalForm();
                root.getStylesheets().add(css);
                System.out.println("✅ CSS loaded");
            } catch (Exception e) {
                System.out.println("⚠️ CSS not found, using default styles");
            }

            // Setup scene
            Scene scene = new Scene(root, 1400, 800);

            // Set window properties
            primaryStage.setTitle("Math Adventure Server - Management Console");
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(1200);
            primaryStage.setMinHeight(850);

            // Set icon (optional)
            try {
                Image icon = new Image(
                        Objects.requireNonNull(
                                getClass().getResourceAsStream("/images/avatars/server-icon.png")
                        )
                );
                primaryStage.getIcons().add(icon);
                System.out.println("✅ Icon loaded");
            } catch (Exception e) {
                System.out.println("⚠️ Server icon not found, using default");
            }

            // Cleanup on close
            primaryStage.setOnCloseRequest(event -> {
                System.out.println("\n🛑 Shutting down server...");
                cleanup();
            });

            primaryStage.show();

            System.out.println("✅ GUI Console ready!");
            System.out.println("💡 Use the interface to manage your server\n");

        } catch (Exception e) {
            System.err.println("❌❌❌ CRITICAL ERROR in start() ❌❌❌");
            System.err.println("Exception: " + e.getClass().getName());
            System.err.println("Message: " + e.getMessage());

            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            System.err.println("Stack Trace:\n" + sw.toString());

            cleanup();

            Platform.runLater(() -> {
                try {
                    javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                            javafx.scene.control.Alert.AlertType.ERROR
                    );
                    alert.setTitle("Server Startup Error");
                    alert.setHeaderText("Failed to start server GUI");
                    alert.setContentText(e.getMessage());
                    alert.showAndWait();
                } catch (Exception dialogError) {
                    // Can't show dialog either
                }
                Platform.exit();
            });

            throw e;
        }
    }

    /**
     * Initialize Web Server and Cloudflare Tunnel
     */
    private void initializeServers() {
        try {
            System.out.println("🌐 Initializing Web Server...");
            webServer = new WebServer();
            webServer.start();
            System.out.println("✅ Web Server started: " + webServer.getUrl());
        } catch (Exception e) {
            System.err.println("❌ Failed to start web server: " + e.getMessage());
            e.printStackTrace();
        }

        try {
            System.out.println("☁️ Initializing Cloudflare Tunnel...");
            cloudflareTunnel = new CloudflareTunnel();
            System.out.println("✅ Cloudflare Tunnel ready (use GUI to start)");
        } catch (Exception e) {
            System.err.println("⚠️ Cloudflare Tunnel not available: " + e.getMessage());
        }

        System.out.println("\n🎮 Game Server: Use GUI to start\n");
    }

    /**
     * Cleanup all resources
     */
    private void cleanup() {
        System.out.println("🧹 Starting cleanup...");

        try {
            if (controller != null) {
                System.out.println("   Shutting down controller...");
                controller.shutdown();
            }
        } catch (Exception e) {
            System.err.println("⚠️ Error shutting down controller: " + e.getMessage());
        }

        try {
            if (cloudflareTunnel != null && cloudflareTunnel.isRunning()) {
                System.out.println("   Stopping Cloudflare Tunnel...");
                cloudflareTunnel.stop();
            }
        } catch (Exception e) {
            System.err.println("⚠️ Error stopping tunnel: " + e.getMessage());
        }

        try {
            if (webServer != null && webServer.isRunning()) {
                System.out.println("   Stopping Web Server...");
                webServer.stop();
            }
        } catch (Exception e) {
            System.err.println("⚠️ Error stopping web server: " + e.getMessage());
        }

        try {
            if (gameServer != null && gameServer.isRunning()) {
                System.out.println("   Stopping Game Server...");
                gameServer.stop();
            }
        } catch (Exception e) {
            System.err.println("⚠️ Error stopping game server: " + e.getMessage());
        }

        System.out.println("✅ Cleanup complete");
    }

    @Override
    public void stop() throws Exception {
        cleanup();
        super.stop();
        System.exit(0);
    }

    /**
     * Main entry point
     */
    public static void main(String[] args) {
        try {
            System.out.println("🚀 Starting JavaFX Application...");
            launch(args);
        } catch (Exception e) {
            System.err.println("❌❌❌ FATAL ERROR ❌❌❌");
            System.err.println("Failed to launch JavaFX application");
            System.err.println("Exception: " + e.getClass().getName());
            System.err.println("Message: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}