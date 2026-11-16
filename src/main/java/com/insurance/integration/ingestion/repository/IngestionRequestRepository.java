package com.insurance.integration.ingestion.repository;

import com.insurance.integration.common.enums.IngestionStatus;
import com.insurance.integration.ingestion.entity.IngestionRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Data access for {@link IngestionRequest}.
 */
@Repository
public interface IngestionRequestRepository extends JpaRepository<IngestionRequest, UUID> {

    Optional<IngestionRequest> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);

    Page<IngestionRequest> findByPartnerId(UUID partnerId, Pageable pageable);

    Page<IngestionRequest> findByPartnerIdAndStatus(UUID partnerId, IngestionStatus status, Pageable pageable);

    @Query("SELECT r FROM IngestionRequest r WHERE " +
           "(:partnerId IS NULL OR r.partnerId = :partnerId) AND " +
           "(:insurerId IS NULL OR r.insurerId = :insurerId) AND " +
           "(:status IS NULL OR r.status = :status) AND " +
           "(:productType IS NULL OR r.productType = :productType) AND " +
           "(:search IS NULL OR LOWER(r.externalReference) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "   OR LOWER(r.productType) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<IngestionRequest> findWithFilters(
            @Param("partnerId") UUID partnerId,
            @Param("insurerId") UUID insurerId,
            @Param("status") IngestionStatus status,
            @Param("productType") String productType,
            @Param("search") String search,
            Pageable pageable);

    List<IngestionRequest> findByStatus(IngestionStatus status);
}
