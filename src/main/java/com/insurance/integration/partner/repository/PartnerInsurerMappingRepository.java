package com.insurance.integration.partner.repository;

import com.insurance.integration.partner.entity.PartnerInsurerMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PartnerInsurerMappingRepository extends JpaRepository<PartnerInsurerMapping, UUID> {

    List<PartnerInsurerMapping> findByPartnerIdAndActive(UUID partnerId, boolean active);

    List<PartnerInsurerMapping> findByInsurerId(UUID insurerId);

    Optional<PartnerInsurerMapping> findByPartnerIdAndInsurerId(UUID partnerId, UUID insurerId);

    boolean existsByPartnerIdAndInsurerId(UUID partnerId, UUID insurerId);

    @Query("SELECT m FROM PartnerInsurerMapping m " +
           "WHERE m.partner.id = :partnerId AND m.insurer.id = :insurerId AND m.active = true")
    Optional<PartnerInsurerMapping> findActiveMapping(
            @Param("partnerId") UUID partnerId,
            @Param("insurerId") UUID insurerId);
}
