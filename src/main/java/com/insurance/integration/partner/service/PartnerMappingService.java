package com.insurance.integration.partner.service;

import com.insurance.integration.common.exception.DuplicateResourceException;
import com.insurance.integration.common.exception.ResourceNotFoundException;
import com.insurance.integration.insurer.entity.Insurer;
import com.insurance.integration.insurer.service.InsurerService;
import com.insurance.integration.partner.dto.CreateMappingRequest;
import com.insurance.integration.partner.dto.MappingResponse;
import com.insurance.integration.partner.entity.Partner;
import com.insurance.integration.partner.entity.PartnerInsurerMapping;
import com.insurance.integration.partner.repository.PartnerInsurerMappingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing partner-insurer authorisation mappings.
 *
 * A mapping must exist before a partner can submit requests for a given insurer.
 * Deactivating a mapping prevents future submissions but preserves historical records.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PartnerMappingService {

    private final PartnerInsurerMappingRepository mappingRepository;
    private final PartnerService partnerService;
    private final InsurerService insurerService;

    /**
     * Create a new partner-insurer mapping.
     * Throws DuplicateResourceException if the mapping already exists (active or not).
     */
    @Transactional
    public MappingResponse createMapping(UUID partnerId, CreateMappingRequest request, String createdBy) {
        Partner partner = partnerService.findByIdOrThrow(partnerId);
        Insurer insurer = insurerService.findByCodeOrThrow(request.getInsurerCode());

        if (mappingRepository.existsByPartnerIdAndInsurerId(partner.getId(), insurer.getId())) {
            throw new DuplicateResourceException(
                    "Mapping between partner " + partner.getPartnerCode() +
                    " and insurer " + insurer.getInsurerCode() + " already exists.");
        }

        PartnerInsurerMapping mapping = PartnerInsurerMapping.builder()
                .partner(partner)
                .insurer(insurer)
                .active(true)
                .permittedProductTypes(request.getPermittedProductTypes())
                .createdBy(createdBy)
                .build();

        mapping = mappingRepository.save(mapping);
        log.info("Partner-insurer mapping created: partner={}, insurer={}, createdBy={}",
                partner.getPartnerCode(), insurer.getInsurerCode(), createdBy);
        return toResponse(mapping);
    }

    /**
     * Deactivate an existing mapping. The mapping record is preserved for audit history.
     */
    @Transactional
    public MappingResponse deactivateMapping(UUID mappingId) {
        PartnerInsurerMapping mapping = mappingRepository.findById(mappingId)
                .orElseThrow(() -> new ResourceNotFoundException("PartnerInsurerMapping", mappingId.toString()));

        mapping.setActive(false);
        mapping = mappingRepository.save(mapping);
        log.warn("Partner-insurer mapping deactivated: id={}, partner={}, insurer={}",
                mappingId, mapping.getPartner().getPartnerCode(), mapping.getInsurer().getInsurerCode());
        return toResponse(mapping);
    }

    /**
     * Get all active mappings for a partner.
     */
    @Transactional(readOnly = true)
    public List<MappingResponse> getMappingsForPartner(UUID partnerId) {
        // verify partner exists
        partnerService.findByIdOrThrow(partnerId);
        return mappingRepository.findByPartnerIdAndActive(partnerId, true)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    /**
     * Check whether a specific partner-insurer combination is actively permitted.
     * Used during request ingestion to validate partner permissions.
     */
    @Transactional(readOnly = true)
    public boolean isPartnerPermittedForInsurer(UUID partnerId, UUID insurerId) {
        return mappingRepository.findActiveMapping(partnerId, insurerId).isPresent();
    }

    // ─── helpers ────────────────────────────────────────────────────────────

    private MappingResponse toResponse(PartnerInsurerMapping mapping) {
        return MappingResponse.builder()
                .id(mapping.getId())
                .partnerCode(mapping.getPartner().getPartnerCode())
                .partnerName(mapping.getPartner().getPartnerName())
                .insurerCode(mapping.getInsurer().getInsurerCode())
                .insurerName(mapping.getInsurer().getInsurerName())
                .active(mapping.isActive())
                .permittedProductTypes(mapping.getPermittedProductTypes())
                .createdBy(mapping.getCreatedBy())
                .createdAt(mapping.getCreatedAt())
                .build();
    }
}
