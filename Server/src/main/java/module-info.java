module com.edugame.server {
    requires javafx.controls;
    requires javafx.fxml;
    requires com.edugame.common;
    requires com.google.gson;
    requires java.sql;
    requires jdk.httpserver;
    requires java.management;
    requires org.apache.poi.ooxml;
    requires java.net.http;

    opens com.edugame.server.controller to javafx.fxml;

    opens com.edugame.server to javafx.fxml;
    exports com.edugame.server;
}