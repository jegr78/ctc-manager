package org.ctc.backup.schema;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MusicTrackBackupValidatorTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String PATH = "/uploads/music/a/b_song.mp3";

    @Test
    void givenValidRowWithArchivedFile_whenValidate_thenAccepted() {
        // given
        var validator = new MusicTrackBackupValidator(26, Set.of("uploads/music/a/b_song.mp3"));

        // when / then
        assertThatCode(() -> {
            validator.accept("music_tracks", row());
            validator.validate();
        }).doesNotThrowAnyException();
    }

    @Test
    void givenSchema25WithMusicRow_whenAccept_thenRejected() {
        // given
        var validator = new MusicTrackBackupValidator(25, Set.of("uploads/music/a/b_song.mp3"));

        // when / then
        assertThatThrownBy(() -> validator.accept("music_tracks", row()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("backup schema 26");
    }

    @Test
    void givenRowWhoseFileIsNotArchived_whenAccept_thenRejected() {
        // given
        var validator = new MusicTrackBackupValidator(26, Set.of());

        // when / then
        assertThatThrownBy(() -> validator.accept("music_tracks", row()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is missing from the archive");
    }

    @Test
    void givenTitleOf151Chars_whenAccept_thenRejected() {
        // given
        var validator = new MusicTrackBackupValidator(26, Set.of("uploads/music/a/b_song.mp3"));
        var row = row().put("title", "T".repeat(151));

        // when / then
        assertThatThrownBy(() -> validator.accept("music_tracks", row)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenZeroDuration_whenAccept_thenRejected() {
        // given
        var validator = new MusicTrackBackupValidator(26, Set.of("uploads/music/a/b_song.mp3"));
        var row = row().put("durationSeconds", 0);

        // when / then
        assertThatThrownBy(() -> validator.accept("music_tracks", row)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenDuplicateId_whenAccept_thenRejected() {
        // given
        var validator = new MusicTrackBackupValidator(26, Set.of("uploads/music/a/b_song.mp3"));
        var row = row();
        validator.accept("music_tracks", row);

        // when / then
        assertThatThrownBy(() -> validator.accept("music_tracks", row)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenOtherTable_whenAccept_thenIgnored() {
        // given
        var validator = new MusicTrackBackupValidator(25, Set.of());

        // when / then
        assertThatCode(() -> validator.accept("cars", JSON.createObjectNode())).doesNotThrowAnyException();
    }

    private static ObjectNode row() {
        return JSON.createObjectNode()
                .put("id", UUID.randomUUID().toString())
                .put("title", "Heat Warning")
                .put("artist", "Jeremy Korpas")
                .putNull("credit")
                .put("filePath", PATH)
                .put("durationSeconds", 150)
                .put("createdAt", "2026-11-16T10:00:00")
                .put("updatedAt", "2026-11-16T10:00:00");
    }
}
