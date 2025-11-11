package com.edugame.client.controller;

import com.edugame.client.network.ServerConnection;
import com.edugame.client.util.AvatarUtil;
import com.edugame.client.util.GameDataParser;
import com.edugame.client.util.SceneManager;
import com.edugame.common.Protocol;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;

/**
 * Controller cho màn hình phòng chờ game
 * - Hiển thị 4 slot người chơi
 * - Chat phòng với emoji support
 * - Danh sách bạn bè với filter (Online/In Game/Offline)
 * - Mời bạn vào phòng
 * - Ready/Start game
 */
public class RoomController {

    // ==================== FXML Components ====================
    @FXML private Button btnBack;
    @FXML private Label lblRoomId;
    @FXML private ImageView avatar1, avatar2, avatar3, avatar4;
    @FXML private Label name1, name2, name3, name4;
    @FXML private Label score1, score2, score3, score4;
    @FXML private Label emptyIcon2, emptyIcon3, emptyIcon4;
    @FXML private Circle readyIndicator1, readyIndicator2, readyIndicator3, readyIndicator4;
    @FXML private VBox chatMessagesContainer;
    @FXML private ScrollPane chatScrollPane;
    @FXML private TextField chatInputField;
    @FXML private Button btnSendChat;
    @FXML private Button emojiButton;
    @FXML private Label onlineCount;
    @FXML private VBox friendCardsContainer;
    @FXML private ScrollPane friendsScrollPane;
    @FXML private Button btnFilterAll, btnFilterOnline, btnFilterInGame;
    @FXML private Button btnReady;
    @FXML private Button btnStart;
    @FXML private Label kickIcon2;
    @FXML private Label kickIcon3;
    @FXML private Label kickIcon4;

    // ==================== Data ====================

    private ServerConnection connection;
    private Map<String, Object> currentRoomData;
    private String roomId;
    private String subject;
    private String difficulty;
    private boolean isHost = false;
    private boolean isReady = false;
    private Map<Integer, PlayerInfo> players = new HashMap<>();
    private Map<Integer, Integer> userIdToSlot = new HashMap<>();
    private List<FriendItem> allFriends = new ArrayList<>();
    private String currentFilter = "ALL";
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private Consumer<JsonObject> kickPlayerCallback;


    @FXML
    private void initialize() {
        connection = ServerConnection.getInstance();
        setupPlayerSlots();
        setupChatSystem();
        setupFriendsList();
        setupEventHandlers();
        loadFriendsList();

        setupKickIcons();

        // Register callbacks
        connection.setPlayerJoinedCallback(this::handlePlayerJoined);
        connection.setPlayerLeftCallback(this::handlePlayerLeft);
        connection.setPlayerReadyCallback(this::handlePlayerReady);
        connection.setRoomChatCallback(this::handleRoomChatMessage);
        connection.setKickPlayerCallback(this::handleKickPlayer);
        connection.setGameStartCallback(this::handleGameStartResponse);


        System.out.println("✅ RoomController initialized");
    }

    /**
     * Setup kick icons - chỉ hiện khi là host
     */
    private void setupKickIcons() {
        kickIcon2.setVisible(false);
        kickIcon3.setVisible(false);
        kickIcon4.setVisible(false);

        // Set style
        String kickStyle = "-fx-text-fill: #ff4444; -fx-font-size: 18px; -fx-cursor: hand;";
        kickIcon2.setStyle(kickStyle);
        kickIcon3.setStyle(kickStyle);
        kickIcon4.setStyle(kickStyle);

        // Add hover effects
        addKickIconHoverEffect(kickIcon2);
        addKickIconHoverEffect(kickIcon3);
        addKickIconHoverEffect(kickIcon4);

        // Add click handlers
        kickIcon2.setOnMouseClicked(e -> kickPlayer(2));
        kickIcon3.setOnMouseClicked(e -> kickPlayer(3));
        kickIcon4.setOnMouseClicked(e -> kickPlayer(4));
    }

    /**
     * Add hover effect to kick icon
     */
    private void addKickIconHoverEffect(Label kickIcon) {
        kickIcon.setOnMouseEntered(e -> {
            kickIcon.setStyle(kickIcon.getStyle() + "-fx-scale-x: 1.2; -fx-scale-y: 1.2;");
        });
        kickIcon.setOnMouseExited(e -> {
            kickIcon.setStyle(kickIcon.getStyle() + "-fx-scale-x: 1.0; -fx-scale-y: 1.0;");
        });
    }



