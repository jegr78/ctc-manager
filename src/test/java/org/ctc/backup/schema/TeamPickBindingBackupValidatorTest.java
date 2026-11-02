package org.ctc.backup.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class TeamPickBindingBackupValidatorTest {
    final ObjectMapper json = new ObjectMapper();
    final UUID season = UUID.randomUUID(), team = UUID.randomUUID();

    @Test
    void givenExactBinding_whenValidatingCurrentBackup_thenRetainReferencesWithoutDiscordLookup() {
        // given
        var validator = validator(6);
        validator.accept("team_pick_channel_bindings", binding());
        // when / then
        assertThat(PickBackupValidator.supports("team_pick_channel_bindings")).isTrue();
        assertThatCode(validator::validate).doesNotThrowAnyException();
        assertThat(validator.hasConfidentialContent()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"season", "team", "guild", "channel", "duplicate-team", "duplicate-channel", "shared-channel", "unknown-field"})
    void givenBrokenBinding_whenValidating_thenRejectBeforeWipingExistingData(String invalid) {
        // given
        var validator = validator(6);
        var row = binding();
        switch (invalid) {
            case "season", "team" -> row.put(invalid, UUID.randomUUID().toString());
            case "guild" -> row.put("guildId", "not-a-snowflake");
            case "channel" -> row.put("channelId", "not-a-channel");
            case "unknown-field" -> row.put("untrustedTeamRole", "admin");
            case "duplicate-team" -> {
                var duplicate = binding(); duplicate.put("channelId", "201");
                validator.accept("team_pick_channel_bindings", duplicate);
            }
            case "duplicate-channel" -> {
                var other = UUID.randomUUID(); validator.accept("teams", json.createObjectNode().put("id", other.toString()));
                var duplicate = binding(); duplicate.put("team", other.toString());
                validator.accept("team_pick_channel_bindings", duplicate);
            }
            case "shared-channel" -> validator.accept("matches", json.createObjectNode().put("id", UUID.randomUUID().toString()).put("discordChannelId", "200"));
        }
        // when / then
        assertThatThrownBy(() -> {
            validator.accept("team_pick_channel_bindings", row); validator.validate();
        }).isInstanceOf(PickBackupValidator.ValidationException.class);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 3, 4, 5})
    void givenLegacySchema_whenBindingRowsAreSmuggledIn_thenRefuseUnsupportedGrant(int version) {
        // given
        var validator = validator(version);
        // when / then
        assertThatThrownBy(() -> {
            validator.accept("team_pick_channel_bindings", binding()); validator.validate();
        }).isInstanceOf(PickBackupValidator.ValidationException.class);
    }

    PickBackupValidator validator(int version) {
        var validator = new PickBackupValidator(version);
        validator.accept("seasons", json.createObjectNode().put("id", season.toString()).put("picksCollectionEnabled", false));
        validator.accept("teams", json.createObjectNode().put("id", team.toString()));
        return validator;
    }
    ObjectNode binding() {
        return json.createObjectNode().put("id", UUID.randomUUID().toString()).put("season", season.toString())
                .put("team", team.toString()).put("guildId", "100").put("channelId", "200");
    }
}
