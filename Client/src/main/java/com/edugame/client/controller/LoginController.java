package com.edugame.client.controller;

import com.edugame.client.config.ServerConfig;
import com.edugame.client.network.ServerConnection;
import com.edugame.client.ui.ServerSelectorDialog;
import com.edugame.client.util.SceneManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.animation.PauseTransition;
import javafx.util.Duration;

public class LoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private CheckBox rememberMeCheckBox;
    @FXML private Button loginButton;
    @FXML private Button registerButton;
    @FXML private Button serverConfigButton; // NEW: Nút config server
    @FXML private Label serverInfoLabel; // NEW: Hiển thị server hiện tại
    @FXML private StackPane loadingOverlay;

    private ServerConnection serverConnection;

    @FXML
    public void initialize() {
        serverConnection = ServerConnection.getInstance();

        // Load saved credentials
        loadSavedCredentials();

        // Add Enter key handlers
        passwordField.setOnAction(event -> handleLogin());
        usernameField.setOnAction(event -> passwordField.requestFocus());

        // Focus on username field
        Platform.runLater(() -> {
            usernameField.requestFocus();
            updateServerInfoLabel(); // Hiển thị server info
        });
    }

    /**
     * NEW: Cập nhật label hiển thị server hiện tại
     */
    private void updateServerInfoLabel() {
        if (serverInfoLabel != null) {
            ServerConfig config = ServerConfig.getInstance();
            String icon = config.isLocal() ? "💻" :
                    config.isNgrok() ? "🌍" : "☁️";
            serverInfoLabel.setText(icon + " " + config.getServerAddress());
        }
    }

    /**
     * NEW: Mở dialog cấu hình server
     */
    @FXML
    private void handleServerConfig() {
        ServerSelectorDialog dialog = new ServerSelectorDialog();
        if (dialog.showAndWait()) {
            updateServerInfoLabel();
            showInfo("✅ Đã cập nhật cấu hình server!\n" +
                    "Bạn có thể đăng nhập ngay bây giờ.");
        }
    }

    @FXML
    private void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        // Validation
        if (username.isEmpty() || password.isEmpty()) {
            showError("Vui lòng nhập đầy đủ thông tin!");
            return;
        }

        if (username.length() < 3) {
            showError("Tên đăng nhập phải có ít nhất 3 ký tự!");
            return;
        }

        // Show loading
        showLoading(true);
        loginButton.setDisable(true);

        // Connect to server and authenticate
        new Thread(() -> {
            try {
                // Ngắt kết nối cũ nếu có
                if (serverConnection.isConnected()) {
                    serverConnection.disconnect();
                    Thread.sleep(200);
                }

                // ✅ Kết nối sử dụng ServerConfig
                ServerConfig config = ServerConfig.getInstance();
                System.out.println("🔌 Connecting to: " + config.getServerAddress());

                boolean connected = serverConnection.connect(config.getHost(), config.getPort());

                if (!connected) {
                    Platform.runLater(() -> {
                        showLoading(false);
                        loginButton.setDisable(false);
                        showError("❌ Không thể kết nối đến server!\n\n" +
                                "Server: " + config.getServerAddress() + "\n" +
                                "Mode: " + config.getMode() + "\n\n" +
                                "Kiểm tra:\n" +
                                "• Server đã chạy chưa?\n" +
                                "• Cấu hình Ngrok đúng chưa?\n" +
                                "• Kết nối mạng ổn định không?");
                    });
                    return;
                }

                // Send login request
                boolean loginSuccess = serverConnection.login(username, password);

                Platform.runLater(() -> {
                    showLoading(false);
                    loginButton.setDisable(false);

                    if (loginSuccess) {
                        // Save credentials if remember me
                        if (rememberMeCheckBox.isSelected()) {
                            saveCredentials(username);
                        } else {
                            clearSavedCredentials();
                        }

                        // Show success
                        showSuccess("✅ Đăng nhập thành công!");

                        // Navigate to home
                        PauseTransition delay = new PauseTransition(Duration.seconds(1));
                        delay.setOnFinished(event -> {
                            try {
                                SceneManager.getInstance().switchScene("Home.fxml");
                            } catch (Exception e) {
                                e.printStackTrace();
                                showError("Lỗi khi chuyển màn hình!");
                            }
                        });
                        delay.play();
                    } else {
                        showError("❌ Tên đăng nhập hoặc mật khẩu không đúng!");
                    }
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    showLoading(false);
                    loginButton.setDisable(false);
                    showError("❌ Lỗi kết nối: " + e.getMessage());
                });
                e.printStackTrace();
            }
        }).start();
    }

    @FXML
    private void handleGoToRegister() {
        try {
            SceneManager.getInstance().switchScene("Register.fxml");
        } catch (Exception e) {
            e.printStackTrace();
            showError("Lỗi khi chuyển sang màn hình đăng ký!");
        }
    }

    @FXML
    private void handleForgotPassword() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Quên mật khẩu");
        alert.setHeaderText("Liên hệ hỗ trợ");
        alert.setContentText("Vui lòng liên hệ với admin để được hỗ trợ khôi phục mật khẩu.\n\n" +
                "Email: support@brainquest.com\n" +
                "Hotline: 1900-xxxx");
        alert.showAndWait();
    }

    private void showLoading(boolean show) {
        loadingOverlay.setVisible(show);
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Lỗi đăng nhập");
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

        PauseTransition delay = new PauseTransition(Duration.seconds(1));
        delay.setOnFinished(event -> alert.close());
        delay.play();
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Thông báo");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void loadSavedCredentials() {
        String savedUsername = System.getProperty("saved.username", "");
        if (!savedUsername.isEmpty()) {
            usernameField.setText(savedUsername);
            rememberMeCheckBox.setSelected(true);
            passwordField.requestFocus();
        }
    }

    private void saveCredentials(String username) {
        System.setProperty("saved.username", username);
    }

    private void clearSavedCredentials() {
        System.clearProperty("saved.username");
    }
}