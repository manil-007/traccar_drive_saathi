package org.traccar.api.resource;

import jakarta.annotation.security.PermitAll;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import com.google.inject.RestrictedBindingSource.Permit;

@Path("docs")
public class SwaggerResource {
    @PermitAll
    @GET
    @Produces(MediaType.TEXT_HTML)
    public Response getSwaggerUI() {
        String html = """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Traccar API Documentation</title>
                    <link rel="stylesheet" type="text/css" href="/api/docs/webjars/swagger-ui.css">
                    <style>
                        body { margin: 0; padding: 0; }
                    </style>
                </head>
                <body>
                    <div id="swagger-ui"></div>
                    <script src="/api/docs/webjars/swagger-ui-bundle.js" charset="UTF-8"></script>
                    <script src="/api/docs/webjars/swagger-ui-standalone-preset.js" charset="UTF-8"></script>
                    <script>
                        window.onload = function() {
                            window.ui = SwaggerUIBundle({
                                url: "/api/docs/openapi.yaml",
                                dom_id: '#swagger-ui',
                                deepLinking: true,
                                presets: [
                                    SwaggerUIBundle.presets.apis,
                                    SwaggerUIStandalonePreset
                                ],
                                plugins: [
                                    SwaggerUIBundle.plugins.DownloadUrl
                                ],
                                layout: "StandaloneLayout",
                                persistAuthorization: true
                            });
                        };
                    </script>
                </body>
                </html>
                """;
        return Response.ok(html).build();
    }

    @PermitAll
    @GET
    @Path("openapi.yaml")
    @Produces("text/yaml")
    public Response getOpenApiSpec() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("openapi.yaml")) {
            if (is == null) {
                return Response.status(Response.Status.NOT_FOUND)
                        .entity("OpenAPI specification not found").build();
            }
            byte[] bytes = is.readAllBytes();
            String content = new String(bytes, StandardCharsets.UTF_8);
            return Response.ok(content).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Error reading OpenAPI specification: " + e.getMessage()).build();
        }
    }

    @PermitAll
    @GET
    @Path("webjars/{resource:.*}")
    public Response getWebJar(@jakarta.ws.rs.PathParam("resource") String resource) {
        try {
            String resourcePath = "META-INF/resources/webjars/swagger-ui/5.17.14/" + resource;
            InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath);
            if (is == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }

            byte[] bytes = is.readAllBytes();
            String contentType = getContentType(resource);
            return Response.ok(bytes).type(contentType).build();
        } catch (Exception e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("Error loading resource: " + e.getMessage()).build();
        }
    }

    private String getContentType(String resource) {
        if (resource.endsWith(".css")) {
            return "text/css";
        } else if (resource.endsWith(".js")) {
            return "application/javascript";
        } else if (resource.endsWith(".png")) {
            return "image/png";
        } else if (resource.endsWith(".html")) {
            return "text/html";
        }
        return "application/octet-stream";
    }
}
