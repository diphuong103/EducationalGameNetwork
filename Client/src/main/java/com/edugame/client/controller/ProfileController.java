package com.edugame.client.controller;

import com.edugame.client.model.User;
import com.edugame.client.network.ServerConnection;
import com.edugame.client.util.SceneManager;
import com.google.gson.JsonObject;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

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

    private boolean isLoading = false;

    @FXML
    public void initialize() {
        System.out.println("🚀 ProfileController.initialize() called");

        // Show loading state
        setLoadingState();

        // Load profile data
        loadProfileData();
    }

    /**
     * Set UI to loading state
     */
    private void setLoadingState() {
        nameLabel.setText("Đang tải...");
        scoreLabel.setText("--");
        totalGamesLabel.setText("--");
        winLabel.setText("--");
        winRateLabel.setText("--");
        ageLabel.setText("--");
    }

    /**
     * Load profile data from server
     */
    private void loadProfileData() {
        if (isLoading) {
            System.out.println("⚠️ Already loading profile");
            return;
        }

        isLoading = true;
        System.out.println("📝 Loading profile data...");

        ServerConnection server = ServerConnection.getInstance();

        if (!server.isConnected()) {
            System.err.println("❌ Not connected to server");
            Platform.runLater(() -> {
                showError("Không thể kết nối đến server. Vui lòng đăng nhập lại.");
                isLoading = false;
            });
            return;
        }

        // Call getProfile with callback
        server.getProfile(user -> {
            isLoading = false;

            if (user != null) {
                System.out.println("✅ Profile data received");
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

    /**
     * Update UI with user data
     */
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

        // Load avatar
        loadAvatar(user.getAvatarUrl());

        // Process name and age
        String fullName = (user.getFullName() != null && !user.getFullName().isEmpty())
                ? user.getFullName()
                : user.getUsername();

        String nameOnly = fullName;
        String ageText = "—";

        // Extract age from name if in format "Name (Age)"
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

        // Set UI values
        nameLabel.setText(nameOnly);
        ageLabel.setText(ageText);
        scoreLabel.setText(String.valueOf(user.getTotalScore()));
        totalGamesLabel.setText(String.valueOf(user.getTotalGames()));
        winLabel.setText(String.valueOf(user.getWins()));

        // Calculate and set win rate
        int winRate = user.getTotalGames() > 0
                ? (user.getWins() * 100 / user.getTotalGames())
                : 0;
        winRateLabel.setText(winRate + "%");

        System.out.println("✅ UI update completed successfully!");
    }

    /**
     * Load avatar image
     */
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

    /**
     * Load avatar image from various sources (URL, file, resource)
     */
    private Image loadAvatarImage(String avatarUrl) {
        final String DEFAULT_AVATAR = "/images/avatars/avatar4.png";

        // 🔹 Null hoặc empty → dùng default
        if (avatarUrl == null || avatarUrl.isBlank()) {
            return new Image(getClass().getResourceAsStream(DEFAULT_AVATAR));
        }

        // 🔹 URL từ internet
        if (avatarUrl.startsWith("http://") || avatarUrl.startsWith("https://")) {
            try {
                return new Image(avatarUrl, true); // true = load nền
            } catch (Exception e) {
                System.err.println("⚠️ Failed to load from URL: " + avatarUrl);
                return new Image(getClass().getResourceAsStream(DEFAULT_AVATAR));
            }
        }

        // 🔹 File từ máy tính
        File file = new File(avatarUrl);
        if (file.exists() && file.isFile()) {
            try {
                return new Image(file.toURI().toString(), true);
            } catch (Exception e) {
                System.err.println("⚠️ Failed to load local file: " + avatarUrl);
                return new Image(getClass().getResourceAsStream(DEFAULT_AVATAR));
            }
        }

        // 🔹 Resource nội bộ (avatar1.png, avatar2.png, ...)
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


    /** ---------------- EVENT HANDLERS ---------------- */

    @FXML
    public void handleBack(ActionEvent event) {
        try {
            SceneManager.getInstance().switchScene("home.fxml");
            // ✅ SceneManager tự động gọi onSceneShown() → refresh
        } catch (Exception e) {
            e.printStackTrace();
            showError("Không thể quay về trang chủ");
        }
    }

    @FXML
    public void handleEditName(ActionEvent event) {
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
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Chọn ảnh đại diện");

        // Tạo lưới ảnh mặc định
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(10));

        String[] defaultAvatars = {
                "avatar1.png", "avatar2.png", "avatar3.png", "avatar4.png",
                "avatar5.png", "avatar6.png", "avatar7.png", "avatar8.png"
        };

        final String[] selectedAvatar = {null};

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
                selectedAvatar[0] = defaultAvatars[finalI];
                dialog.setResult(defaultAvatars[finalI]);
                dialog.close();
            });

            grid.add(imgView, col, row);
        }

        // Nút chọn ảnh từ máy
        Button chooseFromPC = new Button("Chọn ảnh từ máy...");
        chooseFromPC.setOnAction(e -> {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Chọn ảnh đại diện");
            fileChooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter("Hình ảnh", "*.png", "*.jpg", "*.jpeg")
            );

            File file = fileChooser.showOpenDialog(avatarImage.getScene().getWindow());
            if (file != null) {
                // 🔹 Lưu đường dẫn đầy đủ thay vì chỉ tên file
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
            } else {
                System.out.println("⚠️ User clicked CLOSE / CANCEL — skip.");
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
            // 🔹 CẬP NHẬT NGAY VÀO SESSION ĐỂ HomeController LOAD ĐƯỢC
            server.setCurrentAvatarUrl(newAvatar);
        }

        server.sendJson(req);

        // 🔹 Hiển thị thông báo thành công
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
        User user = ServerConnection.getInstance().getCurrentUser();
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
        alert.setHeaderText("Kết quả học tập của bạn");
        alert.setContentText(detail);
        alert.showAndWait();
    }


    /** ---------------- UTILITIES ---------------- */

    private void showError(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Lỗi");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    private void showComingSoon(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Sắp ra mắt");
        alert.setHeaderText(null);
        alert.setContentText(message);

        javafx.animation.PauseTransition delay =
                new javafx.animation.PauseTransition(javafx.util.Duration.seconds(2));
        delay.setOnFinished(e -> alert.close());

        alert.show();
        delay.play();
    }
}