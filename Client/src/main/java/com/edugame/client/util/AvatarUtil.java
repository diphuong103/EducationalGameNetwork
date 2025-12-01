package com.edugame.client.util;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

public class AvatarUtil {

    /**
     * Load ảnh đại diện (avatar) từ URL, file cục bộ hoặc resource.
     * @param imageView ImageView cần hiển thị avatar
     * @param avatarFileName Đường dẫn hoặc tên file avatar
     */
    public static void loadAvatar(ImageView imageView, String avatarFileName) {
        if (imageView == null) return;

        try {
            Image avatarImage = null;

            if (avatarFileName == null || avatarFileName.isBlank()) {
                // 🔹 Không có ảnh → dùng mặc định
                avatarImage = loadDefaultAvatar();
            }
            else if (avatarFileName.startsWith("http://") || avatarFileName.startsWith("https://")) {
                // 🔹 URL từ internet (ImgBB, Firebase, v.v.)
                System.out.println("🌐 Loading from URL: " + avatarFileName);
                avatarImage = new Image(avatarFileName, true);
            }
            else {
                // 🔹 Thử load như file cục bộ trước
                File avatarFile = new File(avatarFileName);

                if (avatarFile.exists() && avatarFile.isFile()) {
                    // ✅ File tồn tại → load bằng FileInputStream để an toàn hơn
                    System.out.println("💾 Loading local file: " + avatarFile.getAbsolutePath());
                    try (InputStream fis = new FileInputStream(avatarFile)) {
                        avatarImage = new Image(fis);
                    }
                } else {
                    // 🔹 Không phải file → thử load từ resource
                    String resourcePath = avatarFileName.startsWith("/")
                            ? avatarFileName
                            : "/images/avatars/" + avatarFileName;

                    System.out.println("📦 Trying resource: " + resourcePath);
                    InputStream inputStream = AvatarUtil.class.getResourceAsStream(resourcePath);

                    if (inputStream != null) {
                        avatarImage = new Image(inputStream);
                        System.out.println("✅ Loaded from resource");
                    } else {
                        System.err.println("⚠️ Resource not found: " + resourcePath);
                        avatarImage = loadDefaultAvatar();
                    }
                }
            }

            if (avatarImage != null) {
                imageView.setImage(avatarImage);
                imageView.setPreserveRatio(true);
                imageView.setSmooth(true);
            }

        } catch (Exception e) {
            System.err.println("❌ Error loading avatar: " + e.getMessage());
            e.printStackTrace();

            try {
                imageView.setImage(loadDefaultAvatar());
            } catch (Exception ex) {
                System.err.println("❌ Failed to load default avatar fallback");
            }
        }
    }

    /**
     * Load ảnh mặc định
     */
    private static Image loadDefaultAvatar() {
        return new Image(AvatarUtil.class.getResourceAsStream("/images/avatars/avatar4.png"));
    }
}