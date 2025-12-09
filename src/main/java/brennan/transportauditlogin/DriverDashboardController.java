package brennan.transportauditlogin;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.firebase.cloud.FirestoreClient;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;

import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class DriverDashboardController {

    @FXML private Label welcomeLabel;
    @FXML private TextField startAddress;
    @FXML private TextField endAddress;
    @FXML private TextField manualMiles;
    @FXML private TextField manualCost;

    @FXML private Label distLabel;
    @FXML private Label costLabel;

    @FXML private WebView mapWebView;

    @FXML private TableView<Expense> tripTable;
    @FXML private TableColumn<Expense, String> colDate;
    @FXML private TableColumn<Expense, String> colType;
    @FXML private TableColumn<Expense, Double> colMiles;
    @FXML private TableColumn<Expense, Double> colCost;
    @FXML private TableColumn<Expense, String> colStatus;

    private final GoogleMapsService mapsService = new GoogleMapsService();
    private final ObservableList<Expense> myTrips = FXCollections.observableArrayList();
    private final double RATE_PER_MILE = 0.67; // IRS Standard Rate

    // Current user's email/ID to filter logs
    private String currentUserEmail;

    public void initialize() {
        // Setup Table
        colDate.setCellValueFactory(new PropertyValueFactory<>("date"));
        colType.setCellValueFactory(new PropertyValueFactory<>("type"));
        colMiles.setCellValueFactory(new PropertyValueFactory<>("mileage"));
        colCost.setCellValueFactory(new PropertyValueFactory<>("amount"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        // NOTE: We REMOVED the FirebaseAuth.getInstance().getCurrentUser() code here.
        // We will load the history in the 'setDriverEmail' method instead.
        // Load a default map view
        WebEngine webEngine = mapWebView.getEngine();
        webEngine.load("https://www.google.com/maps");
    }

    // --- NEW METHOD ---
    /**
     * Called by LoginController to pass the logged-in user's email.
     */
    public void setDriverEmail(String email) {
        this.currentUserEmail = email;
        welcomeLabel.setText("Driver: " + email);
        loadMyHistory(); // Now that we have the email, we can load the data
    }

    @FXML
    private void calculateRoute() {
        String start = startAddress.getText();
        String end = endAddress.getText();

        if (start.isEmpty() || end.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Missing Info", "Please enter start and end addresses.");
            return;
        }

        // Call the service
        GoogleMapsService.RouteInfo route = mapsService.getRouteDetails(start, end);

        if (route != null) {
            // Update UI with calculated data
            distLabel.setText(route.text);

            double cost = route.miles * RATE_PER_MILE;
            costLabel.setText(String.format("$%.2f", cost));

            // Auto-fill the manual fields (so they can be edited if needed)
            manualMiles.setText(String.format("%.1f", route.miles));
            manualCost.setText(String.format("%.2f", cost));

            // Update Map View to show directions
            String apiKey = "AIzaSyD4kEGu8TyGyznen04mwUtp5EacLaWXxA0";

            String mapUrl = "https://www.google.com/maps/embed/v1/directions" +
                    "?key=" + apiKey +
                    "&origin=" + start.replace(" ", "+") +
                    "&destination=" + end.replace(" ", "+") +
                    "&mode=driving";

            // simple HTML string that holds an IFrame because the google maps API needs to be embedded in a website to function properly
            String htmlContent = "<!DOCTYPE html>" +
                    "<html>" +
                    "<head>" +
                    "<style>body,html{margin:0;padding:0;height:100%;overflow:hidden;}</style>" +
                    "</head>" +
                    "<body>" +
                    "<iframe width='100%' height='100%' frameborder='0' style='border:0' " +
                    "src='" + mapUrl + "' allowfullscreen></iframe>" +
                    "</body>" +
                    "</html>";
            // Loads HTML content
            mapWebView.getEngine().loadContent(htmlContent);

        } else {
            showAlert(Alert.AlertType.ERROR, "GPS Error", "Could not calculate route. Check your API Key or address validity.");
        }
    }

    @FXML
    private void submitLog() {
        String milesStr = manualMiles.getText();
        String costStr = manualCost.getText();

        if (milesStr.isEmpty() || costStr.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Error", "No trip data to submit. Calculate a route or enter manually.");
            return;
        }

        try {
            double miles = Double.parseDouble(milesStr);
            double cost = Double.parseDouble(costStr);

            // Creates Data Object
            Map<String, Object> data = new HashMap<>();
            data.put("employeeName", currentUserEmail); // Tie to this driver
            data.put("date", LocalDate.now().toString());
            data.put("type", "Mileage"); // Auto-set type
            data.put("amount", cost);
            data.put("mileage", miles);
            data.put("status", "Pending"); // Default status

            // Saves to Firestore
            Firestore db = FirestoreClient.getFirestore();
            db.collection("expenses").add(data);

            showAlert(Alert.AlertType.INFORMATION, "Success", "Trip log submitted for approval.");

            // Clears fields and refreshes
            startAddress.clear();
            endAddress.clear();
            manualMiles.clear();
            manualCost.clear();
            distLabel.setText("-");
            costLabel.setText("-");

            loadMyHistory();

        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.ERROR, "Format Error", "Miles and Cost must be numbers.");
        }
    }

    private void loadMyHistory() {
        myTrips.clear();
        Firestore db = FirestoreClient.getFirestore();

        // Query only expenses where employeeName matches current user
        ApiFuture<QuerySnapshot> future = db.collection("expenses")
                .whereEqualTo("employeeName", currentUserEmail)
                .get();

        try {
            List<QueryDocumentSnapshot> docs = future.get().getDocuments();
            for (QueryDocumentSnapshot doc : docs) {
                myTrips.add(doc.toObject(Expense.class));
            }
            tripTable.setItems(myTrips);
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void onLogout(ActionEvent event) {
        try {
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/login-view.fxml"));
            Scene scene = new Scene(fxmlLoader.load(), 400, 300);
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(scene);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void showAlert(Alert.AlertType type, String title, String msg) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}