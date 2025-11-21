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

import org.traccar.api.ExtendedObjectResource;
import org.traccar.model.Device;
import org.traccar.model.Driver;
import org.traccar.model.Group;
import org.traccar.model.Trip;
import org.traccar.model.User;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Order;
import org.traccar.storage.query.Request;

import java.util.Date;
import java.util.LinkedList;
import java.util.stream.Stream;

@Path("trips")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TripResource extends ExtendedObjectResource<Trip> {

    public TripResource() {
        super(Trip.class, "name");
    }

    private void validateAndEnrichTrip(Trip trip, boolean isUpdate) throws StorageException {
        // Validate required fields
        if (trip.getDeviceId() <= 0) {
            throw new WebApplicationException(
                    Response.status(Response.Status.BAD_REQUEST)
                            .entity("deviceId is required")
                            .build());
        }

        if (trip.getOrigin() == null || trip.getOrigin().trim().isEmpty()) {
            throw new WebApplicationException(
                    Response.status(Response.Status.BAD_REQUEST)
                            .entity("origin is required")
                            .build());
        }

        if (trip.getDestination() == null || trip.getDestination().trim().isEmpty()) {
            throw new WebApplicationException(
                    Response.status(Response.Status.BAD_REQUEST)
                            .entity("destination is required")
                            .build());
        }

        if (trip.getDepartureDate() == null) {
            throw new WebApplicationException(
                    Response.status(Response.Status.BAD_REQUEST)
                            .entity("departureDate is required")
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

        // Auto-fill asset number from device uniqueId
        trip.setAssetNumber(device.getUniqueId());

        // Auto-fill asset type from device attributes
        if (device.getAttributes() != null && device.getAttributes().containsKey("assetType")) {
            trip.setAssetType(device.getAttributes().get("assetType").toString());
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
        return super.add(entity);
    }

    @PUT
    @Path("{id}")
    @Override
    public Response update(Trip entity) throws Exception {
        validateAndEnrichTrip(entity, true);
        return super.update(entity);
    }

    @GET
    @Override
    public Stream<Trip> get(
            @QueryParam("all") boolean all,
            @QueryParam("userId") long userId,
            @QueryParam("groupId") long groupId,
            @QueryParam("deviceId") long deviceId,
            @QueryParam("excludeAttributes") boolean excludeAttributes) throws StorageException {

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
        }

        Columns columns = excludeAttributes ? new Columns.Exclude("attributes") : new Columns.All();
        return storage.getObjectsStream(baseClass, new Request(
                columns, Condition.merge(conditions), new Order("name")));
    }

}
