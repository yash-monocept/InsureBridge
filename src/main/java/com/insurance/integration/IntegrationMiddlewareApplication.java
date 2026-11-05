package com.insurance.integration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;

/**
 * Insurance Integration Middleware and Cross-Account AWS Document Transfer Platform.
 *
 * Entry point for the Spring Boot application.
 * This system acts as a controlled integration channel between external distribution
 * partners (banks, dealerships, airlines) and insurance providers (Max Life, Care Health, etc.).
 */
@SpringBootApplication
@Slf4j
public class IntegrationMiddlewareApplication {

    private final Environment environment;

    public IntegrationMiddlewareApplication(Environment environment) {
        this.environment = environment;
    }

    public static void main(String[] args) {
        SpringApplication.run(IntegrationMiddlewareApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        String port = environment.getProperty("server.port", "8080");
        String[] activeProfiles = environment.getActiveProfiles();
        String profile = activeProfiles.length > 0 ? activeProfiles[0] : "default";

        log.info("=============================================================");
        log.info("  Insurance Integration Middleware - STARTED");
        log.info("  Profile   : {}", profile);
        log.info("  Port      : {}", port);
        log.info("  Swagger   : http://localhost:{}/swagger-ui.html", port);
        log.info("  Health    : http://localhost:{}/actuator/health", port);
        log.info("=============================================================");
    }
}
