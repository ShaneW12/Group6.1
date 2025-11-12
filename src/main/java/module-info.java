module tec {
    requires javafx.controls;
    requires javafx.fxml;

    opens tec to javafx.fxml;
    exports tec;
}
