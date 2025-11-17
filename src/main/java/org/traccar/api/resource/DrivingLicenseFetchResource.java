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
import org.traccar.api.service.DrivingLicenseService;
import org.traccar.model.DrivingLicenseRecord;
import org.traccar.model.User;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Request;

import java.util.Map;

@Path("fetch_dl")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class DrivingLicenseFetchResource extends BaseResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(DrivingLicenseFetchResource.class);

    @Inject
    private DrivingLicenseService drivingLicenseService;

    @POST
    public Response fetch(Map<String, String> request) {
        try {
            long userId = getUserId();
            if (userId == 0) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(Map.of("error", "unauthorized", "message", "User not authenticated"))
                        .build();
            }

            // Permission: reuse vehicleValidationAccess for now
            User user = storage.getObject(User.class,
                    new Request(new Columns.All(), new Condition.Equals("id", userId)));
            if (user == null) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(Map.of("error", "unauthorized", "message", "User not found")).build();
            }
            if (!user.getVehicleValidationAccess()) {
                return Response.status(Response.Status.FORBIDDEN)
                        .entity(Map.of("error", "forbidden", "message",
                                "You do not have permission to access DL fetch"))
                        .build();
            }

            String dlNo = request.get("dl_no");
            String dob = request.get("dob");
            if (dlNo == null || dlNo.isBlank()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "bad_request", "message", "dl_no is required"))
                        .build();
            }
            if (dob == null || dob.isBlank()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "bad_request", "message", "dob is required (DD-MM-YYYY)"))
                        .build();
            }

            try {
                DrivingLicenseRecord dl = drivingLicenseService.getOrFetch(userId, dlNo, dob);
                if (dl == null) {
                    return Response.status(Response.Status.NOT_FOUND)
                            .entity(Map.of("error", "not_found", "message", "DL not found"))
                            .build();
                }
                JSONObject out = drivingLicenseService.buildResponse(dl);
                return Response.ok(out.toMap()).build();
            } catch (IllegalStateException cfg) {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(Map.of("error", "config_error", "message", cfg.getMessage()))
                        .build();
            } catch (Exception ex) {
                LOGGER.error("Error in DL fetch", ex);
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(Map.of("error", "internal_error", "message", ex.getMessage()))
                        .build();
            }

        } catch (StorageException se) {
            LOGGER.error("Database error in DL fetch", se);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "database_error", "message", "Database error occurred"))
                    .build();
        }
    }
}
