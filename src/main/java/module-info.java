module brennan.transportauditlogin {
    // JavaFX requirements
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web; // Added: For the group's WebView/Map

    // Firebase & Google Cloud requirements
    requires firebase.admin;
    requires com.google.auth.oauth2;
    requires com.google.auth;
    requires google.cloud.firestore;
    requires com.google.api.apicommon;
    requires com.google.common;
    requires google.cloud.core;

    // GSON requirement (for Group6_otherCode.MapController)
    requires com.google.gson; // <-- THIS IS THE NEW LINE FOR GSON

    // Logger requirement (from pom.xml)
    requires org.slf4j.simple; // <-- Added: For the SLF4J logger

    // NEW: For sending the password check
    requires java.net.http;

    // This allows JavaFX FXML to access your controller classes
    opens brennan.transportauditlogin to javafx.fxml;

    // This exports your package so the JavaFX launcher can start it
    exports brennan.transportauditlogin;
}