    public void initializeRoom(Map<String, Object> roomData) {
        this.currentRoomData = roomData;
        this.roomId = getStringValue(roomData.get("roomId"));
        this.subject = getStringValue(roomData.get("subject"));
        this.difficulty = getStringValue(roomData.get("difficulty"));
        lblRoomId.setText("Phòng #" + roomId);

        players.clear();
        userIdToSlot.clear();

        Object playersObj = roomData.get("playersList");
        if (playersObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> playersList = (List<Map<String, Object>>) playersObj;

            List<Map<String, Object>> sortedPlayers = new ArrayList<>();
            Map<String, Object> hostPlayer = null;

            for (Map<String, Object> p : playersList) {
                if (getBooleanValue(p.get("isHost"))) {
                    hostPlayer = p;
                    break;
                }
            }

            if (hostPlayer != null) {
                sortedPlayers.add(hostPlayer);
            }

            for (Map<String, Object> p : playersList) {
                if (hostPlayer == null ||
                        getIntValue(p.get("userId")) != getIntValue(hostPlayer.get("userId"))) {
                    sortedPlayers.add(p);
                }
            }

            for (int i = 0; i < sortedPlayers.size(); i++) {
                Map<String, Object> player = sortedPlayers.get(i);
                int userId = getIntValue(player.get("userId"));
                String name = getStringValue(player.get("fullName"));
                String avatarUrl = getStringValue(player.get("avatarUrl"));
                int score = getIntValue(player.get("totalScore"));
                boolean playerIsHost = getBooleanValue(player.get("isHost"));
                boolean playerIsReady = getBooleanValue(player.get("isReady"));

                int slot = i + 1;
                userIdToSlot.put(userId, slot);

                if (userId == connection.getCurrentUserId()) {
                    isHost = playerIsHost;
                    isReady = playerIsReady;
                }

                updatePlayer(slot, userId, name, avatarUrl, score, playerIsReady);
            }
        }

        // ✅ Update UI based on role
        Platform.runLater(() -> {
            if (isHost) {
                btnReady.setVisible(false);
                btnReady.setManaged(false);
                btnStart.setVisible(true);
                btnStart.setManaged(true);
                checkStartButtonState();
                updateKickIconsVisibility(true);
                System.out.println("👑 I am HOST - Start button visible");
            } else {
                btnStart.setVisible(false);
                btnStart.setManaged(false);
                btnReady.setVisible(true);
                btnReady.setManaged(true);
                updateReadyButton();
                updateKickIconsVisibility(false);
                System.out.println("👤 I am PLAYER - Ready button visible");
            }
        });

        updateOnlineCount();
        System.out.println("✅ Room initialized - Players: " + players.size());
    }
    /**
     * Update kick icons visibility based on host status
     */
    public void updateKickIconsVisibility(boolean visible) {
        Platform.runLater(() -> {
            if (kickIcon2 != null) {
                // Chỉ hiện nếu slot 2 có người
                kickIcon2.setVisible(visible && players.containsKey(2));
            }
            if (kickIcon3 != null) {
                kickIcon3.setVisible(visible && players.containsKey(3));
            }
            if (kickIcon4 != null) {
                kickIcon4.setVisible(visible && players.containsKey(4));
            }
        });
    }

