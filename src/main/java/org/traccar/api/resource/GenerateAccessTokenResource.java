package org.traccar.api.resource;

import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Context;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.annotation.security.PermitAll;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.net.URI;
import java.net.URL;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.util.UUID;
import java.util.LinkedList;
@Path("/generateAccessToken")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class GenerateAccessTokenResource {

    // Token info with expiration
    private static class TokenInfo {
        private String cookie;
        private long expiresAt;

        TokenInfo(String cookie, long expiresAt) {
            this.cookie = cookie;
            this.expiresAt = expiresAt;
        }

        public String getCookie() {
            return cookie;
        }

        public long getExpiresAt() {
            return expiresAt;
        }
    }

    // In-memory token to cookie map (for demo; use persistent store for production)
    private static final ConcurrentHashMap<String, TokenInfo> TOKEN_COOKIE_MAP = new ConcurrentHashMap<>();
    private static final long TOKEN_TTL_MS = TimeUnit.DAYS.toMillis(30); // 30 day token validity

    // Rate limiting: Map<IP, List<timestamp>>
    private static final ConcurrentHashMap<String, LinkedList<Long>> LOGIN_ATTEMPTS = new ConcurrentHashMap<>();
    private static final int MAX_ATTEMPTS = 5;
    private static final long WINDOW_MS = TimeUnit.MINUTES.toMillis(1);

    public static class AuthRequest {
        private String username;
        private String password;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class AuthResponse {
        private String token;
        private long expiresAt;

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public long getExpiresAt() {
            return expiresAt;
        }

        public void setExpiresAt(long expiresAt) {
            this.expiresAt = expiresAt;
        }
    }

    @PermitAll
    @POST
    public Response generateToken(@Context HttpServletRequest req, AuthRequest request) throws Exception {
        // Rate limiting by IP
        String ip = req.getRemoteAddr();
        long now = System.currentTimeMillis();
        LOGIN_ATTEMPTS.putIfAbsent(ip, new LinkedList<>());
        LinkedList<Long> attempts = LOGIN_ATTEMPTS.get(ip);
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
        TOKEN_COOKIE_MAP.put(token, new TokenInfo(cookie, expiresAt));

        AuthResponse resp = new AuthResponse();
        resp.token = token;
        resp.expiresAt = expiresAt;
        return Response.ok(resp).build();
    }

    // Utility for other resources to get cookie by token, with expiration check
    public static String getCookieForToken(String token) {
        TokenInfo info = TOKEN_COOKIE_MAP.get(token);
        if (info == null) {
            return null;
        }
        if (System.currentTimeMillis() > info.getExpiresAt()) {
            TOKEN_COOKIE_MAP.remove(token);
            return null;
        }
        return info.cookie;
    }
}

