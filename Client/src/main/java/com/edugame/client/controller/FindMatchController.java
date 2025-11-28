package com.edugame.client.controller;

import com.edugame.client.network.ServerConnection;
import com.edugame.client.util.SceneManager;
import com.edugame.common.Protocol;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.Duration;

import java.time.LocalDateTime;
import java.util.*;

/**
 * ✅ FIXED: Tìm trận và chuyển sang game với đầy đủ thông tin
 * Version: 2.0 - Fixed NullPointerException & wrong score display
 */
public class FindMatchController {

    @FXML private Label subjectLabel;
    @FXML private Label statusLabel;
    @FXML private Label timerLabel;
    @FXML private Label infoLabel;
    @FXML private Button cancelButton;
    @FXML private ProgressIndicator progressIndicator;

    private static final String[] FXML_PATHS = {
            "/fxml/",
            "/com/edugame/client/view/",
            "/"
    };

    private ServerConnection connection;
    private Timeline timerTimeline;
    private int elapsedSeconds = 0;

    private String selectedSubject;
    private String selectedDifficulty;
    private int selectedCountPlayer;

    // ✅ Lưu thông tin game để truyền sang game screen
    private Map<String, Object> pendingGameData;

    @FXML
    private void initialize() {
        connection = ServerConnection.getInstance();
        selectedSubject = connection.getSelectedSubject();
        selectedDifficulty = connection.getSelectedDifficulty();
        selectedCountPlayer = connection.getSelectedCountPlayer();

        updateSubjectLabel();
        updateInfoLabel();
        startInfiniteTimer();
        setupMessageHandlers();
        startAnimations();
    }

    private void updateSubjectLabel() {
        String subjectText = "";

        switch (selectedSubject) {
            case Protocol.MATH:
                subjectText = "📐 Toán Học";
                break;
            case Protocol.ENGLISH:
                subjectText = "🔤 Tiếng Anh";
                break;
            case Protocol.LITERATURE:
                subjectText = "📚 Văn Học";
                break;
            default:
                subjectText = selectedSubject;
        }

        String diffText = "";
        switch (selectedDifficulty) {
            case Protocol.EASY:
                diffText = " - ⭐ Dễ";
                break;
            case Protocol.MEDIUM:
                diffText = " - ⭐⭐ Trung bình";
                break;
            case Protocol.HARD:
                diffText = " - ⭐⭐⭐ Khó";
                break;
        }

        subjectLabel.setText(subjectText + diffText);
    }

    private void updateInfoLabel() {
        if (infoLabel != null) {
            infoLabel.setText("🔍 Đang tìm đối thủ phù hợp...\n" +
                    "Bạn có thể hủy bất cứ lúc nào");
        }
    }

    private void setupMessageHandlers() {
        System.out.println("🔧 [FindMatchController] Registering handlers...");

        // ✅ Đăng ký MATCH_FOUND
        connection.registerHandler(Protocol.MATCH_FOUND, this::handleMatchFound);
        System.out.println("   ✅ Registered: MATCH_FOUND");

        // ✅ Đăng ký START_GAME - QUAN TRỌNG!
        connection.registerHandler(Protocol.START_GAME, this::handleGameStart);
        System.out.println("   ✅ Registered: START_GAME");

        // Đăng ký FIND_MATCH (nếu cần)
        connection.registerHandler(Protocol.FIND_MATCH, this::handleFindMatchResponse);
        System.out.println("   ✅ Registered: FIND_MATCH");
    }

    private void handleFindMatchResponse(JsonObject response) {
        Platform.runLater(() -> {
            boolean success = response.get("success").getAsBoolean();
            String message = response.has("message") ? response.get("message").getAsString() : "";

            if (!success) {
                showErrorAndGoBack(message);
            } else {
                statusLabel.setText("✅ " + message);
                System.out.println("🔍 Find match started: " + message);
            }
        });
    }

