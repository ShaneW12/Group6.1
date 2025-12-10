package brennan.transportauditlogin;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.firebase.cloud.FirestoreClient;
import io.github.cdimascio.dotenv.Dotenv;
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

    // --- NEW: The Dropdown Box ---
    @FXML private ComboBox<String> expenseTypeCombo;

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
    private final double RATE_PER_MILE = 0.67;
    private final Dotenv dotenv = Dotenv.load();

    private String currentUserEmail;

    public void initialize() {
        // 1. Setup Table Columns
        colDate.setCellValueFactory(new PropertyValueFactory<>("date"));
        colType.setCellValueFactory(new PropertyValueFactory<>("type"));
        colMiles.setCellValueFactory(new PropertyValueFactory<>("mileage"));
        colCost.setCellValueFactory(new PropertyValueFactory<>("amount"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        // 2. Setup Map
        WebEngine webEngine = mapWebView.getEngine();
        webEngine.load("https://www.google.com/maps");

        // 3. NEW: Initialize the Dropdown Options
        expenseTypeCombo.setItems(FXCollections.observableArrayList(
                "Mileage", "Fuel", "Maintenance", "Tolls", "Parking", "Other"
        ));
        expenseTypeCombo.getSelectionModel().select("Mileage"); // Default to Mileage
    }

    public void setDriverEmail(String email) {
        this.currentUserEmail = email;
        welcomeLabel.setText("Driver: " + email);
        loadMyHistory();
    }

    @FXML
    private void calculateRoute() {
        // Calculates the distance of the route
        String start = startAddress.getText();
        String end = endAddress.getText();

        if (start.isEmpty() || end.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Missing Info", "Please enter start and end addresses.");
            return;
        }

        GoogleMapsService.RouteInfo route = mapsService.getRouteDetails(start, end);

        if (route != null) {
            distLabel.setText(route.text);
            double cost = route.miles * RATE_PER_MILE;
            costLabel.setText(String.format("$%.2f", cost));

            // Auto-fill fields
            manualMiles.setText(String.format("%.1f", route.miles));
            manualCost.setText(String.format("%.2f", cost));

            // Ensures "Mileage" is selected since we just calculated a route
            expenseTypeCombo.getSelectionModel().select("Mileage");

            // Load Map
            String apiKey = dotenv.get("GOOGLE_MAPS_API_KEY");
            String mapUrl = "https://www.google.com/maps/embed/v1/directions" +
                    "?key=" + apiKey +
                    "&origin=" + start.replace(" ", "+") +
                    "&destination=" + end.replace(" ", "+") +
                    "&mode=driving";

            // (HTML map code omitted for brevity) ...
            String htmlContent = "<!DOCTYPE html><html><head><style>body,html{margin:0;padding:0;height:100%;overflow:hidden;}</style></head><body><iframe width='100%' height='100%' frameborder='0' style='border:0' src='" + mapUrl + "' allowfullscreen></iframe></body></html>";
            mapWebView.getEngine().loadContent(htmlContent);

        } else {
            showAlert(Alert.AlertType.ERROR, "GPS Error", "Could not calculate route.");
        }
    }

    @FXML
    private void submitLog() {
        String type = expenseTypeCombo.getValue(); // Get the selected type
        String milesStr = manualMiles.getText();
        String costStr = manualCost.getText();

        if (costStr.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Error", "Cost is required.");
            return;
        }

        try {
            double cost = Double.parseDouble(costStr);
            double miles = 0.0;

            // Logic: If user enters text in miles, parse it. If empty, default to 0.
            if (!milesStr.isEmpty()) {
                miles = Double.parseDouble(milesStr);
            }

            // Validation: If they selected "Mileage", they MUST have miles entered.
            if (type.equals("Mileage") && miles == 0) {
                showAlert(Alert.AlertType.ERROR, "Error", "For 'Mileage' expenses, the miles field cannot be 0.");
                return;
            }

            // Create Data Object
            Map<String, Object> data = new HashMap<>();
            data.put("employeeName", currentUserEmail);
            data.put("date", LocalDate.now().toString());
            data.put("type", type); // Use the selected type
            data.put("amount", cost);
            data.put("mileage", miles);
            data.put("status", "Pending");

            Firestore db = FirestoreClient.getFirestore();
            db.collection("expenses").add(data);

            showAlert(Alert.AlertType.INFORMATION, "Success", type + " log submitted for approval.");

            // Clear Form
            startAddress.clear();
            endAddress.clear();
            manualMiles.clear();
            manualCost.clear();
            distLabel.setText("-");
            costLabel.setText("-");
            expenseTypeCombo.getSelectionModel().select("Mileage"); // Reset to default

            loadMyHistory();

        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.ERROR, "Format Error", "Cost and Miles must be valid numbers.");
        }
    }

    // ... (Keep loadMyHistory, onLogout, and showAlert exactly as they were) ...
    private void loadMyHistory() {
        myTrips.clear();
        Firestore db = FirestoreClient.getFirestore();
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
        // --- NEW: Stop the timer to prevent memory leaks ---
        SessionManager.stopSessionTimer();
        // --------------------------------------------------

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