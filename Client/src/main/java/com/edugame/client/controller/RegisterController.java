package com.edugame.client.controller;

import com.edugame.client.config.ServerConfig;
import com.edugame.client.network.ServerConnection;
import com.edugame.client.util.SceneManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.animation.PauseTransition;
import javafx.util.Duration;

public class RegisterController {

    @FXML private Button backButton;
    @FXML private TextField fullNameField;
    @FXML private TextField usernameField;
    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;

    // Age buttons
    @FXML private ToggleButton age6Button;
    @FXML private ToggleButton age7Button;
    @FXML private ToggleButton age8Button;
    @FXML private ToggleButton age9Button;
    @FXML private ToggleButton age10Button;
    @FXML private ToggleButton age11Button;
    @FXML private ToggleButton age12Button;
    @FXML private ToggleGroup ageGroup;

    // Avatar buttons
    @FXML private ToggleButton avatar1Button;
    @FXML private ToggleButton avatar2Button;
    @FXML private ToggleButton avatar3Button;
    @FXML private ToggleButton avatar4Button;
    @FXML private ToggleButton avatar5Button;
    @FXML private ToggleButton avatar6Button;
    @FXML private ToggleButton avatar7Button;
    @FXML private ToggleButton avatar8Button;
    @FXML private ToggleGroup avatarGroup;

    @FXML private Button registerButton;
    @FXML private StackPane loadingOverlay;

    private ServerConnection serverConnection;
    private String selectedAvatar = "avatar4.png";
    private String selectedAge = "9";

    @FXML
    public void initialize() {
        serverConnection = ServerConnection.getInstance();

        // Set defaults
        if (age9Button != null) {
            age9Button.setSelected(true);
            selectedAge = "9";
        }

        if (avatar4Button != null) {
            avatar4Button.setSelected(true);
        }

        // Enter key handler
        confirmPasswordField.setOnAction(event -> handleRegister());

        // Focus
        Platform.runLater(() -> fullNameField.requestFocus());
    }

    @FXML
    private void handleAgeSelection() {
        Toggle selectedToggle = ageGroup.getSelectedToggle();

        if (selectedToggle == age6Button) selectedAge = "6";
        else if (selectedToggle == age7Button) selectedAge = "7";
        else if (selectedToggle == age8Button) selectedAge = "8";
        else if (selectedToggle == age9Button) selectedAge = "9";
        else if (selectedToggle == age10Button) selectedAge = "10";
        else if (selectedToggle == age11Button) selectedAge = "11";
        else if (selectedToggle == age12Button) selectedAge = "12";

        System.out.println("Selected age: " + selectedAge);
    }

    @FXML
    private void handleAvatarSelection() {
        Toggle selectedToggle = avatarGroup.getSelectedToggle();

        if (selectedToggle == avatar1Button) selectedAvatar = "avatar1.png";
        else if (selectedToggle == avatar2Button) selectedAvatar = "avatar2.png";
        else if (selectedToggle == avatar3Button) selectedAvatar = "avatar3.png";
        else if (selectedToggle == avatar4Button) selectedAvatar = "avatar4.png";
        else if (selectedToggle == avatar5Button) selectedAvatar = "avatar5.png";
        else if (selectedToggle == avatar6Button) selectedAvatar = "avatar6.png";
        else if (selectedToggle == avatar7Button) selectedAvatar = "avatar7.png";
        else if (selectedToggle == avatar8Button) selectedAvatar = "avatar8.png";
    }

    @FXML
    private void handleRegister() {
        String fullName = fullNameField.getText().trim();
        String username = usernameField.getText().trim();
        String email = emailField.getText().trim();
        String password = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();

        // Validation
        if (!validateInput(fullName, username, email, password, confirmPassword)) {
            return;
        }

        // Show loading
        showLoading(true);
        registerButton.setDisable(true);

        // Register
        new Thread(() -> {
            try {
                // ✅ Kết nối sử dụng ServerConfig
                if (!serverConnection.isConnected()) {
                    ServerConfig config = ServerConfig.getInstance();
                    System.out.println("🔌 Connecting to: " + config.getServerAddress());

                    boolean connected = serverConnection.connect(config.getHost(), config.getPort());

                    if (!connected) {
                        Platform.runLater(() -> {
                            showLoading(false);
                            registerButton.setDisable(false);
                            showError("❌ Không thể kết nối đến server!\n\n" +
                                    "Server: " + config.getServerAddress() + "\n" +
                                    "Mode: " + config.getMode() + "\n\n" +
                                    "Vui lòng kiểm tra:\n" +
                                    "• Server đã chạy chưa?\n" +
                                    "• Cấu hình kết nối đúng chưa?\n" +
                                    "• Kết nối mạng ổn định không?");
                        });
                        return;
                    }
                }

                // Send registration
                boolean registerSuccess = serverConnection.register(
                        username, password, email, fullName, selectedAge, selectedAvatar
                );

                Platform.runLater(() -> {
                    showLoading(false);
                    registerButton.setDisable(false);

                    if (registerSuccess) {
                        showSuccess("✅ Đăng ký thành công!\nĐang chuyển về màn hình đăng nhập...");

                        // Delay then go back
                        PauseTransition delay = new PauseTransition(Duration.seconds(2));
                        delay.setOnFinished(event -> handleBackToLogin());
                        delay.play();
                    } else {
                        showError("❌ Đăng ký thất bại!\nTên đăng nhập hoặc email đã tồn tại.");
                    }
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    showLoading(false);
                    registerButton.setDisable(false);
                    showError("❌ Lỗi kết nối: " + e.getMessage());
                });
                e.printStackTrace();
            }
        }).start();
    }

    @FXML
    private void handleBackToLogin() {
        try {
            SceneManager.getInstance().switchScene("Login.fxml");
        } catch (Exception e) {
            e.printStackTrace();
            showError("Lỗi khi chuyển về màn hình đăng nhập!");
        }
    }

    private boolean validateInput(String fullName, String username, String email,
                                  String password, String confirmPassword) {
        if (fullName.isEmpty() || username.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            showError("Vui lòng điền đầy đủ thông tin bắt buộc!");
            return false;
        }

        if (fullName.length() < 2) {
            showError("Họ và tên phải có ít nhất 2 ký tự!");
            return false;
        }

        if (username.length() < 3) {
            showError("Tên đăng nhập phải có ít nhất 3 ký tự!");
            return false;
        }

        if (!username.matches("^[a-zA-Z0-9_]+$")) {
            showError("Tên đăng nhập chỉ được chứa chữ cái, số và dấu gạch dưới!");
            return false;
        }

        if (!email.isEmpty() && !email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            showError("Email không hợp lệ!");
            return false;
        }

        if (password.length() < 6) {
            showError("Mật khẩu phải có ít nhất 6 ký tự!");
            return false;
        }

        if (!password.equals(confirmPassword)) {
            showError("Mật khẩu xác nhận không khớp!");
            return false;
        }

        if (ageGroup.getSelectedToggle() == null) {
            showError("Vui lòng chọn độ tuổi của bạn!");
            return false;
        }

        if (avatarGroup.getSelectedToggle() == null) {
            showError("Vui lòng chọn nhân vật yêu thích!");
            return false;
        }

        return true;
    }

    private void showLoading(boolean show) {
        loadingOverlay.setVisible(show);
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Lỗi đăng ký");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showSuccess(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Thành công");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.show();

        PauseTransition delay = new PauseTransition(Duration.seconds(2));
        delay.setOnFinished(event -> alert.close());
        delay.play();
    }
}