package brennan.transportauditlogin;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.google.firebase.cloud.FirestoreClient;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class RegisterController {

    @FXML
    private TextField emailField;
    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private ToggleGroup roleToggleGroup;

    @FXML
    protected void onRegisterSubmitClick(ActionEvent event) {
        String email = emailField.getText();
        String username = usernameField.getText();
        String password = passwordField.getText();
        String role = ((RadioButton) roleToggleGroup.getSelectedToggle()).getText();

        if (email.isEmpty() || username.isEmpty() || password.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Form Error", "Please fill in all fields.");
            return;
        }

        // --- Firebase Integration ---
        try {
            // 1. Create user in Firebase Authentication
            UserRecord.CreateRequest request = new UserRecord.CreateRequest()
                    .setEmail(email)
                    .setPassword(password)
                    .setDisplayName(username) // We can set the display name here
                    .setDisabled(false);

            UserRecord userRecord = FirebaseAuth.getInstance().createUser(request);
            System.out.println("Successfully created new user: " + userRecord.getUid());

            // 2. Save custom data (like role) to Firestore
            Firestore db = FirestoreClient.getFirestore();

            // Create a Map to store the user's data
            Map<String, Object> userData = new HashMap<>();
            userData.put("username", username);
            userData.put("email", email);
            userData.put("role", role);

            // Save the data to the "users" collection with the UID as the document ID
            ApiFuture<WriteResult> future = db.collection("users").document(userRecord.getUid()).set(userData);

            // Wait for the write to complete (optional, but good for confirmation)
            System.out.println("User data saved to Firestore at: " + future.get().getUpdateTime());

            // --- Show Success and Go Back ---
            showAlert(Alert.AlertType.INFORMATION, "Registration Successful",
                    "New " + role + " '" + username + "' has been registered!");

            onBackToLoginClick(event); // Go back to login screen

        } catch (FirebaseAuthException e) {
            // Handle Firebase Auth errors (e.g., email already exists)
            System.err.println("Error creating user: " + e.getMessage());
            showAlert(Alert.AlertType.ERROR, "Registration Failed", "Error: " + e.getMessage());
        } catch (Exception e) {
            // Handle other errors (like Firestore)
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Registration Failed", "An unexpected error occurred.");
        }
        // --- End of Firebase Integration ---
    }

    @FXML
    protected void onBackToLoginClick(ActionEvent event) {
        // ... (This method remains the same)
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/login-view.fxml"));
            Scene scene = new Scene(fxmlLoader.load(), 400, 300);
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(scene);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showAlert(Alert.AlertType alertType, String title, String message) {
        // ... (This method remains theT same)
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}