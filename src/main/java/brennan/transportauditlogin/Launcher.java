package brennan.transportauditlogin;

import javafx.application.Application;

public class Launcher {
    public static void main(String[] args) {
        // Initializes Firebase *before* launching the app
        FirebaseService.initialize();

        // Launch the JavaFX application
        Application.launch(TransportAuditApp.class, args);
    }
}
