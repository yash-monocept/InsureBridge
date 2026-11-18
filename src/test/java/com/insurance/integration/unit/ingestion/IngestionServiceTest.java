package com.insurance.integration.unit.ingestion;

import com.insurance.integration.common.enums.DocumentStatus;
import com.insurance.integration.common.enums.IngestionStatus;
import com.insurance.integration.common.exception.DuplicateResourceException;
import com.insurance.integration.common.exception.ResourceNotFoundException;
import org.springframework.security.access.AccessDeniedException;
import com.insurance.integration.ingestion.dto.CustomerDTO;
import com.insurance.integration.ingestion.dto.DocumentMetadataDTO;
import com.insurance.integration.ingestion.dto.DocumentSubmissionDTO;
import com.insurance.integration.ingestion.dto.IngestionRequestDTO;
import com.insurance.integration.ingestion.dto.IngestionResponseDTO;
import com.insurance.integration.ingestion.entity.IngestionDocument;
import com.insurance.integration.ingestion.entity.IngestionRequest;
import com.insurance.integration.ingestion.repository.IngestionDocumentRepository;
import com.insurance.integration.ingestion.repository.IngestionRequestRepository;
import com.insurance.integration.ingestion.service.IngestionService;
import com.insurance.integration.insurer.entity.Insurer;
import com.insurance.integration.insurer.repository.InsurerRepository;
import com.insurance.integration.partner.entity.Partner;
import com.insurance.integration.partner.entity.PartnerInsurerMapping;
import com.insurance.integration.partner.repository.PartnerInsurerMappingRepository;
import com.insurance.integration.partner.repository.PartnerRepository;
import com.insurance.integration.schema.dto.ValidationResultDTO;
import com.insurance.integration.schema.service.SchemaService;
import com.insurance.integration.schema.service.ValidationService;
import com.insurance.integration.security.principal.PartnerPrincipal;
import com.insurance.integration.security.validator.SsrfValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the Module 3 {@link IngestionService}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("IngestionService Unit Tests")
class IngestionServiceTest {

    @Mock
    private IngestionRequestRepository requestRepository;

    @Mock
    private IngestionDocumentRepository documentRepository;

    @Mock
    private PartnerRepository partnerRepository;

    @Mock
    private InsurerRepository insurerRepository;

    @Mock
    private PartnerInsurerMappingRepository mappingRepository;

    @Mock
    private SchemaService schemaService;

    @Mock
    private ValidationService validationService;

    @Mock
    private SsrfValidator ssrfValidator;

    @InjectMocks
    private IngestionService ingestionService;

    private static UUID randomId() {
        return UUID.randomUUID();
    }

    private Partner activePartner() {
        return Partner.builder()
                .partnerCode("AXIS_BANK")
                .partnerName("Axis Bank Limited")
                .active(true)
                .approvedSourceHosts("[\"*.s3.amazonaws.com\"]")
                .build();
    }

    private Partner inactivePartner() {
        return Partner.builder()
                .partnerCode("INACTIVE_BANK")
                .partnerName("Inactive Bank")
                .active(false)
                .build();
    }

    private Insurer activeInsurer() {
        return Insurer.builder()
                .insurerCode("MAX_LIFE")
                .insurerName("Max Life Insurance")
                .active(true)
                .build();
    }

    private PartnerPrincipal principal(UUID partnerId) {
        return new PartnerPrincipal(partnerId, "AXIS_BANK", "cli_abc",
                List.of("read", "write"), 100, Instant.now().plusSeconds(3600));
    }

    private IngestionRequestDTO validDto(UUID partnerId) {
        CustomerDTO customer = CustomerDTO.builder()
                .name("Rahul Sharma").mobile("9999999999").email("rahul@example.com").build();
        DocumentSubmissionDTO doc = DocumentSubmissionDTO.builder()
                .documentType("IDENTITY_PROOF").fileName("identity.pdf")
                .contentType("application/pdf")
                .sourceUrl("https://bucket.s3.amazonaws.com/identity.pdf").build();
        return IngestionRequestDTO.builder()
                .partnerReferenceId("CAR-92831")
                .insurerCode("MAX_LIFE")
                .productCode("MOTOR_INSURANCE")
                .schemaVersion("1.0")
                .idempotencyKey("idem_123")
                .customer(customer)
                .applicationData(com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode())
                .documents(List.of(doc)).build();
    }

