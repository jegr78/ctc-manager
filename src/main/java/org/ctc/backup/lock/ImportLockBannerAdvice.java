package org.ctc.backup.lock;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Exposes an {@code importInProgress} boolean model attribute on every controller invocation.
 *
 * <p>Reads {@link ImportLockService#isLocked()} — read-only, no holding required.
 * Applied globally: site templates that do not reference {@code ${importInProgress}} in a
 * {@code th:if} evaluate the attribute as null/false and harmlessly skip the banner.
 */
@ControllerAdvice
@RequiredArgsConstructor
public class ImportLockBannerAdvice {

    private final ImportLockService importLockService;

    @ModelAttribute("importInProgress")
    public boolean importInProgress() {
        return importLockService.isLocked();
    }
}
