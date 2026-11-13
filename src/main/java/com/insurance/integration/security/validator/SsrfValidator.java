package com.insurance.integration.security.validator;

import com.insurance.integration.common.exception.SsrfAttemptException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Guards against Server-Side Request Forgery (SSRF) for partner-supplied
 * document URLs.
 *
 * <p>Validation order:</p>
 * <ol>
 *   <li>URL must be syntactically valid with an {@code http}/{@code https} scheme.</li>
 *   <li>Credentials in the URL (userinfo) are rejected.</li>
 *   <li>Loopback, private, link-local and cloud-metadata hosts are rejected
 *       regardless of the approved-host list.</li>
 *   <li>The host must match one of the partner's {@code approved_source_hosts}
 *       patterns. A {@code *} matches exactly one DNS label, so
 *       {@code *.s3.amazonaws.com} matches {@code bucket.s3.amazonaws.com}.</li>
 * </ol>
 *
 * <p>Failure messages never echo the URL or host, so callers learn nothing
 * about internal infrastructure.</p>
 */
@Component
@Slf4j
public class SsrfValidator {

    public void validate(String url, String approvedSourceHostsJson) {
        UriParts parts = parse(url);

        if (parts.host() == null || parts.host().isBlank()) {
            reject();
        }
        if (parts.userInfo() != null) {
            reject();
        }
        if (isInternalHost(parts.host())) {
            log.warn("SECURITY: SSRF attempt blocked — internal host");
            reject();
        }

        List<String> patterns = parsePatterns(approvedSourceHostsJson);
        if (patterns.isEmpty()) {
            log.warn("SECURITY: SSRF attempt blocked — partner has no approved source hosts");
            reject();
        }

        String host = parts.host().toLowerCase(Locale.ROOT);
        for (String pattern : patterns) {
            if (matches(host, pattern)) {
                return;
            }
        }

        log.warn("SECURITY: SSRF attempt blocked — host not in approved list");
        reject();
    }

    public boolean isAllowed(String url, String approvedSourceHostsJson) {
        try {
            validate(url, approvedSourceHostsJson);
            return true;
        } catch (SsrfAttemptException e) {
            return false;
        }
    }

    public boolean matches(String host, String rawPattern) {
        if (rawPattern == null || rawPattern.isBlank()) {
            return false;
        }
        String pattern = rawPattern.trim().toLowerCase(Locale.ROOT);
        String hostNormalised = host.toLowerCase(Locale.ROOT);

        String[] patternLabels = pattern.split("\\.", -1);
        String[] hostLabels = hostNormalised.split("\\.", -1);
        if (patternLabels.length != hostLabels.length) {
            return false;
        }
        for (int i = 0; i < patternLabels.length; i++) {
            String p = patternLabels[i];
            if ("*".equals(p) || "**".equals(p)) {
                if (hostLabels[i].isEmpty()) {
                    return false;
                }
                continue;
            }
            if (!p.equals(hostLabels[i])) {
                return false;
            }
        }
        return true;
    }

    private List<String> parsePatterns(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        // Fast path for the documented JSON-array format, with a comma-separated
        // fallback so misconfigured seed data degrades safely (deny by default).
        String trimmed = json.trim();
        if (trimmed.startsWith("[")) {
            String body = trimmed.substring(1, trimmed.endsWith("]") ? trimmed.length() - 1 : trimmed.length());
            List<String> result = new ArrayList<>();
            for (String piece : body.split(",")) {
                String value = piece.trim().replaceAll("^\"|\"$", "");
                if (!value.isBlank()) {
                    result.add(value);
                }
            }
            return result;
        }
        return List.of(trimmed.split(","));
    }

    private UriParts parse(String url) {
        if (url == null || url.isBlank()) {
            reject();
        }
        try {
            URI uri = new URI(url.trim());
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                reject();
            }
            if (uri.getPort() != -1 && uri.getPort() != 80 && uri.getPort() != 443) {
                log.warn("SECURITY: SSRF attempt blocked — non-standard port");
                reject();
            }
            return new UriParts(uri.getHost(), uri.getUserInfo());
        } catch (URISyntaxException e) {
            reject();
            return null; // unreachable
        }
    }

    public boolean isInternalHost(String host) {
        String h = host.toLowerCase(Locale.ROOT);
        if (h.equals("localhost") || h.equals("0.0.0.0") || h.equals("::1") || h.equals("[::1]")
                || h.endsWith(".localhost") || h.endsWith(".local") || h.endsWith(".internal")
                || h.equals("metadata.google.internal")
                || h.equals("169.254.169.254")) {
            return true;
        }

        // IPv6 loopback / link-local / unique-local
        if (h.contains(":")) {
            return h.startsWith("fe80") || h.startsWith("fc") || h.startsWith("fd");
        }

        // IPv4 literal checks
        if (h.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
            String[] octets = h.split("\\.");
            try {
                int a = Integer.parseInt(octets[0]);
                int b = Integer.parseInt(octets[1]);
                return a == 0 || a == 10 || a == 127
                        || (a == 169 && b == 254)
                        || (a == 172 && b >= 16 && b <= 31)
                        || (a == 192 && b == 168);
            } catch (NumberFormatException e) {
                return true;
            }
        }
        return false;
    }

    private static void reject() {
        throw new SsrfAttemptException("Provided URL is not allowed.");
    }

    private record UriParts(String host, String userInfo) {
    }
}
