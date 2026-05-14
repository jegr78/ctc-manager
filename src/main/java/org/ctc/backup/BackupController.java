package org.ctc.backup;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.backup.dto.BackupImportConfirmForm;
import org.ctc.backup.dto.BackupImportPreview;
import org.ctc.backup.dto.BackupImportResult;
import org.ctc.backup.exception.BackupArchiveException;
import org.ctc.backup.exception.BackupImportException;
import org.ctc.backup.exception.UploadsRestoreException;
import org.ctc.backup.lock.ImportLockService;
import org.ctc.backup.service.BackupArchiveService;
import org.ctc.backup.service.BackupImportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Phase 73-04 — visible Backup feature glue.
 *
 * <p>Two responsibilities, both pure HTTP plumbing:
 * <ul>
 *   <li>{@code GET /admin/backup} renders the {@code admin/backup} Thymeleaf template
 *       with {@code title=Backup} so the sidebar entry highlights via the
 *       {@code title.contains('Backup')} predicate in {@code admin/layout.html}.</li>
 *   <li>{@code POST /admin/backup/export} returns a {@link StreamingResponseBody} that
 *       delegates the actual ZIP composition to
 *       {@link BackupArchiveService#writeZip(java.io.OutputStream, java.time.Instant)}.
 *       The {@code Content-Disposition} filename uses a basic-form ISO instant
 *       ({@code yyyyMMdd'T'HHmmss'Z'}) — Windows-safe because it omits the colon
 *       separators that the canonical ISO-8601 form carries.</li>
 * </ul>
 *
 * <p>Auth + CSRF are inherited from the Spring Security wiring:
 * {@code SecurityConfig} (prod/docker) requires authentication on {@code /admin/**}
 * and enforces CSRF by default; {@code OpenSecurityConfig} (dev/local) permits
 * everything and disables CSRF. No endpoint-specific {@code requestMatchers} rules
 * are needed because the existing catch-all suffices.
 *
 * <p>Controller stays thin per CLAUDE.md: zero business logic, zero repository
 * access, single-method delegation per handler. The {@link BackupArchiveService}
 * carries its own {@code @Transactional(readOnly = true)} boundary so the
 * Hibernate session stays open for the entire streamed export (mandatory because
 * {@code Season.tracks} is lazy and Jackson reaches it only mid-serialization).
 *
 * <p>Phase 75 — D-17: the {@code admin/backup-confirm.html} Thymeleaf template
 * survives unchanged; only the {@link #importExecute} controller body is upgraded
 * from Phase 74's stub-flash to the real
 * {@link BackupImportService#execute(java.util.UUID)} delegation. The locked D-15
 * flash strings (success / failure / uploads-restore-soft-fail) are bound here.
 * PATTERNS Open-Question §5 (UploadsRestoreException GlobalExceptionHandler
 * mapping) is hereby RESOLVED: no global handler is added — the controller's
 * local {@code catch (UploadsRestoreException)} clause below is sufficient
 * defense-in-depth (RESEARCH §7 defensive-default deferred).
 */
@Slf4j
@Controller
@RequestMapping("/admin/backup")
@RequiredArgsConstructor
public class BackupController {

	private static final DateTimeFormatter ISO_COMPACT_INSTANT =
			DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

	private final BackupArchiveService backupArchiveService;
	private final BackupImportService backupImportService;
	private final ImportLockService importLockService;

	@GetMapping
	public String showForm(Model model) {
		model.addAttribute("title", "Backup");
		return "admin/backup";
	}

	@PostMapping("/export")
	public ResponseEntity<StreamingResponseBody> export() {
		Instant now = Instant.now();
		String filename = "ctc-backup-" + isoSafeFilename(now) + ".zip";
		log.info("Backup export requested: filename={}", filename);

		StreamingResponseBody body = outputStream -> {
			try {
				backupArchiveService.writeZip(outputStream, now);
			} catch (IOException e) {
				// The response is already committed once the first byte flushes, so we
				// cannot surface an error page here — log + rethrow so the servlet
				// container truncates the stream and the client sees a partial download.
				log.error("Backup export I/O failure mid-stream (filename={})", filename, e);
				throw new UncheckedIOException(e);
			} catch (RuntimeException e) {
				// Defense-in-depth: any non-IO RuntimeException from the service
				// (LazyInitializationException, Jackson serialization errors,
				// NoSuchElementException, ...) must be logged with the filename context
				// BEFORE Spring's async dispatch unwinds the stack. Without this catch,
				// mid-stream failures vanish into the AsyncRequestTimeoutException
				// handler and are very hard to diagnose in production.
				log.error("Backup export failure mid-stream (filename={})", filename, e);
				throw e;
			}
		};

		String contentDisposition = ContentDisposition.attachment()
				.filename(filename)
				.build()
				.toString();

		return ResponseEntity.ok()
				.contentType(MediaType.APPLICATION_OCTET_STREAM)
				.header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
				.body(body);
	}

	private static String isoSafeFilename(Instant when) {
		return ISO_COMPACT_INSTANT.format(when);
	}

	// =========================================================================
	// Phase 74 — import endpoints (D-22)
	// =========================================================================

	/**
	 * Phase 74 — multipart upload entry point.
	 *
	 * <p>Delegates to {@link BackupImportService#stage(MultipartFile)}, adds the
	 * resulting {@link BackupImportPreview} as {@code "preview"} and renders
	 * {@code admin/backup-preview}. On any {@link BackupArchiveException} or
	 * staging {@link IOException}, redirects to {@code /admin/backup} with an
	 * {@code errorMessage} Flash attribute (D-02 routing via {@link #mapReason}).
	 */
	@PostMapping("/import-preview")
	public String importPreview(@RequestParam("file") MultipartFile file,
			Model model, RedirectAttributes ra) {
		try {
			BackupImportPreview preview = backupImportService.stage(file);
			model.addAttribute("preview", preview);
			return "admin/backup-preview";
		} catch (BackupArchiveException ex) {
			ra.addFlashAttribute("errorMessage", mapReason(ex));
			return "redirect:/admin/backup";
		} catch (IOException ex) {
			log.error("IO error on import-preview", ex);
			ra.addFlashAttribute("errorMessage",
					"Backup archive failed safety checks (size or path) and was rejected.");
			return "redirect:/admin/backup";
		}
	}

	/**
	 * Phase 74 — preview → confirm page transition.
	 *
	 * <p>Re-parses the staged ZIP via {@link BackupImportService#reparse(UUID)}
	 * (stateless, D-18), adds {@code "preview"} and a fresh
	 * {@link BackupImportConfirmForm} with {@code stagingId} pre-filled, and
	 * renders {@code admin/backup-confirm}.
	 */
	@PostMapping("/import-confirm")
	public String importConfirm(@RequestParam("stagingId") UUID stagingId,
			Model model, RedirectAttributes ra) {
		try {
			BackupImportPreview preview = backupImportService.reparse(stagingId);
			model.addAttribute("preview", preview);
			BackupImportConfirmForm form = new BackupImportConfirmForm();
			form.setStagingId(stagingId);
			model.addAttribute("backupImportConfirmForm", form);
			return "admin/backup-confirm";
		} catch (BackupArchiveException ex) {
			ra.addFlashAttribute("errorMessage", mapReason(ex));
			return "redirect:/admin/backup";
		} catch (IOException ex) {
			log.error("IO error on import-confirm: stagingId={}", stagingId, ex);
			ra.addFlashAttribute("errorMessage",
					"Backup archive failed safety checks (size or path) and was rejected.");
			return "redirect:/admin/backup";
		}
	}

	/**
	 * Phase 75 — confirm → execute (D-15, replaces Phase 74 D-08 stub).
	 * Phase 76 — extended with tryLock/finally wrapper + HTTP 409 View-mode redirect (D-04/D-05/D-06).
	 *
	 * <p>Binds {@link BackupImportConfirmForm} with {@code @Valid}. On binding
	 * errors, re-renders {@code admin/backup-confirm} with field errors (re-parses
	 * staging file so the preview data is still available). Otherwise re-runs the
	 * full validation chain via {@link BackupImportService#reparse(UUID)} (D-09
	 * defense-in-depth) and then delegates to
	 * {@link BackupImportService#execute(UUID)} for the real wipe-and-restore
	 * transaction. The three D-15 flash strings (success / failure / uploads-restore
	 * soft-fail) are bound here.
	 *
	 * <p><strong>Phase 76 / D-04:</strong> {@link ImportLockService#tryLock()} is
	 * called BEFORE the binding-error check. If the lock is already held, a HTTP 409
	 * redirect is returned immediately via a {@link RedirectView} with
	 * {@code setStatusCode(HttpStatus.CONFLICT)} + {@code setHttp10Compatible(false)}.
	 * The {@code setHttp10Compatible(false)} is REQUIRED — in the default
	 * {@code http10Compatible=true} mode {@code sendRedirect} overwrites the status
	 * to 302 (RESEARCH Pitfall #1).
	 *
	 * <p><strong>Phase 76 / D-06:</strong> the lock is released in {@code finally}
	 * AFTER {@code execute()} returns. Spring's default
	 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} runs synchronously on
	 * the same thread, so the Plan 75-07 uploads-move listener has completed before
	 * the {@code finally} releases. Do NOT add {@code @Async} to that listener.
	 *
	 * <p><strong>Return type change:</strong> {@code String} → {@link ModelAndView}
	 * to support the View-mode 409 redirect (RESEARCH Pattern 3). Every existing
	 * return path is wrapped as {@code new ModelAndView(...)}.
	 *
	 * <p><strong>REVISION-iteration-1 (W4) catch-chain order:</strong> the catch chain
	 * order ({@link BackupArchiveException} → {@link UploadsRestoreException} →
	 * {@link BackupImportException}) is INDEPENDENT because all three exception types
	 * are siblings extending {@code RuntimeException} — there is NO inheritance
	 * between them. Reorder is safe; the chosen order matches the order in which the
	 * exceptions can be thrown in the request lifecycle: ZIP-parse →
	 * AFTER_COMMIT-listener → @Transactional-body.
	 *
	 * <p><strong>D-15 #3 planner-note:</strong> the {@code catch (UploadsRestoreException)}
	 * clause is DEFENSIVE. The AFTER_COMMIT listener (Plan 75-07) fires after the
	 * controller's redirect response is built, so its
	 * {@code UploadsRestoreException} typically does NOT propagate back to this
	 * thread. The defensive catch here covers a future refactor that invokes the
	 * move-triple from a non-AFTER_COMMIT path. The
	 * {@code auditUuid} is reported as {@code "unknown"} because the listener owns
	 * the audit row and the exception carrier does not transport the UUID. The
	 * operator detects the soft-fail in practice via (a) the ERROR log line from
	 * the listener, (b) the {@code data_import_audit.success=false} row written by
	 * the listener's failure paths, and (c) the {@code data/.import-backups/<ts>/uploads-old/}
	 * directory NOT having been moved back to {@code data/{profile}/uploads/}.
	 */
	@PostMapping("/import-execute")
	public ModelAndView importExecute(
			@Valid @ModelAttribute("backupImportConfirmForm") BackupImportConfirmForm form,
			BindingResult bindingResult, Model model, RedirectAttributes ra) {

		// 409 GUARD — D-04 / D-05 / RESEARCH Pattern 3 (view-mode redirect, NOT response.setStatus)
		if (!importLockService.tryLock()) {
			ra.addFlashAttribute("errorMessage",
					"Another import is already running — please wait.");
			RedirectView rv = new RedirectView("/admin/backup");
			rv.setStatusCode(HttpStatus.CONFLICT);
			rv.setHttp10Compatible(false);  // REQUIRED: in http10Compatible=true mode sendRedirect overwrites status to 302
			return new ModelAndView(rv);
		}
		try {
			if (bindingResult.hasErrors()) {
				try {
					BackupImportPreview preview = backupImportService.reparse(form.getStagingId());
					model.addAttribute("preview", preview);
					return new ModelAndView("admin/backup-confirm");
				} catch (BackupArchiveException ex) {
					ra.addFlashAttribute("errorMessage", mapReason(ex));
					return new ModelAndView("redirect:/admin/backup");
				} catch (IOException ex) {
					log.error("IO error on import-execute (re-render path): stagingId={}", form.getStagingId(), ex);
					ra.addFlashAttribute("errorMessage",
							"Backup archive failed safety checks (size or path) and was rejected.");
					return new ModelAndView("redirect:/admin/backup");
				}
			}
			try {
				backupImportService.reparse(form.getStagingId());  // D-09 defense-in-depth re-validation
				BackupImportResult result = backupImportService.execute(form.getStagingId());
				ra.addFlashAttribute("successMessage",
						String.format("Import completed. %d rows restored across %d tables.",
								result.restoredTotal(), result.entityCount()));  // D-15 #1
			} catch (BackupArchiveException ex) {
				ra.addFlashAttribute("errorMessage", mapReason(ex));
			} catch (IOException ex) {
				log.error("IO error on import-execute (execute path): stagingId={}", form.getStagingId(), ex);
				ra.addFlashAttribute("errorMessage",
						"Backup archive failed safety checks (size or path) and was rejected.");
			} catch (UploadsRestoreException ex) {
				// D-15 #3 — defensive catch. The AFTER_COMMIT listener (Plan 75-07) is the
				// real throw site and runs AFTER the controller's redirect response is built,
				// so this clause is rarely-to-never hit in practice. See class Javadoc for
				// the Q5 resolution + operator-recovery story.
				log.error("UploadsRestoreException reached controller path — unexpected post-Plan 07; "
						+ "stagingId={}", form.getStagingId(), ex);
				ra.addFlashAttribute("errorMessage",
						"Import database succeeded but uploads restore failed and was reverted. "
								+ "See logs. Audit-id: unknown.");
			} catch (BackupImportException ex) {
				// WR-03: when the REQUIRES_NEW audit-write itself failed (double-failure path),
				// no data_import_audit row exists for the operator to query. Reflect that in the
				// flash so "Audit-id: <uuid>" is not a misleading dead-end.
				String auditIdText = ex.isAuditWritten()
						? ex.getAuditUuid().toString()
						: "unavailable (audit write failed; see logs for " + ex.getAuditUuid() + ")";
				// WR-06: if the wrapped cause is a BackupArchiveException (e.g. SCHEMA_MISMATCH
				// detected inside execute() after reparse), surface the reason inline so the
				// operator sees the diagnostic detail instead of just the generic rollback flash.
				String causeDetail = (ex.getCause() instanceof BackupArchiveException bae)
						? " (" + bae.reason() + ")"
						: "";
				ra.addFlashAttribute("errorMessage",
						String.format("Import failed and was rolled back — see logs. Audit-id: %s%s.",
								auditIdText, causeDetail));  // D-15 #2
			}
			// STAGING FILE — on success the AFTER_COMMIT listener (Plan 75-07) deletes it;
			// on failure the file survives so the operator can retry without re-uploading.
			return new ModelAndView("redirect:/admin/backup");
		} finally {
			importLockService.unlock();  // D-06 — synchronous AFTER_COMMIT already completed
		}
	}

	/**
	 * Phase 74 — cancel the import flow and delete the staging file synchronously.
	 *
	 * <p>Calls {@link BackupImportService#deleteStagingFile(UUID)} (idempotent,
	 * never throws) and redirects to {@code /admin/backup} with a success Flash.
	 */
	@PostMapping("/import-cancel")
	public String importCancel(@RequestParam("stagingId") UUID stagingId,
			RedirectAttributes ra) {
		backupImportService.deleteStagingFile(stagingId);
		ra.addFlashAttribute("successMessage", "Import canceled.");
		return "redirect:/admin/backup";
	}

	/**
	 * Routes a {@link BackupArchiveException} to the appropriate locked D-02 Flash string.
	 *
	 * <p>Java 25 exhaustive switch: compiler enforces coverage of all {@link BackupArchiveException.Reason}
	 * values, so future additions to the enum produce a compile error here (fail-fast by design).
	 *
	 * @param ex the exception thrown by the import service
	 * @return the D-02 user-facing Flash string
	 */
	private String mapReason(BackupArchiveException ex) {
		return switch (ex.reason()) {
			case SCHEMA_MISMATCH -> ex.getMessage();  // Plan 05 already formatted with D-02#2
			case PATH_TRAVERSAL, ENTRY_TOO_LARGE, TOTAL_TOO_LARGE, TOO_MANY_ENTRIES,
					MANIFEST_MISSING, MANIFEST_INVALID, NOT_A_ZIP
					-> "Backup archive failed safety checks (size or path) and was rejected.";  // D-02#3
		};
	}
}
