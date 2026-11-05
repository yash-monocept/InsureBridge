package com.insurance.integration.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 3 / Swagger configuration.
 *
 * Swagger UI available at: /swagger-ui.html
 * OpenAPI JSON available at: /v3/api-docs
 */
@Configuration
public class SwaggerConfig {

    @Value("${server.port:8080}")
    private String serverPort;

    private static final String BEARER_AUTH_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(buildApiInfo())
                .servers(buildServers())
                .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH_SCHEME))
                .components(new Components()
                        .addSecuritySchemes(BEARER_AUTH_SCHEME, buildBearerSecurityScheme()));
    }

    private Info buildApiInfo() {
        return new Info()
                .title("Insurance Integration Middleware API")
                .description("""
                        ## Insurance Integration Middleware and Cross-Account AWS Document Transfer Platform
                        
                        This platform acts as a controlled integration channel between **external distribution partners**
                        (banks, car dealerships, airlines, railways, travel platforms) and **insurance providers**
                        (Max Life Insurance, Axis Max Life, Care Health Insurance, etc.).
                        
                        ### Key Capabilities
                        - Partner authentication via OAuth 2.0 Client Credentials
                        - Insurance request ingestion and configurable schema validation
                        - Business rule and cross-field validation
                        - Asynchronous document transfer from partner S3 to insurer S3
                        - Full processing status tracking and partner notifications
                        - Complete audit trail
                        
                        ### Authentication
                        Partners authenticate using **OAuth 2.0 Client Credentials**.
                        Obtain a JWT access token from `POST /api/v1/auth/token` and include it
                        as `Authorization: Bearer <token>` in subsequent requests.
                        
                        ### Important
                        This system is NOT an insurance company. It routes and transfers documents.
                        It does not underwrite, price, approve, or settle insurance.
                        """)
                .version("1.0.0")
                .contact(new Contact()
                        .name("Integration Platform Team")
                        .email("platform@insurance-integration.com"))
                .license(new License()
                        .name("Academic Project — Not for Production")
                        .url("https://github.com"));
    }

    private List<Server> buildServers() {
        return List.of(
                new Server()
                        .url("http://localhost:" + serverPort)
                        .description("Local Development Server"),
                new Server()
                        .url("https://api.insurance-integration.com")
                        .description("Production Server (placeholder)")
        );
    }

    private SecurityScheme buildBearerSecurityScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .name(BEARER_AUTH_SCHEME)
                .description("""
                        JWT Bearer token. Obtain via:
                        `POST /api/v1/auth/token`
                        with client_id and client_secret (OAuth 2.0 Client Credentials).
                        """);
    }
}
