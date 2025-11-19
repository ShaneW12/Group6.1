package brennan.transportauditlogin;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.google.firebase.cloud.FirestoreClient;
import com.google.gson.Gson; // Import Gson
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URI; // Import new HTTP classes
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

public class LoginController {

    //FIREBASE WEB API KEY HERE ---
    private static final String FIREBASE_WEB_API_KEY = "AIzaSyBlyVFqxTv9UU_OQWyKag1sMsZelaTV9WQ";

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Gson gson = new Gson();

    @FXML
    private TextField emailField;
    @FXML
    private PasswordField passwordField;

    @FXML
    protected void onLoginButtonClick() {
        String email = emailField.getText();
        String password = passwordField.getText();

        if (email.isEmpty() || password.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Login Error", "Email and Password cannot be empty.");
            return;
        }

        try {
            // --- NEW PASSWORD CHECK ---
            // Step 1: Verify the password using the REST API.
            if (!verifyPassword(email, password)) {
                showAlert(Alert.AlertType.ERROR, "Login Failed", "Invalid email or password.");
                passwordField.clear(); // Clear password on failure
                return;
            }

            // --- OLD CODE (MODIFIED) ---
            // Step 2: If password is correct, get user's *role* from Firestore.
            // We use the Admin SDK for this part.
            System.out.println("Password verified. Fetching user role...");
            UserRecord userRecord = FirebaseAuth.getInstance().getUserByEmail(email);

            Firestore db = FirestoreClient.getFirestore();
            ApiFuture<DocumentSnapshot> future = db.collection("users").document(userRecord.getUid()).get();
            DocumentSnapshot document = future.get(); // Waits for the data

            String username = "N/A";
            String role = "N/A";

            if (document.exists()) {
                username = document.getString("username");
                role = document.getString("role");
            }

            // Step 3: Show success
            showAlert(Alert.AlertType.INFORMATION, "Login Successful",
                    "Welcome, " + username + "!\nYour role is: " + role);

            // Clear fields and move to next screen (or close)
            emailField.clear();
            passwordField.clear();

            // TODO: Add code here to switch to your main application scene

        } catch (FirebaseAuthException e) {
            // This can still fail if the user exists in Auth but not Firestore
            System.err.println("Error fetching user data: " + e.getMessage());
            showAlert(Alert.AlertType.ERROR, "Login Failed", "User not found in database.");
        } catch (Exception e) {
            // This catches other errors, like no internet connection
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Error", "An unexpected error occurred.");
        }
    }

    /**
     * Verifies a user's email and password against the Firebase Auth REST API.
     *
     * @param email    The user's email.
     * @param password The user's password.
     * @return true if the login is successful, false otherwise.
     */
    private boolean verifyPassword(String email, String password) throws IOException, InterruptedException {
        // 1. Create the JSON request body
        String jsonPayload = String.format(
                "{\"email\":\"%s\",\"password\":\"%s\",\"returnSecureToken\":true}",
                email, password
        );

        // 2. Create the web request
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=" + FIREBASE_WEB_API_KEY))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                .build();

        // 3. Send the request and get the response
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // 4. Check the result
        // A 200 "OK" status means the email and password were correct.
        if (response.statusCode() == 200) {
            System.out.println("Password authentication successful.");
            // You can also get the user's token from the response if needed:
            // Map<String, Object> responseMap = gson.fromJson(response.body(), Map.class);
            // String idToken = (String) responseMap.get("idToken");
            return true;
        } else {
            // Any other status (like 400) means invalid credentials
            System.out.println("Password authentication failed. Status: " + response.statusCode());
            System.out.println("Response: " + response.body());
            return false;
        }
    }


    @FXML
    protected void onRegisterButtonClick(ActionEvent event) {
        // This method is unchanged, but make sure the path is correct
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/register-view.fxml")); // Must have '/'
            Scene scene = new Scene(fxmlLoader.load(), 450, 400);
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(scene);
        } catch (IOException e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Error", "Could not open registration page.");
        }
    }

    private void showAlert(Alert.AlertType alertType, String title, String message) {
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}