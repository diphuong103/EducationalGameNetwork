package com.edugame.server.database;

import com.edugame.server.model.Question;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class QuestionDAO {

    /**
     * Lấy danh sách câu hỏi ngẫu nhiên theo môn học và độ khó
     */
    public List<Question> getRandomQuestions(String subject, String difficulty, int limit) {
        List<Question> questions = new ArrayList<>();

        String query = """
            SELECT * FROM questions
            WHERE subject = ? AND difficulty = ? AND is_active = 1
            ORDER BY RAND()
            LIMIT ?
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, subject);
            stmt.setString(2, difficulty);
            stmt.setInt(3, limit);

            ResultSet rs = stmt.executeQuery();

            while (rs.next()) {
                questions.add(mapResultSetToQuestion(rs));
            }

        } catch (SQLException e) {
            System.err.println("❌ [QuestionDAO] Error getting random questions: " + e.getMessage());
            e.printStackTrace();
        }

        return questions;
    }

    /**
     * Lấy câu hỏi theo ID
     */
    public Question getQuestionById(int questionId) {
        String query = "SELECT * FROM questions WHERE question_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, questionId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return mapResultSetToQuestion(rs);
            }

        } catch (SQLException e) {
            System.err.println("❌ [QuestionDAO] Error getting question by ID: " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Thêm câu hỏi mới
     */
    public boolean addQuestion(Question q) {
        String query = """
            INSERT INTO questions (
                subject, difficulty, question_text, 
                option_a, option_b, option_c, option_d, 
                correct_answer, explanation, points, time_limit, created_by, is_active
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, q.getSubject());
            stmt.setString(2, q.getDifficulty());
            stmt.setString(3, q.getQuestion());
            stmt.setString(4, q.getOptionA());
            stmt.setString(5, q.getOptionB());
            stmt.setString(6, q.getOptionC());
            stmt.setString(7, q.getOptionD());
            stmt.setString(8, q.getCorrectAnswer());
            stmt.setString(9, q.getExplanation());
            stmt.setInt(10, q.getPoints());
            stmt.setInt(11, q.getTimeLimit());
            stmt.setInt(12, q.getCreatedBy());
            stmt.setBoolean(13, q.isActive());

            int rows = stmt.executeUpdate();

            if (rows > 0) {
                ResultSet rs = stmt.getGeneratedKeys();
                if (rs.next()) {
                    int newId = rs.getInt(1);
                    q.setQuestionId(newId);
                }
                System.out.println("✅ [QuestionDAO] Added new question (ID=" + q.getQuestionId() + ")");
                return true;
            }

        } catch (SQLException e) {
            System.err.println("❌ [QuestionDAO] Error adding question: " + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Cập nhật nội dung câu hỏi
     */
    public boolean updateQuestion(Question q) {
        String query = """
            UPDATE questions
            SET subject = ?, difficulty = ?, question_text = ?,
                option_a = ?, option_b = ?, option_c = ?, option_d = ?,
                correct_answer = ?, explanation = ?, points = ?, time_limit = ?, is_active = ?
            WHERE question_id = ?
        """;

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, q.getSubject());
            stmt.setString(2, q.getDifficulty());
            stmt.setString(3, q.getQuestion());
            stmt.setString(4, q.getOptionA());
            stmt.setString(5, q.getOptionB());
            stmt.setString(6, q.getOptionC());
            stmt.setString(7, q.getOptionD());
            stmt.setString(8, q.getCorrectAnswer());
            stmt.setString(9, q.getExplanation());
            stmt.setInt(10, q.getPoints());
            stmt.setInt(11, q.getTimeLimit());
            stmt.setBoolean(12, q.isActive());
            stmt.setInt(13, q.getQuestionId());

            int rows = stmt.executeUpdate();
            System.out.println("✅ [QuestionDAO] Updated question ID=" + q.getQuestionId() + " (" + rows + " rows)");
            return rows > 0;

        } catch (SQLException e) {
            System.err.println("❌ [QuestionDAO] Error updating question: " + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Xóa câu hỏi
     */
    public boolean deleteQuestion(int questionId) {
        String query = "DELETE FROM questions WHERE question_id = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, questionId);
            int rows = stmt.executeUpdate();

            System.out.println("🗑️ [QuestionDAO] Deleted question ID=" + questionId + " (" + rows + " rows)");
            return rows > 0;

        } catch (SQLException e) {
            System.err.println("❌ [QuestionDAO] Error deleting question: " + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Chuyển đổi từ ResultSet → Question object
     */
    private Question mapResultSetToQuestion(ResultSet rs) throws SQLException {
        Question q = new Question();
        q.setQuestionId(rs.getInt("question_id"));
        q.setSubject(rs.getString("subject"));
        q.setDifficulty(rs.getString("difficulty"));
        q.setQuestion(rs.getString("question_text"));
        q.setOptionA(rs.getString("option_a"));
        q.setOptionB(rs.getString("option_b"));
        q.setOptionC(rs.getString("option_c"));
        q.setOptionD(rs.getString("option_d"));
        q.setCorrectAnswer(rs.getString("correct_answer"));
        q.setExplanation(rs.getString("explanation"));
        q.setPoints(rs.getInt("points"));
        q.setTimeLimit(rs.getInt("time_limit"));
        q.setCreatedBy(rs.getInt("created_by"));
        q.setActive(rs.getBoolean("is_active"));
        return q;
    }
}
