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

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller for the Login Screen.
 * I designed this class to act as the security gateway for the application.
 * It handles credential validation via Google's REST API and routes users based on their stored roles.
 */
public class LoginController {

    // I used Dotenv here to keep our API keys out of the source code for security best practices.
    private static final Dotenv dotenv = Dotenv.load();
    private static final String FIREBASE_WEB_API_KEY = dotenv.get("FIREBASE_API_KEY");

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @FXML private GridPane rootPane;
    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;

    /**
     * Called automatically when the view loads.
     * I implemented a dynamic background loader here to ensure the UI looks polished on startup.
     */
    public void initialize() {
        try {
            String imagePath = getClass().getResource("/taxi.jpg").toExternalForm();
            Image image = new Image(imagePath);

            BackgroundSize backgroundSize = new BackgroundSize(100, 100, true, true, true, true);
            BackgroundImage backgroundImage = new BackgroundImage(image,
                    BackgroundRepeat.NO_REPEAT,
                    BackgroundRepeat.NO_REPEAT,
                    BackgroundPosition.CENTER,
                    backgroundSize);

            rootPane.setBackground(new Background(backgroundImage));
        } catch (Exception e) {
            System.err.println("Warning: Could not load background image.");
        }
    }

    /**
     * Main Login Logic.
     * I separated the logic into three distinct steps:
     * 1. Validate Input (prevent empty fields).
     * 2. Verify Credentials (using REST).
     * 3. Authorization (using Firestore Roles).
     */
    @FXML
    protected void onLoginButtonClick() {
        String email = emailField.getText();
        String password = passwordField.getText();

        if (email.isEmpty() || password.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Login Error", "Email and Password cannot be empty.");
            return;
        }

        try {
            // Step 1: Verify Password via REST API
            // I chose the REST API approach here because the Admin SDK does not support client-side password checks.
            if (!verifyPassword(email, password)) {
                showAlert(Alert.AlertType.ERROR, "Login Failed", "Invalid email or password.");
                passwordField.clear();
                return;
            }

            // Step 2: Fetch User Role from Firestore
            UserRecord userRecord = FirebaseAuth.getInstance().getUserByEmail(email);
            Firestore db = FirestoreClient.getFirestore();
            ApiFuture<DocumentSnapshot> future = db.collection("users").document(userRecord.getUid()).get();
            DocumentSnapshot document = future.get();

            String role = "N/A";
            if (document.exists()) {
                role = document.getString("role");
            }

            // Step 3: Role-Based Redirection
            if ("Manager".equalsIgnoreCase(role)) {
                openManagerDashboard();
            } else {
                openDriverDashboard(email);
            }

        } catch (FirebaseAuthException e) {
            showAlert(Alert.AlertType.ERROR, "Login Failed", "User not found or database error.");
        } catch (Exception e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "System Error", "An unexpected error occurred.");
        }
    }

    /**
     * Helper method to verify passwords.
     * I implemented Gson for JSON serialization to safely handle passwords with special characters.
     */
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

        if (response.statusCode() != 200) {
            // Helpful for debugging during development
            System.err.println("Login Debug: " + response.body());
            return false;
        }
        return true;
    }

    // --- Navigation Methods ---

    private void openManagerDashboard() {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/manager-dashboard.fxml"));
            Scene scene = new Scene(fxmlLoader.load(), 900, 600);
            Stage stage = (Stage) emailField.getScene().getWindow();
            stage.setScene(scene);
            stage.centerOnScreen();

            // Start the inactivity timer I created in SessionManager
            SessionManager.startSessionTimer(scene, stage);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void openDriverDashboard(String email) {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/driver-dashboard.fxml"));
            Scene scene = new Scene(fxmlLoader.load(), 1000, 700);

            // Pass the user context to the next controller
            DriverDashboardController driverController = fxmlLoader.getController();
            driverController.setDriverEmail(email);

            Stage stage = (Stage) emailField.getScene().getWindow();
            stage.setScene(scene);
            stage.centerOnScreen();

            // Start the inactivity timer
            SessionManager.startSessionTimer(scene, stage);
        } catch (IOException e) {
            e.printStackTrace();
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
            e.printStackTrace();
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
            // I implemented this check to ensure we only show success if the API actually responds 200 OK
            boolean success = sendPasswordResetEmail(email);

            if (success) {
                showAlert(Alert.AlertType.INFORMATION, "Email Sent",
                        "If an account exists for " + email + ", a password reset link has been sent.");
            } else {
                showAlert(Alert.AlertType.ERROR, "Error",
                        "Could not send reset email. Please check the email address.");
            }
        } catch (Exception e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Error", "An unexpected error occurred.");
        }
    }

    /**
     * Sends a password reset email via the Firebase Auth REST API.
     * I added this helper method to communicate directly with Google's Identity Toolkit.
     */
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

        // Debugging line to see the response in your console
        if (response.statusCode() != 200) {
            System.err.println("Reset Email Failed: " + response.body());
        }

        return response.statusCode() == 200;
    }

    private void showAlert(Alert.AlertType alertType, String title, String message) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}