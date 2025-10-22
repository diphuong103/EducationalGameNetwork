module com.edugame.client {
    requires javafx.controls;
    requires javafx.fxml;
    requires com.google.gson;
    requires javafx.graphics;
    requires javafx.media;
    requires fontawesomefx;
    requires java.sql;


    opens com.edugame.client to javafx.fxml;
    opens com.edugame.client.controller to javafx.fxml;

    exports com.edugame.client;
    exports com.edugame.client.controller;

}

