package org.ctc.backup.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class OneMakePoolBackupValidatorTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void givenOneMakeArchives_whenValidated_thenRequireExclusiveDefinitionAndExactSnapshot() {
        // given: each scenario builds an independent archive fixture

        // when / then
        assertThatCode(() -> validate(12, "valid")).doesNotThrowAnyException();
        assertThatCode(() -> validate(12, "draft")).doesNotThrowAnyException();
        assertThatThrownBy(() -> validate(12, "wrong-member")).hasMessageContaining("exactly");
        assertThatThrownBy(() -> validate(12, "missing-car")).hasMessageContaining("reference");
        assertThatThrownBy(() -> validate(12, "clauses")).hasMessageContaining("cannot also");
        assertThatThrownBy(() -> validate(11, "valid")).hasMessageContaining("version 12");
    }

    private void validate(int version, String kind) {
        var validator = new CarPoolBackupValidator(version);
        var season = row().put("carPoolMode", "CATEGORY_POOL").put("carPoolState", kind.equals("draft") ? "DRAFT" : "FROZEN");
        var car = row();
        var other = row();
        var entry = row().put("season", season.path("id").asText()).put("label", "One-Make").put("sortIndex", 0)
                .put("oneMakeCar", kind.equals("missing-car") ? UUID.randomUUID().toString() : car.path("id").asText());
        validator.accept("seasons", season);
        validator.accept("cars", car);
        validator.accept("cars", other);
        validator.accept("season_car_pool_entries", entry);
        if (!kind.equals("draft")) validator.accept("season_car_pool_members", row().put("entry", entry.path("id").asText())
                .put("car", (kind.equals("wrong-member") ? other : car).path("id").asText()));
        if (kind.equals("clauses")) validator.accept("season_car_pool_clauses", row().put("entry", entry.path("id").asText())
                .put("category", "GR_3").put("sortIndex", 0));
        validator.validate();
    }

    private ObjectNode row() { return json.createObjectNode().put("id", UUID.randomUUID().toString()); }
}
