package org.ctc.backup.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class TyreArchiveValidationTest {
    final ObjectMapper mapper = new ObjectMapper();
    final String season = UUID.randomUUID().toString(), phase = UUID.randomUUID().toString(), day = UUID.randomUUID().toString();
    final String race = UUID.randomUUID().toString(), car = UUID.randomUUID().toString(), entry = UUID.randomUUID().toString();

    @Test
    void givenNewTypedCompounds_whenExporting_thenDeclareSchema15() {
        // when / then
        assertThat(BackupSchema.SCHEMA_VERSION).isEqualTo(21);
    }

    @ParameterizedTest
    @CsvSource({"5,I", "13,W", "14,SS", "14,SM", "14,SH"})
    void givenHistoricalTypedArrayWithNewCode_whenNormalizing_thenRejectBeforeRestore(int version, String code) {
        // given
        var payload = mapper.createObjectNode().put("lobbyRulesProfile", "LEGACY").put("startType", "GRID_FALSE_START").put("slipstream", "REAL");
        payload.putArray("availableTyres").add(code); payload.putArray("mandatoryTyres");
        var row = mapper.createObjectNode(); row.set("payload", payload);
        // when / then
        assertThatThrownBy(() -> LobbyRulesArchive.normalize(version, "matchday_pick_revisions", row))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({"'SS, SM, SH',SM,CTC_2027,true", "'RS, RM, RH, I, W',RM,LEGACY,false", "All,Medium,LEGACY,false", "Test historical custom label,None,LEGACY,false"})
    void givenPermittedNativeSettings_whenValidating_thenPreserveKnownAndHistoricalText(String available, String mandatory, String profile, boolean oneMake) {
        // given
        var validator = graph(profile, oneMake, available, mandatory);
        // when / then
        assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @CsvSource({"SS,None,CTC_2027,false", "SS,None,LEGACY,true", "'RS,SS',None,CTC_2027,true", "'RS,I',I,CTC_2027,true",
            "'RS,W',W,CTC_2027,true", "RS,RM,CTC_2027,true", "'RS,RS',None,CTC_2027,true", "None,RS,CTC_2027,true"})
    void givenInvalidNativeSelection_whenValidating_thenRejectBeforeAnyWipe(String available, String mandatory, String profile, boolean oneMake) {
        // given
        var validator = graph(profile, oneMake, available, mandatory);
        // when / then
        assertThatThrownBy(validator::validate).isInstanceOf(LobbyRulesBackupValidator.ValidationException.class)
                .hasMessageContaining("tyre");
    }

    @ParameterizedTest
    @ValueSource(strings = {"foreign", "wrong-car", "draft"})
    void givenSportsWithInvalidSavedOneMakeContext_whenValidating_thenReject(String problem) {
        // given
        var validator = graph("CTC_2027", true, "SS", "None", problem);
        // when / then
        assertThatThrownBy(validator::validate).isInstanceOf(LobbyRulesBackupValidator.ValidationException.class);
    }

    LobbyRulesBackupValidator graph(String profile, boolean oneMake, String available, String mandatory) {
        return graph(profile, oneMake, available, mandatory, "valid");
    }
    LobbyRulesBackupValidator graph(String profile, boolean oneMake, String available, String mandatory, String problem) {
        var validator = new LobbyRulesBackupValidator();
        validator.accept("seasons", mapper.createObjectNode().put("id", season).put("lobbyRulesProfile", profile)
                .put("carPoolMode", "CATEGORY_POOL").put("carPoolState", problem.equals("draft") ? "DRAFT" : "FROZEN"));
        validator.accept("season_phases", mapper.createObjectNode().put("id", phase).put("season", season));
        validator.accept("matchdays", mapper.createObjectNode().put("id", day).put("phase", phase));
        ObjectNode definition = mapper.createObjectNode().put("id", entry).put("season", problem.equals("foreign") ? UUID.randomUUID().toString() : season);
        if (oneMake) definition.put("oneMakeCar", problem.equals("wrong-car") ? UUID.randomUUID().toString() : car);
        validator.accept("season_car_pool_entries", definition);
        validator.accept("races", mapper.createObjectNode().put("id", race).put("matchday", day).put("car", car).put("carPoolEntry", entry));
        validator.accept("race_settings", mapper.createObjectNode().put("id", UUID.randomUUID().toString()).put("race", race)
                .put("startType", "GRID_FALSE_START").put("slipstream", "REAL").put("availableTyres", available).put("mandatoryTyres", mandatory));
        return validator;
    }
}
