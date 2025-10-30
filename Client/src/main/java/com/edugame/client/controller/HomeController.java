package com.edugame.client.controller;

import com.edugame.client.model.User;
import com.edugame.client.network.ServerConnection;
import com.edugame.client.util.SceneManager;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.util.Duration;
import javafx.geometry.Pos;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static javafx.scene.input.KeyCode.ENTER;

public class HomeController {

    // User info
    @FXML private ImageView userAvatar;
    @FXML private Text userNameText;
    @FXML private Text pointsText;
    @FXML private Text coinsText;

    // Buttons
    @FXML private Button settingsButton;
    @FXML private Button logoutButton;
    @FXML private Button trainingButton;
    @FXML private Button quickMatchButton;
    @FXML private Button roomButton;
    @FXML private Button bossButton;

    // Leaderboard
    @FXML private VBox leaderboardList;

    // Chat
    @FXML private ScrollPane chatScrollPane;
    @FXML private VBox chatMessagesContainer;
    @FXML private TextField chatInputField;
    @FXML private Button sendChatButton;
    @FXML private VBox globalChatBox;
    @FXML private Button toggleChatButton;
    @FXML private Button emojiButton;

    private User currentUser;
    private Gson gson = new Gson();
    private ServerConnection serverConnection;
    private boolean chatExpanded = true;
    private boolean isInitialized = false;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @FXML
    public void initialize() {
        if (isInitialized) {
            System.out.println("⚠️ HomeController already initialized");
            return;
        }

        System.out.println("🚀 HomeController initializing...");
        serverConnection = ServerConnection.getInstance();

        chatInputField.setOnKeyPressed(event -> {
            if (event.getCode() == ENTER && !event.isShiftDown()) {
                event.consume();
                handleSendMessage();
            }
        });

        setupButtonEffects();
        setupChatScroll();

        // Setup chat callback
        setupChatCallback();

        // Load data in background
        loadDataInBackground();
        loadUserData();

        isInitialized = true;
        System.out.println("✅ HomeController initialized");
    }

    /**
     * Load data in proper sequence with delays
     */
    private void loadDataInBackground() {
        executor.submit(() -> {
            try {
                // 1. Load user data first (immediate)
                Platform.runLater(this::loadUserData);
                Thread.sleep(100);

                // 2. Load leaderboard (with callback)
                Platform.runLater(this::loadLeaderboardData);
                Thread.sleep(200);

                // 3. Load daily quests
                Platform.runLater(this::loadDailyQuests);

                System.out.println("✅ All data loading initiated");

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("❌ Background loading interrupted");
            }
        });
    }

    /**
     * Called when scene is shown again (e.g., returning from profile)
     */
    public void onSceneShown() {
        System.out.println("🔄 HomeController scene shown");
        loadUserData();
        // Reload leaderboard
        loadLeaderboardData();

        // Ensure chat callback is set
        setupChatCallback();
    }

    private void setupChatScroll() {
        if (chatMessagesContainer != null && chatScrollPane != null) {
            chatMessagesContainer.heightProperty().addListener((obs, oldVal, newVal) -> {
                chatScrollPane.setVvalue(1.0);
            });
        }
    }

    /**
     * Setup chat message callback
     */
    private void setupChatCallback() {
        serverConnection.setChatCallback(json -> {
            String type = json.get("type").getAsString();

            if ("GLOBAL_CHAT".equals(type)) {
                String username = json.get("username").getAsString();
                String message = json.get("message").getAsString();

                // Don't show own messages (already displayed when sent)
                if (!username.equals(serverConnection.getCurrentUsername())) {
                    addChatMessage(username, message, false);
                }
            } else if ("SYSTEM_MESSAGE".equals(type)) {
                String message = json.get("message").getAsString();
                addSystemMessage(message);
            }
        });
    }

