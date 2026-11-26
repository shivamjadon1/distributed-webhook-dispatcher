package com.web3task.webhook.security;

import com.web3task.webhook.exception.SSRFSecurityException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class SafeUrlValidatorTest {

    private final SafeUrlValidator strictValidator = new SafeUrlValidator(false, false);
    private final SafeUrlValidator testValidator = new SafeUrlValidator(true, true);

    @ParameterizedTest
    @ValueSource(strings = {
            "http://127.0.0.1:8080/webhook",
            "http://localhost:8080/callback",
            "http://169.254.169.254/latest/meta-data/",
            "http://metadata.google.internal/computeMetadata/v1/",
            "http://10.0.0.1/admin",
            "http://172.16.0.1/internal",
            "http://192.168.1.1/router",
            "ftp://example.com/file",
            "file:///etc/passwd"
    })
    @DisplayName("SSRF Attack Vectors must be blocked by strict validator")
    void testDisallowedUrlsBlocked(String blockedUrl) {
        assertThrows(SSRFSecurityException.class, () -> strictValidator.validateUrl(blockedUrl));
    }

    @Test
    @DisplayName("Public HTTPS URL should be accepted")
    void testValidPublicUrl() {
        assertDoesNotThrow(() -> strictValidator.validateUrl("https://httpbin.org/post"));
    }

    @Test
    @DisplayName("Test validator allows loopback when explicitly configured for test suite")
    void testLoopbackAllowedWhenConfigured() {
        assertDoesNotThrow(() -> testValidator.validateUrl("http://localhost:8080/test"));
    }
}
