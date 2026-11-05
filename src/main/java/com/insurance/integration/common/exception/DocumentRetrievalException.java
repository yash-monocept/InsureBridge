package com.insurance.integration.common.exception;

/**
 * Thrown when document retrieval from partner S3 fails.
 * This is an internal exception — callers catch and convert to status updates.
 */
public class DocumentRetrievalException extends RuntimeException {

    private final String documentType;

    public DocumentRetrievalException(String message, String documentType) {
        super(message);
        this.documentType = documentType;
    }

    public DocumentRetrievalException(String message, String documentType, Throwable cause) {
        super(message, cause);
        this.documentType = documentType;
    }

    public String getDocumentType() {
        return documentType;
    }
}
