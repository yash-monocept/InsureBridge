package com.insurance.integration.security.service;

import com.insurance.integration.common.exception.PartnerAuthenticationException;
import com.insurance.integration.common.exception.PartnerNotActiveException;
import com.insurance.integration.partner.entity.Partner;
import com.insurance.integration.partner.repository.PartnerRepository;
import com.insurance.integration.security.dto.TokenResponse;
import com.insurance.integration.security.dto.VerifyTokenResponse;
import com.insurance.integration.security.entity.ApiClient;
import com.insurance.integration.security.jwt.JwtService;
import com.insurance.integration.security.repository.ApiClientRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Orchestrates the OAuth 2.0 Client Credentials flow: credential validation,
 * token issuance, refresh-token exchange and token introspection.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthenticationService {

    private final ClientCredentialsService clientCredentialsService;
    private final ApiClientRepository apiClientRepository;
    private final PartnerRepository partnerRepository;
    private final JwtService jwtService;

    /**
     * Authenticates client credentials and issues an access + refresh token pair.
     */
    public TokenResponse issueTokens(String clientId, String clientSecret, String grantType) {
        if (grantType != null && !grantType.isBlank() && !"client_credentials".equals(grantType)) {
            throw new PartnerAuthenticationException("Unsupported grant_type. Use client_credentials.");
        }

        AuthenticationResult result = clientCredentialsService.authenticate(clientId, clientSecret);
        log.info("OAuth token issued: clientId={}, partnerCode={}",
                result.client().getClientId(), result.partner().getPartnerCode());

        return buildTokenResponse(result.client(), result.partner());
    }

    /**
     * Exchanges a valid refresh token for a new token pair. The client and
     * partner must still be active.
     */
    @Transactional(readOnly = true)
    public TokenResponse refresh(String refreshToken) {
        Claims claims;
        try {
            claims = jwtService.parseClaims(refreshToken);
        } catch (JwtException | IllegalArgumentException e) {
            throw new PartnerAuthenticationException("Invalid or expired refresh token");
        }

        if (!JwtService.TOKEN_TYPE_REFRESH.equals(claims.get(JwtService.CLAIM_TOKEN_TYPE, String.class))) {
            throw new PartnerAuthenticationException("Provided token is not a refresh token");
        }

        String clientId = claims.get(JwtService.CLAIM_CLIENT_ID, String.class);
        ApiClient client = apiClientRepository.findByClientId(clientId)
                .filter(ApiClient::isActive)
                .orElseThrow(() -> new PartnerAuthenticationException("Invalid refresh token"));

        Partner partner = partnerRepository.findById(client.getPartnerId())
                .orElseThrow(() -> new PartnerAuthenticationException("Invalid refresh token"));

        if (!partner.isActive()) {
            throw new PartnerNotActiveException(partner.getPartnerCode());
        }

        log.info("OAuth token refreshed: clientId={}, partnerCode={}", clientId, partner.getPartnerCode());
        return buildTokenResponse(client, partner);
    }

    /**
     * Introspects an access token. Used by {@code GET /api/v1/auth/verify}.
     */
    public VerifyTokenResponse verify(String accessToken) {
        Claims claims;
        try {
            claims = jwtService.parseClaims(accessToken);
        } catch (JwtException | IllegalArgumentException e) {
            throw new PartnerAuthenticationException("Invalid or expired token");
        }

        if (!JwtService.TOKEN_TYPE_ACCESS.equals(claims.get(JwtService.CLAIM_TOKEN_TYPE, String.class))) {
            throw new PartnerAuthenticationException("Provided token is not an access token");
        }

        @SuppressWarnings("unchecked")
        List<String> scopes = claims.get(JwtService.CLAIM_SCOPES, List.class);

        return VerifyTokenResponse.builder()
                .partnerId(UUID.fromString(claims.get(JwtService.CLAIM_PARTNER_ID, String.class)))
                .partnerCode(claims.get(JwtService.CLAIM_PARTNER_CODE, String.class))
                .clientId(claims.get(JwtService.CLAIM_CLIENT_ID, String.class))
                .scopes(scopes == null ? List.of() : scopes)
                .issuedAt(claims.getIssuedAt().toInstant())
                .expiresAt(claims.getExpiration().toInstant())
                .build();
    }

    private TokenResponse buildTokenResponse(ApiClient client, Partner partner) {
        String accessToken = jwtService.generateAccessToken(client, partner);
        String refreshToken = jwtService.generateRefreshToken(client, partner);
        long expiresIn = client.getTokenExpirySeconds() > 0
                ? client.getTokenExpirySeconds()
                : jwtService.getAccessTokenTtlSeconds();

        return TokenResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(expiresIn)
                .refreshToken(refreshToken)
                .refreshExpiresIn(jwtService.getRefreshTokenTtlSeconds())
                .scopes(com.insurance.integration.security.util.ScopeUtils.parse(client.getScopes()))
                .build();
    }
}
