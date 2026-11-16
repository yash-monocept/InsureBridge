package com.insurance.integration.security.util;

/**
 * Builds RFC 6750 {@code WWW-Authenticate} challenge values for the
 * partner-facing API.
 *
 * <p>Including the challenge lets standards-compliant OAuth clients tell a
 * missing credential apart from an expired/invalid one and from a token that
 * lacks the required scope, without parsing the JSON error body.</p>
 */
public final class BearerChallenge {

    public static final String REALM = "partner-api";

    private BearerChallenge() {
    }

    /** No credentials were supplied. */
    public static String missingCredentials() {
        return "Bearer realm=\"" + REALM + "\"";
    }

    /** The supplied token is malformed, expired or signed with another key. */
    public static String invalidToken(String description) {
        return "Bearer realm=\"" + REALM + "\", error=\"invalid_token\", error_description=\""
                + escape(description) + "\"";
    }

    /** The token is valid but does not carry the scope the endpoint requires. */
    public static String insufficientScope(String description) {
        return "Bearer realm=\"" + REALM + "\", error=\"insufficient_scope\", error_description=\""
                + escape(description) + "\"";
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
