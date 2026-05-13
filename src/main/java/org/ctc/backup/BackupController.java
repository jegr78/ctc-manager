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
import org.ctc.backup.exception.BackupArchiveException;
import org.ctc.backup.service.BackupArchiveService;
import org.ctc.backup.service.BackupImportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
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
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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
	 * Phase 74 STUB — confirm → execute (D-08).
	 *
	 * <p>Binds {@link BackupImportConfirmForm} with {@code @Valid}. On binding
	 * errors, re-renders {@code admin/backup-confirm} with field errors (re-parses
	 * staging file so the preview data is still available). Otherwise re-runs the
	 * full validation chain via {@link BackupImportService#reparse(UUID)} (D-09
	 * defense-in-depth) and redirects to {@code /admin/backup} with the locked
	 * D-02#5 stub Flash message.
	 *
	 * <p><strong>D-08 seam:</strong> the staging file is intentionally NOT deleted
	 * here — Phase 75 inherits it.
	 */
	@PostMapping("/import-execute")
	public String importExecute(
			@Valid @ModelAttribute("backupImportConfirmForm") BackupImportConfirmForm form,
			BindingResult bindingResult, Model model, RedirectAttributes ra) {
		if (bindingResult.hasErrors()) {
			try {
				BackupImportPreview preview = backupImportService.reparse(form.getStagingId());
				model.addAttribute("preview", preview);
				return "admin/backup-confirm";
			} catch (BackupArchiveException ex) {
				ra.addFlashAttribute("errorMessage", mapReason(ex));
				return "redirect:/admin/backup";
			} catch (IOException ex) {
				log.error("IO error on import-execute (re-render path): stagingId={}", form.getStagingId(), ex);
				ra.addFlashAttribute("errorMessage",
						"Backup archive failed safety checks (size or path) and was rejected.");
				return "redirect:/admin/backup";
			}
		}
		try {
			backupImportService.reparse(form.getStagingId());  // D-09 defense-in-depth re-validation
		} catch (BackupArchiveException ex) {
			ra.addFlashAttribute("errorMessage", mapReason(ex));
			return "redirect:/admin/backup";
		} catch (IOException ex) {
			log.error("IO error on import-execute (execute path): stagingId={}", form.getStagingId(), ex);
			ra.addFlashAttribute("errorMessage",
					"Backup archive failed safety checks (size or path) and was rejected.");
			return "redirect:/admin/backup";
		}
		ra.addFlashAttribute("successMessage",
				"Validation succeeded. Import execution will be enabled in Phase 75.");
		// STAGING FILE NOT DELETED — Phase 75 inherits per D-08
		return "redirect:/admin/backup";
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
