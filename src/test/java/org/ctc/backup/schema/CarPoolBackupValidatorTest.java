package org.ctc.backup.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CarPoolBackupValidatorTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

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
        var fixture = new Fixture();
        fixture.member.put("car", UUID.randomUUID().toString());
        // when / then
        assertThatThrownBy(fixture.validator::validate).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("car");
        fixture = new Fixture();
        fixture.entry.put("season", UUID.randomUUID().toString());
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
        var fixture = new Fixture();
        fixture.season.put("carPoolState", "DRAFT");
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
        for (Object value : new Object[]{99, 2016.5, "2016"}) {
            var fixture = new Fixture();
            fixture.clause.set("modelYear", MAPPER.valueToTree(value));
            // when / then
            assertThatThrownBy(fixture.validator::validate).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("year");
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
        var fixture = new Fixture();
        fixture.match.put("bye", true);
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
        Fixture() { this(4); }
        Fixture(int version) {
            validator = new CarPoolBackupValidator(version);
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
