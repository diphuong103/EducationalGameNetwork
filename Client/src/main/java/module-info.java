module com.edugame.client {
    requires javafx.controls;
    requires javafx.fxml;


    opens com.edugame.client to javafx.fxml;
    exports com.edugame.client;
}