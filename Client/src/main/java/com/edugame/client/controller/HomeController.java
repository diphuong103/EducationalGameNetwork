package com.edugame.client.controller;

import com.edugame.client.network.ServerConnection;
import com.edugame.client.util.SceneManager;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.util.Duration;
import javafx.geometry.Pos;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HomeController {

    // User info
    @FXML private ImageView userAvatar;
    @FXML private Text userNameText;
    @FXML private Text levelText;
    @FXML private Text pointsText;
    @FXML private Text coinsText;

    // Buttons
    @FXML private Button settingsButton;
    @FXML private Button logoutButton;
    @FXML private Button trainingButton;
    @FXML private Button quickMatchButton;
    @FXML private Button roomButton;
    @FXML private Button bossButton;

    // Leaderboard
    @FXML private VBox leaderboardList;

    // Chat
    @FXML private ScrollPane chatScrollPane;
    @FXML private VBox chatMessagesContainer;
    @FXML private TextField chatInputField;
    @FXML private Button sendChatButton;
    @FXML private VBox globalChatBox;
    @FXML private Button toggleChatButton;

    private ServerConnection serverConnection;
    private boolean chatExpanded = true;

    @FXML
    public void initialize() {
        System.out.println("HomeController initializing...");
        serverConnection = ServerConnection.getInstance();
        System.out.println("Server connection retrieved");

        setupButtonEffects();
        setupChatScroll();
        startChatListener();
        loadDataInBackground();

    }

    private void loadDataInBackground() {
        new Thread(() -> {
            try {
                // Load user data
                Platform.runLater(() -> loadUserData());
                System.out.println("User data loaded");

                // Load leaderboard với delay nhỏ
                Thread.sleep(100);
                Platform.runLater(() -> loadLeaderboardData());
                System.out.println("Leaderboard loaded");

                // Load daily quests
                Thread.sleep(100);
                Platform.runLater(() -> loadDailyQuests());

            } catch (Exception e) {
                System.err.println("Error loading data: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
    }

    private void setupChatScroll() {
        if (chatMessagesContainer != null && chatScrollPane != null) {
            chatMessagesContainer.heightProperty().addListener((obs, oldVal, newVal) -> {
                chatScrollPane.setVvalue(1.0);
            });
        }
    }

    /** ---------------- USER DATA ---------------- **/
    private void loadUserData() {
        if (serverConnection != null && serverConnection.isConnected()) {
            String fullName = serverConnection.getCurrentFullName();
            int level = serverConnection.getCurrentLevel();
            int totalScore = serverConnection.getTotalScore();

            String avatarFileName = serverConnection.getCurrentAvatarUrl();

            userNameText.setText(fullName != null ? fullName : "Người chơi");
            levelText.setText("Level " + level);
            pointsText.setText("Điểm " + formatNumber(totalScore));

            loadAvatar(avatarFileName);
        } else {
            userNameText.setText("Người chơi");
            levelText.setText("Level 1");
            pointsText.setText("Điểm 0");
            loadAvatar("avatar4.png");
        }
    }

    /**
     * Load avatar image from resources
     * @param avatarFileName - Tên file avatar từ database (vd: "avatar1.png")
     */
    private void loadAvatar(String avatarFileName) {
        if (userAvatar == null) return;

        try {
            // Đường dẫn đến thư mục avatars trong resources
            String avatarPath = "/images/avatars/" + (avatarFileName != null ? avatarFileName : "avatar4.png");

            // Load image từ resources
            Image avatarImage = new Image(getClass().getResourceAsStream(avatarPath));

            if (avatarImage.isError()) {
                System.err.println("Failed to load avatar: " + avatarPath);
                // Load default avatar nếu không tìm thấy
                avatarImage = new Image(getClass().getResourceAsStream("/images/avatars/avatar4.png"));
            }

            userAvatar.setImage(avatarImage);

            // Optional: Set avatar properties for circular display
            userAvatar.setPreserveRatio(true);
            userAvatar.setSmooth(true);

            System.out.println("✓ Avatar loaded: " + avatarFileName);

        } catch (Exception e) {
            System.err.println("✗ Error loading avatar: " + e.getMessage());
            e.printStackTrace();

            // Fallback to default avatar
            try {
                Image defaultAvatar = new Image(getClass().getResourceAsStream("/images/avatars/avatar4.png"));
                userAvatar.setImage(defaultAvatar);
            } catch (Exception ex) {
                System.err.println("✗ Failed to load default avatar");
            }
        }
    }


    /** ---------------- LEADERBOARD ---------------- **/
    private void loadLeaderboardData() {
        if (serverConnection != null && serverConnection.isConnected()) {
            // Chạy trong background thread
            new Thread(() -> {
                try {
                    List<Map<String, Object>> leaderboard = serverConnection.getLeaderboard(10);
                    if (leaderboard != null && !leaderboard.isEmpty()) {
                        System.out.println("✓ Leaderboard data received: " + leaderboard.size() + " users");

                        // Update UI trên JavaFX thread
                        Platform.runLater(() -> {
                            // Update leaderboard UI here
                            for (int i = 0; i < Math.min(3, leaderboard.size()); i++) {
                                Map<String, Object> user = leaderboard.get(i);
                                System.out.println("   " + (i + 1) + ". " + user.get("fullName") + " - " + user.get("totalScore"));
                            }
                        });
                    }
                } catch (Exception e) {
                    System.err.println("✗ Error loading leaderboard: " + e.getMessage());
                    Platform.runLater(() -> {
                        // Show error on UI if needed
                    });
                }
            }).start();
        } else {
            System.out.println("⚠ Using default leaderboard data");
        }
    }

    /** ---------------- DAILY QUEST ---------------- **/
    private void loadDailyQuests() {
        // Placeholder
        System.out.println("Daily quests loaded");
    }

    /** ---------------- CHAT ---------------- **/
    private Thread chatListenerThread;
    private volatile boolean isChatListening = false;

    @FXML
    private void handleToggleChat() {
        chatExpanded = !chatExpanded;

        chatScrollPane.setVisible(chatExpanded);
        chatScrollPane.setManaged(chatExpanded);
        chatInputField.setVisible(chatExpanded);
        chatInputField.setManaged(chatExpanded);
        sendChatButton.setVisible(chatExpanded);
        sendChatButton.setManaged(chatExpanded);

        toggleChatButton.setText(chatExpanded ? "−" : "+");
        globalChatBox.setPrefHeight(chatExpanded ? 350 : 50);

        // Bắt đầu lắng nghe chat khi mở chat lần đầu
        if (chatExpanded && !isChatListening) {
            startChatListener();
        }
    }

    @FXML
    private void handleSendMessage() {
        if (chatInputField == null || chatMessagesContainer == null) return;

        String message = chatInputField.getText().trim();
        if (message.isEmpty()) return;

        ServerConnection server = ServerConnection.getInstance();
        if (!server.isConnected()) {
            addSystemMessage("Không thể gửi tin nhắn. Chưa kết nối server.");
            return;
        }

        // Gửi tin nhắn lên server
        Map<String, Object> request = new HashMap<>();
        request.put("type", "GLOBAL_CHAT");
        request.put("username", server.getCurrentUsername());
        request.put("message", message);

        server.sendJson(request);

        // Hiển thị tin nhắn của bản thân
        addChatMessage(server.getCurrentUsername(), message, true);
        chatInputField.clear();
    }

    private void startChatListener() {
        if (isChatListening) return;

        isChatListening = true;
        chatListenerThread = new Thread(() -> {
            ServerConnection server = ServerConnection.getInstance();

            while (isChatListening && server.isConnected()) {
                try {
                    String response = server.receiveMessage();
                    if (response == null) break;

                    JsonObject jsonResponse = new Gson().fromJson(response, JsonObject.class);
                    String type = jsonResponse.get("type").getAsString();

                    if ("GLOBAL_CHAT".equals(type)) {
                        String username = jsonResponse.get("username").getAsString();
                        String message = jsonResponse.get("message").getAsString();

                        // Không hiển thị lại tin nhắn của chính mình
                        if (!username.equals(server.getCurrentUsername())) {
                            addChatMessage(username, message, false);
                        }
                    }
                    else if ("SYSTEM_MESSAGE".equals(type)) {
                        String message = jsonResponse.get("message").getAsString();
                        addSystemMessage(message);
                    }

                } catch (IOException e) {
                    System.err.println("Chat listener error: " + e.getMessage());
                    break;
                } catch (Exception e) {
                    System.err.println("Chat parse error: " + e.getMessage());
                }
            }

            isChatListening = false;
            System.out.println("Chat listener stopped");
        });

        chatListenerThread.setDaemon(true);
        chatListenerThread.start();
    }

    private void stopChatListener() {
        isChatListening = false;
        if (chatListenerThread != null) {
            chatListenerThread.interrupt();
        }
    }

    private void addChatMessage(String username, String message, boolean isSelf) {
        if (chatMessagesContainer == null) return;

        Platform.runLater(() -> {
            VBox messageBox = new VBox(3);
            messageBox.getStyleClass().add("chat-message");
            if (isSelf) messageBox.getStyleClass().add("chat-message-self");

            HBox usernameBox = new HBox(5);
            usernameBox.setAlignment(Pos.CENTER_LEFT);
            Text usernameText = new Text(username);
            usernameText.getStyleClass().add(isSelf ? "chat-username-self" : "chat-username");
            usernameBox.getChildren().add(usernameText);

            if (!isSelf) {
                Text onlineDot = new Text("●");
                onlineDot.getStyleClass().add("online-dot");
                onlineDot.setStyle("-fx-fill: #4CAF50;");
                usernameBox.getChildren().add(onlineDot);
            }

            Text messageText = new Text(message);
            messageText.getStyleClass().add(isSelf ? "chat-text-self" : "chat-text");
            messageText.setWrappingWidth(240);

            messageBox.getChildren().addAll(usernameBox, messageText);
            chatMessagesContainer.getChildren().add(messageBox);

            // Giới hạn số tin nhắn hiển thị
            if (chatMessagesContainer.getChildren().size() > 50) {
                chatMessagesContainer.getChildren().remove(0);
            }

            // Auto scroll xuống tin nhắn mới nhất
            chatScrollPane.layout();
            chatScrollPane.setVvalue(1.0);
        });
    }

    private void addSystemMessage(String message) {
        Platform.runLater(() -> {
            Text systemText = new Text("⚠ " + message);
            systemText.getStyleClass().add("chat-system-message");
            systemText.setWrappingWidth(240);

            chatMessagesContainer.getChildren().add(systemText);

            if (chatMessagesContainer.getChildren().size() > 50) {
                chatMessagesContainer.getChildren().remove(0);
            }

            chatScrollPane.layout();
            chatScrollPane.setVvalue(1.0);
        });
    }

    // Gọi khi đóng HomeController
    public void cleanup() {
        stopChatListener();
    }

    /** ---------------- BUTTON EFFECTS ---------------- **/
    private void setupButtonEffects() {
        addHoverEffect(trainingButton);
        addHoverEffect(quickMatchButton);
        addHoverEffect(roomButton);
        addHoverEffect(bossButton);
    }

    private void addHoverEffect(Button button) {
        if (button == null) return;
        button.setOnMouseEntered(e -> {
            button.setScaleX(1.05);
            button.setScaleY(1.05);
        });
        button.setOnMouseExited(e -> {
            button.setScaleX(1.0);
            button.setScaleY(1.0);
        });
    }

    /** ---------------- BUTTON HANDLERS ---------------- **/
    @FXML private void handleTrainingMode() { showComingSoon("Chế độ Luyện Tập đang được phát triển!"); }

    @FXML
    private void handleQuickMatch() {
        try {
            SceneManager.getInstance().switchScene("FindMatch.fxml");
        } catch (Exception e) {
            showError("Không thể chuyển sang màn hình tìm trận!");
        }
    }

    @FXML private void handleRoomMode() { showComingSoon("Chế độ Phòng Chơi đang được phát triển!"); }
    @FXML private void handleCreateRoom() { showComingSoon("Tạo phòng đang được phát triển!"); }
    @FXML private void handleNotifications() { showComingSoon("Thông báo đang được phát triển!"); }

    @FXML
    private void handleSettings() {
        try {
            SceneManager.getInstance().switchScene("Settings.fxml");
        } catch (Exception e) {
            showComingSoon("Cài đặt đang được phát triển!");
        }
    }

    @FXML
    private void handleLogout() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Đăng xuất");
        alert.setHeaderText("Bạn có chắc muốn đăng xuất?");
        alert.setContentText("Tiến trình chơi sẽ được lưu tự động.");

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    if (serverConnection != null && serverConnection.isConnected()) {
                        serverConnection.disconnect();
                    }
                    SceneManager.getInstance().switchScene("Login.fxml");
                } catch (Exception e) {
                    e.printStackTrace();
                    showError("Lỗi khi đăng xuất!");
                }
            }
        });
    }

    @FXML
    private void handleViewLeaderboard() {
        try {
            SceneManager.getInstance().switchScene("Leaderboard.fxml");
        } catch (Exception e) {
            showComingSoon("Bảng xếp hạng đang được phát triển!");
        }
    }

    /** ---------------- UTILITIES ---------------- **/
    private void showComingSoon(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Sắp ra mắt");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.show();

        PauseTransition delay = new PauseTransition(Duration.seconds(2));
        delay.setOnFinished(event -> alert.close());
        delay.play();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Lỗi");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private String formatNumber(int number) {
        return String.format("%,d", number);
    }

    /** ---------------- UPDATE DATA ---------------- **/
    public void updatePoints(int newPoints) {
        Platform.runLater(() -> pointsText.setText("Điểm " + formatNumber(newPoints)));
    }

    public void updateLevel(int newLevel) {
        Platform.runLater(() -> levelText.setText("Level " + newLevel));
    }

    public void updateCoins(int newCoins) {
        Platform.runLater(() -> coinsText.setText("Coins " + formatNumber(newCoins)));
    }

    public void refreshUserData() {
        loadUserData();
        loadLeaderboardData();
        loadDailyQuests();
    }
}
