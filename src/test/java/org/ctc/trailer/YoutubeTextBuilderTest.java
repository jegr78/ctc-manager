package org.ctc.trailer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.ctc.admin.dto.MatchdayGraphicData;
import org.ctc.domain.model.MusicTrack;
import org.junit.jupiter.api.Test;

class YoutubeTextBuilderTest {

    @Test
    void givenMatchdayPlan_whenBuild_thenTitleNamesMatchdayAndSeasonAndDescriptionEndsWithCredit() {
        // given
        var music = music("Heat Warning", "Jeremy Korpas", "Heat Warning by Jeremy Korpas, YouTube Audio Library");

        // when
        var text = YoutubeTextBuilder.build(plan(), music);

        // then
        assertThat(text.title()).isEqualTo("Matchday 3 · Season 2027 | Community Team Cup Trailer");
        assertThat(text.description()).contains("HOM vs AWY: Circuit de Spa-Francorchamps · Super Formula SF23 '23 (legs 1 & 2)");
        assertThat(text.description().strip()).endsWith("Music: Heat Warning by Jeremy Korpas, YouTube Audio Library");
    }

    @Test
    void givenMusicWithoutCredit_whenBuild_thenFallsBackToTitleAndArtist() {
        // when
        var text = YoutubeTextBuilder.build(plan(), music("Test Run", "Jeremy Korpas", null));

        // then
        assertThat(text.description().strip()).endsWith("Music: Test Run by Jeremy Korpas");
        assertThat(text.asFile()).startsWith("Title: Matchday 3").contains("\n\nDescription:\n");
    }

    private static TrailerPlan plan() {
        var scene = new TrailerRaceScene(UUID.randomUUID(), "HOM vs AWY", "Home", "Away", null, null, List.of(1, 2), null,
                UUID.randomUUID(), "Circuit de Spa-Francorchamps", "SPA", UUID.randomUUID(), "Super Formula SF23 '23", null, null);
        var overview = new MatchdayGraphicData("Matchday 3", "Season 2027", "2027", null, null, List.of());
        return new TrailerPlan(new TrailerScope.MatchdayScope(UUID.randomUUID()), "Matchday 3", "Season 2027", overview, List.of(scene),
                List.of(), Set.of());
    }

    private static MusicTrack music(String title, String artist, String credit) {
        var track = new MusicTrack();
        track.setTitle(title);
        track.setArtist(artist);
        track.setCredit(credit);
        return track;
    }
}
