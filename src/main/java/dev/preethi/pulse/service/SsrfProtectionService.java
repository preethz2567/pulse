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

            // Current network: 0.0.0.0/8 (RFC 1122)
            if (b0 == 0) {
                throw new SsrfProtectionException("Access to current network (0.0.0.0/8) address is prohibited: " + address.getHostAddress());
            }

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
            int b0 = bytes[0] & 0xFF;
            int b1 = bytes[1] & 0xFF;

            // IPv6 Unique Local Address: fc00::/7 (fc00:: - fdff::)
            if ((b0 & 0xFE) == 0xFC) {
                throw new SsrfProtectionException("Access to IPv6 unique local address is prohibited: " + address.getHostAddress());
            }

            // IPv6 Documentation prefix: 2001:db8::/32 (RFC 3849)
            if (b0 == 0x20 && b1 == 0x01 && (bytes[2] & 0xFF) == 0x0D && (bytes[3] & 0xFF) == 0xB8) {
                throw new SsrfProtectionException("Access to IPv6 documentation address is prohibited: " + address.getHostAddress());
            }

            // IPv6 Discard prefix: 100::/64 (RFC 6666)
            if (b0 == 0x01 && b1 == 0x00 && isAllZeros(bytes, 2, 8)) {
                throw new SsrfProtectionException("Access to IPv6 discard-only address is prohibited: " + address.getHostAddress());
            }

            // IPv4-mapped IPv6 address: ::ffff:x.x.x.x (RFC 4291)
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

            // Deprecated IPv4-compatible IPv6 address: ::x.x.x.x (RFC 4291)
            // Preserves :: (unspecified, caught by isAnyLocalAddress) and ::1 (loopback, caught by isLoopbackAddress)
            if (isIPv4Compatible(bytes)) {
                try {
                    byte[] ipv4Bytes = new byte[4];
                    System.arraycopy(bytes, 12, ipv4Bytes, 0, 4);
                    InetAddress ipv4 = InetAddress.getByAddress(ipv4Bytes);
                    validateAddress(ipv4);
                } catch (UnknownHostException e) {
                    throw new SsrfProtectionException("Invalid IPv4-compatible IPv6 address: " + address.getHostAddress());
                }
            }

            // NAT64 well-known prefix: 64:ff9b::/96 (RFC 6052)
            // Validates the embedded IPv4 address in the lowest 32 bits against standard IPv4 rules
            if (isNat64WellKnown(bytes)) {
                try {
                    byte[] ipv4Bytes = new byte[4];
                    System.arraycopy(bytes, 12, ipv4Bytes, 0, 4);
                    InetAddress ipv4 = InetAddress.getByAddress(ipv4Bytes);
                    validateAddress(ipv4);
                } catch (UnknownHostException e) {
                    throw new SsrfProtectionException("Invalid NAT64 address: " + address.getHostAddress());
                }
            }

            // 6to4 tunneling address: 2002::/16 (RFC 3056)
            // Validates the embedded IPv4 address in octets 2-5 against standard IPv4 rules
            if (b0 == 0x20 && b1 == 0x02) {
                try {
                    byte[] ipv4Bytes = new byte[4];
                    System.arraycopy(bytes, 2, ipv4Bytes, 0, 4);
                    InetAddress ipv4 = InetAddress.getByAddress(ipv4Bytes);
                    validateAddress(ipv4);
                } catch (UnknownHostException e) {
                    throw new SsrfProtectionException("Invalid 6to4 address: " + address.getHostAddress());
                }
            }

            // Teredo tunneling address: 2001:0000::/32 (RFC 4380)
            // Validates both the Teredo server IPv4 (octets 4-7) and the client IPv4 (octets 12-15 XOR'd)
            if (b0 == 0x20 && b1 == 0x01 && bytes[2] == 0 && bytes[3] == 0) {
                try {
                    byte[] serverIpv4Bytes = new byte[4];
                    System.arraycopy(bytes, 4, serverIpv4Bytes, 0, 4);
                    InetAddress serverIpv4 = InetAddress.getByAddress(serverIpv4Bytes);
                    validateAddress(serverIpv4);

                    byte[] clientIpv4Bytes = new byte[4];
                    for (int i = 0; i < 4; i++) {
                        clientIpv4Bytes[i] = (byte) (~bytes[12 + i]);
                    }
                    InetAddress clientIpv4 = InetAddress.getByAddress(clientIpv4Bytes);
                    validateAddress(clientIpv4);
                } catch (UnknownHostException e) {
                    throw new SsrfProtectionException("Invalid Teredo address: " + address.getHostAddress());
                }
            }
        }
    }

    private boolean isAllZeros(byte[] bytes, int start, int end) {
        for (int i = start; i < end; i++) {
            if (bytes[i] != 0) return false;
        }
        return true;
    }

    private boolean isIPv4Mapped(byte[] bytes) {
        if (bytes.length != 16) return false;
        for (int i = 0; i < 10; i++) {
            if (bytes[i] != 0) return false;
        }
        return (bytes[10] & 0xFF) == 0xFF && (bytes[11] & 0xFF) == 0xFF;
    }

    private boolean isIPv4Compatible(byte[] bytes) {
        if (bytes.length != 16) return false;
        for (int i = 0; i < 12; i++) {
            if (bytes[i] != 0) return false;
        }
        // Exclude :: (all 16 zero bytes) and ::1 (bytes[15] == 1)
        boolean allRemainingZero = (bytes[12] == 0 && bytes[13] == 0 && bytes[14] == 0 && bytes[15] == 0);
        boolean isLoopback = (bytes[12] == 0 && bytes[13] == 0 && bytes[14] == 0 && bytes[15] == 1);
        return !allRemainingZero && !isLoopback;
    }

    private boolean isNat64WellKnown(byte[] bytes) {
        if (bytes.length != 16) return false;
        if ((bytes[0] & 0xFF) != 0x00 || (bytes[1] & 0xFF) != 0x64 ||
            (bytes[2] & 0xFF) != 0xFF || (bytes[3] & 0xFF) != 0x9B) {
            return false;
        }
        for (int i = 4; i < 12; i++) {
            if (bytes[i] != 0) return false;
        }
        return true;
    }
}
