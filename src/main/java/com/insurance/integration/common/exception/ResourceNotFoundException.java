package com.insurance.integration.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a requested resource does not exist.
 * Maps to HTTP 404 Not Found.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {

    private final String resourceType;
    private final String identifier;

    public ResourceNotFoundException(String resourceType, String identifier) {
        super(resourceType + " not found with identifier: " + identifier);
        this.resourceType = resourceType;
        this.identifier = identifier;
    }

    public ResourceNotFoundException(String message) {
        super(message);
        this.resourceType = null;
        this.identifier = null;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getIdentifier() {
        return identifier;
    }
}
