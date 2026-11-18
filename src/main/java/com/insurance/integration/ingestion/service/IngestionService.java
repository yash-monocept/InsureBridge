package com.insurance.integration.ingestion.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.insurance.integration.common.enums.DocumentStatus;
import com.insurance.integration.common.enums.IngestionStatus;
import com.insurance.integration.common.exception.DuplicateResourceException;
import com.insurance.integration.common.exception.ResourceNotFoundException;
import com.insurance.integration.ingestion.dto.CustomerDetailDTO;
import com.insurance.integration.ingestion.dto.DocumentMetadataDTO;
import com.insurance.integration.ingestion.dto.IngestionRequestDTO;
import com.insurance.integration.ingestion.dto.IngestionResponseDTO;
import com.insurance.integration.ingestion.dto.SubmissionDetailDTO;
import com.insurance.integration.ingestion.dto.SubmissionSummaryDTO;
import com.insurance.integration.ingestion.entity.IngestionDocument;
import com.insurance.integration.ingestion.entity.IngestionRequest;
import com.insurance.integration.ingestion.repository.IngestionDocumentRepository;
import com.insurance.integration.ingestion.repository.IngestionRequestRepository;
import com.insurance.integration.insurer.entity.Insurer;
import com.insurance.integration.insurer.repository.InsurerRepository;
import com.insurance.integration.partner.entity.Partner;
import com.insurance.integration.partner.entity.PartnerInsurerMapping;
import com.insurance.integration.partner.repository.PartnerInsurerMappingRepository;
import com.insurance.integration.partner.repository.PartnerRepository;
import com.insurance.integration.schema.dto.ValidationResultDTO;
import com.insurance.integration.schema.entity.ProductSchema;
import com.insurance.integration.schema.service.SchemaService;
import com.insurance.integration.schema.service.ValidationService;
import com.insurance.integration.security.principal.PartnerPrincipal;
import com.insurance.integration.security.validator.SsrfValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for request ingestion (Module 3).
 *
 * <p>Receives insurance application submissions from authenticated partners, enforces
 * partner/insurer/product authorisation, checks idempotency, validates pre-signed document
 * URLs, persists the submission and document metadata, and invokes schema validation.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IngestionService {

    private final IngestionRequestRepository requestRepository;
    private final IngestionDocumentRepository documentRepository;
    private final PartnerRepository partnerRepository;
    private final InsurerRepository insurerRepository;
    private final PartnerInsurerMappingRepository mappingRepository;
    private final SchemaService schemaService;
    private final ValidationService validationService;
    private final SsrfValidator ssrfValidator;
    private final ObjectMapper objectMapper;

    /**
     * Submit a new insurance application from an authenticated partner.
     */
    @Transactional
    public IngestionResponseDTO submit(IngestionRequestDTO dto, PartnerPrincipal principal,
                                       String ipAddress, String userAgent) {

        UUID partnerId = principal.partnerId();

        // 1. Partner must exist and be active (re-checked per request).
        Partner partner = partnerRepository.findById(partnerId)
                .orElseThrow(() -> new ResourceNotFoundException("Partner", partnerId.toString()));
        if (!partner.isActive()) {
            throw new IllegalStateException("Partner is not active: " + partner.getPartnerCode());
        }

        // 2. Resolve and authorise the insurer.
        Insurer insurer = insurerRepository.findByInsurerCode(dto.getInsurerCode())
                .orElseThrow(() -> new ResourceNotFoundException("Insurer", dto.getInsurerCode()));
        if (!insurer.isActive()) {
            throw new IllegalStateException("Insurer is not active: " + insurer.getInsurerCode());
        }

        // 3. Partner must be mapped to the insurer and permitted for the product.
        PartnerInsurerMapping mapping = mappingRepository.findActiveMapping(partnerId, insurer.getId())
                .orElseThrow(() -> new AccessDeniedException(
                        "Partner is not authorised to submit to insurer " + insurer.getInsurerCode()));
        if (!isProductPermitted(mapping, dto.getProductCode())) {
            throw new AccessDeniedException("Product not permitted for this partner-insurer mapping: "
                    + dto.getProductCode());
        }

        // 4. Idempotency.
        String idempotencyKey = dto.getIdempotencyKey() != null && !dto.getIdempotencyKey().isBlank()
                ? dto.getIdempotencyKey()
                : deriveIdempotencyKey(partnerId, dto);
        if (requestRepository.existsByIdempotencyKey(idempotencyKey)) {
            IngestionRequest existing = requestRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow();
            throw new DuplicateResourceException("Submission", "idempotencyKey", idempotencyKey);
        }

        // 5. Schema selection for validation.
        ProductSchema schema = schemaService.selectSchema(partnerId, insurer.getId(),
                dto.getProductCode(), dto.getSchemaVersion());
        if (schema == null) {
            throw new IllegalStateException("No active schema configured for product "
                    + dto.getProductCode());
        }

        // 6. Validate each document source URL (SSRF: HTTPS only, approved hosts).
        String approvedHosts = partner.getApprovedSourceHosts();
        for (var doc : dto.getDocuments()) {
            ssrfValidator.validate(doc.getSourceUrl(), approvedHosts);
        }

        // 7. Persist the ingestion request.
        IngestionRequest request = IngestionRequest.builder()
                .externalReference(dto.getPartnerReferenceId())
                .idempotencyKey(idempotencyKey)
                .partnerId(partnerId)
                .insurerId(insurer.getId())
                .productType(dto.getProductCode())
                .schemaId(schema.getId())
                .status(IngestionStatus.RECEIVED)
                .customerData(serialize(dto.getCustomer()))
                .productData(serialize(dto.getApplicationData()))
                .metadata(null)
                .validationErrorCount(0)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .receivedAt(Instant.now())
                .build();
        final IngestionRequest savedRequest = requestRepository.save(request);

        // 8. Persist document metadata references (URLs stored but never exposed).
        List<IngestionDocument> documents = dto.getDocuments().stream()
                .map(doc -> toDocument(savedRequest, doc))
                .collect(Collectors.toList());
        documentRepository.saveAll(documents);

        // 9. Invoke schema validation (Module 4).
        ValidationResultDTO validation = validationService.validateSubmission(request.getId());

        // Re-fetch so the response reflects the persisted status (VALIDATED / VALIDATION_FAILED).
        request = requestRepository.findById(request.getId()).orElseThrow();

        log.info("Submission ingested: id={}, partner={}, insurer={}, product={}, status={}",
                request.getId(), partner.getPartnerCode(), insurer.getInsurerCode(),
                dto.getProductCode(), request.getStatus());

        return buildResponse(request, validation);
    }

    /**
     * List submissions belonging to a partner (partner-scoped).
     */
    @Transactional(readOnly = true)
    public Page<SubmissionSummaryDTO> listPartnerSubmissions(UUID partnerId, UUID insurerId,
                                                             IngestionStatus status, String productType,
                                                             String search, Pageable pageable) {
        return requestRepository.findWithFilters(partnerId, insurerId, status, productType, search, pageable)
                .map(this::toSummary);
    }

    /**
     * Get a single submission belonging to a partner (partner-scoped).
     */
    @Transactional(readOnly = true)
    public SubmissionDetailDTO getPartnerSubmission(UUID partnerId, UUID submissionId) {
        IngestionRequest request = requestRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission", submissionId.toString()));
        if (!request.getPartnerId().equals(partnerId)) {
            throw new ResourceNotFoundException("Submission", submissionId.toString());
        }
        return toDetail(request);
    }

    /**
     * Get the validation result for a submission belonging to a partner (partner-scoped).
     */
    @Transactional(readOnly = true)
    public ValidationResultDTO getPartnerSubmissionValidation(UUID partnerId, UUID submissionId) {
        getPartnerSubmission(partnerId, submissionId); // enforces ownership
        return validationService.getValidationResult(submissionId);
    }

    /**
     * List all submissions (admin).
     */
    @Transactional(readOnly = true)
    public Page<SubmissionSummaryDTO> listAllSubmissions(UUID insurerId, IngestionStatus status,
                                                         String productType, String search, Pageable pageable) {
        return requestRepository.findWithFilters(null, insurerId, status, productType, search, pageable)
                .map(this::toSummary);
    }

    /**
     * Get a single submission (admin).
     */
    @Transactional(readOnly = true)
    public SubmissionDetailDTO getAdminSubmission(UUID submissionId) {
        IngestionRequest request = requestRepository.findById(submissionId)
                .orElseThrow(() -> new ResourceNotFoundException("Submission", submissionId.toString()));
        return toDetail(request);
    }

    /**
     * Get the validation result for a submission (admin).
     */
    @Transactional(readOnly = true)
    public ValidationResultDTO getAdminSubmissionValidation(UUID submissionId) {
        return validationService.getValidationResult(submissionId);
    }

    // ─── helpers ──────────────────────────────────────────────────────────

    private boolean isProductPermitted(PartnerInsurerMapping mapping, String productCode) {
        String types = mapping.getPermittedProductTypes();
        if (types == null || types.isBlank()) {
            return true; // no restriction -> all product types permitted
        }
        try {
            java.util.List<String> list = objectMapper.readValue(types, java.util.List.class);
            return list.contains(productCode);
        } catch (Exception e) {
            return types.contains(productCode);
        }
    }

    private String deriveIdempotencyKey(UUID partnerId, IngestionRequestDTO dto) {
        String raw = partnerId + ":" + dto.getPartnerReferenceId() + ":" +
                dto.getProductCode() + ":" + dto.getInsurerCode();
        return sha256(raw);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(64);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private IngestionDocument toDocument(IngestionRequest request,
                                         com.insurance.integration.ingestion.dto.DocumentSubmissionDTO doc) {
        String urlHash = sha256(doc.getSourceUrl());
        String[] bucketKey = parseBucketAndKey(doc.getSourceUrl());
        return IngestionDocument.builder()
                .ingestionRequest(request)
                .ingestionRequestId(request.getId())
                .documentType(doc.getDocumentType())
                .sourceS3Url(doc.getSourceUrl())
                .sourceUrlHash(urlHash)
                .sourceBucket(bucketKey[0])
                .sourceKey(bucketKey[1])
                .originalFilename(doc.getFileName())
                .declaredMimeType(doc.getContentType())
                .declaredSizeBytes(null)
                .documentStatus(DocumentStatus.PENDING)
                .build();
    }

    private String[] parseBucketAndKey(String url) {
        // Best-effort extraction: https://bucket.s3.region.amazonaws.com/key
        String bucket = null;
        String key = null;
        try {
            java.net.URI uri = java.net.URI.create(url);
            String path = uri.getPath();
            if (path != null && path.startsWith("/")) {
                key = path.substring(1);
            }
            String host = uri.getHost();
            if (host != null && host.contains(".s3.")) {
                bucket = host.substring(0, host.indexOf(".s3."));
            }
        } catch (Exception ignored) {
        }
        return new String[]{bucket, key};
    }

    private String serialize(Object value) {
        try {
            return value == null ? "{}" : objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private IngestionResponseDTO buildResponse(IngestionRequest request,
                                               ValidationResultDTO validation) {
        return IngestionResponseDTO.builder()
                .submissionId(request.getId())
                .externalReference(request.getExternalReference())
                .idempotencyKey(request.getIdempotencyKey())
                .status(request.getStatus().name())
                .partnerId(request.getPartnerId())
                .insurerCode(lookupInsurerCode(request.getInsurerId()))
                .productCode(request.getProductType())
                .schemaVersion(request.getSchemaId() != null ? lookupSchemaVersion(request.getSchemaId()) : null)
                .validationStatus(validation != null ? validation.getValidationStatus() : null)
                .documentCount(countDocuments(request.getId()))
                .receivedAt(request.getReceivedAt())
                .message("Submission received and validated.")
                .documents(validationService.listDocumentMetadata(request.getId()))
                .build();
    }

    private String lookupInsurerCode(UUID insurerId) {
        return insurerRepository.findById(insurerId)
                .map(Insurer::getInsurerCode).orElse(null);
    }

    private String lookupSchemaVersion(UUID schemaId) {
        if (schemaId == null) {
            return null;
        }
        return schemaService.getSchema(schemaId).getSchemaVersion();
    }

    private int countDocuments(UUID requestId) {
        return documentRepository.findByIngestionRequestId(requestId).size();
    }

    private SubmissionSummaryDTO toSummary(IngestionRequest request) {
        return SubmissionSummaryDTO.builder()
                .submissionId(request.getId())
                .externalReference(request.getExternalReference())
                .partnerName(lookupPartnerName(request.getPartnerId()))
                .insurerCode(lookupInsurerCode(request.getInsurerId()))
                .productCode(request.getProductType())
                .receivedAt(request.getReceivedAt())
                .validationStatus(request.getStatus().name())
                .processingStatus(request.getStatus().name())
                .validationErrorCount(request.getValidationErrorCount())
                .documentCount(countDocuments(request.getId()))
                .build();
    }

    private SubmissionDetailDTO toDetail(IngestionRequest request) {
        ValidationResultDTO validation = null;
        if (request.getValidationResult() != null) {
            try {
                validation = objectMapper.readValue(request.getValidationResult(), ValidationResultDTO.class);
            } catch (Exception ignored) {
            }
        }
        return SubmissionDetailDTO.builder()
                .submissionId(request.getId())
                .externalReference(request.getExternalReference())
                .idempotencyKey(request.getIdempotencyKey())
                .status(request.getStatus().name())
                .validationStatus(request.getStatus().name())
                .partnerId(request.getPartnerId())
                .partnerCode(lookupPartnerCode(request.getPartnerId()))
                .partnerName(lookupPartnerName(request.getPartnerId()))
                .insurerId(request.getInsurerId())
                .insurerCode(lookupInsurerCode(request.getInsurerId()))
                .insurerName(lookupInsurerName(request.getInsurerId()))
                .productCode(request.getProductType())
                .schemaVersion(lookupSchemaVersion(request.getSchemaId()))
                .customer(toCustomerDetail(request.getCustomerData()))
                .applicationData(parseJson(request.getProductData()))
                .documents(validationService.listDocumentMetadata(request.getId()))
                .validationResult(validation)
                .receivedAt(request.getReceivedAt())
                .validatedAt(request.getValidatedAt())
                .processingHistory(request.getStatus().name())
                .build();
    }

    private CustomerDetailDTO toCustomerDetail(String customerData) {
        try {
            if (customerData == null || customerData.isBlank()) {
                return null;
            }
            var node = objectMapper.readTree(customerData);
            return CustomerDetailDTO.builder()
                    .name(node.path("name").asText(null))
                    .mobile(node.path("mobile").asText(null))
                    .email(node.path("email").asText(null))
                    .build();
        } catch (Exception e) {
            return null;
        }
    }

    private com.fasterxml.jackson.databind.JsonNode parseJson(String json) {
        try {
            return json == null || json.isBlank()
                    ? com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode()
                    : objectMapper.readTree(json);
        } catch (Exception e) {
            return com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.objectNode();
        }
    }

    private String lookupPartnerCode(UUID partnerId) {
        return partnerRepository.findById(partnerId).map(Partner::getPartnerCode).orElse(null);
    }

    private String lookupPartnerName(UUID partnerId) {
        return partnerRepository.findById(partnerId).map(Partner::getPartnerName).orElse(null);
    }

    private String lookupInsurerName(UUID insurerId) {
        return insurerRepository.findById(insurerId).map(Insurer::getInsurerName).orElse(null);
    }
}
