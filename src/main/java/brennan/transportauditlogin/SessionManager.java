package brennan.transportauditlogin;

import javafx.animation.PauseTransition;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.InputEvent;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;

public class SessionManager {

    // Default timeout in seconds (300 seconds = 5 minutes)
    private static final double TIMEOUT_SECONDS = 300;

    private static PauseTransition delay;

    /**
     * Starts tracking user inactivity on the given scene.
     * @param scene The scene to monitor (Driver or Manager Dashboard)
     * @param stage The stage (window) effectively needed to perform the logout navigation
     */
    public static void startSessionTimer(Scene scene, Stage stage) {
        // 1. Create the timer
        if (delay != null) {
            delay.stop(); // Stop any existing timer
        }

        delay = new PauseTransition(Duration.seconds(TIMEOUT_SECONDS));

        // 2. Define what happens when time runs out
        delay.setOnFinished(event -> {
            System.out.println("Session timed out. Logging out...");
            performLogout(stage);
        });

        // 3. Create an event handler that resets the timer on ANY input
        EventHandler<InputEvent> activityHandler = event -> {
            delay.playFromStart(); // Reset timer to 0
        };

        // 4. Attach the handler to the scene (Mouse moves, clicks, key presses)
        scene.addEventFilter(InputEvent.ANY, activityHandler);

        // 5. Start the timer initially
        delay.play();
    }

    /**
     * Stops the timer (call this when manually logging out)
     */
    public static void stopSessionTimer() {
        if (delay != null) {
            delay.stop();
        }
    }

    /**
     * Handles the actual navigation back to the login screen
     */
    private static void performLogout(Stage stage) {
        try {
            stopSessionTimer(); // Cleanup

            FXMLLoader fxmlLoader = new FXMLLoader(SessionManager.class.getResource("/login-view.fxml"));
            Scene scene = new Scene(fxmlLoader.load(), 400, 300);

            stage.setTitle("TransportAudit - Login");
            stage.setScene(scene);
            stage.centerOnScreen();
            stage.show();

            // Optional: Show an alert saying they were logged out
            // (Note: Showing an alert here can be tricky if the window is minimized,
            // but simply switching the scene is safe).

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}