package com.insurance.integration.schema.repository;

import com.insurance.integration.schema.entity.ProductSchema;
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
 * Data access for {@link ProductSchema}.
 */
@Repository
public interface ProductSchemaRepository extends JpaRepository<ProductSchema, UUID> {

    Optional<ProductSchema> findByIdAndActive(UUID id, boolean active);

    List<ProductSchema> findByProductTypeAndActive(String productType, boolean active);

    @Query("SELECT s FROM ProductSchema s WHERE " +
           "(:insurerId IS NULL OR s.insurerId = :insurerId) AND " +
           "(:partnerId IS NULL OR s.partnerId = :partnerId) AND " +
           "(:productType IS NULL OR s.productType = :productType) AND " +
           "(:active IS NULL OR s.active = :active) AND " +
           "(:search IS NULL OR LOWER(s.schemaName) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<ProductSchema> findWithFilters(
            @Param("insurerId") UUID insurerId,
            @Param("partnerId") UUID partnerId,
            @Param("productType") String productType,
            @Param("active") Boolean active,
            @Param("search") String search,
            Pageable pageable);

}
