package com.insurance.integration.security.service;

import com.insurance.integration.partner.entity.Partner;
import com.insurance.integration.security.entity.ApiClient;

/**
 * Internal result of successful client-credential authentication.
 */
public record AuthenticationResult(ApiClient client, Partner partner) {
}
