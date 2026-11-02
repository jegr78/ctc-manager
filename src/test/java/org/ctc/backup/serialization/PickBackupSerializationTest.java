package org.ctc.backup.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.ctc.domain.model.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;

class PickBackupSerializationTest {
    final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule()).registerModule(new BackupSerializationModule());

    @Test
    void givenPrivateAggregate_whenExporting_thenUseScalarReferencesAndNumericPointersWithoutCycles() throws Exception {
        // given
        var pick = pick();
        pick.submit();
        // when
        var row = mapper.readTree(mapper.writeValueAsBytes(pick));
        // then
        assertThat(row.path("race").isTextual()).isTrue();
        assertThat(row.path("race").asText()).isEqualTo(pick.getRace().getId().toString());
        assertThat(row.path("season").asText()).isEqualTo(pick.getSeason().getId().toString());
        assertThat(row.path("team").asText()).isEqualTo(pick.getTeam().getId().toString());
        assertThat(row.path("originalMatchday").asText()).isEqualTo(pick.getOriginalMatchday().getId().toString());
        assertThat(row.path("originalAwayTeam").asText()).isEqualTo(pick.getOriginalAwayTeam().getId().toString());
        assertThat(row.path("latestRevisionNumber").asInt()).isEqualTo(1);
        assertThat(row.path("approvedRevisionNumber").isNull()).isTrue();
        assertThat(row.path("version").asLong()).isEqualTo(1);
    }

    @Test
    void givenConfirmedRevision_whenExporting_thenPreserveTypedPayloadAndReceiptMetadata() throws Exception {
        // given
        var pick = pick();
        pick.submit();
        UUID request = UUID.randomUUID();
        var revision = new MatchdayPickRevision(pick, 1, payload(), Instant.parse("2026-11-02T19:00:00.123456Z"), "12345", request, 1);
        ReflectionTestUtils.setField(revision, "id", UUID.randomUUID());
        // when
        var row = mapper.readTree(mapper.writeValueAsBytes(revision));
        // then
        assertThat(row.path("pick").isTextual()).isTrue();
        assertThat(row.path("pick").asText()).isEqualTo(pick.getId().toString());
        assertThat(row.path("requestId").asText()).isEqualTo(request.toString());
        assertThat(row.path("receiptVersion").asLong()).isEqualTo(1);
        assertThat(row.path("submitterId").asText()).isEqualTo("12345");
        assertThat(row.path("payload").path("timeProgressionMode").asText()).isEqualTo("EQUAL_CONDITIONS");
        assertThat(row.path("payload").path("timeProgressionMultiplier").isNull()).isTrue();
        assertThat(mapper.treeToValue(row.path("payload"), PickPayload.class)).isEqualTo(revision.getPayload());
    }

    @Test
    void givenManagementDecision_whenExporting_thenPreserveActorReasonTimeAndVersion() throws Exception {
        // given
        var pick = pick();
        var decision = new MatchdayPickDecision(pick, PickDecisionKind.OPEN, 1, "test-admin", "Late entry", Instant.parse("2026-11-02T19:00:00Z"));
        ReflectionTestUtils.setField(decision, "id", UUID.randomUUID());
        // when
        var row = mapper.readTree(mapper.writeValueAsBytes(decision));
        // then
        assertThat(row.path("pick").isTextual()).isTrue();
        assertThat(row.path("actorKind").asText()).isEqualTo("MANAGEMENT");
        assertThat(row.path("actorId").asText()).isEqualTo("test-admin");
        assertThat(row.path("reason").asText()).isEqualTo("Late entry");
        assertThat(row.path("aggregateVersion").asLong()).isEqualTo(1);
        assertThat(row.path("correctionPayload").isNull()).isTrue();
    }

    private MatchdayPick pick() {
        var race = new Race();
        race.setId(UUID.randomUUID());
        var season = new Season("Test_BackupPick");
        season.setId(UUID.randomUUID());
        var team = new Team();
        team.setId(UUID.randomUUID());
        var day = new Matchday();
        day.setId(UUID.randomUUID());
        race.setMatchday(day);
        race.setHomeTeamOverride(team);
        var away = new Team();
        away.setId(UUID.randomUUID());
        race.setAwayTeamOverride(away);
        var pick = new MatchdayPick(race, season, team);
        ReflectionTestUtils.setField(pick, "id", UUID.randomUUID());
        return pick;
    }

    private PickPayload payload() {
        return new PickPayload(UUID.randomUUID(), UUID.randomUUID(), null, 12, 2, 3, 4, PickInitialFuel.FUEL_50, 1,
                TimeProgressionMode.EQUAL_CONDITIONS, null, "S01, R03, S02", "Sunset", Set.of(PickTyre.RS, PickTyre.RM), Set.of());
    }
}
