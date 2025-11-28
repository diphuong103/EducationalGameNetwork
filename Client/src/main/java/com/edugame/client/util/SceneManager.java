package com.edugame.client.util;

import com.edugame.client.controller.HomeController;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.animation.FadeTransition;
import javafx.util.Duration;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * ✅ FIXED: SceneManager with controller callback support
 */
public class SceneManager {
    private static SceneManager instance;
    private Stage primaryStage;
    private Map<String, Scene> sceneCache;
    private Object currentController;

    // 🎨 CSS mapping for each scene
    private static final Map<String, String> SCENE_CSS_MAP = new HashMap<>();
    static {
        SCENE_CSS_MAP.put("Login.fxml", "Login.css");
        SCENE_CSS_MAP.put("Register.fxml", "Register.css");
        SCENE_CSS_MAP.put("Home.fxml", "Home.css");
        SCENE_CSS_MAP.put("Lobby.fxml", "Lobby.css");
        SCENE_CSS_MAP.put("Room.fxml", "Room.css");
        SCENE_CSS_MAP.put("Game.fxml", "Game.css");
        SCENE_CSS_MAP.put("MathGame.fxml", "Game.css");
        SCENE_CSS_MAP.put("EnglishGame.fxml", "Game.css");
        SCENE_CSS_MAP.put("LiteratureGame.fxml", "Game.css");
        SCENE_CSS_MAP.put("FindMatch.fxml", "FindMatch.css");
        SCENE_CSS_MAP.put("Result.fxml", "Result.css");
    }

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
     * ✅ ORIGINAL: Switch to a new scene with fade transition
     */
    public void switchScene(String fxmlFile) throws IOException {
        switchScene(fxmlFile, null);
    }

