package com.edugame.server.config;

import java.io.*;
import java.util.Properties;

/**
 * Quản lý cấu hình ứng dụng, bao gồm API Keys
 */
public class ConfigManager {

    private static final String CONFIG_FILE = "config.properties";
    private static Properties properties = new Properties();

    static {
        loadConfig();
    }

    /**
     * Load config từ file
     */
    private static void loadConfig() {
        File configFile = new File(CONFIG_FILE);

        // Nếu file không tồn tại, tạo file mặc định
        if (!configFile.exists()) {
            createDefaultConfig();
        }

        try (FileInputStream fis = new FileInputStream(configFile)) {
            properties.load(fis);
            System.out.println("✅ [Config] Đã load cấu hình từ " + CONFIG_FILE);
        } catch (IOException e) {
            System.err.println("❌ [Config] Lỗi khi load config: " + e.getMessage());
            // Load default values
            setDefaultValues();
        }
    }

    /**
     * Tạo file config mặc định
     */
    private static void createDefaultConfig() {
        setDefaultValues();
        saveConfig();
        System.out.println("✅ [Config] Đã tạo file config mặc định");
    }

    /**
     * Set giá trị mặc định
     */
    private static void setDefaultValues() {
        properties.setProperty("gemini.api.key", "");

        properties.setProperty("gemini.api.url",
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash-latest:generateContent");
        properties.setProperty("ai.temperature", "0.7");
        properties.setProperty("ai.max.tokens", "8192");
        properties.setProperty("ai.max.questions", "50");
    }

    /**
     * Lưu config vào file
     */
    public static void saveConfig() {
        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            properties.store(fos, "EduGame Server Configuration");
            System.out.println("✅ [Config] Đã lưu cấu hình");
        } catch (IOException e) {
            System.err.println("❌ [Config] Lỗi khi lưu config: " + e.getMessage());
        }
    }

    /**
     * Lấy giá trị config
     */
    public static String get(String key) {
        return properties.getProperty(key);
    }

    /**
     * Lấy giá trị config với default
     */
    public static String get(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }

    /**
     * Set giá trị config
     */
    public static void set(String key, String value) {
        properties.setProperty(key, value);
    }

    /**
     * Get Gemini API Key
     */
    public static String getGeminiApiKey() {
        return get("gemini.api.key", "");
    }

    /**
     * Get Gemini API URL
     */
    public static String getGeminiApiUrl() {
        String baseUrl = get("gemini.api.url",
                "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash-latest:generateContent");
        String apiKey = getGeminiApiKey();

        // Đảm bảo baseUrl có scheme
        if (!baseUrl.startsWith("http://") && !baseUrl.startsWith("https://")) {
            baseUrl = "https://" + baseUrl;
        }

        // Kiểm tra apiKey có rỗng không
        if (apiKey == null || apiKey.isEmpty()) {
            System.err.println("⚠️ [Config] API Key chưa được cấu hình!");
            return baseUrl;
        }

        return baseUrl + "?key=" + apiKey;
    }

    /**
     * Set Gemini API Key
     */
    public static void setGeminiApiKey(String apiKey) {
        set("gemini.api.key", apiKey);
        saveConfig();
    }

    /**
     * Get AI temperature
     */
    public static double getAiTemperature() {
        try {
            return Double.parseDouble(get("ai.temperature", "0.7"));
        } catch (NumberFormatException e) {
            return 0.7;
        }
    }

    /**
     * Get max output tokens
     */
    public static int getMaxTokens() {
        try {
            return Integer.parseInt(get("ai.max.tokens", "8192"));
        } catch (NumberFormatException e) {
            return 8192;
        }
    }

    /**
     * Get max questions per generation
     */
    public static int getMaxQuestions() {
        try {
            return Integer.parseInt(get("ai.max.questions", "50"));
        } catch (NumberFormatException e) {
            return 50;
        }
    }

    /**
     * Kiểm tra xem API Key đã được cấu hình chưa
     */
    public static boolean isApiKeyConfigured() {
        String apiKey = getGeminiApiKey();
        return apiKey != null && !apiKey.isEmpty() && !apiKey.equals("YOUR_API_KEY_HERE");
    }
}