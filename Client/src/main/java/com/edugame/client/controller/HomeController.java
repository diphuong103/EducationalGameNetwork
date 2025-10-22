package com.edugame.client.controller;

import com.edugame.client.model.User;
import com.edugame.client.network.ServerConnection;
import com.edugame.client.util.SceneManager;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
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

import de.jensd.fx.glyphs.fontawesome.FontAwesomeIconView;
import de.jensd.fx.glyphs.fontawesome.FontAwesomeIcon;

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

    // User info
    @FXML private ImageView userAvatar;
    @FXML private Text userNameText;
    @FXML private Text levelText;
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

    @FXML
    private Button emojiButton;


    private User currentUser;
    private Gson gson = new Gson();

    private ServerConnection serverConnection;
    private boolean chatExpanded = true;

    private static boolean dataLoaded = false;

    @FXML
    public void initialize() {
        System.out.println("HomeController initializing...");
        serverConnection = ServerConnection.getInstance();
        System.out.println("Server connection retrieved");

        if (dataLoaded) return;
        dataLoaded = true;

        chatInputField.setOnKeyPressed(event -> {
            if (event.getCode() == ENTER && !event.isShiftDown()) {
                event.consume();
                handleSendMessage();
            }
        });

        setupButtonEffects();
        setupChatScroll();
        startChatListener();
        loadDataInBackground();

    }

//    private void loadDataInBackground() {
//        Thread backgroundThread = new Thread(() -> {
//            try {
//                Thread.sleep(100);
//                Platform.runLater(() -> {
//                    try {
//                        loadUserData();
//                        loadLeaderboardData();
//                        loadDailyQuests();
//                        System.out.println("✅ All data loaded");
//                    } catch (Exception e) {
//                        System.err.println("❌ Error loading data: " + e.getMessage());
//                        e.printStackTrace();
//                    }
//                });
//            } catch (InterruptedException e) {
//                Thread.currentThread().interrupt();
//            }
//        });
//        backgroundThread.setDaemon(true);
//        backgroundThread.start();
//    }

    //Dùng ExecutorService thay cho new Thread()
    //
    //Thay vì tạo thread thủ công, dùng Executors quản lý dễ hơn:
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private static List<User> cachedLeaderboard = null;
    private static long lastLeaderboardUpdate = 0;
    private static final long CACHE_DURATION = 60000;

    private void loadDataInBackground() {
        executor.submit(() -> {
            try {
                Thread.sleep(100);
                Platform.runLater(() -> {
                    loadUserData();
                    loadDailyQuests();
                });

                Thread.sleep(300);

                Platform.runLater(() -> {
                    loadLeaderboardData();
                });

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }



    private void setupChatScroll() {
        if (chatMessagesContainer != null && chatScrollPane != null) {
            chatMessagesContainer.heightProperty().addListener((obs, oldVal, newVal) -> {
                chatScrollPane.setVvalue(1.0);
            });
        }
    }

    /** ---------------- EMOJI ---------------- **/
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

            // ✅ Sử dụng ImageView để hiển thị emoji màu
            ImageView emojiImage = createEmojiImageView(emoji, 28);
            if (emojiImage != null) {
                emojiContainer.getChildren().add(emojiImage);
            } else {
                // Fallback to text if image fails to load
                Label emojiLabel = new Label(emoji);
                emojiLabel.setStyle("-fx-font-size: 26px;");
                emojiContainer.getChildren().add(emojiLabel);
            }

            emojiContainer.setOnMouseEntered(e ->
                    emojiContainer.setStyle("-fx-background-color: #f0f2f5; -fx-cursor: hand; -fx-background-radius: 8;"));

            emojiContainer.setOnMouseExited(e ->
                    emojiContainer.setStyle("-fx-background-color: transparent; -fx-cursor: hand; -fx-background-radius: 8;"));

            emojiContainer.setOnMouseClicked(e -> {
                // ✅ Lưu vị trí con trỏ TRƯỚC KHI popup mất focus
                int savedCaretPos = chatInputField.getCaretPosition();
                String currentText = chatInputField.getText();

                //  Chèn emoji vào đúng vị trí con trỏ đã lưu
                String beforeCaret = currentText.substring(0, savedCaretPos);
                String afterCaret = currentText.substring(savedCaretPos);
                String newText = beforeCaret + afterCaret + emoji;

                chatInputField.setText(newText);

                // Đặt con trỏ ngay SAU emoji (không select text)
                int newCaretPos = savedCaretPos + emoji.length();

                emojiPopup.hide();

                Platform.runLater(() -> {
                    chatInputField.requestFocus();
                    chatInputField.positionCaret(newCaretPos);
                    chatInputField.deselect(); // Bỏ selection nếu có
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


    /** ---------------- USER DATA ---------------- **/
    private void loadUserData() {
        if (serverConnection != null && serverConnection.isConnected()) {
            String fullName = serverConnection.getCurrentFullName();
            int level = serverConnection.getCurrentLevel();
            int totalScore = serverConnection.getTotalScore();

            String avatarFileName = serverConnection.getCurrentAvatarUrl();

            userNameText.setText(fullName != null ? fullName : "Người chơi");
            levelText.setText("Level " + level);
            pointsText.setText("Điểm " + formatNumber(totalScore));

            loadAvatar(avatarFileName);
        } else {
            userNameText.setText("Người chơi");
            levelText.setText("Level 1");
            pointsText.setText("Điểm 0");
            loadAvatar("avatar4.png");
        }
    }

    /**
     * Load avatar image from resources
     * @param avatarFileName - Tên file avatar từ database (vd: "avatar1.png")
     */
    private void loadAvatar(String avatarFileName) {
        if (userAvatar == null) return;

        try {
            // Đường dẫn đến thư mục avatars trong resources
            String avatarPath = "/images/avatars/" + (avatarFileName != null ? avatarFileName : "avatar4.png");

            // Load image từ resources
            Image avatarImage = new Image(getClass().getResourceAsStream(avatarPath));

            if (avatarImage.isError()) {
                System.err.println("Failed to load avatar: " + avatarPath);
                // Load default avatar nếu không tìm thấy
                avatarImage = new Image(getClass().getResourceAsStream("/images/avatars/avatar4.png"));
            }

            userAvatar.setImage(avatarImage);

            // Optional: Set avatar properties for circular display
            userAvatar.setPreserveRatio(true);
            userAvatar.setSmooth(true);

            System.out.println("✓ Avatar loaded: " + avatarFileName);

        } catch (Exception e) {
            System.err.println("✗ Error loading avatar: " + e.getMessage());
            e.printStackTrace();

            // Fallback to default avatar
            try {
                Image defaultAvatar = new Image(getClass().getResourceAsStream("/images/avatars/avatar4.png"));
                userAvatar.setImage(defaultAvatar);
            } catch (Exception ex) {
                System.err.println("✗ Failed to load default avatar");
            }
        }
    }


    private void handleLeaderboardResponse(JsonObject response) {
        try {
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

                // ✅ LƯU VÀO STATIC CACHE (giữ qua các instance)
                cachedLeaderboard = leaderboard;
                lastLeaderboardUpdate = System.currentTimeMillis();

                System.out.println("✓ Leaderboard data received and cached: " + leaderboard.size() + " users");

                // Debug top 3
                if (leaderboard.size() >= 3) {
                    System.out.println("   1. " + leaderboard.get(0).getFullName() + " - " + leaderboard.get(0).getTotalScore());
                    System.out.println("   2. " + leaderboard.get(1).getFullName() + " - " + leaderboard.get(1).getTotalScore());
                    System.out.println("   3. " + leaderboard.get(2).getFullName() + " - " + leaderboard.get(2).getTotalScore());
                }

                // ✅ CẬP NHẬT UI
                Platform.runLater(() -> displayLeaderboard(leaderboard));
            }

        } catch (Exception e) {
            System.err.println("✗ Error parsing leaderboard response: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void clearLeaderboardCache() {
        cachedLeaderboard = null;
        lastLeaderboardUpdate = 0;
        System.out.println("✓ Leaderboard cache cleared");
    }

    /** ---------------- LEADERBOARD ---------------- **/
    private void loadLeaderboardData() {

        long now = System.currentTimeMillis();
        if (cachedLeaderboard != null && (now - lastLeaderboardUpdate) < CACHE_DURATION) {
            System.out.println("✓ Using cached leaderboard data (" + cachedLeaderboard.size() + " users)");
            Platform.runLater(() -> displayLeaderboard(cachedLeaderboard));
            return;
        }

        if (serverConnection != null && serverConnection.isConnected()) {
            try {
                Map<String, Object> request = new HashMap<>();
                request.put("type", "GET_LEADERBOARD");
                request.put("subject", "total");
                request.put("limit", 50);

                serverConnection.sendJson(request);
                System.out.println("✓ Sent leaderboard request (cache expired or empty)");

            } catch (Exception e) {
                System.err.println("✗ Error loading leaderboard: " + e.getMessage());
                e.printStackTrace();

                // ✅ Nếu lỗi nhưng có cache cũ, vẫn hiển thị
                if (cachedLeaderboard != null) {
                    System.out.println("⚠ Using old cached data due to error");
                    Platform.runLater(() -> displayLeaderboard(cachedLeaderboard));
                }
            }
        } else {
            // ✅ Không kết nối server nhưng có cache, vẫn hiển thị
            if (cachedLeaderboard != null) {
                System.out.println("⚠ Server disconnected, using cached data");
                Platform.runLater(() -> displayLeaderboard(cachedLeaderboard));
            }
        }
    }

    /** ---------------- DAILY QUEST ---------------- **/
    private void loadDailyQuests() {
        // Placeholder
        System.out.println("Daily quests loaded");
    }

    /** ---------------- CHAT ---------------- **/
    private Thread chatListenerThread;
    private volatile boolean isChatListening = false;

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

        // Bắt đầu lắng nghe chat khi mở chat lần đầu
        if (chatExpanded && !isChatListening) {
            startChatListener();
        }
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

        // Gửi tin nhắn lên server
        Map<String, Object> request = new HashMap<>();
        request.put("type", "GLOBAL_CHAT");
        request.put("username", server.getCurrentUsername());
        request.put("message", message);

        server.sendJson(request);

        // Hiển thị tin nhắn của bản thân
        addChatMessage(server.getCurrentUsername(), message, true);
        chatInputField.clear();
    }

    private void startChatListener() {
        if (isChatListening) return;

        isChatListening = true;
        chatListenerThread = new Thread(() -> {
            ServerConnection server = ServerConnection.getInstance();

            while (isChatListening && server.isConnected()) {
                try {
                    String response = server.receiveMessage();
                    if (response == null) break;

                    JsonObject jsonResponse = new Gson().fromJson(response, JsonObject.class);
                    String type = jsonResponse.get("type").getAsString();

                    // ✅ XỬ LÝ CHAT MESSAGE
                    if ("GLOBAL_CHAT".equals(type)) {
                        String username = jsonResponse.get("username").getAsString();
                        String message = jsonResponse.get("message").getAsString();

                        if (!username.equals(server.getCurrentUsername())) {
                            addChatMessage(username, message, false);
                        }
                    }
                    // ✅ XỬ LÝ SYSTEM MESSAGE
                    else if ("SYSTEM_MESSAGE".equals(type)) {
                        String message = jsonResponse.get("message").getAsString();
                        addSystemMessage(message);
                    }
                    // ✅ XỬ LÝ LEADERBOARD RESPONSE
                    else if ("GET_LEADERBOARD".equals(type)) {
                        handleLeaderboardResponse(jsonResponse);
                    }

                } catch (IOException e) {
                    System.err.println("Chat listener error: " + e.getMessage());
                    break;
                } catch (Exception e) {
                    System.err.println("Chat parse error: " + e.getMessage());
                }
            }

            isChatListening = false;
            System.out.println("Chat listener stopped");
        });

        chatListenerThread.setDaemon(true);
        chatListenerThread.start();
    }

    private void stopChatListener() {
        isChatListening = false;
        if (chatListenerThread != null) {
            chatListenerThread.interrupt();
        }
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

            // Username và thời gian
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

            // ✅ Nội dung tin nhắn - Parse và hiển thị emoji + text
            FlowPane messageContent = parseMessageWithEmojiImages(message, isSelf);
            messageContent.setMaxWidth(250);
            messageContent.setHgap(2);
            messageContent.setVgap(2);

            messageBox.getChildren().addAll(headerBox, messageContent);
            messageContainer.getChildren().add(messageBox);

            chatMessagesContainer.getChildren().add(messageContainer);

            // Giới hạn số tin nhắn
            if (chatMessagesContainer.getChildren().size() > 50) {
                chatMessagesContainer.getChildren().remove(0);
            }

            // Auto scroll
            chatScrollPane.layout();
            chatScrollPane.setVvalue(1.0);
        });
    }

    /**
     * Parse tin nhắn và tạo FlowPane với emoji images và text
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
                // Thêm text trước emoji (nếu có)
                if (textBuffer.length() > 0) {
                    Text textNode = new Text(textBuffer.toString());
                    textNode.setStyle(String.format(
                            "-fx-font-size: 14px; -fx-fill: %s;",
                            isSelf ? "white" : "#050505"
                    ));
                    flowPane.getChildren().add(textNode);
                    textBuffer = new StringBuilder();
                }

                // Thêm emoji image
                ImageView emojiView = createEmojiImageView(currentChar, 20);
                if (emojiView != null) {
                    flowPane.getChildren().add(emojiView);
                } else {
                    // Fallback nếu không load được ảnh
                    Text emojiText = new Text(currentChar);
                    emojiText.setStyle("-fx-font-size: 18px;");
                    flowPane.getChildren().add(emojiText);
                }
            } else {
                textBuffer.append(currentChar);
            }

            i += charCount;
        }

        // Thêm text còn lại
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
     * Kiểm tra xem codepoint có phải emoji không
     */
    private boolean isEmojiCodePoint(int codePoint) {
        return (codePoint >= 0x1F600 && codePoint <= 0x1F64F) || // Emoticons
                (codePoint >= 0x1F300 && codePoint <= 0x1F5FF) || // Misc Symbols and Pictographs
                (codePoint >= 0x1F680 && codePoint <= 0x1F6FF) || // Transport and Map
                (codePoint >= 0x1F1E0 && codePoint <= 0x1F1FF) || // Flags
                (codePoint >= 0x2600 && codePoint <= 0x26FF) ||   // Misc symbols
                (codePoint >= 0x2700 && codePoint <= 0x27BF) ||   // Dingbats
                (codePoint >= 0x1F900 && codePoint <= 0x1F9FF) || // Supplemental Symbols
                (codePoint >= 0x1FA70 && codePoint <= 0x1FAFF) || // Extended-A
                (codePoint >= 0x2764 && codePoint <= 0x2764) ||   // Hearts
                (codePoint >= 0x1F90D && codePoint <= 0x1F90F);   // More hearts
    }

    private void addSystemMessage(String message) {
        if (chatMessagesContainer == null) return;

        Platform.runLater(() -> {
            HBox systemBox = new HBox(6);
            systemBox.setAlignment(Pos.CENTER);
            systemBox.setPadding(new Insets(8, 10, 8, 10));
            systemBox.setStyle("-fx-background-color: #f0f2f5; -fx-background-radius: 12;");
            systemBox.setMaxWidth(300);

            // System icon with emoji image
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



    /** ---------------- EMOJI UTILITY ---------------- **/

    /**
     * Lấy URL hình ảnh emoji từ Twemoji CDN
     * @param emoji - Emoji character
     * @return URL của hình ảnh emoji
     */
    private String getEmojiImageUrl(String emoji) {
        // Lấy Unicode codepoint của emoji
        int codePoint = emoji.codePointAt(0);
        // Chuyển sang hex (format cho Twemoji)
        String hex = Integer.toHexString(codePoint);
        // Trả về URL từ Twemoji CDN
        return "https://cdn.jsdelivr.net/gh/twitter/twemoji@14.0.2/assets/72x72/" + hex + ".png";
    }
    /**
     * Tạo ImageView cho emoji với màu sắc đầy đủ
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
            // Fallback to text if image fails
            return null;
        }
    }


    // Gọi khi đóng HomeController
    public void cleanup() {
        stopChatListener();
        executor.shutdownNow();
        clearLeaderboardCache();
    }

    /** ---------------- BUTTON EFFECTS ---------------- **/
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

    /** ---------------- BUTTON HANDLERS ---------------- **/
    @FXML private void handleTrainingMode() { showComingSoon("Chế độ Luyện Tập đang được phát triển!"); }

    @FXML
    private void handleQuickMatch() {
        try {
            SceneManager.getInstance().switchScene("FindMatch.fxml");
        } catch (Exception e) {
            showError("Không thể chuyển sang màn hình tìm trận!");
        }
    }

    @FXML private void handleRoomMode() { showComingSoon("Chế độ Phòng Chơi đang được phát triển!"); }
    @FXML private void handleCreateRoom() { showComingSoon("Tạo phòng đang được phát triển!"); }
    @FXML private void handleNotifications() { showComingSoon("Thông báo đang được phát triển!"); }

    @FXML
    private void handleSettings() {
        try {
            SceneManager.getInstance().switchScene("Settings.fxml");
        } catch (Exception e) {
            showComingSoon("Cài đặt đang được phát triển!");
        }
    }

    @FXML
    private void handleLogout() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Đăng xuất");
        alert.setHeaderText("Bạn có chắc muốn đăng xuất?");
        alert.setContentText("Tiến trình chơi sẽ được lưu tự động.");

        cleanup();
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    clearLeaderboardCache();

                    if (serverConnection != null && serverConnection.isConnected()) {
                        serverConnection.disconnect();
                    }
                    SceneManager.getInstance().switchScene("Login.fxml");
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
            SceneManager.getInstance().switchScene("Leaderboard.fxml");
        } catch (Exception e) {
            showComingSoon("Bảng xếp hạng đang được phát triển!");
        }
    }


    private void displayLeaderboard(List<User> users) {
        if (leaderboardList == null || users.isEmpty()) {
            return;
        }

        leaderboardList.getChildren().clear();

        // Tìm rank của user hiện tại
        int currentUserRank = -1;
        for (int i = 0; i < users.size(); i++) {
            if (currentUser != null && users.get(i).getUserId() == currentUser.getUserId()) {
                currentUserRank = i + 1;
                break;
            }
        }

        // Hiển thị Top 3
        int topCount = Math.min(3, users.size());
        for (int i = 0; i < topCount; i++) {
            User user = users.get(i);
            int rank = i + 1;
            HBox rankItem = createRankItem(rank, user, false);
            leaderboardList.getChildren().add(rankItem);
        }

        // Hiển thị rank của user hiện tại nếu không trong top 3
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

        // Thêm style class theo rank
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

        // Online status (chỉ cho top 3, không phải user hiện tại)
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



    /** ---------------- UTILITIES ---------------- **/
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

    /** ---------------- UPDATE DATA ---------------- **/
    public void updatePoints(int newPoints) {
        Platform.runLater(() -> pointsText.setText("Điểm " + formatNumber(newPoints)));
    }

    public void updateLevel(int newLevel) {
        Platform.runLater(() -> levelText.setText("Level " + newLevel));
    }

    public void updateCoins(int newCoins) {
        Platform.runLater(() -> coinsText.setText("Coins " + formatNumber(newCoins)));
    }

    public void refreshUserData() {
        loadUserData();
        loadLeaderboardData();
        loadDailyQuests();
    }
}
