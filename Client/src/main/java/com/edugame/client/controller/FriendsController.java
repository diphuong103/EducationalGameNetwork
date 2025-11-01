package com.edugame.client.controller;

import com.edugame.client.network.ServerConnection;
import com.edugame.client.util.SceneManager;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

public class FriendsController {

    @FXML
    private ToggleButton friendsListTab;

    @FXML
    private ToggleButton searchFriendsTab;

    @FXML
    private ToggleButton pendingRequestsTab;

    @FXML
    private VBox friendsListContent;

    @FXML
    private VBox searchFriendsContent;

    @FXML
    private VBox pendingRequestsContent;

    @FXML
    private TextField searchFriendsField;

    @FXML
    private TextField searchNewFriendsField;

    @FXML
    private Text friendsCountText;

    @FXML
    private Text searchResultsText;

    @FXML
    private Text pendingRequestsCountText;

    @FXML
    private FlowPane friendsContainer;

    @FXML
    private FlowPane searchResultsContainer;

    @FXML
    private FlowPane pendingRequestsContainer;

    @FXML
    private ComboBox<String> ageFilter;

    @FXML
    private ImageView avatarImage;

    private ServerConnection server;

    @FXML
    public void initialize() {
        server = ServerConnection.getInstance();

        // Tạo ToggleGroup cho tab
        ToggleGroup tabGroup = new ToggleGroup();
        friendsListTab.setToggleGroup(tabGroup);
        pendingRequestsTab.setToggleGroup(tabGroup);
        searchFriendsTab.setToggleGroup(tabGroup);

        // Mặc định hiển thị tab bạn bè
        showFriendsTab(null);

        // Load danh sách bạn bè
        loadFriendsList();
    }

