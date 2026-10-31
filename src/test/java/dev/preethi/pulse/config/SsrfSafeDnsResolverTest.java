package dev.preethi.pulse.config;

import dev.preethi.pulse.service.SsrfProtectionService;
import org.apache.hc.client5.http.DnsResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SsrfSafeDnsResolverTest {

    private SsrfProtectionService ssrfProtectionService;
    private DnsResolver mockDelegate;
    private SsrfSafeDnsResolver resolver;

    @BeforeEach
    void setUp() {
        ssrfProtectionService = new SsrfProtectionService();
        mockDelegate = mock(DnsResolver.class);
        resolver = new SsrfSafeDnsResolver(ssrfProtectionService, mockDelegate);
    }

    @Test
    void resolve_publicOnlyDnsAnswers_acceptedAndReturned() throws Exception {
        InetAddress publicIp1 = InetAddress.getByName("93.184.216.34");
        InetAddress publicIp2 = InetAddress.getByName("104.244.42.1");
        when(mockDelegate.resolve("example.com")).thenReturn(new InetAddress[]{publicIp1, publicIp2});

        InetAddress[] result = resolver.resolve("example.com");

        assertNotNull(result);
        assertEquals(2, result.length);
        assertArrayEquals(new InetAddress[]{publicIp1, publicIp2}, result);
    }

    @Test
    void resolve_mixedPublicAndProhibitedAnswers_failsClosedAndThrowsException() throws Exception {
        InetAddress publicIp = InetAddress.getByName("93.184.216.34");
        InetAddress loopbackIp = InetAddress.getByName("127.0.0.1");
        when(mockDelegate.resolve("rebind.example.com")).thenReturn(new InetAddress[]{publicIp, loopbackIp});

        UnknownHostException ex = assertThrows(UnknownHostException.class, () -> resolver.resolve("rebind.example.com"));
        assertTrue(ex.getMessage().contains("SSRF Protection"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "127.0.0.1",
            "127.0.0.2",
            "0.0.0.0",
            "0.0.0.1",
            "10.0.0.1",
            "10.254.0.1",
            "172.16.0.1",
            "172.31.255.255",
            "192.168.0.1",
            "192.168.1.1",
            "169.254.169.254", // AWS/GCP/Azure instance metadata
            "100.64.0.1",      // Carrier-grade NAT
            "192.0.2.1",       // TEST-NET-1
            "198.51.100.1",    // TEST-NET-2
            "203.0.113.1",     // TEST-NET-3
            "240.0.0.1"        // Reserved / Broadcast
    })
    void resolve_prohibitedIpv4Addresses_rejected(String ip) throws Exception {
        InetAddress prohibitedAddress = InetAddress.getByName(ip);
        when(mockDelegate.resolve("target.internal")).thenReturn(new InetAddress[]{prohibitedAddress});

        UnknownHostException ex = assertThrows(UnknownHostException.class, () -> resolver.resolve("target.internal"));
        assertTrue(ex.getMessage().contains("SSRF Protection"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "::",              // IPv6 Unspecified
            "::1",             // IPv6 Loopback
            "fe80::1",         // IPv6 Link-Local
            "fc00::1",         // IPv6 Unique Local Address (ULA)
            "fd00::1",         // IPv6 ULA
            "::ffff:127.0.0.1",// IPv4-mapped IPv6 loopback
            "::ffff:10.0.0.1", // IPv4-mapped IPv6 private
            "::ffff:169.254.169.254", // IPv4-mapped IPv6 metadata
            "::127.0.0.1",     // IPv4-compatible IPv6 loopback
            "::10.0.0.1",      // IPv4-compatible IPv6 private
            "64:ff9b::127.0.0.1",     // NAT64 loopback
            "64:ff9b::169.254.169.254", // NAT64 metadata
            "2002:7f00:0001::",       // 6to4 loopback (127.0.0.1)
            "2002:a9fe:a9fe::",       // 6to4 metadata (169.254.169.254)
            "2001:0000:7f00:0001::",  // Teredo server loopback (127.0.0.1)
            "2001:0000:0808:0808:0000:0000:f5ff:fffe", // Teredo client private (10.0.0.1)
            "2001:0000:0808:0808:0000:0000:80ff:fffe", // Teredo client loopback (127.0.0.1)
            "2001:db8::1",            // IPv6 documentation
            "100::1",                 // IPv6 discard
            "ff02::1"                 // IPv6 multicast
    })
    void resolve_prohibitedIpv6Addresses_rejected(String ip) throws Exception {
        InetAddress prohibitedAddress = InetAddress.getByName(ip);
        when(mockDelegate.resolve("target.internal")).thenReturn(new InetAddress[]{prohibitedAddress});

        UnknownHostException ex = assertThrows(UnknownHostException.class, () -> resolver.resolve("target.internal"));
        assertTrue(ex.getMessage().contains("SSRF Protection"));
    }

    @Test
    void resolve_localhostAndSubLocalhost_rejectedWithoutDnsLookup() {
        assertThrows(UnknownHostException.class, () -> resolver.resolve("localhost"));
        assertThrows(UnknownHostException.class, () -> resolver.resolve("app.localhost"));
        assertThrows(UnknownHostException.class, () -> resolver.resolve("sub.test.localhost"));
    }

    @Test
    void resolve_nullOrBlankHost_rejected() {
        assertThrows(UnknownHostException.class, () -> resolver.resolve(null));
        assertThrows(UnknownHostException.class, () -> resolver.resolve(""));
        assertThrows(UnknownHostException.class, () -> resolver.resolve("   "));
    }

    @Test
    void resolveCanonicalHostname_returnsOriginalHostWithoutReverseLookup() throws Exception {
        String host = "api.example.com";
        assertEquals("api.example.com", resolver.resolveCanonicalHostname(host));
    }
}
