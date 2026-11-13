package com.insurance.integration.security.service;

import com.insurance.integration.common.exception.DuplicateResourceException;
import com.insurance.integration.common.exception.PartnerAuthenticationException;
import com.insurance.integration.common.exception.PartnerNotActiveException;
import com.insurance.integration.common.exception.ResourceNotFoundException;
import com.insurance.integration.common.exception.ValidationException;
import com.insurance.integration.partner.entity.Partner;
import com.insurance.integration.partner.repository.PartnerRepository;
import com.insurance.integration.security.dto.ApiClientResponse;
import com.insurance.integration.security.dto.CreateApiClientRequest;
import com.insurance.integration.security.dto.CreateApiClientResponse;
import com.insurance.integration.security.dto.RotateSecretResponse;
import com.insurance.integration.security.entity.ApiClient;
import com.insurance.integration.security.repository.ApiClientRepository;
import com.insurance.integration.security.util.ScopeUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Manages partner API clients: credential issuance, authentication, rotation
 * and activation.
 *
 * <p>Secrets are generated with {@link SecureRandom}, returned exactly once in
 * plaintext, and persisted only as BCrypt hashes (cost 12). The previous secret
 * remains valid for 24 hours after rotation.</p>
 */
@Service
@Slf4j
public class ClientCredentialsService {

    static final Duration ROTATION_GRACE_PERIOD = Duration.ofHours(24);

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ApiClientRepository apiClientRepository;
    private final PartnerRepository partnerRepository;
    private final PasswordEncoder passwordEncoder;
    private final int defaultTokenExpirySeconds;
    private final int defaultRateLimitPerMinute;

    public ClientCredentialsService(
            ApiClientRepository apiClientRepository,
            PartnerRepository partnerRepository,
            PasswordEncoder passwordEncoder,
            @Value("${app.security.jwt-expiry-seconds:3600}") int defaultTokenExpirySeconds,
            @Value("${app.rate-limit.default-per-minute:100}") int defaultRateLimitPerMinute) {
        this.apiClientRepository = apiClientRepository;
        this.partnerRepository = partnerRepository;
        this.passwordEncoder = passwordEncoder;
        this.defaultTokenExpirySeconds = defaultTokenExpirySeconds;
        this.defaultRateLimitPerMinute = defaultRateLimitPerMinute;
    }

    // ─── Admin operations ────────────────────────────────────────────────────

