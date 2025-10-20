package com.edugame.client.network;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import java.io.*;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class ServerConnection {
    private static ServerConnection instance;
    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private Gson gson;
    private boolean connected;

    // User session data
    private String currentUsername;
    private int currentUserId;

    private ServerConnection() {
        gson = new Gson();
        connected = false;
    }

    public static synchronized ServerConnection getInstance() {
        if (instance == null) {
            instance = new ServerConnection();
        }
        return instance;
    }

    /**
     * Connect to server
     */
    public boolean connect(String host, int port) {
        try {
            socket = new Socket(host, port);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(socket.getOutputStream(), true);
            connected = true;

            System.out.println("Connected to server: " + host + ":" + port);
            return true;
        } catch (IOException e) {
            System.err.println("Failed to connect to server: " + e.getMessage());
            connected = false;
            return false;
        }
    }

    /**
     * Login to server
     */
    public boolean login(String username, String password) {
        try {
            // Create login request
            Map<String, Object> request = new HashMap<>();
            request.put("type", "LOGIN");
            request.put("username", username);
            request.put("password", password);

            // Send request
            String jsonRequest = gson.toJson(request);
            writer.println(jsonRequest);

            // Wait for response
            String response = reader.readLine();
            JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);

            boolean success = jsonResponse.get("success").getAsBoolean();

            if (success) {
                currentUsername = username;
                currentUserId = jsonResponse.get("userId").getAsInt();
                System.out.println("Login successful: " + username);
            } else {
                System.out.println("Login failed: " + jsonResponse.get("message").getAsString());
            }

            return success;

        } catch (IOException e) {
            System.err.println("Login error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Register new user
     */
    public boolean register(String username, String password, String email,
                            String fullName, String age, String avatar) {
        try {
            // Create registration request
            Map<String, Object> request = new HashMap<>();
            request.put("type", "REGISTER");
            request.put("username", username);
            request.put("password", password);
            request.put("email", email.isEmpty() ? username + "@mathadventure.com" : email);
            request.put("fullName", fullName);
            request.put("age", age);
            request.put("avatar", avatar);

            // Send request
            String jsonRequest = gson.toJson(request);
            writer.println(jsonRequest);

            // Wait for response
            String response = reader.readLine();
            JsonObject jsonResponse = gson.fromJson(response, JsonObject.class);

            boolean success = jsonResponse.get("success").getAsBoolean();

            if (success) {
                System.out.println("Registration successful: " + username);
            } else {
                System.out.println("Registration failed: " + jsonResponse.get("message").getAsString());
            }

            return success;

        } catch (IOException e) {
            System.err.println("Registration error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Send message to server
     */
    public void sendMessage(String message) {
        if (writer != null) {
            writer.println(message);
        }
    }

    /**
     * Send JSON object to server
     */
    public void sendJson(Map<String, Object> data) {
        String json = gson.toJson(data);
        sendMessage(json);
    }

    /**
     * Receive message from server
     */
    public String receiveMessage() throws IOException {
        if (reader != null) {
            return reader.readLine();
        }
        return null;
    }

    /**
     * Disconnect from server
     */
    public void disconnect() {
        try {
            if (socket != null && !socket.isClosed()) {
                // Send logout message
                Map<String, Object> request = new HashMap<>();
                request.put("type", "LOGOUT");
                request.put("username", currentUsername);
                sendJson(request);

                socket.close();
                connected = false;
                System.out.println("Disconnected from server");
            }
        } catch (IOException e) {
            System.err.println("Error disconnecting: " + e.getMessage());
        }
    }

    // Getters
    public boolean isConnected() {
        return connected && socket != null && !socket.isClosed();
    }

    public String getCurrentUsername() {
        return currentUsername;
    }

    public int getCurrentUserId() {
        return currentUserId;
    }
}