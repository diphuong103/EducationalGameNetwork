package com.edugame.client.controller;

import com.edugame.client.network.ServerConnection;
import com.edugame.client.util.SceneManager;
import com.edugame.common.Protocol;
import com.google.gson.JsonObject;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.Duration;

/**
 * Controller cho màn hình tìm trận
 * - Hiển thị loading animation
 * - Countdown timer
 * - Xử lý MATCH_FOUND, timeout
 */
public class FindMatchController {

    @FXML private Label subjectLabel;
    @FXML private Label statusLabel;
    @FXML private Label timerLabel;
    @FXML private Label playerCountLabel;
    @FXML private Button cancelButton;
    @FXML private ProgressIndicator progressIndicator;

    private ServerConnection connection;
    private Timeline timerTimeline;
    private int elapsedSeconds = 0;
    private static final int TIMEOUT_SECONDS = 30;

    private String selectedSubject;

    @FXML
    private void initialize() {
        connection = ServerConnection.getInstance();
        selectedSubject = connection.getSelectedSubject();

        // Hiển thị môn học
        updateSubjectLabel();

        // Bắt đầu timer
        startTimer();

        // Setup message handlers
        setupMessageHandlers();

        // Animation
        startAnimations();
    }

    /**
     * Cập nhật label môn học
     */
    private void updateSubjectLabel() {
        String subjectText = "Môn: ";

        switch (selectedSubject) {
            case Protocol.MATH:
                subjectText += "📐 Toán Học";
                break;
            case Protocol.ENGLISH:
                subjectText += "🔤 Tiếng Anh";
                break;
            case Protocol.LITERATURE:
                subjectText += "📚 Văn Học";
                break;
            default:
                subjectText += selectedSubject;
        }

        subjectLabel.setText(subjectText);
    }

    /**
     * Setup handlers cho server messages
     */
    private void setupMessageHandlers() {
        // Handler cho MATCH_FOUND
        connection.registerHandler(Protocol.MATCH_FOUND, this::handleMatchFound);

        // Handler cho FIND_MATCH response (bắt đầu tìm)
        connection.registerHandler(Protocol.FIND_MATCH, this::handleFindMatchResponse);
    }

    /**
     * Xử lý response từ server khi bắt đầu tìm
     */
    private void handleFindMatchResponse(JsonObject response) {
        boolean success = response.get("success").getAsBoolean();
        String message = response.has("message") ? response.get("message").getAsString() : "";

        if (!success) {
            // Lỗi khi bắt đầu tìm
            showErrorAndGoBack(message);
        }

        System.out.println("🔍 Find match started: " + message);
    }

    /**
     * Xử lý khi tìm thấy trận đấu
     */
    private void handleMatchFound(JsonObject response) {
        boolean success = response.get("success").getAsBoolean();

        if (success) {
            // ✅ Tìm thấy đối thủ
            String roomId = response.get("roomId").getAsString();
            JsonObject opponent = response.getAsJsonObject("opponent");

            String opponentName = opponent.get("fullName").getAsString();
            int opponentScore = opponent.get("totalScore").getAsInt();

            System.out.println("✅ Match found!");
            System.out.println("   Room: " + roomId);
            System.out.println("   Opponent: " + opponentName + " (" + opponentScore + ")");

            // Hiển thị thông báo
            statusLabel.setText("🎉 Đã tìm thấy đối thủ: " + opponentName);

            // Đợi 2 giây rồi vào phòng
            PauseTransition pause = new PauseTransition(Duration.seconds(2));
            pause.setOnFinished(e -> enterGameRoom(roomId));
            pause.play();

        } else {
            // ❌ Timeout hoặc lỗi
            boolean isTimeout = response.has("timeout") && response.get("timeout").getAsBoolean();
            String message = response.has("message") ? response.get("message").getAsString() :
                    "Không tìm thấy đối thủ";

            if (isTimeout) {
                showTimeoutMessage();
            } else {
                showErrorAndGoBack(message);
            }
        }
    }

    /**
     * Vào phòng game
     */
    private void enterGameRoom(String roomId) {
        try {
            cleanup();

            // TODO: Chuyển sang màn hình game với roomId
            // SceneManager.getInstance().switchScene("GameRoom.fxml");

            showInfo("Đang vào phòng: " + roomId);

            // Tạm thời về Home
            SceneManager.getInstance().switchScene("Home.fxml");

        } catch (Exception e) {
            showError("Không thể vào phòng game!");
            e.printStackTrace();
        }
    }

    /**
     * Hiển thị thông báo timeout
     */
    private void showTimeoutMessage() {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Hết thời gian");
            alert.setHeaderText("Không tìm thấy đối thủ");
            alert.setContentText("Không tìm thấy đối thủ phù hợp trong 30 giây.\nVui lòng thử lại!");

            alert.setOnCloseRequest(e -> goBackToHome());
            alert.showAndWait();

            goBackToHome();
        });
    }

    /**
     * Bắt đầu countdown timer
     */
    private void startTimer() {
        timerTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            elapsedSeconds++;
            updateTimerDisplay();

            // Kiểm tra timeout
            if (elapsedSeconds >= TIMEOUT_SECONDS) {
                timerTimeline.stop();
                handleLocalTimeout();
            }
        }));

        timerTimeline.setCycleCount(TIMEOUT_SECONDS);
        timerTimeline.play();
    }

    /**
     * Cập nhật hiển thị timer
     */
    private void updateTimerDisplay() {
        int minutes = elapsedSeconds / 60;
        int seconds = elapsedSeconds % 60;
        timerLabel.setText(String.format("%02d:%02d", minutes, seconds));
    }

    /**
     * Xử lý timeout local (backup)
     */
    private void handleLocalTimeout() {
        statusLabel.setText("⏰ Hết thời gian chờ...");
        cancelButton.setDisable(true);
    }

    /**
     * Start animations
     */
    private void startAnimations() {
        // Progress indicator animation
        if (progressIndicator != null) {
            progressIndicator.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        }

        // Status label fade animation
        FadeTransition fade = new FadeTransition(Duration.seconds(1.5), statusLabel);
        fade.setFromValue(1.0);
        fade.setToValue(0.5);
        fade.setCycleCount(Animation.INDEFINITE);
        fade.setAutoReverse(true);
        fade.play();
    }

    /**
     * Hủy tìm kiếm
     */
    @FXML
    private void handleCancel() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Xác nhận");
        alert.setHeaderText("Hủy tìm kiếm");
        alert.setContentText("Bạn có chắc muốn hủy tìm kiếm?");

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                cancelMatchmaking();
            }
        });
    }

    /**
     * Gửi CANCEL_FIND_MATCH đến server
     */
    private void cancelMatchmaking() {
        try {
            String request = String.format(
                    "{\"type\":\"%s\"}",
                    Protocol.CANCEL_FIND_MATCH
            );

            connection.sendMessage(request);

            cleanup();
            goBackToHome();

        } catch (Exception e) {
            showError("Lỗi khi hủy tìm kiếm!");
            e.printStackTrace();
        }
    }

    /**
     * Quay về Home
     */
    private void goBackToHome() {
        try {
            cleanup();
            SceneManager.getInstance().switchScene("Home.fxml");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Hiển thị lỗi và quay về
     */
    private void showErrorAndGoBack(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Lỗi");
            alert.setHeaderText(null);
            alert.setContentText(message);

            alert.setOnCloseRequest(e -> goBackToHome());
            alert.showAndWait();

            goBackToHome();
        });
    }

    /**
     * Hiển thị thông báo
     */
    private void showInfo(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Thông báo");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    /**
     * Hiển thị lỗi
     */
    private void showError(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Lỗi");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    /**
     * Cleanup khi rời màn hình
     */
    private void cleanup() {
        // Dừng timer
        if (timerTimeline != null) {
            timerTimeline.stop();
        }

        // Unregister handlers
        connection.unregisterHandler(Protocol.MATCH_FOUND);
        connection.unregisterHandler(Protocol.FIND_MATCH);
    }
}