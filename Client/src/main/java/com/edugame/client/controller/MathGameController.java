package com.edugame.client.controller;

import com.edugame.client.network.ServerConnection;
import com.edugame.client.util.SceneManager;
import com.edugame.common.Protocol;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.util.Duration;

import java.io.IOException;
import java.util.*;

/**
 * MathGameController - Fixed Version with Real-time Broadcasting
 */
public class MathGameController {

    @FXML
    private BorderPane mainPane;
    @FXML
    private ImageView trackBackground;
    @FXML
    private Pane finishLine;

    // Cars
    @FXML
    private ImageView car1, car2, car3, car4;

    // Player Names
    @FXML
    private Label player1Name, player2Name, player3Name, player4Name;

    // Timer
    @FXML
    private Label timerLabel;

    // Question Panel
    @FXML
    private VBox questionPanel;
    @FXML
    private Label questionLabel;
    @FXML
    private ProgressBar timeProgressBar;
    @FXML
    private Button btnA, btnB, btnC, btnD;

    // Overlays
    @FXML
    private StackPane countdownOverlay;
    @FXML
    private Label countdownLabel;
    @FXML
    private StackPane resultOverlay;
    @FXML
    private Label resultTitle, resultMessage;
    @FXML
    private Button btnBackToLobby;

    // Game State
    private ServerConnection connection;
    private String roomId;
    private Timeline gameTimer;
    private Timeline questionTimer;
    private int remainingSeconds = 300;
    private int questionTimeLimit = 15;
    private double questionTimeRemaining = 15.0;

    // Player Data
    private Map<Integer, Double> playerPositions = new HashMap<>();
    private Map<Integer, Integer> playerScores = new HashMap<>();
    private Map<Integer, Integer> userIdToSlot = new HashMap<>();
    private Map<Integer, ImageView> playerCars = new HashMap<>();
    private Map<Integer, Label> playerNameLabels = new HashMap<>();
    private List<ImageView> cars = new ArrayList<>();
    private List<Label> playerLabels = new ArrayList<>();
    private int currentUserId;

    // Track player progress
    private Map<Integer, Integer> playerQuestionNumbers = new HashMap<>();
    private Map<Integer, Label> playerProgressLabels = new HashMap<>();
    private Map<Integer, Label> playerStreakLabels = new HashMap<>();

    // Question Data
    private int currentQuestionId;
    private int currentQuestionNumber;
    private int totalQuestions;
    private long questionStartTime;
    private boolean answered = false;
    private int correctAnswerIndex = -1;

    // Constants
    private static final double FINISH_LINE_X = 1050.0;
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

        System.out.println("✅ [MathGameController] Initialized");
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
                playerQuestionNumbers.put(userId, 0);

                // Map cars and labels
                ImageView car = cars.get(i);
                Label label = playerLabels.get(i);

                playerCars.put(userId, car);
                playerNameLabels.put(userId, label);

                if (userId == currentUserId) {
                    label.setText("#Me|" + fullName);
                    label.setStyle("-fx-font-weight: bold; -fx-text-fill: #FFD700;"); // Gold color
                } else {
                    label.setText(fullName);
                    label.setStyle("-fx-text-fill: white;");
                }

                label.setVisible(true);
                car.setVisible(true);
                car.setTranslateX(START_X);

                // Create progress label for each player
                Label progressLabel = createProgressLabel(fullName, i, userId == currentUserId);
                playerProgressLabels.put(userId, progressLabel);

                // Create streak label for each player
                Label streakLabel = createStreakLabel(i);
                playerStreakLabels.put(userId, streakLabel);
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

