package com.edugame.client.util;

import com.edugame.client.controller.ChatController;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility class to open Chat Window as a popup
 * Supports both general chat view and direct message mode
 */
public class ChatPopupHandler {

    private static Map<Integer, Stage> openChatWindows = new HashMap<>();
    private static Stage generalChatStage = null;

    /**
     * Open general chat window (shows friends list)
     * @param ownerStage Owner stage
     */
    public static void openChatWindow(Stage ownerStage) {
        openChatWindow(ownerStage, null, null, null, false);
    }

    /**
     * Open chat window with specific friend selected
     * @param ownerStage Owner stage
     * @param friendId Friend's user ID (null for general chat)
     * @param friendName Friend's username
     * @param avatarUrl Friend's avatar URL
     * @param isOnline Friend's online status
     */
    public static void openChatWindow(Stage ownerStage, Integer friendId, String friendName,
                                      String avatarUrl, boolean isOnline) {
        try {
            // If opening specific friend chat
            if (friendId != null && friendId > 0) {
                // Check if chat window for this friend already exists
                if (openChatWindows.containsKey(friendId)) {
                    Stage existingStage = openChatWindows.get(friendId);
                    if (existingStage.isShowing()) {
                        existingStage.toFront();
                        existingStage.requestFocus();
                        return;
                    } else {
                        openChatWindows.remove(friendId);
                    }
                }
            } else {
                // General chat window - only one instance
                if (generalChatStage != null && generalChatStage.isShowing()) {
                    generalChatStage.toFront();
                    generalChatStage.requestFocus();
                    return;
                }
            }

            // Load FXML
            FXMLLoader loader = new FXMLLoader(
                    ChatPopupHandler.class.getResource("/com/edugame/client/view/Chat.fxml")
            );

            // Fallback paths if first one fails
            if (loader.getLocation() == null) {
                loader = new FXMLLoader(
                        ChatPopupHandler.class.getResource("/fxml/ChatWindow.fxml")
                );
            }
            if (loader.getLocation() == null) {
                loader = new FXMLLoader(
                        ChatPopupHandler.class.getResource("/view/Chat.fxml")
                );
            }

            Parent root = loader.load();

            // Get controller
            ChatController controller = loader.getController();

            // Create new stage
            Stage chatStage = new Stage();
            chatStage.initModality(Modality.NONE); // Non-blocking popup
            chatStage.initOwner(ownerStage);
            chatStage.initStyle(StageStyle.DECORATED); // Has title bar and controls

            // Create scene
            Scene scene = new Scene(root);

            // Load CSS if available
            try {
                String css = ChatPopupHandler.class.getResource("/com/edugame/client/styles/chat.css").toExternalForm();
                scene.getStylesheets().add(css);
            } catch (Exception e) {
                try {
                    String css = ChatPopupHandler.class.getResource("/css/chat.css").toExternalForm();
                    scene.getStylesheets().add(css);
                } catch (Exception e2) {
                    System.out.println("⚠️ Chat CSS not found, using default styling");
                }
            }

            chatStage.setScene(scene);

            // Configure based on mode
            if (friendId != null && friendId > 0) {
                // Direct message mode
                chatStage.setTitle("💬 Chat với " + friendName + " - EduGame");
                chatStage.setWidth(800);
                chatStage.setHeight(600);

                // Initialize with specific friend
                if (controller != null) {
                    controller.initData(friendId, friendName, avatarUrl, isOnline);
                }

                // Store reference
                openChatWindows.put(friendId, chatStage);

                // Handle close event
                chatStage.setOnCloseRequest(event -> {
                    System.out.println("🔒 [CHAT] Direct chat window closed for: " + friendName);
                    openChatWindows.remove(friendId);
                    if (controller != null) {
                        controller.cleanup();
                    }
                });

                System.out.println("✅ [CHAT] Direct chat opened with: " + friendName + " (ID=" + friendId + ")");

            } else {
                // General chat mode
                chatStage.setTitle("💬 Tin Nhắn - EduGame");
                chatStage.setWidth(900);
                chatStage.setHeight(600);

                // Store reference
                generalChatStage = chatStage;

                // Handle close event
                chatStage.setOnCloseRequest(event -> {
                    System.out.println("🔒 [CHAT] General chat window closed");
                    generalChatStage = null;
                    if (controller != null) {
                        controller.cleanup();
                    }
                });

                System.out.println("✅ [CHAT] General chat window opened");
            }

            // Set minimum size
            chatStage.setMinWidth(700);
            chatStage.setMinHeight(500);

            // Center on screen
            chatStage.centerOnScreen();

            // Show the window
            chatStage.show();

        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("❌ [CHAT] Failed to open chat window: " + e.getMessage());
        }
    }

    /**
     * Close all chat windows
     */
    public static void closeAllChatWindows() {
        // Close general chat
        if (generalChatStage != null && generalChatStage.isShowing()) {
            generalChatStage.close();
        }
        generalChatStage = null;

        // Close all direct message windows
        for (Stage stage : openChatWindows.values()) {
            if (stage.isShowing()) {
                stage.close();
            }
        }
        openChatWindows.clear();

        System.out.println("🔒 [CHAT] All chat windows closed");
    }

    /**
     * Close chat window for specific friend
     * @param friendId Friend's user ID
     */
    public static void closeChatWindow(int friendId) {
        Stage stage = openChatWindows.get(friendId);
        if (stage != null && stage.isShowing()) {
            stage.close();
        }
        openChatWindows.remove(friendId);
        System.out.println("🔒 [CHAT] Closed chat window for friendId=" + friendId);
    }

    /**
     * Close general chat window
     */
    public static void closeChatWindow() {
        if (generalChatStage != null && generalChatStage.isShowing()) {
            generalChatStage.close();
            generalChatStage = null;
            System.out.println("🔒 [CHAT] General chat window closed programmatically");
        }
    }

    /**
     * Check if general chat window is open
     */
    public static boolean isChatWindowOpen() {
        return generalChatStage != null && generalChatStage.isShowing();
    }

    /**
     * Check if chat window for specific friend is open
     */
    public static boolean isChatWindowOpen(int friendId) {
        Stage stage = openChatWindows.get(friendId);
        return stage != null && stage.isShowing();
    }

    /**
     * Get general chat stage instance
     */
    public static Stage getChatStage() {
        return generalChatStage;
    }

    /**
     * Get chat stage for specific friend
     */
    public static Stage getChatStage(int friendId) {
        return openChatWindows.get(friendId);
    }

    /**
     * Get count of open chat windows
     */
    public static int getOpenChatWindowsCount() {
        int count = generalChatStage != null && generalChatStage.isShowing() ? 1 : 0;
        count += openChatWindows.values().stream()
                .filter(Stage::isShowing)
                .count();
        return count;
    }
}