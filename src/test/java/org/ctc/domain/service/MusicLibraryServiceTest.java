package org.ctc.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.MusicTrack;
import org.ctc.domain.repository.MusicTrackRepository;
import org.ctc.media.MediaTool;
import org.ctc.media.MediaToolException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
class MusicLibraryServiceTest {

    @Mock MusicTrackRepository repository;
    @Mock FileStorageService files;
    @Mock MediaTool media;
    @InjectMocks MusicLibraryService service;

    @Test
    void givenId3TaggedMp3_whenUpload_thenStoredWithProbedDuration() throws Exception {
        // given
        var file = mp3("song.mp3", new byte[] {'I', 'D', '3', 4, 0, 0});
        when(files.storeBytes(eq("music"), any(UUID.class), eq("track.mp3"), any())).thenReturn("/uploads/music/x/a_song.mp3");
        when(files.resolve("/uploads/music/x/a_song.mp3")).thenReturn(Path.of("/tmp/a_song.mp3"));
        when(media.probeDuration(Path.of("/tmp/a_song.mp3"))).thenReturn(Duration.ofMillis(151_600));
        when(repository.saveAndFlush(any(MusicTrack.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        var stored = service.upload(file, " Heat Warning ", "Jeremy Korpas", "  ");

        // then
        assertThat(stored.getTitle()).isEqualTo("Heat Warning");
        assertThat(stored.getArtist()).isEqualTo("Jeremy Korpas");
        assertThat(stored.getCredit()).as("blank credit is not stored").isNull();
        assertThat(stored.getDurationSeconds()).as("duration rounds to whole seconds").isEqualTo(152);
        assertThat(stored.getFilePath()).isEqualTo("/uploads/music/x/a_song.mp3");
        verify(files).deleteOnRollback("/uploads/music/x/a_song.mp3");
    }

    @Test
    void givenFrameSyncMp3WithoutTag_whenUpload_thenAccepted() throws Exception {
        // given
        var file = mp3("raw.mp3", new byte[] {(byte) 0xFF, (byte) 0xFB, (byte) 0x90, 0});
        when(files.storeBytes(anyString(), any(UUID.class), anyString(), any())).thenReturn("/uploads/music/x/raw.mp3");
        when(files.resolve(anyString())).thenReturn(Path.of("/tmp/raw.mp3"));
        when(media.probeDuration(any())).thenReturn(Duration.ofSeconds(90));
        when(repository.saveAndFlush(any(MusicTrack.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        var stored = service.upload(file, "Raw", "Artist", null);

        // then
        assertThat(stored.getDurationSeconds()).isEqualTo(90);
    }

    @Test
    void givenAdtsAacRenamedToMp3_whenUpload_thenRejected() {
        // given
        var file = mp3("aac.mp3", new byte[] {(byte) 0xFF, (byte) 0xF1, 0x50, (byte) 0x80});

        // when / then
        assertThatThrownBy(() -> service.upload(file, "Aac", "Artist", null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Only MP3 files are accepted.");
    }

    @Test
    void givenPngRenamedToMp3_whenUpload_thenRejectedWithMessage() throws Exception {
        // given
        var file = mp3("fake.mp3", new byte[] {(byte) 0x89, 'P', 'N', 'G'});

        // when / then
        assertThatThrownBy(() -> service.upload(file, "Fake", "Artist", null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("Only MP3 files are accepted.");
        verify(files, never()).storeBytes(anyString(), any(), anyString(), any());
    }

    @Test
    void givenFileOver20Mb_whenUpload_thenRejected() {
        // given
        byte[] big = new byte[MusicLibraryService.MAX_UPLOAD_BYTES + 1];
        big[0] = 'I';
        big[1] = 'D';
        big[2] = '3';
        var file = mp3("big.mp3", big);

        // when / then
        assertThatThrownBy(() -> service.upload(file, "Big", "Artist", null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("20 MB");
    }

    @Test
    void givenProbeFailure_whenUpload_thenStoredFileRemovedOnRollback() throws Exception {
        // given
        var file = mp3("broken.mp3", new byte[] {'I', 'D', '3', 4});
        when(files.storeBytes(anyString(), any(UUID.class), anyString(), any())).thenReturn("/uploads/music/x/broken.mp3");
        when(files.resolve(anyString())).thenReturn(Path.of("/tmp/broken.mp3"));
        when(media.probeDuration(any())).thenThrow(new MediaToolException("ffprobe failed"));

        // when / then
        assertThatThrownBy(() -> service.upload(file, "Broken", "Artist", null))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessage("The MP3 file could not be read.");
        verify(files).deleteOnRollback("/uploads/music/x/broken.mp3");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void givenNullId_whenPick_thenRandomTrackFromLibrary() {
        // given
        var first = track("A");
        var second = track("B");
        when(repository.findAllByOrderByTitleAscIdAsc()).thenReturn(List.of(first, second));

        // when
        var picked = service.pick(null, new Random(7));

        // then
        assertThat(picked).isPresent();
        assertThat(List.of(first, second)).contains(picked.get());
    }

    @Test
    void givenKnownId_whenPick_thenThatTrack() {
        // given
        var chosen = track("A");
        when(repository.findById(chosen.getId())).thenReturn(Optional.of(chosen));

        // when / then
        assertThat(service.pick(chosen.getId(), new Random())).contains(chosen);
    }

    @Test
    void givenEmptyLibrary_whenPick_thenEmpty() {
        // given
        when(repository.findAllByOrderByTitleAscIdAsc()).thenReturn(List.of());

        // when / then
        assertThat(service.pick(null, new Random())).isEmpty();
    }

    @Test
    void givenTrack_whenDelete_thenRowRemovedAndFileDeletedAfterCommit() {
        // given
        var existing = track("A");
        when(repository.findById(existing.getId())).thenReturn(Optional.of(existing));

        // when
        service.delete(existing.getId());

        // then
        verify(repository).delete(existing);
        verify(files).deleteAfterCommit(existing.getFilePath());
    }

    private static MockMultipartFile mp3(String name, byte[] content) {
        return new MockMultipartFile("file", name, "audio/mpeg", content);
    }

    private static MusicTrack track(String title) {
        var track = new MusicTrack();
        track.setId(UUID.randomUUID());
        track.setTitle(title);
        track.setArtist("Artist");
        track.setFilePath("/uploads/music/" + title + ".mp3");
        track.setDurationSeconds(120);
        return track;
    }
}
