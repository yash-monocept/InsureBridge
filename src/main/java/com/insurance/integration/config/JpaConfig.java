package com.insurance.integration.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA configuration enabling automatic auditing (createdAt, updatedAt).
 */
@Configuration
@EnableJpaAuditing
public class JpaConfig {
    // Auditing enabled — @CreatedDate and @LastModifiedDate annotations
    // on entities will be automatically populated.
}
