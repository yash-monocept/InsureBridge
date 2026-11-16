package com.insurance.integration.unit.security;

import com.insurance.integration.common.exception.SsrfAttemptException;
import com.insurance.integration.partner.entity.Partner;
import com.insurance.integration.security.entity.ApiClient;
import com.insurance.integration.security.jwt.JwtKeyProvider;
import com.insurance.integration.security.jwt.JwtService;
import com.insurance.integration.security.service.RateLimitService;
import com.insurance.integration.security.validator.SsrfValidator;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Randomized (property-style) tests for the Module 2 security controls.
 *
 * <p>The design specifies property-based coverage for token integrity, SSRF
 * host validation and rate limiting. This suite exercises each property across
 * 100 randomized inputs with a fixed seed, so failures reproduce exactly while
 * still covering input shapes beyond the example-based tests.</p>
 */
@DisplayName("Module 2 — randomized security properties")
class SecurityPropertyTest {

    private static final int ITERATIONS = 100;
    private static final long SEED = 20261116L;
    private static final String BASE64_URL =
            "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_";

    private final Random random = new Random(SEED);
    private final JwtService jwtService =
            new JwtService(new JwtKeyProvider("", ""), "property-issuer", 3600, 604800);
    private final SsrfValidator ssrfValidator = new SsrfValidator();

    @Test
    @DisplayName("P1: every valid credential set yields a token with matching identity claims")
    void tokensRoundTripEveryGeneratedIdentity() {
        for (int i = 0; i < ITERATIONS; i++) {
            Partner partner = randomPartner();
            ApiClient client = randomClient(partner);
            List<String> scopes = scopesOf(client);

            Claims claims = jwtService.parseClaims(jwtService.generateAccessToken(client, partner));

            assertThat(claims.get(JwtService.CLAIM_PARTNER_ID, String.class))
                    .isEqualTo(partner.getId().toString());
            assertThat(claims.get(JwtService.CLAIM_PARTNER_CODE, String.class))
                    .isEqualTo(partner.getPartnerCode());
            assertThat(claims.get(JwtService.CLAIM_CLIENT_ID, String.class))
                    .isEqualTo(client.getClientId());
            assertThat(claims.get(JwtService.CLAIM_SCOPES, List.class)).isEqualTo(scopes);
            assertThat(claims.get(JwtService.CLAIM_RATE_LIMIT, Integer.class))
                    .isEqualTo(client.getRateLimitPerMinute());
            assertThat(claims.get(JwtService.CLAIM_TOKEN_TYPE, String.class))
                    .isEqualTo(JwtService.TOKEN_TYPE_ACCESS);
            assertThat(jwtService.isValid(jwtService.generateRefreshToken(client, partner)))
                    .as("refresh tokens are structurally valid JWTs")
                    .isTrue();
        }
    }

    @Test
    @DisplayName("P3: mutating any JWT segment always invalidates the token")
    void everyMutatedTokenIsRejected() {
        Partner partner = randomPartner();

        for (int i = 0; i < ITERATIONS; i++) {
            String token = jwtService.generateAccessToken(randomClient(partner), partner);
            String[] parts = token.split("\\.");
            int segment = random.nextInt(parts.length);
            char replacement = differentChar(parts[segment].charAt(1));
            parts[segment] = parts[segment].charAt(0) + String.valueOf(replacement)
                    + parts[segment].substring(2);

            assertThat(jwtService.isValid(String.join(".", parts)))
                    .as("mutated segment %d must not verify", segment)
                    .isFalse();
        }
    }

    @Test
    @DisplayName("P3: tokens that expired in the past are never accepted")
    void everyExpiredTokenIsRejected() {
        Partner partner = randomPartner();

        for (int i = 0; i < ITERATIONS; i++) {
            String expired = jwtService.buildToken(randomClient(partner), partner,
                    java.time.Instant.now().minusSeconds(1 + random.nextInt(3600)),
                    JwtService.TOKEN_TYPE_ACCESS, List.of("read"));

            assertThat(jwtService.isValid(expired)).isFalse();
        }
    }

