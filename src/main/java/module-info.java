module com.edugame.educationalgamenetwork {
    requires javafx.controls;
    requires javafx.fxml;


    opens com.edugame.educationalgamenetwork to javafx.fxml;
    exports com.edugame.educationalgamenetwork;
}