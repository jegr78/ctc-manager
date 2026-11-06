package org.ctc.gt7sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.Mockito.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class Gt7TrackLogoTest {
    private final Gt7ScraperService scraper = new Gt7ScraperService(org.ctc.TestHelper.enabledOutboundPolicy());

    @ParameterizedTest
    @ValueSource(strings = {"\"", "'", "`"})
    void givenOfficialSugoLogoImport_whenResolveBaseId_thenAcceptsSupportedQuotes(String quote) {
        // given
        var index = "const logos={\"../../../generated-assets_tracks/images/track_logos/light/30cb0b.png\":()=>B(()=>import("
                + quote + "./30cb0b-B4OztsW5.js" + quote + "),[])};";
        // when / then
        assertThat(scraper.extractBaseIdChunks(index)).containsEntry("30cb0b", "30cb0b-B4OztsW5.js");
    }

    @ParameterizedTest
    @ValueSource(strings = {"./30cb0b-../outside.js", "./30cb0b-foo/bar.js", "./30cb0b-build.js?other=1"})
    void givenUnsafeLogoImport_whenResolveBaseId_thenDoesNotResolveIt(String path) {
        // when / then
        assertThat(scraper.extractBaseIdChunks("import(\"" + path + "\")")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"\"", "'", "`"})
    void givenOfficialSugoLogoExport_whenResolveImage_thenMatchesOnlyItsBaseId(String quote) {
        // given
        var chunk = "var e=" + quote + "/common/dist/gt7/tracklist/assets/30cb0b-C2qzNqBm.png" + quote + ";export{e as default};";
        // when / then
        assertThat(scraper.extractTrackImageUrl("30cb0b", chunk))
                .isEqualTo("https://www.gran-turismo.com/common/dist/gt7/tracklist/assets/30cb0b-C2qzNqBm.png");
        assertThat(scraper.extractTrackImageUrl("abcdef", chunk)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/common/dist/gt7/tracklist/assets/30cb0b-../outside.png",
            "https://other.test/30cb0b-build.png", "/common/dist/gt7/carlist/assets/30cb0b-build.png",
            "/common/dist/gt7/tracklist/assets/30cb0b-build.png?query=1", "no image exported"})
    void givenUnexpectedImagePath_whenResolveImage_thenDoesNotResolveIt(String path) {
        // when / then
        assertThat(scraper.extractTrackImageUrl("30cb0b", "var e=`" + path + "`;export{e as default};")).isNull();
    }

    @Test
    void givenMismatchedQuotesOrMissingBaseId_whenResolve_thenRejectsTheSource() {
        // when / then
        assertThat(scraper.extractBaseIdChunks("import(\"./30cb0b-build.js`)")).isEmpty();
        assertThat(scraper.extractTrackImageUrl("30cb0b", "var e=\"/common/dist/gt7/tracklist/assets/30cb0b-build.png`;")).isNull();
        assertThatNoException().isThrownBy(() -> assertThat(scraper.extractTrackImageUrl(null, "")).isNull());
        assertThat(scraper.extractTrackImageUrl("../foo", "var e=`/common/dist/gt7/tracklist/assets/../foo-build.png`;"))
                .isNull();
    }

    @Test
    void givenSharedLogoAndMissingBaseId_whenResolveInParallel_thenResolvesLayoutsOnceAndRetainsMissingRecord() throws Exception {
        // given
        var service = spy(scraper);
        var chunk = "https://www.gran-turismo.com/common/dist/gt7/tracklist/assets/30cb0b-B4OztsW5.js";
        doReturn("var e=`/common/dist/gt7/tracklist/assets/30cb0b-C2qzNqBm.png`;export{e as default};")
                .when(service).fetchText(chunk);
        var records = java.util.List.of(
                new Gt7ScraperService.ScrapedTrack("4bf61b", "SUGO International Racing Course", "Japan", "30cb0b", null),
                new Gt7ScraperService.ScrapedTrack("abcdef", "Test SUGO layout", "Japan", "30cb0b", null),
                new Gt7ScraperService.ScrapedTrack("bcdefa", "Test missing base ID", "Japan", null, null));
        // when
        var result = new java.util.concurrent.atomic.AtomicReference<java.util.List<Gt7ScraperService.ScrapedTrack>>();
        assertThatNoException().isThrownBy(() -> result.set(service.resolveTrackImagesParallel(records,
                java.util.Map.of("30cb0b", "30cb0b-B4OztsW5.js"))));
        var resolved = result.get();
        // then
        assertThat(resolved).hasSize(3);
        assertThat(resolved.get(0).imageUrl()).endsWith("/30cb0b-C2qzNqBm.png");
        assertThat(resolved.get(1).imageUrl()).isEqualTo(resolved.get(0).imageUrl());
        assertThat(resolved.get(2).name()).isEqualTo("Test missing base ID");
        assertThat(resolved.get(2).imageUrl()).isNull();
        verify(service).fetchText(chunk);
    }

    @Test
    void givenUnavailableLogoChunk_whenResolveInParallel_thenRetainsTrackWithoutImage() throws Exception {
        // given
        var service = spy(scraper);
        doThrow(new java.io.IOException("HTTP 404")).when(service).fetchText(anyString());
        var record = new Gt7ScraperService.ScrapedTrack("4bf61b", "SUGO International Racing Course", "Japan", "30cb0b", null);
        // when
        var resolved = service.resolveTrackImagesParallel(java.util.List.of(record), java.util.Map.of("30cb0b", "30cb0b-B4OztsW5.js"));
        // then
        assertThat(resolved).containsExactly(record);
    }
}
