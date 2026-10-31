package dev.preethi.pulse.config;

import dev.preethi.pulse.exception.SsrfProtectionException;
import dev.preethi.pulse.service.SsrfProtectionService;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

@Component
public class SsrfSafeDnsResolver implements DnsResolver {

    private static final Logger log = LoggerFactory.getLogger(SsrfSafeDnsResolver.class);

    private final SsrfProtectionService ssrfProtectionService;
    private final DnsResolver delegate;

    @org.springframework.beans.factory.annotation.Autowired
    public SsrfSafeDnsResolver(SsrfProtectionService ssrfProtectionService) {
        this(ssrfProtectionService, SystemDefaultDnsResolver.INSTANCE);
    }

    public SsrfSafeDnsResolver(SsrfProtectionService ssrfProtectionService, DnsResolver delegate) {
        this.ssrfProtectionService = ssrfProtectionService;
        this.delegate = delegate != null ? delegate : SystemDefaultDnsResolver.INSTANCE;
    }

    @Override
    public InetAddress[] resolve(String host) throws UnknownHostException {
        if (host == null || host.isBlank()) {
            throw new UnknownHostException("Host cannot be empty");
        }

        if (host.equalsIgnoreCase("localhost") || host.toLowerCase().endsWith(".localhost")) {
            throw new UnknownHostException("SSRF Protection: Access to localhost is prohibited: " + host);
        }

        InetAddress[] addresses = delegate.resolve(host);
        if (addresses == null || addresses.length == 0) {
            throw new UnknownHostException("No IP addresses found for host: " + host);
        }

        // Fail-closed validation: verify EVERY resolved address before returning to the connection manager.
        // If any address violates SSRF restrictions, fail the entire resolution immediately.
        for (InetAddress address : addresses) {
            try {
                ssrfProtectionService.validateAddress(address);
            } catch (SsrfProtectionException e) {
                log.warn("Blocked unsafe IP {} resolved for host {}: {}", address.getHostAddress(), host, e.getMessage());
                throw new UnknownHostException("SSRF Protection: " + e.getMessage());
            } catch (Exception e) {
                log.warn("Error validating IP {} resolved for host {}: {}", address.getHostAddress(), host, e.getMessage());
                throw new UnknownHostException("SSRF Protection: Address validation failed for " + address.getHostAddress());
            }
        }

        return addresses;
    }

    @Override
    public String resolveCanonicalHostname(String host) throws UnknownHostException {
        // Return host directly to prevent unnecessary reverse DNS lookups that might stall or leak info
        return host;
    }
}
