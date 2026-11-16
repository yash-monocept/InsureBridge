package com.insurance.integration.schema.repository;

import com.insurance.integration.schema.entity.DocumentRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Data access for {@link DocumentRule}.
 */
@Repository
public interface DocumentRuleRepository extends JpaRepository<DocumentRule, UUID> {

    List<DocumentRule> findBySchemaId(UUID schemaId);

    boolean existsBySchemaIdAndDocumentType(UUID schemaId, String documentType);
}
