package com.insurance.integration.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a deactivated partner attempts to make an API call.
 * Maps to HTTP 403 Forbidden.
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class PartnerNotActiveException extends RuntimeException {

    public PartnerNotActiveException(String partnerCode) {
        super("Partner is not active: " + partnerCode);
    }
}
