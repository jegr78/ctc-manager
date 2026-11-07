package org.ctc.backup.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class PickWindowOwnershipTest {
    private final ObjectMapper json = new ObjectMapper();
    private boolean conflictingBridge;
    private final UUID a = UUID.randomUUID(), b = UUID.randomUUID(), phaseA = UUID.randomUUID(), phaseB = UUID.randomUUID();
    private final UUID dayA = UUID.randomUUID(), dayB = UUID.randomUUID(), match = UUID.randomUUID();
    private final UUID playoff = UUID.randomUUID(), round = UUID.randomUUID(), pair = UUID.randomUUID();
    private ObjectNode row(UUID id) { return json.createObjectNode().put("id", id.toString()); }
    private PickWindowBackupValidator graph(boolean bridge, boolean open, boolean override, boolean foreign, boolean wrongMatchday) {
        var validator = new PickWindowBackupValidator();
        validator.accept("seasons", row(a)); validator.accept("seasons", row(b));
        validator.accept("season_phases", row(phaseA).put("season", a.toString()));
        validator.accept("season_phases", row(phaseB).put("season", b.toString()));
        validator.accept("matchdays", row(dayA).put("phase", phaseA.toString()).put("picksOpen", open));
        validator.accept("matchdays", row(dayB).put("phase", phaseA.toString()).put("picksOpen", false));
        var matchRow = row(match).put("matchday", (wrongMatchday ? dayB : dayA).toString()).putNull("pickDeadlineOverride");
        if (conflictingBridge) matchRow.put("pickDeadlineOverride", "2034-01-01T20:00");
        validator.accept("matches", matchRow);
        validator.accept("playoffs", row(playoff).put("phase", (foreign ? phaseB : phaseA).toString()));
        validator.accept("playoff_rounds", row(round).put("playoff", playoff.toString()));
        var pairing = row(pair).put("round", round.toString());
        if (override) pairing.put("pickDeadlineOverride", "2035-01-01T20:00"); else pairing.putNull("pickDeadlineOverride");
        validator.accept("playoff_matchups", pairing);
        var race = row(UUID.randomUUID()).put("matchday", dayA.toString()).put("playoffMatchup", pair.toString());
        if (bridge) race.put("match", match.toString()); else race.putNull("match");
        validator.accept("races", race);
        return validator;
    }
    @ParameterizedTest @ValueSource(booleans = {true, false})
    void givenAnOpenWindowWithForeignPlayoffOwnership_whenValidating_thenRejectBeforeRestore(boolean bridge) {
        var validator = graph(bridge, true, false, true, false);
        assertThatThrownBy(validator::validate).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ownership");
    }
    @ParameterizedTest @ValueSource(booleans = {true, false})
    void givenAForeignPlayoffDeadlineEvenWithTheWindowClosed_whenValidating_thenRejectBeforeInheritance(boolean bridge) {
        var validator = graph(bridge, false, true, true, false);
        assertThatThrownBy(validator::validate).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ownership");
    }
    @ParameterizedTest @ValueSource(booleans = {true, false})
    void givenLegacyClosedForeignLinksWithoutNewSettings_whenValidating_thenPreserveRestoreCompatibility(boolean bridge) {
        assertThatCode(graph(bridge, false, false, true, false)::validate).doesNotThrowAnyException();
    }
    @ParameterizedTest @ValueSource(booleans = {true, false})
    void givenAnOwnedCurrentWindow_whenValidating_thenAcceptBothCanonicalShapes(boolean bridge) {
        assertThatCode(graph(bridge, true, true, false, false)::validate).doesNotThrowAnyException();
    }
    @Test
    void givenAnOpenWindowWithAMatchFromAnotherDay_whenValidating_thenRejectTheScope() {
        assertThatThrownBy(graph(true, true, false, false, true)::validate).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ownership");
    }
    @Test
    void givenAnOwnedBridgeWithConflictingOverrides_whenValidating_thenRejectItsCanonicalMismatch() {
        conflictingBridge = true;
        assertThatThrownBy(graph(true, true, true, false, false)::validate).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("canonical");
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void givenAMixedBridgeWithNewWindowSettings_whenValidating_thenRejectUnrelatedOrdinaryRaces(boolean open) {
        var validator = graph(true, open, !open, false, false);
        validator.accept("races", row(UUID.randomUUID()).put("matchday", dayA.toString()).put("match", match.toString()));
        assertThatThrownBy(validator::validate).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ownership");
    }
    @Test
    void givenAClosedLegacyMixedBridgeWithoutOverrides_whenValidating_thenPreserveRestoreCompatibility() {
        var validator = graph(true, false, false, false, false);
        validator.accept("races", row(UUID.randomUUID()).put("matchday", dayA.toString()).put("match", match.toString()));
        assertThatCode(validator::validate).doesNotThrowAnyException();
    }
    @ParameterizedTest @ValueSource(booleans = {true, false})
    void givenAnActiveCanonicalPairingAcrossDifferentDays_whenValidating_thenRejectTheEntirePairing(boolean bridge) {
        var validator = graph(bridge, true, false, false, false);
        validator.accept("races", row(UUID.randomUUID()).put("matchday", dayB.toString()).put("playoffMatchup", pair.toString()));
        assertThatThrownBy(validator::validate).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ownership");
    }


    @ParameterizedTest @ValueSource(booleans = {true, false})
    void givenAnOrdinaryMatchAcrossDaysWithNewWindowSettings_whenValidating_thenRejectItsForeignMembers(boolean open) {
        conflictingBridge = !open;
        var validator = graph(false, open, false, false, false);
        validator.accept("races", row(UUID.randomUUID()).put("matchday", dayA.toString()).put("match", match.toString()));
        validator.accept("races", row(UUID.randomUUID()).put("matchday", dayB.toString()).put("match", match.toString()));
        assertThatThrownBy(validator::validate).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ownership");
    }
    @Test
    void givenAClosedLegacyOrdinaryMatchAcrossDaysWithoutOverrides_whenValidating_thenPreserveCompatibility() {
        var validator = graph(false, false, false, false, false);
        validator.accept("races", row(UUID.randomUUID()).put("matchday", dayA.toString()).put("match", match.toString()));
        validator.accept("races", row(UUID.randomUUID()).put("matchday", dayB.toString()).put("match", match.toString()));
        assertThatCode(validator::validate).doesNotThrowAnyException();
    }


    @Test
    void givenAnOpenOwningMatchdayWithOnlyForeignRaceMembers_whenValidating_thenRejectTheWholeMatch() {
        var validator = graph(false, true, false, false, false);
        validator.accept("races", row(UUID.randomUUID()).put("matchday", dayB.toString()).put("match", match.toString()));
        assertThatThrownBy(validator::validate).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ownership");
    }
    @Test
    void givenAClosedOwningMatchdayWithOnlyForeignRaceMembers_whenValidating_thenPreserveLegacyCompatibility() {
        var validator = graph(false, false, false, false, false);
        validator.accept("races", row(UUID.randomUUID()).put("matchday", dayB.toString()).put("match", match.toString()));
        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

}