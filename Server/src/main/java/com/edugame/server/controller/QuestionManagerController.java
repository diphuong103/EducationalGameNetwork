package com.edugame.server.controller;

import com.edugame.server.database.QuestionDAO;
import com.edugame.server.model.Question;
import com.edugame.server.service.AIService;
import com.edugame.server.util.FileImporter;
import com.edugame.server.util.SampleFileGenerator;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class QuestionManagerController {

    // Statistics
    @FXML private Label totalQuestionsLabel;
    @FXML private Label mathQuestionsLabel;
    @FXML private Label literatureQuestionsLabel;
    @FXML private Label englishQuestionsLabel;

    // Filters
    @FXML private TextField searchField;
    @FXML private ComboBox<String> subjectFilter;
    @FXML private ComboBox<String> difficultyFilter;

    // Selection label
    @FXML private Label selectedCountLabel;

    // Table
    @FXML private TableView<Question> questionTable;
    @FXML private TableColumn<Question, Integer> sttColumn;
    @FXML private TableColumn<Question, String> subjectColumn;
    @FXML private TableColumn<Question, String> contentColumn;
    @FXML private TableColumn<Question, String> answerAColumn;
    @FXML private TableColumn<Question, String> answerBColumn;
    @FXML private TableColumn<Question, String> answerCColumn;
    @FXML private TableColumn<Question, String> answerDColumn;
    @FXML private TableColumn<Question, String> correctAnswerColumn;
    @FXML private TableColumn<Question, String> difficultyColumn;

    // Form inputs
    @FXML private TextArea questionContentArea;
    @FXML private ComboBox<String> subjectCombo;
    @FXML private ComboBox<String> difficultyCombo;
    @FXML private TextField answerAField;
    @FXML private TextField answerBField;
    @FXML private TextField answerCField;
    @FXML private TextField answerDField;
    @FXML private ComboBox<String> correctAnswerCombo;
    @FXML private Button saveButton;

    // AI generate form
    @FXML private TextArea aiPromptArea;
    @FXML private ComboBox<String> aiSubjectCombo;
    @FXML private ComboBox<String> aiDifficultyCombo;
    @FXML private TextField aiQuantityField;

    private QuestionDAO questionDAO;
    private ObservableList<Question> questionList = FXCollections.observableArrayList();
    private ObservableList<Question> filteredList = FXCollections.observableArrayList();
    private Question editingQuestion = null;

    // Subject mapping: Display name -> DB value
    private final java.util.Map<String, String> subjectMap = new java.util.LinkedHashMap<>();
    private final java.util.Map<String, String> subjectReverseMap = new java.util.LinkedHashMap<>();

    // Difficulty mapping: Display name -> DB value
    private final java.util.Map<String, String> difficultyMap = new java.util.LinkedHashMap<>();
    private final java.util.Map<String, String> difficultyReverseMap = new java.util.LinkedHashMap<>();

    @FXML
    public void initialize() {
        questionDAO = new QuestionDAO();
        initializeMappings();
        setupTable();
        setupFilters();
        setupFormComponents();
        setupAIComponents();
        loadAllQuestions();
    }

    private void initializeMappings() {
        // Subject mappings
        subjectMap.put("Toán học", "math");
        subjectMap.put("Ngữ văn", "literature");
        subjectMap.put("Tiếng Anh", "english");

        // Reverse mapping for display
        subjectReverseMap.put("math", "Toán học");
        subjectReverseMap.put("literature", "Ngữ văn");
        subjectReverseMap.put("english", "Tiếng Anh");

        // Difficulty mappings
        difficultyMap.put("Dễ", "easy");
        difficultyMap.put("Trung bình", "medium");
        difficultyMap.put("Khó", "hard");

        // Reverse mapping for display
        difficultyReverseMap.put("easy", "Dễ");
        difficultyReverseMap.put("medium", "Trung bình");
        difficultyReverseMap.put("hard", "Khó");
    }

    private String getSubjectDbValue(String displayValue) {
        return subjectMap.getOrDefault(displayValue, displayValue);
    }

    private String getSubjectDisplayValue(String dbValue) {
        return subjectReverseMap.getOrDefault(dbValue, dbValue);
    }

    private String getDifficultyDbValue(String displayValue) {
        return difficultyMap.getOrDefault(displayValue, displayValue);
    }

    private String getDifficultyDisplayValue(String dbValue) {
        return difficultyReverseMap.getOrDefault(dbValue, dbValue);
    }

    private void setupTable() {
        sttColumn.setCellValueFactory(cellData ->
                new SimpleIntegerProperty(questionTable.getItems().indexOf(cellData.getValue()) + 1).asObject());
        subjectColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(getSubjectDisplayValue(cellData.getValue().getSubject())));
        contentColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getQuestionText()));
        answerAColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getOptionA()));
        answerBColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getOptionB()));
        answerCColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getOptionC()));
        answerDColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getOptionD()));
        correctAnswerColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(cellData.getValue().getCorrectAnswer()));
        difficultyColumn.setCellValueFactory(cellData ->
                new SimpleStringProperty(getDifficultyDisplayValue(cellData.getValue().getDifficulty())));

        questionTable.setItems(filteredList);

        // Handle row selection with toggle functionality
        questionTable.setOnMouseClicked(event -> {
            Question selected = questionTable.getSelectionModel().getSelectedItem();

            if (selected != null) {
                // If clicking the same row again, deselect it
                if (selected.equals(editingQuestion)) {
                    questionTable.getSelectionModel().clearSelection();
                    clearForm();
                    editingQuestion = null;
                    saveButton.setText("Thêm câu hỏi");
                    updateSelectionLabel();
                } else {
                    // New selection
                    fillFormWithQuestion(selected);
                    updateSelectionLabel();
                }
            }
        });

        // Update selection label when selection changes
        questionTable.getSelectionModel().selectedItemProperty().addListener(
                (obs, oldVal, newVal) -> updateSelectionLabel()
        );
    }

    private void updateSelectionLabel() {
        Question selected = questionTable.getSelectionModel().getSelectedItem();
        if (selected != null) {
            selectedCountLabel.setText("(Đã chọn: 1 câu hỏi)");
            selectedCountLabel.setStyle("-fx-text-fill: #11998e; -fx-font-weight: bold;");
        } else {
            selectedCountLabel.setText("");
        }
    }

    private void setupFilters() {
        // Filter combos
        subjectFilter.setItems(FXCollections.observableArrayList(
                "Tất cả", "Toán học", "Ngữ văn", "Tiếng Anh"
        ));
        subjectFilter.setValue("Tất cả");

        difficultyFilter.setItems(FXCollections.observableArrayList(
                "Tất cả", "Dễ", "Trung bình", "Khó"
        ));
        difficultyFilter.setValue("Tất cả");

        // Apply filters on change
        subjectFilter.setOnAction(e -> applyFilters());
        difficultyFilter.setOnAction(e -> applyFilters());
        searchField.textProperty().addListener((obs, oldVal, newVal) -> applyFilters());
    }

    private void setupFormComponents() {
        subjectCombo.setItems(FXCollections.observableArrayList(
                "Toán học", "Ngữ văn", "Tiếng Anh"
        ));

        difficultyCombo.setItems(FXCollections.observableArrayList(
                "Dễ", "Trung bình", "Khó"
        ));

        correctAnswerCombo.setItems(FXCollections.observableArrayList(
                "A", "B", "C", "D"
        ));
    }

    private void setupAIComponents() {
        aiSubjectCombo.setItems(FXCollections.observableArrayList(
                "Toán học", "Ngữ văn", "Tiếng Anh"
        ));
        aiSubjectCombo.setValue("Toán học");

        aiDifficultyCombo.setItems(FXCollections.observableArrayList(
                "Dễ", "Trung bình", "Khó"
        ));
        aiDifficultyCombo.setValue("Dễ");
    }

    private void loadAllQuestions() {
        questionList.clear();
        questionList.addAll(questionDAO.getAllQuestions());
        applyFilters();
        updateStatistics();
    }

    private void applyFilters() {
        filteredList.clear();

        String selectedSubject = subjectFilter.getValue();
        String selectedDifficulty = difficultyFilter.getValue();
        String searchText = searchField.getText().toLowerCase().trim();

        // Convert display values to DB values for comparison
        String dbSubject = selectedSubject.equals("Tất cả") ? null : getSubjectDbValue(selectedSubject);
        String dbDifficulty = selectedDifficulty.equals("Tất cả") ? null : getDifficultyDbValue(selectedDifficulty);

        for (Question q : questionList) {
            boolean matchSubject = dbSubject == null || q.getSubject().equals(dbSubject);
            boolean matchDifficulty = dbDifficulty == null || q.getDifficulty().equals(dbDifficulty);
            boolean matchSearch = searchText.isEmpty() ||
                    q.getQuestionText().toLowerCase().contains(searchText);

            if (matchSubject && matchDifficulty && matchSearch) {
                filteredList.add(q);
            }
        }
    }

    private void updateStatistics() {
        int total = questionList.size();
        int math = (int) questionList.stream().filter(q -> "math".equals(q.getSubject())).count();
        int literature = (int) questionList.stream().filter(q -> "literature".equals(q.getSubject())).count();
        int english = (int) questionList.stream().filter(q -> "english".equals(q.getSubject())).count();

        totalQuestionsLabel.setText(String.valueOf(total));
        mathQuestionsLabel.setText(String.valueOf(math));
        literatureQuestionsLabel.setText(String.valueOf(literature));
        englishQuestionsLabel.setText(String.valueOf(english));
    }

    @FXML
    private void handleNewQuestion() {
        editingQuestion = null;
        clearForm();
        saveButton.setText("Thêm câu hỏi");
    }

    @FXML
    private void handleSaveQuestion() {
        if (!validateForm()) {
            showAlert(Alert.AlertType.WARNING, "Thiếu thông tin",
                    "Vui lòng điền đầy đủ thông tin câu hỏi!");
            return;
        }

        if (editingQuestion == null) {
            // Add new question
            Question newQuestion = createQuestionFromForm();
            if (questionDAO.addQuestion(newQuestion)) {
                questionList.add(newQuestion);
                showAlert(Alert.AlertType.INFORMATION, "Thành công", "Đã thêm câu hỏi mới!");
            } else {
                showAlert(Alert.AlertType.ERROR, "Lỗi", "Không thể thêm câu hỏi!");
                return;
            }
        } else {
            // Update existing question
            updateQuestionFromForm(editingQuestion);
            if (questionDAO.updateQuestion(editingQuestion)) {
                showAlert(Alert.AlertType.INFORMATION, "Thành công", "Đã cập nhật câu hỏi!");
            } else {
                showAlert(Alert.AlertType.ERROR, "Lỗi", "Không thể cập nhật câu hỏi!");
                return;
            }
        }

        applyFilters();
        updateStatistics();
        clearForm();
        editingQuestion = null;
        saveButton.setText("Thêm câu hỏi");
        questionTable.refresh();
    }

    @FXML
    private void handleDeleteQuestion() {
        Question selected = questionTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert(Alert.AlertType.WARNING, "Chưa chọn câu hỏi",
                    "Vui lòng chọn câu hỏi cần xóa!");
            return;
        }

        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Xác nhận xóa");
        confirmAlert.setHeaderText("Bạn có chắc muốn xóa câu hỏi này?");
        confirmAlert.setContentText(selected.getQuestionText());

        Optional<ButtonType> result = confirmAlert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            if (questionDAO.deleteQuestion(selected.getQuestionId())) {
                questionList.remove(selected);
                applyFilters();
                updateStatistics();
                clearForm();
                showAlert(Alert.AlertType.INFORMATION, "Thành công", "Đã xóa câu hỏi!");
            } else {
                showAlert(Alert.AlertType.ERROR, "Lỗi", "Không thể xóa câu hỏi!");
            }
        }
    }

    @FXML
    private void handleClearForm() {
        clearForm();
        editingQuestion = null;
        saveButton.setText("Thêm câu hỏi");
        questionTable.getSelectionModel().clearSelection();
    }

    @FXML
    private void handleDeselectQuestion() {
        questionTable.getSelectionModel().clearSelection();
        clearForm();
        editingQuestion = null;
        saveButton.setText("Thêm câu hỏi");
        updateSelectionLabel();
    }

    @FXML
    private void handleImportFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Chọn file Excel/Word");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Excel Files", "*.xlsx", "*.xls"),
                new FileChooser.ExtensionFilter("Word Files", "*.docx", "*.doc"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        File selectedFile = fileChooser.showOpenDialog(null);
        if (selectedFile == null) {
            return;
        }

        // Show loading dialog
        Alert loadingAlert = new Alert(Alert.AlertType.INFORMATION);
        loadingAlert.setTitle("Đang import...");
        loadingAlert.setHeaderText("Vui lòng đợi");
        loadingAlert.setContentText("Đang đọc file: " + selectedFile.getName());
        loadingAlert.show();

        // Import in background thread
        new Thread(() -> {
            try {
                List<Question> importedQuestions;
                String fileName = selectedFile.getName().toLowerCase();

                if (fileName.endsWith(".xlsx") || fileName.endsWith(".xls")) {
                    // Import từ Excel
                    importedQuestions = FileImporter.importFromExcel(selectedFile);
                } else if (fileName.endsWith(".docx") || fileName.endsWith(".doc")) {
                    // Import từ Word
                    importedQuestions = FileImporter.importFromWord(selectedFile);
                } else {
                    importedQuestions = new ArrayList<>();
                    javafx.application.Platform.runLater(() -> {
                        loadingAlert.close();
                        showAlert(Alert.AlertType.ERROR, "Lỗi",
                                "Định dạng file không được hỗ trợ!\nChỉ chấp nhận file .xlsx, .xls, .docx, .doc");
                    });
                    return;
                }

                // Nếu import từ Word, cho phép người dùng chọn môn học và độ khó chung
                if (fileName.endsWith(".docx") || fileName.endsWith(".doc")) {
                    javafx.application.Platform.runLater(() -> {
                        loadingAlert.close();

                        // Create dialog to select subject and difficulty
                        Dialog<javafx.util.Pair<String, String>> dialog = new Dialog<>();
                        dialog.setTitle("Chọn môn học và độ khó");
                        dialog.setHeaderText("Áp dụng cho tất cả câu hỏi từ file Word");

                        ButtonType confirmButtonType = new ButtonType("Xác nhận", ButtonBar.ButtonData.OK_DONE);
                        dialog.getDialogPane().getButtonTypes().addAll(confirmButtonType, ButtonType.CANCEL);

                        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
                        grid.setHgap(10);
                        grid.setVgap(10);
                        grid.setPadding(new javafx.geometry.Insets(20, 150, 10, 10));

                        ComboBox<String> subjectCombo = new ComboBox<>();
                        subjectCombo.setItems(FXCollections.observableArrayList("Toán học", "Ngữ văn", "Tiếng Anh"));
                        subjectCombo.setValue("Toán học");

                        ComboBox<String> difficultyCombo = new ComboBox<>();
                        difficultyCombo.setItems(FXCollections.observableArrayList("Dễ", "Trung bình", "Khó"));
                        difficultyCombo.setValue("Dễ");

                        grid.add(new Label("Môn học:"), 0, 0);
                        grid.add(subjectCombo, 1, 0);
                        grid.add(new Label("Độ khó:"), 0, 1);
                        grid.add(difficultyCombo, 1, 1);

                        dialog.getDialogPane().setContent(grid);

                        dialog.setResultConverter(dialogButton -> {
                            if (dialogButton == confirmButtonType) {
                                return new javafx.util.Pair<>(subjectCombo.getValue(), difficultyCombo.getValue());
                            }
                            return null;
                        });

                        Optional<javafx.util.Pair<String, String>> result = dialog.showAndWait();

                        if (result.isPresent()) {
                            String subject = getSubjectDbValue(result.get().getKey());
                            String difficulty = getDifficultyDbValue(result.get().getValue());

                            // Update all questions with selected subject and difficulty
                            for (Question q : importedQuestions) {
                                q.setSubject(subject);
                                q.setDifficulty(difficulty);
                            }

                            // Save questions
                            saveImportedQuestions(importedQuestions, selectedFile.getName());
                        }
                    });
                } else {
                    // Save Excel questions directly
                    javafx.application.Platform.runLater(() -> {
                        loadingAlert.close();
                        saveImportedQuestions(importedQuestions, selectedFile.getName());
                    });
                }

            } catch (Exception e) {
                e.printStackTrace();
                javafx.application.Platform.runLater(() -> {
                    loadingAlert.close();
                    showAlert(Alert.AlertType.ERROR, "Lỗi import",
                            "Không thể import file!\n\nChi tiết lỗi: " + e.getMessage() +
                                    "\n\nVui lòng kiểm tra định dạng file.");
                });
            }
        }).start();
    }

    /**
     * Save imported questions to database
     */
    private void saveImportedQuestions(List<Question> importedQuestions, String fileName) {
        if (importedQuestions.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Không có dữ liệu",
                    "Không tìm thấy câu hỏi hợp lệ trong file!");
            return;
        }

        // Confirmation dialog
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Xác nhận import");
        confirmAlert.setHeaderText("Đã tìm thấy " + importedQuestions.size() + " câu hỏi");
        confirmAlert.setContentText("Bạn có muốn thêm tất cả câu hỏi này vào hệ thống?");

        Optional<ButtonType> result = confirmAlert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            int successCount = 0;
            int failCount = 0;

            for (Question q : importedQuestions) {
                if (questionDAO.addQuestion(q)) {
                    successCount++;
                } else {
                    failCount++;
                }
            }

            // Update UI
            loadAllQuestions();

            // Show result
            String message = String.format(
                    "Import từ file: %s\n\n" +
                            "✅ Thành công: %d câu hỏi\n" +
                            "❌ Thất bại: %d câu hỏi",
                    fileName, successCount, failCount
            );

            Alert resultAlert = new Alert(
                    failCount == 0 ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING
            );
            resultAlert.setTitle("Kết quả import");
            resultAlert.setHeaderText(null);
            resultAlert.setContentText(message);
            resultAlert.showAndWait();
        }
    }

    @FXML
    private void handleGenerateWithAI() {
        // Validate inputs
        String prompt = aiPromptArea.getText().trim();
        String subject = aiSubjectCombo.getValue();
        String difficulty = aiDifficultyCombo.getValue();
        String quantityStr = aiQuantityField.getText().trim();

        if (prompt.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Thiếu thông tin",
                    "Vui lòng nhập chủ đề/nội dung câu hỏi!");
            return;
        }

        if (subject == null || difficulty == null) {
            showAlert(Alert.AlertType.WARNING, "Thiếu thông tin",
                    "Vui lòng chọn môn học và độ khó!");
            return;
        }

        int quantity;
        try {
            quantity = Integer.parseInt(quantityStr);
            if (quantity <= 0 || quantity > 50) {
                showAlert(Alert.AlertType.WARNING, "Số lượng không hợp lệ",
                        "Vui lòng nhập số lượng từ 1-50!");
                return;
            }
        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.WARNING, "Số lượng không hợp lệ",
                    "Vui lòng nhập số nguyên hợp lệ!");
            return;
        }

        // Convert display values to DB values
        String dbSubject = getSubjectDbValue(subject);
        String dbDifficulty = getDifficultyDbValue(difficulty);

        // Show loading dialog
        Alert loadingAlert = new Alert(Alert.AlertType.INFORMATION);
        loadingAlert.setTitle("Đang tạo câu hỏi với AI...");
        loadingAlert.setHeaderText("Vui lòng đợi");
        loadingAlert.setContentText(String.format(
                "Đang sử dụng Gemini AI để tạo %d câu hỏi về:\n'%s'\n\n" +
                        "Môn: %s | Độ khó: %s\n\n" +
                        "Quá trình này có thể mất 10-30 giây...",
                quantity, prompt, subject, difficulty
        ));

        // Disable button to prevent double click
        Button generateButton = (Button) aiPromptArea.getScene().lookup("#generateAIButton");
        if (generateButton != null) {
            generateButton.setDisable(true);
        }

        loadingAlert.show();

        // Generate questions in background thread
        new Thread(() -> {
            try {
                // Call AI service - FIX: Use AIResult
                AIService.AIResult result = AIService.generateQuestions(
                        prompt, dbSubject, dbDifficulty, quantity
                );

                // Update UI on JavaFX thread
                javafx.application.Platform.runLater(() -> {
                    loadingAlert.close();

                    // Re-enable button
                    if (generateButton != null) {
                        generateButton.setDisable(false);
                    }

                    // FIX: Check for errors first
                    if (result.hasError()) {
                        showAlert(Alert.AlertType.ERROR, "Lỗi tạo câu hỏi",
                                result.getErrorMessage());
                        return;
                    }

                    // FIX: Get questions from result
                    List<Question> generatedQuestions = result.getQuestions();

                    if (generatedQuestions.isEmpty()) {
                        showAlert(Alert.AlertType.ERROR, "Lỗi",
                                "Không thể tạo câu hỏi!\n\n" +
                                        "Nguyên nhân có thể:\n" +
                                        "- API Key không hợp lệ\n" +
                                        "- Không có kết nối internet\n" +
                                        "- Gemini API gặp sự cố\n\n" +
                                        "Vui lòng kiểm tra lại.");
                        return;
                    }

                    // Show preview dialog
                    showAIQuestionsPreview(generatedQuestions, prompt, subject, difficulty);
                });

            } catch (Exception e) {
                e.printStackTrace();
                javafx.application.Platform.runLater(() -> {
                    loadingAlert.close();

                    // Re-enable button
                    if (generateButton != null) {
                        generateButton.setDisable(false);
                    }

                    showAlert(Alert.AlertType.ERROR, "Lỗi tạo câu hỏi",
                            "Đã xảy ra lỗi khi tạo câu hỏi bằng AI!\n\n" +
                                    "Chi tiết lỗi: " + e.getMessage() +
                                    "\n\nVui lòng thử lại hoặc kiểm tra kết nối internet.");
                });
            }
        }).start();
    }

    /**
     * Hiển thị dialog preview và xác nhận câu hỏi từ AI
     */
    private void showAIQuestionsPreview(List<Question> questions, String topic, String subject, String difficulty) {
        // Create dialog
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Xem trước câu hỏi từ AI");
        dialog.setHeaderText(String.format(
                "Đã tạo %d câu hỏi về chủ đề: '%s'\nMôn: %s | Độ khó: %s",
                questions.size(), topic, subject, difficulty
        ));

        // Create content
        javafx.scene.layout.VBox content = new javafx.scene.layout.VBox(10);
        content.setPadding(new javafx.geometry.Insets(20));

        // Add scrollable area
        javafx.scene.control.ScrollPane scrollPane = new javafx.scene.control.ScrollPane();
        javafx.scene.layout.VBox questionsBox = new javafx.scene.layout.VBox(15);
        questionsBox.setPadding(new javafx.geometry.Insets(10));

        // Add each question to preview
        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);

            javafx.scene.layout.VBox questionBox = new javafx.scene.layout.VBox(5);
            questionBox.setStyle("-fx-border-color: #ddd; -fx-border-radius: 5; -fx-padding: 10; -fx-background-color: #f9f9f9;");

            Label titleLabel = new Label("Câu " + (i + 1) + ": " + q.getQuestionText());
            titleLabel.setWrapText(true);
            titleLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");

            Label optionALabel = new Label("A. " + q.getOptionA());
            optionALabel.setWrapText(true);

            Label optionBLabel = new Label("B. " + q.getOptionB());
            optionBLabel.setWrapText(true);

            Label optionCLabel = new Label("C. " + q.getOptionC());
            optionCLabel.setWrapText(true);

            Label optionDLabel = new Label("D. " + q.getOptionD());
            optionDLabel.setWrapText(true);

            Label answerLabel = new Label("✓ Đáp án đúng: " + q.getCorrectAnswer());
            answerLabel.setStyle("-fx-text-fill: green; -fx-font-weight: bold;");

            questionBox.getChildren().addAll(
                    titleLabel,
                    optionALabel, optionBLabel, optionCLabel, optionDLabel,
                    answerLabel
            );

            questionsBox.getChildren().add(questionBox);
        }

        scrollPane.setContent(questionsBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(400);
        scrollPane.setPrefWidth(600);

        content.getChildren().add(scrollPane);

        // Add info label
        Label infoLabel = new Label("💡 Kiểm tra kỹ các câu hỏi trước khi thêm vào hệ thống");
        infoLabel.setStyle("-fx-text-fill: #666; -fx-font-style: italic;");
        content.getChildren().add(infoLabel);

        dialog.getDialogPane().setContent(content);

        // Add buttons
        ButtonType addAllButton = new ButtonType("Thêm tất cả", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButton = new ButtonType("Hủy bỏ", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(addAllButton, cancelButton);

        // Handle result
        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == addAllButton) {
            saveAIQuestions(questions, topic);
        }
    }

    /**
     * Lưu câu hỏi từ AI vào database
     */
    private void saveAIQuestions(List<Question> questions, String topic) {
        int successCount = 0;
        int failCount = 0;

        for (Question q : questions) {
            if (questionDAO.addQuestion(q)) {
                successCount++;
            } else {
                failCount++;
            }
        }

        // Update UI
        loadAllQuestions();

        // Clear AI form
        aiPromptArea.clear();
        aiQuantityField.setText("5");

        // Show result
        String message = String.format(
                "Tạo câu hỏi bằng AI - Chủ đề: '%s'\n\n" +
                        "✅ Thành công: %d câu hỏi\n" +
                        "❌ Thất bại: %d câu hỏi\n\n" +
                        "Các câu hỏi đã được thêm vào hệ thống!",
                topic, successCount, failCount
        );

        Alert resultAlert = new Alert(
                failCount == 0 ? Alert.AlertType.INFORMATION : Alert.AlertType.WARNING
        );
        resultAlert.setTitle("Kết quả tạo câu hỏi");
        resultAlert.setHeaderText(null);
        resultAlert.setContentText(message);
        resultAlert.showAndWait();
    }

    /**
     * Kiểm tra kết nối AI (optional - có thể gọi khi khởi động)
     */
    @FXML
    private void handleTestAIConnection() {
        Alert loadingAlert = new Alert(Alert.AlertType.INFORMATION);
        loadingAlert.setTitle("Kiểm tra kết nối");
        loadingAlert.setHeaderText("Đang kiểm tra kết nối với Gemini AI...");
        loadingAlert.setContentText("Vui lòng đợi...");
        loadingAlert.show();

        new Thread(() -> {
            boolean connected = AIService.testConnection();

            javafx.application.Platform.runLater(() -> {
                loadingAlert.close();

                if (connected) {
                    showAlert(Alert.AlertType.INFORMATION, "Kết nối thành công",
                            "✅ Đã kết nối thành công với Gemini AI!\n\n" +
                                    "Bạn có thể sử dụng tính năng tạo câu hỏi tự động.");
                } else {
                    showAlert(Alert.AlertType.ERROR, "Kết nối thất bại",
                            "❌ Không thể kết nối với Gemini AI!\n\n" +
                                    "Vui lòng kiểm tra:\n" +
                                    "- API Key có đúng không\n" +
                                    "- Kết nối internet\n" +
                                    "- Gemini API có hoạt động không");
                }
            });
        }).start();
    }



    @FXML
    private void handleDownloadSample() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Lưu file mẫu");
        fileChooser.setInitialFileName("mau_cau_hoi.xlsx");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Excel Files", "*.xlsx")
        );

        File file = fileChooser.showSaveDialog(null);
        if (file != null) {
            try {
                SampleFileGenerator.generateSampleExcel(file);
                showAlert(Alert.AlertType.INFORMATION, "Thành công",
                        "Đã tải file mẫu thành công!\n\nĐường dẫn: " + file.getAbsolutePath());
            } catch (Exception e) {
                showAlert(Alert.AlertType.ERROR, "Lỗi",
                        "Không thể tạo file mẫu!\n\n" + e.getMessage());
            }
        }
    }

    @FXML
    private void handleBackToServer() {
//        try {
//            // Load ServerView.fxml
//            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
//                    getClass().getResource("/fxml/ServerView.fxml")
//            );
//            javafx.scene.Parent root = loader.load();
//
//            // Get current stage and set new scene
//            javafx.stage.Stage stage = (javafx.stage.Stage) saveButton.getScene().getWindow();
//            stage.setScene(new javafx.scene.Scene(root));
//            stage.setTitle("Server Management");
//        } catch (Exception e) {
//            System.err.println("Error loading ServerView: " + e.getMessage());
//            e.printStackTrace();
//            showAlert(Alert.AlertType.ERROR, "Lỗi",
//                    "Không thể quay lại trang Server. Vui lòng kiểm tra file ServerView.fxml!");
//        }

        Stage stage = (Stage) saveButton.getScene().getWindow();
        stage.close();
    }

    private Question createQuestionFromForm() {
        Question q = new Question();
        updateQuestionFromForm(q);
        q.setPoints(10);
        q.setTimeLimit(30);
        q.setCreatedBy(1);
        q.setActive(true);
        return q;
    }

    private void updateQuestionFromForm(Question q) {
        // Convert display values to DB values
        q.setSubject(getSubjectDbValue(subjectCombo.getValue()));
        q.setDifficulty(getDifficultyDbValue(difficultyCombo.getValue()));
        q.setQuestionText(questionContentArea.getText().trim());
        q.setOptionA(answerAField.getText().trim());
        q.setOptionB(answerBField.getText().trim());
        q.setOptionC(answerCField.getText().trim());
        q.setOptionD(answerDField.getText().trim());
        q.setCorrectAnswer(correctAnswerCombo.getValue());
    }

    private void fillFormWithQuestion(Question q) {
        editingQuestion = q;
        questionContentArea.setText(q.getQuestionText());
        // Convert DB values to display values
        subjectCombo.setValue(getSubjectDisplayValue(q.getSubject()));
        difficultyCombo.setValue(getDifficultyDisplayValue(q.getDifficulty()));
        answerAField.setText(q.getOptionA());
        answerBField.setText(q.getOptionB());
        answerCField.setText(q.getOptionC());
        answerDField.setText(q.getOptionD());
        correctAnswerCombo.setValue(q.getCorrectAnswer());
        saveButton.setText("Cập nhật câu hỏi");
    }

    private void clearForm() {
        questionContentArea.clear();
        subjectCombo.setValue(null);
        difficultyCombo.setValue(null);
        answerAField.clear();
        answerBField.clear();
        answerCField.clear();
        answerDField.clear();
        correctAnswerCombo.setValue(null);
    }

    private boolean validateForm() {
        return questionContentArea.getText() != null && !questionContentArea.getText().trim().isEmpty()
                && subjectCombo.getValue() != null
                && difficultyCombo.getValue() != null
                && answerAField.getText() != null && !answerAField.getText().trim().isEmpty()
                && answerBField.getText() != null && !answerBField.getText().trim().isEmpty()
                && answerCField.getText() != null && !answerCField.getText().trim().isEmpty()
                && answerDField.getText() != null && !answerDField.getText().trim().isEmpty()
                && correctAnswerCombo.getValue() != null;
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(content);
        alert.showAndWait();
    }
}