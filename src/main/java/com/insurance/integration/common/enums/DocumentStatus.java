package com.insurance.integration.common.enums;

/**
 * Status of an individual document within an ingestion request.
 */
public enum DocumentStatus {
    PENDING,
    FETCHING,
    FETCHED,
    FETCH_FAILED,
    VALIDATING,
    VALIDATION_PASSED,
    VALIDATION_FAILED,
    UPLOADING,
    UPLOADED,
    UPLOAD_FAILED,
    CHECKSUM_MISMATCH
}
