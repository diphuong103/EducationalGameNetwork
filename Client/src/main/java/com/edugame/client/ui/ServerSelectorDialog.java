package com.edugame.client.ui;

import com.edugame.client.config.ServerConfig;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;

/**
 * Dialog chọn server configuration - Modern & Kid-Friendly UI
 */
public class ServerSelectorDialog {

    private Stage dialog;
    private boolean confirmed = false;

    private ToggleGroup modeGroup;
    private RadioButton lanRadio;
    private RadioButton onlineRadio;

    private TextField hostField;
    private TextField portField;
    private VBox onlinePanel;

    public ServerSelectorDialog() {
        createDialog();
    }

    private void createDialog() {
        dialog = new Stage();
        dialog.initModality(Modality.APPLICATION_MODAL);
        dialog.setTitle("🎮 Kết nối Game");
        dialog.setResizable(false);

        // Main ScrollPane để tránh tràn
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setStyle("-fx-background: #f0f4f8; -fx-background-color: #f0f4f8;");

        VBox root = new VBox(25);
        root.setPadding(new Insets(30, 40, 30, 40));
        root.setAlignment(Pos.TOP_CENTER);
        root.setStyle("-fx-background-color: #f0f4f8;");

        // Header với icon và title
        VBox header = createHeader();

        // Mode selection cards
        VBox modeBox = createModeSelection();

        // Online config panel
        onlinePanel = createOnlinePanel();
        onlinePanel.setVisible(false);
        onlinePanel.setManaged(false);

        // Buttons
        HBox buttons = createButtons();

        // Add all
        root.getChildren().addAll(
                header,
                modeBox,
                onlinePanel,
                buttons
        );

        scrollPane.setContent(root);

        // Load current config
        loadCurrentConfig();

        Scene scene = new Scene(scrollPane, 550, 680);
        dialog.setScene(scene);
    }

    /**
     * Tạo header hiện đại
     */
    private VBox createHeader() {
        VBox box = new VBox(10);
        box.setAlignment(Pos.CENTER);

        // Emoji icon lớn
        Label icon = new Label("🎮");
        icon.setStyle("-fx-font-size: 48px;");

        // Title
        Label title = new Label("Chọn Cách Kết Nối");
        title.setStyle(
                "-fx-font-size: 26px; " +
                        "-fx-font-weight: bold; " +
                        "-fx-text-fill: #2c3e50;"
        );

        // Subtitle
        Label subtitle = new Label("Chọn cách bạn muốn chơi với bạn bè");
        subtitle.setStyle(
                "-fx-font-size: 14px; " +
                        "-fx-text-fill: #7f8c8d;"
        );

        box.getChildren().addAll(icon, title, subtitle);
        return box;
    }

    /**
     * Tạo phần chọn mode - Modern Card Style
     */
    private VBox createModeSelection() {
        VBox box = new VBox(15);
        box.setAlignment(Pos.CENTER);

        modeGroup = new ToggleGroup();

        // LAN Card
        VBox lanCard = createLanCard();

        // Online Card
        VBox onlineCard = createOnlineCard();

        box.getChildren().addAll(lanCard, onlineCard);
        return box;
    }

    /**
     * LAN Card - Modern design
     */
    private VBox createLanCard() {
        VBox card = new VBox(12);
        card.setPadding(new Insets(20));
        card.setStyle(
                "-fx-background-color: white; " +
                        "-fx-background-radius: 15; " +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 10, 0, 0, 2); " +
                        "-fx-cursor: hand;"
        );
        card.setPrefWidth(450);

        // Radio button với icon
        HBox radioBox = new HBox(10);
        radioBox.setAlignment(Pos.CENTER_LEFT);

        lanRadio = new RadioButton();
        lanRadio.setToggleGroup(modeGroup);
        lanRadio.setSelected(true);
        lanRadio.setStyle("-fx-font-size: 14px;");

        Label titleLabel = new Label("🏠 Chơi Mạng Nội Bộ (LAN)");
        titleLabel.setStyle(
                "-fx-font-size: 18px; " +
                        "-fx-font-weight: bold; " +
                        "-fx-text-fill: #2c3e50;"
        );

        radioBox.getChildren().addAll(lanRadio, titleLabel);

