package org.ctc.backup.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class MatchWalkoverBackupValidatorTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void givenUuidHistoricalObjectOrMissingMarker_whenReading_thenKeepOnlyNullableIdentity() {
        // given
        UUID id = UUID.randomUUID();
        var object = mapper.createObjectNode().put("id", id.toString()).put("name", "Historical details");
        object.putObject("ignored").put("value", "Not restored as team state");
        // when / then
        assertThat(org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> MatchWalkoverReference.read(mapper.getNodeFactory().textNode(id.toString())))).isEqualTo(id);
        assertThat(org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> MatchWalkoverReference.read(object))).isEqualTo(id);
        assertThat(org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> MatchWalkoverReference.read(null))).isNull();
        assertThat(org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> MatchWalkoverReference.read(mapper.nullNode()))).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "false", "123", "{}", "{\"id\":null}", "{\"id\":123}", "\"0-0-0-0-0\"", "\"000000001-0000-0000-0000-00000000000\"", "\"\""})
    void givenMalformedWalkoverIdentity_whenReading_thenRejectWithoutImplicitConversion(String json) throws Exception {
        // given
        JsonNode value = mapper.readTree(json);
        // when / then
        assertThatThrownBy(() -> MatchWalkoverReference.read(value)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("walkover");
    }

    @ParameterizedTest
    @ValueSource(strings = {"valid", "foreign", "missing-team", "bye"})
    void givenWalkoverReferences_whenValidating_thenRequireExistingDirectParticipantsAndNoBye(String kind) {
        // given
        var validator = new MatchWalkoverBackupValidator(); UUID home = UUID.randomUUID(), away = UUID.randomUUID(), third = UUID.randomUUID();
        for (var team : new UUID[]{home, away, third}) validator.accept("teams", mapper.createObjectNode().put("id", team.toString()));
        UUID first = kind.equals("missing-team") ? UUID.randomUUID() : home;
        var row = mapper.createObjectNode().put("id", UUID.randomUUID().toString()).put("homeTeam", first.toString())
                .put("awayTeam", away.toString()).put("walkoverTeam", kind.equals("foreign") ? third.toString() : first.toString())
                .put("bye", kind.equals("bye"));
        validator.accept("matches", row);
        // when / then
        if (kind.equals("valid")) assertThatCode(validator::validate).doesNotThrowAnyException();
        else assertThatThrownBy(validator::validate).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("walkover");
    }

    @Test
    void givenReferenceBudgetReached_whenAcceptingAnotherIdentity_thenRejectBeforeUnboundedRetention() {
        // given
        var validator = new MatchWalkoverBackupValidator();
        for (int i = 0; i < 100_000; i++) validator.accept("teams", mapper.createObjectNode().put("id", new UUID(0, i).toString()));
        // when / then
        assertThatThrownBy(() -> validator.accept("teams", mapper.createObjectNode().put("id", UUID.randomUUID().toString())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("budget");
    }
    @Test
    void givenNonBooleanByeFlag_whenAcceptingWalkover_thenRejectRatherThanCoercingIt() {
        // given
        var validator = new MatchWalkoverBackupValidator();
        var row = mapper.createObjectNode().put("id", UUID.randomUUID().toString())
                .put("homeTeam", UUID.randomUUID().toString()).put("awayTeam", UUID.randomUUID().toString())
                .put("walkoverTeam", UUID.randomUUID().toString()).put("bye", "false");
        // when / then
        assertThatThrownBy(() -> validator.accept("matches", row)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("walkover bye flag");
    }

    @Test
    void givenMissingOrNullMarker_whenAcceptingLegacyPairing_thenDoNotRequireWalkoverReferences() {
        // given
        var validator = new MatchWalkoverBackupValidator();
        var absent = mapper.createObjectNode().put("id", UUID.randomUUID().toString());
        var empty = mapper.createObjectNode().put("id", UUID.randomUUID().toString()).putNull("walkoverTeam");
        // when / then
        assertThatCode(() -> { validator.accept("matches", absent); validator.accept("matches", empty); validator.validate(); }).doesNotThrowAnyException();
    }

}
