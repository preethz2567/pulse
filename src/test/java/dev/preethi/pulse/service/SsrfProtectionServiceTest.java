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
            "http://0.0.0.1:80",
            "http://0.254.1.1:80",
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
            "http://[::]:8080",
            "http://[::1]:8080",
            "http://[::ffff:127.0.0.1]",
            "http://[::ffff:169.254.169.254]",
            "http://[::127.0.0.1]",
            "http://[::10.0.0.1]",
            "http://[64:ff9b::127.0.0.1]",
            "http://[64:ff9b::169.254.169.254]",
            "http://[2002:7f00:0001::]",
            "http://[2002:a9fe:a9fe::]",
            "http://[fe80::1]",
            "http://[fc00::1]",
            "http://[fd00:ec2::254]",
            "http://[ff02::1]",
            "http://[2001:db8::1]",
            "http://[100::1]",
            "http://[::ffff:127.0.0.1]:8080/path",
            "http://[::ffff:7f00:1]:80/",
            "http://2130706433",
            "http://0x7f.1",
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

    @ParameterizedTest
    @ValueSource(strings = {
            "0.0.0.0",
            "0.0.0.1",
            "0.1.2.3",
            "0.255.255.255",
            "127.0.0.1",
            "127.0.0.2",
            "10.0.0.1",
            "10.254.0.1",
            "172.16.0.1",
            "172.31.255.255",
            "192.168.0.1",
            "192.168.1.1",
            "169.254.169.254",
            "100.64.0.1",
            "100.100.100.200",
            "192.0.2.1",
            "198.51.100.1",
            "203.0.113.1",
            "224.0.0.1",
            "240.0.0.1",
            "255.255.255.255",
            "::",
            "::1",
            "::ffff:127.0.0.1",
            "::ffff:10.0.0.1",
            "::ffff:169.254.169.254",
            "::127.0.0.1",
            "::10.0.0.1",
            "::169.254.169.254",
            "64:ff9b::127.0.0.1",
            "64:ff9b::10.0.0.1",
            "64:ff9b::169.254.169.254",
            "2002:7f00:0001::",
            "2002:0a00:0001::",
            "2002:a9fe:a9fe::",
            "2001:0000:7f00:0001::",
            "2001:0000:0808:0808:0000:0000:f5ff:fffe", // Teredo client: 10.0.0.1
            "2001:0000:0808:0808:0000:0000:80ff:fffe", // Teredo client: 127.0.0.1
            "2001:0000:0808:0808:0000:0000:3f57:fefe", // Teredo client: 192.168.1.1
            "2001:0000:0808:0808:0000:0000:5601:5601", // Teredo client: 169.254.169.254
            "::ffff:7f00:1",
            "2130706433",
            "fe80::1%1",
            "2001:db8::1",
            "100::1",
            "fc00::1",
            "fd00::1",
            "fd00:ec2::254",
            "fe80::1",
            "ff02::1"
    })
    void validateAddress_withProhibitedAddresses_throwsSsrfProtectionException(String ip) throws UnknownHostException {
        InetAddress address = InetAddress.getByName(ip);
        assertThrows(SsrfProtectionException.class, () -> ssrfService.validateAddress(address));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "1.1.1.1",
            "8.8.8.8",
            "93.184.216.34",
            "2606:4700:4700::1111",
            "2001:4860:4860::8888",
            "::ffff:8.8.8.8",
            "::ffff:93.184.216.34",
            "::8.8.8.8",
            "::93.184.216.34",
            "64:ff9b::8.8.8.8",
            "64:ff9b::93.184.216.34",
            "2002:0808:0808::",
            "2002:5db8:d822::",
            "2001:0000:0808:0808:0000:0000:f7f7:f7f7" // Teredo public server (8.8.8.8) and public client (8.8.8.8)
    })
    void validateAddress_withPublicAddresses_doesNotThrow(String ip) throws UnknownHostException {
        InetAddress address = InetAddress.getByName(ip);
        assertDoesNotThrow(() -> ssrfService.validateAddress(address));
    }
}
