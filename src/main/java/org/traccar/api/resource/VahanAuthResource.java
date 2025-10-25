package org.traccar.api.resource;

import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traccar.api.BaseResource;
import org.traccar.model.User;
import org.traccar.storage.Storage;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Request;

import java.util.Map;

@Path("vahanAuth/register")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class VahanAuthResource extends BaseResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(VahanAuthResource.class);

    @Inject
    private Storage storage;

    @PermitAll
    @POST
    public Response register(Map<String, String> request) {
        try {
            String login = request.get("login");
            String password = request.get("password");
            String email = request.get("email");
            String name = request.get("name");

            if (login == null || login.trim().isEmpty() || password == null || password.trim().isEmpty()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(Map.of("error", "bad_request", "message", "login and password are required"))
                        .build();
            }

            // Check if user already exists
            User existing = storage.getObject(User.class, new Request(
                    new Columns.All(),
                    new Condition.Equals("login", login.trim())));
            if (existing != null) {
                return Response.status(Response.Status.CONFLICT)
                        .entity(Map.of("error", "conflict", "message", "User already exists"))
                        .build();
            }

            User user = new User();
            user.setLogin(login.trim());
            user.setName(name != null ? name.trim() : login.trim());
            user.setEmail(email != null ? email.trim() : "");
            user.setPassword(password.trim()); // This sets hashedPassword and salt internally
            user.setVehicleValidationAccess(true);
            // Set as readonly user with vehicle validation access only
            user.setAdministrator(false);
            user.setReadonly(true); // Changed to true - allows login and API access
            user.setDeviceReadonly(false);
            user.setLimitCommands(false);
            user.setDisableReports(false); // Changed to false - allow reports access
            user.setFixedEmail(false);
            user.setDisabled(false);
            user.setTemporary(false);
            user.setDeviceLimit(0);
            user.setUserLimit(0);

            // Step 1: Insert user with all regular fields (excludes @QueryIgnore fields)
            user.setId(storage.addObject(user, new Request(new Columns.Exclude("id"))));

            // Step 2: Update to include hashedPassword and salt (they have @QueryIgnore
            // annotation)
            storage.updateObject(user, new Request(
                    new Columns.Include("hashedPassword", "salt"),
                    new Condition.Equals("id", user.getId())));

            LOGGER.info("Registered new vehicle validation user: {} ({})", login, email);
            return Response.ok(Map.of("success", true, "message", "User registered successfully")).build();
        } catch (StorageException e) {
            LOGGER.error("Database error in vahanAuth/register", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "database_error", "message", "Database error occurred"))
                    .build();
        } catch (Exception e) {
            LOGGER.error("Error in vahanAuth/register", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(Map.of("error", "internal_error", "message",
                            e.getMessage() != null ? e.getMessage() : "An error occurred"))
                    .build();
        }
    }
}
