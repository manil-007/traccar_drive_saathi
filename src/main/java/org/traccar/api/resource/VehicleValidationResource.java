/*
 * Copyright 2025 Anton Tananaev (anton@traccar.org)
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
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traccar.api.BaseResource;
import org.traccar.api.service.VehicleVerificationService;
import org.traccar.model.User;
import org.traccar.model.VehicleVerification;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Request;

import java.util.Map;

@Path("vehicle/validate")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class VehicleValidationResource extends BaseResource {

        private static final Logger LOGGER = LoggerFactory.getLogger(VehicleValidationResource.class);

        @Inject
        private VehicleVerificationService vehicleDataService;

        /**
         * Validate vehicle RC data endpoint
         * Only accessible to users with vehicleValidationAccess permission
         */
        @POST
        public Response validate(Map<String, String> request) {
                try {
                        // Get current user
                        long userId = getUserId();
                        if (userId == 0) {
                                return Response.status(Response.Status.UNAUTHORIZED)
                                                .entity(Map.of("error", "unauthorized", "message",
                                                                "User not authenticated"))
                                                .build();
                        }

                        // Check if user has vehicleValidationAccess permission
                        User user = storage.getObject(User.class, new Request(
                                        new Columns.All(),
                                        new Condition.Equals("id", userId)));

                        if (user == null) {
                                return Response.status(Response.Status.UNAUTHORIZED)
                                                .entity(Map.of("error", "unauthorized", "message", "User not found"))
                                                .build();
                        }

                        // CRITICAL PERMISSION CHECK:
                        // User must have vehicleValidationAccess permission to use this API
                        if (!user.getVehicleValidationAccess()) {
                                LOGGER.warn("User {} attempted to access vehicle validation without permission",
                                                userId);
                                return Response.status(Response.Status.FORBIDDEN)
                                                .entity(Map.of("error", "forbidden",
                                                                "message",
                                                                "You do not have permission to access vehicle validation"))
                                                .build();
                        }

                        // Extract vehicle number and transporter name from request
                        String vehicleNumber = request.get("vehicleNumber");
                        String transporterName = request.get("transporterName");

                        if (vehicleNumber == null || vehicleNumber.trim().isEmpty()) {
                                return Response.status(Response.Status.BAD_REQUEST)
                                                .entity(Map.of("error", "bad_request",
                                                                "message", "vehicleNumber is required"))
                                                .build();
                        }
                        if (transporterName == null || transporterName.trim().isEmpty()) {
                                return Response.status(Response.Status.BAD_REQUEST)
                                                .entity(Map.of("error", "bad_request",
                                                                "message", "transporterName is required"))
                                                .build();
                        }

                        vehicleNumber = vehicleNumber.trim().toUpperCase();
                        transporterName = transporterName.trim().toUpperCase();

                        try {
                                VehicleVerification vehicle = vehicleDataService.getVehicleData(userId, vehicleNumber,
                                                transporterName);
                                if (vehicle == null) {
                                        return Response.status(Response.Status.NOT_FOUND)
                                                        .entity(Map.of("error", "not_found",
                                                                        "message", "Vehicle data not found"))
                                                        .build();
                                }
                                JSONObject responseData = vehicleDataService.buildResponse(vehicle);
                                return Response.ok(responseData.toMap()).build();
                        } catch (Exception ex) {
                                if (ex.getMessage() != null
                                                && ex.getMessage().contains(
                                                                "Vehicle already exists with another transporter")) {
                                        return Response.status(Response.Status.CONFLICT)
                                                        .entity(Map.of("error", "conflict",
                                                                        "message", ex.getMessage()))
                                                        .build();
                                }
                                LOGGER.error("Error in vehicle validation", ex);
                                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                                                .entity(Map.of("error", "internal_error",
                                                                "message",
                                                                ex.getMessage() != null ? ex.getMessage()
                                                                                : "An error occurred"))
                                                .build();
                        }

                } catch (StorageException e) {
                        LOGGER.error("Database error in vehicle validation", e);
                        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                                        .entity(Map.of("error", "database_error",
                                                        "message", "Database error occurred"))
                                        .build();
                } catch (Exception e) {
                        LOGGER.error("Error in vehicle validation", e);
                        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                                        .entity(Map.of("error", "internal_error",
                                                        "message",
                                                        e.getMessage() != null ? e.getMessage() : "An error occurred"))
                                        .build();
                }
        }
}