    private void mockHappyPath(UUID partnerId, UUID insurerId) {
        Partner partner = activePartner();
        Insurer insurer = activeInsurer();
        when(partnerRepository.findById(partnerId)).thenReturn(Optional.of(partner));
        when(insurerRepository.findByInsurerCode("MAX_LIFE")).thenReturn(Optional.of(insurer));
        PartnerInsurerMapping mapping = PartnerInsurerMapping.builder()
                .partner(partner).insurer(insurer).active(true).build();
        when(mappingRepository.findActiveMapping(any(), any())).thenReturn(Optional.of(mapping));
        when(requestRepository.existsByIdempotencyKey("idem_123")).thenReturn(false);
        com.insurance.integration.schema.entity.ProductSchema schema =
                com.insurance.integration.schema.entity.ProductSchema.builder()
                        .productType("MOTOR_INSURANCE").schemaVersion("1.0").build();
        when(schemaService.selectSchema(any(), any(), anyString(), anyString())).thenReturn(schema);
        when(ssrfValidator.isAllowed(anyString(), anyString())).thenReturn(true);
        IngestionRequest saved = IngestionRequest.builder()
                .partnerId(partnerId).insurerId(insurerId)
                .productType("MOTOR_INSURANCE").status(IngestionStatus.RECEIVED)
                .idempotencyKey("idem_123").receivedAt(Instant.now()).build();
        when(requestRepository.save(any(IngestionRequest.class))).thenReturn(saved);
        when(documentRepository.saveAll(any())).thenReturn(List.of());
        ValidationResultDTO validation = ValidationResultDTO.builder()
                .submissionId(saved.getId()).schemaVersion("1.0").validationStatus("VALIDATED")
                .totalChecks(0).passedChecks(0).failedChecks(0).errors(List.of()).build();
        when(validationService.validateSubmission(isNull())).thenReturn(validation);
        when(requestRepository.findById(isNull())).thenReturn(Optional.of(
                IngestionRequest.builder().partnerId(partnerId)
                        .insurerId(insurerId).externalReference("CAR-92831")
                        .idempotencyKey("idem_123").status(IngestionStatus.VALIDATED)
                        .receivedAt(Instant.now()).build()));
        when(validationService.listDocumentMetadata(isNull())).thenReturn(List.of());
    }

    // ─── happy path ─────────────────────────────────────────────────────

    @Test
    @DisplayName("valid submission is persisted and validation is invoked")
    void validSubmission_persistedAndValidated() {
        UUID partnerId = randomId();
        UUID insurerId = randomId();
        mockHappyPath(partnerId, insurerId);

        IngestionResponseDTO response = ingestionService.submit(
                validDto(partnerId), principal(partnerId), "127.0.0.1", "test-agent");

        assertThat(response.getStatus()).isEqualTo("VALIDATED");
        assertThat(response.getExternalReference()).isEqualTo("CAR-92831");
        assertThat(response.getIdempotencyKey()).isEqualTo("idem_123");
        verify(requestRepository).save(any(IngestionRequest.class));
        verify(documentRepository).saveAll(any());
        verify(validationService).validateSubmission(any());
    }

    // ─── idempotency ────────────────────────────────────────────────────

    @Test
    @DisplayName("duplicate idempotency key returns existing submission without creating new")
    void duplicateIdempotencyKey_returnsExisting() {
        UUID partnerId = randomId();
        UUID insurerId = randomId();
        Partner partner = activePartner();
        Insurer insurer = activeInsurer();
        when(partnerRepository.findById(partnerId)).thenReturn(Optional.of(partner));
        when(insurerRepository.findByInsurerCode("MAX_LIFE")).thenReturn(Optional.of(insurer));
        PartnerInsurerMapping mapping = PartnerInsurerMapping.builder()
                .partner(partner).insurer(insurer).active(true).build();
        when(mappingRepository.findActiveMapping(any(), any())).thenReturn(Optional.of(mapping));
        when(requestRepository.existsByIdempotencyKey("idem_123")).thenReturn(true);
        IngestionRequest existing = IngestionRequest.builder()
                .status(IngestionStatus.RECEIVED).idempotencyKey("idem_123").build();
        when(requestRepository.findByIdempotencyKey("idem_123")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> ingestionService.submit(
                validDto(partnerId), principal(partnerId), "127.0.0.1", "test-agent"))
                .isInstanceOf(DuplicateResourceException.class);
        verify(requestRepository, never()).save(any(IngestionRequest.class));
    }

