package com.edugame.client.controller;

import com.edugame.client.model.User;
import com.edugame.client.network.ServerConnection;
import com.edugame.client.util.SceneManager;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class LeaderboardController {

    @FXML private VBox leaderboardContainer;
    @FXML private ProgressIndicator loadingIndicator;

    @FXML private ToggleButton btnAllSubjects;
    @FXML private ToggleButton btnMath;
    @FXML private ToggleButton btnEnglish;
    @FXML private ToggleButton btnLiterature;

    @FXML private HBox currentUserRank;
    @FXML private Text txtCurrentRank;
    @FXML private Text txtCurrentUsername;
    @FXML private Text txtCurrentScore;

    private ServerConnection serverConnection;
    private String currentFilter = "total";
    private static final int LEADERBOARD_LIMIT = 50;

    private Gson gson = new Gson();
    private User currentUser;

    private Thread messageListenerThread;
    private volatile boolean isListening = false;

    // ToggleGroup để đảm bảo chỉ 1 button được chọn
    private ToggleGroup subjectFilterGroup;

    @FXML
    public void initialize() {
        System.out.println("LeaderboardController initializing...");

        serverConnection = ServerConnection.getInstance();
        currentUser = createCurrentUser();

        // ✅ Tạo ToggleGroup và gán cho các button
        setupToggleGroup();

        // Chỉ khởi động listener một lần duy nhất
        if (!isListening) {
            startMessageListener();
        }

        // Load leaderboard ban đầu
        loadLeaderboard("total");
    }

    /**
     * Setup ToggleGroup cho filter buttons
     */
    private void setupToggleGroup() {
        subjectFilterGroup = new ToggleGroup();

        btnAllSubjects.setToggleGroup(subjectFilterGroup);
        btnMath.setToggleGroup(subjectFilterGroup);
        btnEnglish.setToggleGroup(subjectFilterGroup);
        btnLiterature.setToggleGroup(subjectFilterGroup);

        // Chọn mặc định
        btnAllSubjects.setSelected(true);

        // ✅ Ngăn không cho bỏ chọn tất cả (luôn phải có 1 button được chọn)
        subjectFilterGroup.selectedToggleProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue == null) {
                oldValue.setSelected(true);
            }
        });
    }

    private User createCurrentUser() {
        if (serverConnection.getCurrentUserId() > 0) {
            User user = new User();
            user.setUserId(serverConnection.getCurrentUserId());
            user.setUsername(serverConnection.getCurrentUsername());
            user.setFullName(serverConnection.getCurrentFullName());
            user.setTotalScore(serverConnection.getTotalScore());
            return user;
        }
        return null;
    }

    private void startMessageListener() {
        if (isListening) {
            System.out.println("⚠ Listener already running, skipping...");
            return;
        }

        isListening = true;
        messageListenerThread = new Thread(() -> {
            System.out.println("✓ Leaderboard listener started");

            while (isListening && serverConnection.isConnected()) {
                try {
                    String message = serverConnection.receiveMessage();
                    if (message != null && !message.trim().isEmpty()) {
                        handleServerResponse(message);
                    }
                } catch (IOException e) {
                    if (isListening) {
                        System.err.println("✗ Error receiving message: " + e.getMessage());
                    }
                    break;
                }
            }

            System.out.println("✓ Leaderboard listener stopped");
        });

        messageListenerThread.setDaemon(true);
        messageListenerThread.start();
    }

    private void handleServerResponse(String message) {
        try {
            JsonObject response = gson.fromJson(message, JsonObject.class);

            if (!response.has("type")) {
                return;
            }

            String type = response.get("type").getAsString();

            if ("GET_LEADERBOARD".equals(type)) {
                boolean success = response.get("success").getAsBoolean();

                if (success && response.has("leaderboard")) {
                    JsonArray leaderboardArray = response.getAsJsonArray("leaderboard");
                    List<User> leaderboard = new ArrayList<>();

                    for (JsonElement element : leaderboardArray) {
                        JsonObject userObj = element.getAsJsonObject();
                        User user = new User();
                        user.setUserId(userObj.get("userId").getAsInt());
                        user.setUsername(userObj.get("username").getAsString());
                        user.setFullName(userObj.get("fullName").getAsString());
                        user.setTotalScore(userObj.get("totalScore").getAsInt());
                        user.setOnline(userObj.get("isOnline").getAsBoolean());

                        if (userObj.has("avatarUrl") && !userObj.get("avatarUrl").isJsonNull()) {
                            user.setAvatarUrl(userObj.get("avatarUrl").getAsString());
                        }

                        leaderboard.add(user);
                    }

                    System.out.println("✓ Leaderboard received: " + leaderboard.size() + " users");

                    Platform.runLater(() -> {
                        displayLeaderboard(leaderboard);
                        showLoading(false);
                    });
                } else {
                    Platform.runLater(() -> {
                        showError("Không thể tải bảng xếp hạng");
                        showLoading(false);
                    });
                }
            }

        } catch (Exception e) {
            System.err.println("✗ Error parsing server response: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void displayLeaderboard(List<User> users) {
        leaderboardContainer.getChildren().clear();

        if (users.isEmpty()) {
            Text emptyText = new Text("Chưa có dữ liệu xếp hạng");
            emptyText.getStyleClass().add("empty-text");
            HBox emptyBox = new HBox(emptyText);
            emptyBox.setAlignment(Pos.CENTER);
            emptyBox.setPrefHeight(200);
            leaderboardContainer.getChildren().add(emptyBox);
            return;
        }

        // Tìm rank của user hiện tại
        int currentUserRankPosition = -1;
        for (int i = 0; i < users.size(); i++) {
            if (currentUser != null && users.get(i).getUserId() == currentUser.getUserId()) {
                currentUserRankPosition = i + 1;
                break;
            }
        }

        // Hiển thị rank của user hiện tại
        if (currentUserRankPosition > 0) {
            displayCurrentUserRank(currentUserRankPosition, users.get(currentUserRankPosition - 1).getTotalScore());
        }

        // Hiển thị toàn bộ bảng xếp hạng
        for (int i = 0; i < users.size(); i++) {
            User user = users.get(i);
            int rank = i + 1;
            HBox rankItem = createRankItem(rank, user);
            leaderboardContainer.getChildren().add(rankItem);
        }
    }

    private HBox createRankItem(int rank, User user) {
        HBox rankItem = new HBox();
        rankItem.setSpacing(15);
        rankItem.setAlignment(Pos.CENTER_LEFT);
        rankItem.setPadding(new Insets(12, 15, 12, 15));

        // Style theo rank
        if (rank == 1) {
            rankItem.getStyleClass().addAll("rank-item", "rank-1");
        } else if (rank == 2) {
            rankItem.getStyleClass().addAll("rank-item", "rank-2");
        } else if (rank == 3) {
            rankItem.getStyleClass().addAll("rank-item", "rank-3");
        } else {
            rankItem.getStyleClass().add("rank-item");
        }

        // Highlight user hiện tại
        if (currentUser != null && user.getUserId() == currentUser.getUserId()) {
            rankItem.getStyleClass().add("rank-current-highlight");
        }

        // Số thứ hạng
        Text rankText = new Text(String.valueOf(rank));
        rankText.getStyleClass().add("rank-number");
        if (rank == 1) rankText.getStyleClass().add("gold");
        else if (rank == 2) rankText.getStyleClass().add("silver");
        else if (rank == 3) rankText.getStyleClass().add("bronze");

        // Thông tin user
        VBox userInfo = new VBox(3);
        HBox.setHgrow(userInfo, javafx.scene.layout.Priority.ALWAYS);

        Text nameText = new Text(user.getFullName() != null && !user.getFullName().isEmpty()
                ? user.getFullName() : user.getUsername());
        nameText.getStyleClass().add("rank-name");

        Text usernameText = new Text("@" + user.getUsername());
        usernameText.getStyleClass().add("rank-username");

        userInfo.getChildren().addAll(nameText, usernameText);

        // Điểm số
        Text scoreText = new Text(formatScore(user.getTotalScore()));
        scoreText.getStyleClass().add("rank-score");

        // Trạng thái online
        Text onlineStatus = new Text("●");
        onlineStatus.getStyleClass().add(user.isOnline() ? "online-status" : "offline-status");

        rankItem.getChildren().addAll(rankText, userInfo, scoreText, onlineStatus);

        return rankItem;
    }

    private void displayCurrentUserRank(int rank, int score) {
        if (currentUserRank != null && txtCurrentRank != null &&
                txtCurrentUsername != null && txtCurrentScore != null) {

            txtCurrentRank.setText(String.valueOf(rank));
            txtCurrentUsername.setText(currentUser.getFullName() != null && !currentUser.getFullName().isEmpty()
                    ? currentUser.getFullName() : currentUser.getUsername());
            txtCurrentScore.setText(formatScore(score));
            currentUserRank.setVisible(true);
        }
    }

    private void loadLeaderboard(String subject) {
        showLoading(true);

        try {
            java.util.Map<String, Object> request = new java.util.HashMap<>();
            request.put("type", "GET_LEADERBOARD");
            request.put("subject", subject);
            request.put("limit", LEADERBOARD_LIMIT);

            serverConnection.sendJson(request);

            System.out.println("✓ Sent leaderboard request: " + subject);

        } catch (Exception e) {
            e.printStackTrace();
            Platform.runLater(() -> {
                showError("Lỗi kết nối đến server");
                showLoading(false);
            });
        }
    }

    @FXML
    private void handleBack() {
        cleanup();
        try {
            SceneManager.getInstance().switchScene("home.fxml");
        } catch (Exception e) {
            e.printStackTrace();
            showError("Không thể quay về trang chủ");
        }
    }

    @FXML
    private void handleRefresh() {
        loadLeaderboard(currentFilter);
    }

    @FXML
    private void handleFilterAll() {
        currentFilter = "total";
        loadLeaderboard("total");
    }

    @FXML
    private void handleFilterMath() {
        currentFilter = "math";
        loadLeaderboard("math");
    }

    @FXML
    private void handleFilterEnglish() {
        currentFilter = "english";
        loadLeaderboard("english");
    }

    @FXML
    private void handleFilterLiterature() {
        currentFilter = "literature";
        loadLeaderboard("literature");
    }

    private String formatScore(int score) {
        return String.format("%,d điểm", score).replace(",", ".");
    }

    private void showLoading(boolean show) {
        if (loadingIndicator != null) {
            Platform.runLater(() -> loadingIndicator.setVisible(show));
        }
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

    public void cleanup() {
        System.out.println("✓ Cleaning up LeaderboardController...");
        isListening = false;

        if (messageListenerThread != null && messageListenerThread.isAlive()) {
            messageListenerThread.interrupt();
        }
    }
}