package com.edugame.client.controller;

import com.edugame.client.model.User;
import com.edugame.client.network.ServerConnection;
import com.edugame.client.util.SceneManager;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

public class ProfileController {

    @FXML private ImageView avatarImage;
    @FXML private Label nameLabel;
    @FXML private Label ageLabel;
    @FXML private Label scoreLabel;
    @FXML private Label totalGamesLabel;
    @FXML private Label winLabel;
    @FXML private Label winRateLabel;

    private boolean isLoading = false;

    @FXML
    public void initialize() {
        System.out.println("🚀 ProfileController.initialize() called");

        // Show loading state
        setLoadingState();

        // Load profile data
        loadProfileData();
    }

    /**
     * Set UI to loading state
     */
    private void setLoadingState() {
        nameLabel.setText("Đang tải...");
        scoreLabel.setText("--");
        totalGamesLabel.setText("--");
        winLabel.setText("--");
        winRateLabel.setText("--");
        ageLabel.setText("--");
    }

    /**
     * Load profile data from server
     */
    private void loadProfileData() {
        if (isLoading) {
            System.out.println("⚠️ Already loading profile");
            return;
        }

        isLoading = true;
        System.out.println("📝 Loading profile data...");

        ServerConnection server = ServerConnection.getInstance();

        if (!server.isConnected()) {
            System.err.println("❌ Not connected to server");
            Platform.runLater(() -> {
                showError("Không thể kết nối đến server. Vui lòng đăng nhập lại.");
                isLoading = false;
            });
            return;
        }

        // Call getProfile with callback
        server.getProfile(user -> {
            isLoading = false;

            if (user != null) {
                System.out.println("✅ Profile data received");
                Platform.runLater(() -> updateUI(user));
            } else {
                System.err.println("❌ Failed to load profile");
                Platform.runLater(() -> {
                    nameLabel.setText("Không thể tải hồ sơ");
                    showError("Không thể tải thông tin người dùng. Vui lòng thử lại sau.");
                });
            }
        });
    }

    /**
     * Update UI with user data
     */
    private void updateUI(User user) {
        if (user == null) {
            System.err.println("❌ updateUI called with NULL user!");
            return;
        }

        System.out.println("🎨 Updating UI with user data:");
        System.out.println("   - Full Name: " + user.getFullName());
        System.out.println("   - Username: " + user.getUsername());
        System.out.println("   - Total Score: " + user.getTotalScore());
        System.out.println("   - Total Games: " + user.getTotalGames());
        System.out.println("   - Wins: " + user.getWins());
        System.out.println("   - Age: " + user.getAge());

        // Load avatar
        loadAvatar(user.getAvatarUrl());

        // Process name and age
        String fullName = (user.getFullName() != null && !user.getFullName().isEmpty())
                ? user.getFullName()
                : user.getUsername();

        String nameOnly = fullName;
        String ageText = "—";

        // Extract age from name if in format "Name (Age)"
        if (fullName.contains("(") && fullName.contains(")")) {
            int start = fullName.indexOf('(');
            int end = fullName.indexOf(')');
            if (end > start) {
                nameOnly = fullName.substring(0, start).trim();
                String inside = fullName.substring(start + 1, end).trim();

                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+").matcher(inside);
                if (m.find()) {
                    ageText = m.group();
                    System.out.println("   ✅ Extracted age from name: " + ageText);
                }
            }
        } else if (user.getAge() > 0) {
            ageText = String.valueOf(user.getAge());
            System.out.println("   ✅ Using age from User object: " + ageText);
        }

        // Set UI values
        nameLabel.setText(nameOnly);
        ageLabel.setText(ageText);
        scoreLabel.setText(String.valueOf(user.getTotalScore()));
        totalGamesLabel.setText(String.valueOf(user.getTotalGames()));
        winLabel.setText(String.valueOf(user.getWins()));

        // Calculate and set win rate
        int winRate = user.getTotalGames() > 0
                ? (user.getWins() * 100 / user.getTotalGames())
                : 0;
        winRateLabel.setText(winRate + "%");

        System.out.println("✅ UI update completed successfully!");
    }

    /**
     * Load avatar image
     */
    private void loadAvatar(String avatarUrl) {
        try {
            String avatarPath = "/images/avatars/" +
                    (avatarUrl != null ? avatarUrl : "avatar4.png");

            System.out.println("   📸 Loading avatar from: " + avatarPath);

            Image avatar = new Image(getClass().getResourceAsStream(avatarPath));

            if (avatar.isError()) {
                System.err.println("   ❌ Failed to load avatar, using default");
                avatar = new Image(getClass().getResourceAsStream("/images/avatars/avatar4.png"));
            }

            avatarImage.setImage(avatar);
            System.out.println("   ✅ Avatar loaded successfully");

        } catch (Exception e) {
            System.err.println("   ❌ Error loading avatar: " + e.getMessage());
            try {
                avatarImage.setImage(new Image(getClass().getResourceAsStream("/images/avatars/avatar4.png")));
            } catch (Exception ex) {
                System.err.println("   ❌ Failed to load default avatar");
            }
        }
    }

    /** ---------------- EVENT HANDLERS ---------------- */

    @FXML
    public void handleBack(ActionEvent event) {
        try {
            SceneManager.getInstance().switchScene("home.fxml");
        } catch (Exception e) {
            e.printStackTrace();
            showError("Không thể quay về trang chủ");
        }
    }

    @FXML
    public void handleEditName(ActionEvent event) {
        showComingSoon("Chức năng chỉnh sửa tên đang được phát triển!");
    }

    @FXML
    public void handleChangeAvatar(ActionEvent event) {
        showComingSoon("Chức năng thay đổi avatar đang được phát triển!");
    }

    @FXML
    public void handleShowDetails(ActionEvent event) {
        showComingSoon("Chức năng xem chi tiết đang được phát triển!");
    }

    /** ---------------- UTILITIES ---------------- */

    private void showError(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Lỗi");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    private void showComingSoon(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Sắp ra mắt");
        alert.setHeaderText(null);
        alert.setContentText(message);

        javafx.animation.PauseTransition delay =
                new javafx.animation.PauseTransition(javafx.util.Duration.seconds(2));
        delay.setOnFinished(e -> alert.close());

        alert.show();
        delay.play();
    }
}