/*
 * Copyright 2024 Anton Tananaev (anton@traccar.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.api.resource;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traccar.api.ExtendedObjectResource;
import org.traccar.config.Config;
import org.traccar.config.Keys;
import org.traccar.model.Device;
import org.traccar.model.Driver;
import org.traccar.model.Group;
import org.traccar.model.Trip;
import org.traccar.model.User;
import org.traccar.model.Position;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Order;
import org.traccar.storage.query.Request;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Date;
import java.util.LinkedList;
import java.util.stream.Stream;

@Path("trips")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TripResource extends ExtendedObjectResource<Trip> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TripResource.class);

    @Inject
    private Config config;

    public TripResource() {
        super(Trip.class, "name");
    }

    /**
     * Calculate driving distance between two coordinates using ORS API
     * 
     * @return distance in kilometers, or 0.0 if calculation fails
     */
    private double calculateOrsDistance(double lat1, double lon1, double lat2, double lon2) {
        try {
            String apiKey = config.getString(Keys.ORS_API_KEY);
            if (apiKey == null || apiKey.isBlank()) {
                LOGGER.warn("ORS API key not configured");
                return 0.0;
            }

            String body = "{\"coordinates\":[[" + lon1 + "," + lat1 + "],[" + lon2 + "," + lat2 + "]]}";

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.openrouteservice.org/v2/directions/driving-car"))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .header("Authorization", apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                var json = new org.json.JSONObject(response.body());
                var routes = json.getJSONArray("routes");
                if (routes.length() > 0) {
                    var route = routes.getJSONObject(0);
                    if (route.has("summary")) {
                        var summary = route.getJSONObject("summary");
                        double distanceMeters = summary.optDouble("distance", 0.0);
                        return distanceMeters / 1000.0; // Convert to km
                    }
                }
            } else {
                LOGGER.warn("ORS API request failed with status: {}", response.statusCode());
            }
        } catch (Exception e) {
            LOGGER.error("Failed to calculate ORS distance: {}", e.getMessage());
        }
        return 0.0;
    }

    /**
     * Enrich trip with dynamic position data (not stored in DB)
     * Populates currentLatitude, currentLongitude, currentSpeed, distanceCovered
     * Distance covered is calculated from origin to current position
     * If device has no position, leaves fields as 0/null
     */
    private void enrichTripWithPositionData(Trip trip) throws StorageException {
        // Initialize transient fields to default values
        trip.setCurrentLatitude(0.0);
        trip.setCurrentLongitude(0.0);
        trip.setCurrentSpeed(0.0);
        trip.setLastPositionUpdate(null);
        trip.setDistanceCovered(0.0);

        try {
            // Get device
            Device device = storage.getObject(Device.class, new Request(
                    new Columns.All(), new Condition.Equals("id", trip.getDeviceId())));

            if (device == null || device.getPositionId() <= 0) {
                LOGGER.debug("Device {} has no position data available", trip.getDeviceId());
                return; // No position available - leave fields as 0/null
            }

            // Get device's latest position
            Position currentPosition = storage.getObject(Position.class, new Request(
                    new Columns.All(), new Condition.Equals("id", device.getPositionId())));

            if (currentPosition == null) {
                LOGGER.warn("Position {} not found for device {}", device.getPositionId(), trip.getDeviceId());
                return; // Position not found - leave fields as 0/null
            }

            // Set current position data (transient fields)
            trip.setCurrentLatitude(currentPosition.getLatitude());
            trip.setCurrentLongitude(currentPosition.getLongitude());
            trip.setCurrentSpeed(currentPosition.getSpeed());
            trip.setLastPositionUpdate(currentPosition.getFixTime());

            // Calculate distance covered from origin to current position
            // This works regardless of whether referencePositionId was set
            if (trip.getOriginLatitude() != 0 && trip.getOriginLongitude() != 0) {
                double distanceCovered = calculateOrsDistance(
                        trip.getOriginLatitude(), trip.getOriginLongitude(),
                        currentPosition.getLatitude(), currentPosition.getLongitude());
                trip.setDistanceCovered(distanceCovered);
                LOGGER.debug("Distance covered from origin: {} km", distanceCovered);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to enrich trip with position data: {}", e.getMessage());
        }
    }

    private void validateAndEnrichTrip(Trip trip, boolean isUpdate) throws StorageException {
        // If deviceUniqueId provided, look up deviceId
        if (trip.getDeviceUniqueId() != null && !trip.getDeviceUniqueId().trim().isEmpty()) {
            Device deviceByUniqueId = storage.getObject(Device.class, new Request(
                    new Columns.All(), new Condition.Equals("uniqueId", trip.getDeviceUniqueId())));

            if (deviceByUniqueId == null) {
                throw new WebApplicationException(
                        Response.status(Response.Status.NOT_FOUND)
                                .entity("Device not found with uniqueId: " + trip.getDeviceUniqueId())
                                .build());
            }
            trip.setDeviceId(deviceByUniqueId.getId());
        }

        // Validate required fields
        if (trip.getDeviceId() <= 0) {
            throw new WebApplicationException(
                    Response.status(Response.Status.BAD_REQUEST)
                            .entity("deviceId or deviceUniqueId is required")
                            .build());
        }

        if (trip.getOriginLatitude() == 0 || trip.getOriginLongitude() == 0) {
            throw new WebApplicationException(
                    Response.status(Response.Status.BAD_REQUEST)
                            .entity("originLatitude and originLongitude are required")
                            .build());
        }

        if (trip.getDestinationLatitude() == 0 || trip.getDestinationLongitude() == 0) {
            throw new WebApplicationException(
                    Response.status(Response.Status.BAD_REQUEST)
                            .entity("destinationLatitude and destinationLongitude are required")
                            .build());
        }

        if (trip.getEtaDate() == null) {
            throw new WebApplicationException(
                    Response.status(Response.Status.BAD_REQUEST)
                            .entity("etaDate is required")
                            .build());
        }

        if (trip.getConsignorId() <= 0) {
            throw new WebApplicationException(
                    Response.status(Response.Status.BAD_REQUEST)
                            .entity("consignorId is required")
                            .build());
        }

        if (trip.getConsigneeId() <= 0) {
            throw new WebApplicationException(
                    Response.status(Response.Status.BAD_REQUEST)
                            .entity("consigneeId is required")
                            .build());
        }

        // Validate and fetch device
        Device device = storage.getObject(Device.class, new Request(
                new Columns.All(), new Condition.Equals("id", trip.getDeviceId())));

        if (device == null) {
            throw new WebApplicationException(
                    Response.status(Response.Status.NOT_FOUND)
                            .entity("Device not found with id: " + trip.getDeviceId())
                            .build());
        }

        // Check user has permission to the device
        permissionsService.checkPermission(Device.class, getUserId(), trip.getDeviceId());

        // Auto-fill device details
        trip.setDeviceUniqueId(device.getUniqueId());
        trip.setDeviceName(device.getName());
        trip.setAssetNumber(device.getUniqueId());

        // Auto-fill asset type from device attributes
        if (device.getAttributes() != null && device.getAttributes().containsKey("assetType")) {
            trip.setAssetType(device.getAttributes().get("assetType").toString());
        }

        // Always set departure date to now
        trip.setDepartureDate(new Date());

        // Set running status to "running"
        trip.setRunningStatus("running");

        // Check if origin/destination changed (for PUT requests)
        boolean routeChanged = false;
        if (isUpdate) {
            Trip existingTrip = storage.getObject(Trip.class, new Request(
                    new Columns.All(), new Condition.Equals("id", trip.getId())));
            if (existingTrip != null) {
                routeChanged = existingTrip.getOriginLatitude() != trip.getOriginLatitude()
                        || existingTrip.getOriginLongitude() != trip.getOriginLongitude()
                        || existingTrip.getDestinationLatitude() != trip.getDestinationLatitude()
                        || existingTrip.getDestinationLongitude() != trip.getDestinationLongitude();
            }
        }

        // Calculate total distance using ORS API (origin to destination)
        // On POST: always calculate
        // On PUT: only if route changed
        if (!isUpdate || routeChanged) {
            double totalDistance = calculateOrsDistance(
                    trip.getOriginLatitude(), trip.getOriginLongitude(),
                    trip.getDestinationLatitude(), trip.getDestinationLongitude());
            if (totalDistance > 0) {
                trip.setTotalDistance(totalDistance);
                LOGGER.info("Calculated total distance: {} km", totalDistance);
            }
        }

        // Auto-fill transporter name from group
        if (device.getGroupId() > 0) {
            Group group = storage.getObject(Group.class, new Request(
                    new Columns.All(), new Condition.Equals("id", device.getGroupId())));
            if (group != null) {
                trip.setTransporterName(group.getName());
            }
        }

        // Auto-fill driver details if driverNumber provided
        if (trip.getDriverNumber() != null && !trip.getDriverNumber().trim().isEmpty()) {
            Driver driver = storage.getObject(Driver.class, new Request(
                    new Columns.All(), new Condition.Equals("uniqueId", trip.getDriverNumber())));

            if (driver != null) {
                trip.setDriverName(driver.getName());
                // Try to get additional driver details from attributes
                if (driver.getAttributes() != null) {
                    if (driver.getAttributes().containsKey("phone")) {
                        trip.setDriverContact(driver.getAttributes().get("phone").toString());
                    }
                    if (driver.getAttributes().containsKey("license")) {
                        trip.setDriverLicense(driver.getAttributes().get("license").toString());
                    }
                }
            }
        }

        // Handle consignor - validate user exists and auto-fill names
        User consignor = storage.getObject(User.class, new Request(
                new Columns.All(), new Condition.Equals("id", trip.getConsignorId())));

        if (consignor != null) {
            trip.setConsignorName(consignor.getName());
            trip.setOtherConsignorName(consignor.getName());
        } else {
            // If consignorId doesn't match a user, check if otherConsignorName is provided
            if (trip.getOtherConsignorName() == null || trip.getOtherConsignorName().trim().isEmpty()) {
                throw new WebApplicationException(
                        Response.status(Response.Status.BAD_REQUEST)
                                .entity("Consignor user not found. Provide otherConsignorName for custom entry")
                                .build());
            }
            trip.setConsignorName(trip.getOtherConsignorName());
        }

        // Handle consignee - validate user exists and auto-fill names
        User consignee = storage.getObject(User.class, new Request(
                new Columns.All(), new Condition.Equals("id", trip.getConsigneeId())));

        if (consignee != null) {
            trip.setConsigneeName(consignee.getName());
            trip.setOtherConsigneeName(consignee.getName());
        } else {
            // If consigneeId doesn't match a user, check if otherConsigneeName is provided
            if (trip.getOtherConsigneeName() == null || trip.getOtherConsigneeName().trim().isEmpty()) {
                throw new WebApplicationException(
                        Response.status(Response.Status.BAD_REQUEST)
                                .entity("Consignee user not found. Provide otherConsigneeName for custom entry")
                                .build());
            }
            trip.setConsigneeName(trip.getOtherConsigneeName());
        }

        // Set timestamps
        Date now = new Date();
        if (!isUpdate) {
            trip.setCreatedAt(now);
        }
        trip.setUpdatedAt(now);

        // Initialize tracking fields if not set
        if (trip.getRunningStatus() == null) {
            trip.setRunningStatus("pending");
        }
    }

    @POST
    @Override
    public Response add(Trip entity) throws Exception {
        validateAndEnrichTrip(entity, false);
        Response response = super.add(entity);

        // Enrich response with dynamic position data
        if (response.getStatus() == 200 || response.getStatus() == 201) {
            Trip createdTrip = (Trip) response.getEntity();
            enrichTripWithPositionData(createdTrip);
        }

        return response;
    }

    @PUT
    @Path("{id}")
    @Override
    public Response update(Trip entity) throws Exception {
        validateAndEnrichTrip(entity, true);
        Response response = super.update(entity);

        // Enrich response with dynamic position data
        if (response.getStatus() == 200) {
            Trip updatedTrip = (Trip) response.getEntity();
            enrichTripWithPositionData(updatedTrip);
        }

        return response;
    }

    @GET
    @Override
    public Stream<Trip> get(
            @QueryParam("all") boolean all,
            @QueryParam("userId") long userId,
            @QueryParam("groupId") long groupId,
            @QueryParam("deviceId") long deviceId,
            @QueryParam("excludeAttributes") boolean excludeAttributes) throws StorageException {

        // Handle deviceUniqueId separately through a custom query parameter
        return getWithDeviceUniqueId(all, userId, groupId, deviceId, null, excludeAttributes);
    }

    @GET
    @Path("byDeviceUniqueId")
    public Stream<Trip> getByDeviceUniqueId(
            @QueryParam("all") boolean all,
            @QueryParam("userId") long userId,
            @QueryParam("groupId") long groupId,
            @QueryParam("deviceUniqueId") String deviceUniqueId,
            @QueryParam("excludeAttributes") boolean excludeAttributes) throws StorageException {

        return getWithDeviceUniqueId(all, userId, groupId, 0, deviceUniqueId, excludeAttributes);
    }

    private Stream<Trip> getWithDeviceUniqueId(
            boolean all, long userId, long groupId, long deviceId,
            String deviceUniqueId, boolean excludeAttributes) throws StorageException {

        var conditions = new LinkedList<Condition>();

        if (all) {
            if (permissionsService.notAdmin(getUserId())) {
                conditions.add(new Condition.Permission(User.class, getUserId(), baseClass));
            }
        } else {
            if (userId == 0) {
                conditions.add(new Condition.Permission(User.class, getUserId(), baseClass));
            } else {
                permissionsService.checkUser(getUserId(), userId);
                conditions.add(new Condition.Permission(User.class, userId, baseClass).excludeGroups());
            }
        }

        if (groupId > 0) {
            permissionsService.checkPermission(Group.class, getUserId(), groupId);
            conditions.add(new Condition.Permission(Group.class, groupId, baseClass).excludeGroups());
        }
        if (deviceId > 0) {
            permissionsService.checkPermission(Device.class, getUserId(), deviceId);
            conditions.add(new Condition.Permission(Device.class, deviceId, baseClass).excludeGroups());
        } else if (deviceUniqueId != null && !deviceUniqueId.trim().isEmpty()) {
            // Look up device by uniqueId and use its ID
            Device device = storage.getObject(Device.class, new Request(
                    new Columns.All(), new Condition.Equals("uniqueId", deviceUniqueId)));
            if (device != null) {
                permissionsService.checkPermission(Device.class, getUserId(), device.getId());
                conditions.add(new Condition.Permission(Device.class, device.getId(), baseClass).excludeGroups());
            }
        }

        Columns columns = excludeAttributes ? new Columns.Exclude("attributes") : new Columns.All();
        Stream<Trip> trips = storage.getObjectsStream(baseClass, new Request(
                columns, Condition.merge(conditions), new Order("name")));

        // Enrich each trip with dynamic position data
        return trips.map(trip -> {
            try {
                enrichTripWithPositionData(trip);
            } catch (StorageException e) {
                LOGGER.warn("Failed to enrich trip {} with position data", trip.getId());
            }
            return trip;
        });
    }

}
