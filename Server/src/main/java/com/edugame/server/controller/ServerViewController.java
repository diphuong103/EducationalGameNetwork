package com.edugame.server.controller;

import com.edugame.common.Protocol;
import com.edugame.server.database.ServerMessageDAO;
import com.edugame.server.database.UserDAO;
import com.edugame.server.game.GameRoomManager;
import com.edugame.server.model.ServerMessage;
import com.edugame.server.model.User;
import com.edugame.server.network.ClientHandler;
import com.edugame.server.network.GameServer;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.scene.control.SelectionMode;

import java.io.*;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

public class ServerViewController {

    // ==================== FXML INJECTIONS ====================

    // Header
    @FXML private Label serverStatusLabel;
    @FXML private Label connectionsLabel;
    @FXML private Label roomsLabel;
    @FXML private Label playersLabel;
    @FXML private Button startButton;
    @FXML private Button stopButton;
    @FXML private Button questionBankButton;

    // Room Management
    @FXML private VBox roomListContainer;
    @FXML private Label selectedRoomTitle;
    @FXML private Label roomStatusBadge;
    @FXML private Button closeRoomButton;
    @FXML private GridPane roomInfoGrid;
    @FXML private Label roomIdLabel;
    @FXML private Label hostLabel;
    @FXML private Label subjectLabel;
    @FXML private Label difficultyLabel;
    @FXML private VBox playersSection;
    @FXML private VBox playerListContainer;
    @FXML private VBox adminActionsSection;
    @FXML private Button pauseButton;
    @FXML private Button resumeButton;
    @FXML private Button announceButton;
    @FXML private Button destroyButton;
    @FXML private VBox emptyState;

    // User Management
    @FXML private TextField userSearchField;
    @FXML private ComboBox<String> userStatusFilter;
    @FXML private ComboBox<String> userSortFilter;
    @FXML private Label totalUsersLabel;
    @FXML private Label filteredCountLabel;
    @FXML private TableView<UserTableRow> userTable;

    // Console
    @FXML private TextArea consoleArea;

    // Statistics
    @FXML private Label totalConnectionsLabel;
    @FXML private Label totalGamesLabel;
    @FXML private Label peakPlayersLabel;
    @FXML private Label uptimeLabel;
    @FXML private Label messagesSentLabel;
    @FXML private Label avgResponseLabel;

    // Status Bar
    @FXML private Label portLabel;
    @FXML private Label uptimeStatusLabel;
    @FXML private Label memoryLabel;
    @FXML private Label timestampLabel;

    @FXML private Label totalRoomsLabel;
    @FXML private Label playingRoomsLabel;
    @FXML private Label waitingRoomsLabel;
    @FXML private Label subjectIconLabel;
    @FXML private Label playerCountLabel;
    @FXML private Label currentQuestionLabel;
    @FXML private Label timeRemainingLabel;
    @FXML private Label correctAnswersLabel;
    @FXML private Label wrongAnswersLabel;
    @FXML private Label skippedLabel;
    @FXML private ProgressBar gameProgressBar;
    @FXML private VBox roomContent;
    @FXML private Button kickPlayerButton;

    @FXML private Label selectedUsersCountLabel;
    @FXML private Button sendBroadcastButton;
    @FXML private Button sendGroupButton;
    @FXML private Button sendPrivateButton;
    @FXML private TextArea serverMessageInput;
    @FXML private TableView<UserTableRow> recipientTable;
    @FXML private CheckBox importantCheckBox;

    // Server Chat injections
    @FXML private TextField recipientSearchField;
    @FXML private ComboBox<String> recipientStatusFilter;
    @FXML private VBox messageHistoryContainer;

    @FXML private Label charCountLabel;

    private ServerMessageDAO serverMessageDAO;
    private Set<Integer> selectedUserIds = new HashSet<>();
    // ==================== INSTANCE VARIABLES ====================

    private GameServer gameServer;
    private GameRoomManager roomManager;
    private UserDAO userDAO;
    private Thread serverThread;
    private Timer updateTimer;
    private long startTime;

    private int totalConnections = 0;
    private int totalGames = 0;
    private int peakPlayers = 0;
    private String selectedRoomId = null;

    private ObservableList<UserTableRow> allUsers = FXCollections.observableArrayList();
    private ObservableList<UserTableRow> filteredUsers = FXCollections.observableArrayList();

    // ==================== INITIALIZATION ====================

    @FXML
    public void initialize() {
        System.out.println("🔍 [DEBUG] ========== ServerViewController Initialize ==========");
        System.out.println("✅ ServerViewController initialized");

        // Check FXML injections
        System.out.println("🔍 [DEBUG] Checking FXML injections...");
        System.out.println("   - userTable: " + (userTable != null ? "OK" : "NULL"));
        System.out.println("   - totalUsersLabel: " + (totalUsersLabel != null ? "OK" : "NULL"));
        System.out.println("   - filteredCountLabel: " + (filteredCountLabel != null ? "OK" : "NULL"));
        System.out.println("   - userSearchField: " + (userSearchField != null ? "OK" : "NULL"));
        System.out.println("   - userStatusFilter: " + (userStatusFilter != null ? "OK" : "NULL"));
        System.out.println("   - userSortFilter: " + (userSortFilter != null ? "OK" : "NULL"));

        // Initialize UserDAO
        System.out.println("🔍 [DEBUG] Initializing UserDAO...");
        try {
            userDAO = new UserDAO();
            System.out.println("✅ [DEBUG] UserDAO initialized successfully");
        } catch (SQLException e) {
            System.err.println("❌ [DEBUG] Failed to initialize UserDAO: " + e.getMessage());
            e.printStackTrace();
        }

        try {
            serverMessageDAO = new ServerMessageDAO();
            System.out.println("✅ ServerMessageDAO initialized");
        } catch (Exception e) {
            System.err.println("❌ Failed to initialize ServerMessageDAO: " + e.getMessage());
        }


        // Setup components
        System.out.println("🔍 [DEBUG] Setting up UI components...");
        setupConsoleRedirect();
        roomManager = GameRoomManager.getInstance();
        setupUserTable();
        setupUserFilters();
        startUpdateTimer();
        updateUIState(false);
        setupServerChat();

        logToConsole("🎮 Server UI Ready");
        logToConsole("💡 Click 'Start' to begin server");

        // Load users immediately
        System.out.println("🔍 [DEBUG] Loading users...");
        loadUsers();

        System.out.println("🔍 [DEBUG] ========== Initialize Complete ==========");
    }



    public void setGameServer(GameServer server) {
        this.gameServer = server;
        if (server != null && server.isRunning()) {
            startTime = System.currentTimeMillis();
            updateUIState(true);
            logToConsole("✅ Connected to running server on port " + server.getPort());
        }
    }

    // ==================== SERVER CONTROL ====================

    @FXML
    private void handleStart() {
        if (gameServer != null && gameServer.isRunning()) {
            showAlert("Warning", "Server is already running!", Alert.AlertType.WARNING);
            return;
        }

        startButton.setDisable(true);
        logToConsole("🚀 Starting server...");

        try {
            gameServer = new GameServer(Protocol.DEFAULT_PORT);

            serverThread = new Thread(() -> {
                try {
                    gameServer.start();
                } catch (Exception e) {
                    Platform.runLater(() -> {
                        logToConsole("❌ Error: " + e.getMessage());
                        handleStop();
                    });
                }
            }, "GameServer-Thread");

            serverThread.setDaemon(true);
            serverThread.start();

            Thread.sleep(1000);

            if (gameServer.isRunning()) {
                Platform.runLater(() -> {
                    startTime = System.currentTimeMillis();
                    updateUIState(true);
                    logToConsole("✅ Server started successfully on port " + Protocol.DEFAULT_PORT);
                    showNotification("Server started!", "success");
                });
            } else {
                Platform.runLater(() -> {
                    logToConsole("❌ Failed to start server");
                    startButton.setDisable(false);
                });
            }

        } catch (Exception e) {
            logToConsole("❌ Failed to start: " + e.getMessage());
            e.printStackTrace();
            startButton.setDisable(false);
        }
    }

    @FXML
    private void handleStop() {
        if (gameServer == null || !gameServer.isRunning()) {
            showAlert("Warning", "Server is not running!", Alert.AlertType.WARNING);
            return;
        }

        stopButton.setDisable(true);
        logToConsole("🛑 Stopping server...");

        new Thread(() -> {
            try {
                logToConsole("   ⏳ Closing all client connections...");
                gameServer.stop();

                if (serverThread != null && serverThread.isAlive()) {
                    logToConsole("   ⏳ Waiting for server thread...");
                    serverThread.interrupt();
                    serverThread.join(5000);

                    if (serverThread.isAlive()) {
                        logToConsole("   ⚠️ Server thread did not terminate gracefully");
                    }
                }

                Platform.runLater(() -> {
                    updateUIState(false);
                    stopButton.setDisable(false);
                    logToConsole("✅ Server stopped completely");
                    showNotification("Server stopped", "info");
                });

            } catch (Exception e) {
                Platform.runLater(() -> {
                    logToConsole("❌ Error stopping server: " + e.getMessage());
                    e.printStackTrace();
                    stopButton.setDisable(false);
                });
            }
        }, "ServerStop-Thread").start();
    }

