package org.ctc.gt7sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.ctc.domain.exception.BusinessRuleException;
import org.ctc.domain.model.Track;
import org.ctc.domain.repository.TrackRepository;
import org.ctc.domain.service.CatalogImageService;
import org.ctc.domain.service.FileStorageService;
import org.ctc.gt7sync.GtEngineTrackMapService.Outcome;
import org.ctc.integration.ExternalIntegrationDisabledException;
import org.ctc.integration.OutboundPolicy;
import org.ctc.testsupport.TestImages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.CannotCreateTransactionException;

@ExtendWith(MockitoExtension.class)
class GtEngineTrackMapServiceTest {
    static final String MONZA_URL = "https://gt-engine.com/gt7/tracks/images/md/monza.png";
    @Mock TrackRepository tracks;
    @Mock FileStorageService files;
    @Mock CatalogImageService gallery;
    @Mock OutboundPolicy outbound;
    @Mock GtEngineTrackMapService.PageFetcher pages;
    GtEngineTrackMapService service;
    final Track monza = track("Autodromo Nazionale Monza");
    final byte[] map = TestImages.png(1500, 1500);

    @BeforeEach
    void setUp() {
        service = new GtEngineTrackMapService(tracks, files, gallery, outbound, pages);
    }

    @Test
    void givenTrackWithoutAMap_whenLoading_thenStoreTheReviewedMapAsNewLayout() throws IOException {
        // given
        when(tracks.findById(monza.getId())).thenReturn(Optional.of(monza));
        when(files.downloadTrackMap(MONZA_URL)).thenReturn(map);
        when(gallery.gtEngineMapPath(monza.getId())).thenReturn(Optional.empty());
        when(files.storeBytes("track-images", monza.getId(), "gtengine_monza.png", map)).thenReturn("/uploads/track-images/m/new.png");
        when(gallery.applyGtEngineMap(monza.getId(), "/uploads/track-images/m/new.png", 1500, 1500)).thenReturn(false);
        // when
        var outcome = service.load(monza.getId());
        // then
        assertThat(outcome).isEqualTo(Outcome.ADDED);
        assertThat(outcome.message()).isEqualTo("GT-Engine map added.");
    }

    @Test
    void givenIdenticalStoredMap_whenLoading_thenKeepItWithoutStoring() throws IOException {
        // given
        when(tracks.findById(monza.getId())).thenReturn(Optional.of(monza));
        when(files.downloadTrackMap(MONZA_URL)).thenReturn(map);
        when(gallery.gtEngineMapPath(monza.getId())).thenReturn(Optional.of("/uploads/track-images/m/old.png"));
        when(files.hasContent("/uploads/track-images/m/old.png", map)).thenReturn(true);
        // when
        var outcome = service.load(monza.getId());
        // then
        assertThat(outcome).isEqualTo(Outcome.UNCHANGED);
        verify(files, never()).storeBytes(anyString(), any(), anyString(), any());
    }

    @Test
    void givenTrackWithoutReviewedLayout_whenLoading_thenRejectBeforeAnyDownload() throws IOException {
        // given
        var sugo = track("SUGO International Racing Course");
        when(tracks.findById(sugo.getId())).thenReturn(Optional.of(sugo));
        // when / then
        assertThatThrownBy(() -> service.load(sugo.getId())).isInstanceOf(BusinessRuleException.class).hasMessage("GT-Engine has no map for this track");
        verify(files, never()).downloadTrackMap(anyString());
    }

    @Test
    void givenFailedDownload_whenLoading_thenReportTheReasonWithoutStoring() throws IOException {
        // given
        when(tracks.findById(monza.getId())).thenReturn(Optional.of(monza));
        when(files.downloadTrackMap(MONZA_URL)).thenThrow(new IOException("Downloaded file is not a PNG image"));
        // when / then
        assertThatThrownBy(() -> service.load(monza.getId())).isInstanceOf(BusinessRuleException.class)
                .hasMessage("The GT-Engine map could not be loaded: Downloaded file is not a PNG image");
        verify(files, never()).storeBytes(anyString(), any(), anyString(), any());
    }

