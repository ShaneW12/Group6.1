package brennan.transportauditlogin;

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
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller for the Registration Screen.
 * I implemented this to create new users in both Firebase Authentication (for login)
 * and Firestore (for role management).
 */
public class RegisterController {

    @FXML private TextField emailField;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private ToggleGroup roleToggleGroup;

    @FXML
    protected void onRegisterSubmitClick(ActionEvent event) {
        String email = emailField.getText();
        String username = usernameField.getText();
        String password = passwordField.getText();

        // I added a check here to safely get the text from the selected radio button
        RadioButton selectedRadio = (RadioButton) roleToggleGroup.getSelectedToggle();
        String role = (selectedRadio != null) ? selectedRadio.getText() : "Driver";

        if (email.isEmpty() || username.isEmpty() || password.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Form Error", "Please fill in all fields.");
            return;
        }

        try {
            // 1. Create user in Firebase Authentication
            // I specifically set the DisplayName here so we can easily show "Welcome, [Name]" later.
            UserRecord.CreateRequest request = new UserRecord.CreateRequest()
                    .setEmail(email)
                    .setPassword(password)
                    .setDisplayName(username)
                    .setDisabled(false);

            UserRecord userRecord = FirebaseAuth.getInstance().createUser(request);

            // 2. Save custom data (Role) to Firestore
            Firestore db = FirestoreClient.getFirestore();
            Map<String, Object> userData = new HashMap<>();
            userData.put("username", username);
            userData.put("email", email);
            userData.put("role", role);

            db.collection("users").document(userRecord.getUid()).set(userData);

            showAlert(Alert.AlertType.INFORMATION, "Success", "User registered! Please log in.");
            onBackToLoginClick(event);

        } catch (FirebaseAuthException e) {
            showAlert(Alert.AlertType.ERROR, "Registration Failed", e.getMessage());
        } catch (Exception e) {
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Error", "An unexpected error occurred.");
        }
    }

    @FXML
    protected void onBackToLoginClick(ActionEvent event) {
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
        Alert alert = new Alert(alertType);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}