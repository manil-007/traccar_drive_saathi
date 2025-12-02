/*
 * Transporter management API
 */
package org.traccar.api.resource;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import org.traccar.api.BaseResource;
import org.traccar.model.Transporter;
import org.traccar.model.User;
import org.traccar.storage.StorageException;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Order;
import org.traccar.storage.query.Request;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Path("/transporters")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class TransporterResource extends BaseResource {

    private void checkPermission() throws SecurityException, StorageException {
        long userId = getUserId();
        User user = storage.getObject(User.class, new Request(new Columns.All(), new Condition.Equals("id", userId)));
        if (user == null || !user.getVehicleValidationAccess()) {
            throw new SecurityException("Access denied");
        }
    }

    @GET
    public Response list(@QueryParam("limit") @DefaultValue("100") int limit,
            @QueryParam("offset") @DefaultValue("0") int offset,
            @QueryParam("search") String search) throws Exception {
        checkPermission();
        long userId = getUserId();

        // Build conditions
        Condition condition = new Condition.Equals("userId", userId);
        if (search != null && !search.isBlank()) {
            condition = new Condition.And(condition, new Condition.Compare("name", "ILIKE", "%" + search + "%"));
        }

        Stream<Transporter> stream = storage.getObjectsStream(Transporter.class,
                new Request(new Columns.All(), condition, new Order("name")));
        List<Transporter> all = stream.collect(Collectors.toList());
        int total = all.size();
        List<Transporter> page = all.stream().skip(offset).limit(limit).collect(Collectors.toList());

        JSONArray items = new JSONArray();
        for (Transporter t : page) {
            JSONObject o = new JSONObject();
            o.put("id", t.getId());
            o.put("name", t.getName());
            o.put("createdAt", t.getCreatedAt() != null ? t.getCreatedAt().getTime() : JSONObject.NULL);
            o.put("updatedAt", t.getUpdatedAt() != null ? t.getUpdatedAt().getTime() : JSONObject.NULL);
            items.put(o);
        }
        JSONObject out = new JSONObject();
        out.put("items", items);
        out.put("total", total);
        out.put("limit", limit);
        out.put("offset", offset);
        return Response.ok(out.toString()).build();
    }

    @POST
    public Response create(String body) throws Exception {
        checkPermission();
        long userId = getUserId();
        JSONObject json = new JSONObject(body);
        String name = json.optString("name", null);
        if (name == null || name.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("name is required").build();
        }
        name = name.trim().toUpperCase();
        // check uniqueness
        Transporter existing = storage.getObject(Transporter.class, new Request(new Columns.All(),
                new Condition.And(new Condition.Equals("userId", userId), new Condition.Equals("name", name))));
        if (existing != null) {
            return Response.status(Response.Status.CONFLICT).entity("transporter already exists").build();
        }
        Transporter t = new Transporter();
        t.setUserId(userId);
        t.setName(name);
        Date now = new Date();
        t.setCreatedAt(now);
        t.setUpdatedAt(now);
        t.setId(storage.addObject(t, new Request(new Columns.Exclude("id"))));
        JSONObject out = new JSONObject();
        out.put("id", t.getId());
        out.put("name", t.getName());
        return Response.status(Response.Status.CREATED).entity(out.toString()).build();
    }

    @GET
    @Path("/{id}")
    public Response getOne(@PathParam("id") long id) throws Exception {
        checkPermission();
        long userId = getUserId();
        Transporter t = storage.getObject(Transporter.class,
                new Request(new Columns.All(), new Condition.Equals("id", id)));
        if (t == null || t.getUserId() != userId) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        JSONObject out = new JSONObject();
        out.put("id", t.getId());
        out.put("name", t.getName());
        return Response.ok(out.toString()).build();
    }

    @PUT
    @Path("/{id}")
    public Response update(@PathParam("id") long id, String body) throws Exception {
        checkPermission();
        long userId = getUserId();
        Transporter t = storage.getObject(Transporter.class,
                new Request(new Columns.All(), new Condition.Equals("id", id)));
        if (t == null || t.getUserId() != userId) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        JSONObject json = new JSONObject(body);
        String name = json.optString("name", null);
        if (name == null || name.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("name is required").build();
        }
        name = name.trim().toUpperCase();
        // check duplicate
        Transporter dup = storage.getObject(Transporter.class, new Request(new Columns.All(), new Condition.And(
                new Condition.Equals("userId", userId), new Condition.Equals("name", name))));
        if (dup != null && dup.getId() != id) {
            return Response.status(Response.Status.CONFLICT).entity("transporter already exists").build();
        }
        t.setName(name);
        t.setUpdatedAt(new Date());
        storage.updateObject(t, new Request(new Columns.Exclude("id", "createdAt"), new Condition.Equals("id", id)));
        return Response.noContent().build();
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") long id) throws Exception {
        checkPermission();
        long userId = getUserId();
        Transporter t = storage.getObject(Transporter.class,
                new Request(new Columns.All(), new Condition.Equals("id", id)));
        if (t == null || t.getUserId() != userId) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        // Cascade: remove mappings for this user + transporter name, but keep vehicle
        // data and logs intact
        storage.removeObject(org.traccar.model.TransporterVehicleMap.class, new Request(new Condition.And(
                new Condition.Equals("userId", userId),
                new Condition.Equals("transporterName", t.getName()))));

        // Remove transporter record
        storage.removeObject(Transporter.class, new Request(new Condition.Equals("id", id)));
        return Response.noContent().build();
    }
}
