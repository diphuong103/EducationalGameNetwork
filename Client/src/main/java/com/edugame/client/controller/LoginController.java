package com.edugame.client.controller;

import com.edugame.client.network.ServerConnection;
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
    @FXML private StackPane loadingOverlay;

    private ServerConnection serverConnection;

    @FXML
    public void initialize() {
        serverConnection = ServerConnection.getInstance();

        // Load saved username if remember me was checked
        loadSavedCredentials();

        // Add Enter key handler for quick login
        passwordField.setOnAction(event -> handleLogin());
        usernameField.setOnAction(event -> passwordField.requestFocus());

        // Focus on username field when scene loads
        Platform.runLater(() -> usernameField.requestFocus());
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
                // Connect to server if not connected
                if (!serverConnection.isConnected()) {
                    boolean connected = serverConnection.connect("localhost", 8888);
                    if (!connected) {
                        Platform.runLater(() -> {
                            showLoading(false);
                            loginButton.setDisable(false);
                            showError("Không thể kết nối đến server!\nVui lòng kiểm tra kết nối mạng.");
                        });
                        return;
                    }
                }

                // Send login request
                boolean loginSuccess = serverConnection.login(username, password);

                Platform.runLater(() -> {
                    showLoading(false);
                    loginButton.setDisable(false);

                    if (loginSuccess) {
                        // Save credentials if remember me is checked
                        if (rememberMeCheckBox.isSelected()) {
                            saveCredentials(username);
                        } else {
                            clearSavedCredentials();
                        }

                        // Show success message
                        showSuccess("Đăng nhập thành công!");

                        // Navigate to home screen after delay
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
                        showError("Tên đăng nhập hoặc mật khẩu không đúng!");
                    }
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    showLoading(false);
                    loginButton.setDisable(false);
                    showError("Lỗi kết nối: " + e.getMessage());
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
                "Email: support@mathadventure.com\n" +
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

        // Auto close after 1 second
        PauseTransition delay = new PauseTransition(Duration.seconds(1));
        delay.setOnFinished(event -> alert.close());
        delay.play();
    }

    private void loadSavedCredentials() {
        // TODO: Implement preferences loading
        // For now, just a placeholder
        String savedUsername = System.getProperty("saved.username", "");
        if (!savedUsername.isEmpty()) {
            usernameField.setText(savedUsername);
            rememberMeCheckBox.setSelected(true);
            passwordField.requestFocus();
        }
    }

    private void saveCredentials(String username) {
        // TODO: Implement preferences saving
        System.setProperty("saved.username", username);
    }

    private void clearSavedCredentials() {
        // TODO: Implement preferences clearing
        System.clearProperty("saved.username");
    }
}