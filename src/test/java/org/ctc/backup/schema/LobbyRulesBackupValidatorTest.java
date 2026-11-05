package org.ctc.backup.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class LobbyRulesBackupValidatorTest {
    final ObjectMapper mapper = new ObjectMapper();
    final String season = UUID.randomUUID().toString();
    final String phase = UUID.randomUUID().toString();
    final String day = UUID.randomUUID().toString();
    final String race = UUID.randomUUID().toString();

    @Test
    void given2027SeasonAndRollingWeakRace_whenValidating_thenAcceptTheExactOwningSeasonChoices() {
        // given
        var validator = graph("CTC_2027", true);
        validator.accept("race_settings", settings("ROLLING", "WEAK"));
        // when / then
        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    void givenLegacySeasonWithNewChoice_whenValidating_thenRejectBeforeRestore() {
        // given
        var validator = graph("LEGACY", true);
        validator.accept("race_settings", settings("ROLLING", "REAL"));
        // when / then
        assertThatThrownBy(validator::validate).isInstanceOf(LobbyRulesBackupValidator.ValidationException.class)
                .hasMessageContaining("lobby");
    }

    @Test
    void givenMissingOwningSeason_whenValidating_thenRejectDanglingRuleContext() {
        // given
        var validator = graph("CTC_2027", false);
        validator.accept("race_settings", settings("GRID_FALSE_START", "REAL"));
        // when / then
        assertThatThrownBy(validator::validate).isInstanceOf(LobbyRulesBackupValidator.ValidationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"phase", "day", "race"})
    void givenMissingOwningReference_whenValidating_thenRejectBeforeRestore(String missing) {
        // given
        var validator = new LobbyRulesBackupValidator();
        validator.accept("seasons", mapper.createObjectNode().put("id", season).put("lobbyRulesProfile", "CTC_2027"));
        if (!missing.equals("phase")) validator.accept("season_phases", mapper.createObjectNode().put("id", phase).put("season", season));
        if (!missing.equals("day")) validator.accept("matchdays", mapper.createObjectNode().put("id", day).put("phase", phase));
        if (!missing.equals("race")) validator.accept("races", mapper.createObjectNode().put("id", race).put("matchday", day));
        validator.accept("race_settings", settings("ROLLING", "WEAK"));
        // when / then
        assertThatThrownBy(validator::validate).isInstanceOf(LobbyRulesBackupValidator.ValidationException.class);
    }

    @Test
    void givenTwoSettingsRowsForOneRace_whenValidating_thenRejectAmbiguousChoices() {
        // given
        var validator = graph("CTC_2027", true);
        validator.accept("race_settings", settings("GRID_FALSE_START", "REAL"));
        validator.accept("race_settings", settings("ROLLING", "WEAK"));
        // when / then
        assertThatThrownBy(validator::validate).isInstanceOf(LobbyRulesBackupValidator.ValidationException.class);
    }

    @Test
    void givenAFullCompactReferenceBudget_whenAcceptingAnotherRow_thenFailClosedWithoutUnboundedRetention() {
        // given
        var validator = new LobbyRulesBackupValidator(2);
        validator.accept("seasons", mapper.createObjectNode().put("id", season).put("lobbyRulesProfile", "LEGACY"));
        validator.accept("seasons", mapper.createObjectNode().put("id", UUID.randomUUID().toString()).put("lobbyRulesProfile", "LEGACY"));
        // when / then
        assertThatThrownBy(() -> validator.accept("seasons", mapper.createObjectNode().put("id", UUID.randomUUID().toString()).put("lobbyRulesProfile", "LEGACY")))
                .isInstanceOf(LobbyRulesBackupValidator.ValidationException.class).hasMessageContaining("budget");
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-a-uuid", "1-1-1-1-1", "000000001-0000-0000-0000-00000000000"})
    void givenMalformedReferenceIdentity_whenAccepting_thenRejectBeforeRetainingIt(String id) {
        // given
        var validator = new LobbyRulesBackupValidator();
        var row = mapper.createObjectNode().put("id", id).put("lobbyRulesProfile", "LEGACY");
        // when / then
        assertThatThrownBy(() -> validator.accept("seasons", row)).isInstanceOf(LobbyRulesBackupValidator.ValidationException.class);
    }

    @Test
    void givenDuplicateSeasonIdentity_whenAccepting_thenDoNotReplaceTheOriginalProfile() {
        // given
        var validator = new LobbyRulesBackupValidator();
        validator.accept("seasons", mapper.createObjectNode().put("id", season).put("lobbyRulesProfile", "LEGACY"));
        // when / then
        assertThatThrownBy(() -> validator.accept("seasons", mapper.createObjectNode().put("id", season).put("lobbyRulesProfile", "CTC_2027")))
                .isInstanceOf(LobbyRulesBackupValidator.ValidationException.class);
    }

    LobbyRulesBackupValidator graph(String profile, boolean withSeason) {
        var validator = new LobbyRulesBackupValidator();
        if (withSeason) validator.accept("seasons", mapper.createObjectNode().put("id", season).put("lobbyRulesProfile", profile));
        validator.accept("season_phases", mapper.createObjectNode().put("id", phase).put("season", season));
        validator.accept("matchdays", mapper.createObjectNode().put("id", day).put("phase", phase));
        validator.accept("races", mapper.createObjectNode().put("id", race).put("matchday", day));
        return validator;
    }

    ObjectNode settings(String start, String slipstream) {
        return mapper.createObjectNode().put("id", UUID.randomUUID().toString()).put("race", race)
                .put("startType", start).put("slipstream", slipstream);
    }
}
