package org.ctc.backup.service;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.ctc.backup.schema.BackupManifest;
import org.ctc.backup.schema.BackupSchema;
import org.ctc.backup.schema.EntityRef;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Phase 73-03 — stateless ZIP plumbing for the backup export pipeline.
 *
 * <p>Single public method {@link #writeZip(OutputStream, Instant)} streams a self-contained
 * backup archive into the caller's {@link OutputStream}. No intermediate
 * {@code ByteArrayOutputStream}, so the entire pipeline is true streaming (RESEARCH §Pattern 6).
 *
 * <p>ZIP entry order — manifest first, data next, uploads last:
 * <ol>
 *   <li>{@code manifest.json} — pretty-printed {@link BackupManifest}, schema-version 1.
 *       Manifest-first is the wire contract: partial-read consumers can validate the schema
 *       version BEFORE downloading the (potentially large) payload (RESEARCH §L-72.D-14).</li>
 *   <li>{@code data/<slug>.json} — one entry per entity in {@link BackupSchema#getExportOrder()}.
 *       Each is a JSON array of MixIn-shaped entity rows produced by
 *       {@link BackupExportService#fetchAllForBackup(Class)}.</li>
 *   <li>{@code uploads/<rel>} — every {@link UploadEntry} returned by
 *       {@link BackupExportService#enumerateReferencedUploads()}, defensively skipping any
 *       relative path containing {@code ".."} (ZIP-slip-on-EXPORT defense, threat T-73-05).</li>
 * </ol>
 *
 * <p>No JPA injection — repos belong to {@link BackupExportService} and are reached through
 * its public methods. However, the class is annotated {@code @Transactional(readOnly = true)}
 * so that the whole {@link #writeZip(OutputStream, Instant)} call runs inside a single
 * Hibernate session. This is non-negotiable: Plan 73-02 reduced
 * {@code SeasonRepository.findAllForBackup()}'s {@code @EntityGraph} to {@code {"cars"}} to
 * dodge {@code MultipleBagFetchException}, which means {@code season.getTracks()} must
 * materialize lazily during Jackson serialization. Without an open session, the export
 * throws {@code LazyInitializationException} the moment Jackson reaches the
 * {@code seasons.json} array (Wave 1 73-02 deviation rationale).
 */
@Slf4j
@Service
@Transactional(readOnly = true)
public class BackupArchiveService {

	private final BackupExportService backupExportService;
	private final BackupSchema backupSchema;
	private final ObjectMapper backupObjectMapper;
	private final String appVersion;

	public BackupArchiveService(
			BackupExportService backupExportService,
			BackupSchema backupSchema,
			@Qualifier("backupObjectMapper") ObjectMapper backupObjectMapper,
			@Value("${app.version:dev}") String appVersion
	) {
		this.backupExportService = backupExportService;
		this.backupSchema = backupSchema;
		this.backupObjectMapper = backupObjectMapper;
		this.appVersion = appVersion;
	}

	/**
	 * Streams a complete backup ZIP into {@code out}.
	 *
	 * <p>The caller owns the {@code OutputStream} lifecycle. This method opens a
	 * {@link ZipOutputStream} on top of {@code out} via try-with-resources;
	 * {@code ZipOutputStream.close()} writes the central directory AND closes the
	 * delegate stream per the JDK contract. In production this is the desired
	 * behaviour — the servlet container response output is closed only after the
	 * controller's {@code StreamingResponseBody} returns, so the order is correct.
	 * Callers that need to write trailing bytes to the same stream after the ZIP
	 * (none in this codebase) must wrap {@code out} in a close-shield filter.
	 *
	 * @param out the target stream (HTTP response body in production, byte buffer in tests)
	 * @param exportDate the export timestamp recorded in the manifest
	 * @throws IOException if the underlying stream fails
	 */
	public void writeZip(OutputStream out, Instant exportDate) throws IOException {
		List<EntityRef> exportOrder = backupSchema.getExportOrder();
		log.info("Backup export started: schemaVersion={}, appVersion={}, entities={}",
				BackupSchema.SCHEMA_VERSION, appVersion, exportOrder.size());

		try (ZipOutputStream zip = new ZipOutputStream(out)) {
			zip.setLevel(Deflater.DEFAULT_COMPRESSION);

			// Step 1 — manifest.json must be entry #0 (wire contract D-14).
			Map<String, Long> tableCounts = backupExportService.countRowsPerTable();
			BackupManifest manifest = new BackupManifest(
					BackupSchema.SCHEMA_VERSION, appVersion, exportDate, tableCounts);
			zip.putNextEntry(new ZipEntry("manifest.json"));
			writeJson(zip, manifest, /* pretty= */ true);
			zip.closeEntry();

			// Step 2 — data/<slug>.json per entity in EXPORT_ORDER.
			int dataEntries = 0;
			for (EntityRef ref : exportOrder) {
				List<?> rows = backupExportService.fetchAllForBackup(ref.entityClass());
				zip.putNextEntry(new ZipEntry(ref.fileName()));
				writeJson(zip, rows, /* pretty= */ false);
				zip.closeEntry();
				dataEntries++;
			}

			// Step 3 — uploads/<rel> per UploadEntry, with ZIP-slip-on-EXPORT defense.
			int uploadEntries = 0;
			int skippedTraversal = 0;
			for (UploadEntry entry : backupExportService.enumerateReferencedUploads()) {
				if (entry.relativePath().contains("..")) {
					log.warn("Skipping suspicious upload path during export: {}", entry.relativePath());
					skippedTraversal++;
					continue;
				}
				String zipName = "uploads/" + entry.relativePath();
				zip.putNextEntry(new ZipEntry(zipName));
				Files.copy(entry.absolutePath(), zip);
				zip.closeEntry();
				uploadEntries++;
			}

			zip.finish();
			log.info("Backup export completed: dataEntries={}, uploadEntries={}, skippedTraversal={}",
					dataEntries, uploadEntries, skippedTraversal);
		}
	}

	/**
	 * Writes {@code value} as JSON into {@code out} WITHOUT closing the underlying stream.
	 *
	 * <p>Jackson's {@code ObjectMapper.writeValue(OutputStream, Object)} closes the target
	 * stream by default, which is incompatible with streaming into a {@link ZipOutputStream}
	 * where the next entry must be written after the current one. We open a per-entry
	 * {@link JsonGenerator} with {@code AUTO_CLOSE_TARGET=false}, write the value through
	 * the generator, then close only the generator (leaving the {@link ZipOutputStream}
	 * open for the next {@code putNextEntry} call).
	 */
	private void writeJson(OutputStream out, Object value, boolean pretty) throws IOException {
		JsonGenerator generator = backupObjectMapper.getFactory().createGenerator(out);
		generator.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
		if (pretty) {
			generator.useDefaultPrettyPrinter();
		}
		try {
			backupObjectMapper.writeValue(generator, value);
		} finally {
			// AUTO_CLOSE_TARGET=false means generator.close() flushes and releases the
			// generator's internal state WITHOUT touching the underlying ZipOutputStream —
			// exactly what streaming requires. Closing the generator (rather than just
			// flushing it) prevents Jackson buffer references from lingering until GC.
			generator.close();
		}
	}
}
