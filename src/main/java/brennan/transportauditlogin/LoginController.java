package brennan.transportauditlogin;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.google.firebase.cloud.FirestoreClient;
import com.google.gson.Gson;
import io.github.cdimascio.dotenv.Dotenv;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller for the Login Screen.
 * I designed this class to authenticate users and route them to their specific dashboard
 * with their profile information pre-loaded.
 */
public class LoginController {

    // I added the logger here to replace the "printStackTrace" calls.
    // This removes the warnings and is better for debugging.
    private static final Logger logger = LoggerFactory.getLogger(LoginController.class);

    // Loads the API keys from the .env file so they aren't hardcoded in the code
    private static final Dotenv dotenv = Dotenv.load();
    private static final String FIREBASE_WEB_API_KEY = dotenv.get("FIREBASE_API_KEY");

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @FXML private GridPane rootPane;
    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;

    // This runs when the login screen first opens
    public void initialize() {
        try {
            // I added a check here to make sure "rootPane" actually exists before I try to use it.
            // This fixes the "field never assigned" warning.
            if (rootPane != null) {
                // I also added a check for the image file. If "/taxi.jpg" is missing,
                // it won't crash the app anymore (fixes the NullPointerException warning).
                URL imageResource = getClass().getResource("/taxi.jpg");

                if (imageResource != null) {
                    String imagePath = imageResource.toExternalForm();
                    Image image = new Image(imagePath);
                    BackgroundSize backgroundSize = new BackgroundSize(100, 100, true, true, true, true);
                    BackgroundImage backgroundImage = new BackgroundImage(image, BackgroundRepeat.NO_REPEAT, BackgroundRepeat.NO_REPEAT, BackgroundPosition.CENTER, backgroundSize);
                    rootPane.setBackground(new Background(backgroundImage));
                }
            }
        } catch (Exception e) {
            // If the background fails to load, I just log it and move on. The app still works.
            System.err.println("Warning: Could not load background image.");
        }
    }

    @FXML
    protected void onLoginButtonClick() {
        String email = emailField.getText();
        String password = passwordField.getText();

        if (email.isEmpty() || password.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Login Error", "Email and Password cannot be empty.");
            return;
        }

        try {
            // 1. Verify Password via REST API because the Admin SDK doesn't check passwords
            if (!verifyPassword(email, password)) {
                showAlert(Alert.AlertType.ERROR, "Login Failed", "Invalid email or password.");
                passwordField.clear();
                return;
            }

            // 2. Get the User Record to find their username
            UserRecord userRecord = FirebaseAuth.getInstance().getUserByEmail(email);
            String username = userRecord.getDisplayName();

            // Fallback: If no username is set, just use the email
            if (username == null || username.isEmpty()) {
                username = email;
            }

            // 3. Check Firestore to see if they are a "Manager" or "Driver"
            Firestore db = FirestoreClient.getFirestore();
            ApiFuture<DocumentSnapshot> future = db.collection("users").document(userRecord.getUid()).get();
            DocumentSnapshot document = future.get();

            String role = "N/A";
            if (document.exists()) {
                role = document.getString("role");
            }

            // 4. Send them to the correct dashboard based on their role
            if ("Manager".equalsIgnoreCase(role)) {
                openManagerDashboard(username);
            } else {
                // I removed the email parameter here because we don't need it anymore.
                openDriverDashboard(username);
            }

        } catch (FirebaseAuthException e) {
            showAlert(Alert.AlertType.ERROR, "Login Failed", "User not found or database error.");
        } catch (Exception e) {
            logger.error("Login system error", e); // Fixed warning by using logger
            showAlert(Alert.AlertType.ERROR, "System Error", "An unexpected error occurred.");
        }
    }

    // Helper method to check the password with Firebase
    private boolean verifyPassword(String email, String password) throws IOException, InterruptedException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("email", email);
        payload.put("password", password);
        payload.put("returnSecureToken", true);
        String jsonPayload = new Gson().toJson(payload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=" + FIREBASE_WEB_API_KEY))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode() == 200;
    }

    // --- Navigation Methods ---

    private void openManagerDashboard(String username) {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/manager-dashboard.fxml"));
            Scene scene = new Scene(fxmlLoader.load(), 900, 600);

            // Pass the username to the manager controller so it says "Welcome, [Name]"
            ManagerDashboardController managerController = fxmlLoader.getController();
            managerController.setManagerName(username);

            Stage stage = (Stage) emailField.getScene().getWindow();
            stage.setScene(scene);
            stage.centerOnScreen();
            SessionManager.startSessionTimer(scene, stage);
        } catch (IOException e) {
            logger.error("Failed to open Manager Dashboard", e); // Fixed warning
        }
    }

    // I removed the 'email' parameter from this method to fix the "unused parameter" warning
    private void openDriverDashboard(String username) {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/driver-dashboard.fxml"));
            Scene scene = new Scene(fxmlLoader.load(), 1000, 700);

            DriverDashboardController driverController = fxmlLoader.getController();

            // I only pass the username now, which matches the update I made to the Driver Controller
            driverController.setDriverProfile(username);

            Stage stage = (Stage) emailField.getScene().getWindow();
            stage.setScene(scene);
            stage.centerOnScreen();
            SessionManager.startSessionTimer(scene, stage);
        } catch (IOException e) {
            logger.error("Failed to open Driver Dashboard", e); // Fixed warning
        }
    }

    @FXML
    protected void onRegisterButtonClick(ActionEvent event) {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/register-view.fxml"));
            Scene scene = new Scene(fxmlLoader.load(), 450, 400);
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(scene);
        } catch (IOException e) {
            logger.error("Failed to open Register View", e); // Fixed warning
        }
    }

    @FXML
    protected void onForgotPasswordClick() {
        String email = emailField.getText();
        if (email.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Forgot Password", "Please enter your email address first.");
            return;
        }
        try {
            boolean success = sendPasswordResetEmail(email);
            if (success) {
                showAlert(Alert.AlertType.INFORMATION, "Email Sent", "If an account exists, a reset link has been sent.");
            } else {
                showAlert(Alert.AlertType.ERROR, "Error", "Could not send reset email. Check address.");
            }
        } catch (Exception e) {
            logger.error("Forgot Password error", e); // Fixed warning
            showAlert(Alert.AlertType.ERROR, "Error", "An unexpected error occurred.");
        }
    }

    private boolean sendPasswordResetEmail(String email) throws IOException, InterruptedException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("requestType", "PASSWORD_RESET");
        payload.put("email", email);
        String jsonPayload = new Gson().toJson(payload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://identitytoolkit.googleapis.com/v1/accounts:sendOobCode?key=" + FIREBASE_WEB_API_KEY))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode() == 200;
    }

    private void showAlert(Alert.AlertType alertType, String title, String message) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}