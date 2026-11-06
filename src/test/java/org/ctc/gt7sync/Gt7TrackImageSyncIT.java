package org.ctc.gt7sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.ctc.domain.model.Track;
import org.ctc.domain.repository.TrackRepository;
import org.ctc.domain.repository.CarRepository;
import org.ctc.domain.service.FileStorageService;
import org.ctc.testsupport.CtcDevSpringBootContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@CtcDevSpringBootContext
@Tag("integration")
class Gt7TrackImageSyncIT {
    @Autowired Gt7SyncService sync;
    @Autowired TrackRepository tracks;
    @Autowired CarRepository cars;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean Gt7ScraperService scraper;
    @MockitoBean FileStorageService storage;
    @MockitoBean GtPlusCarYearSource years;
    private UUID id;
    private String name;
    private TransactionTemplate transaction;

    @BeforeEach
    void setUpTrack() throws Exception {
        transaction = new TransactionTemplate(transactionManager);
        name = "Test_TrackImage_" + UUID.randomUUID();
        id = tracks.saveAndFlush(new Track(name, "Japan")).getId();
        when(scraper.scrapeTracks(true)).thenReturn(List.of(new Gt7ScraperService.ScrapedTrack(
                "4bf61b", name, "Japan", "30cb0b", "https://www.gran-turismo.com/common/dist/gt7/tracklist/assets/30cb0b-build.png")));
        when(storage.storeFromUrl(eq("tracks"), eq(id), anyString(), eq("4bf61b.png")))
                .thenReturn("/uploads/tracks/test-sugo.png");
    }

    @AfterEach
    void removeTrack() {
        if (id != null) tracks.deleteById(id);
    }

    @Test
    void givenExistingTrack_whenBackfill_thenKeepsIdentityAndPersistsImage() throws Exception {
        // given
        var count = tracks.count();
        // when
        var result = execute();
        // then
        assertThat(result.trackImagesUpdated()).isEqualTo(1);
        assertThat(result.tracksImported()).isZero();
        assertThat(result.errors()).isEmpty();
        var updated = tracks.findById(id).orElseThrow();
        assertThat(updated.getName()).isEqualTo(name);
        assertThat(updated.getCountry()).isEqualTo("Japan");
        assertThat(updated.getImageUrl()).isEqualTo("/uploads/tracks/test-sugo.png");
        assertThat(updated.getUpdatedAt()).isNotNull();
        assertThat(tracks.count()).isEqualTo(count);
    }

    @Test
    void givenManualUploadDuringDownload_whenBackfill_thenCurrentDatabaseImageWins() throws Exception {
        // given
        when(storage.storeFromUrl(eq("tracks"), eq(id), anyString(), anyString())).thenAnswer(call -> {
            transaction.executeWithoutResult(status -> {
                var current = tracks.findById(id).orElseThrow();
                current.setImageUrl("/uploads/tracks/manual.png");
                tracks.saveAndFlush(current);
            });
            return "/uploads/tracks/unused.png";
        });
        // when
        assertConcurrentChangeOutcome();
        // then
        assertThat(tracks.findById(id).orElseThrow().getImageUrl()).isEqualTo("/uploads/tracks/manual.png");
        verify(storage).delete("/uploads/tracks/unused.png");
    }

    @Test
    void givenRenameDuringDownload_whenBackfill_thenDoesNotApplyOldSourceToNewIdentity() throws Exception {
        // given
        when(storage.storeFromUrl(eq("tracks"), eq(id), anyString(), anyString())).thenAnswer(call -> {
            transaction.executeWithoutResult(status -> {
                var current = tracks.findById(id).orElseThrow();
                current.setName(name + " renamed");
                tracks.saveAndFlush(current);
            });
            return "/uploads/tracks/unused.png";
        });
        // when
        assertConcurrentChangeOutcome();
        // then
        assertThat(tracks.findById(id).orElseThrow().getImageUrl()).isNull();
        assertThat(tracks.findById(id).orElseThrow().getName()).endsWith(" renamed");
        verify(storage).delete("/uploads/tracks/unused.png");
    }

    @Test
    void givenOuterRollback_whenBackfill_thenRemovesDownloadedImageAndRevertsDatabase() throws Exception {
        // when
        transaction.executeWithoutResult(status -> {
            try {
                assertThat(execute().trackImagesUpdated()).isEqualTo(1);
                status.setRollbackOnly();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        });
        // then
        assertThat(tracks.findById(id).orElseThrow().getImageUrl()).isNull();
        verify(storage).delete("/uploads/tracks/test-sugo.png");
    }

    @Test
    void givenCarImportAndTrackSourceFailure_whenImportingTogether_thenRollsBackTheCarAsWell() throws Exception {
        // given
        var source = new Gt7ScraperService.ScrapedCar("car2147483000", "Test_TrackImage", "Test Car", "https://example.test/car.png");
        when(scraper.scrapeCars()).thenReturn(List.of(source));
        when(scraper.scrapeTracks()).thenReturn(List.of());
        var preview = sync.fetchAndPreview();
        var count = cars.count();
        when(scraper.scrapeTracks(true)).thenThrow(new java.io.IOException("Track source unavailable"));
        try {
            // when / then
            assertThatThrownBy(() -> sync.executeSync(List.of(source.gt7Id()), List.of(), List.of(), preview.previewId(), List.of(id)))
                    .isInstanceOf(java.io.IOException.class).hasMessage("Track source unavailable");
            assertThat(cars.count()).as("a failed source fetch must not commit the earlier car import").isEqualTo(count);
            assertThat(cars.findAllByGt7Id(source.gt7Id())).isEmpty();
        } finally {
            cars.findAllByGt7Id(source.gt7Id()).forEach(cars::delete);
        }
    }

    private Gt7SyncService.SyncResult execute() throws Exception {
        return sync.executeSync(List.of(), List.of(), List.of(), null, List.of(id));
    }

    protected boolean expectsSnapshotConflict() { return false; }

    private void assertConcurrentChangeOutcome() {
        var result = new java.util.concurrent.atomic.AtomicReference<Gt7SyncService.SyncResult>();
        var failure = catchThrowable(() -> result.set(execute()));
        if (expectsSnapshotConflict()) {
            assertThat(failure).isInstanceOf(org.ctc.domain.exception.BusinessRuleException.class)
                    .hasMessage("Catalog changed while importing track images. Fetch and compare again.");
        } else {
            assertThat(failure).isNull();
            assertThat(result.get().trackImagesUpdated()).isZero();
            assertThat(result.get().errors()).anyMatch(message -> message.contains("changed"));
        }
    }
}
