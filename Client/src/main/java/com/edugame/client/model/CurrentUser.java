package com.edugame.client.model;

public class CurrentUser {
    private static User user;

    public static void setUser(User u) {
        user = u;
    }

    public static User getUser() {
        return user;
    }
}
