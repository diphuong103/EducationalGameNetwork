package com.edugame.server.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Tạo file Excel mẫu để hướng dẫn người dùng
 */
public class SampleFileGenerator {

    public static void generateSampleExcel(File outputFile) throws Exception {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Câu hỏi mẫu");

            // Create header style
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 12);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setAlignment(HorizontalAlignment.CENTER);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            // Create header row
            Row headerRow = sheet.createRow(0);
            String[] headers = {
                    "Môn học", "Độ khó", "Nội dung câu hỏi",
                    "Đáp án A", "Đáp án B", "Đáp án C", "Đáp án D",
                    "Đáp án đúng"
            };

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Add sample data
            addSampleRow(sheet, 1, "Toán học", "Dễ",
                    "2 + 2 = ?",
                    "3", "4", "5", "6",
                    "B");

            addSampleRow(sheet, 2, "Toán học", "Trung bình",
                    "Tính đạo hàm của hàm số y = x²",
                    "y' = x", "y' = 2x", "y' = x² + 1", "y' = 2",
                    "B");

            addSampleRow(sheet, 3, "Ngữ văn", "Dễ",
                    "Tác giả của tác phẩm 'Truyện Kiều' là ai?",
                    "Nguyễn Du", "Hồ Xuân Hương", "Nguyễn Trãi", "Cao Bá Quát",
                    "A");

            addSampleRow(sheet, 4, "Tiếng Anh", "Dễ",
                    "What is the capital of Vietnam?",
                    "Ho Chi Minh City", "Hanoi", "Da Nang", "Hue",
                    "B");

            addSampleRow(sheet, 5, "Tiếng Anh", "Khó",
                    "Choose the correct form: 'She ___ to the store yesterday.'",
                    "go", "goes", "went", "going",
                    "C");

            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
                // Add extra width for readability
                sheet.setColumnWidth(i, sheet.getColumnWidth(i) + 1000);
            }

            // Set column width for question column
            sheet.setColumnWidth(2, 15000); // Question column wider

            // Write to file
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                workbook.write(fos);
            }

            System.out.println("✅ Đã tạo file Excel mẫu: " + outputFile.getAbsolutePath());
        }
    }

    private static void addSampleRow(Sheet sheet, int rowNum,
                                     String subject, String difficulty, String question,
                                     String optionA, String optionB, String optionC, String optionD,
                                     String correctAnswer) {
        Row row = sheet.createRow(rowNum);

        row.createCell(0).setCellValue(subject);
        row.createCell(1).setCellValue(difficulty);
        row.createCell(2).setCellValue(question);
        row.createCell(3).setCellValue(optionA);
        row.createCell(4).setCellValue(optionB);
        row.createCell(5).setCellValue(optionC);
        row.createCell(6).setCellValue(optionD);
        row.createCell(7).setCellValue(correctAnswer);
    }

    public static void main(String[] args) {
        try {
            File outputFile = new File("sample_questions.xlsx");
            generateSampleExcel(outputFile);
            System.out.println("File mẫu đã được tạo thành công!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}