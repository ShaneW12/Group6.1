package tec;

import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

public class TransportExpenseCalculator extends Application {


    private static final double GAS_PRICE_PER_GALLON = 2.96;
    private static final double MILES_PER_GALLON = 24.0;
    private static final double MAINTENANCE_COST_PER_100_MILES = 1.0;


    private TextField[] milesFields = new TextField[5];
    private TextField[] insuranceFields = new TextField[5];


    private Label[] breakdownLabels = new Label[5];
    private Label totalLabel = new Label();

    @Override
    public void start(Stage stage) {
        stage.setTitle("Transport Expense Management System");

        GridPane grid = new GridPane();
        grid.setPadding(new Insets(15));
        grid.setVgap(10);
        grid.setHgap(10);


        grid.add(new Label("Driver"), 0, 0);
        grid.add(new Label("Miles Travelled"), 1, 0);
        grid.add(new Label("Insurance Cost ($)"), 2, 0);


        for (int i = 0; i < 5; i++) {
            grid.add(new Label("Driver " + (i + 1)), 0, i + 1);

            milesFields[i] = new TextField();
            insuranceFields[i] = new TextField();

            grid.add(milesFields[i], 1, i + 1);
            grid.add(insuranceFields[i], 2, i + 1);
        }


        Button calcBtn = new Button("Calculate Expenses");
        grid.add(calcBtn, 0, 7);


        Label breakdownHeader = new Label("Expense Breakdown per Driver:");
        breakdownHeader.setStyle("-fx-font-weight: bold;");
        grid.add(breakdownHeader, 0, 8, 3, 1);


        for (int i = 0; i < 5; i++) {
            breakdownLabels[i] = new Label("Driver " + (i + 1) + " → ");
            grid.add(breakdownLabels[i], 0, 9 + i, 3, 1);
        }


        totalLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: blue;");
        grid.add(totalLabel, 0, 15, 3, 1);


        calcBtn.setOnAction(e -> calculateExpenses());


        Scene scene = new Scene(grid, 600, 420);
        stage.setScene(scene);
        stage.show();
    }


    private void calculateExpenses() {
        double totalCompanyExpense = 0;

        try {
            for (int i = 0; i < 5; i++) {
                double miles = Double.parseDouble(milesFields[i].getText());
                double insurance = Double.parseDouble(insuranceFields[i].getText());

                double gasCost = (miles / MILES_PER_GALLON) * GAS_PRICE_PER_GALLON;
                double maintenanceCost = (miles / 100.0) * MAINTENANCE_COST_PER_100_MILES;
                double totalDriverCost = gasCost + maintenanceCost + insurance;


                breakdownLabels[i].setText(String.format(
                        "Driver %d → Gas: $%.2f | Maintenance: $%.2f | Insurance: $%.2f | Total: $%.2f",
                        (i + 1), gasCost, maintenanceCost, insurance, totalDriverCost
                ));

                totalCompanyExpense += totalDriverCost;
            }


            totalLabel.setText(String.format("TOTAL EXPENSE FOR ALL DRIVERS: $%.2f", totalCompanyExpense));

        } catch (NumberFormatException e) {
            totalLabel.setText("⚠️ Please enter valid numeric values for miles and insurance.");
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
