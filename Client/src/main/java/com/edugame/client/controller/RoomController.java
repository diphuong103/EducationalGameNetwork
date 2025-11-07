package com.edugame.client.controller;

import com.edugame.client.network.ServerConnection;
import com.edugame.client.util.SceneManager;
import com.edugame.common.Protocol;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Controller cho màn hình phòng chờ game
 * - Hiển thị 4 slot người chơi
 * - Chat phòng với emoji support (giống Global Chat)
 * - Danh sách bạn bè với filter (Online/In Game/Offline)
 * - Mời bạn vào phòng
 * - Ready/Start game
 */
public class RoomController {

    // ==================== FXML Components ====================

    // Header
    @FXML private Button btnBack;
    @FXML private Label lblRoomId;


    // Player Slots (4 players)
    @FXML private ImageView avatar1, avatar2, avatar3, avatar4;
    @FXML private Label name1, name2, name3, name4;
    @FXML private Label score1, score2, score3, score4;
    @FXML private Label emptyIcon2, emptyIcon3, emptyIcon4;
    @FXML private Circle readyIndicator1, readyIndicator2, readyIndicator3, readyIndicator4;
    @FXML private VBox playerSlot1, playerSlot2, playerSlot3, playerSlot4;

    // Chat
    @FXML private VBox chatMessagesContainer;
    @FXML private ScrollPane chatScrollPane;
    @FXML private TextField chatInputField;
    @FXML private Button btnSendChat;
    @FXML private Button emojiButton;
    @FXML private Label onlineCount;

    // Friends Panel
    @FXML private ListView<FriendItem> friendList;
    @FXML private Button btnFilterAll, btnFilterOnline, btnFilterInGame;
    @FXML private Button btnInvite;
    @FXML private Button btnReady;
    @FXML private Button btnStart;

    // ==================== Data ====================

    private ServerConnection connection;
    private Map<String, Object> currentRoomData; // Lưu toàn bộ thông tin phòng
    private String roomId;        // ID phòng
    private String subject;       // Môn học (math, science, etc.)
    private String difficulty;    // Độ khó (easy, medium, hard)
    private boolean isHost = false;
    private boolean isReady = false;

    // Player data
    private Map<Integer, PlayerInfo> players = new HashMap<>();
    private List<FriendItem> allFriends = new ArrayList<>();
    private String currentFilter = "ALL";

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm");




    @FXML
    private void initialize() {
        connection = ServerConnection.getInstance();

        // Setup UI
        setupPlayerSlots();
        setupChatSystem();
        setupFriendsList();
        setupEventHandlers();

        // Load initial data
        loadRoomData();
        loadFriendsList();

        // Register room chat callback
        connection.setRoomChatCallback(this::handleRoomChatMessage);

        System.out.println("✅ RoomController initialized");
    }

    public void initializeRoom(Map<String, Object> roomData) {
        this.currentRoomData = roomData;

        // Extract basic room info - safe casting
        this.roomId = getStringValue(roomData.get("roomId"));
        this.subject = getStringValue(roomData.get("subject"));
        this.difficulty = getStringValue(roomData.get("difficulty"));

        lblRoomId.setText(roomId);

        System.out.println("🏠 Initializing room:");
        System.out.println("   Room ID: " + roomId);
        System.out.println("   Subject: " + subject);
        System.out.println("   Difficulty: " + difficulty);

        // Load players
        Object playersObj = roomData.get("players");
        if (playersObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> playersList = (List<Map<String, Object>>) playersObj;

            System.out.println("   Players: " + playersList.size());

            for (int i = 0; i < playersList.size(); i++) {
                Map<String, Object> player = playersList.get(i);

                int userId = getIntValue(player.get("userId"));
                String name = getStringValue(player.get("fullName"));
                String avatarUrl = getStringValue(player.get("avatarUrl"));
                int score = getIntValue(player.get("totalScore"));
                boolean playerIsHost = getBooleanValue(player.get("isHost"));
                boolean playerIsReady = getBooleanValue(player.get("isReady"));

                System.out.println("   Player " + (i+1) + ": " + name +
                        " (ID: " + userId + ")" +
                        (playerIsHost ? " [HOST]" : "") +
                        (playerIsReady ? " [READY]" : ""));

                // Check if this is current user
                if (userId == connection.getCurrentUserId()) {
                    isHost = playerIsHost;
                }

                // Update player slot
                updatePlayer(i + 1, userId, name, avatarUrl, score, playerIsReady);
            }
        }

        // Configure buttons
        if (isHost) {
            btnReady.setVisible(false);
            btnReady.setManaged(false);
            btnStart.setVisible(true);
            btnStart.setManaged(true);
            btnStart.setDisable(true);
            System.out.println("   → You are the HOST");
        } else {
            btnStart.setVisible(false);
            btnStart.setManaged(false);
            System.out.println("   → You are a PLAYER");
        }

        updateOnlineCount();
        System.out.println("✅ Room initialized successfully");
    }

