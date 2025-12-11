package brennan.transportauditlogin;

import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.util.Callback;

// This class helps format the money columns so I don't have to write the same code in both dashboards.
public class FormatUtils {

    // Makes a "Cell Factory" to format Double numbers as Currency ($0.00).
    // The <T> part means it works for any type of list (Driver or Manager view).
    @SuppressWarnings("unused") // Ignores the warning about the 'unused' variable below
    public static <T> Callback<TableColumn<T, Double>, TableCell<T, Double>> getCurrencyCellFactory() {

        // JavaFX requires this parameter, but I don't need it, so I named it 'unused'.
        return unused -> new TableCell<>() {

            // This runs automatically to update the cell text whenever the table changes.
            @Override
            protected void updateItem(Double item, boolean empty) {
                super.updateItem(item, empty); // Keeps the normal table stuff working

                // If the row is empty or has no data, show nothing.
                if (empty || item == null) {
                    setText(null);
                } else {
                    // Formats the number with a $ and 2 decimal places.
                    setText(String.format("$%.2f", item));
                }
            }
        };
    }
}