    @Transactional
    public CreateApiClientResponse createClient(UUID partnerId, CreateApiClientRequest request) {
        Partner partner = partnerRepository.findById(partnerId)
                .orElseThrow(() -> new ResourceNotFoundException("Partner", partnerId.toString()));

        List<String> scopes;
        try {
            scopes = ScopeUtils.normalize(request != null ? request.getScopes() : null);
        } catch (IllegalArgumentException e) {
            throw new ValidationException(e.getMessage());
        }

        String clientId = generateClientId();
        String clientSecret = generateSecret();

        ApiClient client = ApiClient.builder()
                .partnerId(partner.getId())
                .clientId(clientId)
                .clientSecretHash(passwordEncoder.encode(clientSecret))
                .scopes(ScopeUtils.toJson(scopes))
                .tokenExpirySeconds(request != null && request.getTokenExpirySeconds() != null
                        ? request.getTokenExpirySeconds() : defaultTokenExpirySeconds)
                .rateLimitPerMinute(request != null && request.getRateLimitPerMinute() != null
                        ? request.getRateLimitPerMinute() : defaultRateLimitPerMinute)
                .active(true)
                .build();

        client = apiClientRepository.save(client);

        log.info("API client created: clientId={}, partnerCode={}, scopes={}",
                client.getClientId(), partner.getPartnerCode(), scopes);

        return CreateApiClientResponse.builder()
                .id(client.getId())
                .partnerId(client.getPartnerId())
                .clientId(client.getClientId())
                .clientSecret(clientSecret)
                .scopes(scopes)
                .tokenExpirySeconds(client.getTokenExpirySeconds())
                .rateLimitPerMinute(client.getRateLimitPerMinute())
                .active(client.isActive())
                .createdAt(client.getCreatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public List<ApiClientResponse> listClients(UUID partnerId) {
        if (!partnerRepository.existsById(partnerId)) {
            throw new ResourceNotFoundException("Partner", partnerId.toString());
        }
        return apiClientRepository.findByPartnerIdOrderByCreatedAtDesc(partnerId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public RotateSecretResponse rotateSecret(UUID partnerId, String clientId) {
        ApiClient client = findClientForPartner(partnerId, clientId);

        String newSecret = generateSecret();
        Instant rotatedAt = Instant.now();

        client.setPreviousSecretHash(client.getClientSecretHash());
        client.setClientSecretHash(passwordEncoder.encode(newSecret));
        client.setLastRotatedAt(rotatedAt);
        apiClientRepository.save(client);

        log.info("API client secret rotated: clientId={}, previousSecretValidUntil={}",
                client.getClientId(), rotatedAt.plus(ROTATION_GRACE_PERIOD));

        return RotateSecretResponse.builder()
                .clientId(client.getClientId())
                .clientSecret(newSecret)
                .lastRotatedAt(rotatedAt)
                .previousSecretValidUntil(rotatedAt.plus(ROTATION_GRACE_PERIOD))
                .build();
    }

    @Transactional
    public ApiClientResponse setActive(UUID partnerId, String clientId, boolean active) {
        ApiClient client = findClientForPartner(partnerId, clientId);
        client.setActive(active);
        apiClientRepository.save(client);

        if (active) {
            log.info("API client activated: clientId={}", clientId);
        } else {
            log.warn("API client deactivated: clientId={}", clientId);
        }
        return toResponse(client);
    }

    // ─── Authentication ──────────────────────────────────────────────────────

    /**
     * Validates client credentials and returns the client together with its
     * partner. The previous secret is accepted while the 24-hour rotation
     * grace window is open.
     *
     * @throws PartnerAuthenticationException for unknown/inactive clients or a bad secret (401)
     * @throws PartnerNotActiveException      when the partner account is deactivated (403)
     */
    @Transactional(readOnly = true)
    public AuthenticationResult authenticate(String clientId, String clientSecret) {
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            throw new PartnerAuthenticationException("Invalid client credentials");
        }

        ApiClient client = apiClientRepository.findByClientId(clientId)
                .orElseThrow(() -> new PartnerAuthenticationException("Invalid client credentials"));

        if (!client.isActive()) {
            throw new PartnerAuthenticationException("Invalid client credentials");
        }

        boolean currentSecretMatches = passwordEncoder.matches(clientSecret, client.getClientSecretHash());
        boolean previousSecretMatches = isGracePeriodOpen(client)
                && client.getPreviousSecretHash() != null
                && passwordEncoder.matches(clientSecret, client.getPreviousSecretHash());

        if (!currentSecretMatches && !previousSecretMatches) {
            log.warn("SECURITY: failed client authentication for clientId={}", clientId);
            throw new PartnerAuthenticationException("Invalid client credentials");
        }

        Partner partner = partnerRepository.findById(client.getPartnerId())
                .orElseThrow(() -> new PartnerAuthenticationException("Invalid client credentials"));

        if (!partner.isActive()) {
            log.warn("SECURITY: inactive partner authentication attempt: partnerCode={}", partner.getPartnerCode());
            throw new PartnerNotActiveException(partner.getPartnerCode());
        }

        return new AuthenticationResult(client, partner);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private ApiClient findClientForPartner(UUID partnerId, String clientId) {
        return apiClientRepository.findByClientIdAndPartnerId(clientId, partnerId)
                .orElseThrow(() -> new ResourceNotFoundException("ApiClient", clientId));
    }

    private boolean isGracePeriodOpen(ApiClient client) {
        Instant rotatedAt = client.getLastRotatedAt();
        return rotatedAt != null && Instant.now().isBefore(rotatedAt.plus(ROTATION_GRACE_PERIOD));
    }

    private String generateClientId() {
        String candidate;
        do {
            candidate = "cli_" + randomToken(12);
        } while (apiClientRepository.existsByClientId(candidate));
        return candidate;
    }

    private static String generateSecret() {
        // 256 bits of entropy, URL-safe so it can be sent in headers/forms safely.
        return randomToken(32);
    }

    private static String randomToken(int byteCount) {
        byte[] bytes = new byte[byteCount];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private ApiClientResponse toResponse(ApiClient client) {
        return ApiClientResponse.builder()
                .id(client.getId())
                .partnerId(client.getPartnerId())
                .clientId(client.getClientId())
                .scopes(ScopeUtils.parse(client.getScopes()))
                .tokenExpirySeconds(client.getTokenExpirySeconds())
                .rateLimitPerMinute(client.getRateLimitPerMinute())
                .active(client.isActive())
                .lastRotatedAt(client.getLastRotatedAt())
                .createdAt(client.getCreatedAt())
                .updatedAt(client.getUpdatedAt())
                .build();
    }
}