    public void handleBack(ActionEvent actionEvent) {
        try {
            SceneManager.getInstance().switchScene("Home.fxml");
        } catch (Exception e) {
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR, "Không thể quay lại trang chủ!");
            alert.showAndWait();
        }
    }

    public void handleApplyFilters(ActionEvent actionEvent) {
        String selectedAge = ageFilter.getValue();
        if (selectedAge == null || selectedAge.equals("Tất cả")) {
            searchResultsText.setText("Vui lòng nhập tên để tìm kiếm");
        } else {
            searchResultsText.setText("Lọc theo độ tuổi: " + selectedAge);
        }
        System.out.println("Áp dụng filter: " + selectedAge);
    }

    public void handleResetFilters(ActionEvent actionEvent) {
        if (ageFilter != null) {
            ageFilter.setValue("Tất cả");
        }
        searchNewFriendsField.clear();
        searchResultsContainer.getChildren().clear();
        searchResultsText.setText("Kết quả tìm kiếm sẽ hiển thị ở đây");
        System.out.println("Đã reset filters");
    }

    public void handleSearchNewFriends(ActionEvent actionEvent) {
        String searchText = searchNewFriendsField.getText().trim();

        if (searchText.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Tìm kiếm");
            alert.setHeaderText(null);
            alert.setContentText("Vui lòng nhập tên hoặc username để tìm kiếm!");
            alert.showAndWait();
            return;
        }

        searchResultsText.setText("Đang tìm kiếm: " + searchText + "...");
        searchResultsContainer.getChildren().clear();

        // Gọi server để tìm kiếm
        server.searchUsers(searchText, this::displaySearchResults);
    }

    public void handleRefreshPendingRequests(ActionEvent actionEvent) {
        loadPendingRequests();
    }

    public void showSearchTab(ActionEvent actionEvent) {
        friendsListContent.setVisible(false);
        friendsListContent.setManaged(false);

        pendingRequestsContent.setVisible(false);
        pendingRequestsContent.setManaged(false);

        searchFriendsContent.setVisible(true);
        searchFriendsContent.setManaged(true);

        searchFriendsTab.setSelected(true);
        System.out.println("Chuyển sang tab Tìm kiếm");
    }

    public void showFriendsTab(ActionEvent actionEvent) {
        friendsListContent.setVisible(true);
        friendsListContent.setManaged(true);

        pendingRequestsContent.setVisible(false);
        pendingRequestsContent.setManaged(false);

        searchFriendsContent.setVisible(false);
        searchFriendsContent.setManaged(false);

        friendsListTab.setSelected(true);
        loadFriendsList(); // ✅ Reload khi chuyển tab
        System.out.println("Chuyển sang tab Bạn bè");
    }

    public void showPendingRequestsTab(ActionEvent actionEvent) {
        friendsListContent.setVisible(false);
        friendsListContent.setManaged(false);

        searchFriendsContent.setVisible(false);
        searchFriendsContent.setManaged(false);

        pendingRequestsContent.setVisible(true);
        pendingRequestsContent.setManaged(true);

        pendingRequestsTab.setSelected(true);
        loadPendingRequests(); // ✅ Load lời mời khi chuyển tab
        System.out.println("Chuyển sang tab Lời mời kết bạn");
    }

    // ============================================
    // LOAD DATA METHODS
    // ============================================

    private void loadFriendsList() {
        friendsCountText.setText("Đang tải...");
        friendsContainer.getChildren().clear();

        server.getFriendsList(friends -> {
            Platform.runLater(() -> {
                friendsContainer.getChildren().clear();

                if (friends == null || friends.isEmpty()) {
                    friendsCountText.setText("Tổng số bạn bè: 0");
                    Label placeholder = new Label("Bạn chưa có bạn bè nào");
                    placeholder.setStyle("-fx-text-fill: white; -fx-font-size: 14px; -fx-font-style: italic;");
                    friendsContainer.getChildren().add(placeholder);
                    return;
                }

                friendsCountText.setText("Tổng số bạn bè: " + friends.size());

                for (Map<String, Object> friend : friends) {
                    VBox friendCard = createFriendCard(friend);
                    friendsContainer.getChildren().add(friendCard);
                }
            });
        });
    }

    private void loadPendingRequests() {
        pendingRequestsCountText.setText("Đang tải...");
        pendingRequestsContainer.getChildren().clear();

        server.getPendingRequests(requests -> {
            Platform.runLater(() -> {
                pendingRequestsContainer.getChildren().clear();

                if (requests == null || requests.isEmpty()) {
                    pendingRequestsCountText.setText("Lời mời kết bạn: 0");

                    VBox placeholder = new VBox(10);
                    placeholder.setAlignment(Pos.CENTER);
                    placeholder.setPadding(new Insets(40));

                    Label icon = new Label("📭");
                    icon.setStyle("-fx-font-size: 48px;");

                    Label text = new Label("Không có lời mời kết bạn nào");
                    text.setStyle("-fx-text-fill: white; -fx-font-size: 16px; -fx-font-style: italic;");

                    placeholder.getChildren().addAll(icon, text);
                    pendingRequestsContainer.getChildren().add(placeholder);
                    return;
                }

                pendingRequestsCountText.setText("Lời mời kết bạn: " + requests.size());

                for (Map<String, Object> request : requests) {
                    VBox requestCard = createPendingRequestCard(request);
                    pendingRequestsContainer.getChildren().add(requestCard);
                }
            });
        });
    }

    private void displaySearchResults(List<Map<String, Object>> users) {
        Platform.runLater(() -> {
            searchResultsContainer.getChildren().clear();

            if (users == null || users.isEmpty()) {
                searchResultsText.setText("Không tìm thấy kết quả");
                Label noResult = new Label("Không tìm thấy người dùng nào");
                noResult.setStyle("-fx-text-fill: white; -fx-font-size: 14px; -fx-font-style: italic;");
                searchResultsContainer.getChildren().add(noResult);
                return;
            }

            searchResultsText.setText("Tìm thấy " + users.size() + " người dùng");

            for (Map<String, Object> user : users) {
                VBox userCard = createUserCard(user);
                searchResultsContainer.getChildren().add(userCard);
            }
        });
    }

    // ============================================
    // CREATE CARD METHODS
    // ============================================

    private VBox createFriendCard(Map<String, Object> friend) {
        VBox card = new VBox(10);
        card.setAlignment(Pos.TOP_CENTER);
        card.setPrefWidth(200);
        card.setPadding(new Insets(15));
        card.setStyle(
                "-fx-background-color: white; " +
                        "-fx-background-radius: 15; " +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 10, 0, 0, 3);"
        );

        // Avatar
        ImageView avatar = new ImageView();
        avatar.setFitWidth(80);
        avatar.setFitHeight(80);
        avatar.setPreserveRatio(true);

        String avatarUrl = (String) friend.get("avatarUrl");
        Image safeAvatar;
        try {
            safeAvatar = loadAvatarImage(avatarUrl);
        } catch (Exception e) {
            safeAvatar = new Image(getClass().getResourceAsStream("/images/default-avatar.png"));
        }
        avatar.setImage(safeAvatar);

        // Tên
        Label nameLabel = new Label((String) friend.get("fullName"));
        nameLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #333;");
        nameLabel.setWrapText(true);
        nameLabel.setMaxWidth(180);
        nameLabel.setAlignment(Pos.CENTER);

        // Username
        Label usernameLabel = new Label("@" + friend.get("username"));
        usernameLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #666;");

        // Trạng thái online
        HBox statusBox = new HBox(5);
        statusBox.setAlignment(Pos.CENTER);
        Label statusDot = new Label("●");
        boolean isOnline = friend.get("isOnline") != null && (boolean) friend.get("isOnline");
        statusDot.setStyle("-fx-text-fill: " + (isOnline ? "#4CAF50" : "#999") + "; -fx-font-size: 10px;");
        Label statusLabel = new Label(isOnline ? "Online" : "Offline");
        statusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: " + (isOnline ? "#4CAF50" : "#999") + ";");
        statusBox.getChildren().addAll(statusDot, statusLabel);

        // Điểm
        Label scoreLabel = new Label("🏆 " + friend.get("totalScore") + " điểm");
        scoreLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #5B86E5; -fx-font-weight: bold;");

        // ✅ Nút nhắn tin
        Button chatButton = new Button("💬 Nhắn tin");
        chatButton.setPrefWidth(160);
        chatButton.setStyle(
                "-fx-background-color: #5B86E5; -fx-text-fill: white; " +
                        "-fx-background-radius: 10; -fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 8 15;"
        );

        int friendId = (int) friend.get("userId");
        String friendName = (String) friend.get("fullName");
        String friendAvatar = (String) friend.get("avatarUrl");

        chatButton.setOnAction(e -> handleOpenChat(friendId, friendName, friendAvatar, isOnline));

        // Nút xóa bạn
        Button removeButton = new Button("🗑️ Xóa bạn");
        removeButton.setPrefWidth(160);
        removeButton.setStyle(
                "-fx-background-color: #f44336; -fx-text-fill: white; " +
                        "-fx-background-radius: 10; -fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 8 15;"
        );

        removeButton.setOnAction(e -> handleRemoveFriend(friendId, friendName));

        card.getChildren().addAll(avatar, nameLabel, usernameLabel, statusBox, scoreLabel, chatButton, removeButton);
        return card;
    }

    /**
     * Mở cửa sổ chat với bạn bè
     */
    private void handleOpenChat(int friendId, String friendName, String avatarUrl, boolean isOnline) {
        try {
            System.out.println("💬 Opening chat with: " + friendName + " (ID=" + friendId + ")");

            // Load FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ChatWindow.fxml"));
            Parent root = loader.load();

            // Get controller và init data
            ChatController chatController = loader.getController();
            chatController.initData(friendId, friendName, avatarUrl, isOnline);

            // Tạo stage mới
            Stage chatStage = new Stage();
            chatStage.setTitle("Chat với " + friendName);

            // ✅ Sửa: Load icon an toàn
            try {
                InputStream iconStream = getClass().getResourceAsStream("/images/icon.png");
                if (iconStream != null) {
                    chatStage.getIcons().add(new Image(iconStream));
                } else {
                    System.out.println("⚠️ Icon not found, using default");
                }
            } catch (Exception e) {
                System.out.println("⚠️ Cannot load icon: " + e.getMessage());
            }

            chatStage.setScene(new Scene(root));

            // Set kích thước
            chatStage.setMinWidth(500);
            chatStage.setMinHeight(600);
            chatStage.setWidth(550);
            chatStage.setHeight(700);

            // Show stage
            chatStage.show();

            System.out.println("✅ Chat window opened successfully");

        } catch (Exception e) {
            System.err.println("❌ Error opening chat window: " + e.getMessage());
            e.printStackTrace();

            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Lỗi");
            alert.setHeaderText(null);
            alert.setContentText("Không thể mở cửa sổ chat. Vui lòng thử lại!");
            alert.showAndWait();
        }
    }

    private VBox createPendingRequestCard(Map<String, Object> request) {
        VBox card = new VBox(10);
        card.setAlignment(Pos.TOP_CENTER);
        card.setPrefWidth(220);
        card.setPadding(new Insets(15));
        card.setStyle(
                "-fx-background-color: linear-gradient(to bottom, #ffffff 0%, #f8f9fa 100%); " +
                        "-fx-background-radius: 15; " +
                        "-fx-border-color: #FFC107; " +
                        "-fx-border-width: 2; " +
                        "-fx-border-radius: 15; " +
                        "-fx-effect: dropshadow(gaussian, rgba(255,193,7,0.3), 15, 0, 0, 5);"
        );

        // Badge "MỚI"
        Label newBadge = new Label("✨ MỚI");
        newBadge.setStyle(
                "-fx-background-color: #FFC107; " +
                        "-fx-text-fill: white; " +
                        "-fx-font-size: 10px; " +
                        "-fx-font-weight: bold; " +
                        "-fx-padding: 3 8; " +
                        "-fx-background-radius: 10;"
        );
        card.getChildren().add(newBadge);

        // Avatar
        ImageView avatar = new ImageView();
        avatar.setFitWidth(90);
        avatar.setFitHeight(90);
        avatar.setPreserveRatio(true);

        String avatarUrl = (String) request.get("avatarUrl");
        Image safeAvatar;
        try {
            safeAvatar = loadAvatarImage(avatarUrl);
        } catch (Exception e) {
            safeAvatar = new Image(getClass().getResourceAsStream("/images/default-avatar.png"));
        }
        avatar.setImage(safeAvatar);

        // Tên
        Label nameLabel = new Label((String) request.get("fullName"));
        nameLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #333;");
        nameLabel.setWrapText(true);
        nameLabel.setMaxWidth(200);
        nameLabel.setAlignment(Pos.CENTER);

        // Username
        Label usernameLabel = new Label("@" + request.get("username"));
        usernameLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #666;");

        // Trạng thái online
        HBox statusBox = new HBox(5);
        statusBox.setAlignment(Pos.CENTER);
        Label statusDot = new Label("●");
        boolean isOnline = request.get("isOnline") != null && (boolean) request.get("isOnline");
        statusDot.setStyle("-fx-text-fill: " + (isOnline ? "#4CAF50" : "#999") + "; -fx-font-size: 10px;");
        Label statusLabel = new Label(isOnline ? "Online" : "Offline");
        statusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: " + (isOnline ? "#4CAF50" : "#999") + ";");
        statusBox.getChildren().addAll(statusDot, statusLabel);

        // Điểm
        Label scoreLabel = new Label("🏆 " + request.get("totalScore") + " điểm");
        scoreLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #5B86E5; -fx-font-weight: bold;");

        // Text "muốn kết bạn với bạn"
        Label requestText = new Label("muốn kết bạn với bạn");
        requestText.setStyle("-fx-font-size: 11px; -fx-text-fill: #888; -fx-font-style: italic;");

        // Buttons container
        HBox buttonsBox = new HBox(10);
        buttonsBox.setAlignment(Pos.CENTER);

        Button acceptButton = new Button("✓ Chấp nhận");
        acceptButton.setPrefWidth(95);
        acceptButton.setStyle(
                "-fx-background-color: #4CAF50; -fx-text-fill: white; " +
                        "-fx-background-radius: 10; -fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 8 10;"
        );

        Button rejectButton = new Button("✗ Từ chối");
        rejectButton.setPrefWidth(95);
        rejectButton.setStyle(
                "-fx-background-color: #f44336; -fx-text-fill: white; " +
                        "-fx-background-radius: 10; -fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 8 10;"
        );

        int userId = (int) request.get("userId");
        String userName = (String) request.get("fullName");

        acceptButton.setOnAction(e -> handleAcceptFriendRequest(userId, userName));
        rejectButton.setOnAction(e -> handleRejectFriendRequest(userId, userName));

        buttonsBox.getChildren().addAll(acceptButton, rejectButton);

        card.getChildren().addAll(avatar, nameLabel, usernameLabel, statusBox, scoreLabel, requestText, buttonsBox);
        return card;
    }

    private VBox createUserCard(Map<String, Object> user) {
        VBox card = new VBox(10);
        card.setAlignment(Pos.TOP_CENTER);
        card.setPrefWidth(200);
        card.setPadding(new Insets(15));
        card.setStyle(
                "-fx-background-color: white; " +
                        "-fx-background-radius: 15; " +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 10, 0, 0, 3);"
        );

        // Avatar
        ImageView avatar = new ImageView();
        avatar.setFitWidth(80);
        avatar.setFitHeight(80);
        avatar.setPreserveRatio(true);

        String avatarUrl = (String) user.get("avatarUrl");
        Image safeAvatar;
        try {
            safeAvatar = loadAvatarImage(avatarUrl);
        } catch (Exception e) {
            safeAvatar = new Image(getClass().getResourceAsStream("/images/default-avatar.png"));
        }
        avatar.setImage(safeAvatar);

        // Tên
        Label nameLabel = new Label((String) user.get("fullName"));
        nameLabel.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #333;");
        nameLabel.setWrapText(true);
        nameLabel.setMaxWidth(180);
        nameLabel.setAlignment(Pos.CENTER);

        // Username
        Label usernameLabel = new Label("@" + user.get("username"));
        usernameLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #666;");

        // Trạng thái online
        HBox statusBox = new HBox(5);
        statusBox.setAlignment(Pos.CENTER);
        Label statusDot = new Label("●");
        boolean isOnline = user.get("isOnline") != null && (boolean) user.get("isOnline");
        statusDot.setStyle("-fx-text-fill: " + (isOnline ? "#4CAF50" : "#999") + "; -fx-font-size: 10px;");
        Label statusLabel = new Label(isOnline ? "Online" : "Offline");
        statusLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: " + (isOnline ? "#4CAF50" : "#999") + ";");
        statusBox.getChildren().addAll(statusDot, statusLabel);

        // Điểm
        Label scoreLabel = new Label("🏆 " + user.get("totalScore") + " điểm");
        scoreLabel.setStyle("-fx-font-size: 13px; -fx-text-fill: #5B86E5; -fx-font-weight: bold;");

        // Nút hành động
        String friendshipStatus = (String) user.get("friendshipStatus");
        Button actionButton = new Button();
        actionButton.setPrefWidth(160);
        actionButton.setStyle(
                "-fx-background-radius: 10; -fx-font-weight: bold; -fx-cursor: hand; -fx-padding: 8 15;"
        );

        switch (friendshipStatus) {
            case "friend" -> {
                actionButton.setText("✓ Bạn bè");
                actionButton.setStyle(actionButton.getStyle() + "-fx-background-color: #4CAF50; -fx-text-fill: white;");
                actionButton.setDisable(true);
            }
            case "pending_sent" -> {
                actionButton.setText("⏳ Đã gửi lời mời");
                actionButton.setStyle(actionButton.getStyle() + "-fx-background-color: #FFC107; -fx-text-fill: white;");
                actionButton.setDisable(true);
            }
            case "pending_received" -> {
                actionButton.setText("✓ Chấp nhận");
                actionButton.setStyle(actionButton.getStyle() + "-fx-background-color: #2196F3; -fx-text-fill: white;");
                int senderId = (int) user.get("userId");
                String senderName = (String) user.get("fullName");
                actionButton.setOnAction(e -> handleAcceptFriendRequest(senderId, senderName));
            }
            default -> {
                actionButton.setText("➕ Thêm bạn");
                actionButton.setStyle(actionButton.getStyle() + "-fx-background-color: #5B86E5; -fx-text-fill: white;");
                int targetUserId = (int) user.get("userId");
                String targetName = (String) user.get("fullName");
                actionButton.setOnAction(e -> handleSendFriendRequest(targetUserId, targetName));
            }
        }

        card.getChildren().addAll(avatar, nameLabel, usernameLabel, statusBox, scoreLabel, actionButton);
        return card;
    }

    // ============================================
    // ACTION HANDLERS
    // ============================================

    private void handleSendFriendRequest(int targetUserId, String targetName) {
        System.out.println("📨 Đang gửi lời mời kết bạn tới: " + targetName + " (ID=" + targetUserId + ")");

        if (server == null) {
            System.err.println("❌ ServerConnection chưa được khởi tạo!");
            return;
        }

        server.sendFriendRequest(targetUserId, success -> {
            Platform.runLater(() -> {
                if (success) {
                    // ✅ Refresh lại kết quả tìm kiếm
                    handleSearchNewFriends(null);
                    System.out.println("✅ Friend request sent successfully to " + targetName);
                }
            });
        });
    }

    private void handleAcceptFriendRequest(int friendId, String friendName) {
        System.out.println("✅ Chấp nhận lời mời từ: " + friendName + " (ID=" + friendId + ")");

        server.acceptFriendRequest(friendId, success -> {
            Platform.runLater(() -> {
                if (success) {
                    // ✅ Refresh pending requests
                    loadPendingRequests();
                    System.out.println("✅ Friend request accepted from " + friendName);
                }
            });
        });
    }

    private void handleRejectFriendRequest(int friendId, String friendName) {
        System.out.println("❌ Từ chối lời mời từ: " + friendName + " (ID=" + friendId + ")");

        server.rejectFriendRequest(friendId, success -> {
            Platform.runLater(() -> {
                if (success) {
                    // ✅ Refresh pending requests
                    loadPendingRequests();
                    System.out.println("✅ Friend request rejected from " + friendName);
                }
            });
        });
    }

    private void handleRemoveFriend(int friendId, String friendName) {
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Xác nhận");
        confirmAlert.setHeaderText(null);
        confirmAlert.setContentText("Bạn có chắc muốn xóa " + friendName + " khỏi danh sách bạn bè?");

        confirmAlert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                System.out.println("🗑️ Xóa bạn: " + friendName + " (ID=" + friendId + ")");

                server.removeFriend(friendId, success -> {
                    Platform.runLater(() -> {
                        if (success) {
                            // ✅ Refresh friends list
                            loadFriendsList();
                            System.out.println("✅ Friend removed: " + friendName);
                        }
                    });
                });
            }
        });
    }

    // ============================================
    // UTILITY METHODS
    // ============================================

    private Image loadAvatarImage(String avatarUrl) {
        final String DEFAULT_AVATAR = "/images/avatars/avatar4.png";

        if (avatarUrl == null || avatarUrl.isBlank()) {
            return new Image(getClass().getResourceAsStream(DEFAULT_AVATAR));
        }

        if (avatarUrl.startsWith("http://") || avatarUrl.startsWith("https://")) {
            try {
                return new Image(avatarUrl, true);
            } catch (Exception e) {
                return new Image(getClass().getResourceAsStream(DEFAULT_AVATAR));
            }
        }

        File file = new File(avatarUrl);
        if (file.exists() && file.isFile()) {
            return new Image(file.toURI().toString(), true);
        }

        try (InputStream inputStream = getClass().getResourceAsStream("/images/avatars/" + avatarUrl)) {
            if (inputStream != null) {
                return new Image(inputStream);
            }
        } catch (Exception ignored) {
        }

        return new Image(getClass().getResourceAsStream(DEFAULT_AVATAR));
    }
}