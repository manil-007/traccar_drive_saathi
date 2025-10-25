/*
 * Validation log search API
 */
package org.traccar.api.resource;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import org.traccar.api.BaseResource;
import org.traccar.model.TransporterVehicleMap;
import org.traccar.model.User;
import org.traccar.model.ValidationLog;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Order;
import org.traccar.storage.query.Request;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Path("/validationLogs")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ValidationLogResource extends BaseResource {

    private static final SimpleDateFormat INPUT_DATE_FORMAT = new SimpleDateFormat("dd-MM-yyyy");
    private static final SimpleDateFormat OUTPUT_DATE_FORMAT = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
    
    static {
        // Set IST timezone for all date operations
        TimeZone istTimeZone = TimeZone.getTimeZone("Asia/Kolkata");
        INPUT_DATE_FORMAT.setTimeZone(istTimeZone);
        OUTPUT_DATE_FORMAT.setTimeZone(istTimeZone);
    }

    private void checkPermission() throws SecurityException, StorageException {
        long userId = getUserId();
        User user = storage.getObject(User.class, new Request(new Columns.All(), new Condition.Equals("id", userId)));
        if (user == null || !user.getVehicleValidationAccess()) {
            throw new SecurityException("Access denied");
        }
    }

    @GET
    public Response search(
            @QueryParam("startDate") String startDateStr,
            @QueryParam("endDate") String endDateStr,
            @QueryParam("transporterName") String transporterName,
            @QueryParam("number") String number,
            @QueryParam("limit") @DefaultValue("100") int limit,
            @QueryParam("offset") @DefaultValue("0") int offset) throws Exception {

        checkPermission();
        long userId = getUserId();

        // Build conditions
        List<Condition> conditions = new ArrayList<>();
        conditions.add(new Condition.Equals("userId", userId));

        // Date range filter
        if (startDateStr != null && !startDateStr.isBlank()) {
            Date startDate = INPUT_DATE_FORMAT.parse(startDateStr);
            conditions.add(new Condition.Compare("validationDate", ">=", startDate));
        }
        if (endDateStr != null && !endDateStr.isBlank()) {
            Date endDate = INPUT_DATE_FORMAT.parse(endDateStr);
            // Add one day to include the entire end date
            Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"));
            cal.setTime(endDate);
            cal.add(Calendar.DAY_OF_MONTH, 1);
            conditions.add(new Condition.Compare("validationDate", "<", cal.getTime()));
        }

        // Number filter (vehicle number or license number)
        if (number != null && !number.isBlank()) {
            conditions.add(new Condition.Equals("number", number.toUpperCase()));
        }

        Condition finalCondition = Condition.merge(conditions);

        // Fetch all matching logs
        Stream<ValidationLog> stream = storage.getObjectsStream(ValidationLog.class,
                new Request(new Columns.All(), finalCondition, new Order("validationDate", true, 0)));
        List<ValidationLog> allLogs = stream.collect(Collectors.toList());

        // Filter by transporter name if provided (requires join with mapping table)
        List<ValidationLog> filteredLogs = allLogs;
        if (transporterName != null && !transporterName.isBlank()) {
            String upperTransporterName = transporterName.toUpperCase();
            filteredLogs = new ArrayList<>();
            for (ValidationLog log : allLogs) {
                if ("vehicle".equalsIgnoreCase(log.getValidationType())) {
                    // Check if this vehicle is mapped to the specified transporter
                    TransporterVehicleMap mapping = storage.getObject(TransporterVehicleMap.class,
                            new Request(new Columns.All(), new Condition.And(
                                    new Condition.Equals("userId", userId),
                                    new Condition.Equals("vehicleNumber", log.getNumber()))));
                    if (mapping != null && upperTransporterName.equals(mapping.getTransporterName())) {
                        filteredLogs.add(log);
                    }
                } else {
                    // For license validations, no transporter filter applies
                    // Skip them when transporter filter is active
                }
            }
        }

        int total = filteredLogs.size();
        List<ValidationLog> page = filteredLogs.stream().skip(offset).limit(limit).collect(Collectors.toList());

        // Build response with transporter info
        JSONArray items = new JSONArray();
        for (ValidationLog log : page) {
            JSONObject item = new JSONObject();
            item.put("id", log.getId());
            item.put("validationType", log.getValidationType());
            item.put("number", log.getNumber());
            item.put("validationDate", log.getValidationDate() != null ? OUTPUT_DATE_FORMAT.format(log.getValidationDate()) : JSONObject.NULL);
            item.put("status", log.getStatus());

            // Add transporter name if it's a vehicle validation
            if ("vehicle".equalsIgnoreCase(log.getValidationType())) {
                TransporterVehicleMap mapping = storage.getObject(TransporterVehicleMap.class,
                        new Request(new Columns.All(), new Condition.And(
                                new Condition.Equals("userId", userId),
                                new Condition.Equals("vehicleNumber", log.getNumber()))));
                if (mapping != null) {
                    item.put("transporterName", mapping.getTransporterName());
                } else {
                    item.put("transporterName", JSONObject.NULL);
                }
            } else {
                item.put("transporterName", JSONObject.NULL);
            }

            items.put(item);
        }

        JSONObject response = new JSONObject();
        response.put("items", items);
        response.put("total", total);
        response.put("limit", limit);
        response.put("offset", offset);

        return Response.ok(response.toString()).build();
    }
}