    // ✅ FIX: Create progress label with "Me" highlight
    private Label createProgressLabel(String playerName, int slot, boolean isCurrentUser) {
        String displayName = isCurrentUser ? "Me (" + playerName + ")" : playerName;
        Label label = new Label(displayName + ": 0/" + Protocol.QUESTIONS_PER_GAME);

        if (isCurrentUser) {
            label.setStyle("-fx-font-size: 12px; -fx-text-fill: #FFD700; -fx-font-weight: bold;");
        } else {
            label.setStyle("-fx-font-size: 12px; -fx-text-fill: white;");
        }

        label.setLayoutX(20);
        label.setLayoutY(100 + (slot * 25));

        if (mainPane != null) {
            mainPane.getChildren().add(label);
        }

        return label;
    }

    private Label createStreakLabel(int slot) {
        Label label = new Label("");
        label.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");
        label.setVisible(false);
        label.setLayoutX(200 + (slot * 150));
        label.setLayoutY(50);

        if (mainPane != null) {
            mainPane.getChildren().add(label);
        }

        return label;
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

    // ✅ FIX: Handle null options properly
    private void showQuestion(Map<String, Object> questionData) {
        Platform.runLater(() -> {
            try {
                answered = false;
                questionTimeRemaining = questionTimeLimit;

                currentQuestionId = getIntValue(questionData.get("questionId"));
                currentQuestionNumber = getIntValue(questionData.get("questionNumber"));
                totalQuestions = getIntValue(questionData.get("totalQuestions"));

                // ✅ FIX: Extract question from nested structure
                Object questionObj = questionData.get("question");
                String questionText = "";
                List<String> options = new ArrayList<>();

                if (questionObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> questionMap = (Map<String, Object>) questionObj;
                    questionText = getStringValue(questionMap.get("questionText"));

                    @SuppressWarnings("unchecked")
                    List<String> optionsList = (List<String>) questionMap.get("options");
                    if (optionsList != null) {
                        options = optionsList;
                    }
                } else {
                    // Fallback: try direct fields
                    questionText = getStringValue(questionData.get("questionText"));

                    @SuppressWarnings("unchecked")
                    List<String> optionsList = (List<String>) questionData.get("options");
                    if (optionsList != null) {
                        options = optionsList;
                    }
                }

                // ✅ FIX: Validate options before using
                if (options.isEmpty()) {
                    System.err.println("❌ [showQuestion] No options received!");
                    System.err.println("   Question data: " + questionData);
                    return;
                }

                questionLabel.setText("Q" + currentQuestionNumber + "/" + totalQuestions + ": " + questionText);

                List<Button> buttons = Arrays.asList(btnA, btnB, btnC, btnD);
                char[] letters = {'A', 'B', 'C', 'D'};

                for (int i = 0; i < buttons.size() && i < options.size(); i++) {
                    Button btn = buttons.get(i);
                    btn.setText(letters[i] + ") " + options.get(i));
                    btn.setDisable(false);
                    btn.getStyleClass().removeAll("answer-btn-correct", "answer-btn-wrong");
                }

                // Hide unused buttons
                for (int i = options.size(); i < buttons.size(); i++) {
                    buttons.get(i).setVisible(false);
                }

                // Update own progress
                playerQuestionNumbers.put(currentUserId, currentQuestionNumber);
                updateProgressLabel(currentUserId, currentQuestionNumber, totalQuestions);

                startQuestionTimer();
                questionStartTime = System.currentTimeMillis();

                System.out.println("❓ [MathGameController] Question " + currentQuestionNumber + "/" + totalQuestions);

            } catch (Exception e) {
                System.err.println("❌ [showQuestion] Error: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    private void startQuestionTimer() {
        timeProgressBar.setProgress(1.0);

        if (questionTimer != null) {
            questionTimer.stop();
        }

        questionTimer = new Timeline(new KeyFrame(Duration.millis(100), e -> {
            questionTimeRemaining -= 0.15;
            timeProgressBar.setProgress(questionTimeRemaining / questionTimeLimit);

            if (questionTimeRemaining <= 0 && !answered) {
                handleTimeout();
            }
        }));
        questionTimer.setCycleCount((int) (questionTimeLimit * 10));
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

        System.out.println("📝 [MathGameController] Submitted answer: " + (char) ('A' + answerIndex));
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

        showWrongAnswerVisual();
    }

    private void showWrongAnswerVisual() {
        // Tô đỏ toàn bộ (vì timeout không có lựa chọn)
        btnA.getStyleClass().add("answer-btn-wrong");
        btnB.getStyleClass().add("answer-btn-wrong");
        btnC.getStyleClass().add("answer-btn-wrong");
        btnD.getStyleClass().add("answer-btn-wrong");

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

    // ✅ IMPROVED: Animate positions with synchronized updates
    private void animatePositions(List<Map<String, Object>> positions) {
        Platform.runLater(() -> {
            ParallelTransition parallel = new ParallelTransition();

            for (Map<String, Object> pos : positions) {
                int userId = getIntValue(pos.get("userId"));
                double newPosition = getDoubleValue(pos.get("position"));
                boolean gotNitro = getBooleanValue(pos.get("gotNitro"));
                int currentQ = getIntValue(pos.get("currentQuestion"));
                int correctStreak = getIntValue(pos.get("correctStreak"));

                ImageView car = playerCars.get(userId);
                if (car == null) continue;

                double oldPos = playerPositions.getOrDefault(userId, START_X);

                TranslateTransition tt = new TranslateTransition(Duration.seconds(1), car);
                tt.setFromX(oldPos);
                tt.setToX(Math.min(newPosition, FINISH_LINE_X));
                tt.setInterpolator(Interpolator.EASE_OUT);

                parallel.getChildren().add(tt);

                playerPositions.put(userId, newPosition);

                // Update progress
                playerQuestionNumbers.put(userId, currentQ);
                updateProgressLabel(userId, currentQ, totalQuestions);

                // Update streak
                updateStreakLabel(userId, correctStreak);

                if (gotNitro) {
                    animateNitro(car);
                }
            }

            parallel.play();

            System.out.println("🏎️ [MathGameController] Animated positions for all players");
        });
    }

    // ✅ FIX: Update progress label with "Me" highlight
    private void updateProgressLabel(int userId, int currentQ, int total) {
        Label progressLabel = playerProgressLabels.get(userId);
        if (progressLabel != null) {
            Label nameLabel = playerNameLabels.get(userId);
            String name = nameLabel != null ? nameLabel.getText() : "Player";

            // Keep "Me" prefix for current user
            if (userId == currentUserId && !name.startsWith("Me")) {
                name = "Me (" + name + ")";
            }

            progressLabel.setText(name + ": " + currentQ + "/" + total);
        }
    }

    private void updateStreakLabel(int userId, int streak) {
        Label streakLabel = playerStreakLabels.get(userId);
        if (streakLabel != null) {
            if (streak > 0) {
                streakLabel.setText("🔥 " + streak);
                streakLabel.setVisible(true);
                streakLabel.setStyle("-fx-font-size: 14px; -fx-font-weight: bold; -fx-text-fill: orange;");
            } else {
                streakLabel.setVisible(false);
            }
        }
    }

    private void animateNitro(ImageView car) {
        ScaleTransition st = new ScaleTransition(Duration.millis(300), car);
        st.setToX(1.2);
        st.setToY(1.2);
        st.setCycleCount(2);
        st.setAutoReverse(true);
        st.play();

        DropShadow nitroGlow = new DropShadow();
        nitroGlow.setColor(Color.CYAN);
        nitroGlow.setRadius(30);
        nitroGlow.setSpread(0.7);
        car.setEffect(nitroGlow);

        PauseTransition pause = new PauseTransition(Duration.millis(600));
        pause.setOnFinished(e -> car.setEffect(null));
        pause.play();

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

                Label winnerLabel = playerNameLabels.get(winnerId);
                String winnerName = winnerLabel != null ? winnerLabel.getText() : "Unknown";

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
        connection.setGameStartCallback(data -> {
            Platform.runLater(() -> {
                System.out.println("🎮 Game starting...");
            });
        });

        connection.setGameQuestionCallback(this::showQuestion);

        connection.setAnswerResultCallback(data -> {
            boolean isCorrect = getBooleanValue(data.get("isCorrect"));
            showAnswerFeedback(isCorrect, -1);
        });

        // Position updates - SYNCHRONIZED for all players
        connection.setGameUpdateCallback(data -> {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> positions =
                    (List<Map<String, Object>>) data.get("positions");
            if (positions != null) {
                animatePositions(positions);
            }
        });

        // Handle when OTHER players answer
        connection.setPlayerAnsweredCallback(data -> {
            Platform.runLater(() -> {
                try {
                    int userId = getIntValue(data.get("userId"));

                    // Skip if it's the current user
                    if (userId == currentUserId) return;

                    boolean isCorrect = getBooleanValue(data.get("isCorrect"));
                    double position = getDoubleValue(data.get("position"));
                    int score = getIntValue(data.get("score"));
                    boolean gotNitro = getBooleanValue(data.get("gotNitro"));
                    long timeTaken = getLongValue(data.get("timeTaken"));

                    // Show visual feedback
                    ImageView playerCar = playerCars.get(userId);
                    if (playerCar != null) {
                        if (gotNitro) {
                            showNitroEffect(playerCar);
                        } else if (isCorrect) {
                            showCorrectEffect(playerCar);
                        } else {
                            showWrongEffect(playerCar);
                        }

                        if (isCorrect) {
                            showTimePopup(playerCar, timeTaken);
                        }
                    }

                    // Update position immediately
                    updateSinglePlayerPosition(userId, position, score);

                    System.out.println("📢 Player " + userId + " answered: " +
                            (isCorrect ? "✅" : "❌") +
                            (gotNitro ? " 🚀 NITRO!" : ""));

                } catch (Exception e) {
                    System.err.println("❌ Error handling player answered: " + e.getMessage());
                }
            });
        });

        // Handle when OTHER players progress
        connection.setPlayerProgressCallback(data -> {
            Platform.runLater(() -> {
                try {
                    int userId = getIntValue(data.get("userId"));

                    // Skip if it's the current user
                    if (userId == currentUserId) return;

                    int currentQuestion = getIntValue(data.get("currentQuestion"));
                    int totalQuestions = getIntValue(data.get("totalQuestions"));

                    // Update progress
                    playerQuestionNumbers.put(userId, currentQuestion);
                    updateProgressLabel(userId, currentQuestion, totalQuestions);

                    System.out.println("📢 Player " + userId + " -> Question " +
                            currentQuestion + "/" + totalQuestions);

                } catch (Exception e) {
                    System.err.println("❌ Error handling player progress: " + e.getMessage());
                }
            });
        });

        connection.setGameEndCallback(this::showGameResults);

        System.out.println("✅ [MathGameController] Callbacks registered");
    }

    private void updateSinglePlayerPosition(int userId, double position, int score) {
        ImageView car = playerCars.get(userId);
        if (car == null) return;

        double oldPos = playerPositions.getOrDefault(userId, START_X);
        playerPositions.put(userId, position);
        playerScores.put(userId, score);

        TranslateTransition tt = new TranslateTransition(Duration.millis(800), car);
        tt.setFromX(oldPos);
        tt.setToX(Math.min(position, FINISH_LINE_X));
        tt.setInterpolator(Interpolator.EASE_OUT);
        tt.play();
    }

    // Visual effects
    private void showNitroEffect(Node node) {
        ScaleTransition scale = new ScaleTransition(Duration.millis(300), node);
        scale.setToX(1.3);
        scale.setToY(1.3);
        scale.setAutoReverse(true);
        scale.setCycleCount(2);
        scale.play();

        DropShadow nitroGlow = new DropShadow();
        nitroGlow.setColor(Color.CYAN);
        nitroGlow.setRadius(30);
        nitroGlow.setSpread(0.7);
        node.setEffect(nitroGlow);

        PauseTransition pause = new PauseTransition(Duration.millis(600));
        pause.setOnFinished(e -> node.setEffect(null));
        pause.play();
    }

    private void showCorrectEffect(Node node) {
        DropShadow correctGlow = new DropShadow();
        correctGlow.setColor(Color.LIGHTGREEN);
        correctGlow.setRadius(20);
        correctGlow.setSpread(0.5);
        node.setEffect(correctGlow);

        PauseTransition pause = new PauseTransition(Duration.millis(400));
        pause.setOnFinished(e -> node.setEffect(null));
        pause.play();
    }

    private void showWrongEffect(Node node) {
        DropShadow wrongGlow = new DropShadow();
        wrongGlow.setColor(Color.RED);
        wrongGlow.setRadius(15);
        node.setEffect(wrongGlow);

        TranslateTransition shake = new TranslateTransition(Duration.millis(50), node);
        shake.setFromX(0);
        shake.setToX(5);
        shake.setCycleCount(6);
        shake.setAutoReverse(true);
        shake.play();

        PauseTransition pause = new PauseTransition(Duration.millis(400));
        pause.setOnFinished(e -> node.setEffect(null));
        pause.play();
    }

    private void showTimePopup(Node node, long timeTaken) {
        Label timeLabel = new Label(String.format("%.1fs", timeTaken / 1000.0));
        timeLabel.setStyle("-fx-background-color: rgba(0,0,0,0.7); " +
                "-fx-text-fill: white; " +
                "-fx-padding: 5px 10px; " +
                "-fx-font-size: 12px; " +
                "-fx-border-radius: 5px; " +
                "-fx-background-radius: 5px;");

        Bounds bounds = node.localToScene(node.getBoundsInLocal());
        timeLabel.setLayoutX(bounds.getMinX());
        timeLabel.setLayoutY(bounds.getMinY() - 30);

        if (mainPane != null) {
            mainPane.getChildren().add(timeLabel);

            FadeTransition fade = new FadeTransition(Duration.seconds(2), timeLabel);
            fade.setFromValue(1.0);
            fade.setToValue(0.0);
            fade.setOnFinished(e -> mainPane.getChildren().remove(timeLabel));
            fade.play();
        }
    }

    private void cleanup() {
        if (gameTimer != null) gameTimer.stop();
        if (questionTimer != null) questionTimer.stop();

        connection.clearGameCallbacks();

        System.out.println("🧹 [MathGameController] Cleaned up");
    }

    // Helper methods
    private int getIntValue(Object obj) {
        if (obj == null) return 0;
        if (obj instanceof Number) return ((Number) obj).intValue();
        try {
            return Integer.parseInt(obj.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private long getLongValue(Object obj) {
        if (obj == null) return 0L;
        if (obj instanceof Number) return ((Number) obj).longValue();
        try {
            return Long.parseLong(obj.toString());
        } catch (NumberFormatException e) {
            return 0L;
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

    @FXML
    public void handleHelp(ActionEvent actionEvent) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Game Help");
        alert.setHeaderText("How to Play");
        alert.setContentText(
                "🎯 Goal: Answer questions correctly to move your car forward!\n\n" +
                        "⚡ Nitro Boost: Be the fastest to answer correctly!\n" +
                        "🔥 Streak Bonus: Get extra points for consecutive correct answers!\n" +
                        "💥 Wrong Streak: Too many wrong answers will push you back!\n\n" +
                        "Good luck! 🏁"
        );
        alert.showAndWait();
    }

    @FXML
    public void handleSettings(ActionEvent actionEvent) {
        // Settings implementation
    }
}