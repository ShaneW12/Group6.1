package brennan.transportauditlogin;

import javafx.animation.PauseTransition;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.input.InputEvent;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

// I used this class to handle the auto-logout timer and the actual logout process.
// It helps keep the security logic in one place.
public class SessionManager {

    private static final Logger logger = LoggerFactory.getLogger(SessionManager.class);
    private static final double TIMEOUT_SECONDS = 300; // 5 Minutes
    private static PauseTransition delay;

    // Starts the timer that watches for inactivity
    public static void startSessionTimer(Scene scene, Stage stage) {
        if (delay != null) {
            delay.stop();
        }

        // If the timer runs out (5 mins), it logs the user out automatically.
        // I renamed 'event' to 'ignored' because I don't need to check the event details,
        // I just need to know that the time ran out.
        delay = new PauseTransition(Duration.seconds(TIMEOUT_SECONDS));
        delay.setOnFinished(ignored -> {
            logger.info("Session timed out. Performing secure logout.");
            logout(stage);
        });

        // If the user moves the mouse or types, we reset the timer back to 0.
        // I switched to an "Expression Lambda" (removing the curly braces {})
        // to make the code cleaner and fix the IDE warning.
        EventHandler<InputEvent> activityHandler = ignored -> delay.playFromStart();

        // This adds the listener to the whole screen
        scene.addEventFilter(InputEvent.ANY, activityHandler);
        delay.play();
    }

    public static void stopSessionTimer() {
        if (delay != null) {
            delay.stop();
        }
    }

    // I made this method public so the Dashboard buttons can call it too.
    // It handles switching the screen back to the Login view.
    public static void logout(Stage stage) {
        try {
            stopSessionTimer(); // Always stop the timer first

            FXMLLoader fxmlLoader = new FXMLLoader(SessionManager.class.getResource("/login-view.fxml"));
            Scene scene = new Scene(fxmlLoader.load(), 400, 300);

            stage.setTitle("TransportAudit - Login");
            stage.setScene(scene);
            stage.centerOnScreen();
            stage.show();
        } catch (IOException e) {
            logger.error("Logout failed", e);
        }
    }
}