package org.ctc.backup.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CarPoolBackupValidatorTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void givenFrozenPoolWithChangedMetadata_whenValidated_thenStoredMembershipIsAccepted() {
        var fixture = new Fixture();
        fixture.validator.validate();
        fixture.car.put("category", "GR_4");
        assertThatCode(fixture.validator::validate).doesNotThrowAnyException();
    }

    @Test
    void givenMissingOrForeignReferences_whenValidated_thenRejectedBeforeRestore() {
        var fixture = new Fixture();
        fixture.member.put("car", UUID.randomUUID().toString());
        assertThatThrownBy(fixture.validator::validate).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("car");
        fixture = new Fixture();
        fixture.entry.put("season", UUID.randomUUID().toString());
        assertThatThrownBy(fixture.validator::validate).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("season");
    }

    @Test
    void givenDuplicateOrOverlappingMembership_whenValidated_thenRejected() {
        var fixture = new Fixture();
        var duplicate = fixture.member.deepCopy().put("id", UUID.randomUUID().toString());
        fixture.validator.accept("season_car_pool_members", duplicate);
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
        var fixture = new Fixture();
        fixture.season.put("carPoolState", "DRAFT");
        assertThatThrownBy(fixture.validator::validate).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("draft");
        fixture = new Fixture();
        var other = fixture.entry.deepCopy().put("id", UUID.randomUUID().toString());
        fixture.validator.accept("season_car_pool_entries", other);
        fixture.validator.accept("season_car_pool_clauses", fixture.clause.deepCopy().put("id", UUID.randomUUID().toString()).put("entry", other.path("id").asText()));
        assertThatThrownBy(fixture.validator::validate).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("empty");
    }

    @Test
    void givenInvalidClauseValues_whenValidated_thenStrictTypesAndDuplicatesAreRejected() {
        for (Object value : new Object[]{99, 2016.5, "2016"}) {
            var fixture = new Fixture();
            fixture.clause.set("modelYear", MAPPER.valueToTree(value));
            assertThatThrownBy(fixture.validator::validate).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("year");
        }
        var fixture = new Fixture();
        fixture.validator.accept("season_car_pool_clauses", fixture.clause.deepCopy().put("id", UUID.randomUUID().toString()));
        assertThatThrownBy(fixture.validator::validate).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Duplicate");
    }

    @Test
    void givenLegacyFixedArchive_whenValidated_thenAbsentFieldsDefaultSafely() {
        var validator = new CarPoolBackupValidator(3);
        validator.accept("seasons", row().put("name", "Old fixed season"));
        assertThatCode(validator::validate).doesNotThrowAnyException();
        var fixture = new Fixture(3);
        assertThatThrownBy(fixture.validator::validate).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("version");
    }

    static ObjectNode row() { return MAPPER.createObjectNode().put("id", UUID.randomUUID().toString()); }

    static class Fixture {
        final CarPoolBackupValidator validator;
        final ObjectNode season = row().put("carPoolMode", "CATEGORY_POOL").put("carPoolState", "FROZEN");
        final ObjectNode car = row();
        final ObjectNode entry = row().put("season", season.path("id").asText()).put("label", "Combined").put("sortIndex", 0);
        final ObjectNode clause = row().put("entry", entry.path("id").asText()).put("category", "GR_3").put("sortIndex", 0);
        final ObjectNode member = row().put("entry", entry.path("id").asText()).put("car", car.path("id").asText());
        Fixture() { this(4); }
        Fixture(int version) {
            validator = new CarPoolBackupValidator(version);
            validator.accept("seasons", season);
            validator.accept("cars", car);
            validator.accept("season_car_pool_entries", entry);
            validator.accept("season_car_pool_clauses", clause);
            validator.accept("season_car_pool_members", member);
        }
    }
}
