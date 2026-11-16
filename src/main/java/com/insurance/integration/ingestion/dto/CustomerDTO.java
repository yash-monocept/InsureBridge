package com.insurance.integration.ingestion.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

/**
 * Customer information within an ingestion request (Module 3).
 */
@Data
@Builder
@Schema(description = "Customer information")
public class CustomerDTO {

    @NotBlank(message = "Customer name is required")
    @Size(max = 200, message = "Customer name must not exceed 200 characters")
    @Schema(description = "Customer full name", example = "Rahul Sharma")
    private String name;

    @NotBlank(message = "Customer mobile is required")
    @Pattern(regexp = "^[0-9]{10,15}$", message = "Customer mobile must be 10-15 digits")
    @Size(max = 15, message = "Customer mobile must not exceed 15 characters")
    @Schema(description = "Customer mobile number", example = "9999999999")
    private String mobile;

    @NotBlank(message = "Customer email is required")
    @Email(message = "Customer email must be a valid email address")
    @Size(max = 200, message = "Customer email must not exceed 200 characters")
    @Schema(description = "Customer email address", example = "rahul@example.com")
    private String email;
}
