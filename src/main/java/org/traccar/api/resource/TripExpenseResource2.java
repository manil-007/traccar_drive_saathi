
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

@Path("tripExpense2")
@Produces(MediaType.APPLICATION_JSON)
@Consumes({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_FORM_URLENCODED })
public class TripExpenseResource2 {

    @Inject
    private Config config;

    private String getOrsApiKey() {
        try {
            String k = config.getString(Keys.ORS_API_KEY);
            if (k == null || k.isBlank()) {
                LOGGER.warn("ORS API key (ors.apiKey) is missing from configuration");
                return "";
            }
            return k;
        } catch (Exception e) {
            LOGGER.warn("Failed to read ORS API key from config: {}", e.getMessage());
            return "";
        }
    }

    // Caches disabled for debugging: always fetch fresh data from ORS
    private static final Logger LOGGER = LoggerFactory.getLogger(TripExpenseResource2.class);
    // lastRouteError captures a short human-readable reason when ORS
    // geocode/directions fail
    private String lastRouteError = null;

    public TripExpenseResource2() {
    }

    record RequestPayload(String start, String destination, String vehicle_type, Double mileage,
            Double fuel_tank_capacity, Double border_expense, Double loading_unloading,
            Double tyre, Boolean loaded, Double incentive, Double da_amount_per_day,
            Double kilometers_per_day, Integer journey_time_days) {
    }

    @SuppressWarnings("unused")
    @POST
    public Response calculate(@jakarta.ws.rs.core.Context jakarta.servlet.http.HttpServletRequest servletRequest) {
        try {
            // Build payload map from either JSON body or form parameters
            Map<String, Object> payload = new HashMap<>();
            String contentType = servletRequest != null && servletRequest.getContentType() != null
                    ? servletRequest.getContentType().toLowerCase()
                    : "";
            if (contentType.startsWith("application/x-www-form-urlencoded")) {
                if (servletRequest != null) {
                    Map<String, String[]> paramMap = servletRequest.getParameterMap();
                    for (Map.Entry<String, String[]> e : paramMap.entrySet()) {
                        String k = e.getKey();
                        String[] vals = e.getValue();
                        if (vals != null && vals.length > 0) {
                            payload.put(k, vals[0]);
                        }
                    }
                }
            } else {
                // attempt to read JSON body (or any body) and parse to payload map
                StringBuilder sb = new StringBuilder();
                if (servletRequest != null) {
                    try (BufferedReader reader = servletRequest.getReader()) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            sb.append(line);
                        }
                    } catch (Exception ignored) {
                    }
                }
                if (sb.length() > 0) {
                    try {
                        var json = new org.json.JSONObject(sb.toString());
                        for (String k : json.keySet()) {
                            payload.put(k, json.get(k));
                        }
                    } catch (Exception ignored) {
                    }
                }
            }

