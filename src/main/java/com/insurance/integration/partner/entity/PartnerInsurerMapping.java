package com.insurance.integration.partner.entity;

import com.insurance.integration.common.entity.BaseEntity;
import com.insurance.integration.insurer.entity.Insurer;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Authorisation mapping between a partner and an insurer.
 *
 * A partner can only submit requests to insurers it is explicitly mapped to.
 * Each mapping may restrict which product types (MOTOR_INSURANCE, TRAVEL_INSURANCE, etc.)
 * the partner is permitted to submit for that insurer.
 *
 * Example: Axis Bank is mapped to Max Life Insurance for LIFE_INSURANCE products only.
 */
@Entity
@Table(name = "partner_insurer_mappings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PartnerInsurerMapping extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_id", nullable = false)
    private Partner partner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "insurer_id", nullable = false)
    private Insurer insurer;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    /**
     * JSON array of permitted product types for this mapping.
     * Null or empty means all product types are permitted.
     * Example: ["MOTOR_INSURANCE","TRAVEL_INSURANCE"]
     */
    @Column(name = "permitted_product_types", columnDefinition = "TEXT")
    private String permittedProductTypes;

    @Column(name = "created_by", length = 100)
    private String createdBy;
}
