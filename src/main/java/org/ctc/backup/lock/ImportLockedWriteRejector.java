package org.ctc.backup.lock;

import static org.ctc.util.LogSanitizer.sanitize;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Intercepts every mutating request under {@code /admin/**} while the import lock is held
 * and rejects non-whitelisted requests with HTTP 503 before any controller body runs.
 *
 * <p>Whitelist: exactly one URL is exempt — {@code /admin/backup/import-execute}.
 * The match uses {@code String.equals(requestURI)}, NOT {@code startsWith}: a path like
 * {@code /admin/backup/import-execute-anything} must not slip through.
 *
 * <p>The 503 HTML body carries an auto-refresh meta tag; wording matches
 * {@code admin/layout.html} banner verbatim (no string-drift between banner and 503 response).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImportLockedWriteRejector implements HandlerInterceptor {

    private final ImportLockService importLockService;

    /**
     * HTTP verbs that mutate server state. PUT/PATCH/DELETE are not used by current admin
     * controllers, but including them ensures future controllers using those verbs are
     * automatically gated without per-endpoint changes.
     */
    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    /** Minimal 503 HTML body — wording matches admin/layout.html banner verbatim. */
    private static final String LOCK_HTML = """
            <!DOCTYPE html><html><head><meta charset="UTF-8">
            <meta http-equiv="refresh" content="10"></head>
            <body><h1>Backup import in progress — write access is temporarily locked.</h1>
            <p>This page will retry automatically.</p></body></html>
            """;

    /**
     * Short-circuits non-whitelisted mutating requests while the import lock is held.
     *
     * <p>Decision tree:
     * <ol>
     *   <li>Non-mutating method (GET/HEAD/OPTIONS/…) → allow.</li>
     *   <li>Lock not held → allow (normal operation).</li>
     *   <li>Whitelisted URL {@code /admin/backup/import-execute} → allow.</li>
     *   <li>Else → reject with HTTP 503 + HTML body; return {@code false}.</li>
     * </ol>
     *
     * @throws IOException from {@link HttpServletResponse#getWriter()} (servlet I/O; container handles failure)
     */
    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler)
            throws IOException {
		if (!MUTATING_METHODS.contains(req.getMethod().toUpperCase())) {
			return true;   // step 1 — read-only verb: allow
		}
		if (!importLockService.isLocked()) {
			return true;                                // step 2 — no lock: allow
		}
		if ("/admin/backup/import-execute".equals(req.getRequestURI())) {
			return true;   // step 3 — whitelist
		}
		log.info("Rejected admin POST during import lock: {} {}", sanitize(req.getMethod()), sanitize(req.getRequestURI()));
		res.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
		res.setContentType("text/html;charset=UTF-8");
		res.getWriter().write(LOCK_HTML);
		return false;                                                        // step 4 — reject
	}
}