            // Basic validation: require vehicle_type and mileage, and exactly one of
            // either textual start/destination OR start_coord/dest_coord (not both).
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
            if (hasText == hasCoords) { // either both true or both false
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error",
                                "Please provide either text OR coordinates, but not both"))
                        .build();
            }

            // start/destination may be provided as text or as coordinates. Prefer text
            // when available; otherwise, we'll fill them from parsed coords for the
            // response.
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
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "Missing required field: fuel_type (petrol|diesel|cng)"))
                        .build();
            }
            String fuelType = payload.get("fuel_type") != null ? payload.get("fuel_type").toString() : "";

            // fuel tank capacity and starting fuel (optional)
            double fuelTankCapacity = payload.containsKey("fuel_tank_capacity")
                    ? toDouble(payload.get("fuel_tank_capacity"))
                    : 200.0;
            double startFuel = payload.containsKey("start_fuel") ? toDouble(payload.get("start_fuel"))
                    : fuelTankCapacity * 0.9;

            // get route from ORS. If user provided explicit coordinates, prefer them.
            Map<String, Object> route = null;
            List<Double> startCoordParsed = null;
            List<Double> destCoordParsed = null;
            if (payload.containsKey("start_coord") && payload.containsKey("dest_coord")) {
                startCoordParsed = parseCoord(payload.get("start_coord"));
                destCoordParsed = parseCoord(payload.get("dest_coord"));
            }
            if (startCoordParsed != null && destCoordParsed != null) {
                // use provided coordinates (expects [lon, lat] ordering internally)
                route = getRouteUsingCoords(startCoordParsed, destCoordParsed);
                // If textual start/destination weren't provided, synthesize them as
                // lat,lon strings for the response.
                if (start == null) {
                    start = String.valueOf(startCoordParsed.get(1)) + "," + String.valueOf(startCoordParsed.get(0));
                }
                if (destination == null) {
                    destination = String.valueOf(destCoordParsed.get(1)) + "," + String.valueOf(destCoordParsed.get(0));
                }
            } else {
                // fallback to geocoding by text
                route = getRoute(start, destination);
            }
            if (route == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "Unable to compute route", "reason",
                                lastRouteError == null ? "unknown" : lastRouteError))
                        .build();
            }

            double distanceKm = toDouble(route.getOrDefault("distance_km", 0.0));
            // double durationHr = toDouble(route.getOrDefault("duration_hr", 0.0));
            @SuppressWarnings("unchecked")
            List<List<Double>> points = (List<List<Double>>) route.getOrDefault("points", List.of());

            if (points.isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "Route geometry contains no points"))
                        .build();
            }

            // compute cumulative distances along route (km at each point)
            // List<Double> cumulative = computeCumulativeDistances(points);

            // load tolls and compute tolls along route
            List<Map<String, Object>> tolls = loadTollsResource("data/nhai_toll_data.json");
            /*
             * Fuel simulation per user's rules
             * (Commented out for now so original algorithm can be restored later)
             *
             * ... original detailed per-5th-point simulation starts here ...
             *
             */
            /*
             * List<Map<String, Object>> refuels = new ArrayList<>();
             * double totalFuelCost = 0.0;
             * double remaining = startFuel; // litres
             * int pointIndex = 0;
             * int totalPoints = points.size();
             * double pointsPerKm = 6.0; // ORS approx 6 points/km
             * LOGGER.
             * info("Starting fuel simulation: totalPoints={}, distance_km={}, startFuel={}L, capacity={}L, mileage={}"
             * ,
             * totalPoints, distanceKm, remaining, fuelTankCapacity, mileage);
             * while (pointIndex < totalPoints) {
             * // Evaluate at every 5th point
             * if (pointIndex % 5 == 0) {
             * double kmAtPoint = cumulative.get(Math.max(0, Math.min(cumulative.size() - 1,
             * pointIndex)));
             * double fuelPercent = (remaining / fuelTankCapacity) * 100.0;
             * LOGGER.info("Fuel check at point {} (km={}): remaining={}L ({}%)",
             * pointIndex, round(kmAtPoint, 2), round(remaining, 2), round(fuelPercent, 1));
             * if (remaining <= fuelTankCapacity * 0.5 + 1e-9) {
             * // compute search window using 25% of tank * mileage (km)
             * double max_distance_km = 0.25 * fuelTankCapacity * mileage;
             * int search_window_points = Math.max(1, (int) Math.round(max_distance_km *
             * 5.0));
             * int windowStart = pointIndex + 1;
             * int windowEnd = Math.min(totalPoints - 1, pointIndex + search_window_points);
             * LOGGER.
             * info("Triggering refuel search at point {}: max_distance_km={}, search_window_points={} (points {}..{})"
             * ,
             * pointIndex, round(max_distance_km, 2), search_window_points, windowStart,
             * windowEnd);
             * // find reachable cities within 35 km of any route point in window
             * Map<Map<String, Object>, Double> reachable = new HashMap<>();
             * double nearestDist = Double.MAX_VALUE;
             * Map<String, Object> nearestCity = null;
             * for (int pi = windowStart; pi <= windowEnd; pi++) {
             * var pt = points.get(pi);
             * for (var city : fuelPrices) {
             * if (!city.containsKey("lat") || !city.containsKey("lon"))
             * continue;
             * double cityLat = toDouble(city.get("lat"));
             * double cityLon = toDouble(city.get("lon"));
             * double dkm = haversine(pt.get(1), pt.get(0), cityLat, cityLon);
             * if (dkm <= 35.0) {
             * double prev = reachable.getOrDefault(city, Double.MAX_VALUE);
             * if (dkm < prev) reachable.put(city, dkm);
             * }
             * if (dkm < nearestDist) {
             * nearestDist = dkm;
             * nearestCity = city;
             * }
             * }
             * }
             * Map<String, Object> chosenCity = null;
             * String chooseReason = "";
             * if (!reachable.isEmpty()) {
             * chosenCity = reachable.keySet().stream().min((a, b) ->
             * Double.compare(toDouble(a.get("price")),
             * toDouble(b.get("price")))).orElse(null);
             * chooseReason = "cheapest_in_range";
             * } else {
             * chosenCity = nearestCity;
             * chooseReason = "nearest_fallback";
             * }
             * if (chosenCity != null) {
             * double price = toDouble(chosenCity.get("price"));
             * String cname = chosenCity.getOrDefault("city", "<unknown>").toString();
             * String cstate = chosenCity.getOrDefault("state", "").toString();
             * double distanceFromRoute = reachable.containsKey(chosenCity) ?
             * reachable.get(chosenCity) : nearestDist;
             * LOGGER.
             * info("Selected refuel city {} ({}) reason={} price={} distanceFromRoute={}km"
             * , cname, cstate, chooseReason, price, round(distanceFromRoute, 2));
             * double before = remaining;
             * double target = fuelTankCapacity * 0.9;
             * double toFill = Math.max(0.0, target - remaining);
             * double cost = toFill * price;
             * if (toFill > 0) {
             * refuels.add(Map.of("city", cname, "state", cstate, "fuel_type", fuelType,
             * "fuel_price", round(price, 2),
             * "distance_from_route_km", round(distanceFromRoute, 2), "litres_before",
             * round(before, 2),
             * "litres_after", round(remaining + toFill, 2), "litres_filled", round(toFill,
             * 2), "cost", round(cost, 2), "reason", chooseReason));
             * totalFuelCost += cost;
             * remaining += toFill;
             * LOGGER.info("Refueled at {}. price={}, filled={}L, before={}L, after={}L",
             * cname, price, round(toFill, 2), round(before, 2), round(remaining, 2));
             * } else {
             * LOGGER.info("No fuel needed at chosen city {} (already at {}% capacity)",
             * cname, round((remaining / fuelTankCapacity) * 100.0, 1));
             * }
             * int jump_points = Math.max(1, (int) Math.round(0.40 * fuelTankCapacity *
             * mileage * 5.0));
             * int oldIdx = pointIndex;
             * pointIndex = Math.min(totalPoints - 1, pointIndex + jump_points);
             * double jumpKm = jump_points / 5.0;
             * LOGGER.info("Jumping ahead after refuel: {} points (~{} km) from {} to {}",
             * jump_points, round(jumpKm, 2), oldIdx, pointIndex);
             * continue;
             * } else {
             * LOGGER.
             * warn("No city information available for refuel; performing emergency fill to reach destination"
             * );
             * double avgPrice = fuelPrices.stream().mapToDouble(f ->
             * toDouble(f.get("price"))).average().orElse(0.0);
             * double distanceToFinish = distanceKm - cumulative.get(Math.max(0,
             * Math.min(cumulative.size() - 1, pointIndex)));
             * double need = Math.max(0.0, (distanceToFinish / mileage) - remaining);
             * double cost = need * avgPrice;
             * if (need > 0) {
             * refuels.add(Map.of("city", "emergency", "fuel_type", fuelType, "fuel_price",
             * round(avgPrice, 2), "litres_filled", round(need, 2), "cost", round(cost, 2),
             * "reason", "emergency"));
             * totalFuelCost += cost;
             * remaining += need;
             * }
             * }
             * }
             * }
             * // Advance one point and consume fuel accordingly
             * double kmPerPoint = 1.0 / pointsPerKm;
             * double consume = (kmPerPoint) / mileage;
             * remaining = Math.max(0.0, remaining - consume);
             * pointIndex++;
             * }
             */
            // initialize toll accumulators
            String tollKey = mapVehicleType(vehicleType);
            double tollTotal = 0.0;
            List<Map<String, Object>> tollDetails = new ArrayList<>();

            // load fuel prices dataset (expects data/fuel_prices.json)
            List<Map<String, Object>> fuelPrices = loadFuelPricesResource("data/fuel_prices.json", fuelType);
            // Simplified fuel cost calculation (temporary): use user-provided price if
            // available,
            // otherwise try to use Delhi default price from the dataset. This keeps the
            // rest of the
            // trip/toll calculations functional while the detailed simulator is preserved
            // above.
            List<Map<String, Object>> refuels = new ArrayList<>();
            double totalFuelCost = 0.0;

            // Decide fuel pricing and total cost.
            // If user supplies `fuel_cost` treat it as the total amount spent on fuel for
            // the trip.
            // Otherwise, if user supplies a per-litre price (fuel_price, fuel_rate,
            // price_per_litre)
            // use that; if none provided, fallback to Delhi price from dataset, then first
            // entry,
            // then a hardcoded fallback. The `refuels` entry will include price_per_litre,
            // cost and litres_needed; `source` is returned as an empty string as requested.

            double totalFuelNeeded = distanceKm / mileage;
            Double userTotalFuelCost = null;
            // detect explicit total fuel cost (user meant total spent)
            if (payload.containsKey("fuel_cost")) {
                try {
                    userTotalFuelCost = toDouble(payload.get("fuel_cost"));
                } catch (Exception ignored) {
                }
            }

            // detect per-litre user price if provided (alternative to fuel_cost)
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
            String priceSource = "";
            if (userTotalFuelCost != null && userTotalFuelCost > 0) {
                totalFuelCost = userTotalFuelCost;
                // derive per-litre for reporting if possible
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
                // try to find Delhi entry in fuelPrices
                Map<String, Object> delhiEntry = null;
                for (var e : fuelPrices) {
                    String city = e.getOrDefault("city", "").toString().toLowerCase();
                    String state = e.getOrDefault("state", "").toString().toLowerCase();
                    if (city.contains("delhi") || state.contains("delhi")) {
                        delhiEntry = e;
                        break;
                    }
                }
                if (delhiEntry != null) {
                    usedPrice = toDouble(delhiEntry.getOrDefault("price", 0.0));
                    totalFuelCost = totalFuelNeeded * usedPrice;
                    LOGGER.info("Delhi default fuel price applied: {} per litre", round(usedPrice, 2));
                } else if (!fuelPrices.isEmpty()) {
                    // fallback to first entry
                    usedPrice = toDouble(fuelPrices.get(0).getOrDefault("price", 0.0));
                    totalFuelCost = totalFuelNeeded * usedPrice;
                    LOGGER.info("Delhi price not found; using first dataset entry price: {} per litre",
                            round(usedPrice, 2));
                } else {
                    // final fallback hard-coded
                    usedPrice = 100.0;
                    totalFuelCost = totalFuelNeeded * usedPrice;
                    LOGGER.warn("Fuel prices dataset empty; using hardcoded fallback price: {} per litre",
                            round(usedPrice, 2));
                }
            }

            // record a single synthetic 'refuel' entry (no source text per request)
            refuels.add(Map.of("price_per_litre", round(usedPrice, 2), "cost", round(totalFuelCost, 2),
                    "litres_needed", round(totalFuelNeeded, 2), "source", ""));

            // --- Toll matching: find toll plazas close to the route and sum fees ---
            try {
                java.util.Set<Object> seen = new java.util.HashSet<>();
                for (var toll : tolls) {
                    if (toll == null) {
                        continue;
                    }

                    Object locObj = toll.get("location");
                    double tollLat = Double.NaN, tollLon = Double.NaN;

                    // location may be a nested object with lat/lng, or a list, or top-level keys
                    if (locObj instanceof Map<?, ?> locMap) {
                        Map<?, ?> lm = (Map<?, ?>) locMap;
                        if (lm.containsKey("lat") || lm.containsKey("latitude") || lm.containsKey("lng")
                                || lm.containsKey("lon") || lm.containsKey("longitude")) {
                            Object latObj = lm.containsKey("lat") ? lm.get("lat")
                                    : (lm.containsKey("latitude") ? lm.get("latitude") : 0.0);
                            Object lonObj = lm.containsKey("lng") ? lm.get("lng")
                                    : (lm.containsKey("lon") ? lm.get("lon")
                                            : (lm.containsKey("longitude") ? lm.get("longitude") : 0.0));
                            tollLat = toDouble(latObj);
                            tollLon = toDouble(lonObj);
                        }
                    } else if (locObj instanceof List<?> locList && locList.size() >= 2) {
                        tollLat = toDouble(locList.get(0));
                        tollLon = toDouble(locList.get(1));
                    } else {
                        // fallback to top-level lat/lon keys
                        if (toll.containsKey("lat") && (toll.containsKey("lng") || toll.containsKey("lon"))) {
                            tollLat = toDouble(toll.get("lat"));
                            tollLon = toll.containsKey("lng") ? toDouble(toll.get("lng")) : toDouble(toll.get("lon"));
                        } else if (toll.containsKey("latitude") && toll.containsKey("longitude")) {
                            tollLat = toDouble(toll.get("latitude"));
                            tollLon = toDouble(toll.get("longitude"));
                        }
                    }

                    // skip invalid locations
                    if (Double.isNaN(tollLat) || Double.isNaN(tollLon) || (tollLat == 0.0 && tollLon == 0.0)) {
                        continue;
                    }

                    double minDist = minDistanceToRoute(tollLat, tollLon, points); // km
                    // threshold: consider toll on-route if within 2 km (adjustable)
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
                            Map<String, Object> detail = new HashMap<>();
                            detail.put("id", toll.getOrDefault("id", ""));
                            detail.put("name", toll.getOrDefault("name", "<unknown>"));
                            detail.put("state", toll.getOrDefault("state", ""));
                            detail.put("fee", round(fee, 2));
                            tollDetails.add(detail);
                            LOGGER.info("Matched toll plaza '{}' (state={}) dist={}km fee={}",
                                    toll.getOrDefault("name", "<unknown>"), toll.getOrDefault("state", ""),
                                    round(minDist, 2), round(fee, 2));
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("Error while matching toll plazas: {}", e.getMessage());
            }

            // Extras
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

            // compute DA: require kilometers_per_day (always), da_amount_per_day is
            // optional
            double da = 0.0;
            int journeyDays = 0;
            if (!payload.containsKey("kilometers_per_day") || payload.get("kilometers_per_day") == null
                    || toDouble(payload.get("kilometers_per_day")) <= 0.0) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "Missing or invalid required field: kilometers_per_day (must be > 0)"))
                        .build();
            }

            double kmPerDay = toDouble(payload.get("kilometers_per_day"));
            // duration rounded up to nearest whole day
            journeyDays = (int) Math.ceil(distanceKm / kmPerDay);
            if (journeyDays < 1) {
                journeyDays = 1;
            }

            // da_amount_per_day is optional; if provided, compute total DA, otherwise 0
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
            // tripSummary.put("duration_hr", round(durationHr * 1.5, 2));
            tripSummary.put("mileage", mileage);
            // include computed journey days if available
            if (journeyDays > 0) {
                tripSummary.put("journey_time_days", journeyDays);
            }
            // include fuel_tank_capacity only if provided by user
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

    private Map<String, Object> getRoute(String start, String destination) throws Exception {
        // Use simple geocoding via ORS geocode endpoint (caching disabled)
        List<Double> startCoord = geocode(start);
        List<Double> destCoord = geocode(destination);
        if (startCoord == null || destCoord == null) {
            lastRouteError = "geocode_failed_for_start_or_destination";
            return null;
        }

        var client = HttpClient.newHttpClient();
        // NOTE: older/newer ORS instances may not accept 'geometry_format' parameter.
        // Send a minimal directions request and handle geometry in the response (may be
        // encoded polyline or geojson) — we have decoding fallbacks.
        String body = "{\"coordinates\":[[" + startCoord.get(0) + "," + startCoord.get(1) + "],[" + destCoord.get(0)
                + "," + destCoord.get(1) + "]] }";

        LOGGER.info("ORS directions request body (trimmed 500 chars): {}",
                body.length() > 500 ? body.substring(0, 500) : body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openrouteservice.org/v2/directions/driving-car"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Authorization", getOrsApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
        String text = resp.body();
        if (resp.statusCode() != 200) {
            lastRouteError = "directions_http_" + resp.statusCode();
            LOGGER.warn("ORS directions request failed: status={} body={}...", resp.statusCode(),
                    resp.body() == null ? "<empty>"
                            : (resp.body().length() > 500 ? resp.body().substring(0, 500) : resp.body()));
            return null;
        }

        // String text = resp.body();
        double distance = 0.0;
        double duration = 0.0;
        List<List<Double>> points = new ArrayList<>();
        try {
            var obj = new org.json.JSONObject(text);
            var routes = obj.getJSONArray("routes");
            if (routes.length() > 0) {
                var route = routes.getJSONObject(0);
                // Debug: print raw geometry field for inspection
                try {
                    if (route.has("geometry")) {
                        Object rawGeom = route.get("geometry");
                        String raw = rawGeom == null ? "null" : rawGeom.toString();
                        LOGGER.info("Raw ORS geometry (trimmed 2000 chars): {}",
                                raw.length() > 2000 ? raw.substring(0, 2000) : raw);
                        if (rawGeom instanceof org.json.JSONObject) {
                            var gtmp = (org.json.JSONObject) rawGeom;
                            if (gtmp.has("coordinates")) {
                                var ctmp = gtmp.getJSONArray("coordinates");
                                LOGGER.info("ORS geometry.coordinates length = {}", ctmp.length());
                            }
                        } else if (rawGeom instanceof org.json.JSONArray) {
                            var ctmp = (org.json.JSONArray) rawGeom;
                            LOGGER.info("ORS geometry array length = {}", ctmp.length());
                        }
                    } else {
                        LOGGER.warn("ORS route object has no geometry field");
                    }
                } catch (Exception e) {
                    LOGGER.warn("Failed to log raw geometry: {}", e.getMessage());
                }
                if (route.has("summary")) {
                    var summary = route.getJSONObject("summary");
                    distance = summary.optDouble("distance", 0.0) / 1000.0;
                    duration = summary.optDouble("duration", 0.0) / 3600.0;
                }
                // geometry can be GeoJSON 'geometry' with 'coordinates' as array
                if (route.has("geometry")) {
                    var geom = route.get("geometry");
                    if (geom instanceof org.json.JSONObject) {
                        var g = (org.json.JSONObject) geom;
                        if (g.has("coordinates")) {
                            var coords = g.getJSONArray("coordinates");
                            // coordinates may be a nested array for LineString
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
                    } else if (geom instanceof String) {
                        // ORS sometimes returns an encoded polyline string when geometry_format
                        // was not honored or for other reasons. Try to decode as polyline (precision
                        // 5 or 6) as a fallback.
                        String encoded = ((String) geom).trim();
                        LOGGER.info("ORS returned string geometry of length {} - attempting polyline decode",
                                encoded.length());
                        try {
                            List<List<Double>> decoded = decodePolyline(encoded, 5);
                            if (decoded.isEmpty()) {
                                // try precision 6
                                decoded = decodePolyline(encoded, 6);
                            }
                            if (!decoded.isEmpty()) {
                                points.addAll(decoded);
                                LOGGER.info("Decoded {} points from encoded geometry (polyline fallback)",
                                        decoded.size());
                            } else {
                                LOGGER.warn("Polyline decoding produced 0 points for encoded geometry");
                            }
                        } catch (Exception e) {
                            LOGGER.warn("Error while decoding encoded polyline geometry: {}", e.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            lastRouteError = "directions_parse_error: " + e.getMessage();
            LOGGER.warn("Failed to parse ORS directions response: {}", e.getMessage());
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
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openrouteservice.org/geocode/search?text=" + q + "&size=1"))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", getOrsApiKey())
                .GET()
                .build();
        HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            return null;
        }
        var obj = new org.json.JSONObject(resp.body());
        var features = obj.getJSONArray("features");
        if (features.length() == 0) {
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

    /**
     * Reverse geocode a lat,lon point using ORS and return nearest routable
     * [lon,lat]
     * or null if none found.
     */
    private List<Double> reverseGeocodePoint(Double lat, Double lon) {
        try {
            var client = HttpClient.newHttpClient();
            String q = String.format(java.util.Locale.ROOT, "%f,%f", lat, lon);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openrouteservice.org/geocode/reverse?point=" + q + "&size=1"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", getOrsApiKey())
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
            // Return as [lon, lat]
            return List.of(rlon, rlat);
        } catch (Exception e) {
            LOGGER.warn("reverseGeocodePoint failed: {}", e.getMessage());
            return null;
        }
    }

    // private List<Map<String, Object>> loadJsonListResource(String path) {
    // try (InputStream is = getClass().getClassLoader().getResourceAsStream(path))
    // {
    // if (is == null)
    // return List.of();
    // String text = new BufferedReader(new InputStreamReader(is,
    // StandardCharsets.UTF_8)).lines()
    // .collect(Collectors.joining("\n"));
    // var arr = new org.json.JSONArray(text);
    // List<Map<String, Object>> list = new ArrayList<>();
    // for (int i = 0; i < arr.length(); i++) {
    // var o = arr.getJSONObject(i);
    // Map<String, Object> map = o.toMap();
    // list.add(map);
    // }
    // return list;
    // } catch (IOException e) {
    // return List.of();
    // }
    // }

    // convert various numeric object representations to double
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
                // maybe file is an array
                try {
                    var arr = new org.json.JSONArray(text);
                    for (int i = 0; i < arr.length(); i++) {
                        var o = arr.getJSONObject(i);
                        list.add(o.toMap());
                    }
                } catch (Exception e) {
                    // unknown format
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
                    // If the data now contains explicit lat/lng for cities, include them
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
                    // fallback: keep human-readable city name
                    m.put("location", city);
                    list.add(m);
                }
            }
            return list;
        } catch (IOException e) {
            return List.of();
        }
    }

    // geocoding cache removed — geocode() called directly to force fresh data

    // private List<Double> computeCumulativeDistances(List<List<Double>> points) {
    // List<Double> out = new ArrayList<>();
    // double cum = 0.0;
    // if (points.isEmpty())
    // return List.of(0.0);
    // out.add(0.0);
    // for (int i = 1; i < points.size(); i++) {
    // var p1 = points.get(i - 1);
    // var p2 = points.get(i);
    // double lon1 = p1.get(0), lat1 = p1.get(1);
    // double lon2 = p2.get(0), lat2 = p2.get(1);
    // double d = haversine(lat1, lon1, lat2, lon2);
    // cum += d;
    // out.add(cum);
    // }
    // return out;
    // }

    // private int closestPointIndex(List<List<Double>> points, double lat, double
    // lon) {
    // double min = Double.MAX_VALUE;
    // int idx = 0;
    // for (int i = 0; i < points.size(); i++) {
    // var p = points.get(i);
    // double d = haversine(lat, lon, p.get(1), p.get(0));
    // if (d < min) {
    // min = d;
    // idx = i;
    // }
    // }
    // return idx;
    // }

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

    /**
     * Extract toll fee for a given toll record and vehicle key.
     * Handles nested shapes like: { fees: { car: { single: 100 } } }
     */
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
                    // fallback: check common keys
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
                // if fees contains tollKey as a number directly
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

            // older datasets may have fee at top level under vehicle key
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
            // ignore and return 0.0
        }
        return 0.0;
    }

    /**
     * Decode an encoded polyline string (Google polyline / ORS encoded) into a list
     * of [lon, lat] points. Precision is typically 5 (Google) or 6.
     */
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
            // decode latitude
            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20 && index < len);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            // decode longitude
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
            // store as lon, lat to match other code
            p.add(lngD);
            p.add(latD);
            path.add(p);
        }
        return path;
    }

    /**
     * Parse a coordinate input which may be a string like "lat,lon", a list [lat,
     * lon]
     * or a map with lat/lon keys. Returns a list [lon, lat] as expected by ORS.
     */
    private List<Double> parseCoord(Object input) {
        if (input == null) {
            return null;
        }
        try {
            if (input instanceof List<?> lst && lst.size() >= 2) {
                double a = toDouble(lst.get(0));
                double b = toDouble(lst.get(1));
                // if a looks like latitude (-90..90) and b looks like longitude (-180..180)
                if (Math.abs(a) <= 90 && Math.abs(b) <= 180) {
                    // input likely [lat, lon] -> return [lon, lat]
                    return List.of(b, a);
                } else {
                    // fallback: return as [lon, lat]
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

    /**
     * Request route using explicit coordinates (each as [lon, lat]).
     */
    private Map<String, Object> getRouteUsingCoords(List<Double> startCoord, List<Double> destCoord) throws Exception {
        if (startCoord == null || destCoord == null || startCoord.size() < 2 || destCoord.size() < 2) {
            lastRouteError = "invalid_coords";
            return null;
        }

        var client = HttpClient.newHttpClient();
        String body = "{\"coordinates\":[[" + startCoord.get(0) + "," + startCoord.get(1) + "],[" + destCoord.get(0)
                + "," + destCoord.get(1) + "]] }";

        LOGGER.info("ORS directions request body (coords) (trimmed 500 chars): {}",
                body.length() > 500 ? body.substring(0, 500) : body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.openrouteservice.org/v2/directions/driving-car"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Authorization", getOrsApiKey())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
        String text = resp.body();
        if (resp.statusCode() != 200) {
            // If ORS returned a routable-point error (common when point is off-road),
            // try snapping the points via reverse geocode and retry once.
            String bodyText = resp.body() == null ? "" : resp.body();
            LOGGER.warn("ORS directions request failed: status={} body={}...", resp.statusCode(),
                    bodyText.length() > 500 ? bodyText.substring(0, 500) : bodyText);
            // Detect common ORS code 2010 or message text
            if (resp.statusCode() == 404 && bodyText.contains("Could not find routable point")) {
                // attempt to snap start and dest using reverse geocode
                List<Double> snappedStart = reverseGeocodePoint(startCoord.get(1), startCoord.get(0));
                List<Double> snappedDest = reverseGeocodePoint(destCoord.get(1), destCoord.get(0));
                if (snappedStart != null && snappedDest != null) {
                    LOGGER.info("Snapped coords: start={} dest={}", snappedStart, snappedDest);
                    // rebuild request with snapped coords
                    String body2 = "{\"coordinates\":[[" + snappedStart.get(0) + "," + snappedStart.get(1) + "],["
                            + snappedDest.get(0)
                            + "," + snappedDest.get(1) + "]] }";
                    HttpRequest req2 = HttpRequest.newBuilder()
                            .uri(URI.create("https://api.openrouteservice.org/v2/directions/driving-car"))
                            .timeout(Duration.ofSeconds(30))
                            .header("Content-Type", "application/json")
                            .header("Authorization", getOrsApiKey())
                            .POST(HttpRequest.BodyPublishers.ofString(body2))
                            .build();
                    HttpResponse<String> resp2 = client.send(req2, HttpResponse.BodyHandlers.ofString());
                    if (resp2.statusCode() == 200) {
                        // parse resp2 as usual
                        text = resp2.body();
                    } else {
                        lastRouteError = "directions_http_" + resp2.statusCode();
                        LOGGER.warn("ORS retry after snap failed: status={} body={}...", resp2.statusCode(),
                                resp2.body() == null ? "<empty>"
                                        : (resp2.body().length() > 500 ? resp2.body().substring(0, 500)
                                                : resp2.body()));
                        return null;
                    }
                } else {
                    lastRouteError = "routable_point_not_found_and_snap_failed";
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
            var routes = obj.getJSONArray("routes");
            if (routes.length() > 0) {
                var route = routes.getJSONObject(0);
                if (route.has("summary")) {
                    var summary = route.getJSONObject("summary");
                    distance = summary.optDouble("distance", 0.0) / 1000.0;
                    duration = summary.optDouble("duration", 0.0) / 3600.0;
                }
                if (route.has("geometry")) {
                    var geom = route.get("geometry");
                    if (geom instanceof org.json.JSONObject) {
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
                    } else if (geom instanceof String) {
                        String encoded = ((String) geom).trim();
                        try {
                            List<List<Double>> decoded = decodePolyline(encoded, 5);
                            if (decoded.isEmpty()) {
                                decoded = decodePolyline(encoded, 6);
                            }
                            if (!decoded.isEmpty()) {
                                points.addAll(decoded);
                            }
                        } catch (Exception e) {
                            LOGGER.warn("Error while decoding encoded polyline geometry: {}", e.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            lastRouteError = "directions_parse_error: " + e.getMessage();
            LOGGER.warn("Failed to parse ORS directions response: {}", e.getMessage());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("distance_km", distance);
        result.put("duration_hr", duration);
        result.put("points", points);
        return result;
    }

}
