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
 * ✅ FIXED: LAN mode now requires manual SERVER IP input
 */
public class ServerSelectorDialog {

    private Stage dialog;
    private boolean confirmed = false;

    private ToggleGroup modeGroup;
    private RadioButton lanRadio;
    private RadioButton ngrokRadio;

    // ✅ THÊM FIELD CHO LAN HOST INPUT
    private TextField lanHostField;
    private TextField hostField;
    private TextField portField;
    private VBox ngrokPanel;

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

        // Ngrok config panel
        ngrokPanel = createNgrokPanel();
        ngrokPanel.setVisible(false);
        ngrokPanel.setManaged(false);

        // Buttons
        HBox buttons = createButtons();

        // Add all
        root.getChildren().addAll(
                header,
                modeBox,
                ngrokPanel,
                buttons
        );

        scrollPane.setContent(root);

        // Load current config
        loadCurrentConfig();

        Scene scene = new Scene(scrollPane, 550, 720);
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

        // Ngrok Card
        VBox ngrokCard = createNgrokCard();

        box.getChildren().addAll(lanCard, ngrokCard);
        return box;
    }

    /**
     * ✅ LAN Card - FIXED: Có input field để nhập IP server
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

        Label titleLabel = new Label("🏠 LAN - Chơi Cùng Mạng");
        titleLabel.setStyle(
                "-fx-font-size: 18px; " +
                        "-fx-font-weight: bold; " +
                        "-fx-text-fill: #2c3e50;"
        );

        radioBox.getChildren().addAll(lanRadio, titleLabel);

        // Description
        Label desc1 = new Label("Kết nối với máy chủ trong cùng mạng WiFi/LAN");
        desc1.setStyle("-fx-font-size: 13px; -fx-text-fill: #5a6c7d;");
        desc1.setWrapText(true);

        // ✅ INPUT FIELD CHO SERVER IP
        VBox inputBox = new VBox(8);
        inputBox.setStyle("-fx-padding: 10 0 5 0;");

        Label ipLabel = new Label("🔗 Nhập IP của máy chủ (Server):");
        ipLabel.setStyle(
                "-fx-font-size: 12px; " +
                        "-fx-font-weight: bold; " +
                        "-fx-text-fill: #34495e;"
        );

        lanHostField = new TextField();
        lanHostField.setPromptText("VD: 192.168.1.30");
        lanHostField.setText("192.168.1.30"); // Giá trị mặc định
        lanHostField.setStyle(
                "-fx-background-color: #f8f9fa; " +
                        "-fx-background-radius: 8; " +
                        "-fx-padding: 10; " +
                        "-fx-font-size: 13px; " +
                        "-fx-border-color: #dee2e6; " +
                        "-fx-border-width: 1; " +
                        "-fx-border-radius: 8;"
        );

        inputBox.getChildren().addAll(ipLabel, lanHostField);

        // Hiển thị IP của client (chỉ để tham khảo)
        String clientIP = ServerConfig.getLocalIPAddress();
        Label clientInfo = new Label("📍 IP của bạn (Client): " + clientIP);
        clientInfo.setStyle(
                "-fx-font-size: 11px; " +
                        "-fx-text-fill: #7f8c8d; " +
                        "-fx-font-style: italic; " +
                        "-fx-padding: 5 0 0 0;"
        );

        // Help text
        Label helpText = new Label("💡 Hỏi người tạo phòng để biết IP máy chủ");
        helpText.setStyle(
                "-fx-font-size: 11px; " +
                        "-fx-text-fill: #95a5a6; " +
                        "-fx-font-style: italic;"
        );
        helpText.setWrapText(true);

        // Badge
        HBox badge = new HBox(8);
        badge.setAlignment(Pos.CENTER_LEFT);

        Label speedIcon = new Label("⚡");
        speedIcon.setStyle("-fx-font-size: 16px;");

        Label speedText = new Label("Nhanh - Độ trễ thấp");
        speedText.setStyle(
                "-fx-font-size: 12px; " +
                        "-fx-font-weight: bold; " +
                        "-fx-text-fill: #27ae60;"
        );

        badge.getChildren().addAll(speedIcon, speedText);

        card.getChildren().addAll(radioBox, desc1, inputBox, clientInfo, helpText, badge);

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

        return card;
    }

    /**
     * Ngrok Card - Chơi online không cần cùng mạng
     */
    private VBox createNgrokCard() {
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

        ngrokRadio = new RadioButton();
        ngrokRadio.setToggleGroup(modeGroup);
        ngrokRadio.setStyle("-fx-font-size: 14px;");

        Label titleLabel = new Label("🌍 NGROK - Chơi Online");
        titleLabel.setStyle(
                "-fx-font-size: 18px; " +
                        "-fx-font-weight: bold; " +
                        "-fx-text-fill: #2c3e50;"
        );

        radioBox.getChildren().addAll(ngrokRadio, titleLabel);

        // Description
        Label desc1 = new Label("Chơi với bạn bè ở bất kỳ đâu - không cần cùng mạng WiFi");
        desc1.setStyle("-fx-font-size: 13px; -fx-text-fill: #5a6c7d;");
        desc1.setWrapText(true);

        // Badge
        HBox badge = new HBox(8);
        badge.setAlignment(Pos.CENTER_LEFT);

        Label reqIcon = new Label("ℹ️");
        reqIcon.setStyle("-fx-font-size: 16px;");

        Label reqText = new Label("Yêu cầu địa chỉ Ngrok từ người tạo phòng");
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
        card.setOnMouseClicked(e -> ngrokRadio.setSelected(true));

        // Listener
        ngrokRadio.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                ngrokPanel.setVisible(true);
                ngrokPanel.setManaged(true);
            }
        });

        // Listener cho lanRadio để ẩn ngrok panel
        lanRadio.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal) {
                ngrokPanel.setVisible(false);
                ngrokPanel.setManaged(false);
            }
        });

        return card;
    }

    /**
     * Panel cấu hình Ngrok - Compact design
     */
    private VBox createNgrokPanel() {
        VBox box = new VBox(15);
        box.setPadding(new Insets(20));
        box.setStyle(
                "-fx-background-color: linear-gradient(to bottom, #667eea 0%, #764ba2 100%); " +
                        "-fx-background-radius: 15; " +
                        "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.15), 10, 0, 0, 2);"
        );
        box.setPrefWidth(450);

        // Title
        Label title = new Label("⚙️ Cấu Hình Ngrok");
        title.setStyle(
                "-fx-font-size: 18px; " +
                        "-fx-font-weight: bold; " +
                        "-fx-text-fill: white;"
        );

        // Instruction
        Label instruction = new Label(
                "Hỏi người tạo phòng để lấy địa chỉ Ngrok:"
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
        hostField.setPromptText("VD: 0.tcp.ngrok.io");
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
        portField.setPromptText("VD: 12345");
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
     * ✅ Load config hiện tại - FIXED
     */
    private void loadCurrentConfig() {
        ServerConfig config = ServerConfig.getInstance();

        String mode = config.getMode();
        if ("LOCAL".equals(mode) || "LAN".equals(mode)) {
            lanRadio.setSelected(true);

            // ✅ Load IP đã lưu vào field
            if ("LAN".equals(mode)) {
                lanHostField.setText(config.getHost());
            }

        } else if ("NGROK".equals(mode)) {
            ngrokRadio.setSelected(true);
            hostField.setText(config.getHost());
            portField.setText(String.valueOf(config.getPort()));
        }
    }

    /**
     * ✅ Xử lý save - FIXED
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
            // ✅ LAN Mode - LẤY IP TỪ INPUT FIELD (KHÔNG TỰ ĐỘNG PHÁT HIỆN)
            mode = "LAN";
            host = lanHostField.getText().trim();
            port = 8888;

            // Validate IP
            if (host.isEmpty()) {
                showError("Vui lòng nhập IP của máy chủ!");
                return;
            }

            // Kiểm tra format IP đơn giản
            if (!host.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")
                    && !host.equals("localhost")) {
                showError("IP không hợp lệ!\nVí dụ đúng: 192.168.1.30");
                return;
            }

            System.out.println("✅ LAN Mode selected:");
            System.out.println("   Server IP: " + host);
            System.out.println("   Client IP: " + ServerConfig.getLocalIPAddress());

        } else { // ngrokRadio
            // NGROK Mode
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