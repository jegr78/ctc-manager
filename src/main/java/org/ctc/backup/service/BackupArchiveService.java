package org.ctc.backup.service;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.ctc.backup.exception.BackupArchiveException;
import org.ctc.backup.exception.BackupArchiveException.Reason;
import org.ctc.backup.io.LimitedInputStream;
import org.ctc.backup.schema.BackupManifest;
import org.ctc.backup.schema.BackupSchema;
import org.ctc.backup.schema.EntityRef;
import org.ctc.backup.security.PathTraversalGuard;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.ctc.backup.service.BackupImportLimits.MAX_ENTRIES;
import static org.ctc.backup.service.BackupImportLimits.MAX_ENTRY_BYTES;
import static org.ctc.backup.service.BackupImportLimits.MAX_TOTAL_BYTES;

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
 *
 * <h2>Phase 74 reader extension (Plan 04 — D-20 single-class invariant)</h2>
 *
 * <p>Three streaming reader methods are added in Phase 74:
 * <ul>
 *   <li>{@link #readManifest(Path)} — opens a Phase-73 export ZIP, asserts the first entry
 *       is literally {@code manifest.json}, and deserializes it via the qualified
 *       {@code backupObjectMapper}.</li>
 *   <li>{@link #countDataEntries(Path)} — walks every {@code data/<slug>.json} entry via a
 *       Jackson {@code JsonParser} token-loop (no full-document buffering) and returns a
 *       per-table row-count map.</li>
 *   <li>{@link #countUploadFiles(Path)} — counts every non-directory entry under
 *       {@code uploads/} by draining each entry through a discard buffer.</li>
 * </ul>
 *
 * <p>All three methods route every ZIP entry through a single hardened helper
 * {@link #assertEntrySafe(ZipEntry, Path, int, long)} that enforces:
 * ZIP-Slip defense (D-11, SECU-01), per-entry inflate-size cap (D-12, SECU-02),
 * total inflate-size cap, and maximum entry count. The reader does not extract anything
 * to disk — it is a pure counting and parsing pass over the inflated byte stream.
 * Phase 75 will add extraction; at that point the traversal root must be tightened to
 * a per-import extraction subdirectory (see Plan 04 Notes §"Path-traversal scope").
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

	// =========================================================================
	// Phase 74 reader extension — D-20 single-class invariant
	// =========================================================================

	/**
	 * Opens a Phase-73 export ZIP and returns the deserialized {@link BackupManifest}.
	 *
	 * <p><b>Manifest-first contract:</b> the first ZIP entry MUST be literally named
	 * {@code manifest.json} (no path prefix, no nested directory). If it is not, or if
	 * the JSON cannot be deserialized, a typed {@link BackupArchiveException} is thrown.
	 *
	 * <p><b>Hardening invariants (per entry):</b>
	 * <ul>
	 *   <li>ZIP-Slip defense — entry name must not escape the ZIP's parent directory
	 *       (D-11, SECU-01).</li>
	 *   <li>Per-entry inflate cap — entry may not expand beyond
	 *       {@code MAX_ENTRY_BYTES} (50 MB) when read (D-12, SECU-02).</li>
	 *   <li>Total inflate cap — cumulative inflated bytes across all entries must not
	 *       exceed {@code MAX_TOTAL_BYTES} (500 MB).</li>
	 *   <li>Entry-count cap — archive must not contain more than {@code MAX_ENTRIES}
	 *       entries.</li>
	 * </ul>
	 *
	 * @param zipPath path to the backup ZIP file to read
	 * @return the deserialized {@link BackupManifest}
	 * @throws BackupArchiveException with {@code Reason.MANIFEST_MISSING} when entry 0 is
	 *                                not {@code manifest.json}; {@code Reason.MANIFEST_INVALID}
	 *                                on Jackson deserialization failure; bomb/traversal reasons
	 *                                per the hardening invariants above
	 */
	public BackupManifest readManifest(Path zipPath) throws BackupArchiveException {
		Path stagingRoot = resolveStagingRoot(zipPath);
		long[] inflatedAcc = new long[]{0L};

		try (ZipInputStream zis = openHardened(zipPath)) {
			ZipEntry entry = zis.getNextEntry();
			if (entry == null || !"manifest.json".equals(entry.getName())) {
				String got = entry == null ? "<no entries>" : entry.getName();
				log.warn("Backup manifest rejected: reason={}, msg={}", Reason.MANIFEST_MISSING,
						"first entry is not manifest.json, got: " + got);
				throw new BackupArchiveException(Reason.MANIFEST_MISSING,
						"first entry is not manifest.json, got: " + got);
			}

			final String entryName = entry.getName();
			LimitedInputStream limited = new LimitedInputStream(zis, MAX_ENTRY_BYTES,
					finalBytes -> {
						inflatedAcc[0] += finalBytes;
						if (finalBytes >= MAX_ENTRY_BYTES) {
							log.warn("Backup ZIP entry exceeds limit: name={}, limit={} bytes",
									entryName, MAX_ENTRY_BYTES);
						}
					});

			try {
				BackupManifest manifest = backupObjectMapper.readValue(limited, BackupManifest.class);
				// Note: limited is NOT closed here — ZipInputStream manages entry lifecycle.
				// The LongConsumer fires when limited.close() is called, which happens
				// implicitly when the try-with-resources closes the ZipInputStream.
				assertEntrySafe(entry, stagingRoot, 1, inflatedAcc[0]);
				log.info("Backup manifest read: schemaVersion={}, appVersion={}",
						manifest.schemaVersion(), manifest.appVersion());
				return manifest;
			} catch (BackupArchiveException ex) {
				throw ex;
			} catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
				log.warn("Backup manifest rejected: reason={}, msg={}", Reason.MANIFEST_INVALID,
						ex.getMessage());
				throw new BackupArchiveException(Reason.MANIFEST_INVALID,
						"manifest.json parse failed", ex);
			} catch (IOException ex) {
				log.warn("Backup manifest rejected: reason={}, msg={}", Reason.MANIFEST_INVALID,
						ex.getMessage());
				throw new BackupArchiveException(Reason.MANIFEST_INVALID,
						"ZIP read failure", ex);
			}
		} catch (BackupArchiveException ex) {
			throw ex;
		} catch (IOException ex) {
			throw new BackupArchiveException(Reason.MANIFEST_INVALID, "ZIP read failure", ex);
		}
	}

	/**
	 * Walks every {@code data/<slug>.json} entry in the ZIP and returns a per-table row-count
	 * map, derived via a streaming Jackson {@link JsonParser} token-loop (no full-document
	 * buffering).
	 *
	 * <p><b>Contract:</b> the map keys are table-name slugs derived from the entry name by
	 * inverting the {@code EntityRef} slug rule: {@code data/season-phases.json} →
	 * {@code season_phases}. Iteration order is insertion order (ZIP entry order) via
	 * {@link LinkedHashMap}.
	 *
	 * <p><b>Hardening invariants (per entry):</b> same as {@link #readManifest(Path)}.
	 *
	 * @param zipPath path to the backup ZIP file to read
	 * @return per-table row counts in ZIP entry order; never {@code null}
	 * @throws BackupArchiveException with {@code Reason.MANIFEST_INVALID} when a
	 *                                {@code data/*.json} entry is not a JSON array; bomb/traversal
	 *                                reasons per the hardening invariants
	 */
	public Map<String, Long> countDataEntries(Path zipPath) throws BackupArchiveException {
		Path stagingRoot = resolveStagingRoot(zipPath);
		long[] inflatedAcc = new long[]{0L};
		int entryCount = 0;
		LinkedHashMap<String, Long> result = new LinkedHashMap<>();

		try (ZipInputStream zis = openHardened(zipPath)) {
			ZipEntry entry;
			while ((entry = zis.getNextEntry()) != null) {
				String name = entry.getName();
				entryCount++;

				if (name.startsWith("data/") && name.endsWith(".json") && !entry.isDirectory()) {
					final String entryName = name;
					LimitedInputStream limited = new LimitedInputStream(zis, MAX_ENTRY_BYTES,
							finalBytes -> {
								inflatedAcc[0] += finalBytes;
								if (finalBytes >= MAX_ENTRY_BYTES) {
									log.warn("Backup ZIP entry exceeds limit: name={}, limit={} bytes",
											entryName, MAX_ENTRY_BYTES);
								}
							});

					JsonParser parser = backupObjectMapper.getFactory().createParser(limited);
					parser.disable(JsonParser.Feature.AUTO_CLOSE_SOURCE);
					try {
						JsonToken firstToken = parser.nextToken();
						if (firstToken != JsonToken.START_ARRAY) {
							throw new BackupArchiveException(Reason.MANIFEST_INVALID,
									"data file is not a JSON array: " + name);
						}
						long rowCount = 0;
						JsonToken tok;
						while ((tok = parser.nextToken()) != null && tok != JsonToken.END_ARRAY) {
							if (tok == JsonToken.START_OBJECT) {
								rowCount++;
								parser.skipChildren();
							}
						}
						String tableName = name.substring("data/".length(),
								name.length() - ".json".length()).replace('-', '_');
						result.put(tableName, rowCount);
					} finally {
						// parser.close() triggers limited.close() only if AUTO_CLOSE_SOURCE=true.
						// Since we disabled AUTO_CLOSE_SOURCE, we must close limited manually
						// BEFORE parser.close() to fire the LongConsumer callback.
						limited.close();
						parser.close();
					}
				}

				assertEntrySafe(entry, stagingRoot, entryCount, inflatedAcc[0]);
			}
		} catch (BackupArchiveException ex) {
			throw ex;
		} catch (IOException ex) {
			throw new BackupArchiveException(Reason.MANIFEST_INVALID, "ZIP read failure", ex);
		}

		log.info("Backup data counts read: entries={}, totalRows={}", result.size(),
				result.values().stream().mapToLong(Long::longValue).sum());
		return result;
	}

	/**
	 * Counts all non-directory entries under {@code uploads/} in the ZIP by draining each
	 * entry through a discard buffer (counting only is insufficient because
	 * {@link ZipEntry#getSize()} reflects the central-directory value, which a malicious
	 * archive can forge).
	 *
	 * <p><b>Hardening invariants (per entry):</b> same as {@link #readManifest(Path)}.
	 *
	 * @param zipPath path to the backup ZIP file to read
	 * @return the number of upload file entries; {@code 0} if none
	 * @throws BackupArchiveException with bomb/traversal reasons per the hardening invariants
	 */
	public int countUploadFiles(Path zipPath) throws BackupArchiveException {
		Path stagingRoot = resolveStagingRoot(zipPath);
		long[] inflatedAcc = new long[]{0L};
		int entryCount = 0;
		int uploadCount = 0;

		try (ZipInputStream zis = openHardened(zipPath)) {
			ZipEntry entry;
			while ((entry = zis.getNextEntry()) != null) {
				String name = entry.getName();
				entryCount++;

				if (name.startsWith("uploads/") && !entry.isDirectory()) {
					final String entryName = name;
					try (LimitedInputStream limited = new LimitedInputStream(zis, MAX_ENTRY_BYTES,
							finalBytes -> {
								inflatedAcc[0] += finalBytes;
								if (finalBytes >= MAX_ENTRY_BYTES) {
									log.warn("Backup ZIP entry exceeds limit: name={}, limit={} bytes",
											entryName, MAX_ENTRY_BYTES);
								}
							})) {
						// Drain via discard buffer so the bomb defense fires on actual inflated bytes.
						byte[] buf = new byte[8192];
						while (limited.read(buf) != -1) {
							/* discard */
						}
						// limited.close() fires LongConsumer exactly once when try-with-resources exits.
					}
					uploadCount++;
				}

				assertEntrySafe(entry, stagingRoot, entryCount, inflatedAcc[0]);
			}
		} catch (BackupArchiveException ex) {
			throw ex;
		} catch (IOException ex) {
			throw new BackupArchiveException(Reason.MANIFEST_INVALID, "ZIP read failure", ex);
		}

		log.info("Backup upload entries counted: count={}", uploadCount);
		return uploadCount;
	}

	// =========================================================================
	// Private helpers
	// =========================================================================

	/**
	 * Opens the ZIP at {@code zipPath} wrapped in a {@link ZipInputStream}.
	 *
	 * <p>Per-entry guarantees (path-traversal, byte limits, entry count) are NOT applied here;
	 * they live in {@link #assertEntrySafe(ZipEntry, Path, int, long)} so each public reader
	 * method retains explicit, inline visibility into the per-entry safety checks.
	 *
	 * @param zipPath path to the ZIP file to open
	 * @return an open {@link ZipInputStream}; caller is responsible for closing
	 * @throws IOException if the file cannot be opened
	 */
	private ZipInputStream openHardened(Path zipPath) throws IOException {
		InputStream fis = Files.newInputStream(zipPath);
		return new ZipInputStream(fis);
	}

	/**
	 * Resolves the staging root for path-traversal checks: the absolute parent directory of
	 * the ZIP file. Falls back to the current working directory if the parent is {@code null}
	 * (pathological case — ZIP on a root filesystem path).
	 *
	 * @param zipPath the ZIP file path
	 * @return the resolved staging root
	 */
	private static Path resolveStagingRoot(Path zipPath) {
		Path parent = zipPath.toAbsolutePath().getParent();
		return parent != null ? parent : Paths.get(".").toAbsolutePath().normalize();
	}

	/**
	 * Enforces all per-entry and aggregate ZIP-hardening invariants.
	 *
	 * <p>Called AFTER the entry's bytes have been consumed (i.e., after the
	 * {@link LimitedInputStream} has been closed and the {@code LongConsumer} has updated
	 * {@code currentInflatedBytes}). The order of checks intentionally puts the cheapest
	 * arithmetic checks before the path-traversal check that performs path resolution.
	 *
	 * @param entry               the current ZIP entry
	 * @param stagingRoot         the trusted root directory for path-traversal checks
	 * @param currentEntryCount   the number of entries processed so far (1-based)
	 * @param currentInflatedBytes the running total of inflated bytes across all processed entries
	 * @throws BackupArchiveException with {@code Reason.TOO_MANY_ENTRIES} when
	 *                                {@code currentEntryCount > MAX_ENTRIES};
	 *                                {@code Reason.TOTAL_TOO_LARGE} when
	 *                                {@code currentInflatedBytes > MAX_TOTAL_BYTES};
	 *                                {@code Reason.PATH_TRAVERSAL} when the entry name escapes
	 *                                {@code stagingRoot}
	 */
	private static void assertEntrySafe(ZipEntry entry, Path stagingRoot,
			int currentEntryCount, long currentInflatedBytes) {
		if (currentEntryCount > MAX_ENTRIES) {
			throw new BackupArchiveException(Reason.TOO_MANY_ENTRIES,
					"exceeded " + MAX_ENTRIES);
		}
		if (currentInflatedBytes > MAX_TOTAL_BYTES) {
			throw new BackupArchiveException(Reason.TOTAL_TOO_LARGE,
					"exceeded " + MAX_TOTAL_BYTES + " bytes");
		}
		// Skip path-traversal check for directory entries (name ends with '/') — they resolve
		// cleanly and the traversal check would fire on trailing-slash normalization edge cases.
		if (!entry.isDirectory()) {
			PathTraversalGuard.assertWithin(stagingRoot, entry.getName());
		}
	}
}