    /**
     * Kick player khỏi phòng (chỉ host)
     */
    private void kickPlayer(int slot) {
        if (!isHost) {
            showWarning("Chỉ chủ phòng mới có thể kick người chơi!");
            return;
        }

        PlayerInfo player = players.get(slot);
        if (player == null) {
            System.out.println("⚠️ Không có người chơi ở slot " + slot);
            return;
        }

        // Confirm dialog
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Xác nhận");
        alert.setHeaderText("Kick người chơi");
        alert.setContentText("Bạn có chắc muốn kick " + player.name + " ra khỏi phòng?");

        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                System.out.println("👢 Kicking player: " + player.name + " (userId=" + player.userId + ")");
                connection.kickPlayerFromRoom(roomId, player.userId);
            }
        });
    }

    /**
     * Handle player bị kick (từ server)
     */
    private void handleKickPlayer(Map<String, Object> data) {
        Platform.runLater(() -> {
            int kickedUserId = getIntValue(data.get("userId"));
            String kickedUsername = getStringValue(data.get("username"));
            boolean isMe = (kickedUserId == connection.getCurrentUserId());

            System.out.println("👢 Player kicked: " + kickedUsername);

            // ✅ Nếu là chính mình bị kick
            if (isMe) {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Thông báo");
                alert.setHeaderText("Bị kick khỏi phòng");
                alert.setContentText("Bạn đã bị chủ phòng mời ra khỏi phòng!");
                alert.showAndWait();

                System.out.println("🚪 I was kicked - returning to home");

                cleanup();
                try {
                    SceneManager.getInstance().switchScene("Home.fxml");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return;
            }

            // ✅ Nếu người khác bị kick
            Integer slotToRemove = userIdToSlot.get(kickedUserId);
            if (slotToRemove != null) {
                removePlayer(slotToRemove);
                userIdToSlot.remove(kickedUserId);
                addSystemMessage(kickedUsername + " đã bị chủ phòng mời ra khỏi phòng");

                // ✅ Ẩn kick icon của slot đó nếu là host
                if (isHost) {
                    Label kickIcon = getKickIconBySlot(slotToRemove);
                    if (kickIcon != null) {
                        kickIcon.setVisible(false);
                    }
                }
            }

            // ✅ Xử lý chuyển host (nếu có)
            boolean isNewHost = getBooleanValue(data.get("isNewHost"));
            if (isNewHost) {
                int newHostId = getIntValue(data.get("newHostId"));
                handleHostTransfer(newHostId);
            }
        });
    }

    /**
     * Xử lý chuyển host
     */
    private void handleHostTransfer(int newHostUserId) {
        System.out.println("👑 Host transfer: new host userId = " + newHostUserId);

        // ✅ Nếu MÌNH là host mới
        if (newHostUserId == connection.getCurrentUserId()) {
            isHost = true;
            isReady = false;

            btnReady.setVisible(false);
            btnStart.setVisible(true);
            checkStartButtonState();

            // ✅ Hiện kick icons
            updateKickIconsVisibility(true);

            // Ẩn ready indicator của mình
            Integer mySlot = userIdToSlot.get(connection.getCurrentUserId());
            if (mySlot != null) {
                Circle myIndicator = getReadyIndicatorBySlot(mySlot);
                if (myIndicator != null) {
                    myIndicator.setVisible(false);
                }

                PlayerInfo myInfo = players.get(mySlot);
                if (myInfo != null) {
                    myInfo.isReady = false;
                }
            }

            addSystemMessage("Bạn đã trở thành chủ phòng");
            System.out.println("✅ I am now the host!");
        } else {
            // ✅ Người khác thành host - ẩn kick icons
            updateKickIconsVisibility(false);

            // Ẩn ready indicator của host mới
            Integer newHostSlot = userIdToSlot.get(newHostUserId);
            if (newHostSlot != null) {
                Circle indicator = getReadyIndicatorBySlot(newHostSlot);
                if (indicator != null) {
                    indicator.setVisible(false);
                }

                PlayerInfo hostInfo = players.get(newHostSlot);
                if (hostInfo != null) {
                    hostInfo.isReady = false;
                }
            }
        }
    }


    /**
     * Handle player joined - LƯU avatarUrl
     */
    private void handlePlayerJoined(Map<String, Object> data) {
        Platform.runLater(() -> {
            int userId = getIntValue(data.get("userId"));
            String username = getStringValue(data.get("username"));
            String fullName = getStringValue(data.get("fullName"));
            String avatarUrl = getStringValue(data.get("avatarUrl"));
            int score = getIntValue(data.get("totalScore"));

            System.out.println("🆕 Player joined: " + username);

            int emptySlot = findEmptySlot();
            if (emptySlot > 0) {
                userIdToSlot.put(userId, emptySlot);
                updatePlayer(emptySlot, userId, fullName, avatarUrl, score, false);
                addSystemMessage(fullName + " đã tham gia phòng");

                if (isHost) {
                    checkStartButtonState();
                }
            }
        });
    }


    /**
     * Xử lý khi có người rời phòng
     */
    private void handlePlayerLeft(Map<String, Object> data) {
        Platform.runLater(() -> {
            int userId = getIntValue(data.get("userId"));
            String username = getStringValue(data.get("username"));
            boolean isNewHost = getBooleanValue(data.get("isNewHost"));

            System.out.println("👋 Player left: " + username + " (userId=" + userId + ")");

            // ✅ Tìm slot của người rời
            Integer slotToRemove = userIdToSlot.get(userId);

            if (slotToRemove != null) {
                System.out.println("   Removing from slot: " + slotToRemove);

                // ✅ XÓA người chơi khỏi slot
                removePlayer(slotToRemove);
                userIdToSlot.remove(userId);
                addSystemMessage(username + " đã rời phòng");

                if (isHost) {
                    checkStartButtonState();
                }
            }

            // ✅ XỬ LÝ CHUYỂN HOST
            if (isNewHost) {
                int newHostUserId = getIntValue(data.get("newHostId"));

                System.out.println("👑 Host transfer detected!");
                System.out.println("   Old host userId: " + userId);
                System.out.println("   New host userId: " + newHostUserId);
                System.out.println("   Current user userId: " + connection.getCurrentUserId());

                // ✅ Nếu MÌNH là host mới
                if (newHostUserId == connection.getCurrentUserId()) {
                    handleBecomeHost();
                } else {
                    // ✅ Người khác thành host - SWAP vị trí lên slot 1
                    handleOtherBecomeHost(newHostUserId);
                }
            }
        });
    }

    /**
     * Xử lý khi MÌNH trở thành host
     */
    private void handleBecomeHost() {
        System.out.println("🎉 I am now the HOST!");

        isHost = true;
        isReady = false;

        // ✅ Tìm slot hiện tại của mình
        Integer myCurrentSlot = userIdToSlot.get(connection.getCurrentUserId());

        System.out.println("   My current slot: " + myCurrentSlot);

        if (myCurrentSlot != null && myCurrentSlot != 1) {
            // ✅ SWAP: Di chuyển mình lên slot 1
            PlayerInfo myInfo = players.get(myCurrentSlot);

            if (myInfo != null) {
                System.out.println("   Swapping from slot " + myCurrentSlot + " to slot 1");

                // Remove khỏi slot cũ
                removePlayer(myCurrentSlot);
                userIdToSlot.remove(connection.getCurrentUserId());

                // Add vào slot 1
                updatePlayer(1, myInfo.userId, myInfo.name, "", myInfo.score, false);
                userIdToSlot.put(connection.getCurrentUserId(), 1);

                // ✅ Load lại avatar
                ImageView avatar1 = getAvatarBySlot(1);
                if (avatar1 != null) {
                    loadAvatar(avatar1, myInfo.avatarUrl);
                }
            }
        }

//        // ✅ Update UI: Ẩn nút Ready, hiện nút Start
//        btnReady.setVisible(false);
//        btnStart.setVisible(true);
//        checkStartButtonState();

        Platform.runLater(() -> {
            btnReady.setVisible(false);
            btnReady.setManaged(false);
            btnStart.setVisible(true);
            btnStart.setManaged(true);
            checkStartButtonState();
        });

        // ✅ Hiện kick icons
        updateKickIconsVisibility(true);

        // ✅ Ẩn ready indicator của mình
        Circle myIndicator = getReadyIndicatorBySlot(1);
        if (myIndicator != null) {
            myIndicator.setVisible(false);
        }

        addSystemMessage("🎉 Bạn đã trở thành chủ phòng!");
        System.out.println("✅ Become host completed!");
    }

    /**
     * Xử lý khi NGƯỜI KHÁC trở thành host
     */
    private void handleOtherBecomeHost(int newHostUserId) {
        System.out.println("👑 Other player become host: " + newHostUserId);

        // ✅ Tìm slot hiện tại của host mới
        Integer newHostCurrentSlot = userIdToSlot.get(newHostUserId);

        System.out.println("   New host current slot: " + newHostCurrentSlot);

        if (newHostCurrentSlot != null && newHostCurrentSlot != 1) {
            // ✅ SWAP: Di chuyển host mới lên slot 1
            PlayerInfo newHostInfo = players.get(newHostCurrentSlot);

            if (newHostInfo != null) {
                System.out.println("   Swapping new host from slot " + newHostCurrentSlot + " to slot 1");

                String newHostAvatarUrl = newHostInfo.avatarUrl;

                // Remove khỏi slot cũ
                removePlayer(newHostCurrentSlot);
                userIdToSlot.remove(newHostUserId);

                // Add vào slot 1
                updatePlayer(1, newHostInfo.userId, newHostInfo.name, newHostAvatarUrl, newHostInfo.score, false);
                userIdToSlot.put(newHostUserId, 1);

                // ✅ Ẩn ready indicator (vì giờ là host)
                Circle indicator = getReadyIndicatorBySlot(1);
                if (indicator != null) {
                    indicator.setVisible(false);
                }

                // ✅ Update player info
                newHostInfo.isReady = false;
            }
        }

        // ✅ Ẩn kick icons (vì mình không còn là host)
        updateKickIconsVisibility(false);

        System.out.println("✅ Other become host completed!");
    }



    // ==================== Player Management ====================

    /**
     * Update player - LƯU avatarUrl vào PlayerInfo
     */
    private void updatePlayer(int slot, int userId, String name, String avatarUrl, int score, boolean isReady) {
        Platform.runLater(() -> {
            ImageView avatar = getAvatarBySlot(slot);
            Label nameLabel = getNameLabelBySlot(slot);
            Label scoreLabel = getScoreLabelBySlot(slot);
            Circle readyIndicator = getReadyIndicatorBySlot(slot);
            Label emptyIcon = getEmptyIconBySlot(slot);

            if (avatar != null && nameLabel != null) {
                loadAvatar(avatar, avatarUrl);
                nameLabel.setText(name);
                nameLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 16; -fx-font-weight: bold;");

                if (scoreLabel != null) {
                    scoreLabel.setText("⭐ " + score);
                }

                if (emptyIcon != null) {
                    emptyIcon.setVisible(false);
                }

                if (readyIndicator != null) {
                    readyIndicator.setVisible(isReady);
                }

                // ✅ Hiện kick icon nếu là host và không phải slot 1
                if (isHost && slot > 1) {
                    Label kickIcon = getKickIconBySlot(slot);
                    if (kickIcon != null) {
                        kickIcon.setVisible(true);
                    }
                }

                // ✅ LƯU avatarUrl vào PlayerInfo
                PlayerInfo player = new PlayerInfo();
                player.userId = userId;
                player.name = name;
                player.avatarUrl = avatarUrl; // ✅ LƯU avatarUrl
                player.score = score;
                player.isReady = isReady;
                players.put(slot, player);

                System.out.println("✅ Updated slot " + slot + ": " + name + " (userId=" + userId + ")");
            }

            updateOnlineCount();

            if (isHost) {
                checkStartButtonState();
            }
        });
    }


    /**
     * Override removePlayer để ẩn kick icon
     */
    private void removePlayer(int slot) {
        Platform.runLater(() -> {
            ImageView avatar = getAvatarBySlot(slot);
            Label nameLabel = getNameLabelBySlot(slot);
            Label scoreLabel = getScoreLabelBySlot(slot);
            Circle readyIndicator = getReadyIndicatorBySlot(slot);
            Label emptyIcon = getEmptyIconBySlot(slot);
            Label kickIcon = getKickIconBySlot(slot);

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

                // ✅ Ẩn kick icon
                if (kickIcon != null) {
                    kickIcon.setVisible(false);
                }

                players.remove(slot);
            }

            updateOnlineCount();

            if (isHost) {
                checkStartButtonState();
            }
        });
    }

    /**
     * Get kick icon by slot
     */
    private Label getKickIconBySlot(int slot) {
        switch (slot) {
            case 2: return kickIcon2;
            case 3: return kickIcon3;
            case 4: return kickIcon4;
            default: return null;
        }
    }



    private void handlePlayerReady(Map<String, Object> data) {
        Platform.runLater(() -> {
            int userId = getIntValue(data.get("userId"));
            boolean ready = getBooleanValue(data.get("isReady"));

            System.out.println("✅ Player ready status: userId=" + userId + " ready=" + ready);

            // ✅ Tìm slot bằng userIdToSlot map
            Integer slot = userIdToSlot.get(userId);

            if (slot != null) {
                System.out.println("   Found player in slot: " + slot);

                // ✅ Update player info
                PlayerInfo playerInfo = players.get(slot);
                if (playerInfo != null) {
                    playerInfo.isReady = ready;
                    System.out.println("   Updated PlayerInfo.isReady = " + ready);
                }

                // ✅ Update ready indicator
                Circle indicator = getReadyIndicatorBySlot(slot);
                if (indicator != null) {
                    indicator.setVisible(ready);
                    System.out.println("   Set indicator visible = " + ready);
                } else {
                    System.out.println("   ⚠️ Indicator is NULL for slot " + slot);
                }
            } else {
                System.out.println("   ⚠️ User not found in userIdToSlot map");
            }

            // ✅ Check if host can start game
            if (isHost) {
                checkStartButtonState();
            }
        });
    }



    private int findEmptySlot() {
        for (int i = 1; i <= 4; i++) {
            if (!players.containsKey(i)) {
                return i;
            }
        }
        return -1;
    }
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

    private String getStringValue(Object obj) {
        return obj != null ? obj.toString() : "";
    }

    private boolean getBooleanValue(Object obj) {
        if (obj == null) return false;
        if (obj instanceof Boolean) return (Boolean) obj;
        return Boolean.parseBoolean(obj.toString());
    }

    private void setupPlayerSlots() {
        setDefaultAvatar(avatar1);
        setDefaultAvatar(avatar2);
        setDefaultAvatar(avatar3);
        setDefaultAvatar(avatar4);

        readyIndicator1.setVisible(false);
        readyIndicator2.setVisible(false);
        readyIndicator3.setVisible(false);
        readyIndicator4.setVisible(false);
    }

    private void setDefaultAvatar(ImageView imageView) {
        try {
            String defaultPath = "/images/avatars/avatar4.png";
            Image defaultImage = new Image(getClass().getResourceAsStream(defaultPath));
            imageView.setImage(defaultImage);
        } catch (Exception e) {
            System.err.println("⚠️ Could not load default avatar");
        }
    }

    // ==================== CHAT SYSTEM ====================

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

        addSystemMessage("Chào mừng đến phòng chờ! 🎮");
    }

    @FXML
    private void handleSendChat() {
        if (chatInputField == null || chatMessagesContainer == null) return;

        String message = chatInputField.getText().trim();
        if (message.isEmpty()) return;

        if (!connection.isConnected()) {
            addSystemMessage("Không thể gửi tin nhắn. Chưa kết nối server.");
            return;
        }

        try {
            int roomIdInt = Integer.parseInt(roomId);
            connection.sendRoomChatMessage(roomIdInt, message);

            addChatMessage(connection.getCurrentFullName(), message, true);
            chatInputField.clear();

        } catch (Exception e) {
            addSystemMessage("Lỗi khi gửi tin nhắn!");
            e.printStackTrace();
        }
    }

    private void handleRoomChatMessage(com.google.gson.JsonObject json) {
        try {
            // Check which format the server is using
            if (json.has("sender") && json.has("message")) {
                // New format from server
                String senderId = json.has("senderId") ? json.get("senderId").getAsString() : "";
                String sender = json.get("sender").getAsString();
                String message = json.get("message").getAsString();

                // Don't display if it's from current user (already displayed when sent)
                if (!senderId.equals(connection.getCurrentUserId()) &&
                        !sender.equals(connection.getCurrentFullName())) {
                    addChatMessage(sender, message, false);
                }

            } else if (json.has("success")) {
                // Old format (if still supported)
                boolean success = json.get("success").getAsBoolean();
                if (!success) return;

                String username = json.get("username").getAsString();
                String message = json.get("message").getAsString();

                if (!username.equals(connection.getCurrentUsername())) {
                    addChatMessage(username, message, false);
                }
            } else {
                System.err.println("❌ Unknown room chat message format: " + json);
            }

        } catch (Exception e) {
            System.err.println("❌ Error handling room chat: " + e.getMessage());
            e.printStackTrace();
        }
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

            FlowPane messageContent = parseMessageWithEmojiImages(message, isSelf);
            messageContent.setMaxWidth(250);
            messageContent.setHgap(2);
            messageContent.setVgap(2);

            messageBox.getChildren().addAll(headerBox, messageContent);
            messageContainer.getChildren().add(messageBox);

            chatMessagesContainer.getChildren().add(messageContainer);

            if (chatMessagesContainer.getChildren().size() > 50) {
                chatMessagesContainer.getChildren().remove(0);
            }

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

    // ==================== FRIENDS LIST - FIXED ====================
    // ==================== Friends List ====================

    private void setupFriendsList() {
        if (friendCardsContainer == null) {
            System.err.println("⚠️ friendCardsContainer is null!");
            return;
        }
        friendCardsContainer.getChildren().clear();
        System.out.println("✅ Friends list container setup complete");
    }

    private void loadFriendsList() {
        connection.getFriendsList(friends -> {
            Platform.runLater(() -> {
                allFriends.clear();

                for (Map<String, Object> friend : friends) {
                    FriendItem item = new FriendItem();
                    item.userId = (int) friend.get("userId");
                    item.name = (String) friend.get("fullName");
                    item.avatarUrl = (String) friend.getOrDefault("avatarUrl", "");
                    item.score = (int) friend.get("totalScore");
                    item.isOnline = (boolean) friend.get("isOnline");
                    item.status = item.isOnline ? "ONLINE" : "OFFLINE";
                    allFriends.add(item);
                }

                allFriends.sort((a, b) -> {
                    int priorityA = getStatusPriority(a.status);
                    int priorityB = getStatusPriority(b.status);
                    if (priorityA != priorityB) {
                        return priorityA - priorityB;
                    }
                    return b.score - a.score;
                });

                applyFilter();
                System.out.println("✅ Loaded " + allFriends.size() + " friends");
            });
        });
    }

    private HBox createFriendCard(FriendItem friend) {
        HBox card = new HBox(10);
        card.setAlignment(Pos.CENTER_LEFT);
        card.getStyleClass().add("friend-card");
        card.setPadding(new Insets(10));
        card.setStyle("""
            -fx-background-color: #2a2a2a;
            -fx-background-radius: 12;
            -fx-border-color: #3a3a3a;
            -fx-border-width: 1;
            -fx-border-radius: 12;
            -fx-cursor: hand;
            """);

        ImageView avatar = new ImageView();
        avatar.setFitWidth(50);
        avatar.setFitHeight(50);
        avatar.setPreserveRatio(true);
        Circle clip = new Circle(25, 25, 25);
        avatar.setClip(clip);
        loadAvatar(avatar, friend.avatarUrl);

        VBox infoBox = new VBox(3);
        infoBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(infoBox, Priority.ALWAYS);

        HBox nameBox = new HBox(6);
        nameBox.setAlignment(Pos.CENTER_LEFT);

        Label nameLabel = new Label(friend.name);
        nameLabel.setStyle("-fx-text-fill: #ffffff; -fx-font-size: 14px; -fx-font-weight: bold;");

        Label statusLabel = new Label(getStatusIcon(friend.status));
        statusLabel.setStyle("-fx-font-size: 10px;");

        nameBox.getChildren().addAll(nameLabel, statusLabel);

        Label scoreLabel = new Label("⭐ " + friend.score + " điểm");
        scoreLabel.setStyle("-fx-text-fill: #aaaaaa; -fx-font-size: 12px;");

        infoBox.getChildren().addAll(nameBox, scoreLabel);

        Button inviteBtn = new Button("➕");
        inviteBtn.setStyle("""
            -fx-background-color: #4caf50;
            -fx-text-fill: white;
            -fx-font-size: 18px;
            -fx-font-weight: bold;
            -fx-background-radius: 8;
            -fx-cursor: hand;
            -fx-min-width: 36px;
            -fx-min-height: 36px;
            -fx-max-width: 36px;
            -fx-max-height: 36px;
            """);

        Tooltip tooltip = new Tooltip("Mời vào phòng");
        inviteBtn.setTooltip(tooltip);

        if (!"ONLINE".equals(friend.status)) {
            inviteBtn.setDisable(true);
            inviteBtn.setStyle(inviteBtn.getStyle() + "-fx-opacity: 0.5;");
        }

        inviteBtn.setOnAction(e -> handleInviteFriend(friend));

        card.setOnMouseEntered(e ->
                card.setStyle(card.getStyle() + "-fx-background-color: #353535;"));
        card.setOnMouseExited(e ->
                card.setStyle(card.getStyle() + "-fx-background-color: #2a2a2a;"));

        card.getChildren().addAll(avatar, infoBox, inviteBtn);
        return card;
    }

    private void applyFilter() {
        if (friendCardsContainer == null) return;

        Platform.runLater(() -> {
            friendCardsContainer.getChildren().clear();
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

            for (FriendItem friend : filtered) {
                HBox card = createFriendCard(friend);
                friendCardsContainer.getChildren().add(card);
            }

            if (filtered.isEmpty()) {
                Label emptyLabel = new Label(getEmptyMessage());
                emptyLabel.setStyle("-fx-text-fill: #888888; -fx-font-size: 13px; -fx-font-style: italic;");
                emptyLabel.setWrapText(true);
                emptyLabel.setMaxWidth(240);
                emptyLabel.setAlignment(Pos.CENTER);

                VBox emptyBox = new VBox(emptyLabel);
                emptyBox.setAlignment(Pos.CENTER);
                emptyBox.setPadding(new Insets(20));

                friendCardsContainer.getChildren().add(emptyBox);
            }
        });
    }

    private String getEmptyMessage() {
        switch (currentFilter) {
            case "ONLINE": return "Không có bạn bè nào đang online";
            case "IN_GAME": return "Không có bạn bè nào đang trong trận";
            default: return "Danh sách bạn bè trống";
        }
    }

    private String getStatusIcon(String status) {
        switch (status) {
            case "ONLINE": return "🟢";
            case "IN_GAME": return "🎮";
            case "OFFLINE": return "⚫";
            default: return "⚪";
        }
    }

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
     * Setup event handlers
     */
    private void setupEventHandlers() {
        btnBack.setOnAction(e -> handleBack());
        btnSendChat.setOnAction(e -> handleSendChat());
        chatInputField.setOnAction(e -> handleSendChat());
        emojiButton.setOnAction(e -> handleShowEmoji());
        btnFilterAll.setOnAction(e -> handleFilter("ALL"));
        btnFilterOnline.setOnAction(e -> handleFilter("ONLINE"));
        btnFilterInGame.setOnAction(e -> handleFilter("IN_GAME"));
        btnReady.setOnAction(e -> handleReady());
        btnStart.setOnAction(e -> handleStartGame());
    }

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
            connection.leaveGameRoom(roomId);

            cleanup();
            SceneManager.getInstance().switchScene("Home.fxml");

        } catch (Exception e) {
            showError("Không thể rời phòng!");
            e.printStackTrace();
        }
    }

    /**
     * Handle filter button click
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
     * Handle invite friend
     */
    private void handleInviteFriend(FriendItem friend) {
        if (!"ONLINE".equals(friend.status)) {
            showWarning("Chỉ có thể mời bạn bè đang online!");
            return;
        }

        connection.inviteToRoom(friend.userId, roomId);
        showInfo("Đã gửi lời mời đến " + friend.name);
        System.out.println("📧 Invited friend: " + friend.name);
    }

    // ==================== Actions ====================


    /**
     * Handle ready button
     */
    private void handleReady() {
        isReady = !isReady;
        updateReadyButton();

        // ✅ Update own ready indicator
        Integer mySlot = userIdToSlot.get(connection.getCurrentUserId());
        if (mySlot != null) {
            Circle indicator = getReadyIndicatorBySlot(mySlot);
            if (indicator != null) {
                indicator.setVisible(isReady);
            }

            PlayerInfo myInfo = players.get(mySlot);
            if (myInfo != null) {
                myInfo.isReady = isReady;
            }
        }

        // Send to server
        connection.sendReady(isReady);

        System.out.println("✅ Ready status changed: " + isReady);
    }

    private void updateReadyButton() {
        if (isReady) {
            btnReady.setText("❌ Hủy sẵn sàng");
            btnReady.getStyleClass().remove("ready-button");
            btnReady.getStyleClass().add("ready-button-active");
        } else {
            btnReady.setText("✓ Sẵn sàng");
            btnReady.getStyleClass().remove("ready-button-active");
            btnReady.getStyleClass().add("ready-button");
        }
    }



    /**
     * ✅ Handle start game button (host only)
     */
    @FXML
    private void handleStartGame() {
        if (!isHost) {
            System.out.println("⚠️ Not host, cannot start game");
            return;
        }

        if (!checkAllPlayersReady()) {
            showWarning("Chưa đủ người chơi hoặc chưa tất cả sẵn sàng!");
            System.out.println("⚠️ Not all players ready");
            return;
        }

        System.out.println("🎮 Sending START_GAME request to server...");

        // ✅ Disable start button to prevent double-click
        if (btnStart != null) {
            btnStart.setDisable(true);
            btnStart.setText("Đang bắt đầu...");
        }

        // ✅ Set callback first
//        connection.setGameStartCallback(this::handleGameStartResponse);

        // ✅ Send start game request ONCE
        connection.sendStartGame(roomId);
    }


    /**
     * Xử lý phản hồi START_GAME từ server
     */
    private void handleGameStartResponse(Map<String, Object> data) {
        Platform.runLater(() -> {
            try {
                boolean success = getBooleanValue(data.get("success"));

                if (!success) {
                    String message = getStringValue(data.get("message"));
                    showError("Không thể bắt đầu game: " + message);
                    return;
                }

                System.out.println("✅ [RoomController] START_GAME received, switching to game scene");

                // ✅ Chuyển sang màn hình game
                switchToGameScene(data);

            } catch (Exception e) {
                System.err.println("❌ [RoomController] Error handling START_GAME: " + e.getMessage());
                e.printStackTrace();
                showError("Lỗi khi bắt đầu game!");
            }
        });
    }

    /**
     * Chuyển sang màn hình game
     */
    private void switchToGameScene(Map<String, Object> gameData) {
        try {
            System.out.println("🎮 [RoomController] Switching to MathGame scene...");

            // ✅ Load MathGame.fxml
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/MathGame.fxml"));
            Parent root = loader.load();

            // ✅ Get controller và initialize game
            MathGameController gameController = loader.getController();
            gameController.initializeGame(gameData);

            // ✅ Switch scene
            SceneManager.getInstance().switchScene(root);

            // ✅ Cleanup room controller
            cleanup();

            System.out.println("✅ [RoomController] Switched to game scene successfully");

        } catch (IOException e) {
            System.err.println("❌ [RoomController] Error loading game scene: " + e.getMessage());
            e.printStackTrace();
            showError("Không thể tải màn hình game!");
        }
    }



