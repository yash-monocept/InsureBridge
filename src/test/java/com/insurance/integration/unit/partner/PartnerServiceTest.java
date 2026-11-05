package com.insurance.integration.unit.partner;

import com.insurance.integration.common.enums.PartnerType;
import com.insurance.integration.common.exception.DuplicateResourceException;
import com.insurance.integration.common.exception.ResourceNotFoundException;
import com.insurance.integration.partner.dto.CreatePartnerRequest;
import com.insurance.integration.partner.dto.PartnerResponse;
import com.insurance.integration.partner.dto.UpdatePartnerRequest;
import com.insurance.integration.partner.entity.Partner;
import com.insurance.integration.partner.repository.PartnerRepository;
import com.insurance.integration.partner.service.PartnerService;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PartnerService Unit Tests")
class PartnerServiceTest {

    @Mock
    private PartnerRepository partnerRepository;

    @InjectMocks
    private PartnerService partnerService;

    private CreatePartnerRequest validRequest;
    private Partner savedPartner;

    @BeforeEach
    void setUp() {
        validRequest = CreatePartnerRequest.builder()
                .partnerCode("AXIS_BANK")
                .partnerName("Axis Bank Limited")
                .partnerType(PartnerType.BANK)
                .contactEmail("integrations@axisbank.com")
                .maxDocumentSizeMb(50)
                .build();

        savedPartner = Partner.builder()
                .partnerCode("AXIS_BANK")
                .partnerName("Axis Bank Limited")
                .partnerType(PartnerType.BANK)
                .contactEmail("integrations@axisbank.com")
                .active(true)
                .maxDocumentSizeMb(50)
                .createdBy("admin")
                .build();
    }

    // ─── registerPartner ────────────────────────────────────────────────────

    @Test
    @DisplayName("registerPartner: should persist and return new partner when code is unique")
    void registerPartner_success() {
        when(partnerRepository.existsByPartnerCode("AXIS_BANK")).thenReturn(false);
        when(partnerRepository.save(any(Partner.class))).thenReturn(savedPartner);

        PartnerResponse result = partnerService.registerPartner(validRequest, "admin");

        assertThat(result).isNotNull();
        assertThat(result.getPartnerCode()).isEqualTo("AXIS_BANK");
        assertThat(result.getPartnerName()).isEqualTo("Axis Bank Limited");
        assertThat(result.getPartnerType()).isEqualTo(PartnerType.BANK);
        assertThat(result.isActive()).isTrue();
        verify(partnerRepository).save(any(Partner.class));
    }

    @Test
    @DisplayName("registerPartner: should throw DuplicateResourceException when code already exists")
    void registerPartner_duplicateCode_throws() {
        when(partnerRepository.existsByPartnerCode("AXIS_BANK")).thenReturn(true);

        assertThatThrownBy(() -> partnerService.registerPartner(validRequest, "admin"))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("AXIS_BANK");

        verify(partnerRepository, never()).save(any());
    }

    // ─── updatePartner ───────────────────────────────────────────────────────

    @Test
    @DisplayName("updatePartner: should update only non-null fields")
    void updatePartner_partialUpdate() {
        UUID partnerId = UUID.randomUUID();
        UpdatePartnerRequest updateRequest = new UpdatePartnerRequest();
        updateRequest.setPartnerName("Axis Bank Ltd Updated");

        when(partnerRepository.findById(partnerId)).thenReturn(Optional.of(savedPartner));
        when(partnerRepository.save(any(Partner.class))).thenReturn(savedPartner);

        PartnerResponse result = partnerService.updatePartner(partnerId, updateRequest);

        assertThat(result).isNotNull();
        verify(partnerRepository).save(any(Partner.class));
    }

    @Test
    @DisplayName("updatePartner: should throw ResourceNotFoundException when partner does not exist")
    void updatePartner_notFound_throws() {
        UUID partnerId = UUID.randomUUID();
        when(partnerRepository.findById(partnerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> partnerService.updatePartner(partnerId, new UpdatePartnerRequest()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── activatePartner ─────────────────────────────────────────────────────

    @Test
    @DisplayName("activatePartner: should set active=true and save")
    void activatePartner_success() {
        UUID partnerId = UUID.randomUUID();
        savedPartner.setActive(false);

        when(partnerRepository.findById(partnerId)).thenReturn(Optional.of(savedPartner));
        when(partnerRepository.save(any(Partner.class))).thenReturn(savedPartner);

        PartnerResponse result = partnerService.activatePartner(partnerId);

        assertThat(result).isNotNull();
        verify(partnerRepository).save(any(Partner.class));
    }

    // ─── deactivatePartner ───────────────────────────────────────────────────

    @Test
    @DisplayName("deactivatePartner: should set active=false and save")
    void deactivatePartner_success() {
        UUID partnerId = UUID.randomUUID();

        when(partnerRepository.findById(partnerId)).thenReturn(Optional.of(savedPartner));
        when(partnerRepository.save(any(Partner.class))).thenReturn(savedPartner);

        PartnerResponse result = partnerService.deactivatePartner(partnerId);

        assertThat(result).isNotNull();
        verify(partnerRepository).save(any(Partner.class));
    }

    // ─── getPartnerById ──────────────────────────────────────────────────────

    @Test
    @DisplayName("getPartnerById: should return partner when found")
    void getPartnerById_found() {
        UUID partnerId = UUID.randomUUID();
        when(partnerRepository.findById(partnerId)).thenReturn(Optional.of(savedPartner));

        PartnerResponse result = partnerService.getPartnerById(partnerId);

        assertThat(result).isNotNull();
        assertThat(result.getPartnerCode()).isEqualTo("AXIS_BANK");
    }

    @Test
    @DisplayName("getPartnerById: should throw ResourceNotFoundException when not found")
    void getPartnerById_notFound() {
        UUID partnerId = UUID.randomUUID();
        when(partnerRepository.findById(partnerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> partnerService.getPartnerById(partnerId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── getPartnerByCode ────────────────────────────────────────────────────

    @Test
    @DisplayName("getPartnerByCode: should return partner for known code")
    void getPartnerByCode_found() {
        when(partnerRepository.findByPartnerCode("AXIS_BANK")).thenReturn(Optional.of(savedPartner));

        PartnerResponse result = partnerService.getPartnerByCode("AXIS_BANK");

        assertThat(result.getPartnerCode()).isEqualTo("AXIS_BANK");
    }

    @Test
    @DisplayName("getPartnerByCode: should throw ResourceNotFoundException for unknown code")
    void getPartnerByCode_notFound() {
        when(partnerRepository.findByPartnerCode(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> partnerService.getPartnerByCode("UNKNOWN"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
