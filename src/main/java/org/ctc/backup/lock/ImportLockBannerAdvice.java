package org.ctc.backup.lock;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Phase 76 / Plan 02 — Ring 2 banner advice (SECU-06 / CONTEXT D-11).
 *
 * <p>Exposes an {@code importInProgress} boolean model attribute on every controller
 * invocation, mirroring the existing {@code GlobalModelAdvice} pattern (single class,
 * single {@link ModelAttribute} method). The value reads
 * {@link ImportLockService#isLocked()} — read-only, no holding required.
 *
 * <p>Applied globally (no {@code basePackages} filter per CONTEXT D-13): site templates
 * that do not reference {@code ${importInProgress}} in a {@code th:if} evaluate the
 * attribute as null/false and harmlessly skip the banner.
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
