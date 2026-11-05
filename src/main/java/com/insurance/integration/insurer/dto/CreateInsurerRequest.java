package com.insurance.integration.insurer.dto;

import com.insurance.integration.common.enums.InsurerType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(description = "Request to register a new insurance provider")
public class CreateInsurerRequest {

    @NotBlank(message = "Insurer code is required")
    @Pattern(regexp = "^[A-Z0-9_]{3,50}$",
             message = "Insurer code must be 3-50 uppercase letters, digits or underscores")
    @Schema(description = "Unique business code for this insurer", example = "MAX_LIFE")
    private String insurerCode;

    @NotBlank(message = "Insurer name is required")
    @Size(min = 2, max = 200)
    @Schema(description = "Full legal name", example = "Max Life Insurance Company Limited")
    private String insurerName;

    @NotNull(message = "Insurer type is required")
    @Schema(description = "Type of insurance offered", example = "LIFE")
    private InsurerType insurerType;

    @Email(message = "Contact email must be valid")
    @Schema(description = "Integration contact email", example = "integrations@maxlife.in")
    private String contactEmail;

    @Size(max = 200, message = "Bucket name must not exceed 200 characters")
    @Schema(description = "Destination S3 bucket name in insurer's AWS account",
            example = "max-life-inbound-documents")
    private String destinationBucketName;

    @Size(max = 50)
    @Schema(description = "AWS region of the destination bucket", example = "ap-south-1")
    private String destinationBucketRegion;

    @Size(max = 500)
    @Schema(description = "IAM role ARN for cross-account S3 access",
            example = "arn:aws:iam::123456789012:role/InsuranceIntegrationRole")
    private String destinationIamRoleArn;

    @Size(max = 200)
    @Schema(description = "S3 key prefix for uploaded documents", example = "inbound/middleware/")
    private String destinationPathPrefix;

    @Size(max = 500)
    @Schema(description = "SNS topic ARN for event notifications")
    private String snsTopicArn;

    @Size(max = 500)
    @Schema(description = "Webhook URL for HTTP POST notifications")
    private String webhookUrl;

    @Size(max = 500)
    @Schema(description = "HMAC secret for webhook signature verification (stored securely)")
    private String webhookSecret;
}
