module com.edugame.common {
    requires javafx.controls;
    requires javafx.fxml;


    opens com.edugame.common to javafx.fxml;
    exports com.edugame.common;
}