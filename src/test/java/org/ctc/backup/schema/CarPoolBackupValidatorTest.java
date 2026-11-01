package org.ctc.backup.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CarPoolBackupValidatorTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void givenMissingVersionFourPoolFields_whenValidated_thenRejectInsteadOfDefaulting() {
        // given
        for (String field : java.util.List.of("carPoolMode", "carPoolState")) {
            var validator = new CarPoolBackupValidator(4);
            var season = row().put("carPoolMode", "FIXED_CARS").put("carPoolState", "DRAFT");
            season.remove(field);
            validator.accept("seasons", season);
            // when / then
            assertThatThrownBy(validator::validate).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Missing season");
        }
    }

    @Test
    void givenFixedFrozenSeasonOrLegacyCategoryMode_whenValidated_thenRejectTheModeState() {
        // given
        var validator = new CarPoolBackupValidator(4);
        validator.accept("seasons", row().put("carPoolMode", "FIXED_CARS").put("carPoolState", "FROZEN"));
        // when / then
        assertThatThrownBy(validator::validate).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Fixed season");
        var legacy = new CarPoolBackupValidator(3);
        assertThatThrownBy(() -> legacy.accept("seasons", row().put("carPoolMode", "CATEGORY_POOL")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("version");
        assertThatThrownBy(() -> legacy.accept("seasons", row().put("carPoolState", "FROZEN")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("version");
    }

    @Test
    void givenMissingOrNonTextualId_whenAccepted_thenRejectWithValidationError() {
        // given
        var validator = new CarPoolBackupValidator(4);
        // when / then
        assertThatThrownBy(() -> validator.accept("cars", MAPPER.createObjectNode())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.accept("cars", row().put("id", 1))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void givenInvalidScalarTypes_whenAccepting_thenRejectBeforeRetention() {
        // given
        for (Object index : new Object[]{0.5, 4294967296L, "0"}) {
            var validator = new CarPoolBackupValidator(4);
            var entry = row().set("sortIndex", MAPPER.valueToTree(index));
            // when / then
            assertThatThrownBy(() -> validator.accept("season_car_pool_entries", entry)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("sortIndex");
        }
        var validator = new CarPoolBackupValidator(4);
        assertThatThrownBy(() -> validator.accept("matches", row().put("bye", "false")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("bye");
    }

    @Test
    void givenInvalidStateOrEntryProperties_whenValidated_thenReject() {
        // given
        java.util.List<java.util.function.Consumer<Fixture>> changes = java.util.List.of(
                fixture -> fixture.season.remove("carPoolMode"),
                fixture -> fixture.season.remove("carPoolState"),
                fixture -> fixture.season.put("carPoolMode", "INVALID"),
                fixture -> fixture.season.put("carPoolMode", "FIXED_CARS"),
                fixture -> fixture.entry.put("label", " "),
                fixture -> fixture.entry.put("sortIndex", -1));
        for (var change : changes) {
            // when / then
            assertThatThrownBy(() -> new Fixture(change).validator.validate()).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void givenTooManyClausesOrMissingDefinitions_whenValidated_thenReject() {
        // given
        var fixture = new Fixture();
        for (int year = 1000; year < 1032; year++) fixture.validator.accept("season_car_pool_clauses",
                fixture.clause.deepCopy().put("id", UUID.randomUUID().toString()).put("modelYear", year));
        // when / then
        assertThatThrownBy(fixture.validator::validate).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Too many");
        fixture = new Fixture();
        fixture.validator.accept("season_car_pool_entries", fixture.entry.deepCopy().put("id", UUID.randomUUID().toString()));
        assertThatThrownBy(fixture.validator::validate).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no clauses");
        var empty = new CarPoolBackupValidator(4);
        empty.accept("seasons", row().put("carPoolMode", "CATEGORY_POOL").put("carPoolState", "FROZEN"));
        assertThatThrownBy(empty::validate).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("empty");
    }

    @Test
    void givenConfiguredRaceWithoutEffectiveHomeTeam_whenValidated_thenReject() {
        // given
        var fixture = new Fixture();
        var race = fixture.race();
        race.remove("match");
        fixture.validator.accept("races", race);
        // when / then
        assertThatThrownBy(fixture.validator::validate).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("home team");
    }

    @Test
    void givenPlayoffRaceWithoutMatch_whenValidated_thenMatchupHomeOwnsConsumption() {
        // given
        var fixture = new Fixture();
        var matchup = row().put("team1", UUID.randomUUID().toString());
        fixture.validator.accept("playoff_matchups", matchup);
        var race = fixture.race();
        race.remove("match");
        race.put("playoffMatchup", matchup.path("id").asText());
        fixture.validator.accept("races", race);
        // when / then
        assertThatCode(fixture.validator::validate).doesNotThrowAnyException();
        fixture.validator.accept("races", race.deepCopy().put("id", UUID.randomUUID().toString()));
        assertThatThrownBy(fixture.validator::validate).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Duplicate entry consumption");
    }

    @Test
    void givenLegacyRows_whenReading_thenNoRetentionBudgetAppliesAndNewPoolDataIsRejected() {
        // given
        var validator = new CarPoolBackupValidator(3);
        var car = row();
        // when / then
        assertThatCode(() -> {
            for (int i = 0; i < 100_001; i++) validator.accept("cars", car);
            validator.validate();
        }).doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.accept("season_car_pool_entries", row())).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("version");
        assertThatThrownBy(() -> validator.accept("races", row().put("carPoolEntry", UUID.randomUUID().toString())))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("version");
    }

    @Test
    void givenAcceptedScalarProjection_whenCallerChangesItsPayload_thenRetainedStateIsIndependent() {
        // given
        var fixture = new Fixture();
        // when
        fixture.season.put("carPoolState", "DRAFT");
        // then
        assertThatCode(fixture.validator::validate).doesNotThrowAnyException();
    }

    @Test
    void givenContainerInsideRetainedScalarField_whenAccepting_thenRejectBeforeAccumulatingNestedNodes() {
        // given
        for (var field : java.util.Map.of("seasons", "carPoolMode", "season_car_pool_entries", "label",
                "season_car_pool_clauses", "category", "season_car_pool_members", "car", "races", "matchday").entrySet()) {
            var validator = new CarPoolBackupValidator(4);
            var value = row();
            value.set(field.getValue(), MAPPER.createArrayNode().add(MAPPER.createObjectNode().put("nested", "payload")));
            // when / then
            assertThatThrownBy(() -> validator.accept(field.getKey(), value)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(field.getValue());
        }
    }

    @Test
    void givenOversizedRetainedText_whenAccepting_thenRejectBeforeRetention() {
        // given
        var validator = new CarPoolBackupValidator(4);
        // when / then
        assertThatThrownBy(() -> validator.accept("season_car_pool_entries", row().put("label", "x".repeat(121))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("label");
        assertThatThrownBy(() -> validator.accept("races", row().put("matchday", "x".repeat(37))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("matchday");
    }

    @Test
    void givenRepeatedId_whenAcceptingRows_thenRejectBeforeRetainingAnotherRow() {
        // given
        var validator = new CarPoolBackupValidator(4);
        var car = row();
        validator.accept("cars", car);
        // when / then
        assertThatThrownBy(() -> validator.accept("cars", car.deepCopy())).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Duplicate");
    }

    @Test
    void givenManyMinimalRows_whenAcceptingRows_thenBoundedRetentionRejectsTheNextRow() {
        // given
        var validator = new CarPoolBackupValidator(4);
        for (int i = 0; i < 100_000; i++) validator.accept("cars", row());
        // when / then
        assertThatThrownBy(() -> validator.accept("cars", row())).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("row budget");
    }

    @Test
    void givenFrozenPoolWithChangedMetadata_whenValidated_thenStoredMembershipIsAccepted() {
        // given
        var fixture = new Fixture();
        fixture.validator.validate();
        fixture.car.put("category", "GR_4");
        // when / then
        assertThatCode(fixture.validator::validate).doesNotThrowAnyException();
    }

    @Test
    void givenMissingOrForeignReferences_whenValidated_thenRejectedBeforeRestore() {
        // given
        var fixture = new Fixture(f -> f.member.put("car", UUID.randomUUID().toString()));
        // when / then
        assertThatThrownBy(fixture.validator::validate).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("car");
        fixture = new Fixture(f -> f.entry.put("season", UUID.randomUUID().toString()));
        assertThatThrownBy(fixture.validator::validate).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("season");
    }

    @Test
    void givenDuplicateOrOverlappingMembership_whenValidated_thenRejected() {
        // given
        var fixture = new Fixture();
        var duplicate = fixture.member.deepCopy().put("id", UUID.randomUUID().toString());
        fixture.validator.accept("season_car_pool_members", duplicate);
        // when / then
        assertThatThrownBy(fixture.validator::validate).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Duplicate");
        fixture = new Fixture();
        var other = fixture.entry.deepCopy().put("id", UUID.randomUUID().toString());
        fixture.validator.accept("season_car_pool_entries", other);
        fixture.validator.accept("season_car_pool_clauses", fixture.clause.deepCopy().put("id", UUID.randomUUID().toString()).put("entry", other.path("id").asText()));
        fixture.validator.accept("season_car_pool_members", fixture.member.deepCopy().put("id", UUID.randomUUID().toString()).put("entry", other.path("id").asText()));
        assertThatThrownBy(fixture.validator::validate).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("overlap");
    }

    @Test
    void givenDraftWithMembersOrFrozenWithoutMembers_whenValidated_thenRejected() {
        // given
        var fixture = new Fixture(f -> f.season.put("carPoolState", "DRAFT"));
        // when / then
        assertThatThrownBy(fixture.validator::validate).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("draft");
        fixture = new Fixture();
        var other = fixture.entry.deepCopy().put("id", UUID.randomUUID().toString());
        fixture.validator.accept("season_car_pool_entries", other);
        fixture.validator.accept("season_car_pool_clauses", fixture.clause.deepCopy().put("id", UUID.randomUUID().toString()).put("entry", other.path("id").asText()));
        assertThatThrownBy(fixture.validator::validate).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("empty");
    }

    @Test
    void givenInvalidClauseValues_whenValidated_thenStrictTypesAndDuplicatesAreRejected() {
        // given
        for (Object value : new Object[]{99, 10000, 4294969312L, 2016.5, "2016"}) {
            // when / then
            assertThatThrownBy(() -> new Fixture(f -> f.clause.set("modelYear", MAPPER.valueToTree(value))).validator.validate()).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("year");
        }
        var fixture = new Fixture();
        fixture.validator.accept("season_car_pool_clauses", fixture.clause.deepCopy().put("id", UUID.randomUUID().toString()));
        assertThatThrownBy(fixture.validator::validate).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Duplicate");
    }

    @Test
    void givenCategoryRaceWithBothFieldsEmpty_whenValidated_thenDraftRaceRemainsValid() {
        // given
        var fixture = new Fixture();
        var race = fixture.race();
        race.remove(java.util.List.of("car", "carPoolEntry"));
        fixture.validator.accept("races", race);
        // when / then
        assertThatCode(fixture.validator::validate).doesNotThrowAnyException();
    }

    @Test
    void givenPartialCategorySelectionOrFixedModeEntry_whenValidated_thenReject() {
        // given
        for (String missing : new String[]{"carPoolEntry", "car"}) {
            var fixture = new Fixture();
            var race = fixture.race();
            race.remove(missing);
            fixture.validator.accept("races", race);
            // when / then
            assertThatThrownBy(fixture.validator::validate).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("both");
        }
        var fixture = new Fixture();
        var fixedSeason = row().put("carPoolMode", "FIXED_CARS").put("carPoolState", "DRAFT");
        fixture.validator.accept("seasons", fixedSeason);
        var phase = row().put("season", fixedSeason.path("id").asText());
        var matchday = row().put("phase", phase.path("id").asText());
        fixture.validator.accept("season_phases", phase);
        fixture.validator.accept("matchdays", matchday);
        fixture.validator.accept("races", fixture.race().put("matchday", matchday.path("id").asText()));
        assertThatThrownBy(fixture.validator::validate).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Fixed");
    }

    @Test
    void givenForeignEntryOrNonMemberCar_whenValidated_thenReject() {
        // given
        var fixture = new Fixture();
        var otherSeason = row().put("carPoolMode", "CATEGORY_POOL").put("carPoolState", "DRAFT");
        fixture.validator.accept("seasons", otherSeason);
        var phase = row().put("season", otherSeason.path("id").asText());
        var matchday = row().put("phase", phase.path("id").asText());
        fixture.validator.accept("season_phases", phase);
        fixture.validator.accept("matchdays", matchday);
        fixture.validator.accept("races", fixture.race().put("matchday", matchday.path("id").asText()));
        // when / then
        assertThatThrownBy(fixture.validator::validate).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("different season");
        fixture = new Fixture();
        var otherCar = row();
        fixture.validator.accept("cars", otherCar);
        fixture.validator.accept("races", fixture.race().put("car", otherCar.path("id").asText()));
        assertThatThrownBy(fixture.validator::validate).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("member");
    }

    @Test
    void givenDuplicateEntryConsumption_whenValidated_thenRejectEvenWithDifferentMemberCars() {
        // given
        var fixture = new Fixture();
        var otherCar = row();
        fixture.validator.accept("cars", otherCar);
        fixture.validator.accept("season_car_pool_members", fixture.member.deepCopy().put("id", UUID.randomUUID().toString()).put("car", otherCar.path("id").asText()));
        fixture.validator.accept("races", fixture.race());
        fixture.validator.accept("races", fixture.race().put("car", otherCar.path("id").asText()));
        // when / then
        assertThatThrownBy(fixture.validator::validate).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Duplicate entry consumption");
    }

    @Test
    void givenReturnLegWithDifferentEffectiveHomeTeam_whenValidated_thenEachTeamMayUseTheEntry() {
        // given
        var fixture = new Fixture();
        fixture.validator.accept("races", fixture.race());
        fixture.validator.accept("races", fixture.race().put("homeTeamOverride", UUID.randomUUID().toString()));
        // when / then
        assertThatCode(fixture.validator::validate).doesNotThrowAnyException();
        var sameOverride = UUID.randomUUID().toString();
        fixture = new Fixture();
        fixture.validator.accept("races", fixture.race().put("homeTeamOverride", sameOverride));
        fixture.validator.accept("races", fixture.race().put("homeTeamOverride", sameOverride));
        assertThatThrownBy(fixture.validator::validate).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Duplicate entry consumption");
    }

    @Test
    void givenByeWithEntry_whenValidated_thenRejectConsumption() {
        // given
        var fixture = new Fixture(f -> f.match.put("bye", true));
        fixture.validator.accept("races", fixture.race());
        // when / then
        assertThatThrownBy(fixture.validator::validate).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("bye");
    }

    @Test
    void givenLegacyFixedArchive_whenValidated_thenAbsentFieldsDefaultSafely() {
        // given
        var validator = new CarPoolBackupValidator(3);
        validator.accept("seasons", row().put("name", "Old fixed season"));
        // when / then
        assertThatCode(validator::validate).doesNotThrowAnyException();
        assertThatThrownBy(() -> new Fixture(3)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("version");
    }

    static ObjectNode row() { return MAPPER.createObjectNode().put("id", UUID.randomUUID().toString()); }

    static class Fixture {
        final CarPoolBackupValidator validator;
        final ObjectNode season = row().put("carPoolMode", "CATEGORY_POOL").put("carPoolState", "FROZEN");
        final ObjectNode car = row();
        final ObjectNode entry = row().put("season", season.path("id").asText()).put("label", "Combined").put("sortIndex", 0);
        final ObjectNode clause = row().put("entry", entry.path("id").asText()).put("category", "GR_3").put("sortIndex", 0);
        final ObjectNode member = row().put("entry", entry.path("id").asText()).put("car", car.path("id").asText());
        final ObjectNode phase = row().put("season", season.path("id").asText());
        final ObjectNode matchday = row().put("phase", phase.path("id").asText());
        final ObjectNode match = row().put("homeTeam", UUID.randomUUID().toString()).put("awayTeam", UUID.randomUUID().toString()).put("bye", false);
        Fixture() { this(4, ignored -> {}); }
        Fixture(java.util.function.Consumer<Fixture> configure) { this(4, configure); }
        Fixture(int version) { this(version, ignored -> {}); }
        Fixture(int version, java.util.function.Consumer<Fixture> configure) {
            validator = new CarPoolBackupValidator(version);
            configure.accept(this);
            validator.accept("seasons", season);
            validator.accept("cars", car);
            validator.accept("season_car_pool_entries", entry);
            validator.accept("season_car_pool_clauses", clause);
            validator.accept("season_car_pool_members", member);
            validator.accept("season_phases", phase);
            validator.accept("matchdays", matchday);
            validator.accept("matches", match);
        }

        ObjectNode race() {
            return row().put("matchday", matchday.path("id").asText()).put("match", match.path("id").asText())
                    .put("car", car.path("id").asText()).put("carPoolEntry", entry.path("id").asText());
        }
    }
}
