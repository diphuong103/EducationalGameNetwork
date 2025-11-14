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
//    private void notifySceneShown() {
//        if (currentController instanceof HomeController) {
//            ((HomeController) currentController).onSceneShown();
//            System.out.println("🔄 HomeController auto-refreshed");
//        }
//    }

    private void notifySceneShown() {
        if (currentController == null) {
            System.err.println("⚠️ notifySceneShown() called but controller is null");
            return;
        }

        try {
            // Chạy sau 1 tick để đảm bảo scene đã hiển thị xong
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
     * Get scene from cache or load it
     */
    private Scene getScene(String fxmlFile) throws IOException {
        // 🔹 KHÔNG CACHE HOME.FXML để luôn load mới và refresh
        if ("home.fxml".equalsIgnoreCase(fxmlFile) || "Home.fxml".equals(fxmlFile)) {
            sceneCache.remove(fxmlFile);
        }

        // Check cache first
        if (sceneCache.containsKey(fxmlFile)) {
            System.out.println("📦 Using cached scene: " + fxmlFile);
            return sceneCache.get(fxmlFile);
        }

        System.out.println("🔨 Loading new scene: " + fxmlFile);

        // Try to find FXML file
        String fxmlPath = "/fxml/" + fxmlFile;
        URL fxmlUrl = getClass().getResource(fxmlPath);

        // If not found, try root path
        if (fxmlUrl == null) {
            System.out.println("⚠️ Not found at: " + fxmlPath);
            fxmlPath = "/" + fxmlFile;
            fxmlUrl = getClass().getResource(fxmlPath);
        }

        if (fxmlUrl == null) {
            System.err.println("❌ FXML file not found: " + fxmlFile);
            System.err.println("   Tried paths:");
            System.err.println("   - /fxml/" + fxmlFile);
            System.err.println("   - /" + fxmlFile);
            throw new IOException("FXML file not found: " + fxmlFile);
        }

        System.out.println("✅ Found FXML at: " + fxmlPath);

        // Load FXML
        FXMLLoader loader = new FXMLLoader(fxmlUrl);
        Parent root = loader.load();

        currentController = loader.getController();

        // Create scene
        Scene scene = new Scene(root);

        // 🎨 Add CSS files
        loadCssForScene(scene, fxmlFile);

        // Cache scene (except home.fxml)
        if (!"home.fxml".equalsIgnoreCase(fxmlFile) && !"Home.fxml".equals(fxmlFile)) {
            sceneCache.put(fxmlFile, scene);
            System.out.println("💾 Scene cached: " + fxmlFile);
        } else {
            System.out.println("🔄 Scene not cached (Home.fxml)");
        }

        return scene;
    }

    /**
     * 🎨 Load CSS files for a scene
     * Loads both common CSS and scene-specific CSS
     */
    private void loadCssForScene(Scene scene, String fxmlFile) {
        System.out.println("🎨 Loading CSS for: " + fxmlFile);

        // 1️⃣ Load common CSS (if exists)
        try {
            URL commonCssUrl = getClass().getResource("/css/client-style.css");
            if (commonCssUrl != null) {
                scene.getStylesheets().add(commonCssUrl.toExternalForm());
                System.out.println("   ✅ Common CSS loaded: client-style.css");
            }
        } catch (Exception e) {
            System.err.println("   ⚠️ Common CSS not found: " + e.getMessage());
        }

        // 2️⃣ Load scene-specific CSS
        String specificCss = SCENE_CSS_MAP.get(fxmlFile);
        if (specificCss != null) {
            try {
                // Try /css/ folder first
                URL cssUrl = getClass().getResource("/css/" + specificCss);

                // If not found, try root
                if (cssUrl == null) {
                    cssUrl = getClass().getResource("/" + specificCss);
                }

                if (cssUrl != null) {
                    scene.getStylesheets().add(cssUrl.toExternalForm());
                    System.out.println("   ✅ Scene CSS loaded: " + specificCss);
                } else {
                    System.err.println("   ⚠️ Scene CSS not found: " + specificCss);
                    System.err.println("      Tried paths:");
                    System.err.println("      - /css/" + specificCss);
                    System.err.println("      - /" + specificCss);
                }
            } catch (Exception e) {
                System.err.println("   ⚠️ Failed to load scene CSS: " + e.getMessage());
            }
        } else {
            System.out.println("   ℹ️ No specific CSS defined for: " + fxmlFile);
        }
    }

    /**
     * 🆕 Switch scene with custom controller data
     * Useful for passing data to controllers
     */
    public <T> T switchSceneWithController(String fxmlFile) throws IOException {
        Scene scene = getScene(fxmlFile);

        if (primaryStage.getScene() != null) {
            FadeTransition fadeOut = new FadeTransition(Duration.millis(200), primaryStage.getScene().getRoot());
            fadeOut.setFromValue(1.0);
            fadeOut.setToValue(0.0);
            fadeOut.setOnFinished(event -> {
                primaryStage.setScene(scene);
                notifySceneShown();

                FadeTransition fadeIn = new FadeTransition(Duration.millis(200), scene.getRoot());
                fadeIn.setFromValue(0.0);
                fadeIn.setToValue(1.0);
                fadeIn.play();
            });
            fadeOut.play();
        } else {
            primaryStage.setScene(scene);
            notifySceneShown();
        }

        @SuppressWarnings("unchecked")
        T controller = (T) currentController;
        return controller;
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
     * 🆕 Switch scene bằng Parent (đã load thủ công)
     * Dùng khi bạn đã tự load FXML bằng FXMLLoader.
     */
    public void switchScene(Parent root) {
        if (primaryStage == null) {
            throw new IllegalStateException("PrimaryStage chưa được khởi tạo!");
        }

        // Tạo scene mới
        Scene newScene = new Scene(root);

        // Hiệu ứng fade transition
        if (primaryStage.getScene() != null) {
            FadeTransition fadeOut = new FadeTransition(Duration.millis(200), primaryStage.getScene().getRoot());
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

}