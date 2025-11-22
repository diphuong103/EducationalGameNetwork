package com.edugame.server.web;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CloudflareTunnel - Tích hợp Cloudflare Tunnel để expose web server
 *
 * Tính năng:
 * - Tự động tạo tunnel HTTPS miễn phí
 * - Không cần mở port trên router
 * - URL công khai bảo mật qua Cloudflare
 * - Tự động parse public URL
 *
 * Kỹ thuật Network Integration:
 * - Process management
 * - Stream parsing
 * - Regex pattern matching
 * - Error handling
 */
public class CloudflareTunnel {

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private Process tunnelProcess;
    private String publicUrl;
    private boolean running;
    private Thread outputThread;

    public CloudflareTunnel() {
        this.running = false;
        this.publicUrl = null;
    }

    /**
     * Khởi động Cloudflare Tunnel
     * @param localPort Port của web server local (8080)
     * @return true nếu thành công
     */
    public boolean start(int localPort) {
        if (running) {
            log("⚠️ Tunnel đã đang chạy!");
            return false;
        }

        // Kiểm tra cloudflared có sẵn không
        if (!isCloudflaredInstalled()) {
            log("❌ cloudflared chưa được cài đặt!");
            log("📥 Hướng dẫn cài đặt:");
            log("   Windows: winget install --id Cloudflare.cloudflared");
            log("   Linux: wget https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64");
            log("   Mac: brew install cloudflare/cloudflare/cloudflared");
            return false;
        }

        try {
            log("🚀 Đang khởi động Cloudflare Tunnel...");

            // Tạo command: cloudflared tunnel --url http://localhost:8080
            List<String> command = new ArrayList<>();
            command.add("cloudflared");
            command.add("tunnel");
            command.add("--url");
            command.add("http://localhost:" + localPort);

            // Khởi động process
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true); // Merge stderr vào stdout
            tunnelProcess = pb.start();

            // Thread đọc output và tìm public URL
            outputThread = new Thread(() -> parseOutput());
            outputThread.start();

            running = true;

            // Đợi URL được parse (tối đa 10 giây)
            int waitCount = 0;
            while (publicUrl == null && waitCount < 50) {
                Thread.sleep(200);
                waitCount++;
            }

            if (publicUrl != null) {
                log("✅ Cloudflare Tunnel đã sẵn sàng!");
                log("🌍 Public URL: " + publicUrl);
                log("🔒 Kết nối được mã hóa HTTPS bởi Cloudflare");
                return true;
            } else {
                log("⚠️ Tunnel đang chạy nhưng chưa lấy được URL");
                log("   Vui lòng kiểm tra output bên dưới");
                return true;
            }

        } catch (IOException e) {
            log("❌ Lỗi khởi động tunnel: " + e.getMessage());
            return false;
        } catch (InterruptedException e) {
            log("❌ Bị gián đoạn khi chờ tunnel: " + e.getMessage());
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Dừng Cloudflare Tunnel
     */
    public void stop() {
        if (!running) {
            return;
        }

        log("🛑 Đang dừng Cloudflare Tunnel...");

        if (tunnelProcess != null && tunnelProcess.isAlive()) {
            tunnelProcess.destroy();
            try {
                tunnelProcess.waitFor();
            } catch (InterruptedException e) {
                tunnelProcess.destroyForcibly();
                Thread.currentThread().interrupt();
            }
        }

        if (outputThread != null && outputThread.isAlive()) {
            outputThread.interrupt();
        }

        running = false;
        publicUrl = null;
        log("✅ Cloudflare Tunnel đã dừng");
    }

    /**
     * Lấy public URL
     */
    public String getPublicUrl() {
        return publicUrl;
    }

    /**
     * Kiểm tra tunnel có đang chạy không
     */
    public boolean isRunning() {
        return running && tunnelProcess != null && tunnelProcess.isAlive();
    }

    /**
     * Kiểm tra cloudflared đã cài đặt chưa
     */
    private boolean isCloudflaredInstalled() {
        try {
            Process p = Runtime.getRuntime().exec("cloudflared --version");
            p.waitFor();
            return p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Parse output từ cloudflared để tìm public URL
     */
    private void parseOutput() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(tunnelProcess.getInputStream()))) {

            String line;
            // Pattern để match URL: https://random-words-1234.trycloudflare.com
            Pattern urlPattern = Pattern.compile("https://[a-z0-9-]+\\.trycloudflare\\.com");

            while ((line = reader.readLine()) != null && running) {
                // Log mọi dòng output
                log("   [cloudflared] " + line);

                // Tìm public URL
                if (publicUrl == null) {
                    Matcher matcher = urlPattern.matcher(line);
                    if (matcher.find()) {
                        publicUrl = matcher.group();
                        log("🎯 Đã tìm thấy public URL: " + publicUrl);
                    }
                }
            }

        } catch (IOException e) {
            if (running) {
                log("⚠️ Lỗi đọc output: " + e.getMessage());
            }
        }
    }

    /**
     * Logging với timestamp
     */
    private void log(String message) {
        String timestamp = LocalDateTime.now().format(TIME_FORMAT);
        System.out.println("[" + timestamp + "] [CloudflareTunnel] " + message);
    }

    /**
     * Lấy thông tin chi tiết về tunnel
     */
    public String getInfo() {
        StringBuilder info = new StringBuilder();
        info.append("Cloudflare Tunnel Status\n");
        info.append("========================\n");
        info.append("Running: ").append(isRunning() ? "Yes" : "No").append("\n");
        info.append("Public URL: ").append(publicUrl != null ? publicUrl : "N/A").append("\n");
        info.append("Process: ").append(tunnelProcess != null && tunnelProcess.isAlive() ? "Alive" : "Dead").append("\n");
        return info.toString();
    }
}