package com.insurance.integration.unit.security;

import com.insurance.integration.common.exception.SsrfAttemptException;
import com.insurance.integration.security.validator.SsrfValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Module 2 — SsrfValidator")
class SsrfValidatorTest {

    private final SsrfValidator validator = new SsrfValidator();

    private static final String APPROVED = "[\"*.s3.amazonaws.com\",\"docs.axisbank.example\"]";

    @Test
    @DisplayName("accepts a URL whose host matches an approved wildcard")
    void acceptsApprovedWildcard() {
        assertThatCode(() -> validator.validate(
                "https://axis-bank-docs.s3.amazonaws.com/policy.pdf", APPROVED))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("accepts a URL whose host matches an exact approved host")
    void acceptsExactHost() {
        assertThatCode(() -> validator.validate(
                "https://docs.axisbank.example/policy.pdf", APPROVED))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("wildcard matches exactly one DNS label")
    void wildcardMatchesOneLabel() {
        assertThat(validator.matches("bucket.s3.amazonaws.com", "*.s3.amazonaws.com")).isTrue();
        assertThat(validator.matches("s3.amazonaws.com", "*.s3.amazonaws.com")).isFalse();
        assertThat(validator.matches("evil.bucket.s3.amazonaws.com", "*.s3.amazonaws.com")).isFalse();
    }

    @Test
    @DisplayName("rejects a host that is not approved")
    void rejectsUnapprovedHost() {
        assertThatThrownBy(() -> validator.validate(
                "https://evil.example/policy.pdf", APPROVED))
                .isInstanceOf(SsrfAttemptException.class)
                .hasMessageNotContaining("evil.example");
    }

    @Test
    @DisplayName("rejects internal/private targets even when a wildcard matches")
    void rejectsInternalTargets() {
        assertThat(validator.isInternalHost("169.254.169.254")).isTrue();
        assertThat(validator.isInternalHost("localhost")).isTrue();
        assertThat(validator.isInternalHost("127.0.0.1")).isTrue();
        assertThat(validator.isInternalHost("10.1.2.3")).isTrue();
        assertThat(validator.isInternalHost("192.168.1.1")).isTrue();
        assertThat(validator.isInternalHost("172.16.0.9")).isTrue();
        assertThat(validator.isInternalHost("metadata.google.internal")).isTrue();

        assertThatThrownBy(() -> validator.validate(
                "https://169.254.169.254/latest/meta-data/", "[\"*\"]"))
                .isInstanceOf(SsrfAttemptException.class);
    }

    @Test
    @DisplayName("rejects non-HTTP schemes, userinfo and non-standard ports")
    void rejectsDangerousUrlShapes() {
        assertThatThrownBy(() -> validator.validate("file:///etc/passwd", APPROVED))
                .isInstanceOf(SsrfAttemptException.class);
        assertThatThrownBy(() -> validator.validate(
                "https://user:pass@docs.axisbank.example/policy.pdf", APPROVED))
                .isInstanceOf(SsrfAttemptException.class);
        assertThatThrownBy(() -> validator.validate(
                "https://docs.axisbank.example:8080/policy.pdf", APPROVED))
                .isInstanceOf(SsrfAttemptException.class);
    }

    @Test
    @DisplayName("denies when the partner has no approved hosts configured")
    void deniesWithoutApprovedHosts() {
        assertThatThrownBy(() -> validator.validate(
                "https://axis-bank-docs.s3.amazonaws.com/policy.pdf", null))
                .isInstanceOf(SsrfAttemptException.class);
        assertThat(validator.isAllowed(
                "https://axis-bank-docs.s3.amazonaws.com/policy.pdf", "[]")).isFalse();
    }
}
