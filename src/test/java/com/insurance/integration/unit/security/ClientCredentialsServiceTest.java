package com.insurance.integration.unit.security;

import com.insurance.integration.common.exception.PartnerAuthenticationException;
import com.insurance.integration.common.exception.PartnerNotActiveException;
import com.insurance.integration.partner.entity.Partner;
import com.insurance.integration.partner.repository.PartnerRepository;
import com.insurance.integration.security.dto.CreateApiClientRequest;
import com.insurance.integration.security.dto.CreateApiClientResponse;
import com.insurance.integration.security.dto.RotateSecretResponse;
import com.insurance.integration.security.entity.ApiClient;
import com.insurance.integration.security.repository.ApiClientRepository;
import com.insurance.integration.security.service.ClientCredentialsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("Module 2 — ClientCredentialsService")
class ClientCredentialsServiceTest {

    @Mock
    private ApiClientRepository apiClientRepository;

    @Mock
    private PartnerRepository partnerRepository;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);

    private ClientCredentialsService service;
    private Partner partner;

    @BeforeEach
    void setUp() {
        service = new ClientCredentialsService(
                apiClientRepository, partnerRepository, passwordEncoder, 3600, 100);

        partner = Partner.builder()
                .partnerCode("AXIS_BANK")
                .partnerName("Axis Bank")
                .active(true)
                .build();
        ReflectionTestUtils.setField(partner, "id", UUID.randomUUID());
    }

    @Test
    @DisplayName("createClient persists only a BCrypt hash and returns the secret once")
    void createClientHashesSecret() {
        when(partnerRepository.findById(partner.getId())).thenReturn(Optional.of(partner));
        when(apiClientRepository.existsByClientId(anyString())).thenReturn(false);
        when(apiClientRepository.save(any(ApiClient.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateApiClientResponse response = service.createClient(
                partner.getId(), CreateApiClientRequest.builder().scopes(List.of("read", "write")).build());

        assertThat(response.getClientId()).startsWith("cli_");
        assertThat(response.getClientSecret()).isNotBlank();
        assertThat(response.getScopes()).containsExactly("read", "write");
        assertThat(response.getTokenExpirySeconds()).isEqualTo(3600);
        assertThat(response.getRateLimitPerMinute()).isEqualTo(100);

        ArgumentCaptor<ApiClient> captor = ArgumentCaptor.forClass(ApiClient.class);
        verify(apiClientRepository).save(captor.capture());
        ApiClient saved = captor.getValue();

        assertThat(saved.getClientSecretHash()).isNotEqualTo(response.getClientSecret());
        assertThat(saved.getClientSecretHash()).startsWith("$2");
        assertThat(passwordEncoder.matches(response.getClientSecret(), saved.getClientSecretHash())).isTrue();
        assertThat(saved.getScopes()).isEqualTo("[\"read\",\"write\"]");
    }

    @Test
    @DisplayName("default scope is read when none requested")
    void defaultScopeIsRead() {
        when(partnerRepository.findById(partner.getId())).thenReturn(Optional.of(partner));
        when(apiClientRepository.existsByClientId(anyString())).thenReturn(false);
        when(apiClientRepository.save(any(ApiClient.class))).thenAnswer(inv -> inv.getArgument(0));

        CreateApiClientResponse response = service.createClient(partner.getId(), null);

        assertThat(response.getScopes()).containsExactly("read");
    }

    @Test
    @DisplayName("authenticate accepts the current secret")
    void authenticateAcceptsCurrentSecret() {
        String secret = "correct-secret";
        ApiClient client = activeClient(secret);
        when(apiClientRepository.findByClientId(client.getClientId())).thenReturn(Optional.of(client));
        when(partnerRepository.findById(partner.getId())).thenReturn(Optional.of(partner));

        var result = service.authenticate(client.getClientId(), secret);

        assertThat(result.client().getClientId()).isEqualTo(client.getClientId());
        assertThat(result.partner().getPartnerCode()).isEqualTo("AXIS_BANK");
    }

    @Test
    @DisplayName("authenticate rejects a wrong secret with 401 semantics")
    void authenticateRejectsWrongSecret() {
        ApiClient client = activeClient("correct-secret");
        when(apiClientRepository.findByClientId(client.getClientId())).thenReturn(Optional.of(client));

        assertThatThrownBy(() -> service.authenticate(client.getClientId(), "wrong-secret"))
                .isInstanceOf(PartnerAuthenticationException.class);
    }

    @Test
    @DisplayName("authenticate rejects unknown client ids with 401 semantics")
    void authenticateRejectsUnknownClient() {
        when(apiClientRepository.findByClientId("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.authenticate("missing", "secret"))
                .isInstanceOf(PartnerAuthenticationException.class);
    }

    @Test
    @DisplayName("authenticate rejects an inactive client")
    void authenticateRejectsInactiveClient() {
        ApiClient client = activeClient("correct-secret");
        client.setActive(false);
        when(apiClientRepository.findByClientId(client.getClientId())).thenReturn(Optional.of(client));

        assertThatThrownBy(() -> service.authenticate(client.getClientId(), "correct-secret"))
                .isInstanceOf(PartnerAuthenticationException.class);
    }

    @Test
    @DisplayName("authenticate rejects an inactive partner with 403 semantics")
    void authenticateRejectsInactivePartner() {
        ApiClient client = activeClient("correct-secret");
        partner.setActive(false);
        when(apiClientRepository.findByClientId(client.getClientId())).thenReturn(Optional.of(client));
        when(partnerRepository.findById(partner.getId())).thenReturn(Optional.of(partner));

        assertThatThrownBy(() -> service.authenticate(client.getClientId(), "correct-secret"))
                .isInstanceOf(PartnerNotActiveException.class);
    }

    @Test
    @DisplayName("rotation keeps the previous secret valid inside the 24h grace window")
    void rotationGraceWindowAcceptsOldSecret() {
        ApiClient client = activeClient("old-secret");
        when(apiClientRepository.findByClientIdAndPartnerId(client.getClientId(), partner.getId()))
                .thenReturn(Optional.of(client));
        when(apiClientRepository.findByClientId(client.getClientId())).thenReturn(Optional.of(client));
        when(partnerRepository.findById(partner.getId())).thenReturn(Optional.of(partner));
        when(apiClientRepository.save(any(ApiClient.class))).thenAnswer(inv -> inv.getArgument(0));

        RotateSecretResponse rotated = service.rotateSecret(partner.getId(), client.getClientId());

        assertThat(rotated.getClientSecret()).isNotBlank();
        assertThat(client.getPreviousSecretHash()).isNotNull();
        assertThat(client.getLastRotatedAt()).isNotNull();

        // Both old and new secrets authenticate while the window is open.
        assertThat(service.authenticate(client.getClientId(), "old-secret")).isNotNull();
        assertThat(service.authenticate(client.getClientId(), rotated.getClientSecret())).isNotNull();
    }

    @Test
    @DisplayName("rotation rejects the previous secret after 24 hours")
    void rotationGraceWindowExpires() {
        ApiClient client = activeClient("new-secret");
        client.setPreviousSecretHash(passwordEncoder.encode("old-secret"));
        client.setLastRotatedAt(Instant.now().minusSeconds(25 * 3600));
        when(apiClientRepository.findByClientId(client.getClientId())).thenReturn(Optional.of(client));

        assertThatThrownBy(() -> service.authenticate(client.getClientId(), "old-secret"))
                .isInstanceOf(PartnerAuthenticationException.class);
    }

    private ApiClient activeClient(String secret) {
        ApiClient client = ApiClient.builder()
                .partnerId(partner.getId())
                .clientId("cli_" + UUID.randomUUID())
                .clientSecretHash(passwordEncoder.encode(secret))
                .scopes("[\"read\"]")
                .tokenExpirySeconds(3600)
                .rateLimitPerMinute(100)
                .active(true)
                .build();
        ReflectionTestUtils.setField(client, "id", UUID.randomUUID());
        return client;
    }
}
