package org.ctc.backup.serialization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.UUID;
import org.ctc.domain.model.Car;
import org.ctc.domain.model.Season;
import org.ctc.domain.model.Track;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the {@link SeasonMixIn} contract:
 * <ul>
 *   <li>{@code phases}, {@code seasonDrivers}, {@code seasonTeams} child collections are
 *       absent.</li>
 *   <li>{@code cars} renders as an array of UUID strings (length 2).</li>
 *   <li>{@code tracks} renders as an array of UUID strings (length 2).</li>
 *   <li>Computed convenience properties ({@code displayLabel}, {@code teams},
 *       {@code matchdays}, {@code activeTeams}, {@code eligibleTeams}) are absent.</li>
 * </ul>
 */
class SeasonMixInTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper()
                .registerModule(new BackupSerializationModule())
                .registerModule(new JavaTimeModule());
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    @Test
    void givenSeasonWithPhasesAndCarsAndTracks_whenSerialize_thenPhasesAbsentAndCarsTracksAsIdRefs() throws Exception {
        // given
        Season season = new Season("Test Season", 2026, 1);
        season.setId(UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001"));

        Car car1 = new Car("Manufacturer A", "Model 1");
        car1.setId(UUID.fromString("11111111-aaaa-0000-0000-000000000001"));
        Car car2 = new Car("Manufacturer B", "Model 2");
        car2.setId(UUID.fromString("11111111-aaaa-0000-0000-000000000002"));
        season.getCars().add(car1);
        season.getCars().add(car2);

        Track track1 = new Track("Track Alpha", "Country A");
        track1.setId(UUID.fromString("22222222-bbbb-0000-0000-000000000001"));
        Track track2 = new Track("Track Beta", "Country B");
        track2.setId(UUID.fromString("22222222-bbbb-0000-0000-000000000002"));
        season.getTracks().add(track1);
        season.getTracks().add(track2);

        // when
        String json = mapper.writeValueAsString(season);
        JsonNode node = mapper.readTree(json);

        // then — child collections suppressed
        assertThat(node.has("phases")).as("phases must be ignored").isFalse();
        assertThat(node.has("seasonDrivers")).as("seasonDrivers must be ignored").isFalse();
        assertThat(node.has("seasonTeams")).as("seasonTeams must be ignored").isFalse();

        // then — computed convenience properties suppressed
        assertThat(node.has("displayLabel")).isFalse();
        assertThat(node.has("teams")).isFalse();
        assertThat(node.has("matchdays")).isFalse();
        assertThat(node.has("activeTeams")).isFalse();
        assertThat(node.has("eligibleTeams")).isFalse();

        // then — cars and tracks render as arrays of UUID strings
        assertThat(node.has("cars")).isTrue();
        assertThat(node.get("cars").isArray()).isTrue();
        assertThat(node.get("cars")).hasSize(2);
        assertThat(node.get("cars").get(0).isTextual()).isTrue();
        assertThat(node.get("cars").get(0).asText())
                .isEqualTo("11111111-aaaa-0000-0000-000000000001");

        assertThat(node.has("tracks")).isTrue();
        assertThat(node.get("tracks").isArray()).isTrue();
        assertThat(node.get("tracks")).hasSize(2);
        assertThat(node.get("tracks").get(0).isTextual()).isTrue();
        assertThat(node.get("tracks").get(0).asText())
                .isEqualTo("22222222-bbbb-0000-0000-000000000001");

        // then — base attributes present
        assertThat(node.get("id").asText()).isEqualTo("aaaaaaaa-0000-0000-0000-000000000001");
        assertThat(node.get("name").asText()).isEqualTo("Test Season");
        assertThat(node.get("year").asInt()).isEqualTo(2026);
        assertThat(node.get("number").asInt()).isEqualTo(1);
    }
}
