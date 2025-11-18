package com.edugame.client.config;

import java.io.*;
import java.util.Properties;

/**
 * Quản lý cấu hình kết nối server
 * Hỗ trợ localhost, Ngrok, và Cloud server
 */
public class ServerConfig {
    private static final String CONFIG_FILE = "server.properties";
    private static ServerConfig instance;

    private String host;
    private int port;
    private String mode; // "LOCAL", "NGROK", "CLOUD"

    private ServerConfig() {
        loadConfig();
    }

    public static ServerConfig getInstance() {
        if (instance == null) {
            instance = new ServerConfig();
        }
        return instance;
    }

    /**
     * Load config từ file
     */
    private void loadConfig() {
        Properties props = new Properties();
        File configFile = new File(CONFIG_FILE);

        // Nếu file không tồn tại, tạo file mặc định
        if (!configFile.exists()) {
            createDefaultConfig();
        }

        // Đọc config
        try (FileInputStream fis = new FileInputStream(configFile)) {
            props.load(fis);

            mode = props.getProperty("server.mode", "LOCAL");

            switch (mode) {
                case "NGROK":
                    host = props.getProperty("ngrok.host", "0.tcp.ngrok.io");
                    port = Integer.parseInt(props.getProperty("ngrok.port", "12345"));
                    break;

                case "CLOUD":
                    host = props.getProperty("cloud.host", "your_server_ip");
                    port = Integer.parseInt(props.getProperty("cloud.port", "8888"));
                    break;

                case "LOCAL":
                default:
                    host = props.getProperty("local.host", "localhost");
                    port = Integer.parseInt(props.getProperty("local.port", "8888"));
                    break;
            }

            System.out.println("╔════════════════════════════════════════╗");
            System.out.println("║     SERVER CONFIGURATION LOADED        ║");
            System.out.println("╠════════════════════════════════════════╣");
            System.out.println("║  Mode: " + String.format("%-33s", mode) + "║");
            System.out.println("║  Host: " + String.format("%-33s", host) + "║");
            System.out.println("║  Port: " + String.format("%-33s", port) + "║");
            System.out.println("╚════════════════════════════════════════╝");

        } catch (IOException e) {
            System.err.println("❌ Error loading config, using defaults");
            host = "localhost";
            port = 8888;
            mode = "LOCAL";
        }
    }

    /**
     * Tạo file config mặc định với hướng dẫn đầy đủ
     */
    private void createDefaultConfig() {
        Properties props = new Properties();

        // Mặc định là LOCAL
        props.setProperty("server.mode", "LOCAL");

        // LOCAL config
        props.setProperty("local.host", "localhost");
        props.setProperty("local.port", "8888");

        // NGROK config (placeholder)
        props.setProperty("ngrok.host", "0.tcp.ngrok.io");
        props.setProperty("ngrok.port", "12345");

        // CLOUD config (placeholder)
        props.setProperty("cloud.host", "your_server_ip");
        props.setProperty("cloud.port", "8888");

        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            // Header với hướng dẫn chi tiết
            String header =
                    "=======================================================\n" +
                            "  SERVER CONFIGURATION - BrainQuest Game\n" +
                            "=======================================================\n\n" +
                            "HƯỚNG DẪN SỬ DỤNG:\n\n" +
                            "1. CHẠY LOCAL (trên cùng 1 máy):\n" +
                            "   server.mode=LOCAL\n" +
                            "   → Không cần thay đổi gì thêm\n\n" +
                            "2. CHẠY NGROK (chơi với bạn từ xa):\n" +
                            "   a) Máy chạy server:\n" +
                            "      - Chạy: ngrok tcp 8888\n" +
                            "      - Copy URL được cấp (VD: 0.tcp.ngrok.io:12345)\n" +
                            "   b) Máy client (bạn bè):\n" +
                            "      - Đổi server.mode=NGROK\n" +
                            "      - Cập nhật ngrok.host và ngrok.port\n" +
                            "      VD:\n" +
                            "        ngrok.host=0.tcp.ngrok.io\n" +
                            "        ngrok.port=12345\n\n" +
                            "3. CHẠY CLOUD (server online 24/7):\n" +
                            "   server.mode=CLOUD\n" +
                            "   cloud.host=your_public_ip\n" +
                            "   cloud.port=8888\n\n" +
                            "=======================================================\n";

            props.store(fos, header);

            System.out.println("✅ Created default config file: " + CONFIG_FILE);
            System.out.println("📝 Vui lòng chỉnh sửa file để phù hợp với mục đích sử dụng!");

        } catch (IOException e) {
            System.err.println("❌ Error creating config file: " + e.getMessage());
        }
    }

    /**
     * Update config và save
     */
    public void updateConfig(String newMode, String newHost, int newPort) {
        Properties props = new Properties();

        // Load current config first
        try (FileInputStream fis = new FileInputStream(CONFIG_FILE)) {
            props.load(fis);
        } catch (IOException e) {
            // If load fails, start fresh
        }

        // Update mode
        props.setProperty("server.mode", newMode);

        // Update appropriate host/port based on mode
        switch (newMode) {
            case "NGROK":
                props.setProperty("ngrok.host", newHost);
                props.setProperty("ngrok.port", String.valueOf(newPort));
                break;

            case "CLOUD":
                props.setProperty("cloud.host", newHost);
                props.setProperty("cloud.port", String.valueOf(newPort));
                break;

            case "LOCAL":
                props.setProperty("local.host", newHost);
                props.setProperty("local.port", String.valueOf(newPort));
                break;
        }

        // Save
        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            props.store(fos, "Updated at " + new java.util.Date());

            // Update memory
            this.mode = newMode;
            this.host = newHost;
            this.port = newPort;

            System.out.println("✅ Config saved: " + mode + " | " + host + ":" + port);

        } catch (IOException e) {
            System.err.println("❌ Error saving config: " + e.getMessage());
        }
    }

    /**
     * Reload config từ file
     */
    public void reload() {
        loadConfig();
    }

    // Getters
    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getMode() {
        return mode;
    }

    public String getServerAddress() {
        return host + ":" + port;
    }

    public boolean isLocal() {
        return "LOCAL".equals(mode);
    }

    public boolean isNgrok() {
        return "NGROK".equals(mode);
    }

    public boolean isCloud() {
        return "CLOUD".equals(mode);
    }
}