    /** ---------------- USER DATA ---------------- */
    private void loadUserData() {
        if (serverConnection != null && serverConnection.isConnected()) {
            String fullName = serverConnection.getCurrentFullName();
            int level = serverConnection.getCurrentLevel();
            int totalScore = serverConnection.getTotalScore();
            String avatarFileName = serverConnection.getCurrentAvatarUrl();

            userNameText.setText(fullName != null ? fullName : "Người chơi");
            pointsText.setText("Điểm " + formatNumber(totalScore));

            loadAvatar(avatarFileName);

            System.out.println("✅ User data loaded");
        } else {
            userNameText.setText("Người chơi");
            pointsText.setText("Điểm 0");
            loadAvatar("avatar4.png");
        }
    }

    private void loadAvatar(String avatarFileName) {
        if (userAvatar == null) return;

        try {
            Image avatarImage;

            if (avatarFileName == null || avatarFileName.isBlank()) {
                // 🔹 Không có ảnh → dùng mặc định
                avatarImage = new Image(getClass().getResourceAsStream("/images/avatars/avatar4.png"));
            }
            else if (avatarFileName.startsWith("http")) {
                // 🔹 URL từ internet (ImgBB, Firebase...)
                avatarImage = new Image(avatarFileName, true);
            }
            else if (avatarFileName.contains(File.separator) || new File(avatarFileName).isAbsolute()) {
                // 🔹 ĐÂY LÀ FIX CHÍNH: File từ máy tính (đường dẫn đầy đủ)
                File avatarFile = new File(avatarFileName);

                if (avatarFile.exists() && avatarFile.isFile()) {
                    // ✅ File tồn tại → load trực tiếp
                    avatarImage = new Image(avatarFile.toURI().toString(), true);
                    System.out.println("✅ Loaded local file: " + avatarFileName);
                } else {
                    // ❌ File không tồn tại → fallback
                    System.err.println("⚠️ Local file not found: " + avatarFileName);
                    avatarImage = new Image(getClass().getResourceAsStream("/images/avatars/avatar4.png"));
                }
            }
            else {
                // 🔹 Avatar mặc định từ resources (avatar1.png, avatar2.png...)
                String resourcePath = "/images/avatars/" + avatarFileName;
                var inputStream = getClass().getResourceAsStream(resourcePath);

                if (inputStream != null) {
                    avatarImage = new Image(inputStream);
                } else {
                    System.err.println("⚠️ Resource not found: " + resourcePath);
                    avatarImage = new Image(getClass().getResourceAsStream("/images/avatars/avatar4.png"));
                }
            }

            userAvatar.setImage(avatarImage);
            userAvatar.setPreserveRatio(true);
            userAvatar.setSmooth(true);

        } catch (Exception e) {
            System.err.println("❌ Error loading avatar: " + e.getMessage());
            e.printStackTrace();

            try {
                Image defaultAvatar = new Image(getClass().getResourceAsStream("/images/avatars/avatar4.png"));
                userAvatar.setImage(defaultAvatar);
            } catch (Exception ex) {
                System.err.println("❌ Failed to load default avatar fallback");
            }
        }
    }


    /** ---------------- LEADERBOARD ---------------- */
    private void loadLeaderboardData() {
        if (serverConnection == null || !serverConnection.isConnected()) {
            System.err.println("⚠️ Cannot load leaderboard - not connected");
            return;
        }

        System.out.println("📊 Loading leaderboard...");

        // Use callback-based approach
        serverConnection.getLeaderboard(50, leaderboardData -> {
            Platform.runLater(() -> {
                if (leaderboardData != null && !leaderboardData.isEmpty()) {
                    // Convert to User list
                    List<User> users = new ArrayList<>();
                    for (Map<String, Object> data : leaderboardData) {
                        User user = new User();
                        user.setUserId((Integer) data.get("userId"));
                        user.setUsername((String) data.get("username"));
                        user.setFullName((String) data.get("fullName"));
                        user.setTotalScore((Integer) data.get("totalScore"));
                        user.setOnline((Boolean) data.get("isOnline"));

                        if (data.containsKey("avatarUrl")) {
                            user.setAvatarUrl((String) data.get("avatarUrl"));
                        }

                        users.add(user);
                    }

                    displayLeaderboard(users);
                    System.out.println("✅ Leaderboard displayed: " + users.size() + " users");
                } else {
                    System.err.println("⚠️ No leaderboard data received");
                }
            });
        });
    }

