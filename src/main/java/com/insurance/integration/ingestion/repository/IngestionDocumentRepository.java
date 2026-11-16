package com.insurance.integration.ingestion.repository;

import com.insurance.integration.ingestion.entity.IngestionDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Data access for {@link IngestionDocument}.
 */
@Repository
public interface IngestionDocumentRepository extends JpaRepository<IngestionDocument, UUID> {

    List<IngestionDocument> findByIngestionRequestId(UUID ingestionRequestId);

    List<IngestionDocument> findByIngestionRequestIdAndDocumentType(UUID ingestionRequestId, String documentType);
}