    /**
     * ✅ NEW: Switch scene with controller initialization callback
     * @param fxmlFile FXML file name
     * @param controllerInitializer Callback to initialize controller after loading
     */
    public void switchScene(String fxmlFile, Consumer<Object> controllerInitializer) throws IOException {
        if (primaryStage == null) {
            throw new IllegalStateException("Primary stage not set!");
        }

        // ✅ Load scene (không dùng cache cho game scenes)
        SceneData sceneData = loadScene(fxmlFile);
        Scene scene = sceneData.scene;
        Object controller = sceneData.controller;

        // ✅ Initialize controller if callback provided
        if (controllerInitializer != null && controller != null) {
            System.out.println("🎮 [SceneManager] Initializing controller: " +
                    controller.getClass().getSimpleName());
            try {
                controllerInitializer.accept(controller);
            } catch (Exception e) {
                System.err.println("❌ [SceneManager] Controller init error: " + e.getMessage());
                e.printStackTrace();
            }
        }

        // ✅ Apply fade transition
        if (primaryStage.getScene() != null) {
            // Fade out current scene
            FadeTransition fadeOut = new FadeTransition(Duration.millis(200),
                    primaryStage.getScene().getRoot());
            fadeOut.setFromValue(1.0);
            fadeOut.setToValue(0.0);
            fadeOut.setOnFinished(event -> {
                primaryStage.setScene(scene);
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
            notifySceneShown();
        }

        System.out.println("✅ [SceneManager] Scene switched to: " + fxmlFile);
    }

    /**
     * 🔹 Notify controller when scene is shown (for auto-refresh)
     */
    private void notifySceneShown() {
        if (currentController == null) {
            return;
        }

        try {
            Platform.runLater(() -> {
                if (currentController instanceof HomeController home) {
                    home.onSceneShown();
                    System.out.println("🔄 HomeController auto-refreshed");
                }
            });
        } catch (Exception e) {
            System.err.println("❌ Error in notifySceneShown(): " + e.getMessage());
        }
    }

    /**
     * ✅ IMPROVED: Load scene with proper cache handling
     * Game scenes are never cached to ensure fresh state
     */
    private SceneData loadScene(String fxmlFile) throws IOException {
        // 🔹 Don't cache these scenes (need fresh state)
        boolean shouldCache = !fxmlFile.matches("(?i)(home|.*game|findmatch)\\.fxml");

        if (!shouldCache) {
            sceneCache.remove(fxmlFile);
        }

        // Check cache first
        if (sceneCache.containsKey(fxmlFile)) {
            System.out.println("📦 Using cached scene: " + fxmlFile);
            Scene cachedScene = sceneCache.get(fxmlFile);
            return new SceneData(cachedScene, currentController);
        }

        System.out.println("🔨 Loading new scene: " + fxmlFile);

        // ✅ Find FXML file with multiple path attempts
        URL fxmlUrl = findFxmlUrl(fxmlFile);

        if (fxmlUrl == null) {
            throw new IOException("FXML file not found: " + fxmlFile);
        }

        System.out.println("✅ Found FXML at: " + fxmlUrl);

        // Load FXML
        FXMLLoader loader = new FXMLLoader(fxmlUrl);
        Parent root = loader.load();

        currentController = loader.getController();

        // Create scene
        Scene scene = new Scene(root);

        // 🎨 Add CSS files
        loadCssForScene(scene, fxmlFile);

        // Cache scene if appropriate
        if (shouldCache) {
            sceneCache.put(fxmlFile, scene);
            System.out.println("💾 Scene cached: " + fxmlFile);
        } else {
            System.out.println("🔄 Scene not cached: " + fxmlFile);
        }

        return new SceneData(scene, currentController);
    }

    /**
     * ✅ Find FXML URL with multiple path attempts
     */
    private URL findFxmlUrl(String fxmlFile) {
        // Try multiple paths
        String[] paths = {
                "/fxml/" + fxmlFile,
                "/com/edugame/client/view/" + fxmlFile,
                "/" + fxmlFile
        };

        for (String path : paths) {
            URL url = getClass().getResource(path);
            if (url != null) {
                System.out.println("   ✅ Found at: " + path);
                return url;
            }
        }

        System.err.println("❌ FXML file not found: " + fxmlFile);
        System.err.println("   Tried paths:");
        for (String path : paths) {
            System.err.println("   - " + path);
        }

        return null;
    }

    /**
     * 🎨 Load CSS files for a scene
     * Loads both common CSS and scene-specific CSS
     */
    private void loadCssForScene(Scene scene, String fxmlFile) {
        System.out.println("🎨 Loading CSS for: " + fxmlFile);

        // 1️⃣ Load common CSS (if exists)
        loadCssFile(scene, "/css/client-style.css", "Common CSS");

        // 2️⃣ Load scene-specific CSS
        String specificCss = SCENE_CSS_MAP.get(fxmlFile);
        if (specificCss != null) {
            loadCssFile(scene, "/css/" + specificCss, "Scene CSS (" + specificCss + ")");
        } else {
            System.out.println("   ℹ️ No specific CSS defined for: " + fxmlFile);
        }
    }

    /**
     * ✅ Load a single CSS file safely
     */
    private void loadCssFile(Scene scene, String cssPath, String description) {
        try {
            URL cssUrl = getClass().getResource(cssPath);

            // Try without /css/ prefix if not found
            if (cssUrl == null && cssPath.startsWith("/css/")) {
                cssUrl = getClass().getResource(cssPath.substring(4));
            }

            if (cssUrl != null) {
                String cssString = cssUrl.toExternalForm();
                if (!scene.getStylesheets().contains(cssString)) {
                    scene.getStylesheets().add(cssString);
                    System.out.println("   ✅ " + description + " loaded");
                }
            } else {
                System.out.println("   ℹ️ " + description + " not found (optional)");
            }
        } catch (Exception e) {
            System.err.println("   ⚠️ Failed to load " + description + ": " + e.getMessage());
        }
    }

    /**
     * 🆕 Switch scene with custom controller data
     * Useful for passing data to controllers
     */
    public <T> T switchSceneWithController(String fxmlFile) throws IOException {
        switchScene(fxmlFile, null);

        @SuppressWarnings("unchecked")
        T controller = (T) currentController;
        return controller;
    }

    /**
     * 🆕 Switch scene using pre-loaded Parent (manual load)
     */
    public void switchScene(Parent root) {
        if (primaryStage == null) {
            throw new IllegalStateException("PrimaryStage not initialized!");
        }

        Scene newScene = new Scene(root);

        if (primaryStage.getScene() != null) {
            FadeTransition fadeOut = new FadeTransition(Duration.millis(200),
                    primaryStage.getScene().getRoot());
            fadeOut.setFromValue(1.0);
            fadeOut.setToValue(0.0);
            fadeOut.setOnFinished(event -> {
                primaryStage.setScene(newScene);

                FadeTransition fadeIn = new FadeTransition(Duration.millis(200), root);
                fadeIn.setFromValue(0.0);
                fadeIn.setToValue(1.0);
                fadeIn.play();
            });
            fadeOut.play();
        } else {
            primaryStage.setScene(newScene);
            primaryStage.show();
        }
    }

    /**
     * Clear scene cache
     */
    public void clearCache() {
        sceneCache.clear();
        System.out.println("🗑️ Scene cache cleared");
    }

    /**
     * Reload a scene (removes from cache and reloads)
     */
    public void reloadScene(String fxmlFile) throws IOException {
        sceneCache.remove(fxmlFile);
        System.out.println("🔄 Reloading scene: " + fxmlFile);
        switchScene(fxmlFile);
    }

    public Stage getPrimaryStage() {
        return primaryStage;
    }

    /**
     * ✅ Helper class to hold scene and controller together
     */
    private static class SceneData {
        final Scene scene;
        final Object controller;

        SceneData(Scene scene, Object controller) {
            this.scene = scene;
            this.controller = controller;
        }
    }
}