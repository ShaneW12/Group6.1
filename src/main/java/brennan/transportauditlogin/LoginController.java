package brennan.transportauditlogin;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.google.firebase.cloud.FirestoreClient;
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

public class LoginController {

    // --- PASTE YOUR FIREBASE WEB API KEY HERE ---
    private static final String FIREBASE_WEB_API_KEY = "AIzaSyBlyVFqxTv9UU_OQWyKag1sMsZelaTV9WQ";

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @FXML private GridPane rootPane; // Used for the background image
    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;

    /**
     * Initializes the controller class. This method is automatically called
     * after the fxml file has been loaded.
     */
    public void initialize() {
        try {
            // Load the background image
            // We use the absolute path "/" to ensure Maven finds it in resources
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
            System.err.println("Warning: Could not load background image 'taxi.jpg'. Check the file location.");
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
            // 1. Verify the password using the REST API
            if (!verifyPassword(email, password)) {
                showAlert(Alert.AlertType.ERROR, "Login Failed", "Invalid email or password.");
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
                // Open Manager Dashboard
                openManagerDashboard();
            } else {
                // Driver or other role
                showAlert(Alert.AlertType.INFORMATION, "Login Successful",
                        "Welcome! Driver interface is coming soon.\nRole: " + role);
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
            // Load the dashboard FXML
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/manager-dashboard.fxml"));

            // Set the scene size larger for the dashboard (900x600)
            Scene scene = new Scene(fxmlLoader.load(), 900, 600);

            // Get the current stage (window)
            Stage stage = (Stage) emailField.getScene().getWindow();
            stage.setScene(scene);
            stage.centerOnScreen(); // Center the new larger window

        } catch (IOException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Navigation Error", "Could not load the Manager Dashboard.");
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
        String jsonPayload = String.format(
                "{\"email\":\"%s\",\"password\":\"%s\",\"returnSecureToken\":true}",
                email, password
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=" + FIREBASE_WEB_API_KEY))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // 200 OK means password is correct
        return response.statusCode() == 200;
    }

    private boolean sendPasswordResetEmail(String email) throws IOException, InterruptedException {
        String jsonPayload = String.format(
                "{\"requestType\":\"PASSWORD_RESET\",\"email\":\"%s\"}",
                email
        );

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
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}