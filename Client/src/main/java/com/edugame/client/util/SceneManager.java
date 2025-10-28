package com.edugame.client.util;

import com.edugame.client.controller.HomeController;
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
    private static Stage primaryStage;
    private static Map<String, Scene> sceneCache;
    private static Object currentController;

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

    public Object getCurrentController() {
        return currentController;
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

                // 🔹 AUTO REFRESH HOME CONTROLLER
                notifySceneShown();

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

            // 🔹 AUTO REFRESH HOME CONTROLLER
            notifySceneShown();
        }
    }

    /**
     * 🔹 Notify controller when scene is shown (for auto-refresh)
     */
    private void notifySceneShown() {
        if (currentController instanceof HomeController) {
            ((HomeController) currentController).onSceneShown();
            System.out.println("🔄 HomeController auto-refreshed");
        }
    }

    /**
     * Get scene from cache or load it
     */
    private static Scene getScene(String fxmlFile) throws IOException {
        // 🔹 KHÔNG CACHE HOME.FXML để luôn load mới và refresh
        if ("home.fxml".equalsIgnoreCase(fxmlFile)) {
            sceneCache.remove(fxmlFile);
        }

        // Check cache first
        if (sceneCache.containsKey(fxmlFile)) {
            return sceneCache.get(fxmlFile);
        }

        // Load FXML
        FXMLLoader loader = new FXMLLoader(SceneManager.class.getResource("/fxml/" + fxmlFile));
        Parent root = loader.load();

        currentController = loader.getController();

        // Create scene
        Scene scene = new Scene(root);

        // Add CSS
        String css = SceneManager.class.getResource("/css/client-style.css").toExternalForm();
        scene.getStylesheets().add(css);

        // Cache scene (except home.fxml)
        if (!"home.fxml".equalsIgnoreCase(fxmlFile)) {
            sceneCache.put(fxmlFile, scene);
        }

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