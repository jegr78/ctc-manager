package org.ctc.backup.serialization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.ctc.domain.model.Car;
import org.ctc.domain.model.Match;
import org.ctc.domain.model.Matchday;
import org.ctc.domain.model.PlayoffMatchup;
import org.ctc.domain.model.Race;
import org.ctc.domain.model.RaceSettings;
import org.ctc.domain.model.Team;
import org.ctc.domain.model.Track;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 73 / Plan 01 — Representative MixIn JSON-shape test for {@link Race}.
 *
 * <p>Verifies the {@link RaceMixIn} contract:
 * <ul>
 *   <li>All seven {@code @ManyToOne} foreign keys ({@code matchday}, {@code match},
 *       {@code track}, {@code car}, {@code playoffMatchup}, {@code homeTeamOverride},
 *       {@code awayTeamOverride}) render as UUID strings.</li>
 *   <li>{@code settings} inverse {@code @OneToOne} side is absent.</li>
 *   <li>{@code results} and {@code attachments} back-reference collections are absent.</li>
 *   <li>Seven convenience getters ({@code homeTeam}, {@code awayTeam},
 *       {@code homeScore}, {@code awayScore}, {@code bye}, {@code allSettings},
 *       {@code calendarEvent}) are absent.</li>
 * </ul>
 */
class RaceMixInTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper()
                .registerModule(new BackupSerializationModule())
                .registerModule(new JavaTimeModule());
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    @Test
    void givenRaceWithSettings_whenSerialize_thenSettingsAbsent() throws Exception {
        // given — all seven @ManyToOne refs populated, plus the inverse settings side
        Race race = new Race();
        race.setId(UUID.fromString("99999999-9999-9999-9999-999999999999"));

        Matchday matchday = new Matchday();
        matchday.setId(UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001"));
        race.setMatchday(matchday);

        Match match = new Match();
        match.setId(UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001"));
        race.setMatch(match);

        Track track = new Track("Track One", "Country");
        track.setId(UUID.fromString("cccccccc-0000-0000-0000-000000000001"));
        race.setTrack(track);

        Car car = new Car("Manufacturer", "Model");
        car.setId(UUID.fromString("dddddddd-0000-0000-0000-000000000001"));
        race.setCar(car);

        PlayoffMatchup pm = new PlayoffMatchup();
        pm.setId(UUID.fromString("eeeeeeee-0000-0000-0000-000000000001"));
        race.setPlayoffMatchup(pm);

        Team homeOverride = new Team("Home", "T-HOM");
        homeOverride.setId(UUID.fromString("11111111-0000-0000-0000-000000000001"));
        race.setHomeTeamOverride(homeOverride);

        Team awayOverride = new Team("Away", "T-AWY");
        awayOverride.setId(UUID.fromString("22222222-0000-0000-0000-000000000001"));
        race.setAwayTeamOverride(awayOverride);

        RaceSettings settings = new RaceSettings(race);
        settings.setId(UUID.fromString("ffffffff-0000-0000-0000-000000000001"));
        race.setSettings(settings);

        // when
        String json = mapper.writeValueAsString(race);
        JsonNode node = mapper.readTree(json);

        // then — settings inverse @OneToOne side is suppressed
        assertThat(node.has("settings"))
                .as("settings inverse @OneToOne must be ignored — RaceSettings owns the FK")
                .isFalse();

        // then — child collections suppressed
        assertThat(node.has("results")).isFalse();
        assertThat(node.has("attachments")).isFalse();

        // then — convenience getters suppressed
        assertThat(node.has("homeTeam"))
                .as("computed getHomeTeam() must be ignored")
                .isFalse();
        assertThat(node.has("awayTeam")).isFalse();
        assertThat(node.has("homeScore")).isFalse();
        assertThat(node.has("awayScore")).isFalse();
        assertThat(node.has("bye"))
                .as("isBye() computed property must be ignored")
                .isFalse();
        assertThat(node.has("allSettings"))
                .as("hasAllSettings() computed property must be ignored")
                .isFalse();
        assertThat(node.has("calendarEvent"))
                .as("hasCalendarEvent() computed property must be ignored")
                .isFalse();

        // then — all seven @ManyToOne refs render as UUID strings
        assertThat(node.get("matchday").asText()).isEqualTo("aaaaaaaa-0000-0000-0000-000000000001");
        assertThat(node.get("match").asText()).isEqualTo("bbbbbbbb-0000-0000-0000-000000000001");
        assertThat(node.get("track").asText()).isEqualTo("cccccccc-0000-0000-0000-000000000001");
        assertThat(node.get("car").asText()).isEqualTo("dddddddd-0000-0000-0000-000000000001");
        assertThat(node.get("playoffMatchup").asText())
                .isEqualTo("eeeeeeee-0000-0000-0000-000000000001");
        assertThat(node.get("homeTeamOverride").asText())
                .isEqualTo("11111111-0000-0000-0000-000000000001");
        assertThat(node.get("awayTeamOverride").asText())
                .isEqualTo("22222222-0000-0000-0000-000000000001");
    }
}