    private void displayLeaderboard(List<User> users) {
        if (leaderboardList == null || users.isEmpty()) {
            return;
        }

        leaderboardList.getChildren().clear();

        // Find current user rank
        int currentUserRank = -1;
        if (currentUser != null) {
            for (int i = 0; i < users.size(); i++) {
                if (users.get(i).getUserId() == currentUser.getUserId()) {
                    currentUserRank = i + 1;
                    break;
                }
            }
        }

        // Display Top 3
        int topCount = Math.min(3, users.size());
        for (int i = 0; i < topCount; i++) {
            User user = users.get(i);
            int rank = i + 1;
            HBox rankItem = createRankItem(rank, user, false);
            leaderboardList.getChildren().add(rankItem);
        }

        // Display current user if not in top 3
        if (currentUserRank > 3) {
            HBox currentRankItem = createRankItem(currentUserRank,
                    users.get(currentUserRank - 1), true);
            leaderboardList.getChildren().add(currentRankItem);
        }
    }

    private HBox createRankItem(int rank, User user, boolean isCurrentUser) {
        HBox rankItem = new HBox();
        rankItem.setSpacing(12);
        rankItem.setAlignment(Pos.CENTER_LEFT);
        rankItem.getStyleClass().add("rank-item");

        if (isCurrentUser) {
            rankItem.getStyleClass().add("rank-current");
        } else if (rank == 1) {
            rankItem.getStyleClass().add("rank-1");
        } else if (rank == 2) {
            rankItem.getStyleClass().add("rank-2");
        } else if (rank == 3) {
            rankItem.getStyleClass().add("rank-3");
        }

        // Rank number
        Text rankText = new Text(String.valueOf(rank));
        rankText.getStyleClass().add("rank-number");
        if (rank == 1) {
            rankText.getStyleClass().add("gold");
        } else if (rank == 2) {
            rankText.getStyleClass().add("silver");
        } else if (rank == 3) {
            rankText.getStyleClass().add("bronze");
        }

        // User info
        VBox userInfo = new VBox(2);
        HBox.setHgrow(userInfo, javafx.scene.layout.Priority.ALWAYS);

        String displayName = isCurrentUser ? "Bạn" :
                (user.getFullName() != null && !user.getFullName().isEmpty()
                        ? user.getFullName() : user.getUsername());

        Text nameText = new Text(displayName);
        nameText.getStyleClass().add("rank-name");

        Text scoreText = new Text(formatScore(user.getTotalScore()));
        scoreText.getStyleClass().add("rank-score");

        userInfo.getChildren().addAll(nameText, scoreText);
        rankItem.getChildren().addAll(rankText, userInfo);

        // Online status
        if (!isCurrentUser && user.isOnline()) {
            Text onlineStatus = new Text("●");
            onlineStatus.setStyle("-fx-fill: green;");
            onlineStatus.getStyleClass().add("online-status");
            rankItem.getChildren().add(onlineStatus);
        }

        return rankItem;
    }

    private String formatScore(int score) {
        return String.format("%,d điểm", score).replace(",", ".");
    }

    /** ---------------- DAILY QUEST ---------------- */
    private void loadDailyQuests() {
        System.out.println("📋 Daily quests loaded");
    }

    /** ---------------- CHAT ---------------- */
    @FXML
    private void handleToggleChat() {
        chatExpanded = !chatExpanded;

        chatScrollPane.setVisible(chatExpanded);
        chatScrollPane.setManaged(chatExpanded);
        chatInputField.setVisible(chatExpanded);
        chatInputField.setManaged(chatExpanded);
        sendChatButton.setVisible(chatExpanded);
        sendChatButton.setManaged(chatExpanded);

        toggleChatButton.setText(chatExpanded ? "−" : "+");
        globalChatBox.setPrefHeight(chatExpanded ? 350 : 50);
    }

