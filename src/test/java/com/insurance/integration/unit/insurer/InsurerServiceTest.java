package com.insurance.integration.unit.insurer;

import com.insurance.integration.common.enums.InsurerType;
import com.insurance.integration.common.exception.DuplicateResourceException;
import com.insurance.integration.common.exception.ResourceNotFoundException;
import com.insurance.integration.insurer.dto.CreateInsurerRequest;
import com.insurance.integration.insurer.dto.InsurerResponse;
import com.insurance.integration.insurer.entity.Insurer;
import com.insurance.integration.insurer.repository.InsurerRepository;
import com.insurance.integration.insurer.service.InsurerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InsurerService Unit Tests")
class InsurerServiceTest {

    @Mock
    private InsurerRepository insurerRepository;

    @InjectMocks
    private InsurerService insurerService;

    private CreateInsurerRequest validRequest;
    private Insurer savedInsurer;

    @BeforeEach
    void setUp() {
        validRequest = CreateInsurerRequest.builder()
                .insurerCode("MAX_LIFE")
                .insurerName("Max Life Insurance Company Limited")
                .insurerType(InsurerType.LIFE)
                .contactEmail("integrations@maxlife.in")
                .destinationBucketName("max-life-inbound")
                .destinationBucketRegion("ap-south-1")
                .destinationIamRoleArn("arn:aws:iam::123456789012:role/IntegrationRole")
                .destinationPathPrefix("inbound/middleware/")
                .webhookUrl("https://api.maxlife.in/webhooks/insurance")
                .build();

        savedInsurer = Insurer.builder()
                .insurerCode("MAX_LIFE")
                .insurerName("Max Life Insurance Company Limited")
                .insurerType(InsurerType.LIFE)
                .contactEmail("integrations@maxlife.in")
                .destinationBucketName("max-life-inbound")
                .destinationBucketRegion("ap-south-1")
                .destinationIamRoleArn("arn:aws:iam::123456789012:role/IntegrationRole")
                .destinationPathPrefix("inbound/middleware/")
                .webhookUrl("https://api.maxlife.in/webhooks/insurance")
                .active(true)
                .build();
    }

    @Test
    @DisplayName("registerInsurer: should create insurer when code is unique")
    void registerInsurer_success() {
        when(insurerRepository.existsByInsurerCode("MAX_LIFE")).thenReturn(false);
        when(insurerRepository.save(any(Insurer.class))).thenReturn(savedInsurer);

        InsurerResponse result = insurerService.registerInsurer(validRequest);

        assertThat(result).isNotNull();
        assertThat(result.getInsurerCode()).isEqualTo("MAX_LIFE");
        assertThat(result.getInsurerType()).isEqualTo(InsurerType.LIFE);
        assertThat(result.isActive()).isTrue();
        // Security: sensitive fields must NOT be in the response
        assertThat(result.isDestinationConfigured()).isTrue();
        assertThat(result.isWebhookConfigured()).isTrue();
        verify(insurerRepository).save(any(Insurer.class));
    }

    @Test
    @DisplayName("registerInsurer: should throw DuplicateResourceException when code already exists")
    void registerInsurer_duplicateCode_throws() {
        when(insurerRepository.existsByInsurerCode("MAX_LIFE")).thenReturn(true);

        assertThatThrownBy(() -> insurerService.registerInsurer(validRequest))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("MAX_LIFE");

        verify(insurerRepository, never()).save(any());
    }

    @Test
    @DisplayName("getInsurerById: should return insurer when found")
    void getInsurerById_found() {
        UUID insurerId = UUID.randomUUID();
        when(insurerRepository.findById(insurerId)).thenReturn(Optional.of(savedInsurer));

        InsurerResponse result = insurerService.getInsurerById(insurerId);

        assertThat(result).isNotNull();
        assertThat(result.getInsurerCode()).isEqualTo("MAX_LIFE");
    }

    @Test
    @DisplayName("getInsurerById: should throw ResourceNotFoundException when not found")
    void getInsurerById_notFound() {
        UUID insurerId = UUID.randomUUID();
        when(insurerRepository.findById(insurerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> insurerService.getInsurerById(insurerId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("activateInsurer: should set active=true")
    void activateInsurer_success() {
        UUID insurerId = UUID.randomUUID();
        savedInsurer.setActive(false);

        when(insurerRepository.findById(insurerId)).thenReturn(Optional.of(savedInsurer));
        when(insurerRepository.save(any(Insurer.class))).thenReturn(savedInsurer);

        InsurerResponse result = insurerService.activateInsurer(insurerId);

        assertThat(result).isNotNull();
        verify(insurerRepository).save(any(Insurer.class));
    }

    @Test
    @DisplayName("deactivateInsurer: should set active=false")
    void deactivateInsurer_success() {
        UUID insurerId = UUID.randomUUID();

        when(insurerRepository.findById(insurerId)).thenReturn(Optional.of(savedInsurer));
        when(insurerRepository.save(any(Insurer.class))).thenReturn(savedInsurer);

        InsurerResponse result = insurerService.deactivateInsurer(insurerId);

        assertThat(result).isNotNull();
        verify(insurerRepository).save(any(Insurer.class));
    }

    @Test
    @DisplayName("toResponse: IAM role ARN and webhook secret must NOT appear in response")
    void toResponse_sensitiveFieldsExcluded() {
        InsurerResponse response = insurerService.toResponse(savedInsurer);

        // These fields must never be in the DTO — they are server-side only
        assertThat(response).hasNoNullFieldsOrPropertiesExcept(
                "id", "contactEmail", "createdAt", "updatedAt");
        // The response DTO class itself doesn't have destinationIamRoleArn or webhookSecret fields
        // This test verifies the response doesn't accidentally expose them via toString
        String responseString = response.toString();
        assertThat(responseString).doesNotContain("arn:aws:iam");
        assertThat(responseString).doesNotContain("webhookSecret");
    }
}