//    /**
//     * ✅ Handle START_GAME broadcast from server
//     * This is received by ALL players including host
//     */
//    private void handleGameStartBroadcast(JsonObject data) {
//        Platform.runLater(() -> {
//            try {
//                boolean success = data.get("success").getAsBoolean();
//
//                if (!success) {
//                    showError("Không thể bắt đầu game!");
//                    if (btnStart != null) {
//                        btnStart.setDisable(false);
//                        btnStart.setText("Bắt đầu");
//                    }
//                    return;
//                }
//
//                System.out.println("🎮 [START_GAME] Received start game broadcast");
//
//                // ✅ Parse game data using helper
//                Map<String, Object> gameData = GameDataParser.parseStartGameData(data);
//
//                // ✅ Transition to game scene immediately
//                // MathGameController will handle its own 10s countdown
//                transitionToGameScene(gameData);
//
//            } catch (Exception e) {
//                System.err.println("❌ Error handling START_GAME: " + e.getMessage());
//                e.printStackTrace();
//                showError("Lỗi khi bắt đầu game!\n" + e.getMessage());
//
//                // Re-enable button on error
//                if (btnStart != null && isHost) {
//                    btnStart.setDisable(false);
//                    btnStart.setText("Bắt đầu");
//                }
//            }
//        });
//    }

