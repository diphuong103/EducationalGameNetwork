package com.edugame.client.util;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import java.io.File;

public class AvatarUtil {

    /**
     * Load ảnh đại diện (avatar) từ URL, file cục bộ hoặc resource.
     * @param imageView ImageView cần hiển thị avatar
     * @param avatarFileName Đường dẫn hoặc tên file avatar
     */
    public static void loadAvatar(ImageView imageView, String avatarFileName) {
        if (imageView == null) return;

        try {
            Image avatarImage;

            if (avatarFileName == null || avatarFileName.isBlank()) {
                // 🔹 Không có ảnh → dùng mặc định
                avatarImage = new Image(AvatarUtil.class.getResourceAsStream("/images/avatars/avatar4.png"));
            }
            else if (avatarFileName.startsWith("http")) {
                // 🔹 URL từ internet (ImgBB, Firebase, v.v.)
                avatarImage = new Image(avatarFileName, true);
            }
            else if (avatarFileName.contains(File.separator) || new File(avatarFileName).isAbsolute()) {
                // 🔹 File từ máy tính (đường dẫn tuyệt đối)
                File avatarFile = new File(avatarFileName);

                if (avatarFile.exists() && avatarFile.isFile()) {
                    avatarImage = new Image(avatarFile.toURI().toString(), true);
                    System.out.println("✅ Loaded local file: " + avatarFileName);
                } else {
                    System.err.println("⚠️ Local file not found: " + avatarFileName);
                    avatarImage = new Image(AvatarUtil.class.getResourceAsStream("/images/avatars/avatar4.png"));
                }
            }
            else {
                // 🔹 Ảnh trong resource (avatar1.png, avatar2.png...)
                String resourcePath = "/images/avatars/" + avatarFileName;
                var inputStream = AvatarUtil.class.getResourceAsStream(resourcePath);

                if (inputStream != null) {
                    avatarImage = new Image(inputStream);
                } else {
                    System.err.println("⚠️ Resource not found: " + resourcePath);
                    avatarImage = new Image(AvatarUtil.class.getResourceAsStream("/images/avatars/avatar4.png"));
                }
            }

            imageView.setImage(avatarImage);
            imageView.setPreserveRatio(true);
            imageView.setSmooth(true);

        } catch (Exception e) {
            System.err.println("❌ Error loading avatar: " + e.getMessage());
            e.printStackTrace();

            try {
                Image defaultAvatar = new Image(AvatarUtil.class.getResourceAsStream("/images/avatars/avatar4.png"));
                imageView.setImage(defaultAvatar);
            } catch (Exception ex) {
                System.err.println("❌ Failed to load default avatar fallback");
            }
        }
    }
}