    @Test
    void givenTransactionThatCannotStart_whenApplying_thenDeleteTheNewFile() throws IOException {
        // given
        when(tracks.findById(monza.getId())).thenReturn(Optional.of(monza));
        when(files.downloadTrackMap(MONZA_URL)).thenReturn(map);
        when(gallery.gtEngineMapPath(monza.getId())).thenReturn(Optional.empty());
        when(files.storeBytes(eq("track-images"), eq(monza.getId()), anyString(), eq(map))).thenReturn("/uploads/track-images/m/new.png");
        when(gallery.applyGtEngineMap(monza.getId(), "/uploads/track-images/m/new.png", 1500, 1500)).thenThrow(new CannotCreateTransactionException("down"));
        // when / then
        assertThatThrownBy(() -> service.load(monza.getId())).hasMessage("The GT-Engine map could not be saved; try again.");
        verify(files).delete("/uploads/track-images/m/new.png");
    }

    @Test
    void givenMappedUnmappedAndFailingTracks_whenLoadingAll_thenCountEachOutcome() throws IOException {
        // given
        var fuji = track("Fuji International Speedway");
        var sugo = track("SUGO International Racing Course");
        var suzuka = track("Suzuka Circuit");
        when(outbound.isEnabled()).thenReturn(true);
        when(tracks.findAll()).thenReturn(List.of(suzuka, sugo, monza, fuji));
        when(files.downloadTrackMap(MONZA_URL)).thenReturn(map);
        when(files.downloadTrackMap("https://gt-engine.com/gt7/tracks/images/md/fuji.png")).thenReturn(map);
        when(files.downloadTrackMap("https://gt-engine.com/gt7/tracks/images/md/suzuka.png")).thenThrow(new IOException("HTTP 404"));
        when(gallery.gtEngineMapPath(any())).thenReturn(Optional.empty());
        when(files.storeBytes(eq("track-images"), any(), anyString(), eq(map))).thenReturn("/uploads/track-images/x/new.png");
        when(gallery.applyGtEngineMap(eq(monza.getId()), anyString(), eq(1500), eq(1500))).thenReturn(true);
        when(gallery.applyGtEngineMap(eq(fuji.getId()), anyString(), eq(1500), eq(1500))).thenReturn(false);
        // when
        var result = service.loadAll();
        // then
        assertThat(result.message()).isEqualTo("GT-Engine maps: added 1, replaced 1, unchanged 0, tracks without a map 1; failed: Suzuka Circuit.");
    }

    @Test
    void givenDisabledOutboundAccess_whenLoadingAll_thenRejectBeforeReadingTracks() {
        // given
        when(outbound.isEnabled()).thenReturn(false);
        // when / then
        assertThatThrownBy(() -> service.loadAll()).hasMessage(ExternalIntegrationDisabledException.MESSAGE);
        verify(tracks, never()).findAll();
    }

    @Test
    void givenLivePage_whenComparing_thenFetchTheFixedPageWithABoundedBody() throws IOException {
        // given
        when(pages.fetch(GtEngineTrackMaps.PAGE_URL, 2 * 1024 * 1024)).thenReturn(GtEngineTrackMapsTest.PAGE);
        // when
        var comparison = service.compare();
        // then
        assertThat(comparison.liveVersion()).isEqualTo("1.65");
        assertThat(comparison.added()).isEmpty();
        assertThat(comparison.removed()).as("the excerpt lists two of the reviewed layouts").hasSize(120);
    }

    @Test
    void givenUnreachablePage_whenComparing_thenReportItAsARuleViolation() throws IOException {
        // given
        when(pages.fetch(anyString(), eq(2 * 1024 * 1024))).thenThrow(new IOException("GT-Engine answered with HTTP 503"));
        // when / then
        assertThatThrownBy(() -> service.compare()).isInstanceOf(BusinessRuleException.class)
                .hasMessage("GT-Engine could not be compared: GT-Engine answered with HTTP 503");
    }

    private static Track track(String name) {
        var track = new Track(name);
        ReflectionTestUtils.setField(track, "id", UUID.randomUUID());
        return track;
    }
}
