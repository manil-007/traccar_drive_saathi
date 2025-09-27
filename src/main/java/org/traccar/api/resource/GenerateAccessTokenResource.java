package org.traccar.api.resource;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.annotation.security.PermitAll;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Path("/generateAccessToken")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class GenerateAccessTokenResource {

    // Token info with expiration
    private static class TokenInfo {
        String cookie;
        long expiresAt;

        TokenInfo(String cookie, long expiresAt) {
            this.cookie = cookie;
            this.expiresAt = expiresAt;
        }
    }

    // In-memory token to cookie map (for demo; use persistent store for production)
    private static final ConcurrentHashMap<String, TokenInfo> tokenCookieMap = new ConcurrentHashMap<>();
    private static final long TOKEN_TTL_MS = TimeUnit.DAYS.toMillis(30); // 30 day token validity

    // Rate limiting: Map<IP, List<timestamp>>
    private static final ConcurrentHashMap<String, LinkedList<Long>> loginAttempts = new ConcurrentHashMap<>();
    private static final int MAX_ATTEMPTS = 5;
    private static final long WINDOW_MS = TimeUnit.MINUTES.toMillis(1);

    public static class AuthRequest {
        public String username;
        public String password;
    }

    public static class AuthResponse {
        public String token;
        public long expiresAt;
    }

    @PermitAll
    @POST
    public Response generateToken(@Context HttpServletRequest req, AuthRequest request) throws Exception {
        // Rate limiting by IP
        String ip = req.getRemoteAddr();
        long now = System.currentTimeMillis();
        loginAttempts.putIfAbsent(ip, new LinkedList<>());
        LinkedList<Long> attempts = loginAttempts.get(ip);
        synchronized (attempts) {
            // Remove old attempts
            while (!attempts.isEmpty() && now - attempts.peekFirst() > WINDOW_MS) {
                attempts.pollFirst();
            }
            if (attempts.size() >= MAX_ATTEMPTS) {
                return Response.status(Response.Status.TOO_MANY_REQUESTS)
                        .entity("Too many attempts, try again later.").build();
            }
            attempts.addLast(now);
        }

        // Validate and sanitize input
        if (request == null || request.username == null || request.password == null
                || request.username.trim().isEmpty() || request.password.trim().isEmpty()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Username and password required.").build();
        }
        String username = request.username.trim();
        String password = request.password.trim();
        if (username.length() > 100 || password.length() > 100) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Input too long.").build();
        }

        // Call Traccar login API (avoid deprecated URL(String) constructor)
        URI uri = new URI("https://drivesathi.tnvconsult.com/api/session");
        // URI uri = new URI("http://localhost:8082/api/session");
        URL url = uri.toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

        // Build form body
        String body = String.format("email=%s&password=%s",
                URLEncoder.encode(username, "UTF-8"),
                URLEncoder.encode(password, "UTF-8"));
        conn.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));

        int code = conn.getResponseCode();
        if (code != 200) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("Login failed").build();
        }

        // Get session cookie
        String cookie = null;
        String headerName = null;
        for (int i = 1; (headerName = conn.getHeaderFieldKey(i)) != null; i++) {
            if (headerName.equalsIgnoreCase("Set-Cookie")) {
                cookie = conn.getHeaderField(i);
                break;
            }
        }
        if (cookie == null) {
            return Response.status(Response.Status.UNAUTHORIZED).entity("No session cookie").build();
        }
        // Only keep the session part
        cookie = cookie.split(";")[0];

        // Generate token and store mapping with expiration
        String token = UUID.randomUUID().toString();
        long expiresAt = now + TOKEN_TTL_MS;
        tokenCookieMap.put(token, new TokenInfo(cookie, expiresAt));

        AuthResponse resp = new AuthResponse();
        resp.token = token;
        resp.expiresAt = expiresAt;
        return Response.ok(resp).build();
    }

    // Utility for other resources to get cookie by token, with expiration check
    public static String getCookieForToken(String token) {
        TokenInfo info = tokenCookieMap.get(token);
        if (info == null)
            return null;
        if (System.currentTimeMillis() > info.expiresAt) {
            tokenCookieMap.remove(token);
            return null;
        }
        return info.cookie;
    }
}