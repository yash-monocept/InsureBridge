package com.insurance.integration.config;

import com.insurance.integration.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public health check endpoint for load balancers, monitoring tools,
 * and API gateway health probes.
 *
 * This endpoint is intentionally unauthenticated (listed in SecurityConfig PUBLIC_PATHS)
 * and returns a minimal status payload. For detailed health information, use the
 * Spring Actuator endpoint at /actuator/health (authorized access only).
 */
@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health", description = "Public health check endpoint")
@Slf4j
public class HealthController {

    /**
     * Returns a simple status payload confirming the application is running.
     *
     * @return 200 OK with status UP, service name, and version.
     */
    @GetMapping
    @Operation(
            summary = "Application health check",
            description = "Public endpoint returning application liveness status. " +
                          "For detailed health including database and AWS connectivity, " +
                          "use /actuator/health (requires authorization)."
    )
    public ResponseEntity<ApiResponse<Map<String, String>>> health() {
        Map<String, String> healthData = new LinkedHashMap<>();
        healthData.put("status", "UP");
        healthData.put("service", "insurance-integration-middleware");
        healthData.put("version", "1.0.0");

        return ResponseEntity.ok(ApiResponse.success("Application is running", healthData));
    }
}
