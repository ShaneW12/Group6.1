package brennan.transportauditlogin;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;

public class TransportAuditApp extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        // Load the FXML file for the login view
        FXMLLoader fxmlLoader = new FXMLLoader(TransportAuditApp.class.getResource("/login-view.fxml"));

        // Create the scene
        Scene scene = new Scene(fxmlLoader.load(), 400, 300);

        // Set the stage
        stage.setTitle("TransportAudit");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch();
    }
}