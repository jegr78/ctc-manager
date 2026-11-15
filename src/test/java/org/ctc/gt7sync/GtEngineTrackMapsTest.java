package org.ctc.gt7sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import org.ctc.gt7sync.GtEngineTrackMaps.Layout;
import org.ctc.gt7sync.GtEngineTrackMaps.Mapping;
import org.ctc.gt7sync.GtEngineTrackMaps.Page;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class GtEngineTrackMapsTest {
    static final String PAGE = """
            <html><body>
            <h6 class="fs095 mt-3 pt-3 px-4">GT7 - 41 Locations / 49 Tracks / 121 Layouts&ensp;(Ver.1.65)</h6>
            <div class="grid-item lg"><a id="monza" class="map_anchor"></a><a href="images/md/monza.png" data-lightbox="track_maps" data-title="Monza"><img src="images/md/monza.png" alt="Monza track map"></a><div>Monza</div></div>
            <div class="grid-item sm"><a id="alsace-test-2" class="map_anchor"></a><a href="images/md/alsace-test-2.png" data-lightbox="track_maps" data-title="Alsace Test Course Reverse"><img src="images/md/alsace-test-2.png" alt=""></a><div><span class="info-marker">&#x1f535;</span> Alsace Test Course Reverse</div></div>
            <a href="https://coachdaveacademy.com/?ref=gte"><img src="images/ads/product-photos/a.png"></a>
            </body></html>""";

    @Test
    void givenBundledList_whenLoading_thenEveryLayoutIsMappedOrListedAsUnmapped() {
        // when
        var maps = GtEngineTrackMaps.bundled();
        // then
        assertThat(maps.gameVersion()).isEqualTo("1.65");
        assertThat(maps.layouts()).as("one entry per GT-Engine layout of game version 1.65").hasSize(122);
        assertThat(maps.unmappedLayouts()).containsExactly("northern-isle-infield");
        assertThat(maps.unmappedTracks()).containsExactlyInAnyOrder("SUGO International Racing Course", "Test Circuit");
    }

    @Test
    void givenEveryReviewedEnvironmentLayout_whenLookingUpItsMap_thenFindOne() throws IOException {
        // given
        var maps = GtEngineTrackMaps.bundled();
        var environments = new ObjectMapper().readTree(new ClassPathResource("gt7/track-environment-mappings.json").getInputStream());
        // when / then
        for (var entry : environments) {
            String name = entry.path("trackName").asText();
            assertThat(maps.layoutFor(name)).as("GT-Engine map for catalog track " + name).isPresent();
        }
    }

    @Test
    void givenCatalogNames_whenLookingUpMaps_thenUseTheReviewedSlugAndIgnoreSurroundingSpace() {
        // given
        var maps = GtEngineTrackMaps.bundled();
        // when / then
        assertThat(maps.layoutFor("BB Raceway Reverse")).map(Layout::slug).hasValue("broad-bean-2");
        assertThat(maps.layoutFor("Michelin Raceway Road Atlanta ")).map(Layout::slug).hasValue("road-atlanta");
        assertThat(maps.layoutFor("Spa 24h Layout")).map(Layout::slug).hasValue("spa-24h");
        assertThat(maps.layoutFor("Willow Springs International Raceway: Streets of Willow Springs Reverse"))
                .map(maps::imageUrl).hasValue("https://gt-engine.com/gt7/tracks/images/md/willow-springs-streets-of-willow-springs-2.png");
        assertThat(maps.layoutFor("SUGO International Racing Course")).as("GT-Engine 1.65 has no SUGO map").isEmpty();
        assertThat(maps.layoutFor(null)).isEmpty();
    }

    @Test
    void givenInconsistentReviewedLists_whenCreating_thenReject() {
        // given
        var layouts = List.of(new Layout("monza", "Monza"), new Layout("fuji", "Fuji"));
        // when / then
        assertThatThrownBy(() -> list(layouts, List.of(new Mapping("Monza", "monza")), List.of()))
                .hasMessageContaining("Unmapped GT-Engine layouts are not listed exactly: [fuji]");
        assertThatThrownBy(() -> list(layouts, List.of(new Mapping("Monza", "suzuka")), List.of("fuji")))
                .hasMessage("Invalid or duplicate GT-Engine mapping for Monza");
        assertThatThrownBy(() -> list(layouts, List.of(new Mapping("Monza", "monza"), new Mapping(" Monza ", "fuji")), List.of()))
                .hasMessage("Invalid or duplicate GT-Engine mapping for  Monza ");
        assertThatThrownBy(() -> list(List.of(new Layout("../etc", "x")), List.of(), List.of("../etc")))
                .hasMessage("Invalid or duplicate GT-Engine layout ../etc");
        for (String slug : List.of("monza-", "monza--2", "-monza", "Monza"))
            assertThatThrownBy(() -> list(List.of(new Layout(slug, "x")), List.of(), List.of(slug)))
                    .as(slug).hasMessage("Invalid or duplicate GT-Engine layout " + slug);
        assertThatThrownBy(() -> new GtEngineTrackMaps("https://example.com/maps.html", GtEngineTrackMaps.IMAGE_BASE, "1.65", "2026-11-15",
                layouts, List.of(), List.of("fuji", "monza"), List.of())).hasMessage("Unexpected GT-Engine source");
    }

    @Test
    void givenProductionPageMarkup_whenParsing_thenReadSlugsTitlesAndVersionInPageOrder() {
        // when
        var page = GtEngineTrackMaps.parsePage(PAGE);
        // then
        assertThat(page.gameVersion()).isEqualTo("1.65");
        assertThat(page.layouts()).containsExactly(new Layout("monza", "Monza"), new Layout("alsace-test-2", "Alsace Test Course Reverse"));
    }

    @Test
    void givenUnexpectedMapLinkOrNoMaps_whenParsing_thenReject() {
        // when / then
        assertThatThrownBy(() -> GtEngineTrackMaps.parsePage("<a href=\"https://evil.example/x.png\" data-lightbox=\"track_maps\" data-title=\"x\">x</a>"))
                .hasMessage("Unexpected GT-Engine map link https://evil.example/x.png");
        assertThatThrownBy(() -> GtEngineTrackMaps.parsePage("<p>moved</p>")).hasMessage("The GT-Engine page lists no track maps");
    }

    @Test
    void givenLivePage_whenComparing_thenReportVersionAndSlugChangesOnly() {
        // given
        var maps = list(List.of(new Layout("monza", "Monza"), new Layout("fuji", "Fuji")), List.of(new Mapping("Monza", "monza")), List.of("fuji"));
        // when
        var same = maps.compare(new Page("1.65", List.of(new Layout("fuji", "Fuji"), new Layout("monza", "Monza Circuit"))));
        var changed = maps.compare(new Page("1.66", List.of(new Layout("monza", "Monza"), new Layout("sugo", "SUGO"))));
        // then
        assertThat(same.message()).isEqualTo("GT-Engine still lists the reviewed 1.65 layouts.");
        assertThat(changed.message()).isEqualTo("GT-Engine changed: game version 1.66 (reviewed 1.65); new layouts sugo; removed layouts fuji. "
                + "Update gt7/track-maps-gtengine.json after review.");
    }

    private static GtEngineTrackMaps list(List<Layout> layouts, List<Mapping> mappings, List<String> unmapped) {
        return new GtEngineTrackMaps(GtEngineTrackMaps.PAGE_URL, GtEngineTrackMaps.IMAGE_BASE, "1.65", "2026-11-15", layouts, mappings, unmapped, List.of());
    }
}