    /**
     * ✅ SIMPLE: MATCH_FOUND chỉ hiển thị thông báo
     */
    private void handleMatchFound(JsonObject response) {
        Platform.runLater(() -> {
            try {
                boolean success = response.get("success").getAsBoolean();

                if (success) {
                    String roomId = response.get("roomId").getAsString();
                    connection.setCurrentRoomId(roomId);

                    // Dừng timer
                    if (timerTimeline != null) {
                        timerTimeline.stop();
                    }

                    // Hiển thị thông báo đơn giản
                    statusLabel.setText("🎉 Đã tìm thấy trận đấu!");
                    if (infoLabel != null) {
                        infoLabel.setText("⏳ Đang chuẩn bị game...\nVui lòng đợi...");
                    }

                    cancelButton.setDisable(true);

                    System.out.println("✅ Match found! Room: " + roomId);
                    System.out.println("⏳ Waiting for START_GAME...");

                } else {
                    // Xử lý error/timeout
                    String message = response.has("message") ?
                            response.get("message").getAsString() :
                            "Không thể tạo trận đấu";
                    showErrorAndGoBack(message);
                }

            } catch (Exception e) {
                System.err.println("❌ Error: " + e.getMessage());
                showErrorAndGoBack("Lỗi xử lý dữ liệu");
            }
        });
    }

    /**
     * ✅ CRITICAL: Xử lý START_GAME và chuyển sang game screen
     */
    private void handleGameStart(JsonObject response) {
        System.out.println("🎮 [FindMatchController] START_GAME handler called!");
        System.out.println("📦 Response: " + response.toString());

        Platform.runLater(() -> {
            try {
                // ✅ Kiểm tra success
                if (!response.has("success") || !response.get("success").getAsBoolean()) {
                    System.err.println("❌ START_GAME failed");
                    showErrorAndGoBack("Không thể bắt đầu game");
                    return;
                }

                // ✅ Parse data
                String roomId = response.get("roomId").getAsString();
                String subject = response.get("subject").getAsString();
                String difficulty = response.get("difficulty").getAsString();

                System.out.println("📦 Game data:");
                System.out.println("   Room: " + roomId);
                System.out.println("   Subject: " + subject);
                System.out.println("   Difficulty: " + difficulty);

                // ✅ Parse players
                List<Map<String, Object>> playersList = new ArrayList<>();
                if (response.has("players")) {
                    JsonArray playersArray = response.getAsJsonArray("players");
                    System.out.println("   Players: " + playersArray.size());

                    for (int i = 0; i < playersArray.size(); i++) {
                        JsonObject playerJson = playersArray.get(i).getAsJsonObject();
                        Map<String, Object> player = parsePlayerData(playerJson);
                        playersList.add(player);

                        System.out.println("      P" + (i+1) + ": " + player.get("fullName") +
                                " (ID: " + player.get("userId") + ")");
                    }
                } else {
                    System.err.println("⚠️ No players array in START_GAME");
                }

                // ✅ Parse questions
                List<Map<String, Object>> questions = new ArrayList<>();
                if (response.has("questions")) {
                    JsonArray questionsArray = response.getAsJsonArray("questions");
                    System.out.println("   Questions: " + questionsArray.size());

                    for (int i = 0; i < questionsArray.size(); i++) {
                        JsonObject qJson = questionsArray.get(i).getAsJsonObject();
                        Map<String, Object> question = new HashMap<>();
                        question.put("questionId", qJson.get("questionId").getAsInt());
                        question.put("questionNumber", qJson.get("questionNumber").getAsInt());
                        question.put("questionText", qJson.get("questionText").getAsString());
                        question.put("optionA", qJson.get("optionA").getAsString());
                        question.put("optionB", qJson.get("optionB").getAsString());
                        question.put("optionC", qJson.get("optionC").getAsString());
                        question.put("optionD", qJson.get("optionD").getAsString());
                        questions.add(question);
                    }
                } else {
                    System.err.println("⚠️ No questions array in START_GAME");
                }

                // ✅ Tạo game data
                Map<String, Object> gameData = new HashMap<>();
                gameData.put("roomId", roomId);
                gameData.put("subject", subject);
                gameData.put("difficulty", difficulty);
                gameData.put("players", playersList);
                gameData.put("questions", questions);

                // ✅ Update connection
                connection.setCurrentRoomId(roomId);
                connection.setSelectedSubject(subject);
                connection.setSelectedDifficulty(difficulty);

                // ✅ UI feedback
                statusLabel.setText("🎮 Đang vào game...");
                if (infoLabel != null) {
                    infoLabel.setText("✅ Chuẩn bị hoàn tất!\nĐang chuyển màn hình...");
                }

                System.out.println("🎮 Calling openGameScreen()...");

                // ✅ Chuyển sang game screen
                openGameScreen(subject, gameData);

            } catch (Exception e) {
                System.err.println("❌ Error in handleGameStart: " + e.getMessage());
                e.printStackTrace();
                showErrorAndGoBack("Lỗi khi bắt đầu game: " + e.getMessage());
            }
        });
    }

