package org.traccar.config;

/**
 * Helper utilities for APISETU configuration values.
 */
public final class ApiSetuConfig {

    private ApiSetuConfig() {
    }

    /**
     * Return normalized base URL for APISETU. Ensures it ends with a single slash.
     */
    public static String normalizeBase(String base) {
        if (base == null || base.isBlank()) {
            return "https://sandbox.api-setu.in/";
        }
        String trimmed = base.trim();
        // remove trailing spaces
        // ensure single trailing slash
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + "/";
    }

    /**
     * Build full URL by concatenating normalized base and the provided path (path
     * must not start with slash).
     */
    public static String fullUrl(String base, String path) {
        String nb = normalizeBase(base);
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        return nb + path;
    }
}
