package org.ctc.gt7sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.ctc.domain.model.Track;
import org.ctc.domain.repository.CarRepository;
import org.ctc.domain.repository.TrackRepository;
import org.ctc.domain.service.CarMetadataService;
import org.ctc.domain.service.FileStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class Gt7TrackImageSyncTest {
    private static final String NAME = "SUGO International Racing Course";
    private static final String IMAGE = "https://www.gran-turismo.com/common/dist/gt7/tracklist/assets/30cb0b-C2qzNqBm.png";
    @Mock Gt7ScraperService scraperService;
    @Mock CarRepository carRepository;
    @Mock TrackRepository trackRepository;
    @Mock FileStorageService fileStorageService;
    @Mock CarMetadataService metadataService;
    @Mock GtPlusCarYearSource yearSource;
    @Mock Gt7SyncPreviewStore previewStore;
    @InjectMocks Gt7SyncService sync;

    @Test
    void givenMissingAndExistingImages_whenCompare_thenOnlyMissingImagesAreSelectable() throws Exception {
        // given
        var missing = track(NAME, null);
        var manual = track("Test manual track", "/uploads/manual.png");
        when(scraperService.scrapeCars()).thenReturn(List.of());
        when(scraperService.scrapeTracks()).thenReturn(List.of(source(IMAGE),
                new Gt7ScraperService.ScrapedTrack("abcdef", manual.getName(), "Japan", "30cb0b", null)));
        when(trackRepository.findByName(NAME)).thenReturn(Optional.of(missing));
        when(trackRepository.findByName(manual.getName())).thenReturn(Optional.of(manual));
        // when
        var preview = sync.fetchAndPreview();
        // then
        assertThat(preview.getTrackImageUpdateCount()).isEqualTo(1);
        assertThat(preview.getExistingTrackCount()).isEqualTo(2);
        assertThat(preview.tracks()).anySatisfy(entry -> {
            assertThat(entry.existingTrackId()).isEqualTo(missing.getId());
            assertThat(entry.status()).isEqualTo(Gt7SyncPreview.SyncStatus.IMAGE_UPDATE);
        });
        assertThat(preview.tracks()).anySatisfy(entry -> {
            assertThat(entry.existingTrackId()).isEqualTo(manual.getId());
            assertThat(entry.status()).isEqualTo(Gt7SyncPreview.SyncStatus.EXISTS);
        });
        verifyNoInteractions(fileStorageService);
    }

    @Test
    void givenSelectedExistingTrackTwice_whenBackfill_thenUpdatesItsImageOnceWithoutCreatingTrack() throws Exception {
        // given
        var track = eligible(IMAGE);
        when(fileStorageService.storeFromUrl("tracks", track.getId(), IMAGE, "4bf61b.png"))
                .thenReturn("/uploads/tracks/sugo.png");
        when(trackRepository.fillMissingImage(eq(track.getId()), eq(NAME), eq("/uploads/tracks/sugo.png"), any()))
                .thenReturn(1);
        // when
        var result = sync.executeSync(List.of(), List.of(), List.of(), null, List.of(track.getId(), track.getId()));
        // then
        assertThat(result.tracksImported()).isZero();
        assertThat(result.trackImagesUpdated()).isEqualTo(1);
        assertThat(result.errors()).isEmpty();
        verify(fileStorageService).storeFromUrl("tracks", track.getId(), IMAGE, "4bf61b.png");
        verify(trackRepository, never()).save(any());
    }

    @Test
    void givenExistingManualImage_whenSelectedForBackfill_thenPreservesIt() throws Exception {
        // given
        var track = eligible(IMAGE);
        track.setImageUrl("/uploads/manual.png");
        // when
        var result = execute(track);
        // then
        assertThat(result.trackImagesUpdated()).isZero();
        assertThat(track.getImageUrl()).isEqualTo("/uploads/manual.png");
        verifyNoInteractions(fileStorageService);
        verify(trackRepository, never()).fillMissingImage(any(), any(), any(), any());
    }

    @Test
    void givenUnknownExistingId_whenBackfill_thenReportsItWithoutCreatingTrack() throws Exception {
        // given
        when(scraperService.scrapeTracks(true)).thenReturn(List.of(source(IMAGE)));
        // when
        var reference = new java.util.concurrent.atomic.AtomicReference<Gt7SyncService.SyncResult>();
        assertThatNoException().isThrownBy(() -> reference.set(
                sync.executeSync(List.of(), List.of(), List.of(), null, List.of(UUID.randomUUID()))));
        var result = reference.get();
        // then
        assertThat(result.errors()).anyMatch(message -> message.contains("no longer exists"));
        assertThat(result.trackImagesUpdated()).isZero();
        verifyNoInteractions(fileStorageService);
        verify(trackRepository, never()).save(any());
    }

    @Test
    void givenRenamedTrack_whenBackfill_thenDoesNotGuessItsSource() throws Exception {
        // given
        var track = eligible(IMAGE);
        track.setName("Test renamed track");
        // when
        var result = execute(track);
        // then
        assertThat(result.errors()).anyMatch(message -> message.contains("source"));
        assertThat(result.trackImagesUpdated()).isZero();
        verifyNoInteractions(fileStorageService);
    }

    @Test
    void givenAmbiguousTrackSource_whenBackfill_thenDoesNotChooseARecord() throws Exception {
        // given
        var track = eligible(IMAGE);
        when(scraperService.scrapeTracks(true)).thenReturn(List.of(source(IMAGE), source(IMAGE)));
        // when
        var result = execute(track);
        // then
        assertThat(result.errors()).contains("Track source is missing or ambiguous for " + NAME);
        assertThat(result.trackImagesUpdated()).isZero();
        verifyNoInteractions(fileStorageService);
    }

    @Test
    void givenMissingLogoMapping_whenBackfill_thenReportsWarningAndLeavesTrackUnchanged() throws Exception {
        // given
        var track = eligible(null);
        // when
        var result = execute(track);
        // then
        assertThat(result.errors()).contains("Image unavailable for " + NAME);
        assertThat(result.trackImagesUpdated()).isZero();
        verifyNoInteractions(fileStorageService);
    }

    @Test
    void givenDownloadFailure_whenBackfill_thenReportsWarningAndLeavesTrackUnchanged() throws Exception {
        // given
        var track = eligible(IMAGE);
        when(fileStorageService.storeFromUrl(any(), any(), any(), any())).thenThrow(new IOException("HTTP 404"));
        // when
        var result = execute(track);
        // then
        assertThat(result.errors()).contains("Image download failed for " + NAME);
        assertThat(result.trackImagesUpdated()).isZero();
        verify(trackRepository, never()).fillMissingImage(any(), any(), any(), any());
    }

    @Test
    void givenRejectedDownloadUrl_whenBackfill_thenReportsWarningWithoutMutation() throws Exception {
        // given
        var track = eligible(IMAGE);
        when(fileStorageService.storeFromUrl(any(), any(), any(), any())).thenThrow(new IllegalArgumentException("URL blocked"));
        // when
        var result = execute(track);
        // then
        assertThat(result.errors()).contains("Image download failed for " + NAME);
        assertThat(result.trackImagesUpdated()).isZero();
        verify(trackRepository, never()).fillMissingImage(any(), any(), any(), any());
    }

    @Test
    void givenImageOrIdentityChangedDuringDownload_whenConditionalUpdateLoses_thenDiscardsDownloadedFile() throws Exception {
        // given
        var track = eligible(IMAGE);
        when(fileStorageService.storeFromUrl(any(), any(), any(), any())).thenReturn("/uploads/unused.png");
        when(trackRepository.fillMissingImage(eq(track.getId()), eq(NAME), eq("/uploads/unused.png"), any())).thenReturn(0);
        // when
        var result = execute(track);
        // then
        assertThat(result.errors()).anyMatch(message -> message.contains("changed"));
        assertThat(result.trackImagesUpdated()).isZero();
        verify(fileStorageService).delete("/uploads/unused.png");
        verify(trackRepository, never()).save(any());
    }

    @Test
    void givenNewTrackWithoutImage_whenImport_thenReportsIncompleteImageImport() throws Exception {
        // given
        when(scraperService.scrapeTracks(true)).thenReturn(List.of(source(null)));
        when(trackRepository.save(any())).thenAnswer(call -> call.getArgument(0));
        // when
        var result = sync.executeSync(List.of(), List.of(NAME));
        // then
        assertThat(result.tracksImported()).isEqualTo(1);
        assertThat(result.errors()).contains("Image unavailable for " + NAME);
        verifyNoInteractions(fileStorageService);
    }

    @Test
    void givenTwoDownloadsAndDatabaseFailure_whenTransactionRollsBack_thenRemovesBothDownloads() throws Exception {
        // given
        var first = track(NAME, null);
        var second = track("Test second track", null);
        when(scraperService.scrapeTracks(true)).thenReturn(List.of(source(IMAGE),
                new Gt7ScraperService.ScrapedTrack("abcdef", second.getName(), "Japan", "30cb0b", IMAGE)));
        when(trackRepository.findById(first.getId())).thenReturn(Optional.of(first));
        when(trackRepository.findById(second.getId())).thenReturn(Optional.of(second));
        when(fileStorageService.storeFromUrl(eq("tracks"), eq(first.getId()), any(), any())).thenReturn("/uploads/first.png");
        when(fileStorageService.storeFromUrl(eq("tracks"), eq(second.getId()), any(), any())).thenReturn("/uploads/second.png");
        when(trackRepository.fillMissingImage(any(), any(), any(), any())).thenThrow(new IllegalStateException("database unavailable"));
        TransactionSynchronizationManager.initSynchronization();
        try {
            // when / then
            assertThatThrownBy(() -> sync.executeSync(List.of(), List.of(), List.of(), null, List.of(first.getId(), second.getId())))
                    .isInstanceOf(IllegalStateException.class).hasMessage("database unavailable");
            TransactionSynchronizationManager.getSynchronizations().forEach(callback -> callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            verify(fileStorageService).delete("/uploads/first.png");
            verify(fileStorageService).delete("/uploads/second.png");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private Track eligible(String image) throws IOException {
        var track = track(NAME, null);
        when(scraperService.scrapeTracks(true)).thenReturn(List.of(source(image)));
        when(trackRepository.findById(track.getId())).thenReturn(Optional.of(track));
        return track;
    }

    private Gt7SyncService.SyncResult execute(Track track) throws IOException {
        var reference = new java.util.concurrent.atomic.AtomicReference<Gt7SyncService.SyncResult>();
        assertThatNoException().isThrownBy(() -> reference.set(
                sync.executeSync(List.of(), List.of(), List.of(), null, List.of(track.getId()))));
        return reference.get();
    }

    private Track track(String name, String image) {
        var track = new Track(name, "Japan");
        track.setId(UUID.randomUUID());
        track.setImageUrl(image);
        return track;
    }

    private Gt7ScraperService.ScrapedTrack source(String image) {
        return new Gt7ScraperService.ScrapedTrack("4bf61b", NAME, "Japan", "30cb0b", image);
    }
}
