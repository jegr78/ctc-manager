package org.ctc.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.ctc.domain.service.ImageContent.Format;
import org.ctc.integration.ImageDownloadClient.Policy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ImageDownloadClientTest {
    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10, 0, 0, 0, 13};
    static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 16};
    HttpServer server;
    String base;
    final AtomicInteger requests = new AtomicInteger();
    final ImageDownloadClient stub = new ImageDownloadClient(
            new Policy("http", Set.of("127.0.0.1"), -1, address -> true, InetAddress::getAllByName), Duration.ofSeconds(2), Duration.ofMillis(500));

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            String path = exchange.getRequestURI().getPath();
            switch (path) {
                case "/car.png" -> respond(exchange, 200, PNG);
                case "/car.jpg" -> respond(exchange, 200, JPEG);
                case "/redirect.png" -> {
                    exchange.getResponseHeaders().add("Location", base + "/car.png");
                    respond(exchange, 302, new byte[0]);
                }
                case "/large.png" -> respond(exchange, 200, Arrays.copyOf(PNG, (int) ImageDownloadClient.MAX_BYTES + 1));
                case "/streamed.png" -> {
                    exchange.sendResponseHeaders(200, 0);
                    try (var out = exchange.getResponseBody()) {
                        out.write(PNG);
                        out.write(new byte[(int) ImageDownloadClient.MAX_BYTES]);
                    }
                }
                case "/slow.png" -> {
                    try {
                        Thread.sleep(2_000);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                    }
                    respond(exchange, 200, PNG);
                }
                default -> respond(exchange, 404, new byte[0]);
            }
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void givenPngAndJpeg_whenDownloading_thenReturnTheBytes() throws IOException {
        // when / then
        assertThat(stub.download(base + "/car.png", Format.PNG)).isEqualTo(PNG);
        assertThat(stub.download(base + "/car.jpg", Format.JPEG)).isEqualTo(JPEG);
    }

    @Test
    void givenRedirect_whenDownloading_thenRefuseToFollowIt() {
        // when / then
        assertThatThrownBy(() -> stub.download(base + "/redirect.png", Format.PNG))
                .isInstanceOf(IOException.class).hasMessageContaining("refused a redirect (HTTP 302)");
        assertThat(requests).as("the redirect target is never requested").hasValue(1);
    }

    @Test
    void givenDeclaredOrStreamedOversizeBody_whenDownloading_thenRejectIt() {
        // when / then
        assertThatThrownBy(() -> stub.download(base + "/large.png", Format.PNG))
                .isInstanceOf(IOException.class).hasMessageContaining("exceeds");
        assertThatThrownBy(() -> stub.download(base + "/streamed.png", Format.PNG))
                .isInstanceOf(IOException.class).hasMessageContaining("exceeds");
    }

    @Test
    void givenContentOfAnotherType_whenDownloading_thenRejectIt() {
        // when / then
        assertThatThrownBy(() -> stub.download(base + "/car.jpg", Format.PNG))
                .isInstanceOf(IOException.class).hasMessage("Downloaded file is not a PNG image");
    }

    @Test
    void givenMissingImageOrSlowServer_whenDownloading_thenFailWithAnIoException() {
        // when / then
        assertThatThrownBy(() -> stub.download(base + "/missing.png", Format.PNG))
                .isInstanceOf(IOException.class).hasMessage("Image download failed with HTTP 404");
        assertThatThrownBy(() -> stub.download(base + "/slow.png", Format.PNG))
                .as("the read timeout ends a stalled download").isInstanceOf(IOException.class);
    }

    @Test
    void givenProductionPolicy_whenUrlLeavesTheGt7HostOrHttps_thenRejectBeforeAnyRequest() {
        // given
        var production = new ImageDownloadClient();
        // when / then
        assertThatThrownBy(() -> production.download("http://www.gran-turismo.com/a.png", Format.PNG))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Only HTTPS URLs allowed");
        assertThatThrownBy(() -> production.download("https://example.com/a.png", Format.PNG))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("URL host not allowed: example.com");
        assertThatThrownBy(() -> production.download("https://user@www.gran-turismo.com/a.png", Format.PNG))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("URL host not allowed");
        assertThatThrownBy(() -> production.download("https://www.gran-turismo.com:8443/a.png", Format.PNG))
                .as("only the default HTTPS port is allowed").isInstanceOf(IllegalArgumentException.class).hasMessageContaining("URL host not allowed");
        assertThatThrownBy(() -> production.download(base + "/car.png", Format.PNG))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(requests).hasValue(0);
    }

    @Test
    void givenAllowedHostResolvingToAPrivateAddress_whenDownloading_thenRejectIt() {
        // given
        var rebound = new ImageDownloadClient(new Policy("https", Set.of("www.gran-turismo.com"), 443, ImageDownloadClient::publicAddress,
                host -> new InetAddress[] {InetAddress.getByName("93.184.216.34"), InetAddress.getByName("10.0.0.7")}),
                Duration.ofSeconds(1), Duration.ofSeconds(1));
        // when / then
        assertThatThrownBy(() -> rebound.download("https://www.gran-turismo.com/a.png", Format.PNG))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("URL host resolves to a blocked address: www.gran-turismo.com");
    }

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1", "10.1.2.3", "172.16.0.1", "192.168.1.1", "169.254.169.254", "100.64.0.1", "224.0.0.1",
            "0.0.0.0", "0.1.2.3", "198.18.0.1", "240.0.0.1", "::1", "fe80::1", "fd00::1", "ff02::1", "64:ff9b::a00:1", "2002:a00:1::1",
            "2001:0:a00:1::1"})
    void givenNonPublicAddress_whenChecking_thenRejectIt(String address) throws IOException {
        // when / then
        assertThat(ImageDownloadClient.publicAddress(InetAddress.getByName(address))).as(address).isFalse();
    }

    @Test
    void givenPublicAddress_whenChecking_thenAllowIt() throws IOException {
        // when / then
        assertThat(ImageDownloadClient.publicAddress(InetAddress.getByName("93.184.216.34"))).isTrue();
        assertThat(ImageDownloadClient.publicAddress(InetAddress.getByName("2606:2800:220:1::1"))).isTrue();
    }

    @Test
    void givenFilenames_whenDerivingTheExpectedType_thenMapTheExtension() {
        // when / then
        assertThat(Format.ofFilename("car1.PNG")).isEqualTo(Format.PNG);
        assertThat(Format.ofFilename("car1_2_01.jpg")).isEqualTo(Format.JPEG);
        assertThat(Format.ofFilename("x.webp")).isEqualTo(Format.WEBP);
        assertThatThrownBy(() -> Format.ofFilename("x.gif")).isInstanceOf(IllegalArgumentException.class);
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        try (var out = exchange.getResponseBody()) {
            out.write(body);
        }
    }
}
