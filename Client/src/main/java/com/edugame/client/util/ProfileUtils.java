package com.edugame.client.util;

import com.edugame.client.controller.ProfileController;
import com.edugame.client.model.User;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.stage.Stage;

import java.net.URL;

public class ProfileUtils {
    public static void openProfile(User user) {
        try {
            System.out.println("🔵 ProfileUtils.openProfile() called for user: " + user.getUsername());

            URL fxmlLocation = ProfileUtils.class.getResource("/fxml/Profile.fxml");

            if (fxmlLocation == null) {
                System.err.println("❌ FXML file not found: /fxml/Profile.fxml");
                showError("Không tìm thấy file giao diện");
                return;
            }

            System.out.println("✅ Loading FXML from: " + fxmlLocation);

            FXMLLoader loader = new FXMLLoader(fxmlLocation);

            // ✅ Dùng controller factory để tạo controller với data
            loader.setControllerFactory(controllerClass -> {
                if (controllerClass == ProfileController.class) {
                    System.out.println("🏭 Controller factory creating ProfileController");
                    ProfileController controller = new ProfileController();

                    // ✅ Set data NGAY khi tạo controller
                    controller.initData(user);
                    System.out.println("🏭 initData() called with: " + user.getUsername());

                    return controller;
                }

                // Fallback cho các controller khác
                try {
                    return controllerClass.getDeclaredConstructor().newInstance();
                } catch (Exception e) {
                    throw new RuntimeException("Cannot create controller: " + controllerClass, e);
                }
            });

            // ✅ Load FXML (initialize() sẽ chạy SAU initData())
            Scene scene = new Scene(loader.load());

            Stage stage = new Stage();
            stage.setTitle("Hồ sơ người chơi - " + user.getUsername());
            stage.setScene(scene);

            stage.setMinWidth(600);
            stage.setMinHeight(400);


            stage.show();

            System.out.println("✅ Profile window opened successfully for: " + user.getUsername());

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("❌ Lỗi khi mở trang hồ sơ người chơi: " + e.getMessage());
            showError("Không thể mở hồ sơ: " + e.getMessage());
        }
    }

    private static void showError(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Lỗi");
            alert.setHeaderText("Không thể mở hồ sơ");
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
}