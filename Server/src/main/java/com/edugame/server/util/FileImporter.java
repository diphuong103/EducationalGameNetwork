package com.edugame.server.util;

import com.edugame.server.model.Question;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.*;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class để import câu hỏi từ file Excel và Word
 */
public class FileImporter {

    // Subject mapping
    private static final java.util.Map<String, String> SUBJECT_MAP = new java.util.HashMap<>();
    static {
        SUBJECT_MAP.put("toán học", "math");
        SUBJECT_MAP.put("toán", "math");
        SUBJECT_MAP.put("ngữ văn", "literature");
        SUBJECT_MAP.put("văn", "literature");
        SUBJECT_MAP.put("tiếng anh", "english");
        SUBJECT_MAP.put("anh", "english");
    }

    // Difficulty mapping
    private static final java.util.Map<String, String> DIFFICULTY_MAP = new java.util.HashMap<>();
    static {
        DIFFICULTY_MAP.put("dễ", "easy");
        DIFFICULTY_MAP.put("de", "easy");
        DIFFICULTY_MAP.put("trung bình", "medium");
        DIFFICULTY_MAP.put("tb", "medium");
        DIFFICULTY_MAP.put("khó", "hard");
        DIFFICULTY_MAP.put("kho", "hard");
    }

    /**
     * Import câu hỏi từ file Excel
     * Format: Môn học | Độ khó | Câu hỏi | A | B | C | D | Đáp án đúng
     */
    public static List<Question> importFromExcel(File file) throws Exception {
        List<Question> questions = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(file);
             Workbook workbook = new XSSFWorkbook(fis)) {

            Sheet sheet = workbook.getSheetAt(0);
            int rowCount = 0;

            for (Row row : sheet) {
                // Skip header row
                if (rowCount == 0) {
                    rowCount++;
                    continue;
                }

                // Skip empty rows
                if (isRowEmpty(row)) {
                    continue;
                }

                try {
                    Question question = parseExcelRow(row, rowCount);
                    if (question != null && question.isValid()) {
                        questions.add(question);
                    } else {
                        System.err.println("⚠️ Dòng " + (rowCount + 1) + ": Câu hỏi không hợp lệ, bỏ qua");
                    }
                } catch (Exception e) {
                    System.err.println("❌ Lỗi tại dòng " + (rowCount + 1) + ": " + e.getMessage());
                }

                rowCount++;
            }
        }

