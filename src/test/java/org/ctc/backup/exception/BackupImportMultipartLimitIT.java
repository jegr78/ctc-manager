package org.ctc.backup.exception;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Failsafe IT for SECU-04 — verifies that a multipart upload exceeding the 100 MB limit
 * (configured via {@code spring.servlet.multipart.max-request-size: 100MB} in Plan 01)
 * produces a clean Flash-redirect to {@code /admin/backup} with the locked D-02#1 string,
 * and that no Tomcat/Spring stack-trace markers are leaked in the response body.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class BackupImportMultipartLimitIT {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void given101MBMockMultipartFile_whenPostImportPreview_thenRedirectsToBackupWithErrorFlash()
            throws Exception {
        // given
        byte[] payload = new byte[101 * 1024 * 1024 + 1];
        MockMultipartFile file = new MockMultipartFile(
                "file", "oversized.zip", "application/zip", payload);

        // when / then
        MvcResult result = mockMvc.perform(
                        multipart("/admin/backup/import-preview")
                                .file(file)
                                .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/backup"))
                .andExpect(flash().attribute("errorMessage", "Upload too large — maximum is 100 MB."))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body)
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