        // Description
        Label desc1 = new Label("Chỉ dành cho bạn bè đang dùng chung mạng WiFi/LAN.");
        desc1.setStyle("-fx-font-size: 13px; -fx-text-fill: #5a6c7d;");
        desc1.setWrapText(true);

        // Badge
        HBox badge = new HBox(8);
        badge.setAlignment(Pos.CENTER_LEFT);

        Label speedIcon = new Label("⚡");
        speedIcon.setStyle("-fx-font-size: 16px;");

        Label speedText = new Label("Tốc độ nhanh, không cần Internet");
        speedText.setStyle(
                "-fx-font-size: 12px; " +
                        "-fx-font-weight: bold; " +
                        "-fx-text-fill: #27ae60;"
        );

        badge.getChildren().addAll(speedIcon, speedText);

        card.getChildren().addAll(radioBox, desc1, badge);

        // Hover effect
        card.setOnMouseEntered(e -> {
            card.setStyle(
                    "-fx-background-color: #e8f4f8; " +
                            "-fx-background-radius: 15; " +
                            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 15, 0, 0, 3); " +
                            "-fx-cursor: hand;"
            );
        });
        card.setOnMouseExited(e -> {
            card.setStyle(
                    "-fx-background-color: white; " +
                            "-fx-background-radius: 15; " +
                            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 10, 0, 0, 2); " +
                            "-fx-cursor: hand;"
            );
        });

        // Click to select
        card.setOnMouseClicked(e -> lanRadio.setSelected(true));

        // Listener
        lanRadio.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                onlinePanel.setVisible(false);
                onlinePanel.setManaged(false);
            }
        });

        return card;
    }

    /**
     * Online Card - Modern design
     */
    private VBox createOnlineCard() {
        VBox card = new VBox(12);
        card.setPadding(new Insets(20));
        card.setStyle(
                "-fx-background-color: white; " +
                        "-fx-background-radius: 15; " +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 10, 0, 0, 2); " +
                        "-fx-cursor: hand;"
        );
        card.setPrefWidth(450);

        // Radio button với icon
        HBox radioBox = new HBox(10);
        radioBox.setAlignment(Pos.CENTER_LEFT);

        onlineRadio = new RadioButton();
        onlineRadio.setToggleGroup(modeGroup);
        onlineRadio.setStyle("-fx-font-size: 14px;");

        Label titleLabel = new Label("🌍 Chơi Qua Internet (Toàn cầu)");
        titleLabel.setStyle(
                "-fx-font-size: 18px; " +
                        "-fx-font-weight: bold; " +
                        "-fx-text-fill: #2c3e50;"
        );

        radioBox.getChildren().addAll(onlineRadio, titleLabel);

        // Description
        Label desc1 = new Label("Cho phép bạn bè ở bất cứ đâu tham gia.");
        desc1.setStyle("-fx-font-size: 13px; -fx-text-fill: #5a6c7d;");
        desc1.setWrapText(true);

        // Badge
        HBox badge = new HBox(8);
        badge.setAlignment(Pos.CENTER_LEFT);

        Label reqIcon = new Label("ℹ️");
        reqIcon.setStyle("-fx-font-size: 16px;");

        Label reqText = new Label("Yêu cầu server chạy Ngrok hoặc có IP Public");
        reqText.setStyle(
                "-fx-font-size: 12px; " +
                        "-fx-font-weight: bold; " +
                        "-fx-text-fill: #3498db;"
        );

        badge.getChildren().addAll(reqIcon, reqText);

        card.getChildren().addAll(radioBox, desc1, badge);

        // Hover effect
        card.setOnMouseEntered(e -> {
            card.setStyle(
                    "-fx-background-color: #fff4e6; " +
                            "-fx-background-radius: 15; " +
                            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 15, 0, 0, 3); " +
                            "-fx-cursor: hand;"
            );
        });
        card.setOnMouseExited(e -> {
            card.setStyle(
                    "-fx-background-color: white; " +
                            "-fx-background-radius: 15; " +
                            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 10, 0, 0, 2); " +
                            "-fx-cursor: hand;"
            );
        });

        // Click to select
        card.setOnMouseClicked(e -> onlineRadio.setSelected(true));

        // Listener
        onlineRadio.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                onlinePanel.setVisible(true);
                onlinePanel.setManaged(true);
            }
        });

        return card;
    }

    /**
     * Panel cấu hình Online - Compact design
     */
    private VBox createOnlinePanel() {
        VBox box = new VBox(15);
        box.setPadding(new Insets(20));
        box.setStyle(
                "-fx-background-color: linear-gradient(to bottom, #667eea 0%, #764ba2 100%); " +
                        "-fx-background-radius: 15; " +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 10, 0, 0, 2);"
        );
        box.setPrefWidth(450);

        // Title
        Label title = new Label("⚙️ Cấu Hình Kết Nối");
        title.setStyle(
                "-fx-font-size: 18px; " +
                        "-fx-font-weight: bold; " +
                        "-fx-text-fill: white;"
        );

        // Instruction
        Label instruction = new Label(
                "Hỏi người tạo phòng để lấy địa chỉ kết nối:"
        );
        instruction.setStyle(
                "-fx-font-size: 13px; " +
                        "-fx-text-fill: rgba(255,255,255,0.9);"
        );
        instruction.setWrapText(true);

        // Input fields - Modern style
        VBox inputBox = new VBox(12);

        // Host field
        VBox hostBox = new VBox(5);
        Label hostLabel = new Label("🔗 Địa chỉ Host:");
        hostLabel.setStyle("-fx-text-fill: white; -fx-font-size: 12px; -fx-font-weight: bold;");

        hostField = new TextField();
        hostField.setPromptText("VD: 0.tcp.ngrok.io hoặc 123.45.67.89");
        hostField.setStyle(
                "-fx-background-color: white; " +
                        "-fx-background-radius: 8; " +
                        "-fx-padding: 10; " +
                        "-fx-font-size: 13px; " +
                        "-fx-border-radius: 8;"
        );
        hostField.setPrefWidth(410);

        hostBox.getChildren().addAll(hostLabel, hostField);

        // Port field
        VBox portBox = new VBox(5);
        Label portLabel = new Label("🔢 Cổng (Port):");
        portLabel.setStyle("-fx-text-fill: white; -fx-font-size: 12px; -fx-font-weight: bold;");

        portField = new TextField();
        portField.setPromptText("VD: 12345 hoặc 8888");
        portField.setStyle(
                "-fx-background-color: white; " +
                        "-fx-background-radius: 8; " +
                        "-fx-padding: 10; " +
                        "-fx-font-size: 13px; " +
                        "-fx-border-radius: 8;"
        );
        portField.setPrefWidth(410);

        portBox.getChildren().addAll(portLabel, portField);

        inputBox.getChildren().addAll(hostBox, portBox);

        // Help text
        Label helpText = new Label(
                "💡 Tip: Người tạo phòng chạy lệnh 'ngrok tcp 8888' để lấy địa chỉ này"
        );
        helpText.setStyle(
                "-fx-font-size: 11px; " +
                        "-fx-text-fill: rgba(255,255,255,0.8); " +
                        "-fx-font-style: italic;"
        );
        helpText.setWrapText(true);

        box.getChildren().addAll(title, instruction, inputBox, helpText);
        return box;
    }

    /**
     * Tạo buttons - Modern gradient style
     */
    private HBox createButtons() {
        HBox box = new HBox(15);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(10, 0, 0, 0));

        // Save button
        Button saveBtn = new Button("🚀 Kết Nối Ngay");
        saveBtn.setPrefWidth(200);
        saveBtn.setPrefHeight(45);
        saveBtn.setStyle(
                "-fx-background-color: linear-gradient(to right, #56ab2f, #a8e063); " +
                        "-fx-text-fill: white; " +
                        "-fx-font-size: 16px; " +
                        "-fx-font-weight: bold; " +
                        "-fx-background-radius: 25; " +
                        "-fx-cursor: hand; " +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 8, 0, 0, 2);"
        );
        saveBtn.setOnAction(e -> handleSave());

        // Hover effect
        saveBtn.setOnMouseEntered(e -> {
            saveBtn.setStyle(
                    "-fx-background-color: linear-gradient(to right, #a8e063, #56ab2f); " +
                            "-fx-text-fill: white; " +
                            "-fx-font-size: 16px; " +
                            "-fx-font-weight: bold; " +
                            "-fx-background-radius: 25; " +
                            "-fx-cursor: hand; " +
                            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.4), 12, 0, 0, 3);"
            );
        });
        saveBtn.setOnMouseExited(e -> {
            saveBtn.setStyle(
                    "-fx-background-color: linear-gradient(to right, #56ab2f, #a8e063); " +
                            "-fx-text-fill: white; " +
                            "-fx-font-size: 16px; " +
                            "-fx-font-weight: bold; " +
                            "-fx-background-radius: 25; " +
                            "-fx-cursor: hand; " +
                            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 8, 0, 0, 2);"
            );
        });

        // Cancel button
        Button cancelBtn = new Button("❌ Hủy");
        cancelBtn.setPrefWidth(150);
        cancelBtn.setPrefHeight(45);
        cancelBtn.setStyle(
                "-fx-background-color: #e74c3c; " +
                        "-fx-text-fill: white; " +
                        "-fx-font-size: 15px; " +
                        "-fx-font-weight: bold; " +
                        "-fx-background-radius: 25; " +
                        "-fx-cursor: hand; " +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 8, 0, 0, 2);"
        );
        cancelBtn.setOnAction(e -> dialog.close());

        // Hover effect
        cancelBtn.setOnMouseEntered(e -> {
            cancelBtn.setStyle(
                    "-fx-background-color: #c0392b; " +
                            "-fx-text-fill: white; " +
                            "-fx-font-size: 15px; " +
                            "-fx-font-weight: bold; " +
                            "-fx-background-radius: 25; " +
                            "-fx-cursor: hand; " +
                            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.4), 12, 0, 0, 3);"
            );
        });
        cancelBtn.setOnMouseExited(e -> {
            cancelBtn.setStyle(
                    "-fx-background-color: #e74c3c; " +
                            "-fx-text-fill: white; " +
                            "-fx-font-size: 15px; " +
                            "-fx-font-weight: bold; " +
                            "-fx-background-radius: 25; " +
                            "-fx-cursor: hand; " +
                            "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 8, 0, 0, 2);"
            );
        });

        box.getChildren().addAll(saveBtn, cancelBtn);
        return box;
    }

    /**
     * Load config hiện tại
     */
    private void loadCurrentConfig() {
        ServerConfig config = ServerConfig.getInstance();

        String mode = config.getMode();
        if ("LOCAL".equals(mode)) {
            lanRadio.setSelected(true);
        } else {
            onlineRadio.setSelected(true);
            hostField.setText(config.getHost());
            portField.setText(String.valueOf(config.getPort()));
        }
    }

    /**
     * Xử lý save
     */
    private void handleSave() {
        Toggle selected = modeGroup.getSelectedToggle();

        if (selected == null) {
            showError("Vui lòng chọn chế độ kết nối!");
            return;
        }

        String mode;
        String host;
        int port;

        if (selected == lanRadio) {
            mode = "LOCAL";
            host = "localhost";
            port = 8888;

        } else { // onlineRadio
            mode = "NGROK";
            host = hostField.getText().trim();
            String portStr = portField.getText().trim();

            if (host.isEmpty() || portStr.isEmpty()) {
                showError("Vui lòng nhập đầy đủ Host và Port!");
                return;
            }

            try {
                port = Integer.parseInt(portStr);
                if (port < 1 || port > 65535) {
                    showError("Port phải từ 1 đến 65535!");
                    return;
                }
            } catch (NumberFormatException e) {
                showError("Port phải là số nguyên hợp lệ!");
                return;
            }
        }

        // Save config
        ServerConfig.getInstance().updateConfig(mode, host, port);

        confirmed = true;
        dialog.close();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("❌ Lỗi");
        alert.setHeaderText(null);
        alert.setContentText(message);

        // Style the alert
        DialogPane dialogPane = alert.getDialogPane();
        dialogPane.setStyle(
                "-fx-background-color: white; " +
                        "-fx-font-family: 'Segoe UI'; " +
                        "-fx-font-size: 13px;"
        );

        alert.showAndWait();
    }

    public boolean showAndWait() {
        dialog.showAndWait();
        return confirmed;
    }
}