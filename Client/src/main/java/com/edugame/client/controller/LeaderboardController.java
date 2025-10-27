package com.edugame.client.controller;

import com.edugame.client.model.User;
import com.edugame.client.network.ServerConnection;
import com.edugame.client.util.SceneManager;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LeaderboardController {

    @FXML private VBox leaderboardContainer;
    @FXML private ComboBox<String> subjectFilter;
    @FXML private TextField searchField;
    @FXML private Button backButton;
    @FXML private Label loadingLabel;
    @FXML private ProgressIndicator loadingIndicator;

    private ServerConnection serverConnection;
    private List<User> allUsers = new ArrayList<>();
    private String currentSubject = "total";

    @FXML
    private ToggleButton btnAllSubjects, btnMath, btnEnglish, btnLiterature;

    @FXML
    public void initialize() {
        System.out.println("🚀 LeaderboardController initializing...");

        serverConnection = ServerConnection.getInstance();

        ToggleGroup filterGroup = new ToggleGroup();
        btnAllSubjects.setToggleGroup(filterGroup);
        btnMath.setToggleGroup(filterGroup);
        btnEnglish.setToggleGroup(filterGroup);
        btnLiterature.setToggleGroup(filterGroup);

        btnAllSubjects.setSelected(true);

        // Setup subject filter
        setupSubjectFilter();

        // Setup search
        setupSearch();

        // Show loading state
        showLoading(true);

        // Load leaderboard data
        loadLeaderboardData();
    }

    /**
     * Setup subject filter dropdown
     */
    private void setupSubjectFilter() {
        if (subjectFilter != null) {
            subjectFilter.getItems().addAll(
                    "Tổng điểm",
                    "Toán học",
                    "Tiếng Anh",
                    "Văn học"
            );
            subjectFilter.setValue("Tổng điểm");

            subjectFilter.setOnAction(e -> {
                String selected = subjectFilter.getValue();
                switch (selected) {
                    case "Toán học": currentSubject = "math"; break;
                    case "Tiếng Anh": currentSubject = "english"; break;
                    case "Văn học": currentSubject = "literature"; break;
                    default: currentSubject = "total";
                }
                loadLeaderboardData();
            });
        }
    }

    /**
     * Setup search functionality
     */
    private void setupSearch() {
        if (searchField != null) {
            searchField.textProperty().addListener((obs, oldVal, newVal) -> {
                filterLeaderboard(newVal);
            });
        }
    }

    /**
     * Load leaderboard data from server
     */
    private void loadLeaderboardData() {
        if (!serverConnection.isConnected()) {
            showError("Không thể kết nối đến server");
            showLoading(false);
            return;
        }

        System.out.println("📊 Loading leaderboard for subject: " + currentSubject);
        showLoading(true);

        // ✅ QUAN TRỌNG: Gọi getLeaderboardBySubject với currentSubject
        serverConnection.getLeaderboardBySubject(currentSubject, 100, leaderboardData -> {
            Platform.runLater(() -> {
                if (leaderboardData != null && !leaderboardData.isEmpty()) {
                    // Convert to User list
                    allUsers.clear();
                    for (Map<String, Object> data : leaderboardData) {
                        User user = new User();
                        user.setUserId((Integer) data.get("userId"));
                        user.setUsername((String) data.get("username"));
                        user.setFullName((String) data.get("fullName"));
                        user.setTotalScore((Integer) data.get("totalScore")); // Điểm của môn được chọn
                        user.setOnline((Boolean) data.get("isOnline"));

                        if (data.containsKey("avatarUrl")) {
                            user.setAvatarUrl((String) data.get("avatarUrl"));
                        }

                        allUsers.add(user);
                    }

                    System.out.println("✅ Leaderboard loaded: " + allUsers.size() + " users for " + currentSubject);
                    displayLeaderboard(allUsers);
                    showLoading(false);
                } else {
                    System.err.println("⚠️ No leaderboard data received");
                    showError("Không có dữ liệu bảng xếp hạng");
                    showLoading(false);
                }
            });
        });
    }

    /**
     * Display leaderboard in UI
     */
    private void displayLeaderboard(List<User> users) {
        if (leaderboardContainer == null) {
            System.err.println("❌ leaderboardContainer is null!");
            return;
        }

        leaderboardContainer.getChildren().clear();

        if (users.isEmpty()) {
            Label emptyLabel = new Label("Không có dữ liệu");
            emptyLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #999;");
            leaderboardContainer.getChildren().add(emptyLabel);
            return;
        }

        // Find current user
        int currentUserId = serverConnection.getCurrentUserId();

        for (int i = 0; i < users.size(); i++) {
            User user = users.get(i);
            int rank = i + 1;
            boolean isCurrentUser = (user.getUserId() == currentUserId);

            HBox rankItem = createRankItem(rank, user, isCurrentUser);
            leaderboardContainer.getChildren().add(rankItem);
        }

        System.out.println("✅ Displayed " + users.size() + " users in leaderboard");
    }

    /**
     * Create a rank item UI component
     */
    private HBox createRankItem(int rank, User user, boolean isCurrentUser) {
        HBox rankItem = new HBox(15);
        rankItem.setPadding(new Insets(12, 20, 12, 20));
        rankItem.setAlignment(Pos.CENTER_LEFT);
        rankItem.setStyle(
                "-fx-background-color: " + (isCurrentUser ? "#e3f2fd" : "white") + ";" +
                        "-fx-background-radius: 10;" +
                        "-fx-border-color: " + (isCurrentUser ? "#2196F3" : "#e0e0e0") + ";" +
                        "-fx-border-width: " + (isCurrentUser ? "2" : "1") + ";" +
                        "-fx-border-radius: 10;" +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 5, 0, 0, 2);"
        );

        // Rank badge
        StackPane rankBadge = createRankBadge(rank);

        // Avatar
        ImageView avatar = createAvatar(user.getAvatarUrl());

        // User info
        VBox userInfo = new VBox(5);
        HBox.setHgrow(userInfo, Priority.ALWAYS);

        // Name with highlight for current user
        String displayName = isCurrentUser ? "⭐ " +
                (user.getFullName() != null ? user.getFullName() : user.getUsername()) :
                (user.getFullName() != null ? user.getFullName() : user.getUsername());

        Label nameLabel = new Label(displayName);
        nameLabel.setStyle(
                "-fx-font-size: " + (rank <= 3 ? "16px" : "14px") + ";" +
                        "-fx-font-weight: " + (rank <= 3 || isCurrentUser ? "bold" : "normal") + ";" +
                        "-fx-text-fill: " + (isCurrentUser ? "#1976D2" : "#333") + ";"
        );

        Label usernameLabel = new Label("@" + user.getUsername());
        usernameLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #666;");

        userInfo.getChildren().addAll(nameLabel, usernameLabel);

        // Score
        VBox scoreBox = new VBox(2);
        scoreBox.setAlignment(Pos.CENTER_RIGHT);

        Label scoreLabel = new Label(formatScore(user.getTotalScore()));
        scoreLabel.setStyle(
                "-fx-font-size: " + (rank <= 3 ? "18px" : "16px") + ";" +
                        "-fx-font-weight: bold;" +
                        "-fx-text-fill: " + getRankColor(rank) + ";"
        );

        Label scoreText = new Label("điểm");
        scoreText.setStyle("-fx-font-size: 12px; -fx-text-fill: #666;");

        scoreBox.getChildren().addAll(scoreLabel, scoreText);

        // Online indicator
        if (user.isOnline()) {
            HBox onlineBox = new HBox(5);
            onlineBox.setAlignment(Pos.CENTER);

            Text onlineDot = new Text("●");
            onlineDot.setStyle("-fx-fill: #4CAF50; -fx-font-size: 12px;");

            Label onlineLabel = new Label("Online");
            onlineLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #4CAF50;");

            onlineBox.getChildren().addAll(onlineDot, onlineLabel);

            rankItem.getChildren().addAll(rankBadge, avatar, userInfo, scoreBox, onlineBox);
        } else {
            HBox offlineBox = new HBox(5);
            offlineBox.setAlignment(Pos.CENTER);

            Text offlineDot = new Text("●");
            offlineDot.setStyle("-fx-fill: #9E9E9E; -fx-font-size: 12px;");

            Label offlineLabel = new Label("Offline");
            offlineLabel.setStyle("-fx-font-size: 11px; -fx-text-fill: #9E9E9E;");

            offlineBox.getChildren().addAll(offlineDot, offlineLabel);

            rankItem.getChildren().addAll(rankBadge, avatar, userInfo, scoreBox, offlineBox);
        }

        // Hover effect
        rankItem.setOnMouseEntered(e -> {
            rankItem.setStyle(
                    rankItem.getStyle() +
                            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 10, 0, 0, 3);" +
                            "-fx-cursor: hand;"
            );
        });

        rankItem.setOnMouseExited(e -> {
            rankItem.setStyle(
                    rankItem.getStyle().replace(
                            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 10, 0, 0, 3);",
                            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 5, 0, 0, 2);"
                    ) +
                            "-fx-cursor: default;"
            );
        });

        return rankItem;
    }

    /**
     * Create rank badge
     */
    private StackPane createRankBadge(int rank) {
        StackPane badge = new StackPane();
        badge.setPrefSize(50, 50);
        badge.setMinSize(50, 50);
        badge.setMaxSize(50, 50);

        String bgColor;
        String textColor;
        String emoji = "";

        if (rank == 1) {
            bgColor = "#FFD700";
            textColor = "#FFF";
            emoji = "🥇";
        } else if (rank == 2) {
            bgColor = "#C0C0C0";
            textColor = "#FFF";
            emoji = "🥈";
        } else if (rank == 3) {
            bgColor = "#CD7F32";
            textColor = "#FFF";
            emoji = "🥉";
        } else {
            bgColor = "#f5f5f5";
            textColor = "#666";
        }

        badge.setStyle(
                "-fx-background-color: " + bgColor + ";" +
                        "-fx-background-radius: 25;" +
                        "-fx-border-color: white;" +
                        "-fx-border-width: 2;" +
                        "-fx-border-radius: 25;"
        );

        Label rankLabel = new Label(rank <= 3 ? emoji : String.valueOf(rank));
        rankLabel.setStyle(
                "-fx-font-size: " + (rank <= 3 ? "20px" : "16px") + ";" +
                        "-fx-font-weight: bold;" +
                        "-fx-text-fill: " + textColor + ";"
        );

        badge.getChildren().add(rankLabel);
        return badge;
    }

    /**
     * Create avatar image
     */
    private ImageView createAvatar(String avatarUrl) {
        ImageView avatar = new ImageView();
        avatar.setFitWidth(50);
        avatar.setFitHeight(50);
        avatar.setPreserveRatio(true);

        try {
            String path = "/images/avatars/" +
                    (avatarUrl != null ? avatarUrl : "avatar4.png");
            Image image = new Image(getClass().getResourceAsStream(path));

            if (image.isError()) {
                image = new Image(getClass().getResourceAsStream("/images/avatars/avatar4.png"));
            }

            avatar.setImage(image);
        } catch (Exception e) {
            System.err.println("❌ Error loading avatar: " + e.getMessage());
        }

        // Circular clip
        javafx.scene.shape.Circle clip = new javafx.scene.shape.Circle(25, 25, 25);
        avatar.setClip(clip);

        return avatar;
    }

    /**
     * Filter leaderboard by search text
     */
    private void filterLeaderboard(String searchText) {
        if (searchText == null || searchText.trim().isEmpty()) {
            displayLeaderboard(allUsers);
            return;
        }

        String search = searchText.toLowerCase().trim();
        List<User> filtered = new ArrayList<>();

        for (User user : allUsers) {
            String username = user.getUsername().toLowerCase();
            String fullName = user.getFullName() != null ?
                    user.getFullName().toLowerCase() : "";

            if (username.contains(search) || fullName.contains(search)) {
                filtered.add(user);
            }
        }

        displayLeaderboard(filtered);
    }

    /**
     * Show/hide loading indicator
     */
    private void showLoading(boolean show) {
        if (loadingLabel != null) {
            loadingLabel.setVisible(show);
            loadingLabel.setManaged(show);
        }

        if (loadingIndicator != null) {
            loadingIndicator.setVisible(show);
            loadingIndicator.setManaged(show);
        }

        if (leaderboardContainer != null) {
            leaderboardContainer.setVisible(!show);
            leaderboardContainer.setManaged(!show);
        }
    }

    /**
     * Get rank color
     */
    private String getRankColor(int rank) {
        if (rank == 1) return "#FFD700";
        if (rank == 2) return "#C0C0C0";
        if (rank == 3) return "#CD7F32";
        return "#666";
    }

    /**
     * Format score with thousands separator
     */
    private String formatScore(int score) {
        return String.format("%,d", score).replace(",", ".");
    }

    /**
     * Show error message
     */
    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Lỗi");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    /** ---------------- EVENT HANDLERS ---------------- */

    @FXML
    private void handleBack() {
        try {
            SceneManager.getInstance().switchScene("home.fxml");
        } catch (Exception e) {
            e.printStackTrace();
            showError("Không thể quay về trang chủ");
        }
    }

    @FXML
    private void handleRefresh() {
        loadLeaderboardData();
    }

    /** ---------------- FILTER HANDLERS ---------------- */

    @FXML
    private void handleFilterAll() {
        System.out.println("🔘 Filter: Tổng điểm");
        currentSubject = "total";
        if (subjectFilter != null) {
            subjectFilter.setValue("Tổng điểm");
        }
        loadLeaderboardData();
    }

    @FXML
    private void handleFilterMath() {
        System.out.println("🔘 Filter: Toán học");
        currentSubject = "math";
        if (subjectFilter != null) {
            subjectFilter.setValue("Toán học");
        }
        loadLeaderboardData();
    }

    @FXML
    private void handleFilterEnglish() {
        System.out.println("🔘 Filter: Tiếng Anh");
        currentSubject = "english";
        if (subjectFilter != null) {
            subjectFilter.setValue("Tiếng Anh");
        }
        loadLeaderboardData();
    }

    @FXML
    private void handleFilterLiterature() {
        System.out.println("🔘 Filter: Văn học");
        currentSubject = "literature";
        if (subjectFilter != null) {
            subjectFilter.setValue("Văn học");
        }
        loadLeaderboardData();
    }
}
