package org.traccar.api.resource;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.traccar.config.Config;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("drivingLicense")
@Produces(MediaType.APPLICATION_JSON)
@Consumes({ MediaType.APPLICATION_JSON, MediaType.APPLICATION_FORM_URLENCODED })
public class DrivingLicenseResource {

    @Inject
    private Config config;

    private static final Logger LOGGER = LoggerFactory.getLogger(DrivingLicenseResource.class);

    private static final String APISETU_PATH = "certificate/v3/transport/drvlc";

    @POST
    public Response verify(@jakarta.ws.rs.core.Context jakarta.servlet.http.HttpServletRequest servletRequest) {
        try {
            // Read request body as text (JSON) or fallback to form params
            if (servletRequest == null) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(org.json.JSONObject.valueToString(java.util.Map.of("error", "empty_body"))).build();
            }

            String contentType = servletRequest.getContentType() != null
                    ? servletRequest.getContentType().toLowerCase()
                    : "";

            String body = null;
            if (contentType.startsWith("application/x-www-form-urlencoded")) {
                // collect form params into JSON-like object string
                var params = servletRequest.getParameterMap();
                var map = new org.json.JSONObject();
                for (var e : params.entrySet()) {
                    String k = e.getKey();
                    String[] vals = e.getValue();
                    if (vals != null && vals.length > 0) {
                        map.put(k, vals[0]);
                    }
                }
                body = map.toString();
            } else {
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(servletRequest.getInputStream(), StandardCharsets.UTF_8))) {
                    body = br.lines().collect(Collectors.joining("\n"));
                } catch (Exception ex) {
                    LOGGER.warn("Failed to read request body: {}", ex.getMessage());
                    body = null;
                }
            }

            if (body == null || body.isBlank()) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(org.json.JSONObject.valueToString(java.util.Map.of("error", "empty_body"))).build();
            }

            String base = config.getString(org.traccar.config.Keys.APISETU_BASE_URL);
            String apiKey = config.getString(org.traccar.config.Keys.APISETU_API_KEY);
            String clientId = config.getString(org.traccar.config.Keys.APISETU_CLIENT_ID);

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(org.traccar.config.ApiSetuConfig.fullUrl(base, APISETU_PATH)))
                    .timeout(Duration.ofSeconds(30))
                    // later need to change the demo key to production key
                    .header("Content-Type", "application/json")
                    .header("X-APISETU-APIKEY", apiKey)
                    .header("X-APISETU-CLIENTID", clientId)

                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> resp = client.send(request, HttpResponse.BodyHandlers.ofString());
            int status = resp.statusCode();
            String respBody = resp.body();

            // Pass-through response: if APISETU returns PDF or other content types, we
            // default to returning JSON/text
            if (status >= 200 && status < 300) {
                // Try to parse as JSON; if not JSON, return raw string
                try {
                    var json = new org.json.JSONObject(respBody);
                    return Response.ok(json.toMap()).build();
                } catch (Exception ignored) {
                    // return raw body
                    return Response.ok(java.util.Map.of("data", respBody)).build();
                }
            } else if (status == 400) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity(java.util.Map.of("error", "bad_request", "detail", respBody)).build();
            } else if (status == 401) {
                return Response.status(Response.Status.UNAUTHORIZED)
                        .entity(java.util.Map.of("error", "unauthorized", "detail", respBody)).build();
            } else if (status == 404) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity(java.util.Map.of("error", "not_found", "detail", respBody)).build();
            } else {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(java.util.Map.of("error", "upstream_error", "status", status, "detail", respBody))
                        .build();
            }

        } catch (Exception e) {
            LOGGER.warn("DrivingLicense verify failed: {}", e.getMessage());
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity(java.util.Map.of("error", e.getMessage())).build();
        }
    }
}
