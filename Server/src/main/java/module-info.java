module com.edugame.server {
    requires javafx.controls;
    requires javafx.fxml;


    opens com.edugame.server to javafx.fxml;
    exports com.edugame.server;
}