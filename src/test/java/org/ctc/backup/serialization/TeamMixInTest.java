package org.ctc.backup.serialization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.ctc.domain.model.Team;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 73 / Plan 01 — Representative MixIn JSON-shape test for {@link Team}.
 *
 * <p>Verifies the {@link TeamMixIn} contract end-to-end on a manually constructed
 * {@link ObjectMapper} (no Spring context):
 * <ul>
 *   <li>{@code parentTeam} self-FK renders as a UUID string (not a nested object).</li>
 *   <li>{@code subTeams} back-reference collection is absent.</li>
 *   <li>{@code seasonDrivers} back-reference collection is absent.</li>
 *   <li>{@code subTeam} computed property (from {@code isSubTeam()}) is absent.</li>
 *   <li>The {@code id} field is present.</li>
 * </ul>
 */
class TeamMixInTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper()
                .registerModule(new BackupSerializationModule())
                .registerModule(new JavaTimeModule());
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }

    @Test
    void givenTeamWithParentAndSubTeams_whenSerialize_thenParentIsIdRefAndSubTeamsAbsent() throws Exception {
        // given
        Team parent = new Team("Alpha Racing", "T-ALF");
        parent.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));

        Team child = new Team("Alpha Racing II", "T-ALF2", parent);
        child.setId(UUID.fromString("22222222-2222-2222-2222-222222222222"));
        parent.getSubTeams().add(child);                              // populate the back-reference

        // when
        String json = mapper.writeValueAsString(child);
        JsonNode node = mapper.readTree(json);

        // then
        assertThat(node.has("id")).isTrue();
        assertThat(node.get("id").asText())
                .isEqualTo("22222222-2222-2222-2222-222222222222");
        assertThat(node.has("parentTeam")).isTrue();
        assertThat(node.get("parentTeam").isTextual())
                .as("parentTeam must serialize as a UUID string (not a nested object)")
                .isTrue();
        assertThat(node.get("parentTeam").asText())
                .isEqualTo("11111111-1111-1111-1111-111111111111");
        assertThat(node.has("subTeams"))
                .as("subTeams back-reference must be ignored")
                .isFalse();
        assertThat(node.has("seasonDrivers"))
                .as("seasonDrivers back-reference must be ignored")
                .isFalse();
        assertThat(node.has("subTeam"))
                .as("subTeam computed property (isSubTeam) must be ignored")
                .isFalse();
        assertThat(node.has("parentOrSelf"))
                .as("parentOrSelf computed property must be ignored")
                .isFalse();
    }
}
