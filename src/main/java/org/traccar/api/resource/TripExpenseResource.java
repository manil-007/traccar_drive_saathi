package org.traccar.api.resource;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.traccar.config.Config;
import org.traccar.config.Keys;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import java.util.stream.Collectors;

@Path("tripExpense")
@Produces(MediaType.APPLICATION_JSON)
@Consumes({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_FORM_URLENCODED })
public class TripExpenseResource {

    @Inject
    private Config config;

    private String getOlaApiKey() {
        try {
            String k = config.getString(Keys.OLA_MAPS_API_KEY);
            if (k == null || k.isBlank()) {
                LOGGER.warn("Ola Maps API key (ola.maps.apiKey) is missing from configuration");
                return "";
            }
            return k;
        } catch (Exception e) {
            LOGGER.warn("Failed to read Ola Maps API key from config: {}", e.getMessage());
            return "";
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(TripExpenseResource.class);
    private String lastRouteError = null;

    public TripExpenseResource() {
    }

    record RequestPayload(String start, String destination, String vehicle_type, Double mileage,
            Double fuel_tank_capacity, Double border_expense, Double loading_unloading,
            Double tyre, Boolean loaded, Double incentive, Double da_amount_per_day,
            Double kilometers_per_day, Integer journey_time_days) {
    }

    @POST
    public Response calculate(@jakarta.ws.rs.core.Context jakarta.servlet.http.HttpServletRequest servletRequest) {
        try {
            // Guard: servletRequest may be null in some hosting environments/tools
            if (servletRequest == null) {
                return Response.status(Response.Status.BAD_REQUEST).entity(Map.of("error", "empty_body")).build();
            }
            // Build payload map from either JSON body or form parameters
            Map<String, Object> payload = new HashMap<>();
            String contentType = servletRequest != null && servletRequest.getContentType() != null
                    ? servletRequest.getContentType().toLowerCase()
                    : "";
            if (contentType.startsWith("application/x-www-form-urlencoded")) {
                // parse form parameters
                var params = servletRequest.getParameterMap();
                for (var e : params.entrySet()) {
                    String k = e.getKey();
                    String[] vals = e.getValue();
                    if (vals != null && vals.length > 0) {
                        payload.put(k, vals[0]);
                    }
                }
            } else {
                // try read JSON body
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(servletRequest.getInputStream(), StandardCharsets.UTF_8))) {
                    String text = br.lines().collect(Collectors.joining("\n"));
                    if (text != null && !text.isBlank()) {
                        try {
                            var obj = new org.json.JSONObject(text);
                            for (String key : obj.keySet()) {
                                payload.put(key, obj.get(key));
                            }
                        } catch (Exception ex) {
                            // ignore JSON parse error and continue with empty payload
                        }
                    }
                } catch (Exception ex) {
                    // ignore reading body
                }
                // fallback to query/form params if body empty
                if (payload.isEmpty() && servletRequest != null) {
                    var params = servletRequest.getParameterMap();
                    for (var e : params.entrySet()) {
                        String k = e.getKey();
                        String[] vals = e.getValue();
                        if (vals != null && vals.length > 0) {
                            payload.put(k, vals[0]);
                        }
                    }
                }
            }

            if (payload == null || !payload.containsKey("vehicle_type") || payload.get("vehicle_type") == null
                    || !payload.containsKey("mileage") || payload.get("mileage") == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "Missing required fields: vehicle_type and mileage"))
                        .build();
            }

