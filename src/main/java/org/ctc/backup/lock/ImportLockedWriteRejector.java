package org.ctc.backup.lock;

import static org.ctc.util.LogSanitizer.sanitize;

import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

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
public class ImportLockedWriteRejector implements AsyncHandlerInterceptor {

    private final ImportLockService importLockService;

    /**
     * HTTP verbs that mutate server state. PUT/PATCH/DELETE are not used by current admin
     * controllers, but including them ensures future controllers using those verbs are
     * automatically gated without per-endpoint changes.
     */
    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private static final String ADMITTED_WRITER = ImportLockedWriteRejector.class.getName() + ".admitted";

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
     *   <li>Whitelisted URL {@code /admin/backup/import-execute} → allow.</li>
     *   <li>Lock not held → admit as a writer until {@link #afterCompletion}.</li>
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
		if ("/admin/backup/import-execute".equals(req.getRequestURI())) {
			return true;   // step 2 — whitelist, never counted as a writer
		}
		if (req.getAttribute(ADMITTED_WRITER) != null) {
			return true;   // async redispatch of an admitted writer — counted once, released in afterCompletion
		}
		if (importLockService.tryEnterWriter()) {
			req.setAttribute(ADMITTED_WRITER, new AtomicBoolean());
			return true;   // step 3 — admitted writer until afterCompletion
		}
		log.info("Rejected admin POST during import lock: {} {}", sanitize(req.getMethod()), sanitize(req.getRequestURI()));
		res.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
		res.setContentType("text/html;charset=UTF-8");
		res.getWriter().write(LOCK_HTML);
		return false;                                                        // step 4 — reject
	}

	@Override
	public void afterCompletion(HttpServletRequest req, HttpServletResponse res, Object handler, Exception ex) {
		release(req.getAttribute(ADMITTED_WRITER));
	}

	/** Releases an async writer when its context ends, even if the container never redispatches. */
	@Override
	public void afterConcurrentHandlingStarted(HttpServletRequest req, HttpServletResponse res, Object handler) {
		Object token = req.getAttribute(ADMITTED_WRITER);
		if (token != null && req.isAsyncStarted()) {
			req.getAsyncContext().addListener(new AsyncListener() {
				@Override
				public void onComplete(AsyncEvent event) {
					release(token);
				}

				@Override
				public void onTimeout(AsyncEvent event) {
					release(token);
				}

				@Override
				public void onError(AsyncEvent event) {
					release(token);
				}

				@Override
				public void onStartAsync(AsyncEvent event) {
				}
			});
		}
	}

	private void release(Object token) {
		if (token instanceof AtomicBoolean released && released.compareAndSet(false, true)) {
			importLockService.exitWriter();
		}
	}
}
