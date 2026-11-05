package com.insurance.integration.common.enums;

/**
 * Processing lifecycle statuses for an ingestion request.
 * Transitions are enforced — not all status changes are valid.
 */
public enum IngestionStatus {
    /** Request received from partner. Initial state. */
    RECEIVED,

    /** Partner authentication or authorisation failed. Terminal failure. */
    AUTHENTICATION_FAILED,

    /** Request is being validated (schema + business rules). */
    VALIDATING,

    /** Validation found blocking errors. Terminal failure. */
    VALIDATION_FAILED,

    /** Retrieving source documents from partner S3. */
    FETCHING_SOURCE,

    /** Source documents retrieved successfully. */
    SOURCE_RETRIEVED,

    /** Document file-level validation is in progress. */
    DOCUMENT_VALIDATION,

    /** Document validation failed (corrupt/invalid file). Terminal failure. */
    DOCUMENT_REJECTED,

    /** All validations passed. Ready for async processing. */
    VALIDATED,

    /** Job has been published to SQS. Waiting for async worker. */
    QUEUED,

    /** Async worker is processing the transfer. */
    PROCESSING,

    /** Document transfer to insurer S3 failed. Eligible for retry. */
    TRANSFER_FAILED,

    /** Documents transferred to insurer S3 successfully. */
    TRANSFERRED,

    /** Notification to partner/insurer is pending. */
    NOTIFICATION_PENDING,

    /** Notification delivery failed. Eligible for retry. */
    NOTIFICATION_FAILED,

    /** Full processing pipeline completed successfully. Terminal success. */
    COMPLETED,

    /** Max retries exhausted. Moved to dead-letter queue. Terminal failure. */
    DEAD_LETTER
}