    @Test
    @DisplayName("P6: only approved, non-internal hosts pass SSRF validation")
    void ssrfHonoursApprovedHostsForEveryGeneratedUrl() {
        String approvedPatterns = "[\"*.s3.amazonaws.com\",\"docs.axisbank.example\"]";

        for (int i = 0; i < ITERATIONS; i++) {
            String label = "bucket-" + randomAlphaNumeric(12).toLowerCase();

            assertThatCode(() -> ssrfValidator.validate(
                    "https://" + label + ".s3.amazonaws.com/claims/policy.pdf", approvedPatterns))
                    .as("approved wildcard host must be allowed")
                    .doesNotThrowAnyException();

            String foreignHost = randomAlphaNumeric(12).toLowerCase() + ".attacker.example";
            assertThatThrownBy(() -> ssrfValidator.validate(
                    "https://" + foreignHost + "/claims/policy.pdf", approvedPatterns))
                    .as("unapproved host must be blocked")
                    .isInstanceOf(SsrfAttemptException.class)
                    .hasMessageNotContaining(foreignHost);
        }
    }

    @Test
    @DisplayName("P6: loopback, private and metadata addresses are always blocked")
    void internalAddressesAreAlwaysBlocked() {
        List<String> internalHosts = List.of(
                "localhost",
                "127.0.0.1",
                "127.0.0." + (1 + random.nextInt(254)),
                "10." + random.nextInt(255) + "." + random.nextInt(255) + "." + random.nextInt(255),
                "192.168." + random.nextInt(255) + "." + (1 + random.nextInt(254)),
                "172." + (16 + random.nextInt(16)) + ".0." + (1 + random.nextInt(254)),
                "169.254.169.254",
                "metadata.google.internal");

        for (int i = 0; i < ITERATIONS; i++) {
            String host = internalHosts.get(random.nextInt(internalHosts.size()));

            assertThat(ssrfValidator.isInternalHost(host))
                    .as("%s must be classified as internal", host)
                    .isTrue();
            assertThatThrownBy(() -> ssrfValidator.validate("http://" + host + "/latest/", "[\"*\"]"))
                    .as("%s must be blocked even with a wildcard", host)
                    .isInstanceOf(SsrfAttemptException.class);
        }
    }

    @Test
    @DisplayName("P5: a client receives exactly its configured quota per window")
    void rateLimitAllowsExactlyTheConfiguredQuota() {
        long now = 1_800_000_000_000L;

        for (int i = 0; i < ITERATIONS; i++) {
            String key = "client-" + i;
            int limit = 1 + random.nextInt(5);
            RateLimitService service = new RateLimitService(60);

            for (int request = 0; request < limit; request++) {
                RateLimitService.RateLimitResult result =
                        service.tryAcquire(key, limit, now + request);
                assertThat(result.allowed())
                        .as("request %d of %d must be allowed", request + 1, limit)
                        .isTrue();
            }

            RateLimitService.RateLimitResult rejected =
                    service.tryAcquire(key, limit, now + limit);
            assertThat(rejected.allowed())
                    .as("request beyond the quota must be rejected")
                    .isFalse();
            assertThat(rejected.retryAfterSeconds()).isPositive();
        }
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private Partner randomPartner() {
        Partner partner = Partner.builder()
                .partnerCode("PROP_" + randomAlphaNumeric(10))
                .partnerName("Property Test Partner")
                .active(true)
                .build();
        ReflectionTestUtils.setField(partner, "id", UUID.randomUUID());
        return partner;
    }

    private ApiClient randomClient(Partner partner) {
        List<String> scopes = new ArrayList<>();
        for (String candidate : List.of("read", "write", "admin")) {
            if (random.nextBoolean()) {
                scopes.add(candidate);
            }
        }
        if (scopes.isEmpty()) {
            scopes.add("read");
        }

        return ApiClient.builder()
                .partnerId(partner.getId())
                .clientId("cli_" + randomAlphaNumeric(16))
                .clientSecretHash("not-used")
                .scopes(toJson(scopes))
                .tokenExpirySeconds(60 + random.nextInt(86_340))
                .rateLimitPerMinute(1 + random.nextInt(10_000))
                .build();
    }

    private List<String> scopesOf(ApiClient client) {
        return com.insurance.integration.security.util.ScopeUtils.parse(client.getScopes());
    }

    private String toJson(List<String> scopes) {
        return scopes.stream()
                .map(scope -> "\"" + scope + "\"")
                .reduce((left, right) -> left + "," + right)
                .map(joined -> "[" + joined + "]")
                .orElse("[]");
    }

    private String randomAlphaNumeric(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append((char) ('A' + random.nextInt(26)));
        }
        return builder.toString();
    }

    private char differentChar(char current) {
        char candidate;
        do {
            candidate = BASE64_URL.charAt(random.nextInt(BASE64_URL.length()));
        } while (candidate == current);
        return candidate;
    }
}
