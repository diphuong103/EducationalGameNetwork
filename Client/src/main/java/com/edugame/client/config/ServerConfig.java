package com.edugame.client.config;

import java.io.*;
import java.net.*;
import java.util.*;

/**
 * Quản lý cấu hình kết nối server
 * Hỗ trợ localhost, LAN (máy thật), và Ngrok/Cloud
 */
public class ServerConfig {
    private static final String CONFIG_FILE = "server.properties";
    private static ServerConfig instance;

    private String host;
    private int port;
    private String mode; // "LOCAL", "LAN", "NGROK", "CLOUD"

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
                case "LAN":
                    // LAN mode: tự động phát hiện hoặc dùng IP được cấu hình
                    String lanHost = props.getProperty("lan.host", "auto");
                    if ("auto".equals(lanHost)) {
                        host = getLocalIPAddress();
                    } else {
                        host = lanHost;
                    }
                    port = Integer.parseInt(props.getProperty("lan.port", "8888"));
                    break;

                case "NGROK":
                    // ✅ HỖ TRỢ 2 CÁCH NHẬP NGROK:
                    // Cách 1: Nhập URL đầy đủ vào ngrok.url
                    // Cách 2: Nhập riêng ngrok.host và ngrok.port

                    String ngrokUrl = props.getProperty("ngrok.url", "").trim();

