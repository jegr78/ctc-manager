package org.ctc.gt7sync;

import static org.assertj.core.api.Assertions.assertThat;

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
        assertThat(scraper.extractTrackImageUrl(null, "")).isNull();
        assertThat(scraper.extractTrackImageUrl("../foo", "")).isNull();
    }
}
