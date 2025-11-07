package com.edugame.client.controller;

import com.edugame.common.Protocol;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;

/**
 * Controller cho popup chọn môn học
 * Hỗ trợ cả Training Mode và Quick Match
 */
public class SubjectSelectionController {

    @FXML private Label titleLabel;
    @FXML private Label subtitleLabel;
    @FXML private Button mathButton;
    @FXML private Button englishButton;
    @FXML private Button literatureButton;

    private SubjectSelectionCallback callback;
    private SelectionMode mode;
    private Stage dialogStage;

    /**
     * Enum để phân biệt 2 chế độ
     */
    public enum SelectionMode {
        TRAINING,      // Chế độ luyện tập
        QUICK_MATCH,    // Chế độ tìm trận nhanh
        CREATE_ROOM
    }

    /**
     * Interface callback khi chọn môn học
     */
    public interface SubjectSelectionCallback {
        void onSubjectSelected(String subject);
    }

    @FXML
    private void initialize() {
        setupButtonHoverEffects();
    }

    /**
     * Cấu hình popup theo mode
     */
    public void setMode(SelectionMode mode) {
        this.mode = mode;

        switch (mode) {
            case TRAINING:
                titleLabel.setText("Chế độ Luyện Tập");
                subtitleLabel.setText("Chọn môn học để bắt đầu luyện tập");
                break;

            case QUICK_MATCH:
                titleLabel.setText("Tìm Trận Nhanh");
                subtitleLabel.setText("Chọn môn học để tìm đối thủ");
                break;

            case CREATE_ROOM:
                titleLabel.setText("Tạo phòng");
                subtitleLabel.setText("Chọn môn học để tạo phòng");
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
        if (callback != null) {
            callback.onSubjectSelected(subject);
        }
        closeDialog();
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
}