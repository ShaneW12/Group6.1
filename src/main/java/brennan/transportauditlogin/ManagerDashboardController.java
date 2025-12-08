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
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public class ManagerDashboardController {

    @FXML private Label totalCostLabel;
    @FXML private Label pendingCountLabel;
    @FXML private Label totalMileageLabel;

    @FXML private DatePicker filterDate;
    @FXML private ComboBox<String> filterType;

    @FXML private TableView<Expense> expenseTable;
    @FXML private TableColumn<Expense, String> colEmployee;
    @FXML private TableColumn<Expense, String> colDate;
    @FXML private TableColumn<Expense, String> colType;
    @FXML private TableColumn<Expense, Double> colAmount;
    @FXML private TableColumn<Expense, Double> colMileage;
    @FXML private TableColumn<Expense, String> colStatus;

    private final ObservableList<Expense> expenseList = FXCollections.observableArrayList();

    @FXML
    public void initialize() {
        // Setup Table Columns
        colEmployee.setCellValueFactory(new PropertyValueFactory<>("employeeName"));
        colDate.setCellValueFactory(new PropertyValueFactory<>("date"));
        colType.setCellValueFactory(new PropertyValueFactory<>("type"));
        colAmount.setCellValueFactory(new PropertyValueFactory<>("amount"));
        colMileage.setCellValueFactory(new PropertyValueFactory<>("mileage"));
        colStatus.setCellValueFactory(new PropertyValueFactory<>("status"));

        // Setup Filter Options
        filterType.setItems(FXCollections.observableArrayList("All", "Fuel", "Maintenance", "Tolls", "Other"));
        filterType.getSelectionModel().selectFirst();

        // Load initial data
        loadData();
    }

    @FXML
    private void loadData() {
        expenseList.clear();
        Firestore db = FirestoreClient.getFirestore();

        // We get ALL expenses and filter locally (simplifies Firestore indexing)
        ApiFuture<QuerySnapshot> future = db.collection("expenses").get();

        try {
            List<QueryDocumentSnapshot> documents = future.get().getDocuments();
            double totalCost = 0;
            double totalMiles = 0;
            int pendingCount = 0;

            for (DocumentSnapshot doc : documents) {
                Expense expense = doc.toObject(Expense.class);
                expense.setId(doc.getId()); // Store ID for updates

                // --- Apply Filters ---
                boolean dateMatch = (filterDate.getValue() == null) ||
                        expense.getDate().equals(filterDate.getValue().toString());

                boolean typeMatch = filterType.getValue().equals("All") ||
                        (filterType.getValue() != null && filterType.getValue().equals(expense.getType()));

                if (dateMatch && typeMatch) {
                    expenseList.add(expense);

                    // --- Update Widgets ---
                    totalCost += expense.getAmount();
                    totalMiles += expense.getMileage();
                    if ("Pending".equals(expense.getStatus())) {
                        pendingCount++;
                    }
                }
            }

            expenseTable.setItems(expenseList);

            // Update UI Labels
            totalCostLabel.setText(String.format("$%.2f", totalCost));
            totalMileageLabel.setText(String.format("%.1f mi", totalMiles));
            pendingCountLabel.setText(String.valueOf(pendingCount));

        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }

    @FXML
    private void approveExpense() {
        updateExpenseStatus("Approved");
    }

    @FXML
    private void rejectExpense() {
        updateExpenseStatus("Rejected");
    }

    private void updateExpenseStatus(String newStatus) {
        Expense selected = expenseTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("No Selection", "Please select an expense from the table.");
            return;
        }

        Firestore db = FirestoreClient.getFirestore();
        try {
            // Update Firestore
            db.collection("expenses").document(selected.getId()).update("status", newStatus);
            // Update UI immediately
            selected.setStatus(newStatus);
            expenseTable.refresh();
            loadData(); // Re-calculate analytics
        } catch (Exception e) {
            e.printStackTrace();
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
                writer.write("ID,Employee,Date,Type,Amount,Mileage,Status\n");
                for (Expense e : expenseList) {
                    writer.write(String.format("%s,%s,%s,%s,%.2f,%.2f,%s\n",
                            e.getId(), e.getEmployeeName(), e.getDate(), e.getType(), e.getAmount(), e.getMileage(), e.getStatus()));
                }
                showAlert("Success", "CSV Report exported successfully.");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
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
                    String line = String.format("Employee: %s | Type: %s | Amount: $%.2f | Status: %s",
                            e.getEmployeeName(), e.getType(), e.getAmount(), e.getStatus());
                    document.add(new Paragraph(line));
                }

                document.close();
                showAlert("Success", "PDF Report exported successfully.");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @FXML
    private void generateTestData() {
        // Creates dummy data so you can see the dashboard work
        Firestore db = FirestoreClient.getFirestore();
        Map<String, Object> data = new HashMap<>();
        data.put("employeeName", "John Doe");
        data.put("date", java.time.LocalDate.now().toString());
        data.put("type", "Fuel");
        data.put("amount", 55.50);
        data.put("mileage", 120.5);
        data.put("status", "Pending");

        db.collection("expenses").add(data);
        loadData(); // Refresh
    }

    @FXML
    private void resetFilters() {
        filterDate.setValue(null);
        filterType.getSelectionModel().selectFirst();
        loadData();
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

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}