                    if (!ngrokUrl.isEmpty()) {
                        // ✅ Parse URL đầy đủ: tcp://0.tcp.ngrok.io:12345
                        parseNgrokUrl(ngrokUrl);
                    } else {
                        // ✅ Dùng host + port riêng
                        host = props.getProperty("ngrok.host", "0.tcp.ngrok.io");
                        port = Integer.parseInt(props.getProperty("ngrok.port", "12345"));
                    }
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

        } catch (Exception e) {
            System.err.println("❌ Error loading config: " + e.getMessage());
            e.printStackTrace();
            host = "localhost";
            port = 8888;
            mode = "LOCAL";
        }
    }

    /**
     * ✅ Parse Ngrok URL
     * Hỗ trợ các format:
     * - tcp://0.tcp.ngrok.io:12345
     * - 0.tcp.ngrok.io:12345
     * - tcp://1.tcp.ap.ngrok.io:19876
     * - 4.tcp.eu.ngrok.io:12345
     */
    private void parseNgrokUrl(String url) {
        try {
            String cleaned = url.trim();

            // ✅ Bỏ prefix "tcp://" nếu có
            if (cleaned.toLowerCase().startsWith("tcp://")) {
                cleaned = cleaned.substring(6);
            }

            // ✅ Tìm dấu ":" cuối cùng để tách host và port
            int lastColonIndex = cleaned.lastIndexOf(':');

            if (lastColonIndex == -1) {
                throw new IllegalArgumentException("Không tìm thấy port trong URL: " + url);
            }

            // ✅ Tách host và port
            String parsedHost = cleaned.substring(0, lastColonIndex).trim();
            String portStr = cleaned.substring(lastColonIndex + 1).trim();

            // ✅ Validate host
            if (parsedHost.isEmpty() || !parsedHost.contains(".ngrok.io")) {
                throw new IllegalArgumentException("Host không hợp lệ (phải chứa .ngrok.io): " + parsedHost);
            }

            // ✅ Validate port
            int parsedPort = Integer.parseInt(portStr);
            if (parsedPort <= 0 || parsedPort > 65535) {
                throw new IllegalArgumentException("Port không hợp lệ: " + parsedPort);
            }

            // ✅ Gán giá trị
            this.host = parsedHost;
            this.port = parsedPort;

            System.out.println("✅ Parsed Ngrok URL successfully:");
            System.out.println("   Original: " + url);
            System.out.println("   Host: " + host);
            System.out.println("   Port: " + port);

        } catch (Exception e) {
            System.err.println("❌ Error parsing Ngrok URL: " + url);
            System.err.println("   Error: " + e.getMessage());
            System.err.println("   Using default values...");

            // Fallback to defaults
            host = "0.tcp.ngrok.io";
            port = 12345;
        }
    }

    /**
     * Tạo file config mặc định với hướng dẫn đầy đủ
     */
    private void createDefaultConfig() {
        Properties props = new Properties();

        // Mặc định là LOCAL
        props.setProperty("server.mode", "LOCAL");

        // LOCAL config (cùng máy)
        props.setProperty("local.host", "localhost");
        props.setProperty("local.port", "8888");

        // LAN config (mạng nội bộ - máy thật và máy ảo)
        props.setProperty("lan.host", "auto");
        props.setProperty("lan.port", "8888");

        // NGROK config - HỖ TRỢ 2 CÁCH
        props.setProperty("ngrok.url", "");  // Để trống = dùng host+port
        props.setProperty("ngrok.host", "0.tcp.ngrok.io");
        props.setProperty("ngrok.port", "12345");

        // CLOUD config (server online)
        props.setProperty("cloud.host", "your_server_ip");
        props.setProperty("cloud.port", "8888");

        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            String header =
                    "=============================================================\n" +
                            "  SERVER CONFIGURATION - Math Adventure Game\n" +
                            "=============================================================\n\n" +
                            "CÁC CHẾ ĐỘ KẾT NỐI:\n\n" +
                            "1. LOCAL MODE (Chạy trên cùng 1 máy):\n" +
                            "   server.mode=LOCAL\n" +
                            "   → Server và Client chạy trên cùng 1 máy\n" +
                            "   → Dùng localhost:8888\n\n" +
                            "2. LAN MODE (Chạy trên mạng nội bộ - máy thật/máy ảo):\n" +
                            "   server.mode=LAN\n" +
                            "   lan.host=auto    (tự động phát hiện IP)\n" +
                            "   HOẶC\n" +
                            "   lan.host=192.168.1.100  (IP cố định)\n" +
                            "   \n" +
                            "   → Dùng cho:\n" +
                            "     • Máy thật kết nối máy ảo\n" +
                            "     • Máy ảo kết nối máy thật\n" +
                            "     • Các máy trong cùng mạng WiFi/LAN\n" +
                            "   \n" +
                            "   HƯỚNG DẪN:\n" +
                            "   a) Máy chạy SERVER:\n" +
                            "      - Kiểm tra IP: ipconfig (Windows) / ifconfig (Linux/Mac)\n" +
                            "      - Tắt Firewall hoặc mở port 8888\n" +
                            "      - Chạy server\n" +
                            "   \n" +
                            "   b) Máy chạy CLIENT:\n" +
                            "      - Đổi server.mode=LAN\n" +
                            "      - Nhập IP máy server vào lan.host\n" +
                            "      - VD: lan.host=192.168.1.30\n\n" +
                            "3. NGROK MODE (Chơi với bạn bè qua Internet):\n" +
                            "   server.mode=NGROK\n" +
                            "   \n" +
                            "   ✅ CÁCH 1: Nhập URL đầy đủ (KHUYẾN KHÍCH)\n" +
                            "   ngrok.url=tcp://0.tcp.ap.ngrok.io:19876\n" +
                            "   (Copy trực tiếp từ ngrok terminal)\n" +
                            "   \n" +
                            "   ✅ CÁCH 2: Nhập riêng host và port\n" +
                            "   ngrok.url=   (để trống)\n" +
                            "   ngrok.host=0.tcp.ap.ngrok.io\n" +
                            "   ngrok.port=19876\n" +
                            "   \n" +
                            "   a) Máy chạy server:\n" +
                            "      - Cài ngrok: https://ngrok.com/\n" +
                            "      - Chạy: ngrok tcp 8888\n" +
                            "      - Copy URL (VD: tcp://0.tcp.ap.ngrok.io:10873)\n" +
                            "   \n" +
                            "   b) Máy client:\n" +
                            "      - Đổi server.mode=NGROK\n" +
                            "      - Dán URL vào ngrok.url\n\n" +
                            "4. CLOUD MODE (Server online 24/7):\n" +
                            "   server.mode=CLOUD\n" +
                            "   cloud.host=your_public_ip\n" +
                            "   cloud.port=8888\n\n" +
                            "=============================================================\n" +
                            "LƯU Ý QUAN TRỌNG:\n" +
                            "- Kiểm tra Firewall khi dùng LAN mode\n" +
                            "- Máy ảo: Đảm bảo Network Adapter = Bridged/NAT\n" +
                            "- VMware: Preferences > Network > NAT Settings\n" +
                            "- VirtualBox: Settings > Network > Adapter 1 > Bridged\n" +
                            "- Ngrok: Mỗi lần restart ngrok sẽ có URL mới!\n" +
                            "=============================================================\n";

            props.store(fos, header);

            System.out.println("✅ Created default config file: " + CONFIG_FILE);
            System.out.println("📝 Vui lòng xem file để biết cách cấu hình!");

        } catch (IOException e) {
            System.err.println("❌ Error creating config file: " + e.getMessage());
        }
    }

    /**
     * Tự động phát hiện IP của máy trong mạng LAN
     */
    public static String getLocalIPAddress() {
        try {
            // Thử tìm IP không phải localhost
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();

                // Bỏ qua interface không hoạt động hoặc loopback
                if (iface.isLoopback() || !iface.isUp()) {
                    continue;
                }

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();

                    // Chỉ lấy IPv4, bỏ qua loopback
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        String ip = addr.getHostAddress();
                        System.out.println("🔍 Found LAN IP: " + ip + " on " + iface.getDisplayName());
                        return ip;
                    }
                }
            }

            // Fallback: dùng InetAddress.getLocalHost()
            InetAddress localhost = InetAddress.getLocalHost();
            return localhost.getHostAddress();

        } catch (Exception e) {
            System.err.println("❌ Error detecting IP: " + e.getMessage());
            return "localhost";
        }
    }

    /**
     * Liệt kê tất cả IP có thể dùng
     */
    public static List<String> getAllAvailableIPs() {
        List<String> ips = new ArrayList<>();

        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();

                if (!iface.isUp()) continue;

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();

                    if (addr instanceof Inet4Address) {
                        String ip = addr.getHostAddress();
                        String name = iface.getDisplayName();
                        ips.add(ip + " (" + name + ")");
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Error listing IPs: " + e.getMessage());
        }

        return ips;
    }

    /**
     * ✅ Update config và save - HỖ TRỢ NGROK URL
     */
    public void updateConfig(String newMode, String newHost, int newPort) {
        Properties props = new Properties();

        try (FileInputStream fis = new FileInputStream(CONFIG_FILE)) {
            props.load(fis);
        } catch (IOException e) {
            // Start fresh if load fails
        }

        props.setProperty("server.mode", newMode);

        switch (newMode) {
            case "LAN":
                props.setProperty("lan.host", newHost);
                props.setProperty("lan.port", String.valueOf(newPort));
                break;

            case "NGROK":
                // ✅ Kiểm tra xem newHost có phải là URL đầy đủ không
                if (newHost.contains(":") && (newHost.startsWith("tcp://") || newHost.contains(".ngrok.io:"))) {
                    // Đây là URL đầy đủ, lưu vào ngrok.url
                    props.setProperty("ngrok.url", newHost);
                    props.setProperty("ngrok.host", ""); // Clear old values
                    props.setProperty("ngrok.port", "");
                } else {
                    // Đây là host riêng, lưu vào ngrok.host và ngrok.port
                    props.setProperty("ngrok.url", "");
                    props.setProperty("ngrok.host", newHost);
                    props.setProperty("ngrok.port", String.valueOf(newPort));
                }
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

        try (FileOutputStream fos = new FileOutputStream(CONFIG_FILE)) {
            props.store(fos, "Updated at " + new Date());

            this.mode = newMode;

            // ✅ Re-parse nếu là Ngrok URL
            if ("NGROK".equals(newMode) && newHost.contains(":")) {
                parseNgrokUrl(newHost);
            } else {
                this.host = newHost;
                this.port = newPort;
            }

            System.out.println("✅ Config saved: " + mode + " | " + host + ":" + port);

        } catch (IOException e) {
            System.err.println("❌ Error saving config: " + e.getMessage());
        }
    }

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

    public boolean isLAN() {
        return "LAN".equals(mode);
    }

    public boolean isNgrok() {
        return "NGROK".equals(mode);
    }

    public boolean isCloud() {
        return "CLOUD".equals(mode);
    }
}