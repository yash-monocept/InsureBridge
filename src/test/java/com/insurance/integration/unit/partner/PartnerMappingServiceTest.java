package com.insurance.integration.unit.partner;

import com.insurance.integration.common.enums.InsurerType;
import com.insurance.integration.common.enums.PartnerType;
import com.insurance.integration.common.exception.DuplicateResourceException;
import com.insurance.integration.insurer.entity.Insurer;
import com.insurance.integration.insurer.service.InsurerService;
import com.insurance.integration.partner.dto.CreateMappingRequest;
import com.insurance.integration.partner.dto.MappingResponse;
import com.insurance.integration.partner.entity.Partner;
import com.insurance.integration.partner.entity.PartnerInsurerMapping;
import com.insurance.integration.partner.repository.PartnerInsurerMappingRepository;
import com.insurance.integration.partner.service.PartnerMappingService;
import com.insurance.integration.partner.service.PartnerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PartnerMappingService Unit Tests")
class PartnerMappingServiceTest {

    @Mock private PartnerInsurerMappingRepository mappingRepository;
    @Mock private PartnerService partnerService;
    @Mock private InsurerService insurerService;

    @InjectMocks
    private PartnerMappingService mappingService;

    private UUID partnerId;
    private Partner partner;
    private Insurer insurer;

    @BeforeEach
    void setUp() {
        partnerId = UUID.randomUUID();

        partner = Partner.builder()
                .partnerCode("AXIS_BANK")
                .partnerName("Axis Bank Limited")
                .partnerType(PartnerType.BANK)
                .contactEmail("integrations@axisbank.com")
                .active(true)
                .maxDocumentSizeMb(50)
                .build();

        insurer = Insurer.builder()
                .insurerCode("MAX_LIFE")
                .insurerName("Max Life Insurance Company Limited")
                .insurerType(InsurerType.LIFE)
                .active(true)
                .build();
    }

    @Test
    @DisplayName("createMapping: should create mapping between partner and insurer")
    void createMapping_success() {
        CreateMappingRequest request = new CreateMappingRequest();
        request.setInsurerCode("MAX_LIFE");
        request.setPermittedProductTypes("[\"MOTOR_INSURANCE\"]");

        when(partnerService.findByIdOrThrow(partnerId)).thenReturn(partner);
        when(insurerService.findByCodeOrThrow("MAX_LIFE")).thenReturn(insurer);
        when(mappingRepository.existsByPartnerIdAndInsurerId(any(), any())).thenReturn(false);

        PartnerInsurerMapping savedMapping = PartnerInsurerMapping.builder()
                .partner(partner)
                .insurer(insurer)
                .active(true)
                .permittedProductTypes("[\"MOTOR_INSURANCE\"]")
                .createdBy("admin")
                .build();

        when(mappingRepository.save(any(PartnerInsurerMapping.class))).thenReturn(savedMapping);

        MappingResponse result = mappingService.createMapping(partnerId, request, "admin");

        assertThat(result).isNotNull();
        assertThat(result.getPartnerCode()).isEqualTo("AXIS_BANK");
        assertThat(result.getInsurerCode()).isEqualTo("MAX_LIFE");
        assertThat(result.isActive()).isTrue();
        verify(mappingRepository).save(any(PartnerInsurerMapping.class));
    }

    @Test
    @DisplayName("createMapping: should throw DuplicateResourceException if mapping already exists")
    void createMapping_duplicate_throws() {
        CreateMappingRequest request = new CreateMappingRequest();
        request.setInsurerCode("MAX_LIFE");

        when(partnerService.findByIdOrThrow(partnerId)).thenReturn(partner);
        when(insurerService.findByCodeOrThrow("MAX_LIFE")).thenReturn(insurer);
        when(mappingRepository.existsByPartnerIdAndInsurerId(any(), any())).thenReturn(true);

        assertThatThrownBy(() -> mappingService.createMapping(partnerId, request, "admin"))
                .isInstanceOf(DuplicateResourceException.class);

        verify(mappingRepository, never()).save(any());
    }

    @Test
    @DisplayName("isPartnerPermittedForInsurer: returns true when active mapping exists")
    void isPartnerPermitted_activeMappingExists() {
        UUID insurerId = UUID.randomUUID();
        when(mappingRepository.findActiveMapping(partnerId, insurerId))
                .thenReturn(java.util.Optional.of(PartnerInsurerMapping.builder()
                        .partner(partner).insurer(insurer).active(true).build()));

        assertThat(mappingService.isPartnerPermittedForInsurer(partnerId, insurerId)).isTrue();
    }

    @Test
    @DisplayName("isPartnerPermittedForInsurer: returns false when no active mapping exists")
    void isPartnerPermitted_noMapping() {
        UUID insurerId = UUID.randomUUID();
        when(mappingRepository.findActiveMapping(partnerId, insurerId))
                .thenReturn(java.util.Optional.empty());

        assertThat(mappingService.isPartnerPermittedForInsurer(partnerId, insurerId)).isFalse();
    }
}
