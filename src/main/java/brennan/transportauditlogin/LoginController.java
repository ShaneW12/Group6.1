package brennan.transportauditlogin;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.google.firebase.cloud.FirestoreClient;
import com.google.gson.Gson; // Added for safe JSON handling
import io.github.cdimascio.dotenv.Dotenv;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button; // Explicit import just in case
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
import java.util.HashMap; // Added
import java.util.Map;     // Added

public class LoginController {

    private static final Dotenv dotenv = Dotenv.load();
    private static final String FIREBASE_WEB_API_KEY = dotenv.get("FIREBASE_API_KEY");

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @FXML private GridPane rootPane; // Used for the background image
    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private Button loginButton;
    @FXML private Button registerButton;

    /**
     * Initializes the controller class. This method is automatically called
     * after the fxml file has been loaded.
     */
    public void initialize() {
        try {
            // Load the background image
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
            System.err.println("Warning: Could not load background image 'taxi.jpg'. Check file location.");
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
            // 1. Verify the password using the REST API (with fixed JSON handling)
            if (!verifyPassword(email, password)) {
                showAlert(Alert.AlertType.ERROR, "Login Failed", "Invalid email or password.\n(Check console for details)");
                passwordField.clear();
                return;
            }

            // 2. Fetch User Role from Firestore (using Admin SDK)
            System.out.println("Password verified. Fetching user role...");
            UserRecord userRecord = FirebaseAuth.getInstance().getUserByEmail(email);

            Firestore db = FirestoreClient.getFirestore();
            ApiFuture<DocumentSnapshot> future = db.collection("users").document(userRecord.getUid()).get();
            DocumentSnapshot document = future.get();

            String role = "N/A";
            if (document.exists()) {
                role = document.getString("role");
            }

            // 3. Handle Role Redirection
            if ("Manager".equalsIgnoreCase(role)) {
                openManagerDashboard();
            } else {
                // Pass the email to the Driver Dashboard so it knows who logged in
                openDriverDashboard(email);
            }

        } catch (FirebaseAuthException e) {
            System.err.println("Firebase Auth Error: " + e.getMessage());
            showAlert(Alert.AlertType.ERROR, "Login Failed", "User not found or database error.");
        } catch (Exception e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Error", "An unexpected error occurred.\n" + e.getMessage());
        }
    }

    private void openManagerDashboard() {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/manager-dashboard.fxml"));
            Scene scene = new Scene(fxmlLoader.load(), 900, 600);

            Stage stage = (Stage) emailField.getScene().getWindow();
            stage.setScene(scene);
            stage.centerOnScreen();

        } catch (IOException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Navigation Error", "Could not load the Manager Dashboard.");
        }
    }

    private void openDriverDashboard(String email) {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/driver-dashboard.fxml"));
            Scene scene = new Scene(fxmlLoader.load(), 1000, 700);

            // --- Pass the email to the Driver Controller ---
            DriverDashboardController driverController = fxmlLoader.getController();
            driverController.setDriverEmail(email);
            // -----------------------------------------------

            Stage stage = (Stage) emailField.getScene().getWindow();
            stage.setScene(scene);
            stage.centerOnScreen();

        } catch (IOException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Navigation Error", "Could not load the Driver Dashboard.");
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
            showAlert(Alert.AlertType.ERROR, "Error", "Could not open registration page.");
        }
    }

    @FXML
    protected void onForgotPasswordClick() {
        String email = emailField.getText();

        if (email.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Forgot Password",
                    "Please enter your email address in the box above, then click this button again.");
            return;
        }

        try {
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

    // --- HELPER METHODS ---

    private boolean verifyPassword(String email, String password) throws IOException, InterruptedException {
        // Use Gson to create valid JSON, handling special characters in passwords automatically
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

        // Check for success (200 OK)
        if (response.statusCode() == 200) {
            return true;
        } else {
            // --- DEBUGGING: Print the actual error from Firebase to the console ---
            System.err.println("Login Failed. Status Code: " + response.statusCode());
            System.err.println("Firebase Response: " + response.body());
            return false;
        }
    }

    private boolean sendPasswordResetEmail(String email) throws IOException, InterruptedException {
        // Use Gson here as well for consistency
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

        if (response.statusCode() != 200) {
            System.err.println("Reset Email Failed. Status: " + response.statusCode());
            System.err.println("Response: " + response.body());
        }

        return response.statusCode() == 200;
    }

    private void showAlert(Alert.AlertType alertType, String title, String message) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}