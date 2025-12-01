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
import javafx.scene.shape.Circle;
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
    @FXML private HBox aiChatItem;

    // ============ FXML Elements - Chat Area (RIGHT) ============
    @FXML private Text chatFriendName;
    @FXML private ImageView chatFriendAvatar;
    @FXML private ImageView chatFriendAvatar_List;
    @FXML private ImageView chatFriendAi_List;
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
    private int selectedFriendId = -1; // -1: System, -2: AI Chat, >0: Real friend
    private String selectedFriendName = "Hệ Thống";
    private String selectedFriendAvatar = "may_chu.png";
    private Map<Integer, HBox> friendItemsMap = new HashMap<>();
    private static final int MESSAGE_LIMIT = 50;

    private static final int AI_CHAT_ID = -2;
    private static final String AI_CHAT_NAME = "Chat AI";
    private static final String AI_CHAT_AVATAR = "chat_ai.png"; // Ảnh đại diện cho AI

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
        AvatarUtil.loadAvatar(chatFriendAi_List, AI_CHAT_AVATAR);
        setupUI();
        loadFriendsList();
        loadSystemMessages();
        setupGlobalServerMessageListener();
    }

    /**
     * Initialize with specific friend
     */
    public void initData(int friendId, String friendName, String avatarUrl, boolean isOnline) {
        System.out.println("💬 [CHAT] Opening chat with: " + friendName + " (ID=" + friendId + ")");
        Platform.runLater(() -> {
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

        // Setup message input
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

        // Setup AI chat item click
        if (aiChatItem != null) {
            aiChatItem.setOnMouseClicked(e -> selectAIChat());

            // Hover effect for AI Chat
            aiChatItem.setOnMouseEntered(e -> {
                if (selectedFriendId != -2) {
                    aiChatItem.setStyle("-fx-background-color: #f7fafc; -fx-cursor: hand;");
                }
            });

            aiChatItem.setOnMouseExited(e -> {
                if (selectedFriendId != -2) {
                    aiChatItem.setStyle("-fx-background-color: transparent;");
                }
            });
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
        // Keep system user and AI chat items, remove others
        friendsListContainer.getChildren().removeIf(node ->
                node != systemUserItem && node != aiChatItem);
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

        ImageView avatarView = new ImageView();
        avatarView.setFitWidth(45);
        avatarView.setFitHeight(45);
        avatarView.setClip(new javafx.scene.shape.Circle(22.5, 22.5, 22.5));

        if (avatarUrl != null && !avatarUrl.isBlank()) {
            AvatarUtil.loadAvatar(avatarView, avatarUrl);
            avatarPane.getChildren().add(avatarView);
        } else {
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

        String displayMessage;
        if (lastMessage == null || lastMessage.trim().isEmpty()) {
            displayMessage = "Chưa có tin nhắn";
        } else {
            displayMessage = lastMessage.length() > 30 ?
                    lastMessage.substring(0, 30) + "..." : lastMessage;
        }

        Text lastMsgText = new Text(displayMessage);
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
     * Update friend item's last message
     */
    private void updateFriendLastMessage(int friendId, String message, String time) {
        HBox friendItem = friendItemsMap.get(friendId);
        if (friendItem != null) {
            Platform.runLater(() -> {
                VBox infoBox = (VBox) friendItem.getChildren().get(1);
                Text lastMsgText = (Text) infoBox.getChildren().get(1);

                String displayMessage = message.length() > 30 ?
                        message.substring(0, 30) + "..." : message;
                lastMsgText.setText(displayMessage);

                // Update time
                VBox statusBox = (VBox) friendItem.getChildren().get(2);
                if (statusBox.getChildren().size() > 0) {
                    Text timeText = (Text) statusBox.getChildren().get(0);
                    timeText.setText(formatTime(time));
                }
            });
        }
    }

    /**
     * Select system user
     */
    private void selectSystemUser() {
        deselectAllFriends();

        systemUserItem.getStyleClass().add("friend-item-selected");
        selectedFriendId = -1;
        selectedFriendName = "Hệ Thống";
        selectedFriendAvatar = "may_chu.png";

        StackPane currentAvatarContainer = (StackPane) chatFriendAvatar.getParent();
        currentAvatarContainer.getChildren().clear();
        currentAvatarContainer.getChildren().add(chatFriendAvatar);

        AvatarUtil.loadAvatar(chatFriendAvatar, selectedFriendAvatar);
        chatFriendName.setText("Hệ Thống");
        chatFriendName.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");
        chatFriendStatus.setText("Đang hoạt động");
        chatFriendStatus.setStyle("-fx-fill: #718096; -fx-font-size: 12px;");

        // ✅ CHỈ LOAD TIN NHẮN - Listener đã được setup ở initialize()
        loadSystemMessages();

        // ✅ CLEAR UNREAD BADGE
        clearSystemUserUnreadBadge();
    }

    private void clearSystemUserUnreadBadge() {
        if (systemUserItem != null) {
            systemUserItem.getChildren().removeIf(node ->
                    node.getStyleClass().contains("unread-badge"));
        }
    }

    private void setupGlobalServerMessageListener() {
        System.out.println("🔔 [CHAT] Setting up GLOBAL server message listener...");

        // ✅ ĐĂNG KÝ CALLBACK TOÀN CỤC (chỉ 1 lần khi mở chat)
        server.setServerMessageCallback(message -> {
            Platform.runLater(() -> {
                try {
                    String messageType = (String) message.get("messageType");
                    String senderName = (String) message.get("senderName");
                    String content = (String) message.get("content");
                    String sentAt = (String) message.get("sentAt");
                    boolean isImportant = (boolean) message.getOrDefault("isImportant", false);
                    int messageId = (int) message.get("messageId");

                    System.out.println("📨 [CHAT] New server message received (GLOBAL)");
                    System.out.println("   Type: " + messageType);
                    System.out.println("   Important: " + isImportant);
                    System.out.println("   Content: " + content.substring(0, Math.min(50, content.length())));
                    System.out.println("   Current selectedFriendId: " + selectedFriendId);

                    // ✅ NẾU ĐANG XEM SYSTEM USER → HIỂN THỊ NGAY
                    if (selectedFriendId == -1) {
                        System.out.println("   ✅ Currently viewing System User - displaying message");
                        addServerMessage(senderName, content, sentAt, isImportant, messageType);

                        // Auto scroll to bottom
                        Platform.runLater(() -> {
                            chatMessagesScrollPane.layout();
                            chatMessagesScrollPane.setVvalue(1.0);
                        });

                        // Mark as read
                        server.markServerMessageAsRead(messageId);

                    } else {
                        // ✅ ĐANG XEM FRIEND KHÁC → SHOW NOTIFICATION + UPDATE BADGE
                        System.out.println("   ℹ️ Currently viewing other friend - showing notification");
                        showSystemMessageNotification(content, isImportant);

                        // ✅ UPDATE UNREAD BADGE CHO SYSTEM USER
                        updateSystemUserUnreadBadge();
                    }

                } catch (Exception e) {
                    System.err.println("❌ [CHAT] Error handling server message: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        });

        System.out.println("✅ [CHAT] GLOBAL server message listener registered");
    }

    private void showSystemMessageNotification(String content, boolean isImportant) {
        // ✅ TẠO NOTIFICATION TOAST (không block UI)
        javafx.stage.Stage stage = (javafx.stage.Stage) chatMessagesContainer.getScene().getWindow();

        // Simple alert cho đơn giản
        javafx.scene.control.Alert notification = new javafx.scene.control.Alert(
                javafx.scene.control.Alert.AlertType.INFORMATION
        );
        notification.setTitle(isImportant ? "⭐ Thông báo quan trọng" : "📢 Thông báo hệ thống");
        notification.setHeaderText(null);
        notification.setContentText(content.substring(0, Math.min(100, content.length())) +
                (content.length() > 100 ? "..." : ""));

        // Non-blocking show
        notification.show();

        // Auto close after 5 seconds
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                Platform.runLater(() -> notification.close());
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }


    private void updateSystemUserUnreadBadge() {
//        // ✅ LẤY SỐ TIN NHẮN CHƯA ĐỌC
//        server.getUnreadServerMessageCount(count -> {
//            Platform.runLater(() -> {
//                if (count > 0) {
//                    // ✅ HIỂN THỊ BADGE TRÊN SYSTEM USER ITEM
//                    addUnreadBadgeToSystemUser(count);
//                }
//            });
//        });
    }

    /**
     * ✅ THÊM METHOD addUnreadBadgeToSystemUser() - Thêm badge số tin chưa đọc
     */
    private void addUnreadBadgeToSystemUser(int count) {
        if (systemUserItem == null) return;

        // Remove old badge if exists
        systemUserItem.getChildren().removeIf(node ->
                node.getStyleClass().contains("unread-badge"));

        // Create new badge
        StackPane badge = new StackPane();
        badge.getStyleClass().add("unread-badge");
        badge.setStyle("""
        -fx-background-color: #dc2626;
        -fx-background-radius: 10;
        -fx-min-width: 20;
        -fx-min-height: 20;
        -fx-padding: 2 6;
    """);

        Text badgeText = new Text(String.valueOf(count));
        badgeText.setStyle("-fx-fill: white; -fx-font-size: 10px; -fx-font-weight: bold;");
        badge.getChildren().add(badgeText);

        // Add badge to system user item
        systemUserItem.getChildren().add(badge);
    }

    /**
     * Select AI Chat
     */
    private void selectAIChat() {
        System.out.println("🤖 [CHAT] Selected AI Chat");

        deselectAllFriends();

        aiChatItem.getStyleClass().add("friend-item-selected");
        aiChatItem.setStyle("-fx-background-color: #eef2ff;");

        selectedFriendId = AI_CHAT_ID;
        selectedFriendName = AI_CHAT_NAME;

        // Update chat header with AI styling
        StackPane currentAvatarContainer = (StackPane) chatFriendAvatar.getParent();

        // Cập nhật header
        AvatarUtil.loadAvatar(chatFriendAvatar, AI_CHAT_AVATAR);
        chatFriendName.setText(AI_CHAT_NAME);
        chatFriendStatus.setText("Luôn sẵn sàng 🤖");

        chatFriendName.setText("Chat AI");
        chatFriendName.setStyle("-fx-font-size: 15px; -fx-font-weight: bold; -fx-fill: #667eea;");
        chatFriendStatus.setText("Trợ lý AI sẵn sàng");
        chatFriendStatus.setStyle("-fx-fill: #667eea; -fx-font-size: 12px;");

        loadAIIntroMessage();
    }

    /**
     * Select a friend
     */
    private void selectFriend(int friendId, String friendName, String avatarUrl, boolean isOnline) {
        System.out.println("💬 [CHAT] Selected friend: " + friendName + " (ID=" + friendId + ")");

        deselectAllFriends();

        HBox friendItem = friendItemsMap.get(friendId);
        if (friendItem != null) {
            friendItem.getStyleClass().add("friend-item-selected");
            friendItem.setStyle("-fx-background-color: #eef2ff;");
        }

        selectedFriendId = friendId;
        selectedFriendName = friendName;

        // Restore normal header with ImageView
        StackPane currentAvatarContainer = (StackPane) chatFriendAvatar.getParent();
        currentAvatarContainer.getChildren().clear();
        currentAvatarContainer.getChildren().add(chatFriendAvatar);

        AvatarUtil.loadAvatar(chatFriendAvatar, avatarUrl);
        chatFriendName.setText(friendName);
        chatFriendName.setStyle("-fx-font-size: 15px; -fx-font-weight: bold;");
        chatFriendStatus.setText(isOnline ? "Đang hoạt động" : "Offline");
        chatFriendStatus.setStyle("-fx-fill: #718096; -fx-font-size: 12px;");

        // ✅ CLEAR TIN NHẮN CŨ TRƯỚC KHI LOAD MỚI
        chatMessagesContainer.getChildren().clear();

        loadMessages(friendId);
        markAsRead(friendId);
        setupMessageListener(friendId);
    }

    /**
     * Deselect all friends
     */
    private void deselectAllFriends() {
        systemUserItem.getStyleClass().remove("friend-item-selected");

        if (aiChatItem != null) {
            aiChatItem.getStyleClass().remove("friend-item-selected");
            aiChatItem.setStyle("-fx-background-color: transparent;");
        }

        for (HBox item : friendItemsMap.values()) {
            item.getStyleClass().remove("friend-item-selected");
            item.setStyle("-fx-background-color: transparent;");
        }
    }

    /**
     * Load AI intro message
     */
    private void loadAIIntroMessage() {
        chatMessagesContainer.getChildren().clear();

        VBox welcomeMsg = new VBox(8);
        welcomeMsg.getStyleClass().addAll("message-group", "message-received");

        HBox msgContainer = new HBox(8);
        msgContainer.setAlignment(Pos.CENTER_LEFT);

        // 🔹 Tạo StackPane chứa avatar AI
        StackPane avatarPane = new StackPane();
        avatarPane.setPrefSize(36, 36);
        avatarPane.getStyleClass().add("message-avatar");

        // 🔹 Tạo ImageView cho avatar AI (sử dụng AvatarUtil để đảm bảo load được file cục bộ hoặc URL)
        ImageView aiAvatar = new ImageView();
        AvatarUtil.loadAvatar(aiAvatar, "chat_ai.png"); // hoặc URL ảnh AI
        aiAvatar.setFitWidth(36);
        aiAvatar.setFitHeight(36);
        aiAvatar.setClip(new Circle(18, 18, 18));
        avatarPane.getChildren().add(aiAvatar);

        // 🔹 Nội dung tin nhắn chào
        VBox contentBox = new VBox(5);
        Text senderName = new Text("Chat AI");
        senderName.setStyle("-fx-fill: #667eea; -fx-font-weight: bold; -fx-font-size: 12px;");

        VBox bubble = new VBox(4);
        bubble.setStyle("""
            -fx-background-color: white;
            -fx-background-radius: 18;
            -fx-padding: 10 14;
            -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 3, 0, 0, 1);
            """);

        Text msgText = new Text("Xin chào 👋! Tôi là Chat AI — bạn có thể hỏi tôi bất cứ điều gì.");
        msgText.setStyle("-fx-fill: #2d3748; -fx-font-size: 14px;");
        msgText.setWrappingWidth(400);
        bubble.getChildren().add(msgText);

        Text timestamp = new Text("09:00");
        timestamp.setStyle("-fx-fill: #a0aec0; -fx-font-size: 10px;");

        contentBox.getChildren().addAll(senderName, bubble, timestamp);
        msgContainer.getChildren().addAll(avatarPane, contentBox);
        welcomeMsg.getChildren().add(msgContainer);

        chatMessagesContainer.getChildren().add(welcomeMsg);
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
                    // ✅ CHỈ XÓA TIN NHẮN CŨ, KHÔNG HIỂN THỊ EMPTY STATE
                    chatMessagesContainer.getChildren().clear();
                    System.out.println("📭 [CHAT] No messages yet");
                }
            });
        });
    }

    /**
     * Load system messages
     */
    private void loadSystemMessages() {
        System.out.println("📨 [CHAT] Loading server messages...");

        chatMessagesContainer.getChildren().clear();

        // ✅ GỌI API LẤY TIN NHẮN TỪ SERVER
        server.getServerMessages(50, messages -> {
            Platform.runLater(() -> {
                if (messages != null && !messages.isEmpty()) {
                    System.out.println("✅ [CHAT] Loaded " + messages.size() + " server messages");

                    // ✅ HIỂN THỊ TIN NHẮN
                    String lastDate = "";

                    for (Map<String, Object> msg : messages) {
                        String messageType = (String) msg.get("messageType");
                        String senderName = (String) msg.get("senderName");
                        String content = (String) msg.get("content");
                        String sentAt = (String) msg.get("sentAt");
                        boolean isImportant = (boolean) msg.getOrDefault("isImportant", false);

                        // Date separator
                        String messageDate = extractDate(sentAt);
                        if (!messageDate.equals(lastDate)) {
                            addDateSeparator(messageDate);
                            lastDate = messageDate;
                        }

                        // ✅ HIỂN THỊ TIN NHẮN TỪ SERVER
                        addServerMessage(senderName, content, sentAt, isImportant, messageType);
                    }

                    Platform.runLater(() -> chatMessagesScrollPane.setVvalue(1.0));

                } else {
                    // ✅ NẾU CHƯA CÓ TIN NHẮN, HIỂN THỊ WELCOME MESSAGE
                    showServerWelcomeMessage();
                }
            });
        });
    }

    private void addServerMessage(String senderName, String content, String sentAt,
                                  boolean isImportant, String messageType) {
        VBox messageGroup = new VBox(8);
        messageGroup.getStyleClass().add("message-group");
        messageGroup.setPadding(new Insets(4, 0, 4, 0));

        HBox msgContainer = new HBox(8);
        msgContainer.setAlignment(Pos.CENTER_LEFT);

        // ✅ AVATAR HỆ THỐNG
        StackPane avatarPane = new StackPane();
        avatarPane.setPrefSize(36, 36);
        avatarPane.getStyleClass().add("message-avatar");

        ImageView systemAvatar = new ImageView();
        systemAvatar.setFitWidth(36);
        systemAvatar.setFitHeight(36);
        systemAvatar.setPreserveRatio(true);

        // ✅ CHỌN AVATAR DỰA TRÊN MESSAGE TYPE
        String avatarFile = getAvatarForMessageType(messageType);
        AvatarUtil.loadAvatar(systemAvatar, avatarFile);

        Circle clip = new Circle(18, 18, 18);
        systemAvatar.setClip(clip);
        avatarPane.getChildren().add(systemAvatar);

        // ✅ NỘI DUNG TIN NHẮN
        VBox contentBox = new VBox(5);
        VBox.setVgrow(contentBox, Priority.ALWAYS);

        // Sender name
        HBox headerBox = new HBox(8);
        headerBox.setAlignment(Pos.CENTER_LEFT);

        Text senderText = new Text(senderName);
        senderText.setStyle("-fx-fill: #667eea; -fx-font-weight: bold; -fx-font-size: 12px;");

        // ✅ IMPORTANT BADGE
        if (isImportant) {
            StackPane importantBadge = new StackPane();
            importantBadge.setStyle("""
            -fx-background-color: #dc2626;
            -fx-background-radius: 8;
            -fx-padding: 2 8;
        """);
            Text importantText = new Text("⭐ QUAN TRỌNG");
            importantText.setStyle("-fx-fill: white; -fx-font-size: 9px; -fx-font-weight: bold;");
            importantBadge.getChildren().add(importantText);
            headerBox.getChildren().addAll(senderText, importantBadge);
        } else {
            headerBox.getChildren().add(senderText);
        }

        // ✅ TYPE BADGE
        StackPane typeBadge = new StackPane();
        typeBadge.setStyle(getTypeBadgeStyle(messageType));
        Text typeText = new Text(getTypeLabel(messageType));
        typeText.setStyle("-fx-fill: white; -fx-font-size: 9px; -fx-font-weight: bold;");
        typeBadge.getChildren().add(typeText);
        headerBox.getChildren().add(typeBadge);

        // ✅ MESSAGE BUBBLE
        VBox bubble = new VBox(4);
        bubble.setStyle(isImportant ?
                """
                -fx-background-color: linear-gradient(to right, #fef2f2, #fee2e2);
                -fx-border-color: #dc2626;
                -fx-border-width: 2;
                -fx-background-radius: 18;
                -fx-border-radius: 18;
                -fx-padding: 12 16;
                -fx-effect: dropshadow(gaussian, rgba(220,38,38,0.2), 4, 0, 0, 1);
                """ :
                """
                -fx-background-color: white;
                -fx-background-radius: 18;
                -fx-padding: 10 14;
                -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 3, 0, 0, 1);
                """);

        Text msgText = new Text(content);
        msgText.setStyle("-fx-fill: #2d3748; -fx-font-size: 14px; -fx-line-spacing: 2px;");
        msgText.setWrappingWidth(400);
        bubble.getChildren().add(msgText);

        // ✅ TIMESTAMP
        Text timestamp = new Text(formatTime(sentAt));
        timestamp.setStyle("-fx-fill: #a0aec0; -fx-font-size: 10px;");

        contentBox.getChildren().addAll(headerBox, bubble, timestamp);
        msgContainer.getChildren().addAll(avatarPane, contentBox);
        messageGroup.getChildren().add(msgContainer);

        chatMessagesContainer.getChildren().add(messageGroup);
    }


    private String getAvatarForMessageType(String messageType) {
        switch (messageType.toLowerCase()) {
            case "broadcast": return "may_chu.png";
            case "group": return "may_chu.png";
            case "private": return "private_message.png";
            default: return "may_chu.png";
        }
    }
    private String getTypeBadgeStyle(String messageType) {
        switch (messageType.toLowerCase()) {
            case "broadcast":
                return "-fx-background-color: #3b82f6; -fx-background-radius: 8; -fx-padding: 2 8;";
            case "group":
                return "-fx-background-color: #10b981; -fx-background-radius: 8; -fx-padding: 2 8;";
            case "private":
                return "-fx-background-color: #8b5cf6; -fx-background-radius: 8; -fx-padding: 2 8;";
            default:
                return "-fx-background-color: #6b7280; -fx-background-radius: 8; -fx-padding: 2 8;";
        }
    }

    private String getTypeLabel(String messageType) {
        switch (messageType.toLowerCase()) {
            case "broadcast": return "📢 THÔNG BÁO CHUNG";
            case "group": return "📬";
            case "private": return "✉️";
            default: return "💬 TIN NHẮN";
        }
    }
    private void showServerWelcomeMessage() {
        VBox welcomeMsg = new VBox(8);
        welcomeMsg.getStyleClass().add("message-group");
        welcomeMsg.setPadding(new Insets(8, 0, 8, 0));

        HBox msgContainer = new HBox(8);
        msgContainer.setAlignment(Pos.CENTER_LEFT);

        StackPane avatar = new StackPane();
        avatar.getStyleClass().add("message-avatar");
        avatar.setPrefSize(36, 36);

        ImageView systemAvatar = new ImageView();
        systemAvatar.setFitWidth(36);
        systemAvatar.setFitHeight(36);
        AvatarUtil.loadAvatar(systemAvatar, "may_chu.png");
        Circle clip = new Circle(18, 18, 18);
        systemAvatar.setClip(clip);
        avatar.getChildren().add(systemAvatar);

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

        Text msgText = new Text("Chào mừng bạn đến với EduGame! 🎉\n\n" +
                "Đây là kênh thông báo chính thức từ hệ thống.\n" +
                "Bạn sẽ nhận được các thông báo quan trọng tại đây.");
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


    private void setupServerMessageListener() {
        System.out.println("🔔 [CHAT] Setting up server message listener...");

        // ✅ ĐĂNG KÝ CALLBACK
        server.setServerMessageCallback(message -> {
            // ✅ CHỈ HIỂN THỊ NẾU ĐANG XEM SYSTEM USER
            if (selectedFriendId == -1) {
                Platform.runLater(() -> {
                    String messageType = (String) message.get("messageType");
                    String senderName = (String) message.get("senderName");
                    String content = (String) message.get("content");
                    String sentAt = (String) message.get("sentAt");
                    boolean isImportant = (boolean) message.getOrDefault("isImportant", false);

                    System.out.println("📨 [CHAT] New server message received");
                    System.out.println("   Type: " + messageType);
                    System.out.println("   Content: " + content.substring(0, Math.min(50, content.length())));

                    // ✅ HIỂN THỊ TIN NHẮN MỚI
                    addServerMessage(senderName, content, sentAt, isImportant, messageType);

                    // ✅ AUTO SCROLL
                    Platform.runLater(() -> chatMessagesScrollPane.setVvalue(1.0));

                    // ✅ ĐÁNH DẤU ĐÃ ĐỌC
                    int messageId = (int) message.get("messageId");
                    server.markServerMessageAsRead(messageId);
                });
            } else {
                // ✅ NẾU ĐANG XEM FRIEND KHÁC, CHỈ SHOW NOTIFICATION
                Platform.runLater(() -> {
                    String content = (String) message.get("content");
                    showNotification("📢 Tin nhắn hệ thống: " +
                            content.substring(0, Math.min(50, content.length())));
                });
            }
        });

        System.out.println("✅ [CHAT] Server message listener ready");
    }


    private void showNotification(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Thông báo");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.show(); // Non-blocking
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

            FlowPane messageContent = parseMessageWithEmojiImages(message, isSelf);
            messageContent.setMaxWidth(290);

            messageBox.getChildren().addAll(headerBox, messageContent);
            messageContainer.getChildren().add(messageBox);

            chatMessagesContainer.getChildren().add(messageContainer);

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
        String content = messageInputField.getText().trim();
        if (content.isEmpty()) return;

        // Handle AI Chat
        if (selectedFriendId == -2) {
            handleAIMessage(content);
            return;
        }

        // Handle System (cannot send)
        if (selectedFriendId == -1) {
            showError("Không thể gửi tin nhắn cho Hệ Thống!");
            return;
        }

        // Handle normal friend message
        System.out.println("💬 [CHAT] Sending message to friendId=" + selectedFriendId);

        sendMessageButton.setDisable(true);
        messageInputField.setDisable(true);

        server.sendMessage(selectedFriendId, content, success -> {
            Platform.runLater(() -> {
                if (success) {
                    String timeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
                    String fullTime = LocalDateTime.now().toString();
                    addChatMessage(server.getCurrentUsername(), content, true, timeStr, false);
                    messageInputField.clear();
                    Platform.runLater(() -> chatMessagesScrollPane.setVvalue(1.0));
                    updateFriendLastMessage(selectedFriendId, content, fullTime);
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
     * Handle AI message (simulate AI response)
     */
    private void handleAIMessage(String userMessage) {
        String timeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));

        // Display user message
        addAIChatMessage(server.getCurrentUsername(), userMessage, true, timeStr);
        messageInputField.clear();

        // Simulate AI thinking
        sendMessageButton.setDisable(true);
        messageInputField.setDisable(true);

        // Simulate AI response after 1 second
        new Thread(() -> {
            try {
                Thread.sleep(1000);
                Platform.runLater(() -> {
                    String aiResponse = generateAIResponse(userMessage);
                    String aiTimeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
                    addAIChatMessage("Chat AI", aiResponse, false, aiTimeStr);

                    sendMessageButton.setDisable(false);
                    messageInputField.setDisable(false);
                    messageInputField.requestFocus();
                });
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    /**
     * Generate AI response based on user message
     */
    private String generateAIResponse(String userMessage) {
        String msg = userMessage.toLowerCase();

        // Simple keyword-based responses
        if (msg.contains("chào") || msg.contains("hello") || msg.contains("hi")) {
            return "👋 Chào bạn! Tôi có thể giúp gì cho bạn hôm nay?";
        } else if (msg.contains("cách chơi") || msg.contains("hướng dẫn")) {
            return "📚 Để chơi game trong EduGame:\n\n" +
                    "1️⃣ Chọn game từ menu chính\n" +
                    "2️⃣ Đọc kỹ hướng dẫn trước khi bắt đầu\n" +
                    "3️⃣ Hoàn thành thử thách để nhận điểm\n" +
                    "4️⃣ Cạnh tranh với bạn bè trên bảng xếp hạng!\n\n" +
                    "Bạn cần hướng dẫn chi tiết về game nào không? 🎮";
        } else if (msg.contains("điểm") || msg.contains("xếp hạng")) {
            return "🏆 Về hệ thống điểm:\n\n" +
                    "• Mỗi game hoàn thành sẽ được cộng điểm\n" +
                    "• Điểm cao hơn = xếp hạng cao hơn\n" +
                    "• Kiểm tra bảng xếp hạng để xem vị trí của bạn\n" +
                    "• Chơi nhiều để lên top! 💪";
        } else if (msg.contains("bạn bè") || msg.contains("kết bạn")) {
            return "👥 Để thêm bạn bè:\n\n" +
                    "1️⃣ Vào mục 'Bạn bè'\n" +
                    "2️⃣ Tìm kiếm tên người dùng\n" +
                    "3️⃣ Gửi lời mời kết bạn\n" +
                    "4️⃣ Chờ họ chấp nhận!\n\n" +
                    "Sau đó bạn có thể chat và thi đấu cùng nhau nhé! 😊";
        } else if (msg.contains("help") || msg.contains("trợ giúp") || msg.contains("giúp")) {
            return "💡 Tôi có thể giúp bạn về:\n\n" +
                    "🎮 Cách chơi game\n" +
                    "🏆 Hệ thống điểm và xếp hạng\n" +
                    "👥 Kết bạn và chat\n" +
                    "📚 Mẹo học tập\n" +
                    "⚙️ Cài đặt tài khoản\n\n" +
                    "Hãy hỏi tôi bất cứ điều gì bạn muốn biết! 😊";
        } else if (msg.contains("học") || msg.contains("học tập")) {
            return "📖 Mẹo học tập hiệu quả:\n\n" +
                    "✨ Chơi game đều đặn mỗi ngày\n" +
                    "✨ Tập trung vào những nội dung khó\n" +
                    "✨ Thi đấu với bạn bè để tạo động lực\n" +
                    "✨ Ôn tập thường xuyên\n\n" +
                    "Học qua chơi là cách tốt nhất! 🚀";
        } else if (msg.contains("cảm ơn") || msg.contains("thanks")) {
            return "😊 Không có gì! Tôi luôn sẵn sàng giúp đỡ bạn.\n\n" +
                    "Nếu có thắc mắc gì khác, cứ hỏi tôi nhé!";
        } else if (msg.contains("tạm biệt") || msg.contains("bye")) {
            return "👋 Tạm biệt! Chúc bạn học tập vui vẻ!\n\n" +
                    "Hẹn gặp lại bạn sớm! 😊";
        } else {
            return "🤔 Xin lỗi, tôi chưa hiểu câu hỏi của bạn lắm.\n\n" +
                    "Bạn có thể hỏi tôi về:\n" +
                    "• Cách chơi game 🎮\n" +
                    "• Hệ thống điểm 🏆\n" +
                    "• Kết bạn 👥\n" +
                    "• Mẹo học tập 📚\n\n" +
                    "Hoặc gõ 'trợ giúp' để xem đầy đủ! 💡";
        }
    }

    /**
     * Add AI chat message with special styling
     */
    private void addAIChatMessage(String username, String message, boolean isSelf, String timeStr) {
        if (chatMessagesContainer == null) return;

        Platform.runLater(() -> {
            HBox messageContainer = new HBox(8);
            messageContainer.setAlignment(isSelf ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
            messageContainer.setPadding(new Insets(4, 0, 4, 0));

            if (!isSelf) {
                // 🔹 Tạo StackPane chứa avatar
                StackPane avatarPane = new StackPane();
                avatarPane.setPrefSize(36, 36);
                avatarPane.getStyleClass().add("message-avatar");

                // 🔹 Avatar AI (dùng AvatarUtil để tự nhận file cục bộ hoặc URL)
                ImageView aiAvatar = new ImageView();
                AvatarUtil.loadAvatar(aiAvatar, "chat_ai.png"); // ảnh Chat AI
                aiAvatar.setFitWidth(36);
                aiAvatar.setFitHeight(36);
                aiAvatar.setClip(new Circle(18, 18, 18));
                avatarPane.getChildren().add(aiAvatar);

                // 🔹 Nội dung tin nhắn
                VBox contentBox = new VBox(5);

                Text senderName = new Text("Chat AI");
                senderName.setStyle("-fx-fill: #667eea; -fx-font-weight: bold; -fx-font-size: 12px;");

                VBox bubble = new VBox(4);
                bubble.setMaxWidth(320);
                bubble.setStyle("""
                -fx-background-color: rgba(230,235,255,0.8);
                -fx-background-radius: 18;
                -fx-padding: 12 16;
                -fx-effect: dropshadow(gaussian, rgba(102,126,234,0.15), 4, 0, 0, 1);
            """);

                Text msgText = new Text(message);
                msgText.setStyle("-fx-fill: #2d3748; -fx-font-size: 14px; -fx-line-spacing: 3px;");
                msgText.setWrappingWidth(280);
                bubble.getChildren().add(msgText);

                Text timestamp = new Text(timeStr);
                timestamp.setStyle("-fx-fill: #a0aec0; -fx-font-size: 10px;");

                contentBox.getChildren().addAll(senderName, bubble, timestamp);

                // 🔹 Thêm avatar + nội dung vào container
                messageContainer.getChildren().addAll(avatarPane, contentBox);

            } else {
                // 🔹 Tin nhắn của người dùng
                VBox messageBox = new VBox(4);
                messageBox.setMaxWidth(320);
                messageBox.setStyle("""
                -fx-background-color: linear-gradient(to bottom right, #667eea, #764ba2);
                -fx-background-radius: 18;
                -fx-padding: 10 14;
            """);

                HBox headerBox = new HBox(6);
                headerBox.setAlignment(Pos.CENTER_LEFT);

                Text timeText = new Text(timeStr);
                timeText.setStyle("-fx-fill: rgba(255,255,255,0.7); -fx-font-size: 10px;");
                headerBox.getChildren().add(timeText);

                Text msgText = new Text(message);
                msgText.setStyle("-fx-fill: white; -fx-font-size: 14px;");
                msgText.setWrappingWidth(280);

                messageBox.getChildren().addAll(headerBox, msgText);
                messageContainer.getChildren().add(messageBox);
            }

            // 🔹 Cuộn xuống cuối
            chatMessagesContainer.getChildren().add(messageContainer);
            chatMessagesScrollPane.layout();
            chatMessagesScrollPane.setVvalue(1.0);
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
                    updateFriendLastMessage(friendId, content, sentAt);
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

        if (selectedFriendId == -2) {
            // Refresh AI chat
            loadAIIntroMessage();
        } else if (selectedFriendId == -1) {
            // Refresh system messages
            loadSystemMessages();
        } else if (selectedFriendId > 0) {
            // Refresh friend messages
            loadMessages(selectedFriendId);
        }

        loadFriendsList();
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
     */
    public void cleanup() {
        System.out.println("🧹 [CHAT] Cleaning up chat controller...");

        // ✅ XÓA FRIEND LISTENER
        if (selectedFriendId > 0) {
            server.removePrivateChatListener(selectedFriendId);
            System.out.println("✅ [CHAT] Removed listener for friendId=" + selectedFriendId);
        }

        // ✅ XÓA SERVER MESSAGE LISTENER
        server.clearServerMessageCallback();
        System.out.println("✅ [CHAT] Removed server message listener");

        friendItemsMap.clear();
        selectedFriendId = -1;
        selectedFriendName = null;

        System.out.println("✅ [CHAT] Cleanup completed");
    }
}