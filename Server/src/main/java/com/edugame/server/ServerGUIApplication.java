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

import java.util.Objects;

/**
 * JavaFX Application entry point for Server Management GUI
 * Separate from ServerMain to avoid JavaFX initialization issues in CLI mode
 *
 * Usage:
 *   java -cp server.jar com.edugame.server.ServerGUIApplication
 */
public class ServerGUIApplication extends Application {

    private GameServer gameServer;
    private WebServer webServer;
    private CloudflareTunnel cloudflareTunnel;
    private ServerViewController controller;
    private Stage primaryStage;

    @Override
    public void start(Stage primaryStage) throws Exception {
        this.primaryStage = primaryStage;

        System.out.println("========================================");
        System.out.println("🎮 MATH ADVENTURE SERVER - GUI MODE");
        System.out.println("========================================\n");

        // Initialize supporting servers
        initializeServers();

        // Load FXML
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ServerView.fxml"));
        Parent root = loader.load();

        // Get controller
        controller = loader.getController();

        // Pass existing servers to controller if already running
        if (gameServer != null) {
            controller.setGameServer(gameServer);
        }

        // Setup scene
        Scene scene = new Scene(root, 1400, 800);

        // Set window properties
        primaryStage.setTitle("Math Adventure Server - Management Console");
        primaryStage.setScene(scene);
        primaryStage.setMinWidth(1200);
        primaryStage.setMinHeight(700);

        // Set icon (optional)
        try {
            Image icon = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/images/avatars/server-icon.png")));
            primaryStage.getIcons().add(icon);
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
    }

    /**
     * Initialize Web Server and Cloudflare Tunnel
     * Game Server will be controlled via GUI
     */
    private void initializeServers() {
        // Initialize web server
        webServer = new WebServer();
        try {
            webServer.start();
            System.out.println("✅ Web Server started: " + webServer.getUrl());
        } catch (Exception e) {
            System.err.println("❌ Failed to start web server: " + e.getMessage());
        }

        // Initialize Cloudflare tunnel (not started yet)
        cloudflareTunnel = new CloudflareTunnel();
        System.out.println("💡 Cloudflare Tunnel ready (use 'tunnel' command to start)");

        System.out.println("\n🎮 Game Server: Use GUI to start\n");
    }

    /**
     * Cleanup all resources
     */
    private void cleanup() {
        if (controller != null) {
            controller.shutdown();
        }

        if (cloudflareTunnel != null && cloudflareTunnel.isRunning()) {
            System.out.println("🛑 Stopping Cloudflare Tunnel...");
            cloudflareTunnel.stop();
        }

        if (webServer != null && webServer.isRunning()) {
            System.out.println("🛑 Stopping Web Server...");
            webServer.stop();
        }

        if (gameServer != null && gameServer.isRunning()) {
            System.out.println("🛑 Stopping Game Server...");
            gameServer.stop();
        }

        System.out.println("✅ All servers stopped");

        Platform.exit();
        System.exit(0);
    }

    @Override
    public void stop() throws Exception {
        cleanup();
        super.stop();
    }

    /**
     * Main entry point
     */
    public static void main(String[] args) {
        launch(args);
    }
}