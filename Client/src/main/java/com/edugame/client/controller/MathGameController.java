package com.edugame.client.controller;

import com.edugame.client.network.ServerConnection;
import com.edugame.client.util.SceneManager;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.util.Duration;

import java.io.IOException;
import java.util.*;

/**
 * MathGameController - Compact Version
 */
public class MathGameController {

    @FXML private BorderPane mainPane;
    @FXML private ImageView trackBackground;
    @FXML private Pane finishLine;

    // Cars
    @FXML private ImageView car1, car2, car3, car4;

    // Player Names
    @FXML private Label player1Name, player2Name, player3Name, player4Name;

    // Timer
    @FXML private Label timerLabel;

    // Question Panel
    @FXML private VBox questionPanel;
    @FXML private Label questionLabel;
    @FXML private ProgressBar timeProgressBar;
    @FXML private Button btnA, btnB, btnC, btnD;

    // Overlays
    @FXML private StackPane countdownOverlay;
    @FXML private Label countdownLabel;
    @FXML private StackPane resultOverlay;
    @FXML private Label resultTitle, resultMessage;
    @FXML private Button btnBackToLobby;

    // Game State
    private ServerConnection connection;
    private String roomId;
    private Timeline gameTimer;
    private Timeline questionTimer;
    private int remainingSeconds = 300;
    private int questionTimeLimit = 10;
    private double questionTimeRemaining = 10.0;

    // Player Data
    private Map<Integer, Double> playerPositions = new HashMap<>();
    private Map<Integer, Integer> playerScores = new HashMap<>();
    private Map<Integer, Integer> userIdToSlot = new HashMap<>();
    private List<ImageView> cars = new ArrayList<>();
    private List<Label> playerLabels = new ArrayList<>();
    private int currentUserId;

    // Question Data
    private int currentQuestionId;
    private int currentQuestionNumber;
    private int totalQuestions;
    private long questionStartTime;
    private boolean answered = false;
    private int correctAnswerIndex = -1;

    // Constants - Giảm kích thước track
    private static final double FINISH_LINE_X = 1050.0;  // Giảm từ 1450 → 1050
    private static final double START_X = 0.0;

    @FXML
    public void initialize() {
        connection = ServerConnection.getInstance();
        currentUserId = connection.getCurrentUserId();

        // Initialize collections
        cars.addAll(Arrays.asList(car1, car2, car3, car4));
        playerLabels.addAll(Arrays.asList(player1Name, player2Name, player3Name, player4Name));

        // Setup callbacks
        setupGameCallbacks();

        // Hide overlays
        countdownOverlay.setVisible(false);
        resultOverlay.setVisible(false);
        questionPanel.setVisible(false);

        System.out.println("✅ [MathGameController] Initialized (Compact)");
    }

