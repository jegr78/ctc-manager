package org.ctc.gt7sync.environment;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import org.ctc.domain.model.WeatherPreset;
import org.ctc.domain.model.TrackRainCapability;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class GtPlanetEnvironmentSourceTest {
    @Test
    void givenNestedSpaProfilesAndUnrelatedPosts_whenParsing_thenKeepParentAndChildValuesWithSourceRevision() throws Exception {
        // given
        String parent = profile("Circuit de Spa-Francorchamps", List.of("Afternoon", "Evening", "Sunset", "Twilight"), true,
                profile("24h", List.of("Early Dawn", "Dawn", "Sunrise", "Early Morning", "Late Morning", "Afternoon", "Evening", "Sunset", "Twilight", "Night", "Midnight"), true, ""));
        String html = document(profile("Europe-Middle East", null, false, parent)) + "<article data-content='post-other'>" + profile("Wrong", List.of("Bad"), false, "") + "</article>";
        // when
        var snapshot = GtPlanetEnvironmentSource.parse(html, Instant.parse("2026-11-08T12:00:00Z"));
        // then
        assertThat(snapshot.profiles()).hasSize(2);
        assertThat(snapshot.profiles().getFirst().key()).isEqualTo("Europe-Middle East | Circuit de Spa-Francorchamps");
        assertThat(snapshot.profiles().getFirst().values().timeOfDayOptions()).containsExactly("Afternoon", "Evening", "Sunset", "Twilight");
        assertThat(snapshot.profiles().getLast().values().timeOfDayOptions()).hasSize(11);
        assertThat(snapshot.profiles().getLast().values().rainCapability()).isEqualTo(TrackRainCapability.RAIN);
        assertThat(snapshot.sourceEditedAt()).isEqualTo("2026-07-08T01:07:32Z");
        assertThat(snapshot.sourceHash()).matches("[a-f0-9]{64}");
        assertThat(GtPlanetEnvironmentSource.parse(html, Instant.parse("2026-11-09T12:00:00Z")).sourceHash()).isEqualTo(snapshot.sourceHash());
    }

    @ParameterizedTest @ValueSource(strings = {"empty", "missing-weather", "partial-rain", "unknown-weather", "duplicate-time", "missing-date", "duplicate-profile", "other-post", "nested-list"})
    void givenMalformedOrIncompleteReference_whenParsing_thenRejectWithoutInventingOptions(String problem) {
        // given
        String entry = profile("Test", List.of("Dawn", "Night"), false, "");
        String html = document(entry);
        html = switch (problem) {
            case "empty" -> document("");
            case "missing-weather" -> html.replace("<b>Weather:</b>", "<b>Other:</b>");
            case "partial-rain" -> html.replace("</ul></div></div>", "<li>R01 | Light Drizzle</li></ul></div></div>");
            case "unknown-weather" -> html.replace("S01 |", "S99 |");
            case "duplicate-time" -> html.replace("<li>Night</li>", "<li>Dawn</li>");
            case "missing-date" -> html.replace("class='message-lastEdit'", "class='other'");
            case "duplicate-profile" -> document(entry + entry);
            case "other-post" -> html.replace("post-14046048", "post-other");
            default -> html.replace("<li>Dawn</li>", "<li>Dawn<ul><li>Night</li></ul></li>");
        };
        String input = html;
        // when / then
        assertThatThrownBy(() -> GtPlanetEnvironmentSource.parse(input, Instant.EPOCH)).isInstanceOf(java.io.IOException.class);
    }

    @Test
    void givenReviewedBundledSnapshot_whenLoadingOffline_thenValidateLeMansAllLayoutsAndSourceHash() throws Exception {
        // given / when
        var snapshot = GtPlanetEnvironmentSource.bundled();
        // then
        assertThat(snapshot.profiles()).hasSize(48);
        var leMans = snapshot.profiles().stream().filter(p -> p.key().equals("Europe-Middle East | 24 Heures du Mans Racing Circuit (All Layouts)")).findFirst().orElseThrow();
        assertThat(leMans.values().timeOfDayOptions()).hasSize(11);
        assertThat(leMans.values().rainCapability()).isEqualTo(TrackRainCapability.RAIN);
        assertThat(snapshot.sourceHash()).isEqualTo(GtPlanetEnvironmentSource.hash(snapshot.sourceEditedAt(), snapshot.profiles()));
    }

    @Test
    void givenChangedFactsOrOversizedDocument_whenValidating_thenRejectStaleHashAndBoundInput() throws Exception {
        // given
        var snapshot = GtPlanetEnvironmentSource.parse(document(profile("Test", List.of("Dawn"), false, "")), Instant.EPOCH);
        // when / then
        assertThatThrownBy(() -> new GtPlanetEnvironmentSource.Snapshot(snapshot.sourceEditedAt(), snapshot.retrievedAt(), "0".repeat(64), snapshot.profiles()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GtPlanetEnvironmentSource.parse(" ".repeat(1_048_576), Instant.EPOCH)).isInstanceOf(java.io.IOException.class);
    }

    @Test void givenLiveFetch_whenTransporting_thenUseFixedBoundedNonRedirectingRequestAndRejectIncompleteSources() throws Exception {
        var outbound=org.mockito.Mockito.mock(org.ctc.integration.OutboundPolicy.class);
        var connection=org.mockito.Mockito.mock(org.jsoup.Connection.class,org.mockito.Mockito.RETURNS_SELF);
        var response=org.mockito.Mockito.mock(org.jsoup.Connection.Response.class);
        var now=Instant.parse("2026-11-08T12:00:00Z");
        var source=new GtPlanetEnvironmentSource(outbound,java.time.Clock.fixed(now,java.time.ZoneOffset.UTC));
        try(var jsoup=org.mockito.Mockito.mockStatic(org.jsoup.Jsoup.class)) {
            jsoup.when(()->org.jsoup.Jsoup.connect(GtPlanetEnvironmentSource.URL)).thenReturn(connection);
            org.mockito.Mockito.when(connection.execute()).thenReturn(response);
            org.mockito.Mockito.when(response.statusCode()).thenReturn(200);
            var profiles=new StringBuilder();for(int index=0;index<48;index++)profiles.append(profile("Test "+index,List.of("Dawn"),false,""));
            org.mockito.Mockito.when(response.body()).thenReturn(document(profiles.toString()));
            var snapshot=source.fetch();assertThat(snapshot.profiles()).hasSize(48);assertThat(snapshot.retrievedAt()).isEqualTo(now.toString());
            org.mockito.Mockito.verify(connection).timeout(10_000);org.mockito.Mockito.verify(connection).maxBodySize(1_048_576);org.mockito.Mockito.verify(connection).followRedirects(false);
            org.mockito.Mockito.when(response.body()).thenReturn(document(profile("Test",List.of("Dawn"),false,"")));
            assertThatThrownBy(source::fetch).isInstanceOf(java.io.IOException.class).hasMessageContaining("incomplete");
            org.mockito.Mockito.when(response.statusCode()).thenReturn(302);
            assertThatThrownBy(source::fetch).isInstanceOf(java.io.IOException.class).hasMessageContaining("unavailable");
            org.mockito.Mockito.doThrow(new org.ctc.integration.ExternalIntegrationDisabledException()).when(outbound).requireEnabled();
            jsoup.clearInvocations();assertThatThrownBy(source::fetch).isInstanceOf(org.ctc.integration.ExternalIntegrationDisabledException.class);jsoup.verifyNoInteractions();
        }
    }

    static String document(String content) { return "<article data-content='post-14046048'>" + content + "<div class='message-lastEdit'><time datetime='2026-07-08T02:07:32+0100'></time></div></article>"; }
    static String profile(String name, List<String> times, boolean rain, String nested) {
        String sections = times == null ? "" : "<b>Time of Day:</b><ul>" + times.stream().map(t -> "<li>" + t + "</li>").collect(Collectors.joining()) + "</ul><b>Weather:</b><ul>"
                + WeatherPreset.available(rain ? TrackRainCapability.RAIN : TrackRainCapability.DRY_ONLY).stream().map(p -> "<li>" + p.name() + " | " + p.getDescription() + "</li>").collect(Collectors.joining()) + "</ul>";
        return "<div class='bbCodeSpoiler'><button><span class='bbCodeSpoiler-button-title'>" + name + "</span></button><div class='bbCodeBlock-content'>" + sections + nested + "</div></div>";
    }
}
