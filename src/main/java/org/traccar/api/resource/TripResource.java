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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traccar.api.ExtendedObjectResource;
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

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;

/**
 * REST API resource for managing trips (shipments/deliveries).
 *
 * <p>Provides endpoints for creating, updating, and querying trips with device tracking,
 * driver information, and real-time position data enrichment.</p>
 *
 * <p><b>Base Path:</b> /api/trips</p>
 *
 * @author Traccar Team
 * @version 6.17.0
 */
@Tag(name = "Trips",
     description = "Trip management API - Create and track shipments/deliveries with real-time device positions")
@Path("trips")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TripResource extends ExtendedObjectResource<Trip> {

    private static final Logger LOGGER = LoggerFactory.getLogger(TripResource.class);

    // Validation constants
    private static final double COORDINATE_EPSILON = 0.0001; // ~11 meters
    private static final int MAX_STRING_LENGTH = 255;
    private static final int MAX_TEXT_LENGTH = 1000;

    // Earth's radius in kilometers for distance calculations
    private static final double EARTH_RADIUS_KM = 6371.0;

    public TripResource() {
        super(Trip.class, "name");
    }

    /**
     * Sanitize string input to prevent injection and limit length
     */
    private String sanitizeString(String input, int maxLength) {
        if (input == null) {
            return null;
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        // Remove any potentially harmful characters
        trimmed = trimmed.replaceAll("[<>\"'`]", "");
        // Limit length
        if (trimmed.length() > maxLength) {
            trimmed = trimmed.substring(0, maxLength);
        }
        return trimmed;
    }

    /**
     * Validate coordinates are within valid ranges and not at origin (0,0)
     */
    private boolean isValidCoordinate(double latitude, double longitude) {
        // Check valid ranges
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            return false;
        }
        // Check not at exact origin (with small epsilon for genuine near-zero coordinates)
        return Math.abs(latitude) > COORDINATE_EPSILON || Math.abs(longitude) > COORDINATE_EPSILON;
    }

    /**
     * Calculate great-circle distance between two coordinates using the Haversine formula.
     *
     * <p>This provides straight-line distance between points on Earth's surface.
     * Actual driving distance will typically be 20-40% longer depending on road network.</p>
     *
     * @param lat1 Starting latitude in decimal degrees
     * @param lon1 Starting longitude in decimal degrees
     * @param lat2 Ending latitude in decimal degrees
     * @param lon2 Ending longitude in decimal degrees
     * @return distance in kilometers (as-the-crow-flies)
     */
    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        // Convert degrees to radians
        double lat1Rad = Math.toRadians(lat1);
        double lon1Rad = Math.toRadians(lon1);
        double lat2Rad = Math.toRadians(lat2);
        double lon2Rad = Math.toRadians(lon2);

        // Haversine formula
        double dLat = lat2Rad - lat1Rad;
        double dLon = lon2Rad - lon1Rad;

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1Rad) * Math.cos(lat2Rad)
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_KM * c;
    }

    /**
     * Enrich trip with dynamic position data (not stored in DB)
     * Populates currentLatitude, currentLongitude, currentSpeed, distanceCovered
     * Distance covered is calculated from origin to current position
     * If device has no position, leaves fields as 0/null
     * For completed trips, skips enrichment (returns null values)
     */
    private void enrichTripWithPositionData(Trip trip) throws StorageException {
        // Skip enrichment for completed or cancelled trips
        if ("completed".equalsIgnoreCase(trip.getTripStatus())
                || "cancelled".equalsIgnoreCase(trip.getTripStatus())) {
            trip.setCurrentLatitude(0.0);
            trip.setCurrentLongitude(0.0);
            trip.setCurrentSpeed(0.0);
            trip.setLastPositionUpdate(null);
            trip.setDistanceCovered(0.0);
            LOGGER.debug("Skipping position enrichment for {} trip {}", trip.getTripStatus(), trip.getId());
            return;
        }

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
                double distanceCovered = calculateDistance(
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
        // Sanitize string inputs
        trip.setName(sanitizeString(trip.getName(), MAX_STRING_LENGTH));
        trip.setDeviceUniqueId(sanitizeString(trip.getDeviceUniqueId(), MAX_STRING_LENGTH));
        trip.setDriverNumber(sanitizeString(trip.getDriverNumber(), MAX_STRING_LENGTH));
        trip.setOtherConsignorName(sanitizeString(trip.getOtherConsignorName(), MAX_STRING_LENGTH));
        trip.setOtherConsigneeName(sanitizeString(trip.getOtherConsigneeName(), MAX_STRING_LENGTH));
        trip.setOriginAddress(sanitizeString(trip.getOriginAddress(), MAX_TEXT_LENGTH));
        trip.setDestinationAddress(sanitizeString(trip.getDestinationAddress(), MAX_TEXT_LENGTH));

        // If deviceUniqueId provided, look up deviceId
        if (trip.getDeviceUniqueId() != null && !trip.getDeviceUniqueId().trim().isEmpty()) {
            Device deviceByUniqueId = storage.getObject(Device.class, new Request(
                    new Columns.All(), new Condition.Equals("uniqueId", trip.getDeviceUniqueId())));

            if (deviceByUniqueId == null) {
                throw new WebApplicationException(
                        Response.status(Response.Status.NOT_FOUND)
                                .entity("Device not found with the provided unique identifier")
                                .build());
            }
            trip.setDeviceId(deviceByUniqueId.getId());
        }

        // Validate required fields
        if (trip.getDeviceId() <= 0) {
            throw new WebApplicationException(
                    Response.status(Response.Status.BAD_REQUEST)
                            .entity("Valid deviceId or deviceUniqueId is required")
                            .build());
        }

        if (!isValidCoordinate(trip.getOriginLatitude(), trip.getOriginLongitude())) {
            throw new WebApplicationException(
                    Response.status(Response.Status.BAD_REQUEST)
                            .entity("Valid origin coordinates are required")
                            .build());
        }

        if (!isValidCoordinate(trip.getDestinationLatitude(), trip.getDestinationLongitude())) {
            throw new WebApplicationException(
                    Response.status(Response.Status.BAD_REQUEST)
                            .entity("Valid destination coordinates are required")
                            .build());
        }

        if (trip.getEtaDate() == null) {
            throw new WebApplicationException(
                    Response.status(Response.Status.BAD_REQUEST)
                            .entity("Expected time of arrival (ETA) is required")
                            .build());
        }

        // Validate ETA is in the future (only for new trips)
        if (!isUpdate && trip.getEtaDate().before(new Date())) {
            throw new WebApplicationException(
                    Response.status(Response.Status.BAD_REQUEST)
                            .entity("ETA date must be in the future")
                            .build());
        }

        if (trip.getConsignorId() <= 0) {
            throw new WebApplicationException(
                    Response.status(Response.Status.BAD_REQUEST)
                            .entity("Consignor ID is required")
                            .build());
        }

        if (trip.getConsigneeId() <= 0) {
            throw new WebApplicationException(
                    Response.status(Response.Status.BAD_REQUEST)
                            .entity("Consignee ID is required")
                            .build());
        }

        // Validate and fetch device
        Device device = storage.getObject(Device.class, new Request(
                new Columns.All(), new Condition.Equals("id", trip.getDeviceId())));

        if (device == null) {
            throw new WebApplicationException(
                    Response.status(Response.Status.NOT_FOUND)
                            .entity("Associated device not found")
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

        // Set departure date to now if not provided (allows frontend to override)
        if (trip.getDepartureDate() == null) {
            trip.setDepartureDate(new Date());
        }

        // Set trip status based on departure date (only for new trips)
        if (!isUpdate) {
            if (trip.getTripStatus() == null || trip.getTripStatus().trim().isEmpty()) {
                // If departure is now or in the past, set to "running", otherwise "pending"
                if (trip.getDepartureDate().before(new Date())
                        || Math.abs(trip.getDepartureDate().getTime() - new Date().getTime()) < 60000) {
                    trip.setTripStatus("running");
                } else {
                    trip.setTripStatus("pending");
                }
            }
        } else {
            // For updates, don't allow changing status to completed (must use /complete endpoint)
            if ("completed".equalsIgnoreCase(trip.getTripStatus())
                    || "cancelled".equalsIgnoreCase(trip.getTripStatus())) {
                // Preserve the existing status, don't allow reverting completed/cancelled trips
                Trip existingTrip = storage.getObject(Trip.class, new Request(
                        new Columns.All(), new Condition.Equals("id", trip.getId())));
                if (existingTrip != null) {
                    trip.setTripStatus(existingTrip.getTripStatus());
                }
            }
        }

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

        // Calculate total distance using Haversine formula (origin to destination)
        // On POST: always calculate (only once per trip creation)
        // On PUT: only if route changed (recalculate when coordinates change)
        if (!isUpdate || routeChanged) {
            double totalDistance = calculateDistance(
                    trip.getOriginLatitude(), trip.getOriginLongitude(),
                    trip.getDestinationLatitude(), trip.getDestinationLongitude());
            trip.setTotalDistance(totalDistance);
            LOGGER.info("Calculated total distance: {} km (update={}, routeChanged={})",
                totalDistance, isUpdate, routeChanged);
        } else {
            LOGGER.debug("Skipping distance calculation - route unchanged");
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
                                .entity("Consignor not found. Please provide a custom consignor name.")
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
                                .entity("Consignee not found. Please provide a custom consignee name.")
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
    }

    /**
     * Create a new trip.
     *
     * <p><b>HTTP Method:</b> POST</p>
     * <p><b>Path:</b> /api/trips</p>
     *
     * <p><b>Required Fields:</b></p>
     * <ul>
     *   <li><b>deviceId</b> (long) - ID of the device/vehicle for this trip (alternative: deviceUniqueId)</li>
     *   <li><b>originLatitude</b> (double) - Starting point latitude (-90 to 90, not 0,0)</li>
     *   <li><b>originLongitude</b> (double) - Starting point longitude (-180 to 180, not 0,0)</li>
     *   <li><b>destinationLatitude</b> (double) - Ending point latitude (-90 to 90, not 0,0)</li>
     *   <li><b>destinationLongitude</b> (double) - Ending point longitude (-180 to 180, not 0,0)</li>
     *   <li><b>etaDate</b> (Date) - Expected time of arrival (must be in the future for new trips)</li>
     *   <li><b>consignorId</b> (long) - ID of the shipper/sender</li>
     *   <li><b>consigneeId</b> (long) - ID of the receiver</li>
     * </ul>
     *
     * <p><b>Optional Fields:</b></p>
     * <ul>
     *   <li>name - Trip name/identifier</li>
     *   <li>deviceUniqueId - Alternative to deviceId (system will look up deviceId)</li>
     *   <li>departureDate - Trip start date (defaults to now if not provided)</li>
     *   <li>driverNumber - Driver unique ID (system will auto-fill driver details)</li>
     *   <li>originAddress, destinationAddress - Address text</li>
     *   <li>otherConsignorName, otherConsigneeName - Custom names if IDs don't match users</li>
     *   <li>All other trip fields (waybill, invoice, material, etc.)</li>
     * </ul>
     *
     * <p><b>Auto-filled Fields:</b></p>
     * <ul>
     *   <li>deviceName, assetNumber, assetType - From device record</li>
     *   <li>transporterName - From device's group</li>
     *   <li>driverName, driverContact, driverLicense - From driver record (if driverNumber provided)</li>
     *   <li>consignorName, consigneeName - From user records</li>
     *   <li>totalDistance - Calculated using Haversine formula (straight-line distance)</li>
     *   <li>runningStatus - Set to "running" if departure is now/past, "pending" if future</li>
     *   <li>createdAt, updatedAt - Current timestamp</li>
     * </ul>
     *
     * <p><b>Response Enrichment:</b> Response includes dynamic position data (currentLatitude,
     * currentLongitude, currentSpeed, distanceCovered) populated from device's latest position.</p>
     *
     * @param entity Trip object with required fields populated
     * @return Response with created Trip (HTTP 200/201) including auto-filled and enriched fields
     * @throws WebApplicationException if validation fails (400 Bad Request)
     * @throws WebApplicationException if device not found (404 Not Found)
     * @throws StorageException if database error occurs
     */
    @Operation(
        summary = "Create a new trip",
        description = "**REQUIRED:** deviceId (or deviceUniqueId), originLatitude, originLongitude, "
                + "destinationLatitude, destinationLongitude, etaDate (future), consignorId, consigneeId. "
                + "Auto-fills device details, calculates distance, enriches with real-time position.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Trip created successfully",
                content = @Content(schema = @Schema(implementation = Trip.class))),
            @ApiResponse(responseCode = "400",
                description = "Validation failed - missing required fields or invalid data"),
            @ApiResponse(responseCode = "404", description = "Device not found")
        }
    )
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

    /**
     * Update an existing trip.
     *
     * <p><b>HTTP Method:</b> PUT</p>
     * <p><b>Path:</b> /api/trips/{id}</p>
     *
     * <p><b>Required Fields:</b> Same as POST, except:</p>
     * <ul>
     *   <li>etaDate can be in the past (validation only applies to new trips)</li>
     *   <li>departureDate can be in the past</li>
     * </ul>
     *
     * <p><b>Behavior:</b></p>
     * <ul>
     *   <li>Distance recalculation only happens if origin/destination coordinates change</li>
     *   <li>updatedAt timestamp is refreshed</li>
     *   <li>createdAt is NOT modified</li>
     * </ul>
     *
     * @param entity Trip object with id and fields to update
     * @return Response with updated Trip (HTTP 200) including enriched position data
     * @throws WebApplicationException if validation fails
     * @throws StorageException if database error occurs
     */
    @Operation(
        summary = "Update an existing trip",
        description = "Same required fields as POST. Distance recalculates only if coordinates change. "
                + "ETA can be in the past for updates.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Trip updated successfully",
                content = @Content(schema = @Schema(implementation = Trip.class))),
            @ApiResponse(responseCode = "400", description = "Validation failed"),
            @ApiResponse(responseCode = "404", description = "Trip or device not found")
        }
    )
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

    /**
     * Query trips with permission filtering.
     *
     * <p><b>HTTP Method:</b> GET</p>
     * <p><b>Path:</b> /api/trips</p>
     *
     * <p><b>Query Parameters:</b></p>
     * <ul>
     *   <li><b>all</b> (boolean) - If true and user is admin, returns all trips. Otherwise returns user's trips.</li>
     *   <li><b>userId</b> (long) - Filter trips by specific user (requires permission check)</li>
     *   <li><b>groupId</b> (long) - Filter trips by device group (requires permission check)</li>
     *   <li><b>deviceId</b> (long) - Filter trips by specific device (requires permission check)</li>
     *   <li><b>excludeAttributes</b> (boolean) - If true, omits 'attributes' field for performance</li>
     * </ul>
     *
     * <p><b>Performance Note:</b> This endpoint does NOT include position enrichment by default.
     * Use /api/trips/withPosition if you need currentLatitude, currentLongitude, currentSpeed, distanceCovered.</p>
     *
     * @param all whether to return all trips (admin only)
     * @param userId filter by user ID
     * @param groupId filter by group ID
     * @param deviceId filter by device ID
     * @param excludeAttributes whether to exclude attributes field
     * @return Stream of Trip objects matching filters
     * @throws StorageException if database error occurs
     */
    @Operation(
        summary = "Get trips (without position data)",
        description = "Query trips with permission filtering. Does NOT include real-time position data "
                + "for performance. Use /trips/withPosition for position-enriched results.",
        responses = {
            @ApiResponse(responseCode = "200", description = "List of trips",
                content = @Content(schema = @Schema(implementation = Trip.class)))
        }
    )
    @GET
    public Stream<Trip> get(
            @Parameter(description = "Return all trips (admin only)") @QueryParam("all") boolean all,
            @Parameter(description = "Filter by user ID") @QueryParam("userId") long userId,
            @Parameter(description = "Filter by group ID") @QueryParam("groupId") long groupId,
            @Parameter(description = "Filter by device ID") @QueryParam("deviceId") long deviceId,
            @Parameter(description = "Exclude attributes field")
                @QueryParam("excludeAttributes") boolean excludeAttributes) throws StorageException {

        // Handle deviceUniqueId separately through a custom query parameter
        return getWithDeviceUniqueId(all, userId, groupId, deviceId, null, excludeAttributes, false);
    }

    /**
     * Query trips by device unique ID (alternative to deviceId).
     *
     * <p><b>HTTP Method:</b> GET</p>
     * <p><b>Path:</b> /api/trips/byDeviceUniqueId</p>
     *
     * <p><b>Query Parameters:</b></p>
     * <ul>
     *   <li><b>deviceUniqueId</b> (String, required) - Device unique identifier (e.g., IMEI)</li>
     *   <li>all, userId, groupId, excludeAttributes - Same as /api/trips</li>
     * </ul>
     *
     * <p>Useful when you have device IMEI/unique ID but not internal database ID.</p>
     *
     * @param all whether to return all trips
     * @param userId filter by user ID
     * @param groupId filter by group ID
     * @param deviceUniqueId device unique identifier (REQUIRED)
     * @param excludeAttributes whether to exclude attributes
     * @return Stream of Trip objects for the specified device
     * @throws StorageException if database error occurs
     */
    @Operation(
        summary = "Get trips by device unique ID (IMEI)",
        description = "Query trips using device IMEI/unique ID instead of internal database ID. "
                + "Useful when you don't know the deviceId.",
        responses = {
            @ApiResponse(responseCode = "200", description = "List of trips for the device",
                content = @Content(schema = @Schema(implementation = Trip.class)))
        }
    )
    @GET
    @Path("byDeviceUniqueId")
    public Stream<Trip> getByDeviceUniqueId(
            @Parameter(description = "Return all trips") @QueryParam("all") boolean all,
            @Parameter(description = "Filter by user ID") @QueryParam("userId") long userId,
            @Parameter(description = "Filter by group ID") @QueryParam("groupId") long groupId,
            @Parameter(description = "Device IMEI/unique ID (REQUIRED)", required = true)
                @QueryParam("deviceUniqueId") String deviceUniqueId,
            @Parameter(description = "Exclude attributes")
                @QueryParam("excludeAttributes") boolean excludeAttributes) throws StorageException {

        return getWithDeviceUniqueId(all, userId, groupId, 0, deviceUniqueId, excludeAttributes, false);
    }

    /**
     * Query trips with real-time position data enrichment.
     *
     * <p><b>HTTP Method:</b> GET</p>
     * <p><b>Path:</b> /api/trips/withPosition</p>
     *
     * <p><b>Ã¢Å¡Â Ã¯Â¸Â Performance Warning:</b> This endpoint enriches each trip with real-time position data,
     * making additional database queries for each trip. Use sparingly for large result sets.</p>
     *
     * <p><b>Enriched Fields:</b></p>
     * <ul>
     *   <li>currentLatitude - Device's current latitude</li>
     *   <li>currentLongitude - Device's current longitude</li>
     *   <li>currentSpeed - Device's current speed</li>
     *   <li>lastPositionUpdate - Timestamp of last position</li>
     *   <li>distanceCovered - Distance from origin to current position (km)</li>
     * </ul>
     *
     * <p><b>Query Parameters:</b> Same as /api/trips</p>
     *
     * <p><b>Use Cases:</b></p>
     * <ul>
     *   <li>Real-time trip monitoring dashboards</li>
     *   <li>Calculating delivery progress (distanceCovered / totalDistance)</li>
     *   <li>Map visualization with current vehicle positions</li>
     * </ul>
     *
     * @param all whether to return all trips
     * @param userId filter by user ID
     * @param groupId filter by group ID
     * @param deviceId filter by device ID
     * @param excludeAttributes whether to exclude attributes
     * @return Stream of Trip objects with position data enrichment
     * @throws StorageException if database error occurs
     */
    @Operation(
        summary = "Get trips WITH real-time position data (⚡⏱ SLOWER)",
        description = "⚠️ PERFORMANCE WARNING: Adds database queries per trip. "
                + "Returns currentLatitude, currentLongitude, currentSpeed, distanceCovered. "
                + "Use for real-time dashboards only, not bulk operations.",
        responses = {
            @ApiResponse(responseCode = "200", description = "List of trips enriched with real-time position data",
                content = @Content(schema = @Schema(implementation = Trip.class)))
        }
    )
    @GET
    @Path("withPosition")
    public Stream<Trip> getWithPositionData(
            @Parameter(description = "Return all trips") @QueryParam("all") boolean all,
            @Parameter(description = "Filter by user ID") @QueryParam("userId") long userId,
            @Parameter(description = "Filter by group ID") @QueryParam("groupId") long groupId,
            @Parameter(description = "Filter by device ID") @QueryParam("deviceId") long deviceId,
            @Parameter(description = "Exclude attributes")
                @QueryParam("excludeAttributes") boolean excludeAttributes) throws StorageException {

        return getWithDeviceUniqueId(all, userId, groupId, deviceId, null, excludeAttributes, true);
    }

    private Stream<Trip> getWithDeviceUniqueId(
            boolean all, long userId, long groupId, long deviceId,
            String deviceUniqueId, boolean excludeAttributes, boolean includePositionData) throws StorageException {

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

        // Only enrich with position data if explicitly requested to avoid N+1 query problem
        // For bulk GET operations, use /trips/withPosition endpoint instead
        if (includePositionData) {
            LOGGER.debug("Enriching trips with position data (may be slow for large result sets)");
            return trips.map(trip -> {
                try {
                    enrichTripWithPositionData(trip);
                } catch (StorageException e) {
                    LOGGER.warn("Failed to enrich trip {} with position data", trip.getId());
                }
                return trip;
            });
        } else {
            // Return trips without position enrichment for better performance
            return trips;
        }
    }

    /**
     * Complete a trip and mark it as finished.
     *
     * <p><b>HTTP Method:</b> POST</p>
     * <p><b>Path:</b> /api/trips/{id}/complete</p>
     *
     * <p><b>What happens when completing a trip:</b></p>
     * <ul>
     *   <li>Sets tripStatus to "completed"</li>
     *   <li>Records completionDate (current timestamp)</li>
     *   <li>Captures final device position
     *       (completionLatitude, completionLongitude)</li>
     *   <li>Calculates total trip time in minutes
     *       (departureDate to completionDate)</li>
     *   <li>Allows adding optional completion notes</li>
     * </ul>
     *
     * <p><b>Permissions:</b> User must have permission to the trip's device
     * (same permission required to create trips)</p>
     *
     * <p><b>Validations:</b></p>
     * <ul>
     *   <li>Trip must exist</li>
     *   <li>Trip must not already be completed (tripStatus != "completed")</li>
     *   <li>Trip must not be cancelled (tripStatus != "cancelled")</li>
     *   <li>User must have permission to the associated device</li>
     * </ul>
     *
     * <p><b>Post-Completion Behavior:</b> After completion, GET endpoints will return null for
     * transient fields (currentLatitude, currentLongitude, currentSpeed, distanceCovered) to indicate
     * the trip is no longer active. Use completionLatitude/completionLongitude for final position.</p>
     *
     * @param id Trip ID to complete
     * @param completionNotes Optional notes/remarks about the completion
     *                        (can be null or empty)
     * @return Response with completed Trip including completion metadata (HTTP 200)
     * @throws WebApplicationException if trip not found (404),
     *         already completed (400), no permission (403)
     * @throws StorageException if database error occurs
     */
    @Operation(
        summary = "Complete a trip",
        description = "Mark trip as completed. Sets tripStatus='completed', records completion timestamp "
                + "and final position, calculates total trip time. User must have device permission.",
        responses = {
            @ApiResponse(responseCode = "200", description = "Trip completed successfully",
                content = @Content(schema = @Schema(implementation = Trip.class))),
            @ApiResponse(responseCode = "400", description = "Trip already completed or cancelled"),
            @ApiResponse(responseCode = "403", description = "No permission to complete this trip"),
            @ApiResponse(responseCode = "404", description = "Trip not found")
        }
    )
    @POST
    @Path("{id}/complete")
    public Response completeTrip(
            @Parameter(description = "Trip ID", required = true)
                @jakarta.ws.rs.PathParam("id") long id,
            @Parameter(description = "Optional completion notes/remarks")
                @QueryParam("notes") String completionNotes) throws Exception {

        // Fetch the trip
        Trip trip = storage.getObject(Trip.class, new Request(
                new Columns.All(), new Condition.Equals("id", id)));

        if (trip == null) {
            throw new WebApplicationException(
                    Response.status(Response.Status.NOT_FOUND)
                            .entity("Trip not found")
                            .build());
        }

        // Check user has permission to the device (same permission required to create trips)
        permissionsService.checkPermission(Device.class, getUserId(), trip.getDeviceId());

        // Validate trip is not already completed or cancelled
        if ("completed".equalsIgnoreCase(trip.getTripStatus())) {
            throw new WebApplicationException(
                    Response.status(Response.Status.BAD_REQUEST)
                            .entity("Trip is already completed")
                            .build());
        }

        if ("cancelled".equalsIgnoreCase(trip.getTripStatus())) {
            throw new WebApplicationException(
                    Response.status(Response.Status.BAD_REQUEST)
                            .entity("Cannot complete a cancelled trip")
                            .build());
        }

        // Set completion timestamp
        Date completionDate = new Date();
        trip.setCompletionDate(completionDate);

        // Capture final device position
        try {
            Device device = storage.getObject(Device.class, new Request(
                    new Columns.All(), new Condition.Equals("id", trip.getDeviceId())));

            if (device != null && device.getPositionId() > 0) {
                Position finalPosition = storage.getObject(Position.class, new Request(
                        new Columns.All(), new Condition.Equals("id", device.getPositionId())));

                if (finalPosition != null) {
                    trip.setCompletionLatitude(finalPosition.getLatitude());
                    trip.setCompletionLongitude(finalPosition.getLongitude());
                    LOGGER.info("Captured completion position: {}, {}",
                        finalPosition.getLatitude(), finalPosition.getLongitude());
                } else {
                    LOGGER.warn("Position {} not found for device {} during trip completion",
                        device.getPositionId(), trip.getDeviceId());
                }
            } else {
                LOGGER.warn("Device {} has no position available for trip completion", trip.getDeviceId());
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to capture final position for trip {}: {}", id, e.getMessage());
            // Don't fail the completion if we can't get position, just log it
        }

        // Calculate total trip time in minutes
        if (trip.getDepartureDate() != null) {
            long durationMillis = completionDate.getTime() - trip.getDepartureDate().getTime();
            long durationMinutes = durationMillis / (60 * 1000);
            trip.setTotalTripTimeMinutes(durationMinutes);
            LOGGER.info("Trip {} total duration: {} minutes ({} hours)",
                id, durationMinutes, String.format("%.2f", durationMinutes / 60.0));
        }

        // Set completion notes if provided
        if (completionNotes != null && !completionNotes.trim().isEmpty()) {
            trip.setCompletionNotes(sanitizeString(completionNotes, 1000));
        }

        // Update status
        trip.setTripStatus("completed");
        trip.setUpdatedAt(completionDate);

        // Save to database - explicitly include completion fields since they have @QueryIgnore
        // Only include fields that have values to avoid null constraint issues
        List<String> columnsToUpdate = new ArrayList<>();
        columnsToUpdate.add("tripStatus");
        columnsToUpdate.add("completionDate");
        columnsToUpdate.add("updatedAt");

        if (trip.getCompletionLatitude() != null) {
            columnsToUpdate.add("completionLatitude");
        }
        if (trip.getCompletionLongitude() != null) {
            columnsToUpdate.add("completionLongitude");
        }
        if (trip.getCompletionNotes() != null && !trip.getCompletionNotes().trim().isEmpty()) {
            columnsToUpdate.add("completionNotes");
        }
        if (trip.getTotalTripTimeMinutes() != null) {
            columnsToUpdate.add("totalTripTimeMinutes");
        }

        storage.updateObject(trip, new Request(
                new Columns.Include(columnsToUpdate.toArray(new String[0])),
                new Condition.Equals("id", id)));

        LOGGER.info("Trip {} completed by user {} at {}", id, getUserId(), completionDate);

        // Return the completed trip (without transient position data since trip is completed)
        return Response.ok(trip).build();
    }

}

