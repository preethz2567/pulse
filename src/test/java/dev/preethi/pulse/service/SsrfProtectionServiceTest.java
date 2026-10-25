package dev.preethi.pulse.service;

import dev.preethi.pulse.exception.SsrfProtectionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SsrfProtectionServiceTest {

    private SsrfProtectionService ssrfService;

    @BeforeEach
    void setUp() {
        ssrfService = new SsrfProtectionService();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost:8080",
            "http://sub.localhost:3000",
            "http://127.0.0.1:80",
            "http://127.0.0.2:8080",
            "http://0.0.0.0:80",
            "http://10.0.0.1/status",
            "http://10.254.0.1",
            "http://172.16.0.1/api",
            "http://172.31.255.255",
            "http://192.168.0.1",
            "http://192.168.1.254",
            "http://169.254.169.254/latest/meta-data/",
            "http://100.64.0.1",
            "http://192.0.2.1",
            "http://198.51.100.1",
            "http://203.0.113.1",
            "http://[::1]:8080",
            "http://[fe80::1]",
            "http://[fc00::1]",
            "ftp://example.com/file",
            "file:///etc/passwd",
            "gopher://example.com"
    })
    void validateUrl_withPrivateOrInternalDestinations_throwsSsrfProtectionException(String url) {
        assertThrows(SsrfProtectionException.class, () -> ssrfService.validateUrl(url));
    }

    @Test
    void validateUrl_withNullOrBlank_throwsException() {
        assertThrows(SsrfProtectionException.class, () -> ssrfService.validateUrl(null));
        assertThrows(SsrfProtectionException.class, () -> ssrfService.validateUrl("   "));
    }

    @Test
    void validateAddress_withPublicAddresses_doesNotThrow() throws UnknownHostException {
        InetAddress cloudflareDns = InetAddress.getByName("1.1.1.1");
        assertDoesNotThrow(() -> ssrfService.validateAddress(cloudflareDns));

        InetAddress googleDns = InetAddress.getByName("8.8.8.8");
        assertDoesNotThrow(() -> ssrfService.validateAddress(googleDns));
    }
}
