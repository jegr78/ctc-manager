package org.ctc.backup.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.ctc.backup.exception.UploadsSwapPreflightException;
import org.springframework.stereotype.Component;

/**
 * Proves before any database mutation that the post-commit swap can rename the uploads directory
 * into the recovery directory and a new tree into its place.
 */
@Slf4j
@Component
public class UploadsSwapPreflight {

    /**
     * Rejects a mount-root uploads directory and moves a probe directory between
     * {@code importBackupDir} and the uploads parent and back, the same atomic rename the swap needs.
     *
     * @throws UploadsSwapPreflightException when the swap would fail; nothing is left behind
     */
    public void check(Path uploadsTarget, Path importBackupDir) throws UploadsSwapPreflightException {
        Path parent = uploadsTarget.getParent();
        if (parent == null) {
            throw new UploadsSwapPreflightException(uploadsTarget + " has no parent directory");
        }
        try {
            if (Files.exists(uploadsTarget)) {
                if (!Files.isDirectory(uploadsTarget)) {
                    throw new UploadsSwapPreflightException(uploadsTarget + " is not a directory");
                }
                if (!Files.getFileStore(uploadsTarget).equals(Files.getFileStore(parent))) {
                    throw new UploadsSwapPreflightException(uploadsTarget
                            + " is a mount point and cannot be renamed; mount its parent directory instead");
                }
            }
        } catch (IOException e) {
            throw new UploadsSwapPreflightException("Cannot inspect " + uploadsTarget + ": " + e.getMessage(), e);
        }
        probeRename(parent, importBackupDir);
    }

    private void probeRename(Path uploadsParent, Path importBackupDir) throws UploadsSwapPreflightException {
        String probeName = ".swap-probe-" + UUID.randomUUID();
        Path inArchive = importBackupDir.resolve(probeName);
        Path inUploadsParent = uploadsParent.resolve(probeName);
        try {
            Files.createDirectory(inArchive);
            Files.move(inArchive, inUploadsParent, StandardCopyOption.ATOMIC_MOVE);
            Files.move(inUploadsParent, inArchive, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UploadsSwapPreflightException("Uploads in " + uploadsParent
                    + " cannot be moved atomically to and from " + importBackupDir + ": " + e, e);
        } finally {
            deleteQuietly(inArchive);
            deleteQuietly(inUploadsParent);
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("Could not remove swap probe {}: {}", path, e.getMessage());
        }
    }
}
