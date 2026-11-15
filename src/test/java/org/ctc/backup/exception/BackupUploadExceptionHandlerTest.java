package org.ctc.backup.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

class BackupUploadExceptionHandlerTest {
    final BackupUploadExceptionHandler handler = new BackupUploadExceptionHandler();

    @Test
    void givenOversizedGalleryUpload_whenHandling_thenReturnToTheGalleryWithItsError() {
        // given
        var request = new MockHttpServletRequest("POST", "/admin/tracks/0df6450b-8a4c-4eeb-b296-9cd33328a9d6/images");
        var redirect = new RedirectAttributesModelMap();
        // when
        String view = handler.handleMaxUploadSizeExceeded(new MaxUploadSizeExceededException(1), request, redirect);
        // then
        assertThat(view).isEqualTo("redirect:/admin/tracks/0df6450b-8a4c-4eeb-b296-9cd33328a9d6/edit#track-images");
        assertThat(redirect.getFlashAttributes().get("galleryError")).isEqualTo("The upload is larger than 100 MB in total; add fewer files at once.");
    }

    @Test
    void givenOversizedBackupUpload_whenHandling_thenKeepTheBackupRedirect() {
        // given
        var redirect = new RedirectAttributesModelMap();
        // when
        String view = handler.handleMaxUploadSizeExceeded(new MaxUploadSizeExceededException(1),
                new MockHttpServletRequest("POST", "/admin/backup/import"), redirect);
        // then
        assertThat(view).isEqualTo("redirect:/admin/backup");
        assertThat(redirect.getFlashAttributes()).containsKey("errorMessage");
    }
}
