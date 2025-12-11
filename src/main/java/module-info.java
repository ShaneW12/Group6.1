module brennan.transportauditlogin {
    // JavaFX requirements
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;

    // Firebase & Google Cloud requirements
    requires firebase.admin;
    requires com.google.auth.oauth2;
    requires com.google.auth;
    requires google.cloud.firestore;
    requires com.google.api.apicommon;
    requires com.google.common;
    requires google.cloud.core;

    // Allows me to hide both cloud and firebase keys
    requires io.github.cdimascio.dotenv.java;

    // GSON requirement
    requires com.google.gson;

    // This line allows me to use Logger and LoggerFactory ---
    requires org.slf4j;

    // This provides the actual "Simple" implementation of the logger
    requires org.slf4j.simple;

    // For sending the password check
    requires java.net.http;

    // allows openpdf integration
    requires com.github.librepdf.openpdf;

    opens brennan.transportauditlogin to javafx.fxml, google.cloud.firestore;
    exports brennan.transportauditlogin;
}