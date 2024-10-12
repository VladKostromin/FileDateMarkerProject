module com.vladkostromin.filedatemarkerproject {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;

    opens com.vladkostromin.filedatemarkerproject.controllers to javafx.fxml;
    exports com.vladkostromin.filedatemarkerproject;
}