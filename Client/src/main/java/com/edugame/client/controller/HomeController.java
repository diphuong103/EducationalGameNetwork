package com.edugame.client.controller;

import com.edugame.client.network.ServerConnection;
import com.edugame.client.util.SceneManager;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.util.Duration;

public class HomeController {

    @FXML private ImageView userAvatar;
    @FXML private Text userNameText;
    @FXML private Text levelText;
    @FXML private Text pointsText;

    // Buttons có trong FXML
    @FXML private Button settingsButton;
    @FXML private Button logoutButton;

    // Game mode buttons (trỏ qua onAction trong FXML)
    @FXML private VBox leaderboardList;
    @FXML private ScrollPane chatScrollPane;
    @FXML private VBox chatMessagesContainer;
    @FXML private TextField chatInputField;
    @FXML private Button sendButton;

    private ServerConnection serverConnection;

    @FXML
    public void initialize() {
        serverConnection = ServerConnection.getInstance();

        // Load dữ liệu người dùng và nội dung
        loadUserData();
        loadLeaderboardData();
        loadDailyQuests();

        // Setup chat auto-scroll nếu tồn tại
        if (chatMessagesContainer != null && chatScrollPane != null) {
            setupChatScroll();
        }
    }

    private void setupChatScroll() {
        chatMessagesContainer.heightProperty().addListener((obs, oldVal, newVal) -> {
            chatScrollPane.setVvalue(1.0);
        });
    }

    @FXML
    private void handleSendMessage() {
        String message = chatInputField.getText().trim();
        if (message.isEmpty()) return;

        addChatMessage("Bạn", message, true);
        chatInputField.clear();

        // TODO: Gửi tin nhắn lên server
        System.out.println("Chat message sent: " + message);
    }

    private void addChatMessage(String username, String message, boolean isSelf) {
        Platform.runLater(() -> {
            VBox messageBox = new VBox(3);
            messageBox.getStyleClass().add("chat-message");
            if (isSelf) messageBox.getStyleClass().add("chat-self");

            // Username
            HBox usernameBox = new HBox(5);
            usernameBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
            Text usernameText = new Text(username);
            usernameText.getStyleClass().add(isSelf ? "chat-sender-self" : "chat-sender");
            usernameBox.getChildren().add(usernameText);

            if (!isSelf) {
                Text onlineDot = new Text("●");
                onlineDot.getStyleClass().add("chat-online");
                usernameBox.getChildren().add(onlineDot);
            }

            // Message
            Text messageText = new Text(message);
            messageText.getStyleClass().add(isSelf ? "chat-text-self" : "chat-text");
            messageText.setWrappingWidth(240);

            messageBox.getChildren().addAll(usernameBox, messageText);
            chatMessagesContainer.getChildren().add(messageBox);

            if (chatMessagesContainer.getChildren().size() > 50) {
                chatMessagesContainer.getChildren().remove(0);
            }
        });
    }

    private void loadUserData() {
        // Tạm thời là dữ liệu giả
        userNameText.setText("Nguyễn Văn An");
        levelText.setText("Level 12");
        pointsText.setText("2,450 điểm");
    }

    private void loadLeaderboardData() {
        System.out.println("Leaderboard loaded");
    }

    private void loadDailyQuests() {
        System.out.println("Daily quests loaded");
    }

    // ===================== XỬ LÝ CÁC NÚT =====================

    @FXML
    private void handleTrainingMode() {
        System.out.println("Training Mode selected");
        showComingSoon("Chế độ Luyện Tập đang được phát triển!");
    }

    @FXML
    private void handleQuickMatch() {
        System.out.println("Quick Match selected");
        try {
            SceneManager.getInstance().switchScene("FindMatch.fxml");
        } catch (Exception e) {
            e.printStackTrace();
            showError("Không thể chuyển sang màn hình tìm trận!");
        }
    }

    @FXML
    private void handleCreateRoom(ActionEvent event) {
        System.out.println("Create Room selected");
        showComingSoon("Chế độ Tạo Phòng đang được phát triển!");
    }

    @FXML
    private void handleSettings() {
        System.out.println("Settings clicked");
        try {
            SceneManager.getInstance().switchScene("Settings.fxml");
        } catch (Exception e) {
            showComingSoon("Cài đặt đang được phát triển!");
        }
    }

    @FXML
    private void handleLogout(ActionEvent event) {
        System.out.println("Logout clicked");
        try {
            SceneManager.getInstance().switchScene("Login.fxml");
        } catch (Exception e) {
            showError("Không thể đăng xuất!");
        }
    }

    @FXML
    private void handleViewLeaderboard() {
        System.out.println("View Leaderboard clicked");
        try {
            SceneManager.getInstance().switchScene("Leaderboard.fxml");
        } catch (Exception e) {
            showComingSoon("Bảng xếp hạng đang được phát triển!");
        }
    }

    // ===================== HỘP THÔNG BÁO =====================

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

    // ===================== CẬP NHẬT DỮ LIỆU =====================

    public void refreshUserData() {
        loadUserData();
        loadLeaderboardData();
        loadDailyQuests();
    }

    public void updatePoints(int newPoints) {
        Platform.runLater(() -> {
            pointsText.setText(formatNumber(newPoints) + " điểm");
        });
    }

    public void updateLevel(int newLevel) {
        Platform.runLater(() -> {
            levelText.setText("Level " + newLevel);
        });
    }

    private String formatNumber(int number) {
        return String.format("%,d", number);
    }
}
