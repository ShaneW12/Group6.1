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
import javafx.stage.Stage;

import java.io.IOException;

public class LoginController {

    @FXML
    private TextField emailField; // Renamed from usernameField

    @FXML
    private PasswordField passwordField;

    @FXML
    protected void onLoginButtonClick() {
        String email = emailField.getText();
        String password = passwordField.getText(); // We get this but can't check it

        if (email.isEmpty() || password.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Login Error", "Email and Password cannot be empty.");
            return;
        }

        try {
            // 1. Get the user from Firebase Auth by email
            // This proves the user *exists*
            System.out.println("Attempting to find user: " + email);
            UserRecord userRecord = FirebaseAuth.getInstance().getUserByEmail(email);
            System.out.println("Successfully fetched user data: " + userRecord.getUid());

            // 2. Get additional data from Firestore
            Firestore db = FirestoreClient.getFirestore();
            ApiFuture<DocumentSnapshot> future = db.collection("users").document(userRecord.getUid()).get();
            DocumentSnapshot document = future.get(); // Waits for the data

            String username = "N/A";
            String role = "N/A";

            if (document.exists()) {
                username = document.getString("username");
                role = document.getString("role");
            }

            // 3. Show "success"
            // REMINDER: We never actually checked the password!
            showAlert(Alert.AlertType.INFORMATION, "Login 'Successful' (Demo)",
                    "Welcome, " + username + "!\nYour role is: " + role + "\n(Password not checked in this demo)");

            // Clear fields
            emailField.clear();
            passwordField.clear();

        } catch (FirebaseAuthException e) {
            // This error triggers if the email does not exist
            System.err.println("Error fetching user: " + e.getMessage());
            showAlert(Alert.AlertType.ERROR, "Login Failed", "User not found or other Firebase error.");
        } catch (Exception e) {
            // This catches other errors, like Firestore connection issues
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Error", "An unexpected error occurred.");
        }
    }

    @FXML
    protected void onRegisterButtonClick(ActionEvent event) {
        // ... (This method remains the same)
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

    private void showAlert(Alert.AlertType alertType, String title, String message) {
        // ... (This method remains the same)
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}