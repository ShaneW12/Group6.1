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
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class DriverDashboardController {

    // I switched to using a logger instead of System.out.println because it's better practice for debugging
    private static final Logger logger = LoggerFactory.getLogger(DriverDashboardController.class);

    @FXML private Label welcomeLabel;
    @FXML private TextField startAddress;
    @FXML private TextField endAddress;

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

    // I only need to store the list of trips here.
    // I removed the 'RATE_PER_MILE' variable from up here because I only used it in one method.
    private final ObservableList<Expense> myTrips = FXCollections.observableArrayList();

    private String currentUsername;

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

        // Using my FormatUtils helper class to fix the currency formatting
        colCost.setCellFactory(FormatUtils.getCurrencyCellFactory());
    }

    private void setupMap() {
        // Loads a blank map at start so the user doesn't see a weird empty white box
        WebEngine webEngine = mapWebView.getEngine();
        webEngine.load("https://www.google.com/maps");
    }

    private void setupInputs() {
        expenseTypeCombo.setItems(FXCollections.observableArrayList(
                "Mileage", "Fuel", "Maintenance", "Tolls", "Parking", "Other"
        ));
        expenseTypeCombo.getSelectionModel().select("Mileage");

        // The "ignored" variables here silence the warnings about unused parameters
        expenseTypeCombo.valueProperty().addListener((ignored1, ignored2, newVal) -> {
            if ("Mileage".equals(newVal)) {
                manualMiles.setDisable(false);
            } else {
                manualMiles.setDisable(true);
                manualMiles.clear();
            }
        });
    }

    // I removed the 'email' parameter from this method since I realized I wasn't actually using it anymore.
    public void setDriverProfile(String username) {
        this.currentUsername = username;
        welcomeLabel.setText("Driver: " + username);
        loadMyHistory();
    }

    @FXML
    private void calculateRoute() {
        String start = startAddress.getText();
        String end = endAddress.getText();

        if (start.isEmpty() || end.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Missing Info", "Please enter addresses.");
            return;
        }

        // I create the maps service here instead of keeping it open all the time to save resources
        GoogleMapsService mapsService = new GoogleMapsService();
        GoogleMapsService.RouteInfo route = mapsService.getRouteDetails(start, end);

        if (route != null) {
            updateUIWithRoute(route, start, end);
        } else {
            showAlert(Alert.AlertType.ERROR, "GPS Error", "Could not calculate route.");
        }
    }

    private void updateUIWithRoute(GoogleMapsService.RouteInfo route, String start, String end) {
        // I moved the rate variable here because this is the only place it's used
        double ratePerMile = 0.67;

        distLabel.setText(route.text);
        double cost = route.miles * ratePerMile;
        costLabel.setText(String.format("$%.2f", cost));

        manualMiles.setText(String.format("%.1f", route.miles));
        manualCost.setText(String.format("%.2f", cost));
        expenseTypeCombo.getSelectionModel().select("Mileage");

        Dotenv dotenv = Dotenv.load();
        String apiKey = dotenv.get("GOOGLE_MAPS_API_KEY");

        String mapUrl = "https://www.google.com/maps/embed/v1/directions" +
                "?key=" + apiKey +
                "&origin=" + start.replace(" ", "+") +
                "&destination=" + end.replace(" ", "+") +
                "&mode=driving";

        // I moved the messy HTML code into its own method (generateMapHtml)
        // to make this part easier to read and fix the "Long Method" warning.
        String htmlContent = generateMapHtml(mapUrl);
        mapWebView.getEngine().loadContent(htmlContent);
    }

    // This little helper method handles the HTML string creation
    private String generateMapHtml(String mapUrl) {
        // I used a "Text Block" (three quotes) to make the HTML look clean
        return """
            <!DOCTYPE html>
            <html>
            <head><style>body,html{margin:0;padding:0;height:100%%;overflow:hidden;}</style></head>
            <body>
            <iframe width='100%%' height='100%%' frameborder='0' style='border:0' src='%s' allowfullscreen></iframe>
            </body>
            </html>
            """.formatted(mapUrl);
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

        data.put("employeeName", currentUsername);
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
                .whereEqualTo("employeeName", currentUsername)
                .get();
        try {
            List<QueryDocumentSnapshot> docs = future.get().getDocuments();
            for (QueryDocumentSnapshot doc : docs) {
                myTrips.add(doc.toObject(Expense.class));
            }
            tripTable.setItems(myTrips);
        } catch (InterruptedException | ExecutionException e) {
            logger.error("Failed to load history", e);
            showAlert(Alert.AlertType.ERROR, "Data Error", "Could not load history.");
        }
    }

    @FXML
    private void showHelp() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Driver FAQ");
        alert.setHeaderText("Frequently Asked Questions");
        alert.setContentText(
                """
                Q: How is mileage calculated?
                A: We use Google Maps API to find the driving distance between points.
                
                Q: Can I edit a log after submitting?
                A: No. For security, logs are locked once submitted. Contact a manager to reject incorrect logs.
                
                Q: What do I do if the map is gray?
                A: Ensure you have an active internet connection.
                """
        );
        alert.getDialogPane().setPrefSize(400, 300);
        alert.showAndWait();
    }

    @FXML
    private void onLogout(ActionEvent event) {
        // Same fix here: using the shared SessionManager to handle logging out.
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        SessionManager.logout(stage);
    }

    private void showAlert(Alert.AlertType type, String title, String msg) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}