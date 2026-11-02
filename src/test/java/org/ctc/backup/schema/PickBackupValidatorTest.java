package org.ctc.backup.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class PickBackupValidatorTest {
    final ObjectMapper mapper = new ObjectMapper();
    final UUID season = UUID.randomUUID(), team = UUID.randomUUID(), away = UUID.randomUUID(), race = UUID.randomUUID();
    final UUID phase = UUID.randomUUID(), day = UUID.randomUUID(), match = UUID.randomUUID(), pick = UUID.randomUUID();
    final UUID car = UUID.randomUUID(), track = UUID.randomUUID(), request = UUID.randomUUID();

    @Test
    void givenCompletePendingHistory_whenValidating_thenAcceptTypedReferencesAndExactVersion() throws Exception {
        // given / when / then
        assertThatCode(() -> validate(5, fixture())).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4})
    void givenSupportedOldBackup_whenValidating_thenAcceptOnlyDisabledFlagsWithoutHistory(int version) throws Exception {
        // given
        var rows = fixture();
        rows.remove("matchday_picks");
        rows.remove("matchday_pick_revisions");
        rows.get("seasons").getFirst().remove("picksCollectionEnabled");
        rows.get("races").getFirst().remove("picksManaged");
        // when / then
        assertThatCode(() -> validate(version, rows)).doesNotThrowAnyException();
        rows.get("races").getFirst().put("picksManaged", true);
        assertThatThrownBy(() -> validate(version, rows)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("pick");
    }

    @ParameterizedTest
    @ValueSource(strings = {"season-flag-missing", "race-flag-missing", "flag-string", "unmanaged", "cross-team", "cross-season", "orphan-race",
            "orphan-car", "orphan-track", "state", "pointer", "version", "latest", "cross-round", "cross-opponent", "duplicate-race", "duplicate-revision", "duplicate-request",
            "receipt-version", "manual-flags", "approved-without-decision", "revision-number", "revision-payload", "decision-reason"})
    void givenForgedOrBrokenHistory_whenValidating_thenRejectBeforeReplacement(String invalid) throws Exception {
        // given
        var rows = fixture();
        var aggregate = rows.get("matchday_picks").getFirst();
        var revision = rows.get("matchday_pick_revisions").getFirst();
        switch (invalid) {
            case "season-flag-missing" -> rows.get("seasons").getFirst().remove("picksCollectionEnabled");
            case "race-flag-missing" -> rows.get("races").getFirst().remove("picksManaged");
            case "flag-string" -> rows.get("races").getFirst().put("picksManaged", "true");
            case "unmanaged" -> rows.get("races").getFirst().put("picksManaged", false);
            case "cross-team" -> aggregate.put("team", away.toString());
            case "cross-season" -> aggregate.put("season", UUID.randomUUID().toString());
            case "orphan-race" -> aggregate.put("race", UUID.randomUUID().toString());
            case "orphan-car" -> ((ObjectNode) revision.get("payload")).put("carId", UUID.randomUUID().toString());
            case "orphan-track" -> ((ObjectNode) revision.get("payload")).put("trackId", UUID.randomUUID().toString());
            case "cross-round" -> aggregate.put("originalMatchday", UUID.randomUUID().toString());
            case "cross-opponent" -> aggregate.put("originalAwayTeam", team.toString());
            case "state" -> aggregate.put("state", "PUBLISHED");
            case "pointer" -> aggregate.put("latestRevisionNumber", 2);
            case "version" -> aggregate.put("version", -1);
            case "latest" -> aggregate.putNull("latestRevisionNumber");
            case "duplicate-race" -> rows.get("matchday_picks").add(aggregate.deepCopy().put("id", UUID.randomUUID().toString()));
            case "duplicate-revision" -> rows.get("matchday_pick_revisions").add(revision.deepCopy().put("id", UUID.randomUUID().toString()).put("requestId", UUID.randomUUID().toString()));
            case "duplicate-request" -> {
                aggregate.put("version", 2).put("latestRevisionNumber", 2);
                rows.get("matchday_pick_revisions").add(revision.deepCopy().put("id", UUID.randomUUID().toString()).put("revisionNumber", 2).put("receiptVersion", 2));
            }
            case "receipt-version" -> revision.put("receiptVersion", 9);
            case "manual-flags" -> aggregate.put("manualOpen", true).put("manualClosed", true);
            case "approved-without-decision" -> aggregate.put("state", "APPROVED").put("approvedRevisionNumber", 1);
            case "revision-number" -> revision.put("revisionNumber", 0);
            case "revision-payload" -> ((ObjectNode) revision.get("payload")).put("laps", "12");
            case "decision-reason" -> {
                aggregate.put("version", 2).put("state", "RETURNED").put("manualOpen", true);
                rows.put("matchday_pick_decisions", new ArrayList<>(List.of(node("""
                        {"id":"%s","pick":"%s","kind":"RETURN","aggregateVersion":2,"actorKind":"MANAGEMENT","actorId":"test-admin",
                         "reason":" ","decidedAt":"2026-11-02T19:01:00Z","correctionPayload":null}
                        """.formatted(UUID.randomUUID(), pick)))));
            }
            default -> throw new AssertionError(invalid);
        }
        // when / then
        assertThatThrownBy(() -> validate(5, rows)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("pick");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void givenHistoricalSelectionOnBye_whenValidatingPendingReservation_thenKeepTheValidReservation(boolean playoff) throws Exception {
        // given
        var rows = fixture();
        UUID byeRace = UUID.randomUUID(), pairing = UUID.randomUUID();
        if (playoff) {
            rows.put("playoff_matchups", List.of(node("{\"id\":\"%s\",\"team1\":\"%s\",\"team2\":null,\"bye\":true}".formatted(pairing, team))));
        } else {
            rows.put("matches", new ArrayList<>(rows.get("matches")));
            rows.get("matches").add(node("{\"id\":\"%s\",\"homeTeam\":\"%s\",\"awayTeam\":null,\"bye\":true}".formatted(pairing, team)));
        }
        var bye = node("{\"id\":\"%s\",\"matchday\":\"%s\",\"picksManaged\":false,\"car\":\"%s\",\"track\":\"%s\"}".formatted(byeRace, day, car, track));
        bye.put(playoff ? "playoffMatchup" : "match", pairing.toString());
        rows.get("races").add(bye);
        // when / then
        assertThatCode(() -> validate(5, rows)).doesNotThrowAnyException();
    }

    private void validate(int version, Map<String, List<ObjectNode>> rows) {
        var validator = new PickBackupValidator(version);
        rows.forEach((table, values) -> values.forEach(row -> validator.accept(table, row)));
        validator.validate();
    }

    private Map<String, List<ObjectNode>> fixture() throws Exception {
        var rows = new LinkedHashMap<String, List<ObjectNode>>();
        rows.put("seasons", new ArrayList<>(List.of(node("{\"id\":\"%s\",\"picksCollectionEnabled\":true,\"carPoolMode\":\"FIXED_CARS\",\"cars\":[\"%s\"],\"tracks\":[\"%s\"]}".formatted(season, car, track)))));
        rows.put("teams", List.of(node("{\"id\":\"%s\"}".formatted(team)), node("{\"id\":\"%s\"}".formatted(away))));
        rows.put("cars", List.of(node("{\"id\":\"%s\"}".formatted(car))));
        rows.put("tracks", List.of(node("{\"id\":\"%s\"}".formatted(track))));
        rows.put("season_phases", List.of(node("{\"id\":\"%s\",\"season\":\"%s\"}".formatted(phase, season))));
        rows.put("matchdays", List.of(node("{\"id\":\"%s\",\"phase\":\"%s\"}".formatted(day, phase))));
        rows.put("matches", List.of(node("{\"id\":\"%s\",\"homeTeam\":\"%s\",\"awayTeam\":\"%s\"}".formatted(match, team, away))));
        rows.put("races", new ArrayList<>(List.of(node("{\"id\":\"%s\",\"matchday\":\"%s\",\"match\":\"%s\",\"picksManaged\":true}".formatted(race, day, match)))));
        rows.put("matchday_picks", new ArrayList<>(List.of(node("""
                {"id":"%s","race":"%s","season":"%s","team":"%s","version":1,"state":"SUBMITTED",
                 "latestRevisionNumber":1,"approvedRevisionNumber":null,"manualOpen":false,"manualClosed":false,
                 "originalMatchday":"%s","originalAwayTeam":"%s"}
                """.formatted(pick, race, season, team, day, away)))));
        rows.put("matchday_pick_revisions", new ArrayList<>(List.of(node("""
                {"id":"%s","pick":"%s","revisionNumber":1,"receiptVersion":1,"requestId":"%s","submitterId":"12345","confirmedAt":"2026-11-02T19:00:00Z",
                 "payload":{"trackId":"%s","carId":"%s","carPoolEntryId":null,"laps":12,"tyreWear":2,"fuelConsumption":3,
                 "refuelingSpeed":4,"initialFuel":"DEFAULT","requiredPitStops":1,"timeProgressionMode":"MULTIPLIER","timeProgressionMultiplier":0,
                 "weather":"S01","timeOfDay":"Sunset","availableTyres":["RS","RM"],"mandatoryTyres":[]}}
                """.formatted(UUID.randomUUID(), pick, request, track, car)))));
        return rows;
    }

    private ObjectNode node(String json) throws Exception { return (ObjectNode) mapper.readTree(json); }
}