    @FXML
    private void handleOpenQuestionBank() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/QuestionManagerView.fxml"));
            Parent root = loader.load();

            // Load CSS
            try {
                String css = getClass().getResource("/css/question-manager.css").toExternalForm();
                root.getStylesheets().add(css);
            } catch (Exception cssError) {
                logToConsole("⚠️ Could not load CSS: " + cssError.getMessage());
            }

            // Tạo cửa sổ
            Stage stage = new Stage();
            stage.setTitle("Question Bank Manager");
            stage.setScene(new Scene(root, 1400, 800));

            // 🔥 Set icon nhỏ cho cửa sổ
            try {
                stage.getIcons().add(new Image(
                        getClass().getResourceAsStream("/images/avatars/icon-question.png"),
                        16, 16, true, true
                ));
            } catch (Exception e) {
                System.out.println("⚠️ Icon not found");
            }

            stage.initModality(Modality.NONE);
            stage.show();

            logToConsole("📝 Question Bank Manager opened");

        } catch (IOException e) {
            logToConsole("❌ Failed to open Question Bank: " + e.getMessage());
            e.printStackTrace();
            showAlert("Error", "Could not open Question Bank Manager", Alert.AlertType.ERROR);
        }
    }


    // ==================== USER MANAGEMENT ====================

    private void setupUserTable() {
        System.out.println("🔍 [DEBUG] Setting up user table...");

        // CLEAR existing columns first
        userTable.getColumns().clear();

        // ==================== ID COLUMN ====================
        TableColumn<UserTableRow, Integer> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(cellData -> cellData.getValue().userIdProperty().asObject());
        idCol.setPrefWidth(60);
        idCol.setStyle("-fx-alignment: CENTER;");

        // ==================== USERNAME COLUMN ====================
        TableColumn<UserTableRow, String> usernameCol = new TableColumn<>("Username");
        usernameCol.setCellValueFactory(cellData -> cellData.getValue().usernameProperty());
        usernameCol.setPrefWidth(150);

        // ==================== FULL NAME COLUMN ====================
        TableColumn<UserTableRow, String> fullNameCol = new TableColumn<>("Full Name");
        fullNameCol.setCellValueFactory(cellData -> cellData.getValue().fullNameProperty());
        fullNameCol.setPrefWidth(180);

        // ==================== EMAIL COLUMN ====================
        TableColumn<UserTableRow, String> emailCol = new TableColumn<>("Email");
        emailCol.setCellValueFactory(cellData -> cellData.getValue().emailProperty());
        emailCol.setPrefWidth(200);

        // ==================== STATUS COLUMN ====================
        TableColumn<UserTableRow, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(cellData -> cellData.getValue().statusTextProperty());
        statusCol.setPrefWidth(80);
        statusCol.setStyle("-fx-alignment: CENTER;");
        statusCol.setCellFactory(col -> new TableCell<UserTableRow, String>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(status);
                    if (status.equals("Online")) {
                        setStyle("-fx-text-fill: #4ade80; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #94a3b8;");
                    }
                }
            }
        });

        // ==================== TOTAL SCORE COLUMN ====================
        TableColumn<UserTableRow, Integer> scoreCol = new TableColumn<>("Total Score");
        scoreCol.setCellValueFactory(cellData -> cellData.getValue().totalScoreProperty().asObject());
        scoreCol.setPrefWidth(100);
        scoreCol.setStyle("-fx-alignment: CENTER;");

        // ==================== GAMES COLUMN ====================
        TableColumn<UserTableRow, Integer> gamesCol = new TableColumn<>("Games");
        gamesCol.setCellValueFactory(cellData -> cellData.getValue().totalGamesProperty().asObject());
        gamesCol.setPrefWidth(80);
        gamesCol.setStyle("-fx-alignment: CENTER;");

        // ==================== WINS COLUMN ====================
        TableColumn<UserTableRow, Integer> winsCol = new TableColumn<>("Wins");
        winsCol.setCellValueFactory(cellData -> cellData.getValue().winsProperty().asObject());
        winsCol.setPrefWidth(80);
        winsCol.setStyle("-fx-alignment: CENTER;");

        // ==================== WIN RATE COLUMN ====================
        TableColumn<UserTableRow, String> winRateCol = new TableColumn<>("Win Rate");
        winRateCol.setCellValueFactory(cellData -> cellData.getValue().winRateTextProperty());
        winRateCol.setPrefWidth(90);
        winRateCol.setStyle("-fx-alignment: CENTER;");

        // ==================== ACTIONS COLUMN ====================
        TableColumn<UserTableRow, Void> actionsCol = new TableColumn<>("Actions");
        actionsCol.setPrefWidth(200);
        actionsCol.setCellFactory(col -> new TableCell<UserTableRow, Void>() {
            private final Button viewBtn = new Button("👁️ View");
            private final Button editBtn = new Button("✏️ Edit");
            private final Button deleteBtn = new Button("🗑️ Delete");

            {
                viewBtn.getStyleClass().add("btn-secondary");
                editBtn.getStyleClass().add("btn-primary");
                deleteBtn.getStyleClass().add("btn-danger");

                viewBtn.setOnAction(e -> {
                    UserTableRow user = getTableView().getItems().get(getIndex());
                    handleViewUser(user);
                });

                editBtn.setOnAction(e -> {
                    UserTableRow user = getTableView().getItems().get(getIndex());
                    handleEditUser(user);
                });

                deleteBtn.setOnAction(e -> {
                    UserTableRow user = getTableView().getItems().get(getIndex());
                    handleDeleteUser(user);
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    HBox buttons = new HBox(5, viewBtn, editBtn, deleteBtn);
                    buttons.setAlignment(Pos.CENTER);
                    setGraphic(buttons);
                }
            }
        });

        // ADD ALL COLUMNS
        userTable.getColumns().addAll(
                idCol, usernameCol, fullNameCol, emailCol,
                statusCol, scoreCol, gamesCol, winsCol,
                winRateCol, actionsCol
        );

        System.out.println("✅ [DEBUG] Added " + userTable.getColumns().size() + " columns");

        // BIND TO filteredUsers ObservableList
        userTable.setItems(filteredUsers);
        System.out.println("✅ [DEBUG] Table bound to filteredUsers (size: " + filteredUsers.size() + ")");

        // Enable selection
        userTable.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);

        // IMPORTANT: Set placeholder for empty table
        Label placeholder = new Label("No users found");
        placeholder.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 14px;");
        userTable.setPlaceholder(placeholder);

        System.out.println("✅ [DEBUG] User table setup complete");
    }

    private void setupUserFilters() {
        userStatusFilter.setItems(FXCollections.observableArrayList("All", "Online", "Offline"));
        userStatusFilter.setValue("All");

        userSortFilter.setItems(FXCollections.observableArrayList(
                "Total Score", "Username (A-Z)", "Registration Date", "Total Games"
        ));
        userSortFilter.setValue("Total Score");
    }

    @FXML
    private void handleRefreshUsers() {
        loadUsers();
        showNotification("Users refreshed", "info");
    }

    @FXML
    private void handleSearchUsers() {
        String searchText = userSearchField.getText().toLowerCase().trim();

        if (searchText.isEmpty()) {
            filteredUsers.setAll(allUsers);
        } else {
            List<UserTableRow> results = allUsers.stream()
                    .filter(user ->
                            user.getUsername().toLowerCase().contains(searchText) ||
                                    user.getFullName().toLowerCase().contains(searchText) ||
                                    user.getEmail().toLowerCase().contains(searchText)
                    )
                    .collect(Collectors.toList());
            filteredUsers.setAll(results);
        }

        filteredCountLabel.setText("Showing: " + filteredUsers.size() + " users");
        logToConsole("🔍 Search results: " + filteredUsers.size() + " users");
    }

    @FXML
    private void handleFilterUsers() {
        System.out.println("🔍 [DEBUG] Starting handleFilterUsers()...");
        System.out.println("   - allUsers size: " + allUsers.size());

        List<UserTableRow> filtered = new ArrayList<>(allUsers);
        System.out.println("   - Initial filtered size: " + filtered.size());

        // Filter by status
        String statusFilter = userStatusFilter.getValue();
        System.out.println("   - Status filter: " + statusFilter);

        if (statusFilter != null && !statusFilter.equals("All")) {
            filtered = filtered.stream()
                    .filter(user -> user.getStatusText().equals(statusFilter))
                    .collect(Collectors.toList());
            System.out.println("   - After status filter: " + filtered.size());
        }

        // Sort
        String sortBy = userSortFilter.getValue();
        System.out.println("   - Sort by: " + sortBy);

        if (sortBy != null) {
            switch (sortBy) {
                case "Total Score":
                    filtered.sort((a, b) -> Integer.compare(b.getTotalScore(), a.getTotalScore()));
                    System.out.println("   - Sorted by Total Score");
                    break;
                case "Username (A-Z)":
                    filtered.sort((a, b) -> a.getUsername().compareToIgnoreCase(b.getUsername()));
                    System.out.println("   - Sorted by Username");
                    break;
                case "Total Games":
                    filtered.sort((a, b) -> Integer.compare(b.getTotalGames(), a.getTotalGames()));
                    System.out.println("   - Sorted by Total Games");
                    break;
            }
        }

        System.out.println("   - Final filtered size: " + filtered.size());

        // CRITICAL: Update the ObservableList
        filteredUsers.clear();
        filteredUsers.addAll(filtered);
        System.out.println("   - filteredUsers updated: " + filteredUsers.size());

        // Force table refresh
        userTable.refresh();
        System.out.println("   - Table refreshed");

        // Check table items
        System.out.println("   - Table items count: " + userTable.getItems().size());

        filteredCountLabel.setText("Showing: " + filteredUsers.size() + " users");

        System.out.println("✅ [DEBUG] Filter complete - Table should show " + filteredUsers.size() + " users");

        // Print first 3 users for verification
        for (int i = 0; i < Math.min(3, filteredUsers.size()); i++) {
            UserTableRow u = filteredUsers.get(i);
            System.out.println("      [" + (i+1) + "] " + u.getUsername() + " - " + u.getEmail());
        }
    }

    @FXML
    private void handleAddUser() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Add New User");
        dialog.setHeaderText("Enter new user information");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        TextField usernameField = new TextField();
        usernameField.setPromptText("Username");

        TextField fullNameField = new TextField();
        fullNameField.setPromptText("Full Name");

        TextField emailField = new TextField();
        emailField.setPromptText("email@example.com");

        TextField ageField = new TextField();
        ageField.setPromptText("Age");

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("Password");

        PasswordField confirmField = new PasswordField();
        confirmField.setPromptText("Confirm Password");

        grid.add(new Label("Username:*"), 0, 0);
        grid.add(usernameField, 1, 0);

        grid.add(new Label("Full Name:*"), 0, 1);
        grid.add(fullNameField, 1, 1);

        grid.add(new Label("Email:*"), 0, 2);
        grid.add(emailField, 1, 2);

        grid.add(new Label("Age:"), 0, 3);
        grid.add(ageField, 1, 3);

        grid.add(new Label("Password:*"), 0, 4);
        grid.add(passwordField, 1, 4);

        grid.add(new Label("Confirm:*"), 0, 5);
        grid.add(confirmField, 1, 5);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Validation
        Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            String username = usernameField.getText().trim();
            String fullName = fullNameField.getText().trim();
            String email = emailField.getText().trim();
            String age = ageField.getText().trim();
            String password = passwordField.getText();
            String confirm = confirmField.getText();

            // Validate required fields
            if (username.isEmpty() || fullName.isEmpty() || email.isEmpty() || password.isEmpty()) {
                showAlert("Validation Error", "Please fill in all required fields (*)", Alert.AlertType.ERROR);
                event.consume();
                return;
            }

            // Validate username length
            if (username.length() < 3 || username.length() > 20) {
                showAlert("Validation Error", "Username must be 3-20 characters", Alert.AlertType.ERROR);
                event.consume();
                return;
            }

            // Validate email format
            if (!email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
                showAlert("Validation Error", "Invalid email format", Alert.AlertType.ERROR);
                event.consume();
                return;
            }

            // Validate password length
            if (password.length() < 6) {
                showAlert("Validation Error", "Password must be at least 6 characters", Alert.AlertType.ERROR);
                event.consume();
                return;
            }

            // Validate password match
            if (!password.equals(confirm)) {
                showAlert("Validation Error", "Passwords do not match!", Alert.AlertType.ERROR);
                event.consume();
                return;
            }

            // Check username exists
            if (userDAO.usernameExists(username)) {
                showAlert("Validation Error", "Username already exists!", Alert.AlertType.ERROR);
                event.consume();
                return;
            }

            // Check email exists
            if (userDAO.emailExists(email)) {
                showAlert("Validation Error", "Email already exists!", Alert.AlertType.ERROR);
                event.consume();
                return;
            }

            // Register user
            boolean success = userDAO.registerUser(
                    username,
                    password,
                    email,
                    fullName,
                    age.isEmpty() ? "0" : age,
                    "default_avatar.png"
            );

            if (success) {
                logToConsole("✅ User added: " + username);
                loadUsers();
                showNotification("User added successfully!", "success");
            } else {
                showAlert("Error", "Failed to add user. Please try again.", Alert.AlertType.ERROR);
                event.consume();
            }
        });

        dialog.showAndWait();
    }

    private void handleViewUser(UserTableRow user) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("User Details");
        alert.setHeaderText("👤 User: " + user.getUsername());

        // Format win rate
        String winRate = user.getTotalGames() > 0 ?
                String.format("%.1f%%", (user.getWins() * 100.0) / user.getTotalGames()) : "0.0%";

        String details = String.format(
                "═══════════════════════════════════════\n" +
                        "📋 BASIC INFORMATION\n" +
                        "═══════════════════════════════════════\n" +
                        "User ID: %d\n" +
                        "Username: %s\n" +
                        "Full Name: %s\n" +
                        "Email: %s\n" +
                        "Status: %s\n\n" +
                        "═══════════════════════════════════════\n" +
                        "📊 GAME STATISTICS\n" +
                        "═══════════════════════════════════════\n" +
                        "Total Score: %d points\n" +
                        "Total Games Played: %d\n" +
                        "Wins: %d\n" +
                        "Losses: %d\n" +
                        "Win Rate: %s\n\n" +
                        "═══════════════════════════════════════\n" +
                        "📚 SUBJECT SCORES\n" +
                        "═══════════════════════════════════════\n" +
                        "🔢 Math: %d points\n" +
                        "🔤 English: %d points\n" +
                        "📖 Literature: %d points\n",
                user.getUserId(),
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getStatusText(),
                user.getTotalScore(),
                user.getTotalGames(),
                user.getWins(),
                user.getTotalGames() - user.getWins(),
                winRate,
                user.getMathScore(),
                user.getEnglishScore(),
                user.getLiteratureScore()
        );

        TextArea textArea = new TextArea(details);
        textArea.setEditable(false);
        textArea.setWrapText(true);
        textArea.setFont(javafx.scene.text.Font.font("Consolas", 12));
        textArea.setPrefRowCount(20);

        alert.getDialogPane().setContent(textArea);

        // Add buttons for additional actions
        ButtonType resetPasswordBtn = new ButtonType("🔑 Reset Password");
        ButtonType closeBtn = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
        alert.getButtonTypes().setAll(resetPasswordBtn, closeBtn);

        Optional<ButtonType> result = alert.showAndWait();

        if (result.isPresent() && result.get() == resetPasswordBtn) {
            handleResetPassword(user);
        }

        logToConsole("👁️ Viewed user details: " + user.getUsername());
    }

    private void handleEditUser(UserTableRow user) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit User");
        dialog.setHeaderText("Edit information for: " + user.getUsername());

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        TextField usernameField = new TextField(user.getUsername());
        TextField fullNameField = new TextField(user.getFullName());
        TextField emailField = new TextField(user.getEmail());

        // Add status info
        Label statusInfo = new Label("Status: " + user.getStatusText());
        statusInfo.setStyle(user.getStatusText().equals("Online") ?
                "-fx-text-fill: #4ade80;" : "-fx-text-fill: #94a3b8;");

        grid.add(new Label("Username:*"), 0, 0);
        grid.add(usernameField, 1, 0);

        grid.add(new Label("Full Name:*"), 0, 1);
        grid.add(fullNameField, 1, 1);

        grid.add(new Label("Email:*"), 0, 2);
        grid.add(emailField, 1, 2);

        grid.add(statusInfo, 1, 3);

        // Disable username if user is online
        if (user.getStatusText().equals("Online")) {
            usernameField.setDisable(true);
            Label warning = new Label("⚠️ Cannot change username while user is online");
            warning.setStyle("-fx-text-fill: #f59e0b;");
            grid.add(warning, 0, 4, 2, 1);
        }

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Validation
        Button okButton = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        okButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            String username = usernameField.getText().trim();
            String fullName = fullNameField.getText().trim();
            String email = emailField.getText().trim();

            // Validate required fields
            if (username.isEmpty() || fullName.isEmpty() || email.isEmpty()) {
                showAlert("Validation Error", "Please fill in all required fields", Alert.AlertType.ERROR);
                event.consume();
                return;
            }

            // Validate email format
            if (!email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
                showAlert("Validation Error", "Invalid email format", Alert.AlertType.ERROR);
                event.consume();
                return;
            }

            // Check username exists (except current user)
            if (!username.equals(user.getUsername()) && userDAO.usernameExists(username)) {
                showAlert("Validation Error", "Username already exists!", Alert.AlertType.ERROR);
                event.consume();
                return;
            }

            // Check email exists (except current user)
            if (!email.equals(user.getEmail()) && userDAO.emailExists(email)) {
                showAlert("Validation Error", "Email already exists!", Alert.AlertType.ERROR);
                event.consume();
                return;
            }

            // Update user
            boolean success = userDAO.updateUser(user.getUserId(), username, fullName, email);

            if (success) {
                logToConsole("✅ User updated: " + username);
                loadUsers();
                showNotification("User updated successfully!", "success");
            } else {
                showAlert("Error", "Failed to update user. Please try again.", Alert.AlertType.ERROR);
                event.consume();
            }
        });

        dialog.showAndWait();
    }

    private void handleResetPassword(UserTableRow user) {

    }

    private void handleDeleteUser(UserTableRow user) {
        // Check if user is online
        if (user.getStatusText().equals("Online")) {
            showAlert("Cannot Delete",
                    "Cannot delete user '" + user.getUsername() + "' because they are currently online!",
                    Alert.AlertType.WARNING);
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Delete");
        confirm.setHeaderText("Delete User: " + user.getUsername());

        String details = String.format(
                "Are you sure you want to delete this user?\n\n" +
                        "User: %s\n" +
                        "Full Name: %s\n" +
                        "Email: %s\n" +
                        "Total Score: %d\n" +
                        "Total Games: %d\n\n" +
                        "⚠️ This action cannot be undone!",
                user.getUsername(),
                user.getFullName(),
                user.getEmail(),
                user.getTotalScore(),
                user.getTotalGames()
        );

        confirm.setContentText(details);

        Optional<ButtonType> result = confirm.showAndWait();

        if (result.isPresent() && result.get() == ButtonType.OK) {
            boolean success = userDAO.deleteUser(user.getUserId());

            if (success) {
                logToConsole("🗑️ User deleted: " + user.getUsername() + " (ID: " + user.getUserId() + ")");
                loadUsers();
                showNotification("User deleted successfully!", "danger");
            } else {
                showAlert("Error",
                        "Failed to delete user. User may have game records that cannot be removed.",
                        Alert.AlertType.ERROR);
            }
        }
    }

    private void showUserDialog(UserTableRow user, String title) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(user == null ? "Enter new user details" : "Edit user details");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));

        TextField usernameField = new TextField(user != null ? user.getUsername() : "");
        TextField fullNameField = new TextField(user != null ? user.getFullName() : "");
        TextField emailField = new TextField(user != null ? user.getEmail() : "");
        PasswordField passwordField = new PasswordField();

        grid.add(new Label("Username:"), 0, 0);
        grid.add(usernameField, 1, 0);
        grid.add(new Label("Full Name:"), 0, 1);
        grid.add(fullNameField, 1, 1);
        grid.add(new Label("Email:"), 0, 2);
        grid.add(emailField, 1, 2);

        if (user == null) {
            grid.add(new Label("Password:"), 0, 3);
            grid.add(passwordField, 1, 3);
        }

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            // TODO: Implement save logic
            logToConsole((user == null ? "➕ Added" : "✏️ Edited") + " user: " + usernameField.getText());
            loadUsers();
            showNotification("User " + (user == null ? "added" : "updated"), "success");
        }
    }

    private void loadUsers() {
        System.out.println("🔍 [DEBUG] Starting loadUsers()...");

        try {
            // Check if userDAO is initialized
            if (userDAO == null) {
                System.err.println("❌ [DEBUG] userDAO is NULL!");
                logToConsole("❌ UserDAO not initialized!");
                showAlert("Error", "Database connection not available", Alert.AlertType.ERROR);
                return;
            }

            System.out.println("✅ [DEBUG] userDAO is available");

            // Fetch players from database
            List<UserDAO.PlayerInfo> players = userDAO.getAllPlayersForLeaderboard();

            System.out.println("🔍 [DEBUG] Fetched " + (players != null ? players.size() : "NULL") + " players from DB");

            if (players == null || players.isEmpty()) {
                System.out.println("⚠️ [DEBUG] No players found in database");
                logToConsole("⚠️ No users found in database");
                totalUsersLabel.setText("Total: 0 users");
                filteredCountLabel.setText("Showing: 0 users");
                return;
            }

            allUsers.clear();

            // Get list of online user IDs from server
            Set<Integer> onlineUserIds = getOnlineUserIds();
            System.out.println("🔍 [DEBUG] Online user IDs: " + onlineUserIds);

            for (UserDAO.PlayerInfo player : players) {
                System.out.println("🔍 [DEBUG] Processing player: " + player.username + " (ID: " + player.userId + ")");

                UserTableRow row = new UserTableRow(player);

                // Set online status based on connected clients
                if (onlineUserIds.contains(player.userId)) {
                    row.setStatusText("Online");
                    System.out.println("   ✅ Status: Online");
                } else {
                    row.setStatusText("Offline");
                    System.out.println("   ⚪ Status: Offline");
                }

                // Verify data
                System.out.println("   📊 Data check: " +
                        "Username=" + row.getUsername() +
                        ", Email=" + row.getEmail() +
                        ", Score=" + row.getTotalScore());

                allUsers.add(row);
            }

            System.out.println("✅ [DEBUG] Total users loaded into allUsers: " + allUsers.size());
            System.out.println("🔍 [DEBUG] First user in allUsers: " + (allUsers.isEmpty() ? "EMPTY" : allUsers.get(0).toString()));

            totalUsersLabel.setText("Total: " + allUsers.size() + " users");

            // Apply filters
            handleFilterUsers();

            logToConsole("✅ Loaded " + allUsers.size() + " users (" +
                    onlineUserIds.size() + " online)");

        } catch (Exception e) {
            System.err.println("❌ [DEBUG] Exception in loadUsers(): " + e.getMessage());
            e.printStackTrace();
            logToConsole("❌ Failed to load users: " + e.getMessage());
            showAlert("Error", "Failed to load users from database\n" + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    /**
     * Get set of online user IDs from connected clients
     */
    private Set<Integer> getOnlineUserIds() {
        Set<Integer> onlineIds = new HashSet<>();

        if (gameServer != null && gameServer.isRunning()) {
            try {
                // Get connected clients from server
                List<ClientHandler> clients = gameServer.getConnectedClients();

                if (clients != null) {
                    for (ClientHandler client : clients) {
                        if (client != null && client.getCurrentUser() != null) {
                            onlineIds.add(client.getCurrentUser().getUserId());
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("⚠️ Error getting online users: " + e.getMessage());
            }
        }

        return onlineIds;
    }


    /**
     * Kiểm tra xem một user có đang online không
     */
    private boolean isUserOnline(int userId) {
        if (gameServer == null || !gameServer.isRunning()) {
            return false;
        }

        try {
            List<ClientHandler> clients = gameServer.getConnectedClients();
            if (clients != null) {
                for (ClientHandler client : clients) {
                    if (client != null && client.getCurrentUser() != null &&
                            client.getCurrentUser().getUserId() == userId) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("⚠️ Error checking user online status: " + e.getMessage());
        }

        return false;
    }
// ==================== Chat MANAGEMENT ====================
    /**
     * Setup Server Chat tab with recipient table and message composer
     */
    private void setupServerChat() {
        System.out.println("🔍 [DEBUG] Setting up Server Chat tab...");

        if (recipientTable == null) {
            System.err.println("❌ recipientTable is NULL!");
            return;
        }

        // Setup recipient table columns
        setupRecipientTable();

        // Setup search and filter listeners
        setupChatFilters();

        // Load initial recipients
        loadRecipients();

        // Setup character counter for message input
        setupCharacterCounter();

        // Load recent message history
        loadMessageHistory();

        System.out.println("✅ Server Chat setup complete");
    }

    /**
     * Setup recipient selection table with multi-select
     */
    private void setupRecipientTable() {
        System.out.println("🔍 Setting up recipient table...");

        // Clear existing columns
        recipientTable.getColumns().clear();

        // Enable multi-selection
        recipientTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        // Selection change listener
        recipientTable.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            updateSelectedUsers();
            updateSelectedUsersCount();
        });

        // ==================== SELECT COLUMN (Checkbox) ====================
        TableColumn<UserTableRow, Boolean> selectCol = new TableColumn<>("✓");
        selectCol.setPrefWidth(40);
        selectCol.setStyle("-fx-alignment: CENTER;");
        selectCol.setCellFactory(col -> new TableCell<UserTableRow, Boolean>() {
            private final CheckBox checkBox = new CheckBox();

            {
                checkBox.setOnAction(e -> {
                    UserTableRow user = getTableView().getItems().get(getIndex());
                    if (checkBox.isSelected()) {
                        recipientTable.getSelectionModel().select(user);
                    } else {
                        recipientTable.getSelectionModel().clearSelection(getIndex());
                    }
                });
            }

            @Override
            protected void updateItem(Boolean item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    checkBox.setSelected(recipientTable.getSelectionModel().isSelected(getIndex()));
                    setGraphic(checkBox);
                }
            }
        });

        // ==================== USERNAME COLUMN ====================
        TableColumn<UserTableRow, String> usernameCol = new TableColumn<>("Username");
        usernameCol.setCellValueFactory(cellData -> cellData.getValue().usernameProperty());
        usernameCol.setPrefWidth(120);

        // ==================== FULL NAME COLUMN ====================
        TableColumn<UserTableRow, String> fullNameCol = new TableColumn<>("Full Name");
        fullNameCol.setCellValueFactory(cellData -> cellData.getValue().fullNameProperty());
        fullNameCol.setPrefWidth(150);

        // ==================== STATUS COLUMN ====================
        TableColumn<UserTableRow, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(cellData -> cellData.getValue().statusTextProperty());
        statusCol.setPrefWidth(80);
        statusCol.setStyle("-fx-alignment: CENTER;");
        statusCol.setCellFactory(col -> new TableCell<UserTableRow, String>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(status);
                    if (status.equals("Online")) {
                        setStyle("-fx-text-fill: #4ade80; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #94a3b8;");
                    }
                }
            }
        });

        // Add all columns
        recipientTable.getColumns().addAll(selectCol, usernameCol, fullNameCol, statusCol);

        // Bind to filtered users
        recipientTable.setItems(filteredUsers);

        // Placeholder
        Label placeholder = new Label("No users available");
        placeholder.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 14px;");
        recipientTable.setPlaceholder(placeholder);

        System.out.println("✅ Recipient table setup complete");
    }

    /**
     * Setup search and filter listeners for recipient selection
     */
    private void setupChatFilters() {
        if (recipientSearchField != null) {
            recipientSearchField.textProperty().addListener((obs, oldVal, newVal) -> {
                filterRecipients();
            });
        }

        if (recipientStatusFilter != null) {
            recipientStatusFilter.setItems(FXCollections.observableArrayList("All", "Online", "Offline"));
            recipientStatusFilter.setValue("All");
        }
    }

    /**
     * Setup character counter for message input
     */
    private void setupCharacterCounter() {
        if (serverMessageInput != null && charCountLabel != null) {
            serverMessageInput.textProperty().addListener((obs, oldVal, newVal) -> {
                int length = newVal.length();
                charCountLabel.setText(length + " / 500 characters");

                // Change color based on length
                if (length > 450) {
                    charCountLabel.setStyle("-fx-text-fill: #ef4444;");
                } else if (length > 350) {
                    charCountLabel.setStyle("-fx-text-fill: #f59e0b;");
                } else {
                    charCountLabel.setStyle("-fx-text-fill: #64748b;");
                }

                // Limit to 500 characters
                if (length > 500) {
                    serverMessageInput.setText(oldVal);
                }
            });
        }
    }

    /**
     * Load recipients (same as user list)
     */
    private void loadRecipients() {
        System.out.println("🔍 Loading recipients...");
        loadUsers(); // Reuse the existing loadUsers method
    }

    /**
     * Update selected users from table
     */
    private void updateSelectedUsers() {
        selectedUserIds.clear();

        if (recipientTable != null) {
            for (UserTableRow user : recipientTable.getSelectionModel().getSelectedItems()) {
                selectedUserIds.add(user.getUserId());
            }
        }
    }

    /**
     * Update selected users count label
     */
    private void updateSelectedUsersCount() {
        if (selectedUsersCountLabel != null) {
            selectedUsersCountLabel.setText(selectedUserIds.size() + " users");
        }
    }

    /**
     * Filter recipients based on search and status
     */
    @FXML
    private void handleFilterRecipients() {
        filterRecipients();
    }

    /**
     * Filter recipients implementation
     */
    private void filterRecipients() {
        String searchText = recipientSearchField != null ? recipientSearchField.getText().toLowerCase().trim() : "";
        String statusFilter = recipientStatusFilter != null ? recipientStatusFilter.getValue() : "All";

        List<UserTableRow> filtered = new ArrayList<>(allUsers);

        // Apply search filter
        if (!searchText.isEmpty()) {
            filtered = filtered.stream()
                    .filter(user ->
                            user.getUsername().toLowerCase().contains(searchText) ||
                                    user.getFullName().toLowerCase().contains(searchText))
                    .collect(Collectors.toList());
        }

        // Apply status filter
        if (statusFilter != null && !statusFilter.equals("All")) {
            filtered = filtered.stream()
                    .filter(user -> user.getStatusText().equals(statusFilter))
                    .collect(Collectors.toList());
        }

        // Update filtered list
        filteredUsers.clear();
        filteredUsers.addAll(filtered);

        recipientTable.refresh();
    }

    /**
     * Refresh recipients list
     */
    @FXML
    private void handleRefreshRecipients() {
        loadRecipients();
        showNotification("Recipients list refreshed", "info");
    }

    /**
     * Clear selection
     */
    @FXML
    private void handleClearSelection() {
        if (recipientTable != null) {
            recipientTable.getSelectionModel().clearSelection();
        }
        selectedUserIds.clear();
        updateSelectedUsersCount();
        showNotification("Selection cleared", "info");
    }

    /**
     * Clear message input
     */
    @FXML
    private void handleClearMessageInput() {
        if (serverMessageInput != null) {
            serverMessageInput.clear();
        }
        if (importantCheckBox != null) {
            importantCheckBox.setSelected(false);
        }
    }

// ==================== MESSAGE SENDING HANDLERS ====================

    /**
     * Handler: Send broadcast message (all users)
     */
    @FXML
    private void handleSendBroadcast() {
        String content = serverMessageInput.getText().trim();

        if (content.isEmpty()) {
            showAlert("Error", "Please enter a message!", Alert.AlertType.WARNING);
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Broadcast");
        confirm.setHeaderText("Send broadcast to ALL users?");
        confirm.setContentText("Message: " + (content.length() > 50 ? content.substring(0, 50) + "..." : content));

        Optional<ButtonType> result = confirm.showAndWait();

        if (result.isPresent() && result.get() == ButtonType.OK) {
            boolean isImportant = importantCheckBox != null && importantCheckBox.isSelected();

            // Save to database
            ServerMessage msg = serverMessageDAO.sendBroadcast("Admin", content, isImportant);

            if (msg != null) {
                logToConsole("📢 Broadcast sent: " + content);

                // Broadcast to all online clients
                Map<String, Object> notification = new HashMap<>();
                notification.put("type", Protocol.NEW_SERVER_MESSAGE);
                notification.put("messageId", msg.getMessageId());
                notification.put("messageType", "broadcast");
                notification.put("senderName", msg.getSenderName());
                notification.put("content", msg.getContent());
                notification.put("sentAt", msg.getSentAt().toString());
                notification.put("isImportant", msg.isImportant());

                if (gameServer != null) {
                    gameServer.broadcastToAll(notification);
                }

                serverMessageInput.clear();
                if (importantCheckBox != null) importantCheckBox.setSelected(false);

                loadMessageHistory(); // Refresh history
                showNotification("Broadcast sent to all users!", "success");
            } else {
                showAlert("Error", "Failed to send broadcast!", Alert.AlertType.ERROR);
            }
        }
    }

    /**
     * Handler: Send group message (selected users)
     */
    @FXML
    private void handleSendGroup() {
        String content = serverMessageInput.getText().trim();

        if (content.isEmpty()) {
            showAlert("Error", "Please enter a message!", Alert.AlertType.WARNING);
            return;
        }

        if (selectedUserIds.isEmpty()) {
            showAlert("Error", "Please select at least 1 recipient!", Alert.AlertType.WARNING);
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Group Message");
        confirm.setHeaderText("Send to " + selectedUserIds.size() + " selected users?");
        confirm.setContentText("Message: " + (content.length() > 50 ? content.substring(0, 50) + "..." : content));

        Optional<ButtonType> result = confirm.showAndWait();

        if (result.isPresent() && result.get() == ButtonType.OK) {
            boolean isImportant = importantCheckBox != null && importantCheckBox.isSelected();

            // Save to database
            List<Integer> recipients = new ArrayList<>(selectedUserIds);
            ServerMessage msg = serverMessageDAO.sendGroupMessage("Admin", content, recipients, isImportant);

            if (msg != null) {
                logToConsole("📬 Group message sent to " + recipients.size() + " users");

                // Send to online recipients
                Map<String, Object> notification = new HashMap<>();
                notification.put("type", Protocol.NEW_SERVER_MESSAGE);
                notification.put("messageId", msg.getMessageId());
                notification.put("messageType", "group");
                notification.put("senderName", msg.getSenderName());
                notification.put("content", msg.getContent());
                notification.put("sentAt", msg.getSentAt().toString());
                notification.put("isImportant", msg.isImportant());

                int sentCount = 0;
                if (gameServer != null) {
                    for (int userId : recipients) {
                        boolean sent = gameServer.sendToUserId(userId, notification);
                        if (sent) sentCount++;
                    }
                }

                logToConsole("   → Sent to " + sentCount + "/" + recipients.size() + " online users");

                serverMessageInput.clear();
                if (importantCheckBox != null) importantCheckBox.setSelected(false);
                selectedUserIds.clear();
                if (recipientTable != null) recipientTable.getSelectionModel().clearSelection();
                updateSelectedUsersCount();

                loadMessageHistory(); // Refresh history
                showNotification("Sent to " + recipients.size() + " users (" + sentCount + " online)!", "success");
            } else {
                showAlert("Error", "Failed to send group message!", Alert.AlertType.ERROR);
            }
        }
    }

    /**
     * Handler: Send private message (1 user only)
     */
    @FXML
    private void handleSendPrivate() {
        String content = serverMessageInput.getText().trim();

        if (content.isEmpty()) {
            showAlert("Error", "Please enter a message!", Alert.AlertType.WARNING);
            return;
        }

        if (selectedUserIds.size() != 1) {
            showAlert("Error", "Please select EXACTLY 1 recipient for private message!", Alert.AlertType.WARNING);
            return;
        }

        int userId = selectedUserIds.iterator().next();

        // Get username for confirmation
        String username = "User #" + userId;
        if (recipientTable != null) {
            UserTableRow selected = recipientTable.getSelectionModel().getSelectedItem();
            if (selected != null) {
                username = selected.getUsername();
            }
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Confirm Private Message");
        confirm.setHeaderText("Send private message to: " + username);
        confirm.setContentText("Message: " + (content.length() > 50 ? content.substring(0, 50) + "..." : content));

        Optional<ButtonType> result = confirm.showAndWait();

        if (result.isPresent() && result.get() == ButtonType.OK) {
            boolean isImportant = importantCheckBox != null && importantCheckBox.isSelected();

            // Save to database
            ServerMessage msg = serverMessageDAO.sendPrivateMessage("Admin", content, userId, isImportant);

            if (msg != null) {
                logToConsole("✉️ Private message sent to user #" + userId);

                // Send to recipient if online
                Map<String, Object> notification = new HashMap<>();
                notification.put("type", Protocol.NEW_SERVER_MESSAGE);
                notification.put("messageId", msg.getMessageId());
                notification.put("messageType", "private");
                notification.put("senderName", msg.getSenderName());
                notification.put("content", msg.getContent());
                notification.put("sentAt", msg.getSentAt().toString());
                notification.put("isImportant", msg.isImportant());

                boolean sent = false;
                if (gameServer != null) {
                    sent = gameServer.sendToUserId(userId, notification);
                }

                logToConsole("   → " + (sent ? "User online, sent immediately" : "User offline, saved to DB"));

                serverMessageInput.clear();
                if (importantCheckBox != null) importantCheckBox.setSelected(false);
                selectedUserIds.clear();
                if (recipientTable != null) recipientTable.getSelectionModel().clearSelection();
                updateSelectedUsersCount();

                loadMessageHistory(); // Refresh history
                showNotification("Private message sent to " + username + "!", "success");
            } else {
                showAlert("Error", "Failed to send private message!", Alert.AlertType.ERROR);
            }
        }
    }

// ==================== TEMPLATE HANDLERS ====================

    /**
     * Template: Welcome message
     */
    @FXML
    private void handleTemplateWelcome() {
        String template = "🎉 Chào mừng bạn đến với Máy chủ Phiêu lưu Toán học!\n\n" +
                "Chúng tôi rất vui mừng được chào đón bạn. Hãy sẵn sàng cho những trận chiến toán học thú vị và những trải nghiệm học tập bổ ích!\n\n" +
                "Cần trợ giúp? Hãy liên hệ với đội ngũ hỗ trợ của chúng tôi bất cứ lúc nào.\n\n" +
                "Chúc bạn chơi game vui vẻ! 🎮";

        if (serverMessageInput != null) {
            serverMessageInput.setText(template);
        }
    }

    /**
     * Template: Maintenance message
     */
    @FXML
    private void handleTemplateMaintenance() {
        String template = "⚠\uFE0F THÔNG BÁO BẢO TRÌ THEO LỊCH TRÌNH\\n\\n\" +\n" +
                "\"Máy chủ của chúng tôi sẽ được bảo trì vào [NGÀY] từ [GIỜ BẮT ĐẦU] đến [GIỜ KẾT THÚC].\\n\\n\" +\n" +
                "\"Trong thời gian này, trò chơi sẽ tạm thời không thể truy cập. Chúng tôi xin lỗi vì sự bất tiện này.\\n\\n\" +\n" +
                "\"Cảm ơn sự kiên nhẫn và thông cảm của bạn!";

        if (serverMessageInput != null) {
            serverMessageInput.setText(template);
        }
    }

    /**
     * Template: Event announcement
     */
    @FXML
    private void handleTemplateEvent() {
        String template = "🎮 THÔNG BÁO SỰ KIỆN ĐẶC BIỆT!\n\n" +
                "Tham gia [TÊN SỰ KIỆN] của chúng tôi bắt đầu từ [NGÀY]!\n\n" +
                "🏆 Giải thưởng:\n" +
                "- Giải Nhất: [GIẢI THƯỞNG]\n" +
                "- Giải Nhì: [GIẢI THƯỞNG]\n" +
                "- Giải Ba: [GIẢI THƯỞNG]\n\n" +
                "Đừng bỏ lỡ cơ hội hấp dẫn này!\n\n" +
                "Hẹn gặp lại bạn trong trò chơi! 🎯";

        if (serverMessageInput != null) {
            serverMessageInput.setText(template);
        }
    }

    /**
     * Template: General announcement
     */
    @FXML
    private void handleTemplateAnnouncement() {
        String template = "📢 THÔNG BÁO QUAN TRỌNG\n\n" +
                "[Tin nhắn thông báo của bạn tại đây]\n\n" +
                "Để biết thêm thông tin, vui lòng truy cập trang web của chúng tôi hoặc liên hệ với bộ phận hỗ trợ.\n\n" +
                "Cảm ơn bạn!";

        if (serverMessageInput != null) {
            serverMessageInput.setText(template);
        }
    }

// ==================== MESSAGE HISTORY ====================

    /**
     * Load recent message history (last 10 messages)
     */
    private void loadMessageHistory() {
        if (messageHistoryContainer == null || serverMessageDAO == null) {
            return;
        }

        Platform.runLater(() -> {
            try {
                messageHistoryContainer.getChildren().clear();

                // Get recent messages from database
                List<ServerMessage> recentMessages = serverMessageDAO.getRecentMessages(10);

                if (recentMessages == null || recentMessages.isEmpty()) {
                    Label emptyLabel = new Label("No recent messages");
                    emptyLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 12px; -fx-padding: 10;");
                    messageHistoryContainer.getChildren().add(emptyLabel);
                    return;
                }

                // Create message cards
                for (ServerMessage msg : recentMessages) {
                    VBox messageCard = createMessageCard(msg);
                    messageHistoryContainer.getChildren().add(messageCard);
                }

            } catch (Exception e) {
                System.err.println("❌ Error loading message history: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * Create a message card for history display
     */
    private VBox createMessageCard(ServerMessage msg) {
        VBox card = new VBox(8);
        card.getStyleClass().add("message-card");
        card.setPadding(new Insets(12));

        // Header: Type + Time
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);

        Label typeLabel = new Label(getMessageTypeIcon(msg.getMessageType()) + " " +
                msg.getMessageType().toUpperCase());
        typeLabel.getStyleClass().add("message-type-label");

        if (msg.isImportant()) {
            Label importantBadge = new Label("⭐");
            importantBadge.setStyle("-fx-text-fill: #fbbf24;");
            header.getChildren().add(importantBadge);
        }

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label timeLabel = new Label(formatMessageTime(msg.getSentAt()));
        timeLabel.getStyleClass().add("message-time-label");

        header.getChildren().addAll(typeLabel, spacer, timeLabel);

        // Content
        Label contentLabel = new Label(msg.getContent());
        contentLabel.setWrapText(true);
        contentLabel.getStyleClass().add("message-content-label");
        contentLabel.setMaxWidth(Double.MAX_VALUE);

        // Truncate if too long
        if (msg.getContent().length() > 100) {
            contentLabel.setText(msg.getContent().substring(0, 100) + "...");
        }

        // Footer: Recipients count
        Label recipientsLabel = new Label("📧 Recipients: " + getRecipientCount(msg));
        recipientsLabel.getStyleClass().add("message-recipients-label");

        card.getChildren().addAll(header, contentLabel, recipientsLabel);

        return card;
    }

    /**
     * Get message type icon
     */
    private String getMessageTypeIcon(String type) {
        switch (type.toLowerCase()) {
            case "broadcast": return "📢";
            case "group": return "📬";
            case "private": return "✉️";
            default: return "💬";
        }
    }

    /**
     * Format message timestamp
     */
    private String formatMessageTime(java.sql.Timestamp timestamp) {
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, HH:mm");
        return sdf.format(timestamp);
    }

    /**
     * Get recipient count for display
     */
    private int getRecipientCount(ServerMessage msg) {
        if (msg.getMessageType().equals("broadcast")) {
            return allUsers.size(); // All users
        }
        // For group/private, count recipients from database
        // This is a simplified version
        return 1;
    }

    /**
     * Refresh message history
     */
    @FXML
    private void handleRefreshMessageHistory() {
        loadMessageHistory();
        showNotification("Message history refreshed", "info");
    }

    /**
     * View full message history in separate window
     */
    @FXML
    private void handleViewFullHistory() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Message History");
        alert.setHeaderText("Full Server Message History");

        try {
            List<ServerMessage> allMessages = serverMessageDAO.getRecentMessages(50);

            if (allMessages == null || allMessages.isEmpty()) {
                alert.setContentText("No messages found.");
                alert.showAndWait();
                return;
            }

            StringBuilder sb = new StringBuilder();
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

            for (ServerMessage msg : allMessages) {
                sb.append("═══════════════════════════════════════\n");
                sb.append(String.format("[%s] %s %s\n",
                        sdf.format(msg.getSentAt()),
                        getMessageTypeIcon(msg.getMessageType()),
                        msg.getMessageType().toUpperCase()));

                if (msg.isImportant()) {
                    sb.append("⭐ IMPORTANT MESSAGE\n");
                }

                sb.append("\nContent:\n");
                sb.append(msg.getContent());
                sb.append("\n\n");
            }

            TextArea textArea = new TextArea(sb.toString());
            textArea.setEditable(false);
            textArea.setWrapText(true);
            textArea.setFont(javafx.scene.text.Font.font("Consolas", 12));
            textArea.setPrefRowCount(25);
            textArea.setPrefColumnCount(60);

            alert.getDialogPane().setContent(textArea);
            alert.showAndWait();

        } catch (Exception e) {
            System.err.println("❌ Error viewing message history: " + e.getMessage());
            alert.setContentText("Error loading message history: " + e.getMessage());
            alert.showAndWait();
        }
    }


    // ==================== ROOM MANAGEMENT ====================

    @FXML
    private void handleRefreshRooms() {
        updateRoomList();
        updateRoomStatistics();
        showNotification("Danh sách phòng đã được làm mới", "info");
    }

    private void updateRoomList() {
        Platform.runLater(() -> {
            try {
                roomListContainer.getChildren().clear();
                Collection<GameRoomManager.GameRoom> rooms = getRoomsSafely();

                if (rooms == null || rooms.isEmpty()) {
                    Label emptyLabel = new Label("Chưa có phòng nào");
                    emptyLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 14px; -fx-padding: 20;");
                    roomListContainer.getChildren().add(emptyLabel);
                    return;
                }

                for (GameRoomManager.GameRoom room : rooms) {
                    if (room != null && room.getRoomId() != null) {
                        VBox roomCard = createRoomCard(room.getRoomId(), room);
                        roomListContainer.getChildren().add(roomCard);
                    }
                }

                updateRoomStatistics();

            } catch (Exception e) {
                System.err.println("❌ Error updating room list: " + e.getMessage());
            }
        });
    }

    private void updateRoomStatistics() {
        try {
            Collection<GameRoomManager.GameRoom> rooms = getRoomsSafely();
            int total = rooms != null ? rooms.size() : 0;
            int playing = 0;
            int waiting = 0;

            if (rooms != null) {
                for (GameRoomManager.GameRoom room : rooms) {
                    String status = getRoomStatus(room);
                    if ("PLAYING".equals(status)) {
                        playing++;
                    } else if ("WAITING".equals(status)) {
                        waiting++;
                    }
                }
            }

            totalRoomsLabel.setText(String.valueOf(total));
            playingRoomsLabel.setText(String.valueOf(playing));
            waitingRoomsLabel.setText(String.valueOf(waiting));

        } catch (Exception e) {
            System.err.println("⚠️ Error updating room statistics: " + e.getMessage());
        }
    }

    @FXML
    private void handleKickPlayer() {
        if (selectedRoomId == null) return;

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Kick người chơi");
        dialog.setHeaderText("Nhập username của người chơi cần kick");
        dialog.setContentText("Username:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(username -> {
            logToConsole("👊 Kicked player: " + username + " from room " + selectedRoomId);
            showNotification("Đã kick người chơi: " + username, "warning");
            // TODO: Implement kick logic
        });
    }

    private Collection<GameRoomManager.GameRoom> getRoomsSafely() {
        try {
            Object roomsObject = roomManager.getAllRooms();
            if (roomsObject == null) return Collections.emptyList();

            if (roomsObject instanceof Map) {
                Map<?, ?> roomMap = (Map<?, ?>) roomsObject;
                List<GameRoomManager.GameRoom> roomList = new ArrayList<>();
                for (Object value : roomMap.values()) {
                    if (value instanceof GameRoomManager.GameRoom) {
                        roomList.add((GameRoomManager.GameRoom) value);
                    }
                }
                return roomList;
            }

            if (roomsObject instanceof Collection) {
                Collection<?> collection = (Collection<?>) roomsObject;
                List<GameRoomManager.GameRoom> roomList = new ArrayList<>();
                for (Object item : collection) {
                    if (item instanceof GameRoomManager.GameRoom) {
                        roomList.add((GameRoomManager.GameRoom) item);
                    }
                }
                return roomList;
            }

            return Collections.emptyList();
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private void showEmptyRoomList() {
        Label emptyLabel = new Label("No active rooms");
        emptyLabel.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 14px;");
        roomListContainer.getChildren().add(emptyLabel);
    }

    private VBox createRoomCard(String roomId, GameRoomManager.GameRoom room) {
        VBox card = new VBox(10);
        card.getStyleClass().add("room-card");
        card.setPadding(new Insets(15));

        if (roomId.equals(selectedRoomId)) {
            card.getStyleClass().add("room-card-selected");
        }

        // Room Number & Status Badge
        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);

        Label roomNum = new Label("🎮 Room #" + roomId);
        roomNum.getStyleClass().add("room-card-title");
        HBox.setHgrow(roomNum, Priority.ALWAYS);

        String status = getRoomStatus(room);
        Label statusBadge = new Label(status);
        statusBadge.getStyleClass().addAll("status-badge", "status-" + status.toLowerCase());

        header.getChildren().addAll(roomNum, statusBadge);

        // Subject & Difficulty
        String subjectEmoji = getSubjectEmoji(room.getSubject());
        Label subtitle = new Label(subjectEmoji + " " + room.getSubject() + " | " + room.getDifficulty());
        subtitle.getStyleClass().add("room-card-subtitle");

        // Player Count
        Label players = new Label("👥 " + room.getPlayerCount() + "/" + room.getMaxPlayers() + " người chơi");
        players.getStyleClass().add("room-card-players");

        // Host Info
        Label host = new Label("Host: " + (room.getHost() != null ? room.getHost().getUsername() : "N/A"));
        host.getStyleClass().add("room-card-players");

        card.getChildren().addAll(header, subtitle, players, host);
        card.setOnMouseClicked(e -> selectRoom(roomId, room));

        return card;
    }

    private void selectRoom(String roomId, GameRoomManager.GameRoom room) {
        selectedRoomId = roomId;
        updateRoomList();
        displayRoomDetails(roomId, room);
    }

    private void displayRoomDetails(String roomId, GameRoomManager.GameRoom room) {
        Platform.runLater(() -> {
            try {
                // Hide empty state, show content
                emptyState.setVisible(false);
                roomContent.setVisible(true);
                closeRoomButton.setVisible(true);

                // Update header
                selectedRoomTitle.setText("Phòng #" + roomId + " - " + room.getRoomName());
                roomStatusBadge.setText(getRoomStatus(room));
                roomStatusBadge.getStyleClass().clear();
                roomStatusBadge.getStyleClass().addAll("status-badge", "status-" + getRoomStatus(room).toLowerCase());
                roomIdLabel.setText("#" + roomId);

                // Update info
                hostLabel.setText(room.getHost() != null ? room.getHost().getUsername() : "N/A");

                String subjectEmoji = getSubjectEmoji(room.getSubject());
                subjectIconLabel.setText(subjectEmoji);
                subjectLabel.setText(room.getSubject());

                difficultyLabel.setText(room.getDifficulty());
                playerCountLabel.setText(room.getPlayerCount() + "/" + room.getMaxPlayers());

                // Update progress (mock data - replace with real data)
                currentQuestionLabel.setText("Câu 5/10");
                gameProgressBar.setProgress(0.5);
                timeRemainingLabel.setText("30s còn lại");

                correctAnswersLabel.setText("12");
                wrongAnswersLabel.setText("3");
                skippedLabel.setText("1");

                // Update players list
                updatePlayersList(room);

            } catch (Exception e) {
                System.err.println("❌ Error displaying room details: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }


    private void updatePlayersList(GameRoomManager.GameRoom room) {
        try {
            playerListContainer.getChildren().clear();
            List<ClientHandler> players = room.getPlayers();

            if (players == null || players.isEmpty()) {
                Label emptyLabel = new Label("No players");
                emptyLabel.setStyle("-fx-text-fill: #64748b;");
                playerListContainer.getChildren().add(emptyLabel);
                return;
            }

            for (ClientHandler client : players) {
                if (client == null || client.getCurrentUser() == null) continue;

                HBox playerCard = new HBox(10);
                playerCard.getStyleClass().add("player-card");
                playerCard.setAlignment(Pos.CENTER_LEFT);
                playerCard.setPadding(new Insets(10));

                VBox infoBox = new VBox(3);
                infoBox.setAlignment(Pos.CENTER_LEFT);
                HBox.setHgrow(infoBox, Priority.ALWAYS);

                Label nameLabel = new Label(client.getCurrentUser().getFullName());
                nameLabel.getStyleClass().add("player-name");

                Label statusLabel = new Label("@" + client.getCurrentUser().getUsername());
                statusLabel.getStyleClass().add("player-status");

                infoBox.getChildren().addAll(nameLabel, statusLabel);

                HBox badgeBox = new HBox(5);
                badgeBox.setAlignment(Pos.CENTER_RIGHT);

                if (room.getHost() != null &&
                        client.getCurrentUser().getUserId() == room.getHost().getUserId()) {
                    Label hostBadge = new Label("HOST");
                    hostBadge.getStyleClass().add("player-host-badge");
                    badgeBox.getChildren().add(hostBadge);
                }

                if (room.isPlayerReady(client.getCurrentUser().getUserId())) {
                    Label readyBadge = new Label("READY");
                    readyBadge.getStyleClass().add("player-ready-badge");
                    badgeBox.getChildren().add(readyBadge);
                }

                playerCard.getChildren().addAll(infoBox, badgeBox);
                playerListContainer.getChildren().add(playerCard);
            }
        } catch (Exception e) {
            System.err.println("❌ Error updating players: " + e.getMessage());
        }
    }

    @FXML
    private void handleCloseRoom() {
        selectedRoomId = null;
        emptyState.setVisible(true);
        roomContent.setVisible(false);
        closeRoomButton.setVisible(false);
        updateRoomList();
    }

    @FXML
    private void handlePauseGame() {
        if (selectedRoomId == null) return;

        pauseButton.setVisible(false);
        resumeButton.setVisible(true);

        logToConsole("⏸️ Paused room: " + selectedRoomId);
        showNotification("Đã tạm dừng phòng", "warning");
        // TODO: Implement pause logic
    }

    @FXML
    private void handleResumeGame() {
        if (selectedRoomId == null) return;

        resumeButton.setVisible(false);
        pauseButton.setVisible(true);

        logToConsole("▶️ Resumed room: " + selectedRoomId);
        showNotification("Đã tiếp tục phòng", "success");
        // TODO: Implement resume logic
    }

    @FXML
    private void handleAnnounce() {
        if (selectedRoomId == null) return;

        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Gửi thông báo");
        dialog.setHeaderText("Gửi thông báo đến phòng #" + selectedRoomId);
        dialog.setContentText("Nội dung:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(message -> {
            logToConsole("📢 Announcement to room " + selectedRoomId + ": " + message);
            showNotification("Đã gửi thông báo", "info");
            // TODO: Implement broadcast logic
        });
    }

    @FXML
    private void handleDestroyRoom() {
        if (selectedRoomId == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Xác nhận");
        confirm.setHeaderText("Hủy phòng #" + selectedRoomId);
        confirm.setContentText("Điều này sẽ kick tất cả người chơi. Bạn có chắc chắn?");

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            logToConsole("💣 Destroyed room: " + selectedRoomId);
            handleCloseRoom();
            updateRoomList();
            showNotification("Đã hủy phòng", "danger");
            // TODO: Implement destroy logic
        }
    }

    // ==================== CONSOLE ====================

    private void setupConsoleRedirect() {
        OutputStream out = new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                appendText(String.valueOf((char) b));
            }

            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                appendText(new String(b, off, len));
            }

            @Override
            public void write(byte[] b) throws IOException {
                write(b, 0, b.length);
            }
        };

        System.setOut(new PrintStream(out, true));
        System.setErr(new PrintStream(out, true));
    }

    private void appendText(String text) {
        Platform.runLater(() -> {
            if (consoleArea != null) {
                consoleArea.appendText(text);
                consoleArea.setScrollTop(Double.MAX_VALUE);
            }
        });
    }

    private void logToConsole(String message) {
        String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
        String log = "[" + timestamp + "] " + message + "\n";
        appendText(log);
    }

    @FXML
    private void handleClearLog() {
        consoleArea.clear();
        logToConsole("Console cleared");
    }

    // ==================== UPDATE TIMER ====================

    private void startUpdateTimer() {
        updateTimer = new Timer(true);
        updateTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                Platform.runLater(() -> {
                    if (gameServer != null && gameServer.isRunning()) {
                        updateServerStats();
                        updateRoomList();
                    }
                    updateTimestamp();
                });
            }
        }, 0, 1000);
    }

    private void updateServerStats() {
        if (gameServer == null) return;

        try {
            // Connections
            int connections = gameServer.getConnectedClientsCount();
            connectionsLabel.setText(String.valueOf(connections));

            // Update total connections peak
            totalConnections = Math.max(totalConnections, connections);
            totalConnectionsLabel.setText(String.valueOf(totalConnections));

            // Rooms
            Collection<GameRoomManager.GameRoom> rooms = getRoomsSafely();
            int roomCount = rooms != null ? rooms.size() : 0;
            roomsLabel.setText(String.valueOf(roomCount));

            // Players (in rooms)
            int players = 0;
            if (rooms != null) {
                for (GameRoomManager.GameRoom room : rooms) {
                    if (room != null) {
                        players += room.getPlayerCount();
                    }
                }
            }
            playersLabel.setText(String.valueOf(players));

            // Peak players
            peakPlayers = Math.max(peakPlayers, players);
            peakPlayersLabel.setText(String.valueOf(peakPlayers));

            // Uptime
            if (startTime > 0) {
                long uptime = System.currentTimeMillis() - startTime;
                String uptimeStr = formatDuration(uptime);
                uptimeLabel.setText(uptimeStr);
                uptimeStatusLabel.setText("Uptime: " + uptimeStr);
            }

            // Memory
            Runtime runtime = Runtime.getRuntime();
            long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
            memoryLabel.setText("Memory: " + usedMemory + " MB");

            // Port
            portLabel.setText("Port: " + gameServer.getPort());

        } catch (Exception e) {
            System.err.println("⚠️ Error updating stats: " + e.getMessage());
        }
    }

    private void updateTimestamp() {
        String timestamp = new SimpleDateFormat("HH:mm:ss").format(new Date());
        timestampLabel.setText("Last update: " + timestamp);
    }

    private String formatDuration(long millis) {
        Duration duration = Duration.ofMillis(millis);
        long hours = duration.toHours();
        long minutes = duration.toMinutes() % 60;
        long seconds = duration.getSeconds() % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    // ==================== UI STATE ====================

    private void updateUIState(boolean isRunning) {
        Platform.runLater(() -> {
            startButton.setDisable(isRunning);
            stopButton.setDisable(!isRunning);

            if (isRunning) {
                serverStatusLabel.setText("● Server Running");
                serverStatusLabel.setStyle("-fx-text-fill: #4ade80;");
            } else {
                serverStatusLabel.setText("● Server Stopped");
                serverStatusLabel.setStyle("-fx-text-fill: #ef4444;");

                // Reset stats
                connectionsLabel.setText("0");
                roomsLabel.setText("0");
                playersLabel.setText("0");

                // Clear room list
                roomListContainer.getChildren().clear();
                handleCloseRoom();
            }
        });
    }

    // ==================== HELPER METHODS ====================

    private String getRoomStatus(GameRoomManager.GameRoom room) {
        // TODO: Implement proper status detection
        if (room.getPlayerCount() >= room.getMaxPlayers()) {
            return "PLAYING";
        }
        return "WAITING";
    }

    private String getSubjectEmoji(String subject) {
        if (subject == null) return "📖";
        switch (subject.toLowerCase()) {
            case "math": return "🔢";
            case "english": return "🔤";
            case "literature": return "📚";
            default: return "📖";
        }
    }

    private void showAlert(String title, String message, Alert.AlertType type) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    private void showNotification(String message, String type) {
        String emoji = "ℹ️";
        switch (type) {
            case "success": emoji = "✅"; break;
            case "warning": emoji = "⚠️"; break;
            case "danger": emoji = "❌"; break;
        }
        logToConsole(emoji + " " + message);
    }

    // ==================== CLEANUP ====================

    public void shutdown() {
        if (updateTimer != null) {
            updateTimer.cancel();
        }

        if (gameServer != null && gameServer.isRunning()) {
            gameServer.stop();
        }
    }

    public void handleClearMessageInput(ActionEvent actionEvent) {
    }

    public void handleCopyConnectionInfo(ActionEvent actionEvent) {
    }
}