    // ─── authorisation ──────────────────────────────────────────────────

    @Test
    @DisplayName("inactive partner is rejected")
    void inactivePartner_rejected() {
        UUID partnerId = randomId();
        when(partnerRepository.findById(partnerId)).thenReturn(Optional.of(inactivePartner()));

        assertThatThrownBy(() -> ingestionService.submit(
                validDto(partnerId), principal(partnerId), "127.0.0.1", "test-agent"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("unknown insurer is rejected")
    void unknownInsurer_rejected() {
        UUID partnerId = randomId();
        Partner partner = activePartner();
        when(partnerRepository.findById(partnerId)).thenReturn(Optional.of(partner));
        when(insurerRepository.findByInsurerCode("MAX_LIFE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ingestionService.submit(
                validDto(partnerId), principal(partnerId), "127.0.0.1", "test-agent"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("unmapped insurer is rejected")
    void unmappedInsurer_rejected() {
        UUID partnerId = randomId();
        UUID insurerId = randomId();
        Partner partner = activePartner();
        Insurer insurer = activeInsurer();
        when(partnerRepository.findById(partnerId)).thenReturn(Optional.of(partner));
        when(insurerRepository.findByInsurerCode("MAX_LIFE")).thenReturn(Optional.of(insurer));
        when(mappingRepository.findActiveMapping(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> ingestionService.submit(
                validDto(partnerId), principal(partnerId), "127.0.0.1", "test-agent"))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("product not permitted by mapping is rejected")
    void productNotPermitted_rejected() {
        UUID partnerId = randomId();
        UUID insurerId = randomId();
        mockHappyPath(partnerId, insurerId);
        // Override mapping to restrict product types
        Partner partner = activePartner();
        Insurer insurer = activeInsurer();
        PartnerInsurerMapping mapping = PartnerInsurerMapping.builder()
                .partner(partner).insurer(insurer).active(true)
                .permittedProductTypes("[\"HEALTH_INSURANCE\"]").build();
        when(mappingRepository.findActiveMapping(any(), any())).thenReturn(Optional.of(mapping));

        assertThatThrownBy(() -> ingestionService.submit(
                validDto(partnerId), principal(partnerId), "127.0.0.1", "test-agent"))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ─── SSRF ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("invalid source URL is rejected by SSRF validator")
    void invalidSourceUrl_rejected() {
        UUID partnerId = randomId();
        UUID insurerId = randomId();
        Partner partner = activePartner();
        Insurer insurer = activeInsurer();
        when(partnerRepository.findById(partnerId)).thenReturn(Optional.of(partner));
        when(insurerRepository.findByInsurerCode("MAX_LIFE")).thenReturn(Optional.of(insurer));
        PartnerInsurerMapping mapping = PartnerInsurerMapping.builder()
                .partner(partner).insurer(insurer).active(true).build();
        when(mappingRepository.findActiveMapping(any(), any())).thenReturn(Optional.of(mapping));
        when(requestRepository.existsByIdempotencyKey("idem_123")).thenReturn(false);
        com.insurance.integration.schema.entity.ProductSchema schema =
                com.insurance.integration.schema.entity.ProductSchema.builder()
                        .productType("MOTOR_INSURANCE").schemaVersion("1.0").build();
        when(schemaService.selectSchema(any(), any(), anyString(), anyString())).thenReturn(schema);
        when(ssrfValidator.isAllowed(anyString(), anyString())).thenReturn(false);

        IngestionRequestDTO dto = validDto(partnerId);
        // Override document URL to trigger SSRF
        dto.getDocuments().get(0).setSourceUrl("http://169.254.169.254/metadata");

        assertThatThrownBy(() -> ingestionService.submit(
                dto, principal(partnerId), "127.0.0.1", "test-agent"))
                .isInstanceOf(RuntimeException.class);
    }

}
