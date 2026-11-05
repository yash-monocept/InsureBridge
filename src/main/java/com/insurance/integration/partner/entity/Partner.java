package com.insurance.integration.partner.entity;

import com.insurance.integration.common.entity.BaseEntity;
import com.insurance.integration.common.enums.PartnerType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * External distribution partner entity.
 *
 * Represents organisations (banks, car dealerships, airlines, etc.) that integrate
 * with this platform to submit insurance applications on behalf of their customers.
 *
 * Partners are registered by the platform administrator and assigned a unique
 * partner_code. Each partner gets API credentials (ApiClient) to authenticate
 * their machine-to-machine requests.
 */
@Entity
@Table(name = "partners")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Partner extends BaseEntity {

    /**
     * Short unique business code used in logs and API responses.
     * Example: AXIS_BANK, MAX_DEALER, INDIGO_AIRLINES
     */
    @Column(name = "partner_code", nullable = false, unique = true, length = 50)
    private String partnerCode;

    @Column(name = "partner_name", nullable = false, length = 200)
    private String partnerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "partner_type", nullable = false, length = 50)
    private PartnerType partnerType;

    @Column(name = "contact_email", nullable = false, length = 200)
    private String contactEmail;

    @Column(name = "contact_phone", length = 20)
    private String contactPhone;

    @Column(name = "website_url", length = 500)
    private String websiteUrl;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    /**
     * JSON array of approved S3 host patterns.
     * Used for SSRF protection when validating partner-supplied pre-signed URLs.
     * Example: ["*.s3.amazonaws.com", "axis-bank-docs.s3.ap-south-1.amazonaws.com"]
     */
    @Column(name = "approved_source_hosts", columnDefinition = "TEXT")
    private String approvedSourceHosts;

    @Column(name = "max_document_size_mb", nullable = false)
    @Builder.Default
    private int maxDocumentSizeMb = 50;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    /**
     * Partner-insurer authorisation mappings.
     * A partner can only submit to insurers it has been explicitly mapped to.
     */
    @OneToMany(mappedBy = "partner")
    @Builder.Default
    private List<PartnerInsurerMapping> insurerMappings = new ArrayList<>();
}
