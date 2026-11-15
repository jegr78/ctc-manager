package org.ctc.integration;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;
import org.ctc.domain.service.ImageContent;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Downloads images from the GT7 website only: one allowed host over HTTPS, resolved addresses outside private ranges,
 * no redirects, bounded time and size, and file content that matches the expected image type.
 */
@Slf4j
@Component
public class ImageDownloadClient {
    public static final long MAX_BYTES = 15L * 1024 * 1024;
    static final Policy GT7 = new Policy("https", Set.of("www.gran-turismo.com"), 443, ImageDownloadClient::publicAddress, InetAddress::getAllByName);
    private final Policy policy;
    private final RestClient client;

    public ImageDownloadClient() {
        this(GT7, Duration.ofSeconds(5), Duration.ofSeconds(20));
    }

    ImageDownloadClient(Policy policy, Duration connectTimeout, Duration readTimeout) {
        this.policy = policy;
        var requestFactory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER).connectTimeout(connectTimeout).build());
        requestFactory.setReadTimeout(readTimeout);
        this.client = RestClient.builder().requestFactory(requestFactory).build();
    }

    /** Which URLs may be fetched; tests replace it to reach a local stub. */
    record Policy(String scheme, Set<String> hosts, int port, Predicate<InetAddress> address, Resolver resolver) { }

    @FunctionalInterface
    interface Resolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    // CodeQL FP: java/ssrf — host allowlist plus resolved-address check in validate() precede the request; see docs/security/sast-acceptance.md
    public byte[] download(String url, ImageContent.Format expected) throws IOException {
        URI uri = validate(url);
        byte[] data;
        try {
            data = fetch(uri);
        } catch (RestClientException failure) {
            if (failure.getCause() instanceof IOException cause) throw cause;
            throw new IOException("Image download failed", failure);
        }
        if (data == null || !expected.matches(data)) throw new IOException("Downloaded file is not a " + expected + " image");
        return data;
    }

    private byte[] fetch(URI uri) {
        return client.get().uri(uri).exchange((request, response) -> {
            int status = response.getStatusCode().value();
            if (response.getStatusCode().is3xxRedirection()) throw new IOException("Image download refused a redirect (HTTP " + status + ")");
            if (!response.getStatusCode().is2xxSuccessful()) throw new IOException("Image download failed with HTTP " + status);
            long length = response.getHeaders().getContentLength();
            if (length > MAX_BYTES) throw new IOException("Image exceeds " + MAX_BYTES + " bytes");
            return readBounded(response.getBody());
        }, true);
    }

    private URI validate(String url) throws UnknownHostException {
        URI uri;
        try {
            uri = URI.create(url == null ? "" : url.strip());
        } catch (IllegalArgumentException malformed) {
            throw new IllegalArgumentException("Malformed image URL");
        }
        if (uri.getScheme() == null || !policy.scheme().equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Only " + policy.scheme().toUpperCase(Locale.ROOT) + " URLs allowed: " + url);
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!policy.hosts().contains(host) || uri.getUserInfo() != null || (policy.port() != -1 && uri.getPort() != -1 && uri.getPort() != policy.port())) {
            log.warn("Rejected image host: {}", host);
            throw new IllegalArgumentException("URL host not allowed: " + host);
        }
        var addresses = policy.resolver().resolve(host);
        if (addresses.length == 0 || !Arrays.stream(addresses).allMatch(policy.address())) {
            log.warn("Rejected image host {} resolving to a blocked address", host);
            throw new IllegalArgumentException("URL host resolves to a blocked address: " + host);
        }
        return uri;
    }

    private static byte[] readBounded(InputStream body) throws IOException {
        byte[] data = body.readNBytes((int) MAX_BYTES + 1);
        if (data.length > MAX_BYTES) throw new IOException("Image exceeds " + MAX_BYTES + " bytes");
        return data;
    }

    /**
     * False for loopback, private, carrier-grade NAT, link-local, multicast, unspecified, benchmark and reserved
     * addresses, and for IPv6 prefixes that embed an IPv4 address (NAT64, 6to4, Teredo).
     */
    static boolean publicAddress(InetAddress address) {
        if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()
                || address.isMulticastAddress() || address.isAnyLocalAddress()) return false;
        byte[] raw = address.getAddress();
        if (raw.length == 4) {
            int first = raw[0] & 0xFF;
            int second = raw[1] & 0xFF;
            return first != 0 && first < 240 && !(first == 100 && (second & 0xC0) == 64) && !(first == 198 && (second & 0xFE) == 18);
        }
        boolean uniqueLocal = (raw[0] & 0xFE) == 0xFC;
        boolean nat64 = raw[0] == 0x00 && raw[1] == 0x64 && (raw[2] & 0xFF) == 0xFF && (raw[3] & 0xFF) == 0x9B;
        boolean sixToFour = raw[0] == 0x20 && raw[1] == 0x02;
        boolean teredo = raw[0] == 0x20 && raw[1] == 0x01 && raw[2] == 0x00 && raw[3] == 0x00;
        return !(uniqueLocal || nat64 || sixToFour || teredo);
    }
}
