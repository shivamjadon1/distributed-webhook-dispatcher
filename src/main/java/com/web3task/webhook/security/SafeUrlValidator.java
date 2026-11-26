package com.web3task.webhook.security;

import com.web3task.webhook.exception.SSRFSecurityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class SafeUrlValidator {

    private static final Logger log = LoggerFactory.getLogger(SafeUrlValidator.class);

    private static final Set<String> BLOCKED_HOSTNAMES = Set.of(
            "localhost",
            "127.0.0.1",
            "::1",
            "metadata.google.internal",
            "metadata.google",
            "instance-data",
            "169.254.169.254",
            "100.100.100.200"
    );

    private final boolean allowLoopbackForTesting;
    private final boolean allowInsecureHttp;

    public SafeUrlValidator(
            @Value("${webhook.security.allow-loopback-for-testing:false}") boolean allowLoopbackForTesting,
            @Value("${webhook.security.allow-insecure-http:false}") boolean allowInsecureHttp) {
        this.allowLoopbackForTesting = allowLoopbackForTesting;
        this.allowInsecureHttp = allowInsecureHttp;
    }

    /**
     * Validates that destinationUrl is syntactically sound and does not target
     * private networks, loopback addresses, or cloud metadata services.
     * Returns the validated InetAddress for DNS pinning / rebinding defense.
     */
    public List<InetAddress> validateUrl(String urlString) {
        if (urlString == null || urlString.isBlank()) {
            throw new SSRFSecurityException("Destination URL must not be blank");
        }

        URI uri;
        try {
            uri = URI.create(urlString.trim());
        } catch (Exception e) {
            throw new SSRFSecurityException("Malformed destination URL: " + e.getMessage());
        }

        String scheme = uri.getScheme();
        if (scheme == null) {
            throw new SSRFSecurityException("Destination URL missing protocol scheme (http/https)");
        }

        scheme = scheme.toLowerCase(Locale.ROOT);
        if (!scheme.equals("https") && !scheme.equals("http")) {
            throw new SSRFSecurityException("Disallowed protocol '" + scheme + "'. Only HTTP and HTTPS are permitted.");
        }

        if (scheme.equals("http") && !allowInsecureHttp && !allowLoopbackForTesting) {
            throw new SSRFSecurityException("Insecure HTTP protocol is not permitted in production. Use HTTPS.");
        }

        if (uri.getUserInfo() != null) {
            throw new SSRFSecurityException("Destination URL must not include embedded user credentials");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new SSRFSecurityException("Destination URL host must not be empty");
        }

        String normalizedHost = host.toLowerCase(Locale.ROOT).trim();

        if (!allowLoopbackForTesting && BLOCKED_HOSTNAMES.contains(normalizedHost)) {
            throw new SSRFSecurityException("Access to host '" + host + "' is forbidden for security (SSRF protection).");
        }

        // Perform DNS resolution and validate all resolved IP addresses
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new SSRFSecurityException("Unable to resolve destination host: " + host);
        }

        if (addresses == null || addresses.length == 0) {
            throw new SSRFSecurityException("Destination host resolved to zero IP addresses: " + host);
        }

        for (InetAddress address : addresses) {
            if (!allowLoopbackForTesting && isDisallowedIpAddress(address)) {
                throw new SSRFSecurityException("Destination host '" + host + "' resolves to restricted IP: " 
                        + address.getHostAddress() + " (SSRF protection)");
            }
        }

        return Arrays.asList(addresses);
    }

    /**
     * Checks if IP belongs to private, loopback, link-local, cloud metadata, or reserved ranges.
     */
    public boolean isDisallowedIpAddress(InetAddress address) {
        if (address.isLoopbackAddress() || address.isAnyLocalAddress() || address.isMulticastAddress()) {
            return true;
        }

        if (address.isSiteLocalAddress() || address.isLinkLocalAddress()) {
            return true;
        }

        byte[] bytes = address.getAddress();

        // IPv4 checks
        if (bytes.length == 4) {
            int b0 = bytes[0] & 0xFF;
            int b1 = bytes[1] & 0xFF;

            // 127.0.0.0/8 (Loopback)
            if (b0 == 127) return true;

            // 10.0.0.0/8 (Private RFC 1918)
            if (b0 == 10) return true;

            // 172.16.0.0/12 (Private RFC 1918)
            if (b0 == 172 && (b1 >= 16 && b1 <= 31)) return true;

            // 192.168.0.0/16 (Private RFC 1918)
            if (b0 == 192 && b1 == 168) return true;

            // 169.254.0.0/16 (Link Local / Cloud Metadata)
            if (b0 == 169 && b1 == 254) return true;

            // 100.64.0.0/10 (Carrier-Grade NAT)
            if (b0 == 100 && (b1 >= 64 && b1 <= 127)) return true;

            // 0.0.0.0/8 (Broadcast/Current network)
            if (b0 == 0) return true;

            // 240.0.0.0/4 (Reserved)
            if (b0 >= 240) return true;
        }

        // IPv6 checks
        if (address instanceof Inet6Address) {
            // fc00::/7 (Unique Local Address)
            int b0 = bytes[0] & 0xFF;
            if ((b0 & 0xFE) == 0xFC) return true;

            // fe80::/10 (Link Local)
            int b1 = bytes[1] & 0xFF;
            if (b0 == 0xFE && (b1 & 0xC0) == 0x80) return true;

            // IPv4-mapped IPv6 ::ffff:x.x.x.x
            boolean isV4Mapped = true;
            for (int i = 0; i < 10; i++) {
                if (bytes[i] != 0) {
                    isV4Mapped = false;
                    break;
                }
            }
            if (isV4Mapped && (bytes[10] & 0xFF) == 0xFF && (bytes[11] & 0xFF) == 0xFF) {
                byte[] v4Bytes = Arrays.copyOfRange(bytes, 12, 16);
                try {
                    return isDisallowedIpAddress(InetAddress.getByAddress(v4Bytes));
                } catch (UnknownHostException ignored) {
                    return true;
                }
            }
        }

        return false;
    }
}
