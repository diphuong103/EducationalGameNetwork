package com.edugame.client.controller;

import com.edugame.client.model.User;
import com.edugame.client.network.ServerConnection;
import com.edugame.client.util.SceneManager;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class ProfileController {

    @FXML private ImageView avatarImage;
    @FXML private Label nameLabel;
    @FXML private Label ageLabel;
    @FXML private Label scoreLabel;
    @FXML private Label totalGamesLabel;
    @FXML private Label winLabel;
    @FXML private Label winRateLabel;

    @FXML private Button editNameButton;
    @FXML private Button changeAvatarButton;

    // ✅ THÊM: Buttons cho tương tác với người khác
    @FXML private HBox actionButtonsBox; // Container cho các nút action
    @FXML private Button chatButton;
    @FXML private Button addFriendButton;

    private boolean isLoading = false;
    private User viewingUser;
    private boolean isOwnProfile = true;
    private boolean dataSetBeforeInit = false;
    private String friendshipStatus = "none"; // none, pending, friend

    public void initData(User user) {
        System.out.println("🔍 initData() called with user: " + user.getUsername());
        this.viewingUser = user;

        ServerConnection server = ServerConnection.getInstance();
        int currentUserId = server.getCurrentUserId();

        // ✅ Nếu là chính mình → đánh dấu là own profile
        if (user.getUserId() == currentUserId) {
            System.out.println("👤 Viewing OWN profile → hide action buttons");
            isOwnProfile = true;
            dataSetBeforeInit = false; // để initialize() hiểu là profile cá nhân
            return;
        }

        this.isOwnProfile = false;
        this.dataSetBeforeInit = true;
    }

    @FXML
    public void initialize() {
        System.out.println("🚀 ProfileController.initialize() called");
        System.out.println("   dataSetBeforeInit: " + dataSetBeforeInit);
        System.out.println("   viewingUser: " + (viewingUser != null ? viewingUser.getUsername() : "null"));

        // ✅ Ẩn action buttons mặc định
        if (actionButtonsBox != null) {
            actionButtonsBox.setVisible(false);
            actionButtonsBox.setManaged(false);
        }

        if (dataSetBeforeInit && viewingUser != null) {
            System.out.println("✅ Viewing OTHER user's profile: " + viewingUser.getUsername());
            isOwnProfile = false;
            updateUI(viewingUser);
            hideEditButtons();

            // ✅ Kiểm tra trạng thái bạn bè và hiển thị nút phù hợp
            checkFriendshipStatus();
            return;
        }

        System.out.println("✅ Viewing OWN profile");
        isOwnProfile = true;
        setLoadingState();
        loadProfileData();
    }

    /**
     * ✅ THÊM: Kiểm tra trạng thái bạn bè
     */
    private void checkFriendshipStatus() {
        if (viewingUser == null) return;

        ServerConnection server = ServerConnection.getInstance();
        int currentUserId = server.getCurrentUserId();
        int targetUserId = viewingUser.getUserId();

        System.out.println("🔍 Checking friendship status between " + currentUserId + " and " + targetUserId);

        // Gọi server để check status
        server.checkFriendshipStatus(targetUserId, status -> {
            Platform.runLater(() -> {
                this.friendshipStatus = status;
                System.out.println("✅ Friendship status: " + status);
                updateActionButtons(status);
            });
        });
    }

    /**
     * ✅ THÊM: Update action buttons dựa trên trạng thái
     */
    private void updateActionButtons(String status) {
        if (actionButtonsBox == null || chatButton == null || addFriendButton == null) {
            System.err.println("⚠️ Action buttons not initialized");
            return;
        }

        // Reset visibility
        chatButton.setVisible(false);
        chatButton.setManaged(false);
        addFriendButton.setVisible(false);
        addFriendButton.setManaged(false);

        switch (status) {
            case "friend" -> {
                // ✅ Đã là bạn bè → hiện nút chat
                System.out.println("👥 Is friend → showing chat button");
                chatButton.setVisible(true);
                chatButton.setManaged(true);
                actionButtonsBox.setVisible(true);
                actionButtonsBox.setManaged(true);
            }
            case "pending_sent" -> {
                // ⏳ Đã gửi lời mời → hiện nút pending
                System.out.println("⏳ Pending sent → showing pending button");
                addFriendButton.setText("⏳ Đã gửi lời mời");
                addFriendButton.setStyle(
                        "-fx-background-color: #FFC107; " +
                                "-fx-text-fill: white; " +
                                "-fx-background-radius: 10; " +
                                "-fx-font-weight: bold; " +
                                "-fx-font-size: 14px; " +
                                "-fx-padding: 10 20;"
                );
                addFriendButton.setDisable(true);
                addFriendButton.setVisible(true);
                addFriendButton.setManaged(true);
                actionButtonsBox.setVisible(true);
                actionButtonsBox.setManaged(true);
            }
            case "pending_received" -> {
                // 📨 Nhận được lời mời → hiện nút chấp nhận
                System.out.println("📨 Pending received → showing accept button");
                addFriendButton.setText("✓ Chấp nhận kết bạn");
                addFriendButton.setStyle(
                        "-fx-background-color: #4CAF50; " +
                                "-fx-text-fill: white; " +
                                "-fx-background-radius: 10; " +
                                "-fx-font-weight: bold; " +
                                "-fx-font-size: 14px; " +
                                "-fx-padding: 10 20;"
                );
                addFriendButton.setDisable(false);
                addFriendButton.setVisible(true);
                addFriendButton.setManaged(true);
                actionButtonsBox.setVisible(true);
                actionButtonsBox.setManaged(true);
            }
            default -> {
                // ➕ Chưa là bạn → hiện nút thêm bạn
                System.out.println("➕ Not friend → showing add friend button");
                addFriendButton.setText("➕ Add");
                addFriendButton.setStyle(
                        "-fx-background-color: #2196F3; " +
                                "-fx-text-fill: white; " +
                                "-fx-background-radius: 10; " +
                                "-fx-font-weight: bold; " +
                                "-fx-font-size: 14px; " +
                                "-fx-padding: 10 20;"
                );
                addFriendButton.setDisable(false);
                addFriendButton.setVisible(true);
                addFriendButton.setManaged(true);
                actionButtonsBox.setVisible(true);
                actionButtonsBox.setManaged(true);
            }
        }
    }

    /**
     * ✅ THÊM: Xử lý nút chat
     */
    @FXML
    public void handleChat(ActionEvent event) {
        if (viewingUser == null) {
            showError("Không có thông tin người dùng!");
            return;
        }

        try {
            System.out.println("💬 Opening chat with: " + viewingUser.getFullName());

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/ChatWindow.fxml"));
            Parent root = loader.load();

            // Get controller và init data
            com.edugame.client.controller.ChatController chatController = loader.getController();
            chatController.initData(
                    viewingUser.getUserId(),
                    viewingUser.getFullName(),
                    viewingUser.getAvatarUrl(),
                    viewingUser.isOnline()
            );

            // Tạo stage mới
            Stage chatStage = new Stage();
            chatStage.setTitle("Chat với " + viewingUser.getFullName());

            // Load icon
            try {
                InputStream iconStream = getClass().getResourceAsStream("/images/icon.png");
                if (iconStream != null) {
                    chatStage.getIcons().add(new Image(iconStream));
                }
            } catch (Exception e) {
                System.out.println("⚠️ Cannot load icon: " + e.getMessage());
            }

            chatStage.setScene(new Scene(root));
            chatStage.setMinWidth(500);
            chatStage.setMinHeight(600);
            chatStage.setWidth(550);
            chatStage.setHeight(700);
            chatStage.show();

            System.out.println("✅ Chat window opened");

        } catch (Exception e) {
            System.err.println("❌ Error opening chat: " + e.getMessage());
            e.printStackTrace();
            showError("Không thể mở cửa sổ chat!");
        }
    }

    /**
     * ✅ THÊM: Xử lý nút thêm bạn / chấp nhận
     */
    @FXML
    public void handleAddFriend(ActionEvent event) {
        if (viewingUser == null) {
            showError("Không có thông tin người dùng!");
            return;
        }

        ServerConnection server = ServerConnection.getInstance();

        switch (friendshipStatus) {
            case "pending_received" -> {
                // Chấp nhận lời mời
                System.out.println("✅ Accepting friend request from: " + viewingUser.getFullName());
                server.acceptFriendRequest(viewingUser.getUserId(), success -> {
                    Platform.runLater(() -> {
                        if (success) {
                            showSuccess("Đã chấp nhận lời mời kết bạn!");
                            checkFriendshipStatus(); // Refresh status
                        }
                    });
                });
            }
            default -> {
                // Gửi lời mời kết bạn
                System.out.println("📨 Sending friend request to: " + viewingUser.getFullName());
                server.sendFriendRequest(viewingUser.getUserId(), success -> {
                    Platform.runLater(() -> {
                        if (success) {
                            showSuccess("Đã gửi lời mời kết bạn!");
                            checkFriendshipStatus(); // Refresh status
                        }
                    });
                });
            }
        }
    }

    private void hideEditButtons() {
        System.out.println("🔒 Hiding edit buttons (viewing other user's profile)");

        if (editNameButton != null) {
            editNameButton.setVisible(false);
            editNameButton.setManaged(false);
        }
        if (changeAvatarButton != null) {
            changeAvatarButton.setVisible(false);
            changeAvatarButton.setManaged(false);
        }
    }

    private void setLoadingState() {
        nameLabel.setText("Đang tải...");
        scoreLabel.setText("--");
        totalGamesLabel.setText("--");
        winLabel.setText("--");
        winRateLabel.setText("--");
        ageLabel.setText("--");
    }

    private void loadProfileData() {
        if (isLoading) {
            System.out.println("⚠️ Already loading profile");
            return;
        }

        isLoading = true;
        System.out.println("📝 Loading CURRENT USER profile data...");

        ServerConnection server = ServerConnection.getInstance();

        if (!server.isConnected()) {
            System.err.println("❌ Not connected to server");
            Platform.runLater(() -> {
                showError("Không thể kết nối đến server. Vui lòng đăng nhập lại.");
                isLoading = false;
            });
            return;
        }

        server.getProfile(user -> {
            isLoading = false;

            if (user != null) {
                System.out.println("✅ Current user profile data received");
                Platform.runLater(() -> updateUI(user));
            } else {
                System.err.println("❌ Failed to load profile");
                Platform.runLater(() -> {
                    nameLabel.setText("Không thể tải hồ sơ");
                    showError("Không thể tải thông tin người dùng. Vui lòng thử lại sau.");
                });
            }
        });
    }

    private void updateUI(User user) {
        if (user == null) {
            System.err.println("❌ updateUI called with NULL user!");
            return;
        }

        System.out.println("🎨 Updating UI with user data:");
        System.out.println("   - Full Name: " + user.getFullName());
        System.out.println("   - Username: " + user.getUsername());
        System.out.println("   - Total Score: " + user.getTotalScore());
        System.out.println("   - Total Games: " + user.getTotalGames());
        System.out.println("   - Wins: " + user.getWins());
        System.out.println("   - Age: " + user.getAge());
        System.out.println("   - Is Own Profile: " + isOwnProfile);

        loadAvatar(user.getAvatarUrl());

        String fullName = (user.getFullName() != null && !user.getFullName().isEmpty())
                ? user.getFullName()
                : user.getUsername();

        String nameOnly = fullName;
        String ageText = "—";

        if (fullName.contains("(") && fullName.contains(")")) {
            int start = fullName.indexOf('(');
            int end = fullName.indexOf(')');
            if (end > start) {
                nameOnly = fullName.substring(0, start).trim();
                String inside = fullName.substring(start + 1, end).trim();

                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+").matcher(inside);
                if (m.find()) {
                    ageText = m.group();
                    System.out.println("   ✅ Extracted age from name: " + ageText);
                }
            }
        } else if (user.getAge() > 0) {
            ageText = String.valueOf(user.getAge());
            System.out.println("   ✅ Using age from User object: " + ageText);
        }

        nameLabel.setText(nameOnly);
        ageLabel.setText(ageText);
        scoreLabel.setText(String.valueOf(user.getTotalScore()));
        totalGamesLabel.setText(String.valueOf(user.getTotalGames()));
        winLabel.setText(String.valueOf(user.getWins()));

        int winRate = user.getTotalGames() > 0
                ? (user.getWins() * 100 / user.getTotalGames())
                : 0;
        winRateLabel.setText(winRate + "%");

        System.out.println("✅ UI update completed successfully!");
    }

    private void loadAvatar(String avatarUrl) {
        final String DEFAULT_AVATAR = "/images/avatars/avatar4.png";

        try {
            System.out.println("📸 Loading avatar from: " + avatarUrl);
            Image avatar = loadAvatarImage(avatarUrl);
            avatarImage.setImage(avatar);
            System.out.println("✅ Avatar loaded successfully");
        } catch (Exception e) {
            System.err.println("❌ Error loading avatar: " + e.getMessage());
            avatarImage.setImage(new Image(getClass().getResourceAsStream(DEFAULT_AVATAR)));
        }
    }

    private Image loadAvatarImage(String avatarUrl) {
        final String DEFAULT_AVATAR = "/images/avatars/avatar4.png";

        if (avatarUrl == null || avatarUrl.isBlank()) {
            return new Image(getClass().getResourceAsStream(DEFAULT_AVATAR));
        }

        if (avatarUrl.startsWith("http://") || avatarUrl.startsWith("https://")) {
            try {
                return new Image(avatarUrl, true);
            } catch (Exception e) {
                System.err.println("⚠️ Failed to load from URL: " + avatarUrl);
                return new Image(getClass().getResourceAsStream(DEFAULT_AVATAR));
            }
        }

        File file = new File(avatarUrl);
        if (file.exists() && file.isFile()) {
            try {
                return new Image(file.toURI().toString(), true);
            } catch (Exception e) {
                System.err.println("⚠️ Failed to load local file: " + avatarUrl);
                return new Image(getClass().getResourceAsStream(DEFAULT_AVATAR));
            }
        }

        try {
            String resourcePath = "/images/avatars/" + avatarUrl;
            InputStream inputStream = getClass().getResourceAsStream(resourcePath);

            if (inputStream != null) {
                return new Image(inputStream);
            } else {
                System.err.println("⚠️ Resource not found: " + resourcePath);
                return new Image(getClass().getResourceAsStream(DEFAULT_AVATAR));
            }
        } catch (Exception e) {
            System.err.println("⚠️ Error loading resource: " + avatarUrl);
            return new Image(getClass().getResourceAsStream(DEFAULT_AVATAR));
        }
    }

    @FXML
    public void handleBack(ActionEvent event) {
        try {
            if (!isOwnProfile) {
                ((javafx.stage.Stage) nameLabel.getScene().getWindow()).close();
            } else {
                SceneManager.getInstance().switchScene("home.fxml");
            }
        } catch (Exception e) {
            e.printStackTrace();
            showError("Không thể quay về trang chủ");
        }
    }

    @FXML
    public void handleEditName(ActionEvent event) {
        if (!isOwnProfile) {
            showError("Bạn không thể chỉnh sửa profile của người khác!");
            return;
        }

        TextInputDialog dialog = new TextInputDialog(nameLabel.getText());
        dialog.setTitle("Đổi tên hiển thị");
        dialog.setHeaderText("Nhập tên mới của bạn:");
        dialog.setContentText("Tên:");

        dialog.showAndWait().ifPresent(newName -> {
            if (!newName.trim().isEmpty()) {
                updateProfile(newName, null);
            }
        });
    }

    @FXML
    public void handleChangeAvatar(ActionEvent event) {
        if (!isOwnProfile) {
            showError("Bạn không thể chỉnh sửa profile của người khác!");
            return;
        }

        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Chọn ảnh đại diện");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));

        String[] defaultAvatars = {
                "avatar1.png", "avatar2.png", "avatar3.png", "avatar4.png",
                "avatar5.png", "avatar6.png", "avatar7.png", "avatar8.png"
        };

        for (int i = 0; i < defaultAvatars.length; i++) {
            String avatarPath = "/images/avatars/" + defaultAvatars[i];
            ImageView imgView = new ImageView(new Image(getClass().getResource(avatarPath).toExternalForm()));
            imgView.setFitWidth(60);
            imgView.setFitHeight(60);
            imgView.setCursor(Cursor.HAND);

            int row = i / 4;
            int col = i % 4;

            int finalI = i;
            imgView.setOnMouseClicked(e -> {
                dialog.setResult(defaultAvatars[finalI]);
                dialog.close();
            });

            grid.add(imgView, col, row);
        }

        Button chooseFromPC = new Button("Chọn ảnh từ máy...");
        chooseFromPC.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Chọn ảnh đại diện");
            fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Hình ảnh", "*.png", "*.jpg", "*.jpeg")
            );

            File file = fileChooser.showOpenDialog(avatarImage.getScene().getWindow());
            if (file != null) {
                dialog.setResult(file.getAbsolutePath());
                dialog.close();
            }
        });

        VBox box = new VBox(10, grid, chooseFromPC);
        box.setAlignment(Pos.CENTER);
        dialog.getDialogPane().setContent(box);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        dialog.showAndWait().ifPresent(result -> {
            if (result instanceof String) {
                String selected = (String) result;

                boolean isDefaultAvatar = false;
                for (String avatar : defaultAvatars) {
                    if (selected.equals(avatar)) {
                        isDefaultAvatar = true;
                        break;
                    }
                }

                if (isDefaultAvatar) {
                    avatarImage.setImage(new Image(getClass().getResource("/images/avatars/" + selected).toExternalForm()));
                    updateProfile(null, selected);
                } else {
                    File selectedFile = new File(selected);
                    if (selectedFile.exists()) {
                        avatarImage.setImage(new Image(selectedFile.toURI().toString()));
                        updateProfile(null, selectedFile.getAbsolutePath());
                    }
                }
            }
        });
    }

    private void updateProfile(String newName, String newAvatar) {
        ServerConnection server = ServerConnection.getInstance();
        if (!server.isConnected()) {
            showError("Không thể kết nối server");
            return;
        }

        Map<String, Object> req = new HashMap<>();
        req.put("type", "UPDATE_PROFILE");
        if (newName != null) req.put("fullName", newName);
        if (newAvatar != null) {
            req.put("avatarUrl", newAvatar);
            server.setCurrentAvatarUrl(newAvatar);
        }

        server.sendJson(req);

        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Thành công");
            alert.setContentText("Cập nhật thông tin thành công!");
            alert.show();

            javafx.animation.PauseTransition delay =
                    new javafx.animation.PauseTransition(javafx.util.Duration.seconds(1.5));
            delay.setOnFinished(e -> alert.close());
            delay.play();
        });
    }

    @FXML
    public void handleShowDetails(ActionEvent event) {
        User user = isOwnProfile ?
                ServerConnection.getInstance().getCurrentUser() :
                viewingUser;

        if (user == null) {
            showError("Chưa có thông tin người dùng!");
            return;
        }

        String detail = String.format("""
        📘 Điểm chi tiết:
        • Toán: %d
        • Anh: %d
        • Văn: %d
        • Tổng điểm: %d
        """,
                user.getMathScore(),
                user.getEnglishScore(),
                user.getLiteratureScore(),
                user.getTotalScore()
        );

        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Chi tiết điểm");
        alert.setHeaderText("Kết quả học tập của " + (isOwnProfile ? "bạn" : user.getFullName()));
        alert.setContentText(detail);
        alert.showAndWait();
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

    private void showSuccess(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Thành công");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.show();

            javafx.animation.PauseTransition delay =
                    new javafx.animation.PauseTransition(javafx.util.Duration.seconds(2));
            delay.setOnFinished(e -> alert.close());
            delay.play();
        });
    }
}