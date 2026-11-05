package com.insurance.integration.partner.service;

import com.insurance.integration.common.enums.PartnerType;
import com.insurance.integration.common.exception.DuplicateResourceException;
import com.insurance.integration.common.exception.ResourceNotFoundException;
import com.insurance.integration.partner.dto.CreatePartnerRequest;
import com.insurance.integration.partner.dto.PartnerResponse;
import com.insurance.integration.partner.dto.UpdatePartnerRequest;
import com.insurance.integration.partner.entity.Partner;
import com.insurance.integration.partner.repository.PartnerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Service for managing distribution partner lifecycle.
 *
 * All operations are admin-only and require prior authentication.
 * Partners cannot self-register.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PartnerService {

    private final PartnerRepository partnerRepository;

    /**
     * Register a new distribution partner.
     *
     * @param request partner registration details
     * @param createdBy admin username performing the operation
     * @return the created partner response
     * @throws DuplicateResourceException if partner_code already exists
     */
    @Transactional
    public PartnerResponse registerPartner(CreatePartnerRequest request, String createdBy) {
        if (partnerRepository.existsByPartnerCode(request.getPartnerCode())) {
            throw new DuplicateResourceException("Partner", "partnerCode", request.getPartnerCode());
        }

        Partner partner = Partner.builder()
                .partnerCode(request.getPartnerCode())
                .partnerName(request.getPartnerName())
                .partnerType(request.getPartnerType())
                .contactEmail(request.getContactEmail())
                .contactPhone(request.getContactPhone())
                .websiteUrl(request.getWebsiteUrl())
                .description(request.getDescription())
                .approvedSourceHosts(request.getApprovedSourceHosts())
                .maxDocumentSizeMb(request.getMaxDocumentSizeMb())
                .active(true)
                .createdBy(createdBy)
                .build();

        partner = partnerRepository.save(partner);
        log.info("Partner registered: code={}, type={}, createdBy={}",
                partner.getPartnerCode(), partner.getPartnerType(), createdBy);

        return toResponse(partner);
    }

    /**
     * Update an existing partner's mutable fields.
     * partner_code and partner_type cannot be changed after creation.
     */
    @Transactional
    public PartnerResponse updatePartner(UUID partnerId, UpdatePartnerRequest request) {
        Partner partner = findByIdOrThrow(partnerId);

        if (request.getPartnerName() != null)        partner.setPartnerName(request.getPartnerName());
        if (request.getContactEmail() != null)       partner.setContactEmail(request.getContactEmail());
        if (request.getContactPhone() != null)       partner.setContactPhone(request.getContactPhone());
        if (request.getWebsiteUrl() != null)         partner.setWebsiteUrl(request.getWebsiteUrl());
        if (request.getDescription() != null)        partner.setDescription(request.getDescription());
        if (request.getApprovedSourceHosts() != null) partner.setApprovedSourceHosts(request.getApprovedSourceHosts());
        if (request.getMaxDocumentSizeMb() != null)  partner.setMaxDocumentSizeMb(request.getMaxDocumentSizeMb());

        partner = partnerRepository.save(partner);
        log.info("Partner updated: id={}, code={}", partner.getId(), partner.getPartnerCode());
        return toResponse(partner);
    }

    /**
     * Activate a deactivated partner. Idempotent — activating an already-active partner is safe.
     */
    @Transactional
    public PartnerResponse activatePartner(UUID partnerId) {
        Partner partner = findByIdOrThrow(partnerId);
        partner.setActive(true);
        partner = partnerRepository.save(partner);
        log.info("Partner activated: id={}, code={}", partner.getId(), partner.getPartnerCode());
        return toResponse(partner);
    }

    /**
     * Deactivate a partner. Their existing data is preserved (soft delete).
     * A deactivated partner cannot authenticate or submit requests.
     */
    @Transactional
    public PartnerResponse deactivatePartner(UUID partnerId) {
        Partner partner = findByIdOrThrow(partnerId);
        partner.setActive(false);
        partner = partnerRepository.save(partner);
        log.warn("Partner deactivated: id={}, code={}", partner.getId(), partner.getPartnerCode());
        return toResponse(partner);
    }

    /**
     * Retrieve a single partner by ID.
     */
    @Transactional(readOnly = true)
    public PartnerResponse getPartnerById(UUID partnerId) {
        return toResponse(findByIdOrThrow(partnerId));
    }

    /**
     * Retrieve a single partner by their business code.
     */
    @Transactional(readOnly = true)
    public PartnerResponse getPartnerByCode(String partnerCode) {
        Partner partner = partnerRepository.findByPartnerCode(partnerCode)
                .orElseThrow(() -> new ResourceNotFoundException("Partner", partnerCode));
        return toResponse(partner);
    }

    /**
     * List partners with optional filters and pagination.
     */
    @Transactional(readOnly = true)
    public Page<PartnerResponse> listPartners(Boolean active, PartnerType partnerType,
                                               String search, Pageable pageable) {
        return partnerRepository.findWithFilters(active, partnerType, search, pageable)
                .map(this::toResponse);
    }

    // ─── internal helpers ───────────────────────────────────────────────────

    public Partner findByIdOrThrow(UUID partnerId) {
        return partnerRepository.findById(partnerId)
                .orElseThrow(() -> new ResourceNotFoundException("Partner", partnerId.toString()));
    }

    public Partner findByCodeOrThrow(String partnerCode) {
        return partnerRepository.findByPartnerCode(partnerCode)
                .orElseThrow(() -> new ResourceNotFoundException("Partner", partnerCode));
    }

    public PartnerResponse toResponse(Partner partner) {
        return PartnerResponse.builder()
                .id(partner.getId())
                .partnerCode(partner.getPartnerCode())
                .partnerName(partner.getPartnerName())
                .partnerType(partner.getPartnerType())
                .contactEmail(partner.getContactEmail())
                .contactPhone(partner.getContactPhone())
                .websiteUrl(partner.getWebsiteUrl())
                .description(partner.getDescription())
                .active(partner.isActive())
                .approvedSourceHosts(partner.getApprovedSourceHosts())
                .maxDocumentSizeMb(partner.getMaxDocumentSizeMb())
                .createdBy(partner.getCreatedBy())
                .createdAt(partner.getCreatedAt())
                .updatedAt(partner.getUpdatedAt())
                .build();
    }
}
