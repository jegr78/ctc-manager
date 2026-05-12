package org.ctc.backup;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ctc.backup.service.BackupArchiveService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

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
}
