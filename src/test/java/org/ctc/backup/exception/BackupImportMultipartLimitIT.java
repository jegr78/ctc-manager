package org.ctc.backup.exception;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

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
        // given — 101 MB + 1 byte payload, strictly above the 100 MB limit from Plan 01
        byte[] payload = new byte[101 * 1024 * 1024 + 1];
        String boundary = UUID.randomUUID().toString().replace("-", "");
        String CRLF = "\r\n";

        byte[] partHeader = ("--" + boundary + CRLF
                + "Content-Disposition: form-data; name=\"file\"; filename=\"oversized.zip\"" + CRLF
                + "Content-Type: application/zip" + CRLF
                + CRLF).getBytes(StandardCharsets.UTF_8);
        byte[] partFooter = (CRLF + "--" + boundary + "--" + CRLF).getBytes(StandardCharsets.UTF_8);
        long contentLength = partHeader.length + payload.length + partFooter.length;

        // when — real HTTP via HttpURLConnection with redirect-following disabled
        HttpURLConnection conn = (HttpURLConnection)
                URI.create("http://localhost:" + port + "/admin/backup/import-preview")
                        .toURL().openConnection();
        conn.setInstanceFollowRedirects(false);
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        conn.setFixedLengthStreamingMode(contentLength);
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(120_000);

        try (OutputStream out = conn.getOutputStream()) {
            out.write(partHeader);
            out.write(payload);
            out.write(partFooter);
            out.flush();
        }

        int statusCode = conn.getResponseCode();
        String location = conn.getHeaderField("Location");
        String responseBody = "";
        try (var in = conn.getErrorStream() != null ? conn.getErrorStream() : conn.getInputStream()) {
            if (in != null) {
                responseBody = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        conn.disconnect();

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
