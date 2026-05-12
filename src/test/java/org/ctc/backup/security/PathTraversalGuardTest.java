package org.ctc.backup.security;

import org.ctc.backup.exception.BackupArchiveException;
import org.ctc.backup.exception.BackupArchiveException.Reason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.ctc.backup.security.PathTraversalGuard.assertWithin;

class PathTraversalGuardTest {

    @Test
    void givenSafeRelativeName_whenAssertWithin_thenPasses(@TempDir Path tempDir) {
        // given — two representative safe entry names used by BackupArchiveService
        // when / then — no exception thrown
        assertWithin(tempDir, "manifest.json");
        assertWithin(tempDir, "data/seasons.json");
    }

    @Test
    void givenDotDotEntry_whenAssertWithin_thenThrowsPathTraversal(@TempDir Path tempDir) {
        // given — attacker-supplied traversal attempt
        // when / then
        assertThatThrownBy(() -> assertWithin(tempDir, "../../etc/passwd"))
                .isInstanceOf(BackupArchiveException.class)
                .satisfies(ex -> {
                    BackupArchiveException bae = (BackupArchiveException) ex;
                    assertThat(bae.reason()).isEqualTo(Reason.PATH_TRAVERSAL);
                    assertThat(bae.getMessage()).contains("Path traversal detected");
                });
    }

    @Test
    void givenAbsoluteEntry_whenAssertWithin_thenThrowsPathTraversal(@TempDir Path tempDir) {
        // given — Unix-shaped absolute path; CI runs on Ubuntu so this is reliable
        // when / then
        assertThatThrownBy(() -> assertWithin(tempDir, "/etc/passwd"))
                .isInstanceOf(BackupArchiveException.class)
                .satisfies(ex -> {
                    BackupArchiveException bae = (BackupArchiveException) ex;
                    assertThat(bae.reason()).isEqualTo(Reason.PATH_TRAVERSAL);
                    assertThat(bae.getMessage()).contains("Absolute path rejected");
                });
    }

    @Test
    void givenNestedSafePath_whenAssertWithin_thenPasses(@TempDir Path tempDir) {
        // given — uploads/-mirror path shape used by BackupArchiveService:127
        // when / then — no exception thrown
        assertWithin(tempDir, "uploads/races/abc/photo.png");
    }

    @Test
    void givenNullBaseDir_whenAssertWithin_thenThrowsIllegalArgument() {
        // given / when / then
        assertThatThrownBy(() -> assertWithin(null, "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenNullCandidate_whenAssertWithin_thenThrowsIllegalArgument(@TempDir Path tempDir) {
        // given / when / then
        assertThatThrownBy(() -> assertWithin(tempDir, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenEmptyCandidate_whenAssertWithin_thenThrowsIllegalArgument(@TempDir Path tempDir) {
        // given / when / then
        assertThatThrownBy(() -> assertWithin(tempDir, ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenEntryNormalizingExactlyToBase_whenAssertWithin_thenPasses(@TempDir Path tempDir) {
        // given — "." resolves to tempDir itself; startsWith(tempDir) is true
        // when / then — harmless: no file can be extracted from an entry named "."
        assertWithin(tempDir, ".");
    }
}
