package com.insurance.integration.security.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Utility for converting between the JSON-array representation stored in
 * {@code api_clients.scopes} and Java collections.
 *
 * <p>Parsing is deliberately lenient: malformed rows yield an empty scope list
 * rather than an exception, so a bad row cannot break authentication globally.
 * </p>
 */
public final class ScopeUtils {

    /** Scopes this platform understands. */
    public static final Set<String> ALLOWED_SCOPES = Set.of("read", "write", "admin");

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ScopeUtils() {
    }

    public static List<String> parse(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<String> scopes = MAPPER.readValue(json, new TypeReference<List<String>>() {
            });
            return scopes == null ? List.of() : scopes;
        } catch (Exception e) {
            return List.of();
        }
    }

    public static String toJson(Collection<String> scopes) {
        try {
            return MAPPER.writeValueAsString(scopes == null ? List.of() : scopes);
        } catch (Exception e) {
            return "[]";
        }
    }

    /**
     * Normalises requested scopes: trims, lowercases, de-duplicates and
     * validates against {@link #ALLOWED_SCOPES}. Defaults to {@code ["read"]}.
     *
     * @throws IllegalArgumentException if an unknown scope is requested
     */
    public static List<String> normalize(Collection<String> requested) {
        if (requested == null || requested.isEmpty()) {
            return List.of("read");
        }
        Set<String> result = new LinkedHashSet<>();
        for (String raw : requested) {
            if (raw == null || raw.isBlank()) {
                continue;
            }
            String scope = raw.trim().toLowerCase(Locale.ROOT);
            if (!ALLOWED_SCOPES.contains(scope)) {
                throw new IllegalArgumentException("Unsupported scope: " + scope);
            }
            result.add(scope);
        }
        if (result.isEmpty()) {
            return List.of("read");
        }
        return List.copyOf(result);
    }
}
