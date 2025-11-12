package Group6_otherCode;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.fxml.FXML;
import javafx.geometry.Side;
import javafx.scene.control.*;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class MapController {

    @FXML private TextField originField;
    @FXML private TextField destinationField;
    @FXML private Button clearRoute;
    @FXML private Button calculateBtn;
    @FXML private Label distanceLabel;
    @FXML private WebView mapView;

    private WebEngine webEngine;

    @FXML
    public void initialize() {
        autocomplete(originField);
        autocomplete(destinationField);

        webEngine = mapView.getEngine();

        String html = """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="utf-8" />
          <title>Leaflet Map</title>
          <link rel="stylesheet" href="https://unpkg.com/leaflet/dist/leaflet.css" />
          <script src="https://unpkg.com/leaflet/dist/leaflet.js"></script>
          <style>
            html, body, #map { height: 100%; margin: 0; padding: 0; }
          </style>
        </head>
        <body>
          <div id="map"></div>
          <script>
          function clearRoute() {
                      if (window.routeLine) {
                          map.removeLayer(window.routeLine);
                          window.routeLine = null;
                      }
                      if (window.originMarker) {
                          map.removeLayer(window.originMarker);
                          window.originMarker = null;
                      }
                      if (window.destMarker) {
                          map.removeLayer(window.destMarker);
                          window.destMarker = null;
                      }
                  }
          function drawRoute(coords) {
                      if (window.routeLine) {
                          map.removeLayer(window.routeLine);
                      }
                
                      window.routeLine = L.polyline(coords, {
                          color: 'blue',
                          weight: 4
                      }).addTo(map);
                
                      map.fitBounds(window.routeLine.getBounds());
                  }
            var map = L.map('map').setView([40.7128, -74.0060], 10);

            L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
              maxZoom: 19,
              attribution: '© OpenStreetMap'
            }).addTo(map);

            window.originMarker = null;
            window.destMarker = null;
            window.routeLine = null;

            function updateMap(lat1, lon1, lat2, lon2) {
              if (window.originMarker) map.removeLayer(window.originMarker);
              if (window.destMarker) map.removeLayer(window.destMarker);
              if (window.routeLine) map.removeLayer(window.routeLine);

              window.originMarker = L.marker([lat1, lon1]).addTo(map);
              window.destMarker = L.marker([lat2, lon2]).addTo(map);

              window.routeLine = L.polyline([[lat1, lon1], [lat2, lon2]], {color: 'red'}).addTo(map);

              map.fitBounds(window.routeLine.getBounds());
            }
          </script>
        </body>
        </html>
        """;

        webEngine.loadContent(html);
    }
    @FXML
    public void onClearRoute() {
        webEngine.executeScript("clearRoute();");
        distanceLabel.setText("Distance: ");
    }

    @FXML
    public void onCalculateDistance() {
        String origin = originField.getText();
        String destination = destinationField.getText();

        if (origin.isEmpty() || destination.isEmpty()) {
            distanceLabel.setText("Please enter both locations.");
            return;
        }

        try {
            // Geocode origin
            double[] originCoords = geocode(origin);
            if (originCoords == null) {
                distanceLabel.setText("Could not find origin.");
                return;
            }

            // Geocode destination
            double[] destCoords = geocode(destination);
            if (destCoords == null) {
                distanceLabel.setText("Could not find destination.");
                return;
            }

            double lat1 = originCoords[0];
            double lon1 = originCoords[1];
            double lat2 = destCoords[0];
            double lon2 = destCoords[1];

            // ✅ Get driving route from OSRM
            JsonObject routeData = getRoute(lat1, lon1, lat2, lon2);

            JsonObject route = routeData
                    .getAsJsonArray("routes")
                    .get(0)
                    .getAsJsonObject();

            // ✅ Driving distance (meters → miles)
            double meters = route.get("distance").getAsDouble();
            double miles = meters * 0.000621371;

            distanceLabel.setText(String.format("Distance: %.2f miles (driving)", miles));

            // ✅ Extract full geometry polyline
            JsonArray coords = route
                    .getAsJsonObject("geometry")
                    .getAsJsonArray("coordinates");

            // ✅ Convert coordinates to Leaflet array format
            StringBuilder jsArray = new StringBuilder("[");
            for (int i = 0; i < coords.size(); i++) {
                JsonArray pair = coords.get(i).getAsJsonArray();
                double lon = pair.get(0).getAsDouble();
                double lat = pair.get(1).getAsDouble();
                jsArray.append("[").append(lat).append(",").append(lon).append("]");
                if (i < coords.size() - 1) jsArray.append(",");
            }
            jsArray.append("]");

            // ✅ Send route polyline to JavaScript
            String js = "drawRoute(" + jsArray + ");";
            webEngine.executeScript(js);

        } catch (Exception e) {
            e.printStackTrace();
            distanceLabel.setText("Error occurred.");
        }
    }

    private double[] geocode(String address) throws IOException {
        String encoded = URLEncoder.encode(address, StandardCharsets.UTF_8);
        String urlStr = "https://nominatim.openstreetmap.org/search?q=" + encoded + "&format=json&limit=1";

        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestProperty("User-Agent", "JavaFXApp"); // required

        Scanner sc = new Scanner(conn.getInputStream());
        StringBuilder sb = new StringBuilder();
        while (sc.hasNext()) sb.append(sc.nextLine());
        sc.close();

        JsonArray arr = JsonParser.parseString(sb.toString()).getAsJsonArray();
        if (arr.size() == 0) return null;

        JsonObject obj = arr.get(0).getAsJsonObject();
        double lat = obj.get("lat").getAsDouble();
        double lon = obj.get("lon").getAsDouble();

        return new double[]{lat, lon};
    }

    private double haversine(double lat1, double lon1, double lat2, double lon2) {
        final double R = 3958.8; // km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon/2) * Math.sin(dLon/2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private void autocomplete(TextField field) {
        final ContextMenu menu = new ContextMenu();

        final java.util.Map<String, java.util.List<MenuItem>> cache = new java.util.HashMap<>();
        final long[] lastRequestTime = {0};

        field.textProperty().addListener((obs, oldText, newText) -> {
            if (newText.isBlank()) {
                menu.hide();
                return;
            }

            long timeOfRequest = System.currentTimeMillis();
            lastRequestTime[0] = timeOfRequest;

            // Debounce: wait 150 ms before making request
            new Thread(() -> {
                try {
                    Thread.sleep(150);

                    // If user typed again, cancel this request
                    if (lastRequestTime[0] != timeOfRequest) {
                        return;
                    }

                    String key = newText.trim().toLowerCase();

                    // Cache hit
                    if (cache.containsKey(key)) {
                        java.util.List<MenuItem> cachedItems = cache.get(key);

                        javafx.application.Platform.runLater(() -> {
                            menu.getItems().setAll(cachedItems);
                            if (!cachedItems.isEmpty()) {
                                menu.show(field, Side.BOTTOM, 0, 0);
                            } else {
                                menu.hide();
                            }
                        });
                        return;
                    }

                    // Network request if not cached
                    String encoded = URLEncoder.encode(newText, StandardCharsets.UTF_8);
                    String urlStr = "https://photon.komoot.io/api/?limit=5&q=" + encoded;

                    HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
                    conn.setRequestProperty("User-Agent", "JavaFXApp");

                    Scanner sc = new Scanner(conn.getInputStream());
                    StringBuilder sb = new StringBuilder();
                    while (sc.hasNext()) sb.append(sc.nextLine());
                    sc.close();

                    JsonObject json = JsonParser.parseString(sb.toString()).getAsJsonObject();
                    JsonArray features = json.getAsJsonArray("features");

                    java.util.List<MenuItem> items = new java.util.ArrayList<>();

                    for (int i = 0; i < features.size(); i++) {
                        JsonObject prop = features.get(i)
                                .getAsJsonObject()
                                .getAsJsonObject("properties");

                        // USA only
                        if (!prop.has("countrycode") ||
                                !prop.get("countrycode").getAsString().equalsIgnoreCase("US")) {
                            continue;
                        }

                        StringBuilder sbLabel = new StringBuilder(prop.get("name").getAsString());
                        if (prop.has("street")) sbLabel.append(", ").append(prop.get("street").getAsString());
                        if (prop.has("city")) sbLabel.append(", ").append(prop.get("city").getAsString());
                        if (prop.has("state")) sbLabel.append(", ").append(prop.get("state").getAsString());
                        sbLabel.append(", USA");

                        final String labelText = sbLabel.toString();
                        MenuItem item = new MenuItem(labelText);
                        item.setOnAction(e -> {
                            field.setText(labelText);
                            menu.hide();
                        });

                        items.add(item);
                    }

                    // Save to cache
                    cache.put(key, items);

                    // Ensure this is still the latest request
                    if (lastRequestTime[0] != timeOfRequest) {
                        return;
                    }

                    // Update UI
                    javafx.application.Platform.runLater(() -> {
                        menu.getItems().setAll(items);
                        if (!items.isEmpty()) {
                            menu.show(field, Side.BOTTOM, 0, 0);
                        } else {
                            menu.hide();
                        }
                    });

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
        });
    }

    private JsonObject getRoute(double lat1, double lon1, double lat2, double lon2) throws IOException {
        String urlStr = String.format(
                "http://router.project-osrm.org/route/v1/driving/%f,%f;%f,%f?overview=full&geometries=geojson",
                lon1, lat1, lon2, lat2
        );

        HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
        conn.setRequestProperty("User-Agent", "JavaFXApp");

        Scanner sc = new Scanner(conn.getInputStream());
        StringBuilder sb = new StringBuilder();
        while (sc.hasNext()) sb.append(sc.nextLine());
        sc.close();

        JsonObject json = JsonParser.parseString(sb.toString()).getAsJsonObject();
        return json;
    }
}
