package org.ctc.backup;

import jakarta.validation.Valid;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.backup.dto.BackupImportConfirmForm;
import org.ctc.backup.dto.BackupImportPreview;
import org.ctc.backup.dto.BackupImportResult;
import org.ctc.backup.exception.AutoBackupBeforeImportException;
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
 * Backup feature controller: export ZIP download, import upload/preview/confirm/execute/cancel.
 * Stays thin per CLAUDE.md — all business logic in {@link BackupArchiveService} and
 * {@link BackupImportService}. Auth + CSRF are inherited from the Spring Security wiring.
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

	/**
	 * Multipart upload entry point. Delegates to {@link BackupImportService#stage(MultipartFile)},
	 * renders {@code admin/backup-preview}, or redirects with an error flash on failure.
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
	 * Preview → confirm page transition. Re-parses the staged ZIP, adds {@code "preview"} and a
	 * fresh {@link BackupImportConfirmForm}, and renders {@code admin/backup-confirm}.
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
	 * Confirm → execute handler. Tries the import lock first (HTTP 409 if already locked).
	 * Binds {@link BackupImportConfirmForm} with {@code @Valid}; on binding errors re-renders
	 * {@code admin/backup-confirm}. Otherwise re-validates via {@link BackupImportService#reparse}
	 * and delegates to {@link BackupImportService#execute} for the wipe-and-restore transaction.
	 * Lock is released in {@code finally} after the synchronous AFTER_COMMIT listener completes.
	 * {@code setHttp10Compatible(false)} on the 409 redirect view is required — the default
	 * {@code http10Compatible=true} mode overwrites the status to 302 via {@code sendRedirect}.
	 */
	@PostMapping("/import-execute")
	public ModelAndView importExecute(
			@Valid @ModelAttribute("backupImportConfirmForm") BackupImportConfirmForm form,
			BindingResult bindingResult, Model model, RedirectAttributes ra) {

		// 409 GUARD — view-mode redirect while import lock is held
		if (!importLockService.tryLock()) {
			ra.addFlashAttribute("errorMessage",
					"Another import is already running — please wait.");
			RedirectView rv = new RedirectView("/admin/backup");
			rv.setStatusCode(HttpStatus.CONFLICT);
			rv.setHttp10Compatible(false);  // REQUIRED: http10Compatible=true overwrites status to 302 via sendRedirect
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
				backupImportService.reparse(form.getStagingId());  // defense-in-depth re-validation
				BackupImportResult result = backupImportService.execute(form.getStagingId());
				ra.addFlashAttribute("successMessage",
						String.format("Import completed. %d rows restored across %d tables.",
								result.restoredTotal(), result.entityCount()));
			} catch (BackupArchiveException ex) {
				ra.addFlashAttribute("errorMessage", mapReason(ex));
			} catch (IOException ex) {
				log.error("IO error on import-execute (execute path): stagingId={}", form.getStagingId(), ex);
				ra.addFlashAttribute("errorMessage",
						"Backup archive failed safety checks (size or path) and was rejected.");
			} catch (UploadsRestoreException ex) {
				// Defensive catch: the AFTER_COMMIT listener is the real throw site and runs AFTER
				// the controller's redirect response is built, so this clause is rarely hit in practice.
				log.error("UploadsRestoreException reached controller path — unexpected; "
						+ "stagingId={}", form.getStagingId(), ex);
				ra.addFlashAttribute("errorMessage",
						"Import database succeeded but uploads restore failed and was reverted. "
								+ "See logs. Audit-id: unknown.");
			} catch (AutoBackupBeforeImportException ex) {
				// Auto-backup step runs BEFORE wipe — no rollback needed.
				// This catch MUST appear BEFORE BackupImportException (parent type) per Java first-match-wins.
				log.error("Pre-import auto-backup failed for stagingId={}, auditUuid={}",
						form.getStagingId(), ex.getAuditUuid(), ex);
				String auditIdText = ex.isAuditWritten()
						? ex.getAuditUuid().toString()
						: "unavailable (audit write failed; see logs for " + ex.getAuditUuid() + ")";
				ra.addFlashAttribute("errorMessage",
						String.format("Import aborted — pre-import auto-backup failed. "
								+ "No database changes. Audit-id: %s.", auditIdText));
				return new ModelAndView("redirect:/admin/backup");
			} catch (BackupImportException ex) {
				// When audit-write itself failed (double-failure path), no data_import_audit row exists.
				String auditIdText = ex.isAuditWritten()
						? ex.getAuditUuid().toString()
						: "unavailable (audit write failed; see logs for " + ex.getAuditUuid() + ")";
				// Surface BackupArchiveException cause detail when present (e.g. SCHEMA_MISMATCH).
				String causeDetail = ex.getCause() instanceof BackupArchiveException bae
						? " (" + bae.reason() + ")"
						: "";
				ra.addFlashAttribute("errorMessage",
						String.format("Import failed and was rolled back — see logs. Audit-id: %s%s.",
								auditIdText, causeDetail));
			}
			// On success the AFTER_COMMIT listener deletes the staging file;
			// on failure it survives so the operator can retry without re-uploading.
			return new ModelAndView("redirect:/admin/backup");
		} finally {
			importLockService.unlock();  // synchronous AFTER_COMMIT already completed
		}
	}

	/**
	 * Cancels the import flow and deletes the staging file synchronously (idempotent).
	 */
	@PostMapping("/import-cancel")
	public String importCancel(@RequestParam("stagingId") UUID stagingId,
			RedirectAttributes ra) {
		backupImportService.deleteStagingFile(stagingId);
		ra.addFlashAttribute("successMessage", "Import canceled.");
		return "redirect:/admin/backup";
	}

	/**
	 * Routes a {@link BackupArchiveException} to the appropriate user-facing Flash string.
	 * Exhaustive switch enforces coverage of all {@link BackupArchiveException.Reason} values.
	 */
	private String mapReason(BackupArchiveException ex) {
		return switch (ex.reason()) {
			case SCHEMA_MISMATCH -> ex.getMessage();
			case PATH_TRAVERSAL, ENTRY_TOO_LARGE, TOTAL_TOO_LARGE, TOO_MANY_ENTRIES,
					MANIFEST_MISSING, MANIFEST_INVALID, NOT_A_ZIP
					-> "Backup archive failed safety checks (size or path) and was rejected.";
		};
	}
}
