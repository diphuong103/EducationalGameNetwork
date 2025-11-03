package com.edugame.client.controller;

import com.edugame.client.network.ServerConnection;
import com.edugame.client.util.AvatarUtil;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.stage.Popup;
import javafx.stage.Stage;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ChatController {

    // ============ FXML Elements - Friends List (LEFT) ============
    @FXML private TextField searchFriendField;
    @FXML private ScrollPane friendsScrollPane;
    @FXML private VBox friendsListContainer;
    @FXML private HBox systemUserItem;

    // ============ FXML Elements - Chat Area (RIGHT) ============
    @FXML private Text chatFriendName;
    @FXML private ImageView chatFriendAvatar;
    @FXML private ImageView chatFriendAvatar_List;
    @FXML private ImageView avt_in_main_chat;
    @FXML private Text chatFriendStatus;
    @FXML private ScrollPane chatMessagesScrollPane;
    @FXML private VBox chatMessagesContainer;
    @FXML private TextField messageInputField;
    @FXML private Button sendMessageButton;
    @FXML private Button emojiButton;


    // ============ Data ============
    private ServerConnection server;
    private int currentUserId;
    private int selectedFriendId = -1; // System user by default
    private String selectedFriendName = "Hệ Thống";
    private String selectedFriendAvatar = "maychu_avt.png";
    private Map<Integer, HBox> friendItemsMap = new HashMap<>();
    private static final int MESSAGE_LIMIT = 50;

    /**
     * Initialize controller
     */
    @FXML
    public void initialize() {
        this.server = ServerConnection.getInstance();

        // Get current user ID
        if (server.getCurrentUser() != null) {
            this.currentUserId = server.getCurrentUser().getUserId();
        } else {
            this.currentUserId = server.getCurrentUserId();
        }

        if (this.currentUserId == 0) {
            System.err.println("❌ [CHAT] Cannot get current user ID!");
            showError("Không thể lấy thông tin người dùng. Vui lòng đăng nhập lại!");
            return;
        }

        System.out.println("💬 [CHAT] Chat window opened. Current user ID: " + currentUserId);
        AvatarUtil.loadAvatar(chatFriendAvatar_List, selectedFriendAvatar);
        setupUI();
        loadFriendsList();
        loadSystemMessages(); // Load system messages by default
    }

    /**
     * Initialize with specific friend (for opening chat from other screens)
     * @param friendId Friend's user ID
     * @param friendName Friend's username
     * @param avatarUrl Friend's avatar URL
     * @param isOnline Friend's online status
     */
    public void initData(int friendId, String friendName, String avatarUrl, boolean isOnline) {
        System.out.println("💬 [CHAT] Opening chat with: " + friendName + " (ID=" + friendId + ")");

        // Wait for initialize to complete
        Platform.runLater(() -> {
            // Select the friend after friends list is loaded
            selectFriend(friendId, friendName, avatarUrl, isOnline);
        });
    }

    /**
     * Setup UI elements
     */
    private void setupUI() {
        // Setup search field
        searchFriendField.textProperty().addListener((obs, oldVal, newVal) -> {
            filterFriendsList(newVal);
        });

        // Setup message input - Enter to send
        messageInputField.setOnKeyPressed(event -> {
            if (event.getCode().toString().equals("ENTER")) {
                event.consume();
                handleSendMessage();
            }
        });

        // Auto-scroll chat messages
        chatMessagesContainer.heightProperty().addListener((obs, oldVal, newVal) -> {
            Platform.runLater(() -> chatMessagesScrollPane.setVvalue(1.0));
        });

        // Setup system user item click
        if (systemUserItem != null) {
            systemUserItem.setOnMouseClicked(e -> selectSystemUser());
        }
    }

    /**
     * Load friends list from server
     */
    private void loadFriendsList() {
        System.out.println("👥 [CHAT] Loading friends list...");

        server.getFriendsList(friends -> {
            Platform.runLater(() -> {
                if (friends != null && !friends.isEmpty()) {
                    displayFriendsList(friends);
                    System.out.println("✅ [CHAT] Loaded " + friends.size() + " friends");
                } else {
                    System.out.println("📭 [CHAT] No friends yet");
                }
            });
        });
    }

    /**
     * Display friends list in UI
     */
    private void displayFriendsList(List<Map<String, Object>> friends) {
        // Keep system user item, remove others
        friendsListContainer.getChildren().removeIf(node -> node != systemUserItem);
        friendItemsMap.clear();

        for (Map<String, Object> friend : friends) {
            int friendId = (int) friend.get("userId");
            String friendName = (String) friend.get("username");
            String avatarUrl = (String) friend.getOrDefault("avatarUrl", "");
            boolean isOnline = (boolean) friend.getOrDefault("isOnline", false);
            int unreadCount = (int) friend.getOrDefault("unreadCount", 0);
            String lastMessage = (String) friend.getOrDefault("lastMessage", "");
            String lastMessageTime = (String) friend.getOrDefault("lastMessageTime", "");

            HBox friendItem = createFriendItem(friendId, friendName, avatarUrl, isOnline,
                    unreadCount, lastMessage, lastMessageTime);
            friendItemsMap.put(friendId, friendItem);
            friendsListContainer.getChildren().add(friendItem);
        }
    }

    /**
     * Create friend item for list
     */
    private HBox createFriendItem(int friendId, String friendName, String avatarUrl,
                                  boolean isOnline, int unreadCount, String lastMessage,
                                  String lastMessageTime) {
        HBox friendItem = new HBox(12);
        friendItem.setAlignment(Pos.CENTER_LEFT);
        friendItem.getStyleClass().add("friend-item");
        friendItem.setPadding(new Insets(12));
        friendItem.setUserData(friendId);

        // Avatar
        StackPane avatarPane = new StackPane();
        avatarPane.getStyleClass().add("friend-avatar");
        avatarPane.setPrefSize(45, 45);

          // Ảnh đại diện
        ImageView avatarView = new ImageView();
        avatarView.setFitWidth(45);
        avatarView.setFitHeight(45);
        avatarView.setClip(new javafx.scene.shape.Circle(22.5, 22.5, 22.5)); // bo tròn

        if (avatarUrl != null && !avatarUrl.isBlank()) {
            // Dùng AvatarUtil để load ảnh từ URL, file hoặc resource
            AvatarUtil.loadAvatar(avatarView, avatarUrl);
            avatarPane.getChildren().add(avatarView);
        } else {
            // Nếu không có ảnh, hiển thị emoji
            String emoji = getEmojiForName(friendName);
            Text avatarText = new Text(emoji);
            avatarText.getStyleClass().add("avatar-icon");
            avatarText.setStyle("-fx-font-size: 24px;");
            avatarPane.getChildren().add(avatarText);
        }
        // Info
        VBox infoBox = new VBox(3);
        HBox.setHgrow(infoBox, Priority.ALWAYS);

        HBox nameBox = new HBox(6);
        nameBox.setAlignment(Pos.CENTER_LEFT);

        Text nameText = new Text(friendName);
        nameText.getStyleClass().add("friend-name");
        nameText.setStyle("-fx-font-size: 14px; -fx-font-weight: bold;");

        if (unreadCount > 0) {
            StackPane unreadBadge = new StackPane();
            unreadBadge.setStyle("-fx-background-color: #dc2626; -fx-background-radius: 10; " +
                    "-fx-min-width: 20; -fx-min-height: 20; -fx-padding: 2 6;");
            Text unreadText = new Text(String.valueOf(unreadCount));
            unreadText.setStyle("-fx-fill: white; -fx-font-size: 10px; -fx-font-weight: bold;");
            unreadBadge.getChildren().add(unreadText);
            nameBox.getChildren().addAll(nameText, unreadBadge);
        } else {
            nameBox.getChildren().add(nameText);
        }

        Text lastMsgText = new Text(lastMessage.isEmpty() ? "Chưa có tin nhắn" : lastMessage);
        lastMsgText.getStyleClass().add("last-message");
        lastMsgText.setStyle("-fx-fill: #718096; -fx-font-size: 12px;");
        lastMsgText.setWrappingWidth(180);

        infoBox.getChildren().addAll(nameBox, lastMsgText);

        // Status & Time
        VBox statusBox = new VBox(5);
        statusBox.setAlignment(Pos.TOP_RIGHT);

        Text timeText = new Text(formatTime(lastMessageTime));
        timeText.getStyleClass().add("message-time");
        timeText.setStyle("-fx-fill: #a0aec0; -fx-font-size: 11px;");

        StackPane onlineIndicator = new StackPane();
        onlineIndicator.setPrefSize(12, 12);
        Text onlineDot = new Text("●");
        onlineDot.setStyle(isOnline ? "-fx-fill: #31a24c; -fx-font-size: 16px;" :
                "-fx-fill: #cbd5e0; -fx-font-size: 16px;");
        onlineIndicator.getChildren().add(onlineDot);

        statusBox.getChildren().addAll(timeText, onlineIndicator);

        friendItem.getChildren().addAll(avatarPane, infoBox, statusBox);

        // Click handler
        friendItem.setOnMouseClicked(e -> selectFriend(friendId, friendName, avatarUrl, isOnline));

        // Hover effect
        friendItem.setOnMouseEntered(e -> {
            if (selectedFriendId != friendId) {
                friendItem.setStyle("-fx-background-color: #f7fafc; -fx-cursor: hand;");
            }
        });

        friendItem.setOnMouseExited(e -> {
            if (selectedFriendId != friendId) {
                friendItem.setStyle("-fx-background-color: transparent;");
            }
        });

        return friendItem;
    }

    /**
     * Select system user
     */
    private void selectSystemUser() {
        // Deselect all friends
        deselectAllFriends();

        // Select system user
        systemUserItem.getStyleClass().add("friend-item-selected");
        selectedFriendId = -1;
        selectedFriendName = "Hệ Thống";

        // Update chat header
        AvatarUtil.loadAvatar(chatFriendAvatar, "maychu_avt.png");
        chatFriendName.setText("Hệ Thống");
        chatFriendStatus.setText("Đang hoạt động");

        // Load system messages
        loadSystemMessages();
    }

    /**
     * Select a friend
     */
    private void selectFriend(int friendId, String friendName, String avatarUrl, boolean isOnline) {
        System.out.println("💬 [CHAT] Selected friend: " + friendName + " (ID=" + friendId + ")");

        // Deselect all
        deselectAllFriends();

        // Select this friend
        HBox friendItem = friendItemsMap.get(friendId);
        if (friendItem != null) {
            friendItem.getStyleClass().add("friend-item-selected");
            friendItem.setStyle("-fx-background-color: #eef2ff;");
        }

        selectedFriendId = friendId;
        selectedFriendName = friendName;

        // Update chat header
        String emoji = getEmojiForName(friendName);
        AvatarUtil.loadAvatar(chatFriendAvatar, avatarUrl);
        chatFriendName.setText(friendName);
        chatFriendStatus.setText(isOnline ? "Đang hoạt động" : "Offline");

        // Load messages
        loadMessages(friendId);

        // Mark as read
        markAsRead(friendId);

        // Setup listener for this friend
        setupMessageListener(friendId);
    }

    /**
     * Deselect all friends
     */
    private void deselectAllFriends() {
        systemUserItem.getStyleClass().remove("friend-item-selected");

        for (HBox item : friendItemsMap.values()) {
            item.getStyleClass().remove("friend-item-selected");
            item.setStyle("-fx-background-color: transparent;");
        }
    }

    /**
     * Load messages for selected friend
     */
    private void loadMessages(int friendId) {
        System.out.println("💬 [CHAT] Loading messages with friendId=" + friendId);

        server.getMessages(friendId, MESSAGE_LIMIT, messages -> {
            Platform.runLater(() -> {
                if (messages != null && !messages.isEmpty()) {
                    displayMessages(messages);
                    System.out.println("✅ [CHAT] Loaded " + messages.size() + " messages");
                } else {
                    System.out.println("📭 [CHAT] No messages yet");
                    showEmptyState();
                }
            });
        });
    }

    /**
     * Load system messages (welcome message)
     */
    private void loadSystemMessages() {
        chatMessagesContainer.getChildren().clear();

        VBox welcomeMsg = new VBox(8);
        welcomeMsg.getStyleClass().addAll("message-group", "message-received");

        HBox msgContainer = new HBox(8);
        msgContainer.setAlignment(Pos.CENTER_LEFT);

        // 🧩 Avatar của hệ thống
        StackPane avatar = new StackPane();
        avatar.getStyleClass().add("message-avatar");
        avatar.setPrefSize(36, 36);

        // ✅ Tạo ImageView và load avatar qua AvatarUtil
        ImageView systemAvatar = new ImageView();
        systemAvatar.setFitWidth(36);
        systemAvatar.setFitHeight(36);
        systemAvatar.setPreserveRatio(true);
        AvatarUtil.loadAvatar(systemAvatar, "maychu_avt.png");

        avatar.getChildren().add(systemAvatar);

        // 🧩 Nội dung tin nhắn
        VBox contentBox = new VBox(5);

        Text senderName = new Text("Hệ Thống");
        senderName.setStyle("-fx-fill: #667eea; -fx-font-weight: bold; -fx-font-size: 12px;");

        VBox bubble = new VBox(4);
        bubble.setStyle("""
        -fx-background-color: white;
        -fx-background-radius: 18;
        -fx-padding: 10 14;
        -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 3, 0, 0, 1);
    """);

        Text msgText = new Text("Chào mừng bạn đến với EduGame! 🎉");
        msgText.setStyle("-fx-fill: #2d3748; -fx-font-size: 14px;");
        msgText.setWrappingWidth(400);
        bubble.getChildren().add(msgText);

        Text timestamp = new Text("09:00");
        timestamp.setStyle("-fx-fill: #a0aec0; -fx-font-size: 10px;");

        contentBox.getChildren().addAll(senderName, bubble, timestamp);

        msgContainer.getChildren().addAll(avatar, contentBox);
        welcomeMsg.getChildren().add(msgContainer);

        chatMessagesContainer.getChildren().add(welcomeMsg);
    }

    /**
     * Display messages in chat area
     */
    private void displayMessages(List<Map<String, Object>> messages) {
        chatMessagesContainer.getChildren().clear();

        String lastDate = "";

        for (Map<String, Object> msg : messages) {
            int senderId = (int) msg.get("senderId");
            String content = (String) msg.get("content");
            String sentAt = (String) msg.get("sentAt");
            boolean isRead = (boolean) msg.getOrDefault("isRead", false);

            String messageDate = extractDate(sentAt);
            if (!messageDate.equals(lastDate)) {
                addDateSeparator(messageDate);
                lastDate = messageDate;
            }

            boolean isSentByMe = (senderId == currentUserId);
            String senderName = isSentByMe ? server.getCurrentUsername() : selectedFriendName;

            addChatMessage(senderName, content, isSentByMe, formatTime(sentAt), isRead);
        }

        Platform.runLater(() -> chatMessagesScrollPane.setVvalue(1.0));
    }

    /**
     * Add chat message bubble
     */
    private void addChatMessage(String username, String message, boolean isSelf, String timeStr, boolean isRead) {
        if (chatMessagesContainer == null) return;

        Platform.runLater(() -> {
            HBox messageContainer = new HBox(8);
            messageContainer.setAlignment(isSelf ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
            messageContainer.setPadding(new Insets(4, 0, 4, 0));

            VBox messageBox = new VBox(4);
            messageBox.setMaxWidth(320);
            messageBox.setStyle(isSelf ?
                    "-fx-background-color: linear-gradient(to bottom right, #667eea, #764ba2); " +
                            "-fx-background-radius: 18; -fx-padding: 10 14;" :
                    "-fx-background-color: white; " +
                            "-fx-background-radius: 18; -fx-padding: 10 14; " +
                            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 3, 0, 0, 1);");

            // Header
            HBox headerBox = new HBox(6);
            headerBox.setAlignment(Pos.CENTER_LEFT);

            if (!isSelf) {
                Text usernameText = new Text(username);
                usernameText.setStyle("-fx-fill: #667eea; -fx-font-weight: bold; -fx-font-size: 12px;");
                headerBox.getChildren().add(usernameText);
            }

            Text timeText = new Text(timeStr);
            timeText.setStyle(isSelf ?
                    "-fx-fill: rgba(255,255,255,0.7); -fx-font-size: 10px;" :
                    "-fx-fill: #a0aec0; -fx-font-size: 10px;");
            headerBox.getChildren().add(timeText);

            if (isSelf) {
                Text readStatus = new Text(isRead ? "✓✓" : "✓");
                readStatus.setStyle("-fx-fill: rgba(255,255,255,0.7); -fx-font-size: 10px;");
                headerBox.getChildren().add(readStatus);
            }

            // Message content
            FlowPane messageContent = parseMessageWithEmojiImages(message, isSelf);
            messageContent.setMaxWidth(290);

            messageBox.getChildren().addAll(headerBox, messageContent);
            messageContainer.getChildren().add(messageBox);

            chatMessagesContainer.getChildren().add(messageContainer);

            // Auto scroll
            chatMessagesScrollPane.layout();
            chatMessagesScrollPane.setVvalue(1.0);
        });
    }

    /**
     * Parse message with emoji support
     */
    private FlowPane parseMessageWithEmojiImages(String message, boolean isSelf) {
        FlowPane flowPane = new FlowPane();
        flowPane.setHgap(2);
        flowPane.setVgap(2);

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
                            isSelf ? "white" : "#2d3748"
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
                    isSelf ? "white" : "#2d3748"
            ));
            flowPane.getChildren().add(textNode);
        }

        return flowPane;
    }

    /**
     * Check if codepoint is emoji
     */
    private boolean isEmojiCodePoint(int codePoint) {
        return (codePoint >= 0x1F600 && codePoint <= 0x1F64F) ||
                (codePoint >= 0x1F300 && codePoint <= 0x1F5FF) ||
                (codePoint >= 0x1F680 && codePoint <= 0x1F6FF) ||
                (codePoint >= 0x1F1E0 && codePoint <= 0x1F1FF) ||
                (codePoint >= 0x2600 && codePoint <= 0x26FF) ||
                (codePoint >= 0x2700 && codePoint <= 0x27BF) ||
                (codePoint >= 0x1F900 && codePoint <= 0x1F9FF) ||
                (codePoint >= 0x1FA70 && codePoint <= 0x1FAFF);
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
            return imageView;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Get emoji image URL
     */
    private String getEmojiImageUrl(String emoji) {
        int codePoint = emoji.codePointAt(0);
        String hex = Integer.toHexString(codePoint);
        return "https://cdn.jsdelivr.net/gh/twitter/twemoji@14.0.2/assets/72x72/" + hex + ".png";
    }

    /**
     * Send message
     */
    @FXML
    public void handleSendMessage() {
        if (selectedFriendId == -1) {
            showError("Không thể gửi tin nhắn cho Hệ Thống!");
            return;
        }

        String content = messageInputField.getText().trim();
        if (content.isEmpty()) return;

        System.out.println("💬 [CHAT] Sending message to friendId=" + selectedFriendId);

        sendMessageButton.setDisable(true);
        messageInputField.setDisable(true);

        server.sendMessage(selectedFriendId, content, success -> {
            Platform.runLater(() -> {
                if (success) {
                    String timeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
                    addChatMessage(server.getCurrentUsername(), content, true, timeStr, false);
                    messageInputField.clear();
                    Platform.runLater(() -> chatMessagesScrollPane.setVvalue(1.0));
                } else {
                    showError("Không thể gửi tin nhắn!");
                }

                sendMessageButton.setDisable(false);
                messageInputField.setDisable(false);
                messageInputField.requestFocus();
            });
        });
    }

    /**
     * Show emoji picker
     */
    @FXML
    public void handleShowEmoji() {
        Popup emojiPopup = new Popup();

        FlowPane emojiPane = new FlowPane(5, 5);
        emojiPane.setPadding(new Insets(10));
        emojiPane.setStyle("""
            -fx-background-color: white;
            -fx-border-color: #ddd;
            -fx-border-radius: 12;
            -fx-background-radius: 12;
            -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 8, 0, 0, 2);
        """);
        emojiPane.setPrefSize(320, 220);

        String[] emojis = {
                "😀","😃","😄","😁","😆","😅","🤣","😂","🙂","🙃",
                "😉","😊","😇","🥰","😍","🤩","😘","😗","😚","😙",
                "😋","😛","😜","🤪","😝","🤑","🤗","🤭","🤫","🤔",
                "👍","👎","👌","✌","🤞","🙏","💪","❤","💙","💚",
                "🎉","🎊","🎁","🎈","🎂","🏆","🔥","⭐","✨","💯"
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
                int caretPos = messageInputField.getCaretPosition();
                String text = messageInputField.getText();
                String newText = text.substring(0, caretPos) + emoji + text.substring(caretPos);
                messageInputField.setText(newText);
                messageInputField.positionCaret(caretPos + emoji.length());
                emojiPopup.hide();
                messageInputField.requestFocus();
            });

            emojiPane.getChildren().add(emojiContainer);
        }

        ScrollPane scrollPane = new ScrollPane(emojiPane);
        scrollPane.setStyle("-fx-background: white;");
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefViewportHeight(220);
        emojiPopup.getContent().add(scrollPane);
        emojiPopup.setAutoHide(true);

        javafx.geometry.Point2D point = emojiButton.localToScreen(0, 0);
        emojiPopup.show(emojiButton, point.getX(), point.getY() - 230);
    }

    /**
     * Setup real-time message listener
     */
    private void setupMessageListener(int friendId) {
        server.addPrivateChatListener(friendId, message -> {
            int senderId = (int) message.get("senderId");
            if (senderId == friendId && selectedFriendId == friendId) {
                Platform.runLater(() -> {
                    String content = (String) message.get("content");
                    String sentAt = (String) message.get("sentAt");
                    addChatMessage(selectedFriendName, content, false, formatTime(sentAt), false);
                    markAsRead(friendId);
                });
            }
        });
    }

    /**
     * Mark messages as read
     */
    private void markAsRead(int friendId) {
        server.markMessagesAsRead(friendId);
    }

    /**
     * Add date separator
     */
    private void addDateSeparator(String date) {
        HBox separator = new HBox();
        separator.setAlignment(Pos.CENTER);
        separator.setPadding(new Insets(10, 0, 10, 0));

        Label dateLabel = new Label(date);
        dateLabel.setStyle("""
            -fx-background-color: rgba(0,0,0,0.05);
            -fx-padding: 5 15;
            -fx-background-radius: 10;
            -fx-text-fill: #718096;
            -fx-font-size: 12px;
        """);
        separator.getChildren().add(dateLabel);
        chatMessagesContainer.getChildren().add(separator);
    }

    /**
     * Filter friends list
     */
    private void filterFriendsList(String searchText) {
        if (searchText == null || searchText.trim().isEmpty()) {
            friendItemsMap.values().forEach(item -> item.setVisible(true));
            return;
        }

        String search = searchText.toLowerCase();
        friendItemsMap.forEach((id, item) -> {
            VBox infoBox = (VBox) item.getChildren().get(1);
            HBox nameBox = (HBox) infoBox.getChildren().get(0);
            Text nameText = (Text) nameBox.getChildren().get(0);
            String name = nameText.getText().toLowerCase();
            item.setVisible(name.contains(search));
        });
    }

    /**
     * Refresh friends list
     */
    @FXML
    public void handleRefresh() {
        System.out.println("🔄 [CHAT] Refreshing...");
        loadFriendsList();
        if (selectedFriendId > 0) {
            loadMessages(selectedFriendId);
        }
    }

    /**
     * Back to main menu
     */
    @FXML
    public void handleBack() {
        Stage stage = (Stage) chatMessagesContainer.getScene().getWindow();
        stage.close();
    }

    /**
     * Show empty state
     */
    private void showEmptyState() {
        VBox emptyState = new VBox(20);
        emptyState.setAlignment(Pos.CENTER);
        emptyState.setPadding(new Insets(50));

        Label emoji = new Label("💬");
        emoji.setStyle("-fx-font-size: 48px;");

        Label text = new Label("Chưa có tin nhắn nào");
        text.setStyle("-fx-font-size: 16px; -fx-text-fill: #a0aec0;");

        Label subtext = new Label("Hãy bắt đầu cuộc trò chuyện!");
        subtext.setStyle("-fx-font-size: 14px; -fx-text-fill: #cbd5e0;");

        emptyState.getChildren().addAll(emoji, text, subtext);
        chatMessagesContainer.getChildren().add(emptyState);
    }

    /**
     * Show error alert
     */
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Lỗi");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Format time string
     */
    private String formatTime(String sentAt) {
        if (sentAt == null || sentAt.isEmpty()) {
            return "";
        }
        try {
            LocalDateTime dateTime = LocalDateTime.parse(sentAt);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
            return dateTime.format(formatter);
        } catch (Exception e) {
            return sentAt;
        }
    }

    /**
     * Extract date for separator
     */
    private String extractDate(String sentAt) {
        if (sentAt == null || sentAt.isEmpty()) {
            return "";
        }
        try {
            LocalDateTime dateTime = LocalDateTime.parse(sentAt);
            LocalDateTime now = LocalDateTime.now();

            if (dateTime.toLocalDate().equals(now.toLocalDate())) {
                return "Hôm nay";
            } else if (dateTime.toLocalDate().equals(now.toLocalDate().minusDays(1))) {
                return "Hôm qua";
            } else {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
                return dateTime.format(formatter);
            }
        } catch (Exception e) {
            return sentAt;
        }
    }

    /**
     * Get emoji for username
     */
    private String getEmojiForName(String name) {
        String[] emojis = {"👤", "👨", "👩", "🧑", "👦", "👧", "🧒", "👶", "🐱", "🐶",
                "🐼", "🐻", "🦊", "🦁", "🐯", "🐨", "🐰", "🐹", "🐸", "🐵"};
        int index = Math.abs(name.hashCode() % emojis.length);
        return emojis[index];
    }

    /**
     * Cleanup when closing chat window
     * Remove all listeners and clean up resources
     */
    public void cleanup() {
        System.out.println("🧹 [CHAT] Cleaning up chat controller...");

        // Remove listener if a friend was selected
        if (selectedFriendId > 0) {
            server.removePrivateChatListener(selectedFriendId);
            System.out.println("✅ [CHAT] Removed listener for friendId=" + selectedFriendId);
        }

        // Clear data
        friendItemsMap.clear();
        selectedFriendId = -1;
        selectedFriendName = null;

        System.out.println("✅ [CHAT] Cleanup completed");
    }
}