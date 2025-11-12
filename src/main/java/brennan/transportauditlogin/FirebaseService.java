package brennan.transportauditlogin;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

import java.io.IOException;
import java.io.InputStream;

public class FirebaseService {

    public static void initialize() {
        try {
            // The path to your service account key JSON file
            // This assumes the file is in 'src/main/resources'
            String serviceAccountPath = "/transportaudit-9d899-firebase-adminsdk-fbsvc-21bf609568.json";

            // Load the service account file from the resources
            InputStream serviceAccount = FirebaseService.class.getResourceAsStream(serviceAccountPath);

            if (serviceAccount == null) {
                throw new IOException("Cannot find " + serviceAccountPath + ". Make sure it's in src/main/resources/");
            }

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .setDatabaseUrl("https://transportaudit-9d899-default-rtdb.firebaseio.com") // You can find this in your Firebase console
                    .build();

            // Initialize the app only if it hasn't been initialized yet
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
                System.out.println("Firebase has been initialized.");
            }
        } catch (IOException e) {
            e.printStackTrace();
            // Handle initialization error (e.g., exit the app)
            System.exit(1);
        }
    }
}