    /**
     * ✅ Helper: Parse player data từ JsonObject
     */
    private Map<String, Object> parsePlayerData(JsonObject playerJson) {
        Map<String, Object> player = new HashMap<>();

        player.put("userId", playerJson.has("userId") ?
                playerJson.get("userId").getAsInt() : 0);
        player.put("username", playerJson.has("username") ?
                playerJson.get("username").getAsString() : "Unknown");
        player.put("fullName", playerJson.has("fullName") ?
                playerJson.get("fullName").getAsString() : "Unknown");

        // Hỗ trợ cả "total" và "totalScore"
        if (playerJson.has("total")) {
            player.put("total", playerJson.get("total").getAsInt());
        } else if (playerJson.has("totalScore")) {
            player.put("total", playerJson.get("totalScore").getAsInt());
        } else {
            player.put("total", 0);
        }

        player.put("score_math", playerJson.has("score_math") ?
                playerJson.get("score_math").getAsInt() :
                (playerJson.has("scoreMath") ? playerJson.get("scoreMath").getAsInt() : 0));

        player.put("score_english", playerJson.has("score_english") ?
                playerJson.get("score_english").getAsInt() :
                (playerJson.has("scoreEnglish") ? playerJson.get("scoreEnglish").getAsInt() : 0));

        player.put("score_literature", playerJson.has("score_literature") ?
                playerJson.get("score_literature").getAsInt() :
                (playerJson.has("scoreLiterature") ? playerJson.get("scoreLiterature").getAsInt() : 0));

        return player;
    }

    private static void log(String message) {
        System.out.println("[FindMatchController: ]  " + message);
    }

    /**
     * ✅ IMPROVED: Mở màn hình game với dữ liệu đầy đủ
     */
    private void openGameScreen(String subject, Map<String, Object> gameData) {
        try {
            System.out.println("🎮 [openGameScreen] Starting...");
            System.out.println("   Subject: " + subject);
            System.out.println("   Game data keys: " + gameData.keySet());

            String fxmlFile;

            switch (subject.toLowerCase()) {
                case "math":
                    fxmlFile = "MathGame.fxml";
                    break;

                case "english":
                    fxmlFile = "EnglishGame.fxml";
                    break;

                case "literature":
                    fxmlFile = "LiteratureGame.fxml";
                    break;

                default:
                    System.err.println("❌ Unknown subject: " + subject);
                    showErrorAndGoBack("Môn học không hợp lệ: " + subject);
                    return;
            }

            System.out.println("📂 Loading FXML: " + fxmlFile);
            log("📂 Loading FXML: " + fxmlFile);

            // ✅ Cleanup TRƯỚC KHI chuyển scene
            cleanup();

            // ✅ Chuyển scene
            SceneManager sceneManager = SceneManager.getInstance();

            System.out.println("🔄 Switching to game scene...");

            sceneManager.switchScene(fxmlFile, (controller) -> {
                System.out.println("🎮 Game controller loaded: " + controller.getClass().getSimpleName());

                try {
                    // ✅ Khởi tạo game controller
                    if (controller instanceof MathGameController) {
                        System.out.println("   Initializing MathGameController...");
                        ((MathGameController) controller).initializeGame(gameData);
                    } else if (controller instanceof EnglishGameController) {
                        System.out.println("   Initializing EnglishGameController...");
                        ((EnglishGameController) controller).initializeGame(gameData);
                    } else if (controller instanceof LiteratureGameController) {
                        System.out.println("   Initializing LiteratureGameController...");
                        ((LiteratureGameController) controller).initializeGame(gameData);
                    } else {
                        System.err.println("❌ Unknown controller type: " + controller.getClass());
                    }

                    System.out.println("✅ Game initialized successfully!");

                } catch (Exception e) {
                    System.err.println("❌ Error initializing game controller: " + e.getMessage());
                    e.printStackTrace();
                }
            });

            System.out.println("✅ Scene switch completed");

        } catch (Exception e) {
            System.err.println("❌ Failed to open game screen: " + e.getMessage());
            e.printStackTrace();
            showErrorAndGoBack("Không thể mở màn hình game!");
        }
    }

