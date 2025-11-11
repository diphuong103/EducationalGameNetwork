package com.edugame.server.model;

import java.sql.Timestamp;

public class Question {
    private int questionId;
    private String subject;         // math, english, science
    private String difficulty;      // easy, medium, hard
    private String questionText;
    private String optionA;
    private String optionB;
    private String optionC;
    private String optionD;
    private String correctAnswer;   // 'A', 'B', 'C', 'D'
    private String explanation;
    private int points;
    private int timeLimit;
    private int createdBy;
    private boolean isActive;
    private Timestamp createdAt;

    // Constructors
    public Question() {
        this.isActive = true;
        this.points = 10;
        this.timeLimit = 10;
        this.createdAt = new Timestamp(System.currentTimeMillis());
    }

    public Question(String subject, String difficulty, String questionText,
                    String optionA, String optionB, String optionC, String optionD,
                    String correctAnswer, String explanation) {
        this();
        this.subject = subject;
        this.difficulty = difficulty;
        this.questionText = questionText;
        this.optionA = optionA;
        this.optionB = optionB;
        this.optionC = optionC;
        this.optionD = optionD;
        this.correctAnswer = correctAnswer;
        this.explanation = explanation;
    }

    // Getters & Setters
    public int getQuestionId() { return questionId; }
    public void setQuestionId(int questionId) { this.questionId = questionId; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }

    public String getQuestionText() { return questionText; }
    public void setQuestionText(String questionText) { this.questionText = questionText; }

    public String getOptionA() { return optionA; }
    public void setOptionA(String optionA) { this.optionA = optionA; }

    public String getOptionB() { return optionB; }
    public void setOptionB(String optionB) { this.optionB = optionB; }

    public String getOptionC() { return optionC; }
    public void setOptionC(String optionC) { this.optionC = optionC; }

    public String getOptionD() { return optionD; }
    public void setOptionD(String optionD) { this.optionD = optionD; }

    public String getCorrectAnswer() { return correctAnswer; }
    public void setCorrectAnswer(String correctAnswer) { this.correctAnswer = correctAnswer; }

    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }

    public int getPoints() { return points; }
    public void setPoints(int points) { this.points = points; }

    public int getTimeLimit() { return timeLimit; }
    public void setTimeLimit(int timeLimit) { this.timeLimit = timeLimit; }

    public int getCreatedBy() { return createdBy; }
    public void setCreatedBy(int createdBy) { this.createdBy = createdBy; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    // Helper methods
    public String[] getOptions() {
        return new String[] { optionA, optionB, optionC, optionD };
    }

    public boolean isCorrect(String answer) {
        return correctAnswer != null && correctAnswer.equalsIgnoreCase(answer);
    }

    public boolean isValid() {
        return questionText != null && !questionText.isEmpty() &&
                optionA != null && !optionA.isEmpty() &&
                optionB != null && !optionB.isEmpty() &&
                optionC != null && !optionC.isEmpty() &&
                optionD != null && !optionD.isEmpty() &&
                correctAnswer != null && correctAnswer.matches("[ABCD]");
    }

    @Override
    public String toString() {
        return "Question{" +
                "id=" + questionId +
                ", subject='" + subject + '\'' +
                ", difficulty='" + difficulty + '\'' +
                ", correctAnswer='" + correctAnswer + '\'' +
                ", points=" + points +
                ", timeLimit=" + timeLimit +
                '}';
    }
}
