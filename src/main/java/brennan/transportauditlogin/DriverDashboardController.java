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

/**
 * Controller for the Driver Dashboard.
 * I designed this interface to be the primary workspace for drivers.
 * It integrates Google Maps for route verification and allows for easy expense submission.
 */
public class DriverDashboardController {

    @FXML private Label welcomeLabel;
    @FXML private TextField startAddress;
    @FXML private TextField endAddress;

    // I added a ComboBox for expense types to standardize the data entry
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
    private final double RATE_PER_MILE = 0.67; // Based on 2024 IRS Standard
    private final Dotenv dotenv = Dotenv.load();

    private String currentUserEmail;

    public void initialize() {
        setupTable();
        setupMap();
        setupInputs();
    }

    private void setupTable() {
        colDate.setCellValueFactory(new PropertyValueFactory<>("date"));
        colType.setCellValueFactory(new PropertyValueFactory<>("type"));
        colMiles.setCellValueFactory(new PropertyValueFactory<>("mileage"));
        colCost.setCellValueFactory(new PropertyValueFactory<>("amount"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));
    }

    private void setupMap() {
        WebEngine webEngine = mapWebView.getEngine();
        webEngine.load("https://www.google.com/maps");
    }

    /**
     * I implemented a listener here to make the UI dynamic.
     * If a driver selects "Fuel" or "Tolls", the mileage field is automatically disabled.
     * This reduces user error and keeps the data clean.
     */
    private void setupInputs() {
        expenseTypeCombo.setItems(FXCollections.observableArrayList(
                "Mileage", "Fuel", "Maintenance", "Tolls", "Parking", "Other"
        ));
        expenseTypeCombo.getSelectionModel().select("Mileage");

        expenseTypeCombo.valueProperty().addListener((obs, oldVal, newVal) -> {
            if ("Mileage".equals(newVal)) {
                manualMiles.setDisable(false);
            } else {
                manualMiles.setDisable(true);
                manualMiles.clear();
            }
        });
    }

    public void setDriverEmail(String email) {
        this.currentUserEmail = email;
        welcomeLabel.setText("Driver: " + email);
        loadMyHistory();
    }

    /**
     * Calculates the route using the Google Directions API.
     * I ensure the API Key is loaded from the secure .env file here.
     */
    @FXML
    private void calculateRoute() {
        String start = startAddress.getText();
        String end = endAddress.getText();

        if (start.isEmpty() || end.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Missing Info", "Please enter addresses.");
            return;
        }

        GoogleMapsService.RouteInfo route = mapsService.getRouteDetails(start, end);

        if (route != null) {
            updateUIWithRoute(route, start, end);
        } else {
            showAlert(Alert.AlertType.ERROR, "GPS Error", "Could not calculate route.");
        }
    }

    private void updateUIWithRoute(GoogleMapsService.RouteInfo route, String start, String end) {
        distLabel.setText(route.text);
        double cost = route.miles * RATE_PER_MILE;
        costLabel.setText(String.format("$%.2f", cost));

        // Auto-fill fields for the user
        manualMiles.setText(String.format("%.1f", route.miles));
        manualCost.setText(String.format("%.2f", cost));
        expenseTypeCombo.getSelectionModel().select("Mileage");

        // Load the embedded map
        String apiKey = dotenv.get("GOOGLE_MAPS_API_KEY");
        String mapUrl = "https://www.google.com/maps/embed/v1/directions" +
                "?key=" + apiKey +
                "&origin=" + start.replace(" ", "+") +
                "&destination=" + end.replace(" ", "+") +
                "&mode=driving";

        String htmlContent = "<!DOCTYPE html><html><head><style>body,html{margin:0;padding:0;height:100%;overflow:hidden;}</style></head><body><iframe width='100%' height='100%' frameborder='0' style='border:0' src='" + mapUrl + "' allowfullscreen></iframe></body></html>";
        mapWebView.getEngine().loadContent(htmlContent);
    }

    @FXML
    private void submitLog() {
        String type = expenseTypeCombo.getValue();
        String costStr = manualCost.getText();

        if (costStr.isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Error", "Cost is required.");
            return;
        }

        try {
            double cost = Double.parseDouble(costStr);
            double miles = 0.0;
            if (!manualMiles.getText().isEmpty()) {
                miles = Double.parseDouble(manualMiles.getText());
            }

            saveExpenseToFirestore(type, cost, miles);

        } catch (NumberFormatException e) {
            showAlert(Alert.AlertType.ERROR, "Format Error", "Must be valid numbers.");
        }
    }

    private void saveExpenseToFirestore(String type, double cost, double miles) {
        Map<String, Object> data = new HashMap<>();
        data.put("employeeName", currentUserEmail);
        data.put("date", LocalDate.now().toString());
        data.put("type", type);
        data.put("amount", cost);
        data.put("mileage", miles);
        data.put("status", "Pending");

        Firestore db = FirestoreClient.getFirestore();
        db.collection("expenses").add(data);

        showAlert(Alert.AlertType.INFORMATION, "Success", "Trip log submitted.");
        loadMyHistory();
    }

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
        // I make sure to stop the timer to prevent memory leaks when logging out
        SessionManager.stopSessionTimer();
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
        alert.setContentText(msg);
        alert.showAndWait();
    }
}