package com.insurance.integration.insurer.entity;

import com.insurance.integration.common.entity.BaseEntity;
import com.insurance.integration.common.enums.InsurerType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Insurance provider entity.
 *
 * Represents the destination organisations that receive document transfers from partners
 * via this middleware. Examples: Max Life Insurance, Care Health Insurance.
 *
 * SECURITY NOTE: The destination_iam_role_arn, webhook_secret, and bucket configuration
 * are server-side only and must NEVER be exposed to partners or included in any API response.
 */
@Entity
@Table(name = "insurers")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Insurer extends BaseEntity {

    /**
     * Short unique business code.
     * Example: MAX_LIFE, CARE_HEALTH, AXIS_MAX_LIFE
     */
    @Column(name = "insurer_code", nullable = false, unique = true, length = 50)
    private String insurerCode;

    @Column(name = "insurer_name", nullable = false, length = 200)
    private String insurerName;

    @Enumerated(EnumType.STRING)
    @Column(name = "insurer_type", nullable = false, length = 50)
    private InsurerType insurerType;

    @Column(name = "contact_email", length = 200)
    private String contactEmail;

    /**
     * Destination S3 bucket name in the insurer's AWS account.
     * NEVER exposed to partners. ALWAYS read from server-side config.
     */
    @Column(name = "destination_bucket_name", length = 200)
    private String destinationBucketName;

    @Column(name = "destination_bucket_region", length = 50)
    private String destinationBucketRegion;

    /**
     * IAM role ARN to assume for cross-account S3 write access.
     * NEVER exposed to partners.
     */
    @Column(name = "destination_iam_role_arn", length = 500)
    private String destinationIamRoleArn;

    /**
     * S3 key prefix for uploaded documents.
     * Example: inbound/middleware/
     */
    @Column(name = "destination_path_prefix", length = 200)
    private String destinationPathPrefix;

    /** SNS topic ARN to publish events to when transfer completes. */
    @Column(name = "sns_topic_arn", length = 500)
    private String snsTopicArn;

    /** Webhook URL for HTTP POST notification on transfer completion. */
    @Column(name = "webhook_url", length = 500)
    private String webhookUrl;

    /**
     * HMAC secret for signing webhook payloads.
     * Stored encrypted. NEVER returned in API responses.
     */
    @Column(name = "webhook_secret", length = 500)
    private String webhookSecret;

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;
}
