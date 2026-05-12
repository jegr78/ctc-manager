package org.ctc.backup.security;

import org.ctc.backup.exception.BackupArchiveException;
import org.ctc.backup.exception.BackupArchiveException.Reason;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Static utility that validates a ZIP-entry name resolves safely inside a base directory.
 *
 * <p>Extracts the canonical SECU-01 path-traversal idiom from
 * {@code FileStorageService.validatePathWithinUploadDir} (lines 153–158) into a reusable,
 * unit-tested helper so {@code BackupArchiveService} and {@code BackupImportService} can apply
 * it without pulling {@code org.ctc.domain.service} into {@code org.ctc.backup.service}
 * (D-11 discretion — own class for reuse + unit-test isolation).
 *
 * <h3>Predicate</h3>
 * {@code baseDir.toAbsolutePath().normalize().resolve(candidate).normalize().startsWith(absoluteBase)}
 * — bit-identical to the {@code FileStorageService:153-158} predicate (REQUIREMENTS SECU-01:
 * "Wiederverwendung statt Duplikat").
 *
 * <h3>Note on {@code toRealPath()} vs {@code toAbsolutePath().normalize()}</h3>
 * This class intentionally uses {@code toAbsolutePath().normalize()}, NOT {@code toRealPath()}.
 * {@code toRealPath()} would require the path to exist on disk AND throws checked
 * {@code IOException}. The production base directory may not yet exist at validation time,
 * and {@code FileStorageService:30} uses the same {@code toAbsolutePath().normalize()} idiom —
 * consistency is the mandate (SECU-01 reuse). The symlink-TOCTOU risk is accepted per the
 * threat model (T-74-02-05): {@code baseDir} is application-owned; no third-party can plant
 * symlinks there.
 */
public final class PathTraversalGuard {

    private PathTraversalGuard() {
        /* utility class — no instances */
    }

    /**
     * Asserts that {@code candidateEntryName} resolves inside {@code baseDir} after
     * {@code toAbsolutePath().normalize()}.
     *
     * <p>Rejects:
     * <ul>
     *   <li>Absolute entry names (e.g. {@code /etc/passwd}) — regardless of whether they
     *       would normalize inside {@code baseDir}.</li>
     *   <li>Relative names that traverse out of {@code baseDir} after normalization
     *       (e.g. {@code ../../etc/passwd}).</li>
     * </ul>
     *
     * @param baseDir            the trusted root directory (may be relative — resolved internally
     *                           via {@code toAbsolutePath().normalize()})
     * @param candidateEntryName the ZIP-entry name to validate; must be non-null and non-empty
     * @throws IllegalArgumentException if {@code baseDir} or {@code candidateEntryName} is
     *                                  {@code null}, or {@code candidateEntryName} is empty
     *                                  (programmer-error guard — not a security event)
     * @throws BackupArchiveException   with {@link Reason#PATH_TRAVERSAL} if the name is
     *                                  absolute or resolves outside {@code baseDir}
     */
    public static void assertWithin(Path baseDir, String candidateEntryName) {
        // Programmer-error guard: fires before any path math
        if (baseDir == null || candidateEntryName == null || candidateEntryName.isEmpty()) {
            throw new IllegalArgumentException("baseDir and candidateEntryName must be non-null/non-empty");
        }

        // Resolve baseDir to an absolute, normalized path (mirrors FileStorageService:30)
        Path absoluteBase = baseDir.toAbsolutePath().normalize();

        // Reject absolute entry names explicitly (e.g. /etc/passwd)
        if (Paths.get(candidateEntryName).isAbsolute()) {
            throw new BackupArchiveException(Reason.PATH_TRAVERSAL,
                    "Absolute path rejected: " + candidateEntryName);
        }

        // Resolve the candidate relative to absoluteBase and normalize (collapses .. segments)
        Path resolved = absoluteBase.resolve(candidateEntryName).normalize();

        // Reject if the normalized path escapes the base directory
        if (!resolved.startsWith(absoluteBase)) {
            throw new BackupArchiveException(Reason.PATH_TRAVERSAL,
                    "Path traversal detected: candidate=" + candidateEntryName + " baseDir=" + absoluteBase);
        }
        // Silent return on success
    }
}
