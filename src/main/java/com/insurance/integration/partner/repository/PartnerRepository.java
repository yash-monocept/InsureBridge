package com.insurance.integration.partner.repository;

import com.insurance.integration.common.enums.PartnerType;
import com.insurance.integration.partner.entity.Partner;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PartnerRepository extends JpaRepository<Partner, UUID> {

    Optional<Partner> findByPartnerCode(String partnerCode);

    boolean existsByPartnerCode(String partnerCode);

    boolean existsByContactEmail(String contactEmail);

    Page<Partner> findAll(Pageable pageable);

    Page<Partner> findByActive(boolean active, Pageable pageable);

    Page<Partner> findByPartnerType(PartnerType partnerType, Pageable pageable);

    Page<Partner> findByActiveAndPartnerType(boolean active, PartnerType partnerType, Pageable pageable);

    @Query("SELECT p FROM Partner p WHERE " +
           "(:active IS NULL OR p.active = :active) AND " +
           "(:partnerType IS NULL OR p.partnerType = :partnerType) AND " +
           "(:search IS NULL OR LOWER(p.partnerName) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "   OR LOWER(p.partnerCode) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Partner> findWithFilters(
            @Param("active") Boolean active,
            @Param("partnerType") PartnerType partnerType,
            @Param("search") String search,
            Pageable pageable);
}
