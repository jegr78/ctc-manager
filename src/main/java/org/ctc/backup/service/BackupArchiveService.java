package org.ctc.backup.service;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
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

import static org.ctc.backup.service.BackupImportLimits.*;

/**
 * Stateless ZIP plumbing for the backup export and import pipeline.
 *
 * <p>Single public method {@link #writeZip(OutputStream, Instant)} streams a self-contained
 * backup archive into the caller's {@link OutputStream}. No intermediate
 * {@code ByteArrayOutputStream}, so the entire pipeline is true streaming.
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
 * <h2>Reader methods — manifest, counting, uploads extraction</h2>
 *
 * <p>Three streaming reader methods complement the write path:
 * <ul>
 *   <li>{@link #readManifest(Path)} — opens an export ZIP, asserts the first entry is
 *       literally {@code manifest.json}, and deserializes it via the qualified
 *       {@code backupObjectMapper}.</li>
 *   <li>{@link #countDataEntries(Path)} — walks every {@code data/<slug>.json} entry via a
 *       Jackson {@code JsonParser} token-loop (no full-document buffering) and returns a
 *       per-table row-count map.</li>
 *   <li>{@link #countUploadFiles(Path)} — counts every non-directory entry under
 *       {@code uploads/} by draining each entry through a discard buffer.</li>
 * </ul>
 *
 * <p>All three methods route every ZIP entry through a single hardened helper
 * {@link #assertEntrySafe(ZipEntry, Path, int, long)} that enforces ZIP-Slip defense,
 * per-entry inflate-size cap (50 MB), total inflate-size cap (500 MB), and maximum entry
 * count (50 000). The reader does not extract anything to disk — it is a pure counting and
 * parsing pass over the inflated byte stream.
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
	// Reader methods — manifest, data counts, upload extraction
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
					// Wrap zis in a non-closing view so that limited.close() does not cascade
					// to ZipInputStream.close() (which would prevent the next getNextEntry()).
					LimitedInputStream limited = new LimitedInputStream(
							nonClosingView(zis), MAX_ENTRY_BYTES,
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
						// Closing limited fires the LongConsumer (updating inflatedAcc[0]).
						// The non-closing view ensures the ZipInputStream is NOT closed here.
						limited.close();
						parser.close();
					}
				}

				assertEntrySafe(entry, stagingRoot, entryCount, inflatedAcc[0]);
			}
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
					// Wrap zis in a non-closing view so that limited.close() does not cascade
					// to ZipInputStream.close() when the try-with-resources exits.
					try (LimitedInputStream limited = new LimitedInputStream(
							nonClosingView(zis), MAX_ENTRY_BYTES,
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
		} catch (IOException ex) {
			throw new BackupArchiveException(Reason.MANIFEST_INVALID, "ZIP read failure", ex);
		}

		log.info("Backup upload entries counted: count={}", uploadCount);
		return uploadCount;
	}

	// =========================================================================
	// Uploads extraction
	// =========================================================================

	/**
	 * Extracts every {@code uploads/<rel>} entry of the backup ZIP at {@code zipPath} to the
	 * given destination directory {@code destDir}. The {@code uploads/} prefix is stripped from
	 * the entry name so the on-disk layout under {@code destDir} mirrors the original
	 * {@code app.upload-dir/} tree.
	 *
	 * <p>Called from {@code BackupImportService.execute(UUID)} INSIDE the {@code @Transactional}
	 * boundary, BEFORE the post-commit move triple. On wipe-or-restore rollback the
	 * partially-extracted {@code uploads-new/} is cleaned by the orchestrator's catch-block.
	 *
	 * <p><strong>Hardening invariants (per entry):</strong>
	 * <ul>
	 *   <li>{@link PathTraversalGuard#assertWithin(Path, String)} on the {@code uploads/}-stripped
	 *       relative path against {@code destDir} (ZIP-Slip defense).</li>
	 *   <li>Per-entry inflate cap via {@link LimitedInputStream} with
	 *       {@link BackupImportLimits#MAX_ENTRY_BYTES} (50 MB).</li>
	 *   <li>Aggregate inflate cap via the running {@code totalInflated} counter against
	 *       {@link BackupImportLimits#MAX_TOTAL_BYTES} (500 MB).</li>
	 *   <li>{@link BackupImportLimits#MAX_ENTRIES} entry-count cap on the overall ZIP.</li>
	 * </ul>
	 *
	 * <p>Skips directory entries silently (parent directories are materialized on demand via
	 * {@link Files#createDirectories(Path, java.nio.file.attribute.FileAttribute[])}). Entries
	 * outside the {@code uploads/} prefix are also skipped — manifest and {@code data/*.json}
	 * entries are out of scope here; those are read by {@link #readManifest(Path)} /
	 * {@code countDataEntries(...)}.
	 *
	 * @param zipPath path to the staged backup ZIP file
	 * @param destDir destination directory under which the {@code uploads/}-rooted tree is
	 *                materialized; must already exist (the caller creates it)
	 * @throws BackupArchiveException with traversal / bomb / count reasons per the invariants
	 *                                above
	 * @throws IOException            for plain I/O failures reading {@code zipPath} or writing
	 *                                under {@code destDir}
	 */
	public void extractUploadsTo(Path zipPath, Path destDir) throws BackupArchiveException, IOException {
		Path absoluteDest = destDir.toAbsolutePath().normalize();
		Files.createDirectories(absoluteDest);

		long[] inflatedAcc = new long[]{0L};
		int entryCount = 0;
		int extracted = 0;

		try (ZipInputStream zis = openHardened(zipPath)) {
			ZipEntry entry;
			while ((entry = zis.getNextEntry()) != null) {
				String name = entry.getName();
				entryCount++;

				if (!name.startsWith("uploads/") || entry.isDirectory()) {
					// Still enforce aggregate caps so a hostile ZIP with 100k empty data
					// entries does not slip past the entry-count guard.
					assertEntrySafe(entry, absoluteDest, entryCount, inflatedAcc[0]);
					continue;
				}

				// Strip the "uploads/" prefix; reject entries that strip to empty (e.g. bare
				// "uploads/" directory entry that did not have isDirectory()==true).
				String relativePath = name.substring("uploads/".length());
				if (relativePath.isEmpty()) {
					continue;
				}

				// Validate the stripped path resolves inside destDir (ZIP-Slip defense).
				PathTraversalGuard.assertWithin(absoluteDest, relativePath);

				// WR-07: pre-check the entry-count cap BEFORE materializing the file so a
				// hostile ZIP with MAX_ENTRIES+1 entries does not bloat the disk with the
				// first MAX_ENTRIES files before the post-check fires. The total-byte cap
				// remains fundamentally post-write (we only learn inflated size by inflating);
				// LimitedInputStream's per-entry cap (50 MB) bounds the per-write damage.
				if (entryCount > MAX_ENTRIES) {
					throw new BackupArchiveException(Reason.TOO_MANY_ENTRIES,
							"exceeded " + MAX_ENTRIES);
				}

				Path target = absoluteDest.resolve(relativePath).normalize();
				// NP: target.getParent() is called only inside the null-guard at the preceding line —
				// SpotBugs reports a false positive because it evaluates each getParent() call independently.
				// See config/spotbugs-exclude.xml BackupArchiveService NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE.
				if (target.getParent() != null) {
					Files.createDirectories(target.getParent());
				}

				final String entryName = name;
				LimitedInputStream limited = new LimitedInputStream(
						nonClosingView(zis), MAX_ENTRY_BYTES,
						finalBytes -> {
							inflatedAcc[0] += finalBytes;
							if (finalBytes >= MAX_ENTRY_BYTES) {
								log.warn("Backup ZIP upload entry exceeds limit: name={}, limit={} bytes",
										entryName, MAX_ENTRY_BYTES);
							}
						});
				try {
					Files.copy(limited, target, StandardCopyOption.REPLACE_EXISTING);
				} finally {
					// Fires the LongConsumer (updating inflatedAcc[0]) and shields the
					// ZipInputStream from being closed (nonClosingView wrapper).
					limited.close();
				}
				extracted++;

				assertEntrySafe(entry, absoluteDest, entryCount, inflatedAcc[0]);
			}
		}

		log.info("Backup uploads extracted: zip={}, destDir={}, extractedFiles={}, totalInflated={} bytes",
				zipPath.getFileName(), absoluteDest, extracted, inflatedAcc[0]);
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
	 * Returns a view of {@code delegate} whose {@link InputStream#close()} is a no-op.
	 *
	 * <p>This prevents {@link LimitedInputStream#close()} from cascading to
	 * {@link ZipInputStream#close()} when the {@code LimitedInputStream} wrapping a single
	 * ZIP entry is closed after parsing. Without this guard, closing the
	 * {@code LimitedInputStream} would close the underlying {@code ZipInputStream}, making
	 * the next {@link ZipInputStream#getNextEntry()} call throw {@code "Stream closed"}.
	 *
	 * <p>The {@link LimitedInputStream}'s {@code LongConsumer onClose} fires correctly on
	 * {@link #close()} regardless — the guard only suppresses the {@code super.close()} cascade.
	 *
	 * @param delegate the stream to shield from close propagation
	 * @return a {@link FilterInputStream} wrapper that ignores {@code close()} calls
	 */
	private static InputStream nonClosingView(InputStream delegate) {
		return new FilterInputStream(delegate) {
			@Override
			public void close() {
				// intentionally no-op: the ZipInputStream lifecycle is managed by
				// the enclosing try-with-resources in the calling method.
			}
		};
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
	// PATH_TRAVERSAL defense: PathTraversalGuard.assertWithin() is the sanitizer; find-sec-bugs
	// cannot trace the defense through the delegated utility call. See config/spotbugs-exclude.xml
	// BackupArchiveService PATH_TRAVERSAL_IN entry for the corresponding suppression rationale.
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