    public void initializeGame(Map<String, Object> gameData) {
        try {
            this.roomId = getStringValue(gameData.get("roomId"));

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> players = (List<Map<String, Object>>) gameData.get("players");

            for (int i = 0; i < players.size() && i < 4; i++) {
                Map<String, Object> player = players.get(i);
                int userId = getIntValue(player.get("userId"));
                String fullName = getStringValue(player.get("fullName"));

                int slot = i + 1;
                userIdToSlot.put(userId, slot);
                playerPositions.put(userId, START_X);
                playerScores.put(userId, 0);

                playerLabels.get(i).setText(fullName);
                playerLabels.get(i).setVisible(true);
                cars.get(i).setVisible(true);
                cars.get(i).setTranslateX(START_X);
            }

            for (int i = players.size(); i < 4; i++) {
                playerLabels.get(i).setVisible(false);
                cars.get(i).setVisible(false);
            }

            System.out.println("✅ [MathGameController] Game initialized with " + players.size() + " players");
            startCountdown();

        } catch (Exception e) {
            System.err.println("❌ [MathGameController] Error initializing: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void startCountdown() {
        countdownOverlay.setVisible(true);
        countdownOverlay.setAlignment(Pos.CENTER);
        questionPanel.setVisible(false);

        Timeline countdown = new Timeline(
                new KeyFrame(Duration.seconds(0), e -> countdownLabel.setText("10")),
                new KeyFrame(Duration.seconds(1), e -> countdownLabel.setText("9")),
                new KeyFrame(Duration.seconds(2), e -> countdownLabel.setText("8")),
                new KeyFrame(Duration.seconds(3), e -> countdownLabel.setText("7")),
                new KeyFrame(Duration.seconds(4), e -> countdownLabel.setText("6")),
                new KeyFrame(Duration.seconds(5), e -> countdownLabel.setText("5")),
                new KeyFrame(Duration.seconds(6), e -> countdownLabel.setText("4")),
                new KeyFrame(Duration.seconds(7), e -> countdownLabel.setText("3")),
                new KeyFrame(Duration.seconds(8), e -> countdownLabel.setText("2")),
                new KeyFrame(Duration.seconds(9), e -> countdownLabel.setText("1")),
                new KeyFrame(Duration.seconds(10), e -> countdownLabel.setText("GO!")),
                new KeyFrame(Duration.seconds(11), e -> {
                    countdownOverlay.setVisible(false);
                    questionPanel.setVisible(true);
                    startGameTimer();
                })
        );
        countdown.play();

        System.out.println("⏳ [MathGameController] Countdown started");
    }

    private void startGameTimer() {
        gameTimer = new Timeline(new KeyFrame(Duration.seconds(1), e -> {
            remainingSeconds--;
            updateTimerDisplay();

            if (remainingSeconds <= 0) {
                handleTimeUp();
            }
        }));
        gameTimer.setCycleCount(Timeline.INDEFINITE);
        gameTimer.play();

        System.out.println("⏱️ [MathGameController] Game timer started");
    }

    private void updateTimerDisplay() {
        int minutes = remainingSeconds / 60;
        int seconds = remainingSeconds % 60;
        timerLabel.setText(String.format("%d:%02d", minutes, seconds));

        if (remainingSeconds <= 30) {
            timerLabel.setStyle("-fx-text-fill: #ff0000;");
        }
    }

    private void showQuestion(Map<String, Object> questionData) {
        Platform.runLater(() -> {
            answered = false;
            questionTimeRemaining = questionTimeLimit;

            currentQuestionId = getIntValue(questionData.get("questionId"));
            currentQuestionNumber = getIntValue(questionData.get("questionNumber"));
            totalQuestions = getIntValue(questionData.get("totalQuestions"));
            String questionText = getStringValue(questionData.get("questionText"));

            @SuppressWarnings("unchecked")
            List<String> options = (List<String>) questionData.get("options");

            questionLabel.setText(questionText);

            List<Button> buttons = Arrays.asList(btnA, btnB, btnC, btnD);
            char[] letters = {'A', 'B', 'C', 'D'};

            for (int i = 0; i < buttons.size() && i < options.size(); i++) {
                Button btn = buttons.get(i);
                btn.setText(letters[i] + ") " + options.get(i));
                btn.setDisable(false);
                btn.getStyleClass().removeAll("answer-btn-correct", "answer-btn-wrong");
            }

            startQuestionTimer();
            questionStartTime = System.currentTimeMillis();

            System.out.println("❓ [MathGameController] Question " + currentQuestionNumber + "/" + totalQuestions);
        });
    }

    private void startQuestionTimer() {
        timeProgressBar.setProgress(1.0);

        if (questionTimer != null) {
            questionTimer.stop();
        }

        questionTimer = new Timeline(new KeyFrame(Duration.millis(100), e -> {
            questionTimeRemaining -= 0.1;
            timeProgressBar.setProgress(questionTimeRemaining / questionTimeLimit);

            if (questionTimeRemaining <= 0 && !answered) {
                handleTimeout();
            }
        }));
        questionTimer.setCycleCount((int)(questionTimeLimit * 10));
        questionTimer.play();
    }

    @FXML
    private void handleAnswer(javafx.event.ActionEvent event) {
        if (answered) return;

        Button clickedButton = (Button) event.getSource();
        int answerIndex = -1;

        if (clickedButton == btnA) answerIndex = 0;
        else if (clickedButton == btnB) answerIndex = 1;
        else if (clickedButton == btnC) answerIndex = 2;
        else if (clickedButton == btnD) answerIndex = 3;

        if (answerIndex != -1) {
            handleAnswerButton(answerIndex);
        }
    }

    private void handleAnswerButton(int answerIndex) {
        if (answered) return;

        answered = true;

        if (questionTimer != null) {
            questionTimer.stop();
        }

        btnA.setDisable(true);
        btnB.setDisable(true);
        btnC.setDisable(true);
        btnD.setDisable(true);

        connection.submitAnswer(roomId, answerIndex);

        System.out.println("📝 [MathGameController] Submitted answer: " + (char)('A' + answerIndex));
    }

    private void handleTimeout() {
        if (answered) return;

        answered = true;

        btnA.setDisable(true);
        btnB.setDisable(true);
        btnC.setDisable(true);
        btnD.setDisable(true);

        System.out.println("⏰ [MathGameController] Question timeout");

        connection.submitAnswer(roomId, -1);
    }

    private void showAnswerFeedback(boolean isCorrect, int correctIndex) {
        Platform.runLater(() -> {
            List<Button> buttons = Arrays.asList(btnA, btnB, btnC, btnD);

            if (correctIndex >= 0 && correctIndex < buttons.size()) {
                buttons.get(correctIndex).getStyleClass().add("answer-btn-correct");
            }

            System.out.println((isCorrect ? "✅" : "❌") + " [MathGameController] Answer feedback shown");
        });
    }

    private void animatePositions(List<Map<String, Object>> positions) {
        Platform.runLater(() -> {
            ParallelTransition parallel = new ParallelTransition();

            for (Map<String, Object> pos : positions) {
                int userId = getIntValue(pos.get("userId"));
                double newPosition = getDoubleValue(pos.get("position"));
                boolean gotNitro = getBooleanValue(pos.get("gotNitro"));

                Integer slot = userIdToSlot.get(userId);
                if (slot == null || slot < 1 || slot > 4) continue;

                ImageView car = cars.get(slot - 1);
                double oldPos = playerPositions.getOrDefault(userId, START_X);

                TranslateTransition tt = new TranslateTransition(Duration.seconds(1), car);
                tt.setFromX(oldPos);
                tt.setToX(Math.min(newPosition, FINISH_LINE_X));
                tt.setInterpolator(Interpolator.EASE_OUT);

                parallel.getChildren().add(tt);

                playerPositions.put(userId, newPosition);

                if (gotNitro) {
                    animateNitro(car);
                }
            }

            parallel.play();

            System.out.println("🏎️ [MathGameController] Animated positions");
        });
    }

    private void animateNitro(ImageView car) {
        ScaleTransition st = new ScaleTransition(Duration.millis(300), car);
        st.setToX(1.2);
        st.setToY(1.2);
        st.setCycleCount(2);
        st.setAutoReverse(true);
        st.play();

        System.out.println("🚀 [MathGameController] Nitro boost!");
    }

    private void handleTimeUp() {
        if (gameTimer != null) {
            gameTimer.stop();
        }
        if (questionTimer != null) {
            questionTimer.stop();
        }

        System.out.println("⏰ [MathGameController] Time's up!");
    }

    private void showGameResults(Map<String, Object> endData) {
        Platform.runLater(() -> {
            if (gameTimer != null) gameTimer.stop();
            if (questionTimer != null) questionTimer.stop();

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> rankings = (List<Map<String, Object>>) endData.get("rankings");

            if (rankings != null && !rankings.isEmpty()) {
                Map<String, Object> winner = rankings.get(0);
                int winnerId = getIntValue(winner.get("userId"));
                int winnerScore = getIntValue(winner.get("score"));

                Integer winnerSlot = userIdToSlot.get(winnerId);
                String winnerName = winnerSlot != null && winnerSlot > 0 ?
                        playerLabels.get(winnerSlot - 1).getText() : "Unknown";

                resultTitle.setText("🏆 WINNER 🏆");
                resultMessage.setText(winnerName + " Wins!\nScore: " + winnerScore);

                if (winnerId == currentUserId) {
                    resultTitle.setText("🎉 YOU WIN! 🎉");
                }
            }

            resultOverlay.setVisible(true);

            FadeTransition ft = new FadeTransition(Duration.seconds(0.5), resultOverlay);
            ft.setFromValue(0);
            ft.setToValue(1);
            ft.play();

            System.out.println("🏁 [MathGameController] Game results shown");
        });
    }

    @FXML
    private void handleBackToLobby() {
        cleanup();
        try {
            SceneManager.getInstance().switchScene("Home.fxml");
        } catch (IOException e) {
            System.err.println("❌ Error returning to lobby: " + e.getMessage());
        }
    }

    private void setupGameCallbacks() {
        connection.setGameQuestionCallback(this::showQuestion);

        connection.setAnswerResultCallback(data -> {
            boolean isCorrect = getBooleanValue(data.get("isCorrect"));
            showAnswerFeedback(isCorrect, -1);
        });

        connection.setGameUpdateCallback(data -> {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> positions = (List<Map<String, Object>>) data.get("positions");
            if (positions != null) {
                animatePositions(positions);
            }
        });

        connection.setGameEndCallback(this::showGameResults);

        System.out.println("✅ [MathGameController] Callbacks registered");
    }

    private void cleanup() {
        if (gameTimer != null) gameTimer.stop();
        if (questionTimer != null) questionTimer.stop();

        connection.clearGameCallbacks();

        System.out.println("🧹 [MathGameController] Cleaned up");
    }

    private int getIntValue(Object obj) {
        if (obj == null) return 0;
        if (obj instanceof Number) return ((Number) obj).intValue();
        try {
            return Integer.parseInt(obj.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private double getDoubleValue(Object obj) {
        if (obj == null) return 0.0;
        if (obj instanceof Number) return ((Number) obj).doubleValue();
        try {
            return Double.parseDouble(obj.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private String getStringValue(Object obj) {
        return obj != null ? obj.toString() : "";
    }

    private boolean getBooleanValue(Object obj) {
        if (obj == null) return false;
        if (obj instanceof Boolean) return (Boolean) obj;
        return Boolean.parseBoolean(obj.toString());
    }

    public void handleHelp(ActionEvent actionEvent) {
    }

    public void handleSettings(ActionEvent actionEvent) {
    }
}