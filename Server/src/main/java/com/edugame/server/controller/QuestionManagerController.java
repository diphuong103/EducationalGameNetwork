package com.edugame.server.controller;

import com.edugame.server.database.QuestionDAO;
import com.edugame.server.model.Question;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.stage.FileChooser;

import java.io.File;
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
        if (selectedFile != null) {
            // TODO: Implement file import logic
            showAlert(Alert.AlertType.INFORMATION, "Import file",
                    "Chức năng import từ file đang được phát triển.\nFile đã chọn: " + selectedFile.getName());
        }
    }

    @FXML
    private void handleGenerateWithAI() {
        String prompt = aiPromptArea.getText().trim();
        String subject = aiSubjectCombo.getValue();
        String difficulty = aiDifficultyCombo.getValue();
        String quantityStr = aiQuantityField.getText().trim();

        if (prompt.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Thiếu thông tin",
                    "Vui lòng nhập chủ đề/nội dung câu hỏi!");
            return;
        }

        try {
            int quantity = Integer.parseInt(quantityStr);
            if (quantity <= 0 || quantity > 50) {
                showAlert(Alert.AlertType.WARNING, "Số lượng không hợp lệ",
                        "Vui lòng nhập số lượng từ 1-50!");
                return;
            }

            // Convert to DB values
            String dbSubject = getSubjectDbValue(subject);
            String dbDifficulty = getDifficultyDbValue(difficulty);

            // TODO: Implement AI generation logic with dbSubject and dbDifficulty
            showAlert(Alert.AlertType.INFORMATION, "Tạo câu hỏi bằng AI",
                    String.format("Chức năng tạo câu hỏi bằng AI đang được phát triển.\n\n" +
                                    "Thông tin:\n" +
                                    "- Môn học: %s (%s)\n" +
                                    "- Độ khó: %s (%s)\n" +
                                    "- Số lượng: %d câu\n" +
                                    "- Chủ đề: %s",
                            subject, dbSubject, difficulty, dbDifficulty, quantity, prompt));

        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.WARNING, "Số lượng không hợp lệ",
                    "Vui lòng nhập số nguyên hợp lệ!");
        }
    }

    @FXML
    private void handleBackToServer() {
        try {
            // Load ServerView.fxml
            javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
                    getClass().getResource("/com/edugame/server/view/ServerView.fxml")
            );
            javafx.scene.Parent root = loader.load();

            // Get current stage and set new scene
            javafx.stage.Stage stage = (javafx.stage.Stage) saveButton.getScene().getWindow();
            stage.setScene(new javafx.scene.Scene(root));
            stage.setTitle("Server Management");
        } catch (Exception e) {
            System.err.println("Error loading ServerView: " + e.getMessage());
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Lỗi",
                    "Không thể quay lại trang Server. Vui lòng kiểm tra file ServerView.fxml!");
        }
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