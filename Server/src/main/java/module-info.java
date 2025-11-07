module com.edugame.server {
    requires javafx.controls;
    requires javafx.fxml;
    requires com.edugame.common;
    requires com.google.gson;
    requires java.sql;
    requires org.apache.poi.poi;


    opens com.edugame.server to javafx.fxml;
    exports com.edugame.server;
}