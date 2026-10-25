package dev.preethi.pulse.service;

import dev.preethi.pulse.exception.SsrfProtectionException;
import org.springframework.stereotype.Service;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

@Service
public class SsrfProtectionService {

    public void validateUrl(String urlString) {
        if (urlString == null || urlString.isBlank()) {
            throw new SsrfProtectionException("URL cannot be empty");
        }

        URI uri;
        try {
            uri = URI.create(urlString);
        } catch (IllegalArgumentException e) {
            throw new SsrfProtectionException("Invalid URL syntax: " + urlString);
        }

        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new SsrfProtectionException("Only HTTP and HTTPS protocols are permitted: " + scheme);
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new SsrfProtectionException("URL must contain a valid host: " + urlString);
        }

        if (host.equalsIgnoreCase("localhost") || host.toLowerCase().endsWith(".localhost")) {
            throw new SsrfProtectionException("Access to localhost is prohibited: " + host);
        }

        try {
            InetAddress[] addresses = resolveHost(host);
            for (InetAddress address : addresses) {
                validateAddress(address);
            }
        } catch (UnknownHostException e) {
            throw new SsrfProtectionException("Cannot resolve host: " + host);
        }
    }

    protected InetAddress[] resolveHost(String host) throws UnknownHostException {
        return InetAddress.getAllByName(host);
    }

    public void validateAddress(InetAddress address) {
        if (address.isLoopbackAddress()) {
            throw new SsrfProtectionException("Access to loopback address is prohibited: " + address.getHostAddress());
        }

        if (address.isAnyLocalAddress()) {
            throw new SsrfProtectionException("Access to wildcard local address is prohibited: " + address.getHostAddress());
        }

        if (address.isLinkLocalAddress()) {
            throw new SsrfProtectionException("Access to link-local address is prohibited: " + address.getHostAddress());
        }

        if (address.isSiteLocalAddress()) {
            throw new SsrfProtectionException("Access to private network address is prohibited: " + address.getHostAddress());
        }

        if (address.isMulticastAddress()) {
            throw new SsrfProtectionException("Access to multicast address is prohibited: " + address.getHostAddress());
        }

        byte[] bytes = address.getAddress();

        if (address instanceof Inet4Address) {
            int b0 = bytes[0] & 0xFF;
            int b1 = bytes[1] & 0xFF;

            // Carrier-grade NAT: 100.64.0.0/10 (100.64.0.0 - 100.127.255.255)
            if (b0 == 100 && (b1 >= 64 && b1 <= 127)) {
                throw new SsrfProtectionException("Access to carrier-grade NAT address is prohibited: " + address.getHostAddress());
            }

            // Benchmark testing: 198.18.0.0/15 (198.18.0.0 - 198.19.255.255)
            if (b0 == 198 && (b1 == 18 || b1 == 19)) {
                throw new SsrfProtectionException("Access to benchmark testing address is prohibited: " + address.getHostAddress());
            }

            // TEST-NET-1: 192.0.2.0/24
            if (b0 == 192 && b1 == 0 && (bytes[2] & 0xFF) == 2) {
                throw new SsrfProtectionException("Access to documentation address is prohibited: " + address.getHostAddress());
            }

            // TEST-NET-2: 198.51.100.0/24
            if (b0 == 198 && b1 == 51 && (bytes[2] & 0xFF) == 100) {
                throw new SsrfProtectionException("Access to documentation address is prohibited: " + address.getHostAddress());
            }

            // TEST-NET-3: 203.0.113.0/24
            if (b0 == 203 && b1 == 0 && (bytes[2] & 0xFF) == 113) {
                throw new SsrfProtectionException("Access to documentation address is prohibited: " + address.getHostAddress());
            }

            // Reserved / Broadcast: 240.0.0.0/4
            if (b0 >= 240) {
                throw new SsrfProtectionException("Access to reserved/broadcast address is prohibited: " + address.getHostAddress());
            }
        } else if (address instanceof Inet6Address) {
            // IPv6 Unique Local Address: fc00::/7 (fc00:: - fdff::)
            int b0 = bytes[0] & 0xFF;
            if ((b0 & 0xFE) == 0xFC) {
                throw new SsrfProtectionException("Access to IPv6 unique local address is prohibited: " + address.getHostAddress());
            }

            // IPv4-mapped IPv6 address (::ffff:x.x.x.x)
            if (isIPv4Mapped(bytes)) {
                try {
                    byte[] ipv4Bytes = new byte[4];
                    System.arraycopy(bytes, 12, ipv4Bytes, 0, 4);
                    InetAddress ipv4 = InetAddress.getByAddress(ipv4Bytes);
                    validateAddress(ipv4);
                } catch (UnknownHostException e) {
                    throw new SsrfProtectionException("Invalid IPv4-mapped IPv6 address: " + address.getHostAddress());
                }
            }
        }
    }

    private boolean isIPv4Mapped(byte[] bytes) {
        if (bytes.length != 16) return false;
        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) return false;
        }
        return (bytes[10] & 0xFF) == 0xFF && (bytes[11] & 0xFF) == 0xFF;
    }
}
