package brennan.transportauditlogin;

import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class ManagerDashboardController {

    // I switched to using a real logger here instead of System.out.println.
    // It helps me track errors better if something goes wrong with the database or exports.
    private static final Logger logger = LoggerFactory.getLogger(ManagerDashboardController.class);

    @FXML private Label welcomeLabel;

    @FXML private Label totalCostLabel;
    @FXML private Label pendingCountLabel;
    @FXML private Label totalMileageLabel;

    @FXML private DatePicker filterDate;
    @FXML private ComboBox<String> filterType;
    @FXML private TextField minMileageField;

    @FXML private TableView<Expense> expenseTable;
    @FXML private TableColumn<Expense, String> colEmployee;
    @FXML private TableColumn<Expense, String> colDate;
    @FXML private TableColumn<Expense, String> colType;
    @FXML private TableColumn<Expense, Double> colAmount;
    @FXML private TableColumn<Expense, Double> colMileage;
    @FXML private TableColumn<Expense, String> colStatus;

    // This list holds the data that gets shown in the table
    private final ObservableList<Expense> expenseList = FXCollections.observableArrayList();

    // This method runs automatically when the screen loads
    @FXML
    public void initialize() {
        setupTable();
        // I populate the filter dropdown here so I don't have to do it manually in SceneBuilder
        filterType.setItems(FXCollections.observableArrayList("All", "Mileage", "Fuel", "Maintenance", "Tolls", "Other"));
        filterType.getSelectionModel().selectFirst();
        loadData();
    }

    // Called by the Login screen to greet the manager by name
    public void setManagerName(String username) {
        if (welcomeLabel != null) {
            welcomeLabel.setText("Welcome, " + username);
        }
    }

    private void setupTable() {
        // These lines connect the table columns to the variables in my Expense class
        colEmployee.setCellValueFactory(new PropertyValueFactory<>("employeeName"));
        colDate.setCellValueFactory(new PropertyValueFactory<>("date"));
        colType.setCellValueFactory(new PropertyValueFactory<>("type"));
        colAmount.setCellValueFactory(new PropertyValueFactory<>("amount"));
        colMileage.setCellValueFactory(new PropertyValueFactory<>("mileage"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        // I used my FormatUtils helper class here.
        // It makes the cost column look like money ($10.00) instead of just a number (10.0).
        // This also fixed a "duplicate code" warning I was getting.
        colAmount.setCellFactory(FormatUtils.getCurrencyCellFactory());
    }

    @FXML
    private void loadData() {
        expenseList.clear(); // Clear the list first so we don't get duplicates
        Firestore db = FirestoreClient.getFirestore();
        ApiFuture<QuerySnapshot> future = db.collection("expenses").get();

        try {
            List<QueryDocumentSnapshot> documents = future.get().getDocuments();
            double totalCost = 0;
            double totalMiles = 0;
            int pendingCount = 0;

            double minMiles = parseMinMiles();

            for (DocumentSnapshot doc : documents) {
                // I convert the database document into an Expense object
                Expense expense = doc.toObject(Expense.class);

                // I added a check here just in case the data is bad, so the app doesn't crash
                if (expense != null) {
                    expense.setId(doc.getId());

                    // Only add the expense if it matches the filters the user set
                    if (matchesFilters(expense, minMiles)) {
                        expenseList.add(expense);
                        totalCost += expense.getAmount();
                        totalMiles += expense.getMileage();

                        // Count how many are pending so I can show the red alert number
                        if ("Pending".equals(expense.getStatus())) {
                            pendingCount++;
                        }
                    }
                }
            }

            // Sorting Logic:
            // I sort the list here so the newest dates always show up at the top.
            expenseList.sort((e1, e2) -> e2.getDate().compareTo(e1.getDate()));

            expenseTable.setItems(expenseList);
            updateAnalyticsLabels(totalCost, totalMiles, pendingCount);

        } catch (InterruptedException | ExecutionException e) {
            // Using the logger to report the error
            logger.error("Failed to load data", e);
        }
    }

    // Checks if an expense matches the selected filters (Date, Type, Min Miles)
    private boolean matchesFilters(Expense expense, double minMiles) {
        boolean dateMatch = (filterDate.getValue() == null) ||
                expense.getDate().equals(filterDate.getValue().toString());

        boolean typeMatch = filterType.getValue().equals("All") ||
                (filterType.getValue() != null && filterType.getValue().equals(expense.getType()));

        boolean mileageMatch = expense.getMileage() >= minMiles;

        return dateMatch && typeMatch && mileageMatch;
    }

    // Helper to safely get the number from the "Min Miles" text box
    private double parseMinMiles() {
        try {
            if (minMileageField.getText() != null && !minMileageField.getText().isEmpty()) {
                return Double.parseDouble(minMileageField.getText());
            }
        } catch (NumberFormatException e) {
            // If they type letters instead of numbers, just ignore it and use 0
        }
        return 0;
    }

    private void updateAnalyticsLabels(double cost, double miles, int pending) {
        totalCostLabel.setText(String.format("$%.2f", cost));
        totalMileageLabel.setText(String.format("%.1f mi", miles));
        pendingCountLabel.setText(String.valueOf(pending));
    }

    @FXML
    private void approveExpense() {
        updateStatus("Approved");
    }

    @FXML
    private void rejectExpense() {
        updateStatus("Rejected");
    }

    // Shared method to update status in Firebase so I don't write the same code twice
    private void updateStatus(String newStatus) {
        Expense selected = expenseTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("No Selection", "Please select an expense.");
            return;
        }

        FirestoreClient.getFirestore().collection("expenses").document(selected.getId()).update("status", newStatus);
        selected.setStatus(newStatus);
        expenseTable.refresh();
        loadData(); // Reload to reflect changes
    }

    @FXML
    private void exportPDF() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save PDF Report");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF Files", "*.pdf"));
        File file = fileChooser.showSaveDialog(null);

        if (file != null) {
            try {
                Document document = new Document();
                PdfWriter.getInstance(document, new FileOutputStream(file));
                document.open();

                document.add(new Paragraph("TransportAudit Expense Report"));
                document.add(new Paragraph("Generated on: " + java.time.LocalDate.now()));
                document.add(new Paragraph("--------------------------------------------------"));

                for (Expense e : expenseList) {
                    String line = String.format("Employee: %s | Type: %s | Amount: $%.2f | Miles: %.1f | Status: %s",
                            e.getEmployeeName(), e.getType(), e.getAmount(), e.getMileage(), e.getStatus());
                    document.add(new Paragraph(line));
                }

                document.close();
                showAlert("Success", "PDF Report exported successfully.");
            } catch (Exception e) {
                logger.error("PDF Export failed", e);
                showAlert("Error", "Could not export PDF.");
            }
        }
    }

    @FXML
    private void exportCSV() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save CSV Report");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        File file = fileChooser.showSaveDialog(null);

        if (file != null) {
            try (FileWriter writer = new FileWriter(file)) {
                // Write the header row
                writer.write("ID,Employee,Date,Type,Amount,Mileage,Status\n");
                // Write each expense row
                for (Expense e : expenseList) {
                    writer.write(String.format("%s,%s,%s,%s,%.2f,%.2f,%s\n",
                            e.getId(), e.getEmployeeName(), e.getDate(), e.getType(), e.getAmount(), e.getMileage(), e.getStatus()));
                }
                showAlert("Success", "CSV Report exported successfully.");
            } catch (IOException e) {
                logger.error("CSV Export failed", e);
            }
        }
    }

    @FXML
    private void generateTestData() {
        Firestore db = FirestoreClient.getFirestore();
        java.util.Map<String, Object> data = new HashMap<>();
        data.put("employeeName", "Demo User");
        data.put("date", java.time.LocalDate.now().toString());
        data.put("type", "Fuel");
        data.put("amount", 55.50);
        data.put("mileage", 120.5);
        data.put("status", "Pending");

        db.collection("expenses").add(data);
        loadData();
    }

    @FXML
    private void resetFilters() {
        filterDate.setValue(null);
        filterType.getSelectionModel().selectFirst();
        minMileageField.clear();
        loadData(); // Reloads all data since filters are gone
    }

    // Shows the FAQ popup window
    @FXML
    private void showHelp() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Manager FAQ");
        alert.setHeaderText("Manager Help & FAQ");

        // I used a "Text Block" here to make the long string easier to read in the code
        alert.setContentText(
                """
                Q: How do I export data?
                A: Use the 'Export to CSV' or 'Export to PDF' buttons at the bottom right.
                
                Q: Can I recover a rejected expense?
                A: No, but the driver can resubmit it as a new entry.
                
                Q: How do I filter by high mileage?
                A: Enter a number in the 'Min Miles' box and click Apply Filters.
                """
        );
        alert.getDialogPane().setPrefSize(400, 300);
        alert.showAndWait();
    }

    @FXML
    private void onLogout(ActionEvent event) {
        // I replaced the 9 lines of duplicated code with this single call to my helper class.
        // This fixes the "Duplicated Code" warning.
        Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
        SessionManager.logout(stage);
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}