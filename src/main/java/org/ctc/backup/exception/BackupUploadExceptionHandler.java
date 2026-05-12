package org.ctc.backup.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Sibling {@code @ControllerAdvice} that maps {@link MaxUploadSizeExceededException}
 * (thrown by the Spring Multipart resolver when the upload exceeds
 * {@code spring.servlet.multipart.max-request-size: 100MB}) to a clean Flash-redirect
 * back to the backup landing page.
 *
 * <p>Kept in a separate advice class (not merged into {@code GlobalExceptionHandler})
 * because every handler in that class returns {@link org.springframework.web.servlet.ModelAndView},
 * and mixing {@code String "redirect:..."} return types in one advice class creates Spring
 * binding ambiguity (D-14).
 *
 * <p>The global scope (no {@code basePackageClasses} restriction) is intentional:
 * {@link MaxUploadSizeExceededException} is thrown by the Multipart resolver BEFORE
 * controller dispatch, so a package-scoped advice would never match.
 */
@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class BackupUploadExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public String handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException ex,
            HttpServletRequest request,
            RedirectAttributes redirectAttributes) {

        log.warn("Multipart upload rejected: max-size exceeded — uri={}, limit={}",
                request.getRequestURI(), ex.getMaxUploadSize());
        redirectAttributes.addFlashAttribute("errorMessage",
                "Upload too large — maximum is 100 MB.");
        return "redirect:/admin/backup";
    }
}
