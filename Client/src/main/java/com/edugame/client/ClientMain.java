package com.edugame.client;

import com.edugame.client.network.ServerConnection;
import com.edugame.client.util.SceneManager;
import javafx.application.Application;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.util.Objects;

public class ClientMain extends Application {

    @Override
    public void start(Stage primaryStage) {
        try {
            // Set up SceneManager
            SceneManager sceneManager = SceneManager.getInstance();
            sceneManager.setPrimaryStage(primaryStage);

            // Configure primary stage
            primaryStage.setTitle("Math Adventure - Educational Game");
            primaryStage.setWidth(1000);
            primaryStage.setHeight(800);
            primaryStage.setResizable(true);
            primaryStage.setMinWidth(950);
            primaryStage.setMinHeight(650);

            try {
                Image icon = new Image(Objects.requireNonNull(getClass().getResourceAsStream("/images/icons/game-controller.png")));
                primaryStage.getIcons().add(icon);
            } catch (Exception e) {
                System.out.println("⚠️ Server icon not found, using default");
            }

            // Load login screen
            sceneManager.switchScene("Login.fxml");

            // Show stage
            primaryStage.show();

            // Handle window close
            primaryStage.setOnCloseRequest(event -> {
                handleExit();
            });

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error starting application: " + e.getMessage());
        }
    }

    @Override
    public void stop() {
        handleExit();
    }

    private void handleExit() {
        // Disconnect from server
        ServerConnection connection = ServerConnection.getInstance();
        if (connection.isConnected()) {
            connection.disconnect();
        }

        System.out.println("Application closed");
    }

    public static void main(String[] args) {
        launch(args);
    }
}