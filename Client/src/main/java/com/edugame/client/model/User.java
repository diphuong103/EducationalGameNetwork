package com.edugame.client.model;

public class User {
    private int userId;
    private String username;
    private String fullName;
    private String avatarUrl;
    private int totalScore;
    private boolean isOnline;

    // ---- Thêm các field mới ----
    private int totalGames;
    private int wins;
    private int mathScore;
    private int englishScore;
    private int literatureScore;
    private String email;
    private int age; // nếu có hoặc giả định sau này thêm

    // ---- Getter & Setter ----
    public int getUserId() { return userId; }
    public void setUserId(int userId) { this.userId = userId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public int getTotalScore() { return totalScore; }
    public void setTotalScore(int totalScore) { this.totalScore = totalScore; }

    public boolean isOnline() { return isOnline; }
    public void setOnline(boolean online) { isOnline = online; }

    public int getTotalGames() { return totalGames; }
    public void setTotalGames(int totalGames) { this.totalGames = totalGames; }

    public int getWins() { return wins; }
    public void setWins(int wins) { this.wins = wins; }

    public int getMathScore() { return mathScore; }
    public void setMathScore(int mathScore) { this.mathScore = mathScore; }

    public int getEnglishScore() { return englishScore; }
    public void setEnglishScore(int englishScore) { this.englishScore = englishScore; }

    public int getLiteratureScore() { return literatureScore; }
    public void setLiteratureScore(int literatureScore) { this.literatureScore = literatureScore; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }
}
