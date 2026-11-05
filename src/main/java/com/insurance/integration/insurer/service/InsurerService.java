package com.insurance.integration.insurer.service;

import com.insurance.integration.common.enums.InsurerType;
import com.insurance.integration.common.exception.DuplicateResourceException;
import com.insurance.integration.common.exception.ResourceNotFoundException;
import com.insurance.integration.insurer.dto.CreateInsurerRequest;
import com.insurance.integration.insurer.dto.InsurerResponse;
import com.insurance.integration.insurer.entity.Insurer;
import com.insurance.integration.insurer.repository.InsurerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * Service for managing insurance provider (insurer) lifecycle.
 * All write operations are admin-only.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InsurerService {

    private final InsurerRepository insurerRepository;

    @Transactional
    public InsurerResponse registerInsurer(CreateInsurerRequest request) {
        if (insurerRepository.existsByInsurerCode(request.getInsurerCode())) {
            throw new DuplicateResourceException("Insurer", "insurerCode", request.getInsurerCode());
        }

        Insurer insurer = Insurer.builder()
                .insurerCode(request.getInsurerCode())
                .insurerName(request.getInsurerName())
                .insurerType(request.getInsurerType())
                .contactEmail(request.getContactEmail())
                .destinationBucketName(request.getDestinationBucketName())
                .destinationBucketRegion(request.getDestinationBucketRegion())
                .destinationIamRoleArn(request.getDestinationIamRoleArn())
                .destinationPathPrefix(request.getDestinationPathPrefix())
                .snsTopicArn(request.getSnsTopicArn())
                .webhookUrl(request.getWebhookUrl())
                .webhookSecret(request.getWebhookSecret())
                .active(true)
                .build();

        insurer = insurerRepository.save(insurer);
        log.info("Insurer registered: code={}, type={}", insurer.getInsurerCode(), insurer.getInsurerType());
        return toResponse(insurer);
    }

    @Transactional
    public InsurerResponse activateInsurer(UUID insurerId) {
        Insurer insurer = findByIdOrThrow(insurerId);
        insurer.setActive(true);
        insurer = insurerRepository.save(insurer);
        log.info("Insurer activated: id={}, code={}", insurer.getId(), insurer.getInsurerCode());
        return toResponse(insurer);
    }

    @Transactional
    public InsurerResponse deactivateInsurer(UUID insurerId) {
        Insurer insurer = findByIdOrThrow(insurerId);
        insurer.setActive(false);
        insurer = insurerRepository.save(insurer);
        log.warn("Insurer deactivated: id={}, code={}", insurer.getId(), insurer.getInsurerCode());
        return toResponse(insurer);
    }

    @Transactional(readOnly = true)
    public InsurerResponse getInsurerById(UUID insurerId) {
        return toResponse(findByIdOrThrow(insurerId));
    }

    @Transactional(readOnly = true)
    public InsurerResponse getInsurerByCode(String insurerCode) {
        Insurer insurer = insurerRepository.findByInsurerCode(insurerCode)
                .orElseThrow(() -> new ResourceNotFoundException("Insurer", insurerCode));
        return toResponse(insurer);
    }

    @Transactional(readOnly = true)
    public Page<InsurerResponse> listInsurers(Boolean active, InsurerType insurerType, Pageable pageable) {
        return insurerRepository.findWithFilters(active, insurerType, pageable)
                .map(this::toResponse);
    }

    // ─── internal helpers ────────────────────────────────────────────────────

    public Insurer findByIdOrThrow(UUID insurerId) {
        return insurerRepository.findById(insurerId)
                .orElseThrow(() -> new ResourceNotFoundException("Insurer", insurerId.toString()));
    }

    public Insurer findByCodeOrThrow(String insurerCode) {
        return insurerRepository.findByInsurerCode(insurerCode)
                .orElseThrow(() -> new ResourceNotFoundException("Insurer", insurerCode));
    }

    public InsurerResponse toResponse(Insurer insurer) {
        return InsurerResponse.builder()
                .id(insurer.getId())
                .insurerCode(insurer.getInsurerCode())
                .insurerName(insurer.getInsurerName())
                .insurerType(insurer.getInsurerType())
                .contactEmail(insurer.getContactEmail())
                .active(insurer.isActive())
                // Don't expose bucket/IAM/secret details — derive booleans instead
                .destinationConfigured(StringUtils.hasText(insurer.getDestinationBucketName()))
                .snsConfigured(StringUtils.hasText(insurer.getSnsTopicArn()))
                .webhookConfigured(StringUtils.hasText(insurer.getWebhookUrl()))
                .createdAt(insurer.getCreatedAt())
                .updatedAt(insurer.getUpdatedAt())
                .build();
    }
}
