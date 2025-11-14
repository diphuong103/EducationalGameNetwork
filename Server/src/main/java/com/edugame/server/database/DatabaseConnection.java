package com.edugame.server.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {
    private static DatabaseConnection instance;

    // Database configuration
    private static final String DB_HOST = "localhost";
    private static final String DB_PORT = "3306";
    private static final String DB_NAME = "educational_game_db";
    private static final String DB_USER = "root";
    private static final String DB_PASSWORD = "533464";

    private static final String DB_URL = "jdbc:mysql://" + DB_HOST + ":" + DB_PORT + "/" + DB_NAME +
            "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true&characterEncoding=utf8";

    private DatabaseConnection() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            System.out.println("✓ MySQL JDBC Driver loaded successfully");
        } catch (ClassNotFoundException e) {
            System.err.println("✗ MySQL JDBC Driver not found!");
            e.printStackTrace();
        }
    }

    public static synchronized DatabaseConnection getInstance() {
        if (instance == null) {
            instance = new DatabaseConnection();
        }
        return instance;
    }

    /**
     * Always return a NEW connection.
     * Caller is responsible for closing it after use.
     */
    public static Connection getConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
        if (conn != null && !conn.isClosed()) {
            System.out.println("✓ Database connection established");
        }
        return conn;
    }

    /**
     * Test if database is reachable
     */
    public boolean testConnection() {
        try (Connection conn = getConnection()) {
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            System.err.println("✗ Database test connection failed: " + e.getMessage());
            return false;
        }
    }
}
