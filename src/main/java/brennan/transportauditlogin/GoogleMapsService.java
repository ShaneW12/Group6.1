package brennan.transportauditlogin;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class GoogleMapsService {

    // REPLACE THIS WITH YOUR REAL GOOGLE MAPS API KEY
    private static final String API_KEY = "AIzaSyD4kEGu8TyGyznen04mwUtp5EacLaWXxA0";

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final Gson gson = new Gson();

    public RouteInfo getRouteDetails(String origin, String destination) {
        try {
            // Encode addresses (e.g., "New York" -> "New%20York")
            String encodedOrigin = URLEncoder.encode(origin, StandardCharsets.UTF_8);
            String encodedDest = URLEncoder.encode(destination, StandardCharsets.UTF_8);

            String url = String.format(
                    "https://maps.googleapis.com/maps/api/directions/json?origin=%s&destination=%s&key=%s",
                    encodedOrigin, encodedDest, API_KEY
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return parseRoute(response.body());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private RouteInfo parseRoute(String json) {
        try {
            JsonObject jsonObject = gson.fromJson(json, JsonObject.class);
            JsonObject route = jsonObject.getAsJsonArray("routes").get(0).getAsJsonObject();
            JsonObject leg = route.getAsJsonArray("legs").get(0).getAsJsonObject();

            // Extract Distance
            JsonObject distObj = leg.getAsJsonObject("distance");
            String distText = distObj.get("text").getAsString(); // e.g. "15.4 mi"
            double distValueMeters = distObj.get("value").getAsDouble();

            // Convert meters to miles
            double miles = distValueMeters * 0.000621371;

            return new RouteInfo(distText, miles);
        } catch (Exception e) {
            System.err.println("Error parsing Maps JSON: " + e.getMessage());
            return null;
        }
    }

    // Simple inner class to hold the result
    public static class RouteInfo {
        public String text;
        public double miles;

        public RouteInfo(String text, double miles) {
            this.text = text;
            this.miles = miles;
        }
    }
}