        System.out.println("✅ Đã import " + questions.size() + " câu hỏi từ Excel");
        return questions;
    }

    /**
     * Parse một dòng Excel thành Question object
     */
    private static Question parseExcelRow(Row row, int rowNum) {
        try {
            // Cột 1: Môn học
            String subjectDisplay = getCellValue(row.getCell(0)).trim().toLowerCase();
            String subject = SUBJECT_MAP.getOrDefault(subjectDisplay, null);
            if (subject == null) {
                throw new IllegalArgumentException("Môn học không hợp lệ: " + subjectDisplay);
            }

            // Cột 2: Độ khó
            String difficultyDisplay = getCellValue(row.getCell(1)).trim().toLowerCase();
            String difficulty = DIFFICULTY_MAP.getOrDefault(difficultyDisplay, null);
            if (difficulty == null) {
                throw new IllegalArgumentException("Độ khó không hợp lệ: " + difficultyDisplay);
            }

            // Cột 3: Câu hỏi
            String questionText = getCellValue(row.getCell(2)).trim();
            if (questionText.isEmpty()) {
                throw new IllegalArgumentException("Nội dung câu hỏi trống");
            }

            // Cột 4-7: Đáp án A, B, C, D
            String optionA = getCellValue(row.getCell(3)).trim();
            String optionB = getCellValue(row.getCell(4)).trim();
            String optionC = getCellValue(row.getCell(5)).trim();
            String optionD = getCellValue(row.getCell(6)).trim();

            if (optionA.isEmpty() || optionB.isEmpty() || optionC.isEmpty() || optionD.isEmpty()) {
                throw new IllegalArgumentException("Các đáp án không được để trống");
            }

            // Cột 8: Đáp án đúng
            String correctAnswer = getCellValue(row.getCell(7)).trim().toUpperCase();
            if (!correctAnswer.matches("[ABCD]")) {
                throw new IllegalArgumentException("Đáp án đúng phải là A, B, C hoặc D");
            }

            // Tạo Question object
            Question question = new Question();
            question.setSubject(subject);
            question.setDifficulty(difficulty);
            question.setQuestionText(questionText);
            question.setOptionA(optionA);
            question.setOptionB(optionB);
            question.setOptionC(optionC);
            question.setOptionD(optionD);
            question.setCorrectAnswer(correctAnswer);
            question.setPoints(10);
            question.setTimeLimit(30);
            question.setCreatedBy(1);
            question.setActive(true);

            return question;

        } catch (Exception e) {
            System.err.println("❌ Lỗi parse dòng " + (rowNum + 1) + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Import câu hỏi từ file Word
     * Format:
     * Câu hỏi?
     * A. Đáp án A
     * B. Đáp án B
     * C. Đáp án C
     * D. Đáp án D
     * Đáp án đúng: X
     */
    public static List<Question> importFromWord(File file) throws Exception {
        List<Question> questions = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(file);
             XWPFDocument document = new XWPFDocument(fis)) {

            List<XWPFParagraph> paragraphs = document.getParagraphs();

            int i = 0;
            while (i < paragraphs.size()) {
                String line = paragraphs.get(i).getText().trim();

                // Bỏ qua dòng trống
                if (line.isEmpty()) {
                    i++;
                    continue;
                }

                // Tìm câu hỏi (dòng có dấu ?)
                if (line.contains("?")) {
                    try {
                        Question question = parseWordQuestion(paragraphs, i);
                        if (question != null && question.isValid()) {
                            questions.add(question);
                        }
                        i += 7; // Skip 7 dòng (câu hỏi + 4 đáp án + đáp án đúng + dòng trống)
                    } catch (Exception e) {
                        System.err.println("❌ Lỗi parse câu hỏi tại dòng " + (i + 1) + ": " + e.getMessage());
                        i++;
                    }
                } else {
                    i++;
                }
            }
        }

        System.out.println("✅ Đã import " + questions.size() + " câu hỏi từ Word");
        return questions;
    }

    /**
     * Parse một câu hỏi từ Word document
     */
    private static Question parseWordQuestion(List<XWPFParagraph> paragraphs, int startIndex) throws Exception {
        if (startIndex + 5 >= paragraphs.size()) {
            throw new IllegalArgumentException("Không đủ dòng để tạo câu hỏi đầy đủ");
        }

        // Câu hỏi
        String questionText = paragraphs.get(startIndex).getText().trim();

        // Đáp án A, B, C, D
        String optionA = extractOption(paragraphs.get(startIndex + 1).getText(), "A");
        String optionB = extractOption(paragraphs.get(startIndex + 2).getText(), "B");
        String optionC = extractOption(paragraphs.get(startIndex + 3).getText(), "C");
        String optionD = extractOption(paragraphs.get(startIndex + 4).getText(), "D");

        // Đáp án đúng
        String correctAnswerLine = paragraphs.get(startIndex + 5).getText().trim();
        String correctAnswer = extractCorrectAnswer(correctAnswerLine);

        if (correctAnswer == null) {
            throw new IllegalArgumentException("Không tìm thấy đáp án đúng");
        }

        // Mặc định môn học và độ khó (có thể điều chỉnh)
        String subject = "math";
        String difficulty = "easy";

        // Tạo Question object
        Question question = new Question();
        question.setSubject(subject);
        question.setDifficulty(difficulty);
        question.setQuestionText(questionText);
        question.setOptionA(optionA);
        question.setOptionB(optionB);
        question.setOptionC(optionC);
        question.setOptionD(optionD);
        question.setCorrectAnswer(correctAnswer);
        question.setPoints(10);
        question.setTimeLimit(30);
        question.setCreatedBy(1);
        question.setActive(true);

        return question;
    }

    /**
     * Trích xuất nội dung đáp án từ dòng text (ví dụ: "A. Đáp án A" -> "Đáp án A")
     */
    private static String extractOption(String line, String optionLetter) throws Exception {
        // Pattern: A. hoặc A) hoặc A:
        Pattern pattern = Pattern.compile("^" + optionLetter + "[.):)]\\s*(.+)$", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(line.trim());

        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        throw new IllegalArgumentException("Không thể parse đáp án " + optionLetter + " từ: " + line);
    }

    /**
     * Trích xuất đáp án đúng từ dòng text
     * Ví dụ: "Đáp án đúng: A" hoặc "Đáp án: B" hoặc "Answer: C"
     */
    private static String extractCorrectAnswer(String line) {
        Pattern pattern = Pattern.compile("(?:đáp án đúng|đáp án|answer)\\s*[:：]?\\s*([ABCD])", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(line);

        if (matcher.find()) {
            return matcher.group(1).toUpperCase();
        }

        return null;
    }

    /**
     * Lấy giá trị cell dưới dạng String
     */
    private static String getCellValue(Cell cell) {
        if (cell == null) {
            return "";
        }

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                // Kiểm tra nếu là số nguyên
                if (cell.getNumericCellValue() % 1 == 0) {
                    return String.valueOf((int) cell.getNumericCellValue());
                }
                return String.valueOf(cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                return cell.getCellFormula();
            default:
                return "";
        }
    }

    /**
     * Kiểm tra xem row có rỗng không
     */
    private static boolean isRowEmpty(Row row) {
        if (row == null) {
            return true;
        }

        for (int i = 0; i < 8; i++) {
            Cell cell = row.getCell(i);
            if (cell != null && !getCellValue(cell).trim().isEmpty()) {
                return false;
            }
        }

        return true;
    }
}