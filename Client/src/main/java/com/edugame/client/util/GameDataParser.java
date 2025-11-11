package com.edugame.client.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.*;

/**
 * GameDataParser - Helper class to safely parse server data
 */
public class GameDataParser {

    /**
     * Parse START_GAME data into Map format for MathGameController
     */
    public static Map<String, Object> parseStartGameData(JsonObject data) {
        Map<String, Object> gameData = new HashMap<>();

        try {
            // Basic info
            gameData.put("roomId", data.get("roomId").getAsString());
            gameData.put("subject", data.get("subject").getAsString());
            gameData.put("difficulty", data.get("difficulty").getAsString());

            // Parse players array
            JsonArray playersArray = data.getAsJsonArray("players");
            List<Map<String, Object>> players = new ArrayList<>();

            for (int i = 0; i < playersArray.size(); i++) {
                JsonObject playerObj = playersArray.get(i).getAsJsonObject();
                Map<String, Object> player = new HashMap<>();

                player.put("userId", playerObj.get("userId").getAsInt());
                player.put("username", playerObj.get("username").getAsString());
                player.put("fullName", playerObj.get("fullName").getAsString());

                if (playerObj.has("avatarUrl") && !playerObj.get("avatarUrl").isJsonNull()) {
                    player.put("avatarUrl", playerObj.get("avatarUrl").getAsString());
                }

                players.add(player);
            }

            gameData.put("players", players);

            System.out.println("✅ [GameDataParser] Parsed START_GAME data:");
            System.out.println("   Room: " + gameData.get("roomId"));
            System.out.println("   Players: " + players.size());

        } catch (Exception e) {
            System.err.println("❌ [GameDataParser] Error parsing data: " + e.getMessage());
            e.printStackTrace();
        }

        return gameData;
    }

    /**
     * Safe get int value
     */
    public static int getInt(Object obj, int defaultValue) {
        if (obj == null) return defaultValue;
        if (obj instanceof Number) return ((Number) obj).intValue();
        try {
            return Integer.parseInt(obj.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Safe get double value
     */
    public static double getDouble(Object obj, double defaultValue) {
        if (obj == null) return defaultValue;
        if (obj instanceof Number) return ((Number) obj).doubleValue();
        try {
            return Double.parseDouble(obj.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Safe get string value
     */
    public static String getString(Object obj, String defaultValue) {
        return obj != null ? obj.toString() : defaultValue;
    }

    /**
     * Safe get boolean value
     */
    public static boolean getBoolean(Object obj, boolean defaultValue) {
        if (obj == null) return defaultValue;
        if (obj instanceof Boolean) return (Boolean) obj;
        try {
            return Boolean.parseBoolean(obj.toString());
        } catch (Exception e) {
            return defaultValue;
        }
    }
}