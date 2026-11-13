package com.insurance.integration.unit.security;

import com.insurance.integration.partner.entity.Partner;
import com.insurance.integration.security.entity.ApiClient;
import com.insurance.integration.security.jwt.JwtKeyProvider;
import com.insurance.integration.security.jwt.JwtService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Module 2 — JwtService")
class JwtServiceTest {

    private JwtService jwtService;
    private ApiClient client;
    private Partner partner;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(new JwtKeyProvider("", ""), "test-issuer", 3600, 604800);

        partner = Partner.builder()
                .partnerCode("AXIS_BANK")
                .partnerName("Axis Bank")
                .active(true)
                .build();
        ReflectionTestUtils.setField(partner, "id", UUID.randomUUID());

        client = ApiClient.builder()
                .partnerId(partner.getId())
                .clientId("cli_test123")
                .clientSecretHash("not-used")
                .scopes("[\"read\",\"write\"]")
                .tokenExpirySeconds(900)
                .rateLimitPerMinute(25)
                .build();
    }

    @Test
    @DisplayName("access token contains all required claims")
    void accessTokenContainsRequiredClaims() {
        String token = jwtService.generateAccessToken(client, partner);
        Claims claims = jwtService.parseClaims(token);

        assertThat(claims.getIssuer()).isEqualTo("test-issuer");
        assertThat(claims.get(JwtService.CLAIM_PARTNER_ID, String.class))
                .isEqualTo(partner.getId().toString());
        assertThat(claims.get(JwtService.CLAIM_PARTNER_CODE, String.class)).isEqualTo("AXIS_BANK");
        assertThat(claims.get(JwtService.CLAIM_CLIENT_ID, String.class)).isEqualTo("cli_test123");
        assertThat(claims.get(JwtService.CLAIM_SCOPES, List.class)).containsExactly("read", "write");
        assertThat(claims.get(JwtService.CLAIM_RATE_LIMIT, Integer.class)).isEqualTo(25);
        assertThat(claims.get(JwtService.CLAIM_TOKEN_TYPE, String.class)).isEqualTo("access");
        assertThat(claims.getExpiration()).isAfter(claims.getIssuedAt());
    }

    @Test
    @DisplayName("access token honours the per-client expiry")
    void accessTokenHonoursClientExpiry() {
        Instant before = Instant.now();
        String token = jwtService.generateAccessToken(client, partner);
        Instant expiry = jwtService.getExpiration(token);

        assertThat(expiry).isAfter(before.plusSeconds(880));
        assertThat(expiry).isBefore(before.plusSeconds(920));
    }

    @Test
    @DisplayName("refresh token is typed as refresh")
    void refreshTokenIsTyped() {
        String token = jwtService.generateRefreshToken(client, partner);
        Claims claims = jwtService.parseClaims(token);

        assertThat(claims.get(JwtService.CLAIM_TOKEN_TYPE, String.class)).isEqualTo("refresh");
    }

    @Test
    @DisplayName("expired tokens are rejected")
    void expiredTokenRejected() {
        String expired = jwtService.buildToken(client, partner,
                Instant.now().minusSeconds(60), JwtService.TOKEN_TYPE_ACCESS, List.of("read"));

        assertThat(jwtService.isValid(expired)).isFalse();
        assertThatThrownBy(() -> jwtService.parseClaims(expired))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("tokens signed by a different key are rejected")
    void wrongSignatureRejected() {
        JwtService other = new JwtService(new JwtKeyProvider("", ""), "test-issuer", 3600, 604800);
        String foreignToken = other.generateAccessToken(client, partner);

        assertThat(jwtService.isValid(foreignToken)).isFalse();
    }

    @Test
    @DisplayName("tokens from a different issuer are rejected")
    void wrongIssuerRejected() {
        JwtService other = new JwtService(new JwtKeyProvider("", ""), "different-issuer", 3600, 604800);
        String foreignToken = other.generateAccessToken(client, partner);

        assertThat(jwtService.isValid(foreignToken)).isFalse();
    }
}
