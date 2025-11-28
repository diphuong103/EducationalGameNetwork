package com.edugame.client.controller;

import com.edugame.common.Protocol;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 * Controller cho popup chọn môn học và độ khó
 * Hỗ trợ cả Training Mode và Quick Match
 */
public class SubjectSelectionController {

    @FXML private Label titleLabel;
    @FXML private Label subtitleLabel;
    @FXML private Button mathButton;
    @FXML private Button englishButton;
    @FXML private Button literatureButton;

    @FXML private ToggleGroup difficultyGroup;
    @FXML private ToggleButton easyButton;
    @FXML private ToggleButton mediumButton;
    @FXML private ToggleButton hardButton;

    @FXML private VBox playerCountBox;
    @FXML private ToggleGroup playerCountGroup;
    @FXML private ToggleButton player2Button;
    @FXML private ToggleButton player4Button;


    private SubjectSelectionCallback callback;
    private SelectionMode mode;
    private Stage dialogStage;

    /**
     * Enum để phân biệt 2 chế độ
     */
    public enum SelectionMode {
        TRAINING,
        QUICK_MATCH,
        CREATE_ROOM
    }
    /**
     * Interface callback khi chọn môn học và độ khó
     */
    public interface SubjectSelectionCallback {
        void onSubjectSelected(String subject, String difficulty, int playerCount);

    }

    @FXML
    private void initialize() {
        setupButtonHoverEffects();
        setupDifficultyButtons();
    }

    /**
     * Cấu hình popup theo mode
     */
    public void setMode(SelectionMode mode) {
        this.mode = mode;

        switch (mode) {
            case TRAINING:
                titleLabel.setText("Chế độ Luyện Tập");
                subtitleLabel.setText("Chọn môn học và độ khó để bắt đầu luyện tập");
                playerCountBox.setVisible(false);
                playerCountBox.setManaged(false);
                break;

            case QUICK_MATCH:
                titleLabel.setText("Tìm Trận Nhanh");
                subtitleLabel.setText("Chọn môn học, độ khó và số người chơi");
                playerCountBox.setVisible(true);
                playerCountBox.setManaged(true);
                if (player2Button != null) {
                    player2Button.setSelected(true);
                }
                break;

            case CREATE_ROOM:
                titleLabel.setText("Tạo phòng");
                subtitleLabel.setText("Chọn môn học và độ khó để tạo phòng");
                playerCountBox.setVisible(false);
                playerCountBox.setManaged(false);
                break;
        }

    }

    /**
     * Set callback function
     */
    public void setCallback(SubjectSelectionCallback callback) {
        this.callback = callback;
    }

    /**
     * Set dialog stage để đóng khi xong
     */
    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    @FXML
    private void handleMathSelected() {
        selectSubject(Protocol.MATH);
    }

    @FXML
    private void handleEnglishSelected() {
        selectSubject(Protocol.ENGLISH);
    }

    @FXML
    private void handleLiteratureSelected() {
        selectSubject(Protocol.LITERATURE);
    }

    @FXML
    private void handleCancel() {
        closeDialog();
    }

    /**
     * Xử lý khi chọn môn học
     */
    private void selectSubject(String subject) {
        String difficulty = getSelectedDifficulty();
        int playerCount = getPlayerCount();

        if (callback != null) {
            callback.onSubjectSelected(subject, difficulty, playerCount);
        }
        closeDialog();
    }

    private int getPlayerCount() {
        if (player4Button.isVisible() && player4Button.isSelected()) {
            return 4;
        }
        return 2; // default
    }


    /**
     * Lấy độ khó đã chọn
     */
    private String getSelectedDifficulty() {
        if (easyButton.isSelected()) {
            return "easy";
        } else if (mediumButton.isSelected()) {
            return "medium";
        } else if (hardButton.isSelected()) {
            return "hard";
        }
        return "easy"; // Default
    }

    /**
     * Đóng dialog
     */
    private void closeDialog() {
        if (dialogStage != null) {
            dialogStage.close();
        }
    }

    /**
     * Setup hover effects cho buttons
     */
    private void setupButtonHoverEffects() {
        setupButtonHover(mathButton,
                "-fx-background-color: linear-gradient(to right, #667eea, #764ba2);",
                "-fx-background-color: linear-gradient(to right, #5568d3, #654393);");

        setupButtonHover(englishButton,
                "-fx-background-color: linear-gradient(to right, #f093fb, #f5576c);",
                "-fx-background-color: linear-gradient(to right, #d97fe2, #dc4a5d);");

        setupButtonHover(literatureButton,
                "-fx-background-color: linear-gradient(to right, #4facfe, #00f2fe);",
                "-fx-background-color: linear-gradient(to right, #3a9ae5, #00d9e5);");
    }

    private void setupButtonHover(Button button, String normalStyle, String hoverStyle) {
        String baseStyle = "-fx-background-radius: 15; -fx-cursor: hand;";

        button.setOnMouseEntered(e ->
                button.setStyle(hoverStyle + baseStyle + "-fx-scale-x: 1.02; -fx-scale-y: 1.02;")
        );

        button.setOnMouseExited(e ->
                button.setStyle(normalStyle + baseStyle)
        );

        button.setOnMousePressed(e ->
                button.setStyle(hoverStyle + baseStyle + "-fx-scale-x: 0.98; -fx-scale-y: 0.98;")
        );

        button.setOnMouseReleased(e ->
                button.setStyle(hoverStyle + baseStyle + "-fx-scale-x: 1.02; -fx-scale-y: 1.02;")
        );
    }

    /**
     * Setup các nút độ khó với hiệu ứng
     */
    private void setupDifficultyButtons() {
        setupDifficultyButtonStyle(easyButton, "#2ecc71", "#27ae60");
        setupDifficultyButtonStyle(mediumButton, "#f39c12", "#e67e22");
        setupDifficultyButtonStyle(hardButton, "#e74c3c", "#c0392b");
    }

    private void setupDifficultyButtonStyle(ToggleButton button, String normalColor, String selectedColor) {

        // Base style giữ nguyên cho mọi trạng thái
        final String baseStyle = """
            -fx-background-radius: 10;
            -fx-cursor: hand;
            -fx-font-size: 14;
            -fx-font-weight: bold;
            -fx-text-fill: white;
    """;

        // Hàm apply style
        Runnable updateStyle = () -> {
            if (button.isSelected()) {
                button.setStyle(
                        baseStyle +
                                "-fx-background-color: " + selectedColor + ";" +
                                "-fx-border-color: white;" +
                                "-fx-border-width: 3;" +
                                "-fx-effect: dropshadow(gaussian, rgba(0,0,0,0.35), 12, 0.2, 0, 3);"
                );
            } else {
                button.setStyle(
                        baseStyle +
                                "-fx-background-color: " + normalColor + ";" +
                                "-fx-border-width: 0;" +
                                "-fx-effect: null;"
                );
            }
        };


        // Lần đầu chạy
        updateStyle.run();

        // Khi chọn
        button.selectedProperty().addListener((obs, oldV, newV) -> updateStyle.run());

        // Hover
        button.setOnMouseEntered(e -> {
            if (!button.isSelected()) {
                button.setStyle(
                        baseStyle +
                                "-fx-background-color: " + selectedColor + ";" +
                                "-fx-scale-x: 1.05;" +
                                "-fx-scale-y: 1.05;"
                );
            }
        });

        button.setOnMouseExited(e -> updateStyle.run());
    }
}