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

    @FXML private ImageView friendAvatar;
    @FXML private Label friendNameLabel;
    @FXML private Label friendStatusLabel;
    @FXML private ScrollPane messagesScrollPane;
    @FXML private VBox messagesContainer;
    @FXML private TextField messageInput;
    @FXML private Button sendButton;
    @FXML private Button emojiButton;
    @FXML private Button refreshButton;
    @FXML private Button closeButton;

    private ServerConnection server;
    private int friendId;
    private String friendName;
    private String friendAvatarUrl;
    private boolean isOnline;
    private int currentUserId;

    private static final int MESSAGE_LIMIT = 50;
    private List<Map<String, Object>> allMessages = new ArrayList<>();

    /**
     * Initialize với friend info
     */
    public void initData(int friendId, String friendName, String avatarUrl, boolean isOnline) {
        this.friendId = friendId;
        this.friendName = friendName;
        this.friendAvatarUrl = avatarUrl;
        this.isOnline = isOnline;
        this.server = ServerConnection.getInstance();

        // ✅ Kiểm tra và lấy userId an toàn
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

        System.out.println("💬 [CHAT] Opening chat with: " + friendName + " (ID=" + friendId + ")");
        System.out.println("💬 [CHAT] Current user ID: " + currentUserId);

        setupUI();
        loadMessages();
        setupMessageListener(); // ✅ Setup listener TRƯỚC khi mark as read
        markAsRead();

        Platform.runLater(() -> messagesScrollPane.setVvalue(1.0));
    }

    /**
     * Setup UI elements
     */
    private void setupUI() {
        friendNameLabel.setText(friendName);
        friendStatusLabel.setText(isOnline ? "Đang online" : "Offline");

        Text onlineStatus = new Text("●");
        onlineStatus.setStyle(isOnline
                ? "-fx-fill: #4CAF50;"
                : "-fx-fill: #9E9E9E;"
        );

        friendStatusLabel.setGraphic(onlineStatus);
        friendStatusLabel.setContentDisplay(ContentDisplay.LEFT);
        friendStatusLabel.setGraphicTextGap(4);

        if (friendAvatarUrl != null && !friendAvatarUrl.isEmpty()) {
            AvatarUtil.loadAvatar(friendAvatar, friendAvatarUrl);
        }

        // Enter to send message
        messageInput.setOnKeyPressed(event -> {
            if (event.getCode().toString().equals("ENTER")) {
                event.consume();
                handleSendMessage();
            }
        });

        // Auto-scroll
        messagesContainer.heightProperty().addListener((obs, oldVal, newVal) -> {
            Platform.runLater(() -> messagesScrollPane.setVvalue(1.0));
        });
    }

    /**
     * Load messages from server
     */
    private void loadMessages() {
        System.out.println("💬 [CHAT] Loading messages...");
        System.out.println("💬 [CHAT] friendId=" + friendId + ", currentUserId=" + currentUserId);

        server.getMessages(friendId, MESSAGE_LIMIT, messages -> {
            Platform.runLater(() -> {
                if (messages != null && !messages.isEmpty()) {
                    allMessages.clear();
                    allMessages.addAll(messages);
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
     * Display messages in UI
     */
    private void displayMessages(List<Map<String, Object>> messages) {
        messagesContainer.getChildren().clear();

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

            String senderName = (senderId == currentUserId)
                    ? server.getCurrentUsername()
                    : friendName;
            boolean isSentByMe = (senderId == currentUserId);
            addChatMessage(senderName, content, isSentByMe, formatTime(sentAt), isRead);

        }
    }

    /**
     * Add chat message with emoji support
     */
    private void addChatMessage(String username, String message, boolean isSelf, String timeStr, boolean isRead) {
        if (messagesContainer == null) return;

        Platform.runLater(() -> {
            HBox messageContainer = new HBox(8);
            messageContainer.setAlignment(isSelf ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
            messageContainer.setPadding(new Insets(4, 0, 4, 0));

            VBox messageBox = new VBox(4);
            messageBox.setMaxWidth(320);
            messageBox.setStyle(isSelf ?
                    "-fx-background-color: linear-gradient(to bottom right, #667eea, #764ba2); " +
                            "-fx-background-radius: 18; -fx-padding: 10 14 10 14;" :
                    "-fx-background-color: white; " +
                            "-fx-background-radius: 18; -fx-padding: 10 14 10 14; " +
                            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 3, 0, 0, 1);");

            // Header with username and time
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

            // Read status for sent messages
            if (isSelf) {
                Text readStatus = new Text(isRead ? "✓✓" : "✓");
                readStatus.setStyle("-fx-fill: rgba(255,255,255,0.7); -fx-font-size: 10px;");
                headerBox.getChildren().add(readStatus);
            } else {
                Text onlineDot = new Text("●");
                onlineDot.setStyle("-fx-fill: #31a24c; -fx-font-size: 8px;");
                headerBox.getChildren().add(onlineDot);
            }

            // ✅ Message content with emoji support
            FlowPane messageContent = parseMessageWithEmojiImages(message, isSelf);
            messageContent.setMaxWidth(290);
            messageContent.setHgap(2);
            messageContent.setVgap(2);

            messageBox.getChildren().addAll(headerBox, messageContent);
            messageContainer.getChildren().add(messageBox);

            messagesContainer.getChildren().add(messageContainer);

            // Limit messages
            if (messagesContainer.getChildren().size() > 100) {
                messagesContainer.getChildren().remove(0);
            }

            // Auto scroll
            messagesScrollPane.layout();
            messagesScrollPane.setVvalue(1.0);
        });
    }

    /**
     * ✅ Parse message with emoji images (from global chat)
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
                // Add buffered text first
                if (textBuffer.length() > 0) {
                    Text textNode = new Text(textBuffer.toString());
                    textNode.setStyle(String.format(
                            "-fx-font-size: 14px; -fx-fill: %s;",
                            isSelf ? "white" : "#2d3748"
                    ));
                    flowPane.getChildren().add(textNode);
                    textBuffer = new StringBuilder();
                }

                // Add emoji image
                ImageView emojiView = createEmojiImageView(currentChar, 20);
                if (emojiView != null) {
                    flowPane.getChildren().add(emojiView);
                } else {
                    // Fallback to text
                    Text emojiText = new Text(currentChar);
                    emojiText.setStyle("-fx-font-size: 18px;");
                    flowPane.getChildren().add(emojiText);
                }
            } else {
                textBuffer.append(currentChar);
            }

            i += charCount;
        }

        // Add remaining text
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
     * ✅ Check if codepoint is emoji
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
     * ✅ Create emoji ImageView from Twemoji CDN
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

    /**
     * ✅ Get emoji image URL from Twemoji CDN
     */
    private String getEmojiImageUrl(String emoji) {
        int codePoint = emoji.codePointAt(0);
        String hex = Integer.toHexString(codePoint);
        return "https://cdn.jsdelivr.net/gh/twitter/twemoji@14.0.2/assets/72x72/" + hex + ".png";
    }

    /**
     * ✅ Show emoji picker popup (from global chat)
     */
    @FXML
    private void handleShowEmoji() {
        if (emojiButton == null || messageInput == null) return;

        Popup emojiPopup = new Popup();

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
                int savedCaretPos = messageInput.getCaretPosition();
                String currentText = messageInput.getText();

                String beforeCaret = currentText.substring(0, savedCaretPos);
                String afterCaret = currentText.substring(savedCaretPos);
                String newText = beforeCaret + afterCaret + emoji;

                messageInput.setText(newText);
                int newCaretPos = savedCaretPos + emoji.length();

                emojiPopup.hide();

                Platform.runLater(() -> {
                    messageInput.requestFocus();
                    messageInput.positionCaret(newCaretPos);
                    messageInput.deselect();
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
     * Send message
     */
    @FXML
    private void handleSendMessage() {
        String content = messageInput.getText().trim();

        if (content.isEmpty()) {
            return;
        }

        System.out.println("💬 [CHAT] Sending message to friendId=" + friendId + ": " + content);

        sendButton.setDisable(true);
        messageInput.setDisable(true);

        server.sendMessage(friendId, content, success -> {
            Platform.runLater(() -> {
                if (success) {
                    System.out.println("✅ [CHAT] Message sent successfully");

                    // ✅ Add message to UI immediately (as SENT by me)
                    String timeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
                    addChatMessage(server.getCurrentUsername(), content, true, timeStr, false);

                    // ✅ Clear input
                    messageInput.clear();

                    // ✅ Scroll to bottom
                    Platform.runLater(() -> messagesScrollPane.setVvalue(1.0));
                } else {
                    System.err.println("❌ [CHAT] Failed to send message");
                    showError("Không thể gửi tin nhắn. Vui lòng thử lại.");
                }

                sendButton.setDisable(false);
                messageInput.setDisable(false);
                messageInput.requestFocus();
            });
        });
    }


    /**
     * Setup real-time message listener
     */
    private void setupMessageListener() {
        // ✅ Register listener cho friend này
        server.addPrivateChatListener(friendId, message -> {
            try {
                int senderId = (int) message.get("senderId");

                // ✅ Chỉ xử lý nếu là từ friend đang chat
                if (senderId == friendId) {
                    Platform.runLater(() -> {
                        System.out.println("📨 [CHAT] New message received from: " + friendName);

                        String content = (String) message.get("content");
                        String sentAt = (String) message.get("sentAt");

                        // ✅ Thêm message vào UI
                        addChatMessage(friendName, content, false, formatTime(sentAt), false);

                        // ✅ Mark as read ngay
                        markAsRead();

                        // ✅ Scroll to bottom
                        Platform.runLater(() -> messagesScrollPane.setVvalue(1.0));
                    });
                } else {
                    System.out.println("⏭️ [CHAT] Message from different user (ID=" + senderId + "), ignoring");
                }
            } catch (Exception e) {
                System.err.println("❌ [CHAT] Error handling new message: " + e.getMessage());
                e.printStackTrace();
            }
        });

        System.out.println("✅ [CHAT] Real-time listener registered for friendId=" + friendId);
    }


    /**
     * Add date separator
     */
    private void addDateSeparator(String date) {
        HBox separator = new HBox();
        separator.getStyleClass().add("date-separator");
        separator.setAlignment(Pos.CENTER);
        separator.setPadding(new Insets(10, 0, 10, 0));

        Label dateLabel = new Label(date);
        dateLabel.setStyle("""
            -fx-background-color: rgba(0,0,0,0.05);
            -fx-padding: 5 15 5 15;
            -fx-background-radius: 10;
            -fx-text-fill: #718096;
            -fx-font-size: 12px;
        """);
        separator.getChildren().add(dateLabel);

        messagesContainer.getChildren().add(separator);
    }

    /**
     * Mark messages as read
     */
    private void markAsRead() {
        server.markMessagesAsRead(friendId);
    }

    /**
     * Refresh messages
     */
    @FXML
    private void handleRefresh() {
        System.out.println("🔄 [CHAT] Refreshing messages...");
        loadMessages();
    }

    /**
     * Close chat window và cleanup
     */
    @FXML
    private void handleClose() {
        System.out.println("🔒 [CHAT] Closing chat window with: " + friendName + " (friendId=" + friendId + ")");

        // ✅ Remove listener cho friend này
        server.removePrivateChatListener(friendId);

        Stage stage = (Stage) closeButton.getScene().getWindow();
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

        messagesContainer.getChildren().add(emptyState);
    }

    /**
     * Show error
     */
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Lỗi");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /**
     * Format time
     */
    private String formatTime(String sentAt) {
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
}