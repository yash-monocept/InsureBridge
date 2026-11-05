package com.insurance.integration.insurer.repository;

import com.insurance.integration.common.enums.InsurerType;
import com.insurance.integration.insurer.entity.Insurer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface InsurerRepository extends JpaRepository<Insurer, UUID> {

    Optional<Insurer> findByInsurerCode(String insurerCode);

    boolean existsByInsurerCode(String insurerCode);

    Page<Insurer> findByActive(boolean active, Pageable pageable);

    @Query("SELECT i FROM Insurer i WHERE " +
           "(:active IS NULL OR i.active = :active) AND " +
           "(:insurerType IS NULL OR i.insurerType = :insurerType)")
    Page<Insurer> findWithFilters(
            @Param("active") Boolean active,
            @Param("insurerType") InsurerType insurerType,
            Pageable pageable);
}