    @FXML
    private void handleSendMessage() {
        if (chatInputField == null || chatMessagesContainer == null) return;

        String message = chatInputField.getText().trim();
        if (message.isEmpty()) return;

        ServerConnection server = ServerConnection.getInstance();
        if (!server.isConnected()) {
            addSystemMessage("Không thể gửi tin nhắn. Chưa kết nối server.");
            return;
        }

        // Send message to server
        Map<String, Object> request = new HashMap<>();
        request.put("type", "GLOBAL_CHAT");
        request.put("username", server.getCurrentUsername());
        request.put("message", message);

        server.sendJson(request);

        // Display own message
        addChatMessage(server.getCurrentUsername(), message, true);
        chatInputField.clear();
    }

    @FXML
    private void handleShowEmoji() {
        if (emojiButton == null || chatInputField == null) return;

        javafx.stage.Popup emojiPopup = new javafx.stage.Popup();

        FlowPane emojiPane = new FlowPane(5, 5);
        emojiPane.setPadding(new Insets(10));
        emojiPane.setAlignment(Pos.CENTER_LEFT);
        emojiPane.setStyle("""
        -fx-background-color: white;
        -fx-border-color: #ddd;
        -fx-border-width: 1;
        -fx-border-radius: 12;
        -fx-background-radius: 12;
        -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 8, 0, 0, 2);
        """);
        emojiPane.setPrefSize(320, 220);

        String[] emojis = {
                "😀","😃","😄","😁","😆","😅","🤣","😂","🙂","🙃",
                "😉","😊","😇","🥰","😍","🤩","😘","😗","😚","😙",
                "😋","😛","😜","🤪","😝","🤑","🤗","🤭","🤫","🤔",
                "🤐","🤨","😐","😑","😶","😏","😒","🙄","😬","😮",
                "😯","😲","😳","🥺","😢","😭","😤","😠","😡","🤬",
                "😈","👿","💀","💩","🤡","👻","👽","🤖","❤","🧡",
                "💛","💚","💙","💜","🖤","🤍","🤎","💔","❣","💕",
                "💞","💓","💗","💖","💘","💝","👍","👎","👌","✌",
                "🤞","🤟","🤘","🤙","👏","🙌","👐","🤲","🙏","💪",
                "🎉","🎊","🎁","🎈","🎂","🎀","🏆","🥇","🥈","🥉"
        };

        for (String emoji : emojis) {
            StackPane emojiContainer = new StackPane();
            emojiContainer.setPrefSize(42, 42);
            emojiContainer.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-background-radius: 8;");

            ImageView emojiImage = createEmojiImageView(emoji, 28);
            if (emojiImage != null) {
                emojiContainer.getChildren().add(emojiImage);
            } else {
                Label emojiLabel = new Label(emoji);
                emojiLabel.setStyle("-fx-font-size: 26px;");
                emojiContainer.getChildren().add(emojiLabel);
            }

            emojiContainer.setOnMouseEntered(e ->
                    emojiContainer.setStyle("-fx-background-color: #f0f2f5; -fx-cursor: hand; -fx-background-radius: 8;"));

            emojiContainer.setOnMouseExited(e ->
                    emojiContainer.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-background-radius: 8;"));

            emojiContainer.setOnMouseClicked(e -> {
                int savedCaretPos = chatInputField.getCaretPosition();
                String currentText = chatInputField.getText();

                String beforeCaret = currentText.substring(0, savedCaretPos);
                String afterCaret = currentText.substring(savedCaretPos);
                String newText = beforeCaret + afterCaret  + emoji;

                chatInputField.setText(newText);
                int newCaretPos = savedCaretPos + emoji.length();

                emojiPopup.hide();

                Platform.runLater(() -> {
                    chatInputField.requestFocus();
                    chatInputField.positionCaret(newCaretPos);
                    chatInputField.deselect();
                });
            });

            emojiPane.getChildren().add(emojiContainer);
        }

        ScrollPane scrollPane = new ScrollPane(emojiPane);
        scrollPane.setStyle("-fx-background: white; -fx-background-color: white; -fx-border-color: transparent;");
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefViewportHeight(220);
        emojiPopup.getContent().add(scrollPane);
        emojiPopup.setAutoHide(true);

        javafx.geometry.Point2D point = emojiButton.localToScreen(0, 0);
        emojiPopup.show(emojiButton, point.getX(), point.getY() - 230);
    }

    private void addChatMessage(String username, String message, boolean isSelf) {
        if (chatMessagesContainer == null) return;

        Platform.runLater(() -> {
            HBox messageContainer = new HBox(8);
            messageContainer.setAlignment(isSelf ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
            messageContainer.setPadding(new Insets(4, 0, 4, 0));

            VBox messageBox = new VBox(4);
            messageBox.setMaxWidth(280);
            messageBox.setStyle(isSelf ?
                    "-fx-background-color: #0084ff; -fx-background-radius: 18; -fx-padding: 10 14 10 14;" :
                    "-fx-background-color: #e4e6eb; -fx-background-radius: 18; -fx-padding: 10 14 10 14;");

            // Header with username and time
            HBox headerBox = new HBox(6);
            headerBox.setAlignment(Pos.CENTER_LEFT);

            Text usernameText = new Text(username);
            usernameText.setStyle(isSelf ?
                    "-fx-fill: white; -fx-font-weight: bold; -fx-font-size: 12px;" :
                    "-fx-fill: #050505; -fx-font-weight: bold; -fx-font-size: 12px;");

            String timeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
            Text timeText = new Text(timeStr);
            timeText.setStyle(isSelf ?
                    "-fx-fill: rgba(255,255,255,0.7); -fx-font-size: 10px;" :
                    "-fx-fill: #65676b; -fx-font-size: 10px;");

            headerBox.getChildren().addAll(usernameText, timeText);

            if (!isSelf) {
                Text onlineDot = new Text("●");
                onlineDot.setStyle("-fx-fill: #31a24c; -fx-font-size: 8px;");
                headerBox.getChildren().add(onlineDot);
            }

            // Message content with emoji support
            FlowPane messageContent = parseMessageWithEmojiImages(message, isSelf);
            messageContent.setMaxWidth(250);
            messageContent.setHgap(2);
            messageContent.setVgap(2);

            messageBox.getChildren().addAll(headerBox, messageContent);
            messageContainer.getChildren().add(messageBox);

            chatMessagesContainer.getChildren().add(messageContainer);

            // Limit messages
            if (chatMessagesContainer.getChildren().size() > 50) {
                chatMessagesContainer.getChildren().remove(0);
            }

            // Auto scroll
            chatScrollPane.layout();
            chatScrollPane.setVvalue(1.0);
        });
    }

    private FlowPane parseMessageWithEmojiImages(String message, boolean isSelf) {
        FlowPane flowPane = new FlowPane();
        flowPane.setStyle("-fx-background-color: transparent;");

        StringBuilder textBuffer = new StringBuilder();

        for (int i = 0; i < message.length(); ) {
            int codePoint = message.codePointAt(i);
            int charCount = Character.charCount(codePoint);
            String currentChar = message.substring(i, i + charCount);

            if (isEmojiCodePoint(codePoint)) {
                if (textBuffer.length() > 0) {
                    Text textNode = new Text(textBuffer.toString());
                    textNode.setStyle(String.format(
                            "-fx-font-size: 14px; -fx-fill: %s;",
                            isSelf ? "white" : "#050505"
                    ));
                    flowPane.getChildren().add(textNode);
                    textBuffer = new StringBuilder();
                }

                ImageView emojiView = createEmojiImageView(currentChar, 20);
                if (emojiView != null) {
                    flowPane.getChildren().add(emojiView);
                } else {
                    Text emojiText = new Text(currentChar);
                    emojiText.setStyle("-fx-font-size: 18px;");
                    flowPane.getChildren().add(emojiText);
                }
            } else {
                textBuffer.append(currentChar);
            }

            i += charCount;
        }

        if (textBuffer.length() > 0) {
            Text textNode = new Text(textBuffer.toString());
            textNode.setStyle(String.format(
                    "-fx-font-size: 14px; -fx-fill: %s;",
                    isSelf ? "white" : "#050505"
            ));
            flowPane.getChildren().add(textNode);
        }

        return flowPane;
    }

    private boolean isEmojiCodePoint(int codePoint) {
        return (codePoint >= 0x1F600 && codePoint <= 0x1F64F) ||
                (codePoint >= 0x1F300 && codePoint <= 0x1F5FF) ||
                (codePoint >= 0x1F680 && codePoint <= 0x1F6FF) ||
                (codePoint >= 0x1F1E0 && codePoint <= 0x1F1FF) ||
                (codePoint >= 0x2600 && codePoint <= 0x26FF) ||
                (codePoint >= 0x2700 && codePoint <= 0x27BF) ||
                (codePoint >= 0x1F900 && codePoint <= 0x1F9FF) ||
                (codePoint >= 0x1FA70 && codePoint <= 0x1FAFF) ||
                (codePoint >= 0x2764 && codePoint <= 0x2764) ||
                (codePoint >= 0x1F90D && codePoint <= 0x1F90F);
    }

    private void addSystemMessage(String message) {
        if (chatMessagesContainer == null) return;

        Platform.runLater(() -> {
            HBox systemBox = new HBox(6);
            systemBox.setAlignment(Pos.CENTER);
            systemBox.setPadding(new Insets(8, 10, 8, 10));
            systemBox.setStyle("-fx-background-color: #f0f2f5; -fx-background-radius: 12;");
            systemBox.setMaxWidth(300);

            ImageView systemIcon = createEmojiImageView("ℹ️", 16);
            if (systemIcon == null) {
                Text iconText = new Text("ℹ️");
                iconText.setStyle("-fx-font-size: 12px;");
                systemBox.getChildren().add(iconText);
            } else {
                systemBox.getChildren().add(systemIcon);
            }

            Label systemLabel = new Label(message);
            systemLabel.setWrapText(true);
            systemLabel.setMaxWidth(250);
            systemLabel.setStyle("-fx-text-fill: #65676b; -fx-font-size: 12px; -fx-background-color: transparent;");

            systemBox.getChildren().add(systemLabel);

            HBox centerWrapper = new HBox(systemBox);
            centerWrapper.setAlignment(Pos.CENTER);
            centerWrapper.setPadding(new Insets(4, 0, 4, 0));

            chatMessagesContainer.getChildren().add(centerWrapper);

            if (chatMessagesContainer.getChildren().size() > 50) {
                chatMessagesContainer.getChildren().remove(0);
            }

            chatScrollPane.layout();
            chatScrollPane.setVvalue(1.0);
        });
    }

    private String getEmojiImageUrl(String emoji) {
        int codePoint = emoji.codePointAt(0);
        String hex = Integer.toHexString(codePoint);
        return "https://cdn.jsdelivr.net/gh/twitter/twemoji@14.0.2/assets/72x72/" + hex + ".png";
    }

    private ImageView createEmojiImageView(String emoji, double size) {
        try {
            String imageUrl = getEmojiImageUrl(emoji);
            Image emojiImage = new Image(imageUrl, size, size, true, true, true);
            ImageView imageView = new ImageView(emojiImage);
            imageView.setFitWidth(size);
            imageView.setFitHeight(size);
            imageView.setPreserveRatio(true);
            return imageView;
        } catch (Exception e) {
            return null;
        }
    }

    /** ---------------- CLEANUP ---------------- */
    public void cleanup() {
        System.out.println("🧹 HomeController cleanup...");

        // Clear chat callback
        if (serverConnection != null) {
            serverConnection.clearChatCallback();
        }

        // Shutdown executor
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }

        isInitialized = false;
    }

    /** ---------------- BUTTON EFFECTS ---------------- */
    private void setupButtonEffects() {
        addHoverEffect(trainingButton);
        addHoverEffect(quickMatchButton);
        addHoverEffect(roomButton);
        addHoverEffect(bossButton);
    }

    private void addHoverEffect(Button button) {
        if (button == null) return;
        button.setOnMouseEntered(e -> {
            button.setScaleX(1.05);
            button.setScaleY(1.05);
        });
        button.setOnMouseExited(e -> {
            button.setScaleX(1.0);
            button.setScaleY(1.0);
        });
    }

    /** ---------------- BUTTON HANDLERS ---------------- */
    @FXML
    private void handleTrainingMode() {
        showComingSoon("Chế độ Luyện Tập đang được phát triển!");
    }

    @FXML
    private void handleQuickMatch() {
        try {
            cleanup(); // Clean up before switching
            SceneManager.getInstance().switchScene("FindMatch.fxml");
        } catch (Exception e) {
            showError("Không thể chuyển sang màn hình tìm trận!");
        }
    }

    @FXML
    private void handleRoomMode() {
        showComingSoon("Chế độ Phòng Chơi đang được phát triển!");
    }

    @FXML
    private void handleCreateRoom() {
        showComingSoon("Tạo phòng đang được phát triển!");
    }

    @FXML
    private void handleNotifications() {
        showComingSoon("Thông báo đang được phát triển!");
    }

    @FXML
    private void handleSettings() {
        try {
            cleanup(); // Clean up before switching
            SceneManager.getInstance().switchScene("Settings.fxml");
        } catch (Exception e) {
            showComingSoon("Cài đặt đang được phát triển!");
        }
    }

    public void handleAvatarClick() {
        try {
            System.out.println("🖱️ Avatar clicked");
            cleanup(); // Clean up before switching
            SceneManager.getInstance().switchScene("Profile.fxml");
        } catch (Exception e) {
            showComingSoon("Profile đang được phát triển!");
        }
    }

    @FXML
    private void handleLogout() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Đăng xuất");
        alert.setHeaderText("Bạn có chắc muốn đăng xuất?");
        alert.setContentText("Tiến trình chơi sẽ được lưu tự động.");

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    cleanup();

                    if (serverConnection != null && serverConnection.isConnected()) {
                        serverConnection.logoutAndClearSession();
                        serverConnection.disconnect();
                        System.out.println("🔌 Disconnected from server.");
                    }

                    SceneManager.getInstance().clearCache();
                    // Xóa thông tin rememberMe
                    System.clearProperty("saved.username");

                    // Gợi ý GC dọn rác
                    System.gc();

                    SceneManager.getInstance().switchScene("Login.fxml");
                    System.out.println("✅ Logged out successfully");

                } catch (Exception e) {
                    e.printStackTrace();
                    showError("Lỗi khi đăng xuất!");
                }
            }
        });
    }

    @FXML
    private void handleViewLeaderboard() {
        try {
            System.out.println("🔍 Attempting to switch to Leaderboard...");
            SceneManager.getInstance().switchScene("Leaderboard.fxml");
            System.out.println("✅ Successfully switched to Leaderboard");
        } catch (Exception e) {
            System.err.println("❌ Error switching to Leaderboard:");
            e.printStackTrace(); // In ra stack trace để thấy lỗi gì
            showError("Không thể mở bảng xếp hạng: " + e.getMessage());
        }
    }

    /** ---------------- UTILITIES ---------------- */
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

    private String formatNumber(int number) {
        return String.format("%,d", number);
    }

    /** ---------------- UPDATE DATA ---------------- */
    public void updatePoints(int newPoints) {
        Platform.runLater(() -> pointsText.setText("Điểm " + formatNumber(newPoints)));
    }

    public void updateCoins(int newCoins) {
        Platform.runLater(() -> coinsText.setText("Coins " + formatNumber(newCoins)));
    }

    public void refreshUserData() {
        System.out.println("🔄 Refreshing user data...");

        Platform.runLater(() -> {
            loadUserData();      // ✅ Load lại avatar + tên
            loadLeaderboardData(); // ✅ Load lại bảng xếp hạng (nếu rank thay đổi)
        });
    }

    public void handleFriends() {
        try {
            SceneManager.getInstance().switchScene("Friends.fxml");
        } catch (Exception e) {
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR, "Không thể mở danh sách bạn bè!");
            alert.showAndWait();
        }
    }
}