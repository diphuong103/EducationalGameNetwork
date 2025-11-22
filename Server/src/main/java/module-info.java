module com.edugame.server {
    requires javafx.controls;
    requires javafx.fxml;
    requires com.edugame.common;
    requires com.google.gson;
    requires java.sql;
    requires org.apache.poi.poi;
    requires jdk.httpserver;
    requires java.management;

    opens com.edugame.server.controller to javafx.fxml;

    opens com.edugame.server to javafx.fxml;
    exports com.edugame.server;
}