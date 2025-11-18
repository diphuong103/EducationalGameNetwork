module com.edugame.client {
    requires javafx.controls;
    requires com.google.gson;
    requires javafx.media;
    requires fontawesomefx;
    requires java.sql;
    requires com.edugame.common;
    requires javafx.fxml;
    requires java.desktop;


    opens com.edugame.client.model to com.google.gson;
    opens com.edugame.client to javafx.fxml;
    opens com.edugame.client.controller to javafx.fxml;

    exports com.edugame.client;
    exports com.edugame.client.controller;

}