    /**
     * Safely get int from Object (handles Integer, Long, Double, etc.)
     */
    private int getIntValue(Object obj) {
        if (obj == null) return 0;
        if (obj instanceof Number) {
            return ((Number) obj).intValue();
        }
        try {
            return Integer.parseInt(obj.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Safely get String from Object
     */
    private String getStringValue(Object obj) {
        return obj != null ? obj.toString() : "";
    }

    /**
     * Safely get boolean from Object
     */
    private boolean getBooleanValue(Object obj) {
        if (obj == null) return false;
        if (obj instanceof Boolean) return (Boolean) obj;
        return Boolean.parseBoolean(obj.toString());
    }

    /**
     * Setup player slots
     */
    private void setupPlayerSlots() {
        // Set default avatars
        setDefaultAvatar(avatar1);
        setDefaultAvatar(avatar2);
        setDefaultAvatar(avatar3);
        setDefaultAvatar(avatar4);

        // Hide ready indicators initially
        readyIndicator1.setVisible(false);
        readyIndicator2.setVisible(false);
        readyIndicator3.setVisible(false);
        readyIndicator4.setVisible(false);
    }

    /**
     * Set default avatar
     */
    private void setDefaultAvatar(ImageView imageView) {
        try {
            String defaultPath = "/images/avatars/avatar4.png";
            Image defaultImage = new Image(getClass().getResourceAsStream(defaultPath));
            imageView.setImage(defaultImage);
        } catch (Exception e) {
            System.err.println("⚠️ Could not load default avatar");
        }
    }

    // ==================== CHAT SYSTEM (Like your previous implementation) ====================

    /**
     * Setup chat system với emoji support
     */
    private void setupChatSystem() {
        if (chatMessagesContainer == null) {
            chatMessagesContainer = new VBox(8);
            chatMessagesContainer.setPadding(new Insets(10));
            chatMessagesContainer.setStyle("-fx-background-color: transparent;");
        }

        if (chatScrollPane != null) {
            chatScrollPane.setContent(chatMessagesContainer);
            chatScrollPane.setFitToWidth(true);
            chatScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            chatScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        }

        // Add welcome message
        addSystemMessage("Chào mừng đến phòng chờ! 🎮");
    }

    /**
     * Handle send chat message
     */
    @FXML
    private void handleSendChat() {
        if (chatInputField == null || chatMessagesContainer == null) return;

        String message = chatInputField.getText().trim();
        if (message.isEmpty()) return;

        if (!connection.isConnected()) {
            addSystemMessage("Không thể gửi tin nhắn. Chưa kết nối server.");
            return;
        }

        // Send message to server (Room Chat)
        try {
            int roomIdInt = Integer.parseInt(roomId);
            connection.sendRoomChatMessage(roomIdInt, message);

            // Display own message
            addChatMessage(connection.getCurrentFullName(), message, true);
            chatInputField.clear();

        } catch (Exception e) {
            addSystemMessage("Lỗi khi gửi tin nhắn!");
            e.printStackTrace();
        }
    }

    /**
     * Handle room chat message from server
     */
    private void handleRoomChatMessage(com.google.gson.JsonObject json) {
        try {
            boolean success = json.get("success").getAsBoolean();
            if (!success) return;

            String username = json.get("username").getAsString();
            String message = json.get("message").getAsString();

            // Don't display if it's our own message (already displayed)
            if (!username.equals(connection.getCurrentUsername())) {
                addChatMessage(username, message, false);
            }

        } catch (Exception e) {
            System.err.println("❌ Error handling room chat: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Show emoji popup (same as your implementation)
     */
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
                String newText = beforeCaret + emoji + afterCaret;

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

    /**
     * Add chat message với emoji support
     */
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

            String timeStr = LocalDateTime.now().format(TIME_FORMAT);
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

    /**
     * Parse message với emoji images
     */
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

    /**
     * Check if code point is emoji
     */
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

    /**
     * Add system message
     */
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

    /**
     * Get emoji image URL from CDN
     */
    private String getEmojiImageUrl(String emoji) {
        int codePoint = emoji.codePointAt(0);
        String hex = Integer.toHexString(codePoint);
        return "https://cdn.jsdelivr.net/gh/twitter/twemoji@14.0.2/assets/72x72/" + hex + ".png";
    }

    /**
     * Create emoji ImageView
     */
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

    // ==================== FRIENDS LIST ====================

    /**
     * Setup friends list
     */
    private void setupFriendsList() {
        friendList.setCellFactory(listView -> new ListCell<FriendItem>() {
            @Override
            protected void updateItem(FriendItem friend, boolean empty) {
                super.updateItem(friend, empty);

                if (empty || friend == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    String statusIcon = getStatusIcon(friend.status);
                    String statusColor = getStatusColor(friend.status);

                    setText(String.format("%s %s\n⭐ %d",
                            statusIcon, friend.name, friend.score));

                    setStyle(String.format(
                            "-fx-text-fill: %s; -fx-font-size: 13; -fx-font-weight: bold;",
                            statusColor
                    ));
                }
            }
        });
    }

    /**
     * Get status icon
     */
    private String getStatusIcon(String status) {
        switch (status) {
            case "ONLINE": return "🟢";
            case "IN_GAME": return "🎮";
            case "OFFLINE": return "⚫";
            default: return "⚪";
        }
    }

    /**
     * Get status color
     */
    private String getStatusColor(String status) {
        switch (status) {
            case "ONLINE": return "#4caf50";
            case "IN_GAME": return "#ff9800";
            case "OFFLINE": return "#757575";
            default: return "#aaaaaa";
        }
    }

    /**
     * Setup event handlers
     */
    private void setupEventHandlers() {
        // Back button
        btnBack.setOnAction(e -> handleBack());

        // Chat
        btnSendChat.setOnAction(e -> handleSendChat());
        chatInputField.setOnAction(e -> handleSendChat());

        // Friends filter
        btnFilterAll.setOnAction(e -> handleFilter("ALL"));
        btnFilterOnline.setOnAction(e -> handleFilter("ONLINE"));
        btnFilterInGame.setOnAction(e -> handleFilter("IN_GAME"));

        // Actions
        btnInvite.setOnAction(e -> handleInviteFriend());
        btnReady.setOnAction(e -> handleReady());
        btnStart.setOnAction(e -> handleStartGame());
    }

    // ==================== Data Loading ====================

    /**
     * Load room data
     */
    private void loadRoomData() {
        // TODO: Get room data from server
        // For now, use mock data
        roomId = "579494"; // Get from connection or server
        lblRoomId.setText(roomId);

        // Set current user as player 1 (host)
        isHost = true; // TODO: Get from server
        updatePlayer(1,
                connection.getCurrentUserId(),
                connection.getCurrentFullName(),
                connection.getCurrentAvatarUrl(),
                connection.getTotalScore(),
                false
        );

        updateOnlineCount();

        // Show/hide start button based on host status
        if (isHost) {
            btnReady.setVisible(false);
            btnReady.setManaged(false);
            btnStart.setVisible(true);
            btnStart.setManaged(true);
            btnStart.setDisable(true); // Enable when all ready
        } else {
            btnStart.setVisible(false);
            btnStart.setManaged(false);
        }
    }

    /**
     * Load friends list
     */
    private void loadFriendsList() {
        connection.getFriendsList(friends -> {
            Platform.runLater(() -> {
                allFriends.clear();

                for (Map<String, Object> friend : friends) {
                    FriendItem item = new FriendItem();
                    item.userId = (int) friend.get("userId");
                    item.name = (String) friend.get("fullName");
                    item.score = (int) friend.get("totalScore");
                    item.isOnline = (boolean) friend.get("isOnline");

                    // TODO: Get actual in-game status from server
                    if (item.isOnline) {
                        item.status = "ONLINE"; // or "IN_GAME"
                    } else {
                        item.status = "OFFLINE";
                    }

                    allFriends.add(item);
                }

                // Sort: Online > In Game > Offline
                allFriends.sort((a, b) -> {
                    int priorityA = getStatusPriority(a.status);
                    int priorityB = getStatusPriority(b.status);
                    if (priorityA != priorityB) {
                        return priorityA - priorityB;
                    }
                    return b.score - a.score; // Sort by score if same status
                });

                applyFilter();

                System.out.println("✅ Loaded " + allFriends.size() + " friends");
            });
        });
    }

    /**
     * Get status priority for sorting
     */
    private int getStatusPriority(String status) {
        switch (status) {
            case "ONLINE": return 1;
            case "IN_GAME": return 2;
            case "OFFLINE": return 3;
            default: return 4;
        }
    }

    // ==================== Event Handlers ====================

    /**
     * Handle back button
     */
    private void handleBack() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Xác nhận");
        alert.setHeaderText("Rời phòng");
        alert.setContentText("Bạn có chắc muốn rời khỏi phòng?");

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                leaveRoom();
            }
        });
    }

    /**
     * Leave room
     */
    private void leaveRoom() {
        try {
            // TODO: Send LEAVE_ROOM to server
            connection.leaveGameRoom();

            cleanup();
            SceneManager.getInstance().switchScene("Home.fxml");

        } catch (Exception e) {
            showError("Không thể rời phòng!");
            e.printStackTrace();
        }
    }

    /**
     * Handle filter
     */
    private void handleFilter(String filter) {
        currentFilter = filter;

        // Update button styles
        btnFilterAll.getStyleClass().remove("filter-active");
        btnFilterOnline.getStyleClass().remove("filter-active");
        btnFilterInGame.getStyleClass().remove("filter-active");

        switch (filter) {
            case "ALL":
                btnFilterAll.getStyleClass().add("filter-active");
                break;
            case "ONLINE":
                btnFilterOnline.getStyleClass().add("filter-active");
                break;
            case "IN_GAME":
                btnFilterInGame.getStyleClass().add("filter-active");
                break;
        }

        applyFilter();
    }

    /**
     * Apply current filter
     */
    private void applyFilter() {
        List<FriendItem> filtered = new ArrayList<>();

        for (FriendItem friend : allFriends) {
            switch (currentFilter) {
                case "ALL":
                    filtered.add(friend);
                    break;
                case "ONLINE":
                    if ("ONLINE".equals(friend.status)) {
                        filtered.add(friend);
                    }
                    break;
                case "IN_GAME":
                    if ("IN_GAME".equals(friend.status)) {
                        filtered.add(friend);
                    }
                    break;
            }
        }

        friendList.getItems().setAll(filtered);
    }

    /**
     * Handle invite friend
     */
    private void handleInviteFriend() {
        FriendItem selected = friendList.getSelectionModel().getSelectedItem();

        if (selected == null) {
            showWarning("Vui lòng chọn bạn bè để mời!");
            return;
        }

        if (!"ONLINE".equals(selected.status)) {
            showWarning("Chỉ có thể mời bạn bè đang online!");
            return;
        }

        // TODO: Send invite to server
        // connection.inviteToRoom(selected.userId, roomId);

        showInfo("Đã gửi lời mời đến " + selected.name);
    }

    /**
     * Handle ready button
     */
    private void handleReady() {
        isReady = !isReady;

        // Update button
        if (isReady) {
            btnReady.setText("❌ Hủy sẵn sàng");
            btnReady.getStyleClass().remove("ready-button");
            btnReady.getStyleClass().add("ready-button-active");
        } else {
            btnReady.setText("✓ Sẵn sàng");
            btnReady.getStyleClass().remove("ready-button-active");
            btnReady.getStyleClass().add("ready-button");
        }

        // Update ready indicator for current player
        readyIndicator1.setVisible(isReady);

        // TODO: Send to server
        connection.sendReady();

        System.out.println("✅ Ready status: " + isReady);
    }

    /**
     * Handle start game (host only)
     */
    private void handleStartGame() {
        if (!isHost) {
            return;
        }

        // TODO: Check if all players are ready
        boolean allReady = checkAllPlayersReady();

        if (!allReady) {
            showWarning("Chưa đủ người chơi hoặc chưa tất cả sẵn sàng!");
            return;
        }

        // TODO: Send START_GAME to server
        System.out.println("🎮 Starting game...");

        // Navigate to game screen
        try {
            SceneManager.getInstance().switchScene("Game.fxml");
        } catch (Exception e) {
            showError("Không thể bắt đầu game!");
            e.printStackTrace();
        }
    }

    /**
     * Check if all players are ready
     */
    private boolean checkAllPlayersReady() {
        // TODO: Implement actual check
        // For now, return false if less than 2 players
        return players.size() >= 2;
    }

    // ==================== Player Management ====================

    /**
     * Update player slot
     */
    private void updatePlayer(int slot, int userId, String name,
                              String avatarUrl, int score, boolean isReady) {
        Platform.runLater(() -> {
            ImageView avatar = getAvatarBySlot(slot);
            Label nameLabel = getNameLabelBySlot(slot);
            Label scoreLabel = getScoreLabelBySlot(slot);
            Circle readyIndicator = getReadyIndicatorBySlot(slot);
            Label emptyIcon = getEmptyIconBySlot(slot);

            if (avatar != null && nameLabel != null) {
                // Load avatar
                loadAvatar(avatar, avatarUrl);

                // Update labels
                nameLabel.setText(name);
                nameLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 16; -fx-font-weight: bold;");

                if (scoreLabel != null) {
                    scoreLabel.setText("⭐ " + score);
                }

                // Hide empty icon
                if (emptyIcon != null) {
                    emptyIcon.setVisible(false);
                }

                // Update ready status
                if (readyIndicator != null) {
                    readyIndicator.setVisible(isReady);
                }

                // Store player data
                PlayerInfo player = new PlayerInfo();
                player.userId = userId;
                player.name = name;
                player.score = score;
                player.isReady = isReady;
                players.put(slot, player);
            }

            updateOnlineCount();
            checkStartButtonState();
        });
    }

    /**
     * Remove player from slot
     */
    private void removePlayer(int slot) {
        Platform.runLater(() -> {
            ImageView avatar = getAvatarBySlot(slot);
            Label nameLabel = getNameLabelBySlot(slot);
            Label scoreLabel = getScoreLabelBySlot(slot);
            Circle readyIndicator = getReadyIndicatorBySlot(slot);
            Label emptyIcon = getEmptyIconBySlot(slot);

            if (avatar != null && nameLabel != null) {
                setDefaultAvatar(avatar);
                nameLabel.setText("Đang chờ...");
                nameLabel.setStyle("-fx-text-fill: #666666; -fx-font-size: 14; -fx-font-style: italic;");

                if (scoreLabel != null) {
                    scoreLabel.setText("");
                }

                if (emptyIcon != null) {
                    emptyIcon.setVisible(true);
                }

                if (readyIndicator != null) {
                    readyIndicator.setVisible(false);
                }

                players.remove(slot);
            }

            updateOnlineCount();
            checkStartButtonState();
        });
    }

    /**
     * Load avatar from URL
     */
    private void loadAvatar(ImageView imageView, String url) {
        try {
            if (url != null && !url.isEmpty()) {
                Image image = new Image(url, true);
                imageView.setImage(image);
            } else {
                setDefaultAvatar(imageView);
            }
        } catch (Exception e) {
            setDefaultAvatar(imageView);
        }
    }

    /**
     * Update online count
     */
    private void updateOnlineCount() {
        int playerCount = players.size();
        onlineCount.setText(String.format("🟢 %d/4", playerCount));
    }

    /**
     * Check if start button should be enabled
     */
    private void checkStartButtonState() {
        if (!isHost) {
            return;
        }

        boolean canStart = players.size() >= 2 && checkAllPlayersReady();
        btnStart.setDisable(!canStart);
    }

    // ==================== Helper Methods ====================

    private ImageView getAvatarBySlot(int slot) {
        switch (slot) {
            case 1: return avatar1;
            case 2: return avatar2;
            case 3: return avatar3;
            case 4: return avatar4;
            default: return null;
        }
    }

    private Label getNameLabelBySlot(int slot) {
        switch (slot) {
            case 1: return name1;
            case 2: return name2;
            case 3: return name3;
            case 4: return name4;
            default: return null;
        }
    }

    private Label getScoreLabelBySlot(int slot) {
        switch (slot) {
            case 1: return score1;
            case 2: return score2;
            case 3: return score3;
            case 4: return score4;
            default: return null;
        }
    }

    private Circle getReadyIndicatorBySlot(int slot) {
        switch (slot) {
            case 1: return readyIndicator1;
            case 2: return readyIndicator2;
            case 3: return readyIndicator3;
            case 4: return readyIndicator4;
            default: return null;
        }
    }

    private Label getEmptyIconBySlot(int slot) {
        switch (slot) {
            case 2: return emptyIcon2;
            case 3: return emptyIcon3;
            case 4: return emptyIcon4;
            default: return null;
        }
    }

    // ==================== Alert Methods ====================

    private void showInfo(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Thông báo");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    private void showWarning(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Cảnh báo");
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

    /**
     * Cleanup when leaving room
     */
    private void cleanup() {
        // Clear room chat callback
        connection.clearRoomChatCallback();

        // TODO: Unregister other server handlers
        // connection.unregisterHandler("ROOM_UPDATE");
        // connection.unregisterHandler("PLAYER_JOINED");
        // connection.unregisterHandler("PLAYER_LEFT");

        System.out.println("🧹 RoomController cleaned up");
    }

    // ==================== Inner Classes ====================

    /**
     * Friend item for ListView
     */
    private static class FriendItem {
        int userId;
        String name;
        int score;
        boolean isOnline;
        String status; // "ONLINE", "IN_GAME", "OFFLINE"
    }

    /**
     * Player info
     */
    private static class PlayerInfo {
        int userId;
        String name;
        int score;
        boolean isReady;
    }
}