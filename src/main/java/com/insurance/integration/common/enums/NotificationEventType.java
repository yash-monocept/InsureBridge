package com.insurance.integration.common.enums;

/**
 * Event types for partner and insurer notifications.
 */
public enum NotificationEventType {
    INGESTION_RECEIVED,
    VALIDATION_FAILED,
    VALIDATION_PASSED,
    DOCUMENT_TRANSFER_STARTED,
    DOCUMENT_TRANSFER_COMPLETED,
    DOCUMENT_TRANSFER_FAILED,
    PROCESSING_COMPLETED,
    PROCESSING_DEAD_LETTERED
}
