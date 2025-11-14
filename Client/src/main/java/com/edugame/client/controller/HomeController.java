package com.edugame.client.controller;

import com.edugame.client.model.User;
import com.edugame.client.network.ServerConnection;
import com.edugame.client.util.ChatPopupHandler;
import com.edugame.client.util.SceneManager;
import com.edugame.common.Protocol;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import javafx.geometry.Pos;

import java.io.File;
import java.io.IOException;
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

    @FXML private Button joinRoomButton;
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

    @FXML
    private Button btnCreateRoom;


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

    @FXML
    private Label chatBadge;
    @FXML
    private Label friendsBadge;

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
        serverConnection.setGlobalChatCallback(json -> {
            Platform.runLater(() -> {
                try {
                    String type = json.has("type") ? json.get("type").getAsString() : "GLOBAL_CHAT";

                    if ("GLOBAL_CHAT".equals(type) || "GLOBAL_CHAT_MESSAGE".equals(type)) {
                        String username = json.has("username") ? json.get("username").getAsString() : "Unknown";
                        String message = json.has("message") ? json.get("message").getAsString() : "";

                        // Don't show own messages (already displayed when sent)
                        if (!username.equals(serverConnection.getCurrentUsername())) {
                            addChatMessage(username, message, false);
                        }
                    } else if ("SYSTEM_MESSAGE".equals(type)) {
                        String message = json.has("message") ? json.get("message").getAsString() : "";
                        addSystemMessage(message);
                    }
                } catch (Exception e) {
                    System.err.println("❌ Error handling chat callback: " + e.getMessage());
                    e.printStackTrace();
                }
            });
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

        // Clear global chat callback only
        if (serverConnection != null) {
            serverConnection.clearGlobalChatCallback();
        }

        // Shutdown executor
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }

        isInitialized = false;
        System.out.println("✅ HomeController cleanup complete");
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
        showSubjectSelectionPopup(
                SubjectSelectionController.SelectionMode.TRAINING,
                this::startTrainingMode
        );
    }

    @FXML
    private void handleQuickMatch() {
        try {
            cleanup(); // Clean up before switching
            showSubjectSelectionPopup(
                    SubjectSelectionController.SelectionMode.QUICK_MATCH,
                    this::startQuickMatch
            );
        } catch (Exception e) {
            showError("Không thể chuyển sang màn hình tìm trận!");
        }
    }

    private void showSubjectSelectionPopup(
            SubjectSelectionController.SelectionMode mode,
            SubjectSelectionController.SubjectSelectionCallback callback) {

        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/SubjectSelectionPopup.fxml")
            );
            StackPane root = loader.load();

            SubjectSelectionController controller = loader.getController();
            controller.setMode(mode);
            controller.setCallback(callback);

            // Tạo stage cho popup
            Stage dialogStage = new Stage();
            dialogStage.initModality(Modality.APPLICATION_MODAL);
            dialogStage.initStyle(StageStyle.TRANSPARENT);
            dialogStage.setTitle("Chọn Môn Học");

            Scene scene = new Scene(root);
            scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
            dialogStage.setScene(scene);

            controller.setDialogStage(dialogStage);

            // Hiển thị popup
            dialogStage.showAndWait();

        } catch (IOException e) {
            System.err.println("❌ Error loading SubjectSelectionPopup.fxml");
            System.err.println("   Path: " + getClass().getResource("/SubjectSelectionPopup.fxml"));
            e.printStackTrace();
            showError("Không thể hiển thị popup chọn môn học!\n" + e.getMessage());
        } catch (Exception e) {
            System.err.println("❌ Unexpected error in popup");
            e.printStackTrace();
            showError("Lỗi không xác định: " + e.getMessage());
        }
    }

    /**
     * Bắt đầu chế độ luyện tập với môn học đã chọn
     */
    private void startTrainingMode(String subject) {
        System.out.println("🎓 Starting Training Mode: " + subject);

        try {
            // Lưu subject vào session
           serverConnection.setSelectedSubject(subject);

            // Chuyển sang màn hình luyện tập
            cleanup();
            SceneManager.getInstance().switchScene("TrainingMode.fxml");

        } catch (Exception e) {
            showError("Không thể vào chế độ luyện tập!");
            e.printStackTrace();
        }
    }

    /**
     * Bắt đầu tìm trận nhanh với môn học đã chọn
     */
    private void startQuickMatch(String subject) {
        System.out.println("🔍 Starting Quick Match: " + subject);

        try {
            // Lưu subject vào session
            serverConnection.setSelectedSubject(subject);

            // Gửi request tìm trận đến server
            String request = String.format(
                    "{\"type\":\"%s\",\"subject\":\"%s\",\"difficulty\":\"%s\"}",
                    Protocol.FIND_MATCH,
                    subject,
                    Protocol.MEDIUM  // Default difficulty
            );

            serverConnection.sendMessage(request);

            // Chuyển sang màn hình tìm trận (có loading + countdown)
            cleanup();
            SceneManager.getInstance().switchScene("FindMatch.fxml");

        } catch (Exception e) {
            showError("Không thể bắt đầu tìm trận!");
            e.printStackTrace();
        }
    }


    /**
     * Bắt đầu tạo phòng với môn học đã chọn
     */
    private void startCreateRoom(String subject) {
        handleCreateRoom(subject, "medium");
    }

    @FXML
    private void handleCreateRoom() {
        showSubjectSelectionPopup(
                SubjectSelectionController.SelectionMode.CREATE_ROOM,
                this::startCreateRoom  // callback
        );
    }


    /**
     * Handle Create Room
     */
    private void handleCreateRoom(String subject, String difficulty) {
        showLoadingDialog("Đang tạo phòng...");

        serverConnection.createRoom(subject, difficulty, roomData -> {
            Platform.runLater(() -> {
                hideLoadingDialog();

                if (roomData == null) {
                    System.err.println("❌ Room data is null");
                    showError("Không thể tạo phòng!");
                    return;
                }

                // Debug: Print received data
                System.out.println("✅ Received room data from server:");
                System.out.println("   Room ID: " + roomData.get("roomId"));
                System.out.println("   Room Name: " + roomData.get("roomName"));
                System.out.println("   Subject: " + roomData.get("subject"));
                System.out.println("   Difficulty: " + roomData.get("difficulty"));
                System.out.println("   Players: " + roomData.get("players"));

                try {
                    // Load Room.fxml
                    FXMLLoader loader = new FXMLLoader(
                            getClass().getResource("/fxml/Room.fxml")
                    );
                    Parent root = loader.load();

                    // Get controller and initialize with room data
                    RoomController roomController = loader.getController();
                    roomController.initializeRoom(roomData);  // Pass Map directly

                    // Switch scene
                    Scene scene = new Scene(root);
                    Stage stage = (Stage) btnCreateRoom.getScene().getWindow();
                    stage.setScene(scene);
                    stage.show();

                    System.out.println("✅ Successfully navigated to room: " + roomData.get("roomId"));

                } catch (IOException e) {
                    System.err.println("❌ IO Error loading Room.fxml: " + e.getMessage());
                    e.printStackTrace();
                    showError("Không thể tải giao diện phòng!");
                } catch (Exception e) {
                    System.err.println("❌ Error switching to room: " + e.getMessage());
                    e.printStackTrace();
                    showError("Không thể vào phòng: " + e.getMessage());
                }
            });
        });
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

    @FXML
    public void handleChats(ActionEvent event) {
        try {
            System.out.println("💬 [MENU] Opening chat window...");

            Stage ownerStage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            ChatPopupHandler.openChatWindow(ownerStage);

        } catch (Exception e) {
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Lỗi");
            alert.setHeaderText("Không thể mở tin nhắn");
            alert.setContentText("Đã xảy ra lỗi khi mở cửa sổ tin nhắn: " + e.getMessage());
            alert.showAndWait();
        }
    }

    public void updateChatNotificationCount(int count) {
        if (count > 0) {
            chatBadge.setText(String.valueOf(count));
            chatBadge.setVisible(true);
        } else {
            chatBadge.setVisible(false);
        }
    }

    public void updateFriendNotificationCount(int count) {
        if (count > 0) {
            friendsBadge.setText(String.valueOf(count));
            friendsBadge.setVisible(true);
        } else {
            friendsBadge.setVisible(false);
        }
    }
    private Stage loadingStage;

    private void showLoadingDialog(String message) {
        if (loadingStage != null && loadingStage.isShowing()) return; // tránh mở trùng

        Platform.runLater(() -> {
            Label lblMessage = new Label(message);
            lblMessage.setStyle("-fx-font-size: 14px; -fx-text-fill: #333;");

            ProgressIndicator progressIndicator = new ProgressIndicator();
            progressIndicator.setPrefSize(50, 50);

            VBox layout = new VBox(15, progressIndicator, lblMessage);
            layout.setAlignment(Pos.CENTER);
            layout.setPadding(new Insets(20));
            layout.setStyle("-fx-background-color: rgba(255,255,255,0.9); -fx-background-radius: 10;");

            Scene scene = new Scene(layout);
            scene.setFill(Color.TRANSPARENT);

            loadingStage = new Stage();
            loadingStage.initStyle(StageStyle.TRANSPARENT);
            loadingStage.initModality(Modality.NONE); // ✅ modeless, không block
            loadingStage.setScene(scene);
            loadingStage.setResizable(false);

            loadingStage.show();
        });
    }

    private void hideLoadingDialog() {
        Platform.runLater(() -> {
            if (loadingStage != null) {
                loadingStage.close();
                loadingStage = null;
            }
        });
    }


    /**
     * Xử lý sự kiện click vào button Join Room - Custom Popup
     */
    @FXML
    private void handleJoinRoom() {
        // Tạo Stage mới cho popup
        Stage popupStage = new Stage();
        popupStage.initModality(Modality.APPLICATION_MODAL);
        popupStage.initOwner(joinRoomButton.getScene().getWindow());
        popupStage.setTitle("Tham Gia Phòng");

        // Tạo layout cho popup
        VBox popupLayout = new VBox(20);
        popupLayout.setPadding(new Insets(30));
        popupLayout.setAlignment(Pos.CENTER);
        popupLayout.setStyle("-fx-background-color: #2a2d3a;");

        // Icon
        Text iconText = new Text("🏠");
        iconText.setStyle("-fx-font-size: 48px;");

        // Title
        Text titleText = new Text("Nhập Mã Phòng");
        titleText.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-fill: white;");

        // Description
        Text descText = new Text("Nhập mã phòng 6 ký tự để tham gia");
        descText.setStyle("-fx-font-size: 14px; -fx-fill: #a0a0a0;");

        // TextField cho mã phòng
        TextField roomCodeField = new TextField();
        roomCodeField.setPromptText("VD: ABC123");
        roomCodeField.setPrefWidth(300);
        roomCodeField.setStyle(
                "-fx-background-color: #1e1e2e; " +
                        "-fx-text-fill: white; " +
                        "-fx-font-size: 18px; " +
                        "-fx-padding: 12px; " +
                        "-fx-background-radius: 8px; " +
                        "-fx-border-color: #4a4a6a; " +
                        "-fx-border-radius: 8px; " +
                        "-fx-border-width: 2px;"
        );

        // Giới hạn nhập tối đa 6 ký tự và tự động chuyển thành chữ hoa
        roomCodeField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                String upperCase = newVal.toUpperCase();
                if (upperCase.length() > 6) {
                    roomCodeField.setText(oldVal);
                } else if (!upperCase.equals(newVal)) {
                    roomCodeField.setText(upperCase);
                }
            }
        });

        // Buttons
        HBox buttonBox = new HBox(15);
        buttonBox.setAlignment(Pos.CENTER);

        Button cancelButton = new Button("Hủy");
        cancelButton.setStyle(
                "-fx-background-color: #4a4a6a; " +
                        "-fx-text-fill: white; " +
                        "-fx-font-size: 14px; " +
                        "-fx-padding: 10px 30px; " +
                        "-fx-background-radius: 8px; " +
                        "-fx-cursor: hand;"
        );
        cancelButton.setOnAction(e -> popupStage.close());

        Button joinButton = new Button("Tham Gia");
        joinButton.setStyle(
                "-fx-background-color: #5865f2; " +
                        "-fx-text-fill: white; " +
                        "-fx-font-size: 14px; " +
                        "-fx-padding: 10px 30px; " +
                        "-fx-background-radius: 8px; " +
                        "-fx-cursor: hand;"
        );
        joinButton.setDefaultButton(true);
        joinButton.setOnAction(e -> {
            String roomCode = roomCodeField.getText().trim();
            if (roomCode.isEmpty()) {
                showError(roomCodeField, "Vui lòng nhập mã phòng!");
                return;
            }
            if (roomCode.length() < 6) {
                showError(roomCodeField, "Mã phòng phải có 6 ký tự!");
                return;
            }

            popupStage.close();
            joinRoomWithCode(roomCode);
        });

        // Hover effects
        cancelButton.setOnMouseEntered(e ->
                cancelButton.setStyle(cancelButton.getStyle() + "-fx-background-color: #5a5a7a;")
        );
        cancelButton.setOnMouseExited(e ->
                cancelButton.setStyle(cancelButton.getStyle().replace("-fx-background-color: #5a5a7a;", "-fx-background-color: #4a4a6a;"))
        );

        joinButton.setOnMouseEntered(e ->
                joinButton.setStyle(joinButton.getStyle() + "-fx-background-color: #4752c4;")
        );
        joinButton.setOnMouseExited(e ->
                joinButton.setStyle(joinButton.getStyle().replace("-fx-background-color: #4752c4;", "-fx-background-color: #5865f2;"))
        );

        buttonBox.getChildren().addAll(cancelButton, joinButton);

        // Thêm tất cả vào layout
        popupLayout.getChildren().addAll(iconText, titleText, descText, roomCodeField, buttonBox);

        // Tạo scene và hiển thị
        Scene scene = new Scene(popupLayout);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        popupStage.setScene(scene);
        popupStage.setResizable(false);

        // Focus vào textfield khi mở
        Platform.runLater(() -> roomCodeField.requestFocus());

        popupStage.showAndWait();
    }

    /**
     * Hiển thị lỗi validation
     */
    private void showError(TextField field, String message) {
        field.setStyle(field.getStyle() + "-fx-border-color: #ff4444;");

        // Tạo tooltip hiển thị lỗi
        Tooltip errorTooltip = new Tooltip(message);
        errorTooltip.setStyle("-fx-background-color: #ff4444; -fx-text-fill: white;");
        field.setTooltip(errorTooltip);
        errorTooltip.show(field,
                field.localToScreen(field.getBoundsInLocal()).getMinX(),
                field.localToScreen(field.getBoundsInLocal()).getMaxY() + 5
        );

        // Ẩn tooltip sau 2 giây
        PauseTransition pause = new PauseTransition(Duration.seconds(2));
        pause.setOnFinished(e -> {
            errorTooltip.hide();
            field.setStyle(field.getStyle().replace("-fx-border-color: #ff4444;", "-fx-border-color: #4a4a6a;"));
            field.setTooltip(null);
        });
        pause.play();
    }

    /**
     * Tham gia phòng với mã phòng đã nhập
     */
    private void joinRoomWithCode(String roomCode) {
        try {
            if (roomCode == null || roomCode.isEmpty()) {
                showAlert("Thông báo", "Vui lòng nhập mã phòng!", Alert.AlertType.WARNING);
                return;
            }

            System.out.println("🚪 Đang tham gia phòng: " + roomCode);

            // Hiển thị loading (Stage modeless)
            showLoadingDialog("🔄 Đang tham gia phòng...");

            // ✅ Timeout tự động đóng sau 10 giây
            Thread timeoutThread = new Thread(() -> {
                try {
                    Thread.sleep(10000); // 10 giây
                    Platform.runLater(() -> {
                        if (loadingStage != null && loadingStage.isShowing()) {
                            hideLoadingDialog();
                            showAlert("Timeout", "Không nhận được phản hồi từ server!", Alert.AlertType.WARNING);
                        }
                    });
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }, "JoinRoomTimeout");
            timeoutThread.setDaemon(true);
            timeoutThread.start();

            // ✅ Đăng ký callback xử lý response từ server
            serverConnection.setJoinRoomCallback((success, message, roomData) -> {
                Platform.runLater(() -> {
                    // Đóng loading ngay khi có phản hồi
                    hideLoadingDialog();

                    if (success && roomData != null) {
                        System.out.println("✅ Join room thành công!");
                        try {
                            // Chuyển sang RoomController
                            Platform.runLater(() -> {
                                try {
                                    FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Room.fxml"));
                                    Parent root = loader.load();
                                    RoomController controller = loader.getController();
                                    controller.initializeRoom(roomData);

                                    Scene scene = new Scene(root);
                                    Stage stage = SceneManager.getInstance().getPrimaryStage();
                                    stage.setScene(scene);
                                    stage.show();
                                } catch (Exception e) {
                                    showAlert("Lỗi", "Không thể chuyển màn hình: " + e.getMessage(), Alert.AlertType.ERROR);
                                    e.printStackTrace();
                                }
                            });

                        } catch (Exception e) {
                            showAlert("Lỗi", "Không thể chuyển màn hình: " + e.getMessage(), Alert.AlertType.ERROR);
                            e.printStackTrace();
                        }
                    } else {
                        showAlert("Lỗi", message != null ? message : "Không thể tham gia phòng!", Alert.AlertType.ERROR);
                    }
                });
            });

            // Gửi request tham gia phòng
            serverConnection.joinGameRoom(roomCode);

        } catch (Exception e) {
            hideLoadingDialog(); // chắc chắn đóng nếu có lỗi
            showAlert("Lỗi", "Không thể tham gia phòng: " + e.getMessage(), Alert.AlertType.ERROR);
            e.printStackTrace();
        }
    }


    /**
     * Hiển thị alert thông báo
     */
    private void showAlert(String title, String content, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }

}