    private void startInfiniteTimer() {
        timerTimeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            elapsedSeconds++;
            updateTimerDisplay();
            updateStatusText();
        }));

        timerTimeline.setCycleCount(Timeline.INDEFINITE);
        timerTimeline.play();

        System.out.println("⏱️ Timer started (INFINITE MODE)");
    }

    private void updateTimerDisplay() {
        timerLabel.setText(formatTime(elapsedSeconds));
    }

    private void updateStatusText() {
        String text;

        if (elapsedSeconds < 10) {
            text = "🔍 Đang tìm kiếm đối thủ...";
        } else if (elapsedSeconds < 30) {
            text = "🔍 Đang tìm kiếm... Vui lòng đợi";
        } else if (elapsedSeconds < 60) {
            text = "⏳ Vẫn đang tìm kiếm...";
        } else if (elapsedSeconds < 120) {
            text = "⏳ Có thể chưa có đối thủ phù hợp\nBạn có thể hủy và thử lại";
        } else {
            text = "⏳ Đang chờ... (" + formatTime(elapsedSeconds) + ")\n" +
                    "Bạn có thể hủy bất cứ lúc nào";
        }

        statusLabel.setText(text);
    }

    private String formatTime(int totalSeconds) {
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    private void startAnimations() {
        if (progressIndicator != null) {
            progressIndicator.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
        }

        FadeTransition fade = new FadeTransition(Duration.seconds(1.5), statusLabel);
        fade.setFromValue(1.0);
        fade.setToValue(0.6);
        fade.setCycleCount(Animation.INDEFINITE);
        fade.setAutoReverse(true);
        fade.play();
    }

    @FXML
    private void handleCancel() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Xác nhận");
        alert.setHeaderText("Hủy tìm kiếm");
        alert.setContentText("Bạn đã tìm kiếm " + formatTime(elapsedSeconds) +
                "\nBạn có chắc muốn hủy?");

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                cancelMatchmaking();
            }
        });
    }

    private void cancelMatchmaking() {
        try {
            System.out.println("❌ Cancelling matchmaking after " + formatTime(elapsedSeconds));

            connection.cancelFindMatch();
            cleanup();
            goBackToHome();

        } catch (Exception e) {
            showError("Lỗi khi hủy tìm kiếm!");
            e.printStackTrace();
        }
    }

    private void goBackToHome() {
        try {
            cleanup();
            SceneManager.getInstance().switchScene("Home.fxml");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * ✅ FIXED: Hiển thị lỗi và tự động quay về Home sau 2 giây
     */
    private void showErrorAndGoBack(String message) {
        Platform.runLater(() -> {
            if (statusLabel != null) {
                statusLabel.setText("❌ " + message);
            }

            if (timerTimeline != null) {
                timerTimeline.stop();
            }

            if (cancelButton != null) {
                cancelButton.setDisable(false);
            }

            // Hiển thị alert
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Lỗi");
            alert.setHeaderText(null);
            alert.setContentText(message);

            alert.setOnHidden(e -> {
                // Tự động quay về sau khi đóng alert
                PauseTransition pause = new PauseTransition(Duration.seconds(0.5));
                pause.setOnFinished(evt -> goBackToHome());
                pause.play();
            });

            alert.showAndWait();
        });
    }

    private void showInfo(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Thông báo");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    private void showError(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Lỗi");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    private void cleanup() {
        if (timerTimeline != null) {
            timerTimeline.stop();
            timerTimeline = null;
        }

        connection.unregisterHandler(Protocol.MATCH_FOUND);
        connection.unregisterHandler(Protocol.GAME_START);
        connection.unregisterHandler(Protocol.FIND_MATCH);

        pendingGameData = null;

        System.out.println("🧹 FindMatchController cleaned up");
    }
}