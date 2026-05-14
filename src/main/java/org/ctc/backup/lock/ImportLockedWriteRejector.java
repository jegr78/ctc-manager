package org.ctc.backup.lock;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

/**
 * Phase 76 / Plan 02 — Ring 2 write-rejector (SECU-06 / CONTEXT D-07 through D-10).
 *
 * <p>Registered in {@link org.ctc.admin.WebConfig} with
 * {@code .addPathPatterns("/admin/**")}. Intercepts every POST under
 * {@code /admin/**} while the {@link ImportLockService} lock is held and rejects
 * non-whitelisted requests with HTTP 503 + a minimal HTML body before any controller
 * body runs.
 *
 * <p><strong>Whitelist (D-09 / D-10):</strong> exactly one URL is exempt —
 * {@code /admin/backup/import-execute}. The match is {@code String.equals(requestURI)},
 * NOT {@code startsWith} (D-10 forbids prefix matching: a path like
 * {@code /admin/backup/import-execute-anything} must not slip through).
 *
 * <p><strong>503 HTML body (CD-03):</strong> a static literal HTML constant with an
 * auto-refresh meta tag. Body wording matches the {@code admin/layout.html} banner
 * verbatim (feedback_template_details: no string-drift between banner and 503 response).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImportLockedWriteRejector implements HandlerInterceptor {

    private final ImportLockService importLockService;

    /** Minimal 503 HTML body — wording matches admin/layout.html banner verbatim (D-12). */
    private static final String LOCK_HTML = """
            <!DOCTYPE html><html><head><meta charset="UTF-8">
            <meta http-equiv="refresh" content="10"></head>
            <body><h1>Backup import in progress — write access is temporarily locked.</h1>
            <p>This page will retry automatically.</p></body></html>
            """;

    /**
     * Short-circuits non-whitelisted POST requests while the import lock is held.
     *
     * <p>Decision tree (CONTEXT D-08):
     * <ol>
     *   <li>Non-POST methods → allow (GET is never rejected; only POST mutates state).</li>
     *   <li>Lock not held → allow (normal operation).</li>
     *   <li>Whitelisted URL {@code /admin/backup/import-execute} → allow (D-09 / D-10).</li>
     *   <li>Else → reject with HTTP 503 + HTML body; return {@code false}.</li>
     * </ol>
     *
     * @throws IOException from {@link HttpServletResponse#getWriter()} (servlet I/O; container handles failure)
     */
    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler)
            throws IOException {
        if (!"POST".equalsIgnoreCase(req.getMethod())) return true;         // step 1 — non-POST: allow
        if (!importLockService.isLocked()) return true;                     // step 2 — no lock: allow
        if ("/admin/backup/import-execute".equals(req.getRequestURI())) return true; // step 3 — whitelist (D-09 / D-10)
        log.info("Rejected admin POST during import lock: {} {}", req.getMethod(), req.getRequestURI());
        res.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        res.setContentType("text/html;charset=UTF-8");
        res.getWriter().write(LOCK_HTML);
        return false;                                                        // step 4 — reject
    }
}
