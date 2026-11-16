package com.insurance.integration.schema.repository;

import com.insurance.integration.schema.entity.FieldRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Data access for {@link FieldRule}.
 */
@Repository
public interface FieldRuleRepository extends JpaRepository<FieldRule, UUID> {

    List<FieldRule> findBySchemaId(UUID schemaId);

    boolean existsBySchemaIdAndFieldPath(UUID schemaId, String fieldPath);
}