            boolean hasText = payload.containsKey("start") && payload.get("start") != null
                    && payload.containsKey("destination")
                    && payload.get("destination") != null;
            boolean hasCoords = payload.containsKey("start_coord") && payload.get("start_coord") != null
                    && payload.containsKey("dest_coord")
                    && payload.get("dest_coord") != null;
            if (hasText == hasCoords) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error",
                                "Provide either start/destination text OR start_coord/dest_coord, not both"))
                        .build();
            }

            String start = null;
            String destination = null;
            if (payload.containsKey("start") && payload.get("start") != null) {
                start = payload.get("start").toString();
            }
            if (payload.containsKey("destination") && payload.get("destination") != null) {
                destination = payload.get("destination").toString();
            }
            String vehicleType = payload.get("vehicle_type") != null ? payload.get("vehicle_type").toString() : "";
            double mileage = toDouble(payload.get("mileage"));

            if (!payload.containsKey("fuel_type") || payload.get("fuel_type") == null) {
                payload.put("fuel_type", "petrol");
            }
            String fuelType = payload.get("fuel_type") != null ? payload.get("fuel_type").toString() : "";

            double fuelTankCapacity = payload.containsKey("fuel_tank_capacity")
                    ? toDouble(payload.get("fuel_tank_capacity"))
                    : 200.0;

            Map<String, Object> route = null;
            List<Double> startCoordParsed = null;
            List<Double> destCoordParsed = null;
            if (payload.containsKey("start_coord") && payload.containsKey("dest_coord")) {
                startCoordParsed = parseCoord(payload.get("start_coord"));
                destCoordParsed = parseCoord(payload.get("dest_coord"));
            }
            if (startCoordParsed != null && destCoordParsed != null) {
                route = getRouteUsingCoords(startCoordParsed, destCoordParsed);
                if (start == null) {
                    start = startCoordParsed.get(1) + "," + startCoordParsed.get(0);
                }
                if (destination == null) {
                    destination = destCoordParsed.get(1) + "," + destCoordParsed.get(0);
                }
            } else {
                route = getRoute(start, destination);
            }
            if (route == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "Failed to compute route: " + lastRouteError))
                        .build();
            }

            double distanceKm = toDouble(route.getOrDefault("distance_km", 0.0));
            @SuppressWarnings("unchecked")
            List<List<Double>> points = (List<List<Double>>) route.getOrDefault("points", List.of());

            if (points.isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "Route returned no points"))
                        .build();
            }

            // cumulative distances intentionally not used currently

            List<Map<String, Object>> tolls = loadTollsResource("data/nhai_toll_data.json");

            // simplified fuel handling (same as original)
            List<Map<String, Object>> refuels = new ArrayList<>();
            double totalFuelCost = 0.0;

            double totalFuelNeeded = distanceKm / mileage;
            Double userTotalFuelCost = null;
            if (payload.containsKey("fuel_cost")) {
                try {
                    userTotalFuelCost = toDouble(payload.get("fuel_cost"));
                } catch (Exception ignored) {
                }
            }

            Double userPerLitrePrice = null;
            for (String key : new String[]{"fuel_price", "fuel_rate", "price_per_litre"}) {
                if (payload.containsKey(key)) {
                    double v = toDouble(payload.get(key));
                    if (v > 0) {
                        userPerLitrePrice = v;
                        break;
                    }
                }
            }

            double usedPrice = 0.0;
            if (userTotalFuelCost != null && userTotalFuelCost > 0) {
                totalFuelCost = userTotalFuelCost;
                if (totalFuelNeeded > 0) {
                    usedPrice = totalFuelCost / totalFuelNeeded;
                } else {
                    usedPrice = 0.0;
                }
                LOGGER.info("Using user-provided total fuel cost: {} (implied price_per_litre={})",
                        round(totalFuelCost, 2), round(usedPrice, 2));
            } else if (userPerLitrePrice != null && userPerLitrePrice > 0) {
                usedPrice = userPerLitrePrice;
                totalFuelCost = totalFuelNeeded * usedPrice;
                LOGGER.info("Using user-provided per-litre fuel price: {} per litre", round(usedPrice, 2));
            } else {
                List<Map<String, Object>> fuelPrices = loadFuelPricesResource("data/fuel_prices.json", fuelType);
                Map<String, Object> delhiEntry = null;
                for (var e : fuelPrices) {
                    if ("Delhi".equalsIgnoreCase((String) e.getOrDefault("state", ""))) {
                        delhiEntry = e;
                        break;
                    }
                }
                if (delhiEntry != null) {
                    usedPrice = toDouble(delhiEntry.getOrDefault("price", 0.0));
                    totalFuelCost = totalFuelNeeded * usedPrice;
                } else if (!fuelPrices.isEmpty()) {
                    usedPrice = toDouble(fuelPrices.get(0).getOrDefault("price", 0.0));
                    totalFuelCost = totalFuelNeeded * usedPrice;
                } else {
                    usedPrice = 110.0; // hardcoded fallback
                    totalFuelCost = totalFuelNeeded * usedPrice;
                }
            }

            refuels.add(Map.of("price_per_litre", round(usedPrice, 2), "cost", round(totalFuelCost, 2),
                    "litres_needed", round(totalFuelNeeded, 2), "source", ""));

            String tollKey = mapVehicleType(vehicleType);
            double tollTotal = 0.0;
            List<Map<String, Object>> tollDetails = new ArrayList<>();

            try {
                java.util.Set<Object> seen = new java.util.HashSet<>();
                for (var toll : tolls) {
                    if (toll == null) {
                        continue;
                    }

                    Object locObj = toll.get("location");
                    double tollLat = Double.NaN, tollLon = Double.NaN;

                    if (locObj instanceof Map<?, ?> locMap) {
                        Map<?, ?> lm = (Map<?, ?>) locMap;
                        if (lm.containsKey("lat") || lm.containsKey("latitude") || lm.containsKey("lng")
                                || lm.containsKey("lon") || lm.containsKey("longitude")) {
                            Object latObj = lm.containsKey("lat") ? lm.get("lat")
                                    : (lm.containsKey("latitude") ? lm.get("latitude") : null);
                            Object lonObj = lm.containsKey("lon") ? lm.get("lon")
                                    : (lm.containsKey("longitude") ? lm.get("longitude")
                                            : (lm.containsKey("lng") ? lm.get("lng") : null));
                            tollLat = toDouble(latObj);
                            tollLon = toDouble(lonObj);
                        }
                    } else if (locObj instanceof List<?> locList && locList.size() >= 2) {
                        double a = toDouble(locList.get(0));
                        double b = toDouble(locList.get(1));
                        if (Math.abs(a) <= 90 && Math.abs(b) <= 180) {
                            tollLat = a;
                            tollLon = b;
                        } else {
                            tollLat = b;
                            tollLon = a;
                        }
                    } else {
                        Object latObj = toll.get("lat") != null ? toll.get("lat") : toll.get("latitude");
                        Object lonObj = toll.get("lon") != null ? toll.get("lon") : toll.get("longitude");
                        if (latObj != null && lonObj != null) {
                            tollLat = toDouble(latObj);
                            tollLon = toDouble(lonObj);
                        }
                    }

                    if (Double.isNaN(tollLat) || Double.isNaN(tollLon) || (tollLat == 0.0 && tollLon == 0.0)) {
                        continue;
                    }

                    double minDist = minDistanceToRoute(tollLat, tollLon, points);
                    if (minDist <= 2.0) {
                        Object id = toll.getOrDefault("id", toll.get("name"));
                        if (id != null && seen.contains(id)) {
                            continue;
                        }
                        if (id != null) {
                            seen.add(id);
                        }

                        double fee = extractTollFee(toll, tollKey);
                        if (fee > 0.0) {
                            tollTotal += fee;
                            Map<String, Object> td = new HashMap<>();
                            td.put("name", toll.getOrDefault("name", "<unknown>"));
                            td.put("lat", tollLat);
                            td.put("lon", tollLon);
                            td.put("fee", fee);
                            td.put("distance_from_route_km", round(minDist, 2));
                            tollDetails.add(td);
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("Error while matching toll plazas: {}", e.getMessage());
            }

            double borderExpense = payload.getOrDefault("border_expense", 0) instanceof Number
                    ? ((Number) payload.getOrDefault("border_expense", 0)).doubleValue()
                    : Double.parseDouble(payload.getOrDefault("border_expense", "0").toString());
            double loading = payload.getOrDefault("loading_unloading", 0) instanceof Number
                    ? ((Number) payload.getOrDefault("loading_unloading", 0)).doubleValue()
                    : Double.parseDouble(payload.getOrDefault("loading_unloading", "0").toString());
            double tyre = payload.getOrDefault("tyre", 0) instanceof Number
                    ? ((Number) payload.getOrDefault("tyre", 0)).doubleValue()
                    : Double.parseDouble(payload.getOrDefault("tyre", "0").toString());
            double incentive = payload.getOrDefault("incentive", 0) instanceof Number
                    ? ((Number) payload.getOrDefault("incentive", 0)).doubleValue()
                    : Double.parseDouble(payload.getOrDefault("incentive", "0").toString());

            double da = 0.0;
            int journeyDays = 0;
            if (!payload.containsKey("kilometers_per_day") || payload.get("kilometers_per_day") == null
                    || toDouble(payload.get("kilometers_per_day")) <= 0.0) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "Missing or invalid required field: kilometers_per_day (must be > 0)"))
                        .build();
            }

            double kmPerDay = toDouble(payload.get("kilometers_per_day"));
            journeyDays = (int) Math.ceil(distanceKm / kmPerDay);
            if (journeyDays < 1) {
                journeyDays = 1;
            }

            double daPerDay = payload.containsKey("da_amount_per_day") && payload.get("da_amount_per_day") != null
                    ? toDouble(payload.get("da_amount_per_day"))
                    : 0.0;
            da = daPerDay * journeyDays;

            double additionalCost = payload.getOrDefault("additional_cost", 0) instanceof Number
                    ? ((Number) payload.getOrDefault("additional_cost", 0)).doubleValue()
                    : Double.parseDouble(payload.getOrDefault("additional_cost", "0").toString());

            double extrasTotal = borderExpense + loading + tyre + incentive + da + additionalCost;

            double totalExpense = tollTotal + totalFuelCost + extrasTotal;

            Map<String, Object> response = new HashMap<>();
            Map<String, Object> tripSummary = new HashMap<>();
            tripSummary.put("from", start);
            tripSummary.put("to", destination);
            tripSummary.put("vehicle_type", vehicleType);
            tripSummary.put("distance_km", round(distanceKm, 2));
            tripSummary.put("mileage", mileage);
            if (journeyDays > 0) {
                tripSummary.put("journey_time_days", journeyDays);
            }
            if (payload.containsKey("fuel_tank_capacity")) {
                tripSummary.put("fuel_tank_capacity", fuelTankCapacity);
            }
            response.put("trip_summary", tripSummary);

            response.put("tolls", Map.of("total_toll_cost", round(tollTotal, 2), "toll_details", tollDetails));
            response.put("fuel", Map.of("total_fuel_needed", round(totalFuelNeeded, 2), "refuels", refuels,
                    "total_fuel_cost", round(totalFuelCost, 2)));
            response.put("extras", Map.of(
                    "border_expense", borderExpense,
                    "loading_unloading", loading,
                    "tyre", tyre,
                    "incentive", incentive,
                    "da_trip_amount", da,
                    "additional_cost", additionalCost,
                    "total_extras", round(extrasTotal, 2)));
            response.put("final_cost", Map.of(
                    "toll", round(tollTotal, 2),
                    "fuel", round(totalFuelCost, 2),
                    "extras", round(extrasTotal, 2),
                    "total_trip_cost", round(totalExpense, 2)));

            return Response.ok(response).build();

        } catch (Exception e) {
            LOGGER.warn("Error in calculate: {}", e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(Map.of("error", e.getMessage()))
                    .build();
        }
    }

    // --- Ola Maps specific helpers: geocode, reverse geocode, and routing ---

    private Map<String, Object> getRoute(String start, String destination) throws Exception {
        // Use Ola Maps geocoding for start/destination then request directions
        List<Double> startCoord = geocode(start);
        List<Double> destCoord = geocode(destination);
        if (startCoord == null || destCoord == null) {
            lastRouteError = "geocode_failed_for_start_or_destination";
            return null;
        }

        var client = HttpClient.newHttpClient();
        // Use origin/destination query params (lat,lng) per Ola Maps spec. Our coords
        // are [lon,lat].
        String origin = String.format(java.util.Locale.ROOT, "%f,%f", startCoord.get(1), startCoord.get(0));
        String destinationQ = String.format(java.util.Locale.ROOT, "%f,%f", destCoord.get(1), destCoord.get(0));
        String url = "https://api.olamaps.io/routing/v1/directions?origin="
                + java.net.URLEncoder.encode(origin, StandardCharsets.UTF_8)
                + "&destination=" + java.net.URLEncoder.encode(destinationQ, StandardCharsets.UTF_8)
                + "&overview=full&steps=false&api_key=" + getOlaApiKey();

        LOGGER.info("Ola directions request URL: {}", url.length() > 500 ? url.substring(0, 500) : url);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("X-Request-Id", java.util.UUID.randomUUID().toString())
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
        System.out.println(resp.body());
        String text = resp.body();
        if (resp.statusCode() != 200) {
            lastRouteError = "directions_http_" + resp.statusCode();
            LOGGER.warn("Ola directions request failed: status={} body={}...", resp.statusCode(),
                    resp.body() == null ? "<empty>"
                            : (resp.body().length() > 500 ? resp.body().substring(0, 500) : resp.body()));
            return null;
        }

        double distance = 0.0;
        double duration = 0.0;
        List<List<Double>> points = new ArrayList<>();
        try {
            var obj = new org.json.JSONObject(text);
            // Ola Maps follows a similar structure: routes -> legs -> geometry or polyline
            var routes = obj.getJSONArray("routes");
            if (routes.length() > 0) {
                var route = routes.getJSONObject(0);
                // Ola may provide per-leg distances/durations. Sum them if present.
                double distanceMeters = 0.0;
                double durationSeconds = 0.0;
                if (route.has("summary")) {
                    try {
                        var summary = route.get("summary");
                        if (summary instanceof org.json.JSONObject) {
                            var s = (org.json.JSONObject) summary;
                            distanceMeters = s.optDouble("distance", 0.0);
                            durationSeconds = s.optDouble("duration", 0.0);
                        }
                    } catch (Exception ignored) {
                    }
                }
                if (route.has("legs")) {
                    try {
                        var legs = route.getJSONArray("legs");
                        for (int li = 0; li < legs.length(); li++) {
                            var leg = legs.getJSONObject(li);
                            distanceMeters += leg.optDouble("distance", 0.0);
                            durationSeconds += leg.optDouble("duration", 0.0);
                        }
                    } catch (Exception ignored) {
                    }
                }
                distance = distanceMeters / 1000.0;
                duration = durationSeconds / 3600.0;
                if (route.has("geometry")) {
                    Object geom = route.get("geometry");
                    if (geom instanceof String) {
                        String encoded = ((String) geom).trim();
                        // try decode with precision 6 then 5
                        var p6 = decodePolyline(encoded, 6);
                        if (!p6.isEmpty()) {
                            points = p6;
                        } else {
                            points = decodePolyline(encoded, 5);
                        }
                    } else if (geom instanceof org.json.JSONObject) {
                        var g = (org.json.JSONObject) geom;
                        if (g.has("coordinates")) {
                            var coords = g.getJSONArray("coordinates");
                            for (int i = 0; i < coords.length(); i++) {
                                var pair = coords.getJSONArray(i);
                                double lon = pair.getDouble(0);
                                double lat = pair.getDouble(1);
                                List<Double> p = new ArrayList<>();
                                p.add(lon);
                                p.add(lat);
                                points.add(p);
                            }
                        }
                    } else if (geom instanceof org.json.JSONArray) {
                        var coords = (org.json.JSONArray) geom;
                        for (int i = 0; i < coords.length(); i++) {
                            var pair = coords.getJSONArray(i);
                            double lon = pair.getDouble(0);
                            double lat = pair.getDouble(1);
                            List<Double> p = new ArrayList<>();
                            p.add(lon);
                            p.add(lat);
                            points.add(p);
                        }
                    }
                }
                // fallback: Ola may return an encoded overview polyline as 'overview_polyline'
                if (points.isEmpty() && route.has("overview_polyline")) {
                    try {
                        Object ov = route.get("overview_polyline");
                        if (ov instanceof String) {
                            String encoded = ((String) ov).trim();
                            var p6 = decodePolyline(encoded, 6);
                            if (!p6.isEmpty()) {
                                points = p6;
                            } else {
                                points = decodePolyline(encoded, 5);
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }
                // fallback: try to extract coordinates from legs->steps start/end locations
                if (points.isEmpty() && route.has("legs")) {
                    try {
                        var legs = route.getJSONArray("legs");
                        List<List<Double>> pts = new ArrayList<>();
                        for (int li = 0; li < legs.length(); li++) {
                            var leg = legs.getJSONObject(li);
                            if (leg.has("steps")) {
                                var steps = leg.getJSONArray("steps");
                                for (int si = 0; si < steps.length(); si++) {
                                    var step = steps.getJSONObject(si);
                                    if (step.has("start_location")) {
                                        var sl = step.getJSONObject("start_location");
                                        double lat = sl.optDouble("lat", Double.NaN);
                                        double lng = sl.optDouble("lng", Double.NaN);
                                        if (!Double.isNaN(lat) && !Double.isNaN(lng)) {
                                            pts.add(List.of(lng, lat));
                                        }
                                    }
                                    if (step.has("end_location")) {
                                        var el = step.getJSONObject("end_location");
                                        double lat = el.optDouble("lat", Double.NaN);
                                        double lng = el.optDouble("lng", Double.NaN);
                                        if (!Double.isNaN(lat) && !Double.isNaN(lng)) {
                                            pts.add(List.of(lng, lat));
                                        }
                                    }
                                }
                            } else {
                                // fall back to leg start/end
                                if (leg.has("start_location")) {
                                    var sl = leg.getJSONObject("start_location");
                                    double lat = sl.optDouble("lat", Double.NaN);
                                    double lng = sl.optDouble("lng", Double.NaN);
                                    if (!Double.isNaN(lat) && !Double.isNaN(lng)) {
                                        pts.add(List.of(lng, lat));
                                    }
                                }
                                if (leg.has("end_location")) {
                                    var el = leg.getJSONObject("end_location");
                                    double lat = el.optDouble("lat", Double.NaN);
                                    double lng = el.optDouble("lng", Double.NaN);
                                    if (!Double.isNaN(lat) && !Double.isNaN(lng)) {
                                        pts.add(List.of(lng, lat));
                                    }
                                }
                            }
                        }
                        // dedupe sequential duplicates
                        List<List<Double>> dedup = new ArrayList<>();
                        List<Double> prev = null;
                        for (var p : pts) {
                            if (prev == null || Math.abs(prev.get(0) - p.get(0)) > 1e-9
                                    || Math.abs(prev.get(1) - p.get(1)) > 1e-9) {
                                dedup.add(p);
                                prev = p;
                            }
                        }
                        points.addAll(dedup);
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception e) {
            lastRouteError = "directions_parse_error: " + e.getMessage();
            LOGGER.warn("Failed to parse Ola directions response: {}", e.getMessage());
        }

        // If we found no points, log raw response for debugging
        if (points.isEmpty()) {
            LOGGER.warn("Parsed route had no points. rawResponse={}", text == null ? "<empty>" : text);
            try {
                var rawObj = new org.json.JSONObject(text == null ? "{}" : text);
                LOGGER.warn("Full route JSON for debugging: {}", rawObj.toString(2));
            } catch (Exception ignored) {
            }
        }

        if (points.isEmpty()) {
            LOGGER.warn("Parsed route had no points. rawResponse={}", text == null ? "<empty>" : text);
            try {
                var rawObj = new org.json.JSONObject(text == null ? "{}" : text);
                LOGGER.warn("Full route JSON for debugging: {}", rawObj.toString(2));
            } catch (Exception ignored) {
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("distance_km", distance);
        result.put("duration_hr", duration);
        result.put("points", points);
        return result;
    }

    private List<Double> geocode(String text) throws Exception {
        var client = HttpClient.newHttpClient();
        String q = java.net.URLEncoder.encode(text, StandardCharsets.UTF_8);
        String url = "https://api.olamaps.io/places/v1/geocode?input=" + q + "&size=1&api_key=" + getOlaApiKey();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();
        HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            return null;
        }
        var obj = new org.json.JSONObject(resp.body());
        var features = obj.optJSONArray("features");
        if (features == null || features.length() == 0) {
            return null;
        }
        var feat = features.getJSONObject(0);
        var geom = feat.getJSONObject("geometry");
        var coords = geom.getJSONArray("coordinates");
        double lon = coords.getDouble(0);
        double lat = coords.getDouble(1);
        List<Double> c = new ArrayList<>();
        c.add(lon);
        c.add(lat);
        return c;
    }

    private List<Double> reverseGeocodePoint(Double lat, Double lon) {
        try {
            var client = HttpClient.newHttpClient();
            String q = String.format(java.util.Locale.ROOT, "%f,%f", lat, lon);
            String url = "https://api.olamaps.io/places/v1/reverse-geocode?location=" + q + "&size=1&api_key="
                    + getOlaApiKey();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                return null;
            }
            var obj = new org.json.JSONObject(resp.body());
            var features = obj.optJSONArray("features");
            if (features == null || features.length() == 0) {
                return null;
            }
            var feat = features.getJSONObject(0);
            if (!feat.has("geometry")) {
                return null;
            }
            var geom = feat.getJSONObject("geometry");
            if (!geom.has("coordinates")) {
                return null;
            }
            var coords = geom.getJSONArray("coordinates");
            double rlon = coords.getDouble(0);
            double rlat = coords.getDouble(1);
            return List.of(rlon, rlat);
        } catch (Exception e) {
            LOGGER.warn("reverseGeocodePoint failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Call Ola Maps snapToRoad API to align start and dest points to the nearest
     * routable points.
     * Returns a list of snapped [lon, lat] points (first..last) or null on failure.
     */
    private List<List<Double>> snapToRoadPoints(List<Double> startCoord, List<Double> destCoord) {
        try {
            if (startCoord == null || destCoord == null) {
                return null;
            }
            // startCoord/destCoord are [lon, lat], but SnapToRoad expects lat,lng
            String p1 = String.format(java.util.Locale.ROOT, "%f,%f", startCoord.get(1), startCoord.get(0));
            String p2 = String.format(java.util.Locale.ROOT, "%f,%f", destCoord.get(1), destCoord.get(0));
            String pointsParam = java.net.URLEncoder.encode(p1 + "|" + p2, StandardCharsets.UTF_8);
            String url = "https://api.olamaps.io/routing/v1/snapToRoad?points=" + pointsParam + "&api_key="
                    + getOlaApiKey();
            var client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .GET()
                    .build();
            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                return null;
            }
            var obj = new org.json.JSONObject(resp.body());
            var snapped = obj.optJSONArray("snapped_points");
            if (snapped == null || snapped.length() == 0) {
                return null;
            }
            List<List<Double>> out = new ArrayList<>();
            for (int i = 0; i < snapped.length(); i++) {
                var sp = snapped.getJSONObject(i);
                var loc = sp.getJSONObject("location");
                double lat = loc.optDouble("lat", Double.NaN);
                double lng = loc.optDouble("lng", Double.NaN);
                if (!Double.isNaN(lat) && !Double.isNaN(lng)) {
                    out.add(List.of(lng, lat));
                }
            }
            return out;
        } catch (Exception e) {
            LOGGER.warn("snapToRoadPoints failed: {}", e.getMessage());
            return null;
        }
    }

    private double toDouble(Object o) {
        if (o == null) {
            return 0.0;
        }
        if (o instanceof Number) {
            return ((Number) o).doubleValue();
        }
        try {
            return Double.parseDouble(o.toString());
        } catch (Exception e) {
            return 0.0;
        }
    }

    private List<Map<String, Object>> loadTollsResource(String path) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                return List.of();
            }
            String text = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8)).lines()
                    .collect(Collectors.joining("\n"));
            var obj = new org.json.JSONObject(text);
            var list = new ArrayList<Map<String, Object>>();
            if (obj.has("toll_plazas")) {
                var arr = obj.getJSONArray("toll_plazas");
                for (int i = 0; i < arr.length(); i++) {
                    var o = arr.getJSONObject(i);
                    list.add(o.toMap());
                }
            } else if (obj.has("plazas")) {
                var arr = obj.getJSONArray("plazas");
                for (int i = 0; i < arr.length(); i++) {
                    var o = arr.getJSONObject(i);
                    list.add(o.toMap());
                }
            } else {
                try {
                    var arr = new org.json.JSONArray(text);
                    for (int i = 0; i < arr.length(); i++) {
                        var o = arr.getJSONObject(i);
                        list.add(o.toMap());
                    }
                } catch (Exception e) {
                }
            }
            return list;
        } catch (IOException e) {
            return List.of();
        }
    }

    private List<Map<String, Object>> loadFuelPricesResource(String path, String fuelType) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                return List.of();
            }
            String text = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8)).lines()
                    .collect(Collectors.joining("\n"));
            var obj = new org.json.JSONObject(text);
            var list = new ArrayList<Map<String, Object>>();
            var states = obj.keySet();
            for (String state : states) {
                var citiesObj = obj.getJSONObject(state);
                for (var city : citiesObj.keySet()) {
                    var cityObj = citiesObj.getJSONObject(city);
                    if (!cityObj.has(fuelType)) {
                        continue;
                    }
                    Object priceObj = cityObj.isNull(fuelType) ? null : cityObj.get(fuelType);
                    if (priceObj == null) {
                        continue;
                    }
                    double price = toDouble(priceObj);
                    Map<String, Object> m = new HashMap<>();
                    m.put("state", state);
                    m.put("city", city);
                    m.put("price", price);
                    if (cityObj.has("location") && cityObj.get("location") instanceof org.json.JSONObject) {
                        var loc = cityObj.getJSONObject("location");
                        if (loc.has("lat") && loc.has("lng")) {
                            m.put("lat", loc.getDouble("lat"));
                            m.put("lon", loc.getDouble("lng"));
                        } else if (loc.has("lat") && loc.has("lon")) {
                            m.put("lat", loc.getDouble("lat"));
                            m.put("lon", loc.getDouble("lon"));
                        }
                    }
                    m.put("location", city);
                    list.add(m);
                }
            }
            return list;
        } catch (IOException e) {
            return List.of();
        }
    }

    // NOTE: cumulative distance and closest-point helpers removed because they
    // were not referenced by the current trip expense flow. Re-add if needed
    // in future enhancements.

    private String mapVehicleType(String t) {
        if (t == null) {
            return "car";
        }
        t = t.toLowerCase();
        return switch (t) {
            case "car", "van", "jeep" -> "car";
            case "lcv" -> "lcv";
            case "bus", "truck" -> "bus";
            case "multi axle", "multi_axle" -> "multi_axle";
            case "4 to 6 axle", "4to6_axle" -> "4to6_axle";
            case "7 or more axle", "7_or_more_axle" -> "7_or_more_axle";
            case "hcm eme", "hcm_eme" -> "hcm_eme";
            default -> "car";
        };
    }

    private double minDistanceToRoute(double lat, double lon, List<List<Double>> routePoints) {
        double min = Double.MAX_VALUE;
        for (var p : routePoints) {
            double d = haversine(lat, lon, p.get(1), p.get(0));
            if (d < min) {
                min = d;
            }
        }
        return min;
    }

    private double haversine(double lat1, double lon1, double lat2, double lon2) {
        double earthRadius = 6371; // km
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat1))
                * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadius * c;
    }

    private double round(double v, int places) {
        double scale = Math.pow(10, places);
        return Math.round(v * scale) / scale;
    }

    private double extractTollFee(Map<String, Object> toll, String tollKey) {
        if (toll == null) {
            return 0.0;
        }
        try {
            Object feesObj = toll.get("fees");
            if (feesObj instanceof Map) {
                Map<?, ?> fees = (Map<?, ?>) feesObj;
                Object vehicleObj = fees.get(tollKey);
                if (vehicleObj instanceof Map) {
                    Map<?, ?> veh = (Map<?, ?>) vehicleObj;
                    Object single = veh.get("single");
                    if (single instanceof Number) {
                        return ((Number) single).doubleValue();
                    }
                    if (single instanceof String) {
                        try {
                            return Double.parseDouble((String) single);
                        } catch (Exception ignored) {
                        }
                    }
                    for (String k : new String[]{"single", "singleFare", "fare", "amount"}) {
                        Object o = veh.get(k);
                        if (o instanceof Number) {
                            return ((Number) o).doubleValue();
                        }
                        if (o instanceof String) {
                            try {
                                return Double.parseDouble((String) o);
                            } catch (Exception ignored) {
                            }
                        }
                    }
                }
                Object direct = fees.get(tollKey);
                if (direct instanceof Number) {
                    return ((Number) direct).doubleValue();
                }
                if (direct instanceof String) {
                    try {
                        return Double.parseDouble((String) direct);
                    } catch (Exception ignored) {
                    }
                }
            }

            Object top = toll.get(tollKey);
            if (top instanceof Number) {
                return ((Number) top).doubleValue();
            }
            if (top instanceof String) {
                try {
                    return Double.parseDouble((String) top);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
        }
        return 0.0;
    }

    private List<List<Double>> decodePolyline(String encoded, int precision) {
        List<List<Double>> path = new ArrayList<>();
        if (encoded == null || encoded.isEmpty()) {
            return path;
        }
        int index = 0;
        int len = encoded.length();
        int lat = 0, lng = 0;
        int shift, result, b;
        int factor = (int) Math.pow(10, precision);
        while (index < len) {
            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20 && index < len);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20 && index < len);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            double latD = ((double) lat) / factor;
            double lngD = ((double) lng) / factor;
            List<Double> p = new ArrayList<>();
            p.add(lngD);
            p.add(latD);
            path.add(p);
        }
        return path;
    }

    private List<Double> parseCoord(Object input) {
        if (input == null) {
            return null;
        }
        try {
            if (input instanceof List<?> lst && lst.size() >= 2) {
                double a = toDouble(lst.get(0));
                double b = toDouble(lst.get(1));
                if (Math.abs(a) <= 90 && Math.abs(b) <= 180) {
                    return List.of(b, a);
                } else {
                    return List.of(a, b);
                }
            }
            if (input instanceof String s) {
                String str = s.trim();
                if (str.contains(",")) {
                    String[] parts = str.split("\\s*,\\s*");
                    if (parts.length >= 2) {
                        double first = Double.parseDouble(parts[0]);
                        double second = Double.parseDouble(parts[1]);
                        if (Math.abs(first) <= 90 && Math.abs(second) <= 180) {
                            return List.of(second, first);
                        } else {
                            return List.of(first, second);
                        }
                    }
                }
            }
            if (input instanceof Map<?, ?> m) {
                Object latObj = m.containsKey("lat") ? m.get("lat")
                        : (m.containsKey("latitude") ? m.get("latitude") : null);
                Object lonObj = m.containsKey("lon") ? m.get("lon")
                        : (m.containsKey("longitude") ? m.get("longitude") : null);
                if (latObj != null && lonObj != null) {
                    double lat = toDouble(latObj);
                    double lon = toDouble(lonObj);
                    return List.of(lon, lat);
                }
            }
        } catch (Exception e) {
            LOGGER.warn("parseCoord failed: {}", e.getMessage());
        }
        return null;
    }

    private Map<String, Object> getRouteUsingCoords(List<Double> startCoord, List<Double> destCoord) throws Exception {
        if (startCoord == null || destCoord == null || startCoord.size() < 2 || destCoord.size() < 2) {
            lastRouteError = "invalid_coords";
            return null;
        }

        var client = HttpClient.newHttpClient();
        String origin = String.format(java.util.Locale.ROOT, "%f,%f", startCoord.get(1), startCoord.get(0));
        String destinationQ = String.format(java.util.Locale.ROOT, "%f,%f", destCoord.get(1), destCoord.get(0));
        String url = "https://api.olamaps.io/routing/v1/directions?origin="
                + java.net.URLEncoder.encode(origin, StandardCharsets.UTF_8)
                + "&destination=" + java.net.URLEncoder.encode(destinationQ, StandardCharsets.UTF_8)
                + "&overview=full&steps=false&api_key=" + getOlaApiKey();

        LOGGER.info("Ola directions request URL (coords): {}", url.length() > 500 ? url.substring(0, 500) : url);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("X-Request-Id", java.util.UUID.randomUUID().toString())
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
        String text = resp.body();
        if (resp.statusCode() != 200) {
            String bodyText = resp.body() == null ? "" : resp.body();
            LOGGER.warn("Ola directions request failed: status={} body={}...", resp.statusCode(),
                    bodyText.length() > 500 ? bodyText.substring(0, 500) : bodyText);
            // Try snapping points to roads via SnapToRoad API when route not found
            if (resp.statusCode() == 404 || bodyText.contains("Route Not Found")
                    || bodyText.contains("Could not find routable point")) {
                try {
                    List<List<Double>> snapped = snapToRoadPoints(startCoord, destCoord);
                    if (snapped != null && snapped.size() >= 2) {
                        var snappedStart = snapped.get(0);
                        var snappedDest = snapped.get(snapped.size() - 1);
                        LOGGER.info("SnapToRoad returned snappedStart={} snappedDest={}", snappedStart, snappedDest);
                        String origin2 = String.format(java.util.Locale.ROOT, "%f,%f", snappedStart.get(0),
                                snappedStart.get(1));
                        String destination2 = String.format(java.util.Locale.ROOT, "%f,%f", snappedDest.get(0),
                                snappedDest.get(1));
                        String url2 = "https://api.olamaps.io/routing/v1/directions?origin="
                                + java.net.URLEncoder.encode(origin2, StandardCharsets.UTF_8)
                                + "&destination=" + java.net.URLEncoder.encode(destination2, StandardCharsets.UTF_8)
                                + "&overview=full&steps=false&api_key=" + getOlaApiKey();
                        HttpRequest request2 = HttpRequest.newBuilder()
                                .uri(URI.create(url2))
                                .timeout(Duration.ofSeconds(30))
                                .header("X-Request-Id", java.util.UUID.randomUUID().toString())
                                .header("Accept", "application/json")
                                .POST(HttpRequest.BodyPublishers.noBody())
                                .build();
                        HttpResponse<String> resp2 = client.send(request2, HttpResponse.BodyHandlers.ofString());
                        text = resp2.body();
                        if (resp2.statusCode() != 200) {
                            lastRouteError = "directions_http_" + resp2.statusCode();
                            return null;
                        }
                    } else {
                        // fallback: attempt reverse geocode as previous behavior
                        List<Double> snappedStart = reverseGeocodePoint(startCoord.get(1), startCoord.get(0));
                        List<Double> snappedDest = reverseGeocodePoint(destCoord.get(1), destCoord.get(0));
                        if (snappedStart != null && snappedDest != null) {
                            LOGGER.info("Snapped coords (reverse geocode): start={} dest={}", snappedStart,
                                    snappedDest);
                            String origin2 = String.format(java.util.Locale.ROOT, "%f,%f", snappedStart.get(0),
                                    snappedStart.get(1));
                            String destination2 = String.format(java.util.Locale.ROOT, "%f,%f", snappedDest.get(0),
                                    snappedDest.get(1));
                            String url2 = "https://api.olamaps.io/routing/v1/directions?origin="
                                    + java.net.URLEncoder.encode(origin2, StandardCharsets.UTF_8)
                                    + "&destination=" + java.net.URLEncoder.encode(destination2, StandardCharsets.UTF_8)
                                    + "&overview=full&steps=false&api_key=" + getOlaApiKey();
                            HttpRequest request2 = HttpRequest.newBuilder()
                                    .uri(URI.create(url2))
                                    .timeout(Duration.ofSeconds(30))
                                    .header("X-Request-Id", java.util.UUID.randomUUID().toString())
                                    .header("Accept", "application/json")
                                    .POST(HttpRequest.BodyPublishers.noBody())
                                    .build();
                            HttpResponse<String> resp2 = client.send(request2, HttpResponse.BodyHandlers.ofString());
                            text = resp2.body();
                            if (resp2.statusCode() != 200) {
                                lastRouteError = "directions_http_" + resp2.statusCode();
                                return null;
                            }
                        } else {
                            lastRouteError = "could_not_snap_points";
                            return null;
                        }
                    }
                } catch (Exception ex) {
                    lastRouteError = "directions_http_" + resp.statusCode();
                    return null;
                }
            } else {
                lastRouteError = "directions_http_" + resp.statusCode();
                return null;
            }
        }

        double distance = 0.0;
        double duration = 0.0;
        List<List<Double>> points = new ArrayList<>();
        try {
            var obj = new org.json.JSONObject(text);
            var routes = obj.optJSONArray("routes");
            if (routes != null && routes.length() > 0) {
                var route = routes.getJSONObject(0);

                // If summary object exists use it, otherwise sum legs distances/durations
                if (route.has("summary") && route.opt("summary") instanceof org.json.JSONObject) {
                    var summary = route.getJSONObject("summary");
                    distance = summary.optDouble("distance", 0.0) / 1000.0;
                    duration = summary.optDouble("duration", 0.0) / 3600.0;
                } else if (route.has("legs")) {
                    try {
                        var legs = route.getJSONArray("legs");
                        double distMeters = 0.0;
                        double durSeconds = 0.0;
                        for (int li = 0; li < legs.length(); li++) {
                            var leg = legs.getJSONObject(li);
                            distMeters += leg.optDouble("distance", 0.0);
                            durSeconds += leg.optDouble("duration", 0.0);
                        }
                        distance = distMeters / 1000.0;
                        duration = durSeconds / 3600.0;
                    } catch (Exception ignored) {
                    }
                }

                // geometry can be string (encoded polyline), object (GeoJSON) or array
                if (route.has("geometry")) {
                    Object geom = route.get("geometry");
                    if (geom instanceof String) {
                        String encoded = ((String) geom).trim();
                        var p6 = decodePolyline(encoded, 6);
                        if (!p6.isEmpty()) {
                            points = p6;
                        } else {
                            points = decodePolyline(encoded, 5);
                        }
                    } else if (geom instanceof org.json.JSONObject) {
                        var g = (org.json.JSONObject) geom;
                        if (g.has("coordinates")) {
                            var coords = g.getJSONArray("coordinates");
                            for (int i = 0; i < coords.length(); i++) {
                                var pair = coords.getJSONArray(i);
                                double lon = pair.getDouble(0);
                                double lat = pair.getDouble(1);
                                points.add(List.of(lon, lat));
                            }
                        }
                    } else if (geom instanceof org.json.JSONArray) {
                        var coords = (org.json.JSONArray) geom;
                        for (int i = 0; i < coords.length(); i++) {
                            var pair = coords.getJSONArray(i);
                            double lon = pair.getDouble(0);
                            double lat = pair.getDouble(1);
                            points.add(List.of(lon, lat));
                        }
                    }
                }

                // fallback: overview_polyline might be a string or an object with 'points'
                if (points.isEmpty() && route.has("overview_polyline")) {
                    try {
                        Object ov = route.get("overview_polyline");
                        String encoded = null;
                        if (ov instanceof String) {
                            encoded = ((String) ov).trim();
                        } else if (ov instanceof org.json.JSONObject) {
                            var ovObj = (org.json.JSONObject) ov;
                            if (ovObj.has("points")) {
                                encoded = ovObj.optString("points", "").trim();
                            }
                        }
                        if (encoded != null && !encoded.isEmpty()) {
                            var p5 = decodePolyline(encoded, 5);
                            if (!p5.isEmpty()) {
                                points = p5;
                            } else {
                                points = decodePolyline(encoded, 6);
                            }
                        }
                    } catch (Exception ignored) {
                    }
                }

                // fallback: leg-level polyline (string or object)
                if (points.isEmpty() && route.has("legs")) {
                    try {
                        var legs = route.getJSONArray("legs");
                        for (int li = 0; li < legs.length(); li++) {
                            var leg = legs.getJSONObject(li);
                            // first try leg.polyline as string
                            if (leg.has("polyline")) {
                                Object lp = leg.get("polyline");
                                String encoded = null;
                                if (lp instanceof String) {
                                    encoded = ((String) lp).trim();
                                } else if (lp instanceof org.json.JSONObject) {
                                    encoded = ((org.json.JSONObject) lp).optString("points", "").trim();
                                }
                                if (encoded != null && !encoded.isEmpty()) {
                                    var seg = decodePolyline(encoded, 5);
                                    if (seg.isEmpty()) {
                                        seg = decodePolyline(encoded, 6);
                                    }
                                    if (!seg.isEmpty()) {
                                        points.addAll(seg);
                                    }
                                }
                            }

                            // try steps start/end locations
                            if (leg.has("steps")) {
                                var steps = leg.getJSONArray("steps");
                                for (int si = 0; si < steps.length(); si++) {
                                    var step = steps.getJSONObject(si);
                                    if (step.has("start_location")) {
                                        var sl = step.getJSONObject("start_location");
                                        double lat = sl.optDouble("lat", Double.NaN);
                                        double lng = sl.optDouble("lng", Double.NaN);
                                        if (!Double.isNaN(lat) && !Double.isNaN(lng)) {
                                            points.add(List.of(lng, lat));
                                        }
                                    }
                                    if (step.has("end_location")) {
                                        var el = step.getJSONObject("end_location");
                                        double lat = el.optDouble("lat", Double.NaN);
                                        double lng = el.optDouble("lng", Double.NaN);
                                        if (!Double.isNaN(lat) && !Double.isNaN(lng)) {
                                            points.add(List.of(lng, lat));
                                        }
                                    }
                                }
                            } else {
                                // fall back to leg start/end
                                if (leg.has("start_location")) {
                                    var sl = leg.getJSONObject("start_location");
                                    double lat = sl.optDouble("lat", Double.NaN);
                                    double lng = sl.optDouble("lng", Double.NaN);
                                    if (!Double.isNaN(lat) && !Double.isNaN(lng)) {
                                        points.add(List.of(lng, lat));
                                    }
                                }
                                if (leg.has("end_location")) {
                                    var el = leg.getJSONObject("end_location");
                                    double lat = el.optDouble("lat", Double.NaN);
                                    double lng = el.optDouble("lng", Double.NaN);
                                    if (!Double.isNaN(lat) && !Double.isNaN(lng)) {
                                        points.add(List.of(lng, lat));
                                    }
                                }
                            }
                        }
                        // dedupe sequential duplicates
                        if (!points.isEmpty()) {
                            List<List<Double>> dedup = new ArrayList<>();
                            List<Double> prev = null;
                            for (var p : points) {
                                if (prev == null || Math.abs(prev.get(0) - p.get(0)) > 1e-9
                                        || Math.abs(prev.get(1) - p.get(1)) > 1e-9) {
                                    dedup.add(p);
                                    prev = p;
                                }
                            }
                            points = dedup;
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
        } catch (Exception e) {
            lastRouteError = "directions_parse_error: " + e.getMessage();
            LOGGER.warn("Failed to parse Ola directions response: {}", e.getMessage());
        }

        // If we found no points, log raw response for debugging (pretty-print if
        // possible)
        if (points.isEmpty()) {
            LOGGER.warn("Parsed route had no points (coords path). rawResponse={}",
                    text == null ? "<empty>" : (text.length() > 500 ? text.substring(0, 500) : text));
            try {
                var rawObj = new org.json.JSONObject(text == null ? "{}" : text);
                LOGGER.warn("Full route JSON (coords path) for debugging: {}", rawObj.toString(2));
            } catch (Exception ignored) {
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("distance_km", distance);
        result.put("duration_hr", duration);
        result.put("points", points);
        return result;
    }

}
