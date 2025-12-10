package brennan.transportauditlogin;

import javafx.animation.PauseTransition;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.input.InputEvent;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;

/**
 * Utility class to handle security timeouts.
 * I implemented this to enforce a strict security policy:
 * If a user is inactive for 5 minutes, they are automatically logged out to prevent unauthorized access.
 */
public class SessionManager {

    private static final double TIMEOUT_SECONDS = 300; // 5 Minutes
    private static PauseTransition delay;

    /**
     * Starts tracking user inactivity on the given scene.
     * I used an EventFilter here to catch ALL input events (mouse, keyboard) before they reach other nodes.
     */
    public static void startSessionTimer(Scene scene, Stage stage) {
        if (delay != null) {
            delay.stop();
        }

        // The timer that triggers the logout
        delay = new PauseTransition(Duration.seconds(TIMEOUT_SECONDS));
        delay.setOnFinished(event -> {
            System.out.println("Session timed out. Performing secure logout.");
            performLogout(stage);
        });

        // The listener that resets the timer on any interaction
        EventHandler<InputEvent> activityHandler = event -> {
            delay.playFromStart(); // Reset timer to 0
        };

        // Attach the handler to the scene
        scene.addEventFilter(InputEvent.ANY, activityHandler);
        delay.play();
    }

    public static void stopSessionTimer() {
        if (delay != null) {
            delay.stop();
        }
    }

    private static void performLogout(Stage stage) {
        try {
            stopSessionTimer();

            FXMLLoader fxmlLoader = new FXMLLoader(SessionManager.class.getResource("/login-view.fxml"));
            Scene scene = new Scene(fxmlLoader.load(), 400, 300);

            stage.setTitle("TransportAudit - Login");
            stage.setScene(scene);
            stage.centerOnScreen();
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}