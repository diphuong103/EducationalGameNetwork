package com.edugame.server.controller;

import com.edugame.server.database.UserDAO;
import javafx.beans.property.*;

/**
 * JavaFX Property wrapper class for displaying User data in TableView
 */
public class UserTableRow {

    // Properties for TableView binding
    private final IntegerProperty userId;
    private final StringProperty username;
    private final StringProperty fullName;
    private final StringProperty email;
    private final IntegerProperty age;
    private final StringProperty statusText;
    private final IntegerProperty totalScore;
    private final IntegerProperty mathScore;
    private final IntegerProperty englishScore;
    private final IntegerProperty literatureScore;
    private final IntegerProperty totalGames;
    private final IntegerProperty wins;
    private final StringProperty winRateText;

    /**
     * Constructor from UserDAO.PlayerInfo
     */
    public UserTableRow(UserDAO.PlayerInfo player) {
        this.userId = new SimpleIntegerProperty(player.userId);
        this.username = new SimpleStringProperty(player.username != null ? player.username : "");
        this.fullName = new SimpleStringProperty(player.fullName != null ? player.fullName : "");
        this.email = new SimpleStringProperty(player.email != null ? player.email : "");
        this.age = new SimpleIntegerProperty(player.age);

        // Determine status (you'll need to check against connected clients)
        this.statusText = new SimpleStringProperty("Offline"); // Default

        this.totalScore = new SimpleIntegerProperty(player.totalScore);
        this.mathScore = new SimpleIntegerProperty(player.mathScore);
        this.englishScore = new SimpleIntegerProperty(player.englishScore);
        this.literatureScore = new SimpleIntegerProperty(player.literatureScore);
        this.totalGames = new SimpleIntegerProperty(player.totalGames);
        this.wins = new SimpleIntegerProperty(player.wins);

        // Calculate win rate
        double winRate = player.getWinRate();
        this.winRateText = new SimpleStringProperty(String.format("%.1f%%", winRate));

        System.out.println("📦 Created UserTableRow: " + this.username.get() +
                " | Score: " + this.totalScore.get() +
                " | Email: " + this.email.get());
    }

    // ==================== GETTERS for PropertyValueFactory ====================
    // CRITICAL: PropertyValueFactory cần getter methods với tên chính xác

    public Integer getUserId() {
        return userId.get();
    }

    public IntegerProperty userIdProperty() {
        return userId;
    }

    public String getUsername() {
        return username.get();
    }

    public StringProperty usernameProperty() {
        return username;
    }

    public String getFullName() {
        return fullName.get();
    }

    public StringProperty fullNameProperty() {
        return fullName;
    }

    public String getEmail() {
        return email.get();
    }

    public StringProperty emailProperty() {
        return email;
    }

    public Integer getAge() {
        return age.get();
    }

    public IntegerProperty ageProperty() {
        return age;
    }

    public String getStatusText() {
        return statusText.get();
    }

    public StringProperty statusTextProperty() {
        return statusText;
    }

    public void setStatusText(String status) {
        this.statusText.set(status);
    }

    public Integer getTotalScore() {
        return totalScore.get();
    }

    public IntegerProperty totalScoreProperty() {
        return totalScore;
    }

    public Integer getMathScore() {
        return mathScore.get();
    }

    public IntegerProperty mathScoreProperty() {
        return mathScore;
    }

    public Integer getEnglishScore() {
        return englishScore.get();
    }

    public IntegerProperty englishScoreProperty() {
        return englishScore;
    }

    public Integer getLiteratureScore() {
        return literatureScore.get();
    }

    public IntegerProperty literatureScoreProperty() {
        return literatureScore;
    }

    public Integer getTotalGames() {
        return totalGames.get();
    }

    public IntegerProperty totalGamesProperty() {
        return totalGames;
    }

    public Integer getWins() {
        return wins.get();
    }

    public IntegerProperty winsProperty() {
        return wins;
    }

    public String getWinRateText() {
        return winRateText.get();
    }

    public StringProperty winRateTextProperty() {
        return winRateText;
    }

    // ==================== HELPER METHODS ====================

    /**
     * Get win rate as double (0-100)
     */
    public double getWinRate() {
        if (totalGames.get() == 0) return 0.0;
        return (wins.get() * 100.0) / totalGames.get();
    }

    /**
     * Check if user is online
     */
    public boolean isOnline() {
        return "Online".equals(statusText.get());
    }

    @Override
    public String toString() {
        return String.format("UserTableRow{userId=%d, username='%s', status='%s', score=%d}",
                userId.get(), username.get(), statusText.get(), totalScore.get());
    }
}