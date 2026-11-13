package com.insurance.integration.security.jwt;

import com.insurance.integration.partner.entity.Partner;
import com.insurance.integration.security.entity.ApiClient;
import com.insurance.integration.security.util.ScopeUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Generates and validates RS256 JWT access/refresh tokens.
 *
 * <p>Access token claims: {@code partner_id}, {@code partner_code},
 * {@code client_id}, {@code scopes}, {@code rate_limit}, {@code token_type},
 * {@code iss}, {@code iat}, {@code exp}, {@code jti}.</p>
 */
@Service
public class JwtService {

    public static final String CLAIM_PARTNER_ID = "partner_id";
    public static final String CLAIM_PARTNER_CODE = "partner_code";
    public static final String CLAIM_CLIENT_ID = "client_id";
    public static final String CLAIM_SCOPES = "scopes";
    public static final String CLAIM_RATE_LIMIT = "rate_limit";
    public static final String CLAIM_TOKEN_TYPE = "token_type";

    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    private final JwtKeyProvider keyProvider;
    private final String issuer;
    private final long accessTokenTtlSeconds;
    private final long refreshTokenTtlSeconds;

    public JwtService(
            JwtKeyProvider keyProvider,
            @Value("${app.security.jwt.issuer:insurance-integration-middleware}") String issuer,
            @Value("${app.security.jwt-expiry-seconds:3600}") long accessTokenTtlSeconds,
            @Value("${app.security.jwt.refresh-expiry-seconds:604800}") long refreshTokenTtlSeconds) {
        this.keyProvider = keyProvider;
        this.issuer = issuer;
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
        this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
    }

    public String generateAccessToken(ApiClient client, Partner partner) {
        long ttl = client.getTokenExpirySeconds() > 0
                ? client.getTokenExpirySeconds()
                : accessTokenTtlSeconds;
        return buildToken(client, partner, Instant.now().plusSeconds(ttl),
                TOKEN_TYPE_ACCESS, ScopeUtils.parse(client.getScopes()));
    }

    public String generateRefreshToken(ApiClient client, Partner partner) {
        return buildToken(client, partner, Instant.now().plusSeconds(refreshTokenTtlSeconds),
                TOKEN_TYPE_REFRESH, ScopeUtils.parse(client.getScopes()));
    }

    /**
     * Builds a token with an explicit expiry. Exposed for tests that need to
     * produce expired tokens without sleeping.
     */
    public String buildToken(ApiClient client, Partner partner, Instant expiresAt,
                             String tokenType, List<String> scopes) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(issuer)
                .subject(partner.getPartnerCode())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .id(UUID.randomUUID().toString())
                .claim(CLAIM_PARTNER_ID, partner.getId().toString())
                .claim(CLAIM_PARTNER_CODE, partner.getPartnerCode())
                .claim(CLAIM_CLIENT_ID, client.getClientId())
                .claim(CLAIM_SCOPES, scopes)
                .claim(CLAIM_RATE_LIMIT, client.getRateLimitPerMinute())
                .claim(CLAIM_TOKEN_TYPE, tokenType)
                .signWith(keyProvider.getPrivateKey(), Jwts.SIG.RS256)
                .compact();
    }

    /**
     * Parses and verifies signature, issuer and expiry.
     *
     * @throws io.jsonwebtoken.JwtException if the token is invalid or expired
     */
    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(keyProvider.getPublicKey())
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public Instant getExpiration(String token) {
        return parseClaims(token).getExpiration().toInstant();
    }

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtlSeconds;
    }

    public long getRefreshTokenTtlSeconds() {
        return refreshTokenTtlSeconds;
    }
}
