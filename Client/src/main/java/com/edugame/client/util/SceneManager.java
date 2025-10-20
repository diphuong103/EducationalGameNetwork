package com.edugame.client.util;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.animation.FadeTransition;
import javafx.util.Duration;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class SceneManager {
    private static SceneManager instance;
    private Stage primaryStage;
    private Map<String, Scene> sceneCache;

    private SceneManager() {
        sceneCache = new HashMap<>();
    }

    public static synchronized SceneManager getInstance() {
        if (instance == null) {
            instance = new SceneManager();
        }
        return instance;
    }

    public void setPrimaryStage(Stage stage) {
        this.primaryStage = stage;
    }

    /**
     * Switch to a new scene with fade transition
     */
    public void switchScene(String fxmlFile) throws IOException {
        Scene scene = getScene(fxmlFile);

        if (primaryStage.getScene() != null) {
            // Fade out current scene
            FadeTransition fadeOut = new FadeTransition(Duration.millis(200), primaryStage.getScene().getRoot());
            fadeOut.setFromValue(1.0);
            fadeOut.setToValue(0.0);
            fadeOut.setOnFinished(event -> {
                primaryStage.setScene(scene);

                // Fade in new scene
                FadeTransition fadeIn = new FadeTransition(Duration.millis(200), scene.getRoot());
                fadeIn.setFromValue(0.0);
                fadeIn.setToValue(1.0);
                fadeIn.play();
            });
            fadeOut.play();
        } else {
            // First scene, no transition
            primaryStage.setScene(scene);
        }
    }

    /**
     * Get scene from cache or load it
     */
    private Scene getScene(String fxmlFile) throws IOException {
        // Check cache first
        if (sceneCache.containsKey(fxmlFile)) {
            return sceneCache.get(fxmlFile);
        }

        // Load FXML
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/" + fxmlFile));
        Parent root = loader.load();

        // Create scene
        Scene scene = new Scene(root);

        // Add CSS
        String css = getClass().getResource("/css/client-style.css").toExternalForm();
        scene.getStylesheets().add(css);

        // Cache scene
        sceneCache.put(fxmlFile, scene);

        return scene;
    }

    /**
     * Clear scene cache
     */
    public void clearCache() {
        sceneCache.clear();
    }

    /**
     * Reload a scene (removes from cache and reloads)
     */
    public void reloadScene(String fxmlFile) throws IOException {
        sceneCache.remove(fxmlFile);
        switchScene(fxmlFile);
    }

    public Stage getPrimaryStage() {
        return primaryStage;
    }
}