//    /**
//     * ✅ Transition to MathGame Scene
//     */
//    private void transitionToGameScene(Map<String, Object> gameData) {
//        try {
//            System.out.println("🎮 [TRANSITION] Loading game scene...");
//
//            // Clear room callbacks before leaving
//            connection.clearPlayerJoinedCallback();
//            connection.clearPlayerLeftCallback();
//            connection.clearPlayerReadyCallback();
//            connection.setRoomChatCallback(null);
//            connection.setKickPlayerCallback(null);
//            connection.unregisterHandler(Protocol.START_GAME);
//
//            System.out.println("🧹 [TRANSITION] Cleared room callbacks");
//
//            // Load MathGame Scene with FXMLLoader
//            FXMLLoader loader = new FXMLLoader(
//                    getClass().getResource("/fxml/MathGame.fxml")
//            );
//            Parent root = loader.load();
//
//            // Get MathGameController and initialize with game data
//            MathGameController gameController = loader.getController();
//            gameController.initializeGame(gameData);
//
//            // Switch scene
//            Stage stage = (Stage) btnStart.getScene().getWindow();
//            Scene scene = new Scene(root);
//            stage.setScene(scene);
//            stage.setTitle("Math Racing Game - " + gameData.get("subject"));
//            stage.show();
//
//            System.out.println("✅ [TRANSITION] Successfully loaded game scene");
//
//        } catch (IOException e) {
//            System.err.println("❌ [TRANSITION] Failed to load FXML: " + e.getMessage());
//            e.printStackTrace();
//            showError("Không thể tải giao diện game!\nKiểm tra file: /fxml/MathGame.fxml");
//        } catch (Exception e) {
//            System.err.println("❌ [TRANSITION] Error: " + e.getMessage());
//            e.printStackTrace();
//            showError("Lỗi khi chuyển sang màn hình game!\n" + e.getMessage());
//        }
//    }

    /**
     * Check if all players are ready
     */
    private boolean checkAllPlayersReady() {
        int playerCount = players.size();

        System.out.println("🔍 [VALIDATE_START] Players: " + playerCount);

        if (playerCount < 2) {
            System.out.println("   ❌ Not enough players");
            return false;
        }

        int myUserId = connection.getCurrentUserId();

        for (Map.Entry<Integer, PlayerInfo> entry : players.entrySet()) {
            PlayerInfo player = entry.getValue();

            if (player.userId == myUserId && isHost) {
                System.out.println("   ⏭️ Skip host: " + player.name);
                continue;
            }

            if (!player.isReady) {
                System.out.println("   ❌ Not ready: " + player.name);
                return false;
            }

            System.out.println("   ✅ Ready: " + player.name);
        }

        System.out.println("   ✅ All non-host players ready!");
        return true;
    }

    // ==================== Data Loading ====================



    // ==================== Player Management ====================


    /**
     * Load avatar from URL
     */
    private void loadAvatar(ImageView imageView, String url) {
        try {
            if (imageView == null) return;
            AvatarUtil.loadAvatar(imageView, url);
            imageView.setPreserveRatio(true);
            imageView.setSmooth(true);
        } catch (Exception e) {
            System.err.println("❌ Error loading avatar: " + e.getMessage());
            try {
                AvatarUtil.loadAvatar(imageView, null);
            } catch (Exception ex) {
                System.err.println("❌ Failed to load default avatar");
            }
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
        if (!isHost || btnStart == null) {
            return;
        }

        int playerCount = players.size();

        System.out.println("🔍 [CHECK_START] Total players: " + playerCount);

        // Điều kiện 1: Tối thiểu 2 người
        if (playerCount < 2) {
            btnStart.setDisable(true);
            btnStart.setText("▶ Chờ người chơi (1/2)");
            System.out.println("   ❌ Not enough players");
            return;
        }

        // Điều kiện 2: Tất cả NON-HOST players phải ready
        int myUserId = connection.getCurrentUserId();
        int readyCount = 0;
        int nonHostCount = 0;

        for (Map.Entry<Integer, PlayerInfo> entry : players.entrySet()) {
            PlayerInfo player = entry.getValue();

            if (player.userId == myUserId) {
                System.out.println("   ⏭️ Skip host: " + player.name);
                continue;
            }

            nonHostCount++;

            if (player.isReady) {
                readyCount++;
                System.out.println("   ✅ Ready: " + player.name);
            } else {
                System.out.println("   ❌ Not ready: " + player.name);
            }
        }

        System.out.println("   Ready: " + readyCount + "/" + nonHostCount);

        boolean allReady = (nonHostCount > 0 && readyCount == nonHostCount);

        if (allReady) {
            btnStart.setDisable(false);
            btnStart.setText("▶ Bắt đầu trò chơi");
            System.out.println("   ✅ CAN START!");
        } else {
            btnStart.setDisable(true);
            btnStart.setText("▶ Chờ sẵn sàng (" + readyCount + "/" + nonHostCount + ")");
            System.out.println("   ❌ Waiting for ready");
        }
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
        connection.clearRoomChatCallback();
        connection.clearPlayerJoinedCallback();
        connection.clearPlayerLeftCallback();
        connection.clearPlayerReadyCallback();
        connection.clearKickPlayerCallback();
        System.out.println("🧹 RoomController cleaned up");
    }

    // ==================== Inner Classes ====================

    /**
     * Friend item
     */
    private static class FriendItem {
        int userId;
        String name;
        String avatarUrl;
        int score;
        boolean isOnline;
        String status;
    }


    /**
     * Player info
     */
    private static class PlayerInfo {
        int userId;
        String name;
        String avatarUrl;
        int score;
        boolean isReady;
    }
}