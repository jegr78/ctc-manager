package org.ctc.backup.exception;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/**
 * Failsafe IT for SECU-04 — verifies that a multipart upload exceeding the 100 MB limit
 * (configured via {@code spring.servlet.multipart.max-request-size: 100MB} in Plan 01)
 * produces a clean Flash-redirect to {@code /admin/backup} with the locked D-02#1 string,
 * and that no Tomcat/Spring stack-trace markers are leaked in the response body.
 *
 * <p>Test 1 uses {@code WebEnvironment.RANDOM_PORT} with a real Tomcat instance because
 * {@code MockMvc} bypasses the Servlet-container multipart size enforcement — the
 * {@code MaxUploadSizeExceededException} is only thrown when the actual Tomcat multipart
 * resolver parses an oversized request body over a real HTTP connection.
 *
 * <p>Test 2 uses the injected {@code MockMvc} to verify that the advice is selective:
 * a small upload does NOT trigger the Flash-redirect interceptor.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@Tag("integration")
class BackupImportMultipartLimitIT {

    @LocalServerPort
    private int port;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void given101MBMockMultipartFile_whenPostImportPreview_thenRedirectsToBackupWithErrorFlash()
            throws Exception {
        // given — declared payload of 101 MB + 1 byte, strictly above the 100 MB limit from Plan 01
        long payloadLength = 101L * 1024 * 1024 + 1;
        String boundary = UUID.randomUUID().toString().replace("-", "");
        String CRLF = "\r\n";

        byte[] partHeader = ("--" + boundary + CRLF
                + "Content-Disposition: form-data; name=\"file\"; filename=\"oversized.zip\"" + CRLF
                + "Content-Type: application/zip" + CRLF
                + CRLF).getBytes(StandardCharsets.UTF_8);
        byte[] partFooter = (CRLF + "--" + boundary + "--" + CRLF).getBytes(StandardCharsets.UTF_8);
        // partFooter is never sent — it only contributes to the declared Content-Length.
        long contentLength = partHeader.length + payloadLength + partFooter.length;

        // when — raw socket streaming the body in chunks, stopping as soon as the early 302
        // arrives. HTTP clients keep writing the fixed-length body after the server has
        // rejected and closed the connection; on Windows the resulting TCP RST discards the
        // buffered response, so the redirect is only observable when the client stops writing.
        int statusCode;
        String location;
        String responseBody;
        try (Socket socket = new Socket("localhost", port)) {
            socket.setSoTimeout(120_000);
            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();
            out.write(("POST /admin/backup/import-preview HTTP/1.1" + CRLF
                    + "Host: localhost:" + port + CRLF
                    + "Content-Type: multipart/form-data; boundary=" + boundary + CRLF
                    + "Content-Length: " + contentLength + CRLF
                    + "Connection: close" + CRLF
                    + CRLF).getBytes(StandardCharsets.UTF_8));
            try {
                out.write(partHeader);

                byte[] chunk = new byte[64 * 1024];
                long remaining = payloadLength;
                while (remaining > 0 && in.available() == 0) {
                    int n = (int) Math.min(chunk.length, remaining);
                    out.write(chunk, 0, n);
                    remaining -= n;
                }
                out.flush();
            } catch (IOException earlyClose) {
                // The server rejected and closed while we were still writing; the response
                // may already be buffered locally — fall through and try to read it.
            }

            String head = readResponseHead(in);
            int statusLineEnd = head.indexOf(CRLF);
            if (statusLineEnd < 0) {
                throw new IOException("Truncated HTTP response head: \"" + head + "\"");
            }
            String statusLine = head.substring(0, statusLineEnd);
            Matcher status = Pattern.compile("^HTTP/\\S+ (\\d{3})").matcher(statusLine);
            if (!status.find()) {
                throw new IOException("Malformed HTTP status line: \"" + statusLine + "\"");
            }
            statusCode = Integer.parseInt(status.group(1));
            location = headerValue(head, "Location");
            String declaredLength = headerValue(head, "Content-Length");
            int bodyLength = declaredLength != null ? Integer.parseInt(declaredLength) : 0;
            byte[] body = in.readNBytes(bodyLength);
            if (body.length < bodyLength) {
                throw new IOException("Response body truncated: expected " + bodyLength
                        + " bytes, got " + body.length);
            }
            responseBody = new String(body, StandardCharsets.UTF_8);
        }

        // then — Tomcat enforces the 100 MB limit; BackupUploadExceptionHandler produces Flash-redirect
        assertThat(statusCode)
                .as("Oversized upload must produce a 3xx redirect, not HTTP %d", statusCode)
                .isBetween(300, 399);
        assertThat(location)
                .as("Redirect Location header must point to /admin/backup")
                .endsWith("/admin/backup");

        // Stack-trace leak guard — no Spring/Tomcat internal class names in the response body
        assertThat(responseBody)
                .doesNotContain("Servlet.service()")
                .doesNotContain("java.lang.Throwable")
                .doesNotContain("MaxUploadSizeExceededException");
    }

    private static String readResponseHead(InputStream in) throws IOException {
        var head = new ByteArrayOutputStream();
        int lastFour = 0;
        int b;
        while ((b = in.read()) != -1) {
            head.write(b);
            lastFour = (lastFour << 8) | b;
            if (lastFour == 0x0D0A0D0A) {
                break;
            }
        }
        return head.toString(StandardCharsets.UTF_8);
    }

    private static String headerValue(String head, String name) {
        return head.lines()
                .filter(line -> line.regionMatches(true, 0, name + ":", 0, name.length() + 1))
                .map(line -> line.substring(name.length() + 1).trim())
                .findFirst().orElse(null);
    }

    @Test
    void givenUploadOf1KB_whenPostImportPreview_thenHandlerDoesNotIntercept() throws Exception {
        // given
        MockMultipartFile file = new MockMultipartFile(
                "file", "ok.zip", "application/zip", new byte[1024]);

        // when
        MvcResult result = mockMvc.perform(
                        multipart("/admin/backup/import-preview")
                                .file(file)
                                .with(csrf()))
                .andReturn();

        // then — the advice must NOT intercept a small upload with the Upload-too-large flash
        assertThat(result.getFlashMap().get("errorMessage"))
                .isNotEqualTo("Upload too large — maximum is 100 MB.");
    }
}
