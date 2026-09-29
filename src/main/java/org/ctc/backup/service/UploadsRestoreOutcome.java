package org.ctc.backup.service;

import java.nio.file.Path;

/**
 * Result of the post-commit uploads swap for one import.
 *
 * @param restored    {@code true} when the restored uploads tree is live
 * @param recoveryDir directory holding the pre-import auto-backup and {@code uploads-old}
 * @param failure     what went wrong; {@code null} when restored
 */
public record UploadsRestoreOutcome(boolean restored, Path recoveryDir, String failure) {

    static UploadsRestoreOutcome restored(Path recoveryDir) {
        return new UploadsRestoreOutcome(true, recoveryDir, null);
    }

    static UploadsRestoreOutcome failed(Path recoveryDir, String failure) {
        return new UploadsRestoreOutcome(false, recoveryDir, failure);
    }
}
