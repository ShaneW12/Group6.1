package brennan.transportauditlogin;

public class Expense {
    private String id; // Firestore Document ID
    private String employeeName;
    private String date;
    private String type; // e.g., "Fuel", "Maintenance"
    private double amount;
    private double mileage;
    private String status; // "Pending", "Approved", "Rejected"

    // Empty constructor for Firestore
    public Expense() {
    }

    public Expense(String id, String employeeName, String date, String type, double amount, double mileage, String status) {
        this.id = id;
        this.employeeName = employeeName;
        this.date = date;
        this.type = type;
        this.amount = amount;
        this.mileage = mileage;
        this.status = status;
    }

    // Getters and Setters are REQUIRED for TableView
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getEmployeeName() {
        return employeeName;
    }

    public void setEmployeeName(String employeeName) {
        this.employeeName = employeeName;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    public double getMileage() {
        return mileage;
    }

    public void setMileage(double mileage) {
        this.mileage = mileage;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}