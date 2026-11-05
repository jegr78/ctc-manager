package org.ctc.backup.restore.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import db.migration.V26__AddConfidentialPicks;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.flywaydb.core.api.migration.Context;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.assertj.core.api.Assertions.*;

class PickRestorersTest {
    final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void givenExportedHistory_whenRestoring_thenRetainPointersPayloadIdentityAndUtcMetadata() throws Exception {
        // given
        var jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:pick-restorers-" + UUID.randomUUID()));
        UUID season = UUID.randomUUID();
        UUID team = UUID.randomUUID();
        UUID race = UUID.randomUUID();
        UUID day = UUID.randomUUID();
        UUID away = UUID.randomUUID();
        UUID car = UUID.randomUUID();
        UUID track = UUID.randomUUID();
        UUID pick = UUID.randomUUID();
        UUID request = UUID.randomUUID();
        try (var connection = jdbc.getDataSource().getConnection()) {
            // Keep this connection open for the in-memory database lifetime.
            for (String table : List.of("seasons", "teams", "races", "cars", "tracks", "season_car_pool_entries", "matchdays"))
                jdbc.execute("CREATE TABLE " + table + " (id UUID PRIMARY KEY)");
            jdbc.execute("ALTER TABLE races ADD COLUMN matchday_id UUID");
            var context = mock(Context.class);
            when(context.getConnection()).thenReturn(connection);
            new V26__AddConfidentialPicks().migrate(context);
            for (String table : List.of("matchday_pick_revisions", "matchday_pick_decisions")) {
                jdbc.execute("ALTER TABLE " + table + " ADD COLUMN lobby_rules_profile VARCHAR(16)");
                jdbc.execute("ALTER TABLE " + table + " ADD COLUMN start_type VARCHAR(24)");
                jdbc.execute("ALTER TABLE " + table + " ADD COLUMN slipstream VARCHAR(16)");
            }
            jdbc.update("INSERT INTO seasons (id) VALUES (?)", season);
            jdbc.update("INSERT INTO teams (id) VALUES (?)", team);
            jdbc.update("INSERT INTO teams (id) VALUES (?)", away);
            jdbc.update("INSERT INTO matchdays (id) VALUES (?)", day);
            jdbc.update("INSERT INTO races (id,matchday_id) VALUES (?,?)", race, day);
            jdbc.update("INSERT INTO cars (id) VALUES (?)", car);
            jdbc.update("INSERT INTO tracks (id) VALUES (?)", track);
            var aggregate = mapper.createObjectNode();
            aggregate.put("id", pick.toString()).put("race", race.toString()).put("season", season.toString()).put("team", team.toString())
                    .put("originalMatchday", day.toString()).put("originalAwayTeam", away.toString())
                    .put("version", 4).put("state", "APPROVED").put("latestRevisionNumber", 2).put("approvedRevisionNumber", 2)
                    .put("manualOpen", false).put("manualClosed", false).put("createdAt", "2026-11-02T19:00:00").put("updatedAt", "2026-11-02T19:04:00");
            // when / then
            new MatchdayPickRestorer().restore(List.of(aggregate), jdbc);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM matchday_picks", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT aggregate_version FROM matchday_picks", Long.class)).isEqualTo(4);
            assertThat(jdbc.queryForObject("SELECT approved_revision_number FROM matchday_picks", Integer.class)).isEqualTo(2);
            var revision = mapper.createObjectNode();
            revision.put("id", UUID.randomUUID().toString()).put("pick", pick.toString()).put("revisionNumber", 2)
                    .put("requestId", request.toString()).put("receiptVersion", 3).put("submitterId", "12345")
                    .put("confirmedAt", "2026-10-25T01:30:00.123456Z").put("createdAt", "2026-11-02T19:00:00").put("updatedAt", "2026-11-02T19:00:00");
            revision.set("payload", mapper.readTree("""
                    {"trackId":"%s","carId":"%s","carPoolEntryId":null,"laps":12,"tyreWear":2,"fuelConsumption":3,
                     "refuelingSpeed":4,"initialFuel":"DEFAULT","requiredPitStops":1,"timeProgressionMode":"EQUAL_CONDITIONS",
                     "timeProgressionMultiplier":null,"weather":"S01, R03, S02","timeOfDay":"Sunset","availableTyres":["RS","RM"],"mandatoryTyres":[]}
                    """.formatted(track, car)));
            new MatchdayPickRevisionRestorer().restore(List.of(org.ctc.backup.schema.LobbyRulesArchive.normalize(13, "matchday_pick_revisions", revision)), jdbc);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM matchday_pick_revisions", Integer.class)).isEqualTo(1);
            var restored = jdbc.queryForMap("SELECT * FROM matchday_pick_revisions");
            assertThat(restored.get("CONFIRMED_AT").toString()).isEqualTo("2026-10-25 01:30:00.123456");
            assertThat(restored.get("TIME_PROGRESSION_MULTIPLIER")).isNull();
            assertThat(restored.get("TIME_PROGRESSION_MODE")).isEqualTo("EQUAL_CONDITIONS");
            assertThat(restored.get("AVAILABLE_TYRES")).isEqualTo("RS,RM");
            assertThat(restored.get("MANDATORY_TYRES")).isEqualTo("");
            assertThat(restored.get("WEATHER")).isEqualTo("S01, R03, S02");
            assertThat(restored.get("REQUEST_ID")).isEqualTo(request);
            assertThat(restored.get("LOBBY_RULES_PROFILE")).isEqualTo("LEGACY");
            assertThat(restored.get("START_TYPE")).isEqualTo("GRID_FALSE_START");
            assertThat(restored.get("SLIPSTREAM")).isEqualTo("REAL");
            var decision = mapper.createObjectNode();
            decision.put("id", UUID.randomUUID().toString()).put("pick", pick.toString()).put("kind", "APPROVE").put("aggregateVersion", 4)
                    .put("actorKind", "MANAGEMENT").put("actorId", "test-admin").put("reason", "Checked exact revision 2")
                    .put("decidedAt", "2026-11-02T19:04:00Z").put("createdAt", "2026-11-02T19:04:00").put("updatedAt", "2026-11-02T19:04:00");
            decision.putNull("correctionPayload");
            new MatchdayPickDecisionRestorer().restore(List.of(decision), jdbc);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM matchday_pick_decisions", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT reason FROM matchday_pick_decisions", String.class)).isEqualTo("Checked exact revision 2");
            assertThat(jdbc.queryForObject("SELECT actor_id FROM matchday_pick_decisions", String.class)).isEqualTo("test-admin");
